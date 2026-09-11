package ma.jurika.ticket.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEvenement;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.FormaliteVolet;
import ma.jurika.ticket.domain.model.JustificatifAttendu;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketCommentType;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DemarcheJournalRepository;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import ma.jurika.ticket.domain.port.SaisieDossierLookup;
import ma.jurika.ticket.domain.port.TicketDemarcheRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.domain.service.EcheanceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cochage des demarches d'un ticket, et vue d'avancement.
 *
 * <p>Toutes les regles de l'enonce sont verifiees ICI, cote serveur : le front
 * ne fait que refleter. Un workflow sans referentiel charge (tout sauf CREATION
 * a ce jour) renvoie une vue vide et refuse tout cochage — les autres workflows
 * ne sont donc pas modifies par ce lot.
 */
@Service
public class DemarcheUseCases {

    /** Seuils d'alerte, alignes sur ceux deja pratiques par DeadlineRule (J-15 / J-3). */
    private static final int JOURS_APPROCHE = 15;
    private static final int JOURS_CRITIQUE = 3;

    private final TicketRepository tickets;
    private final DemarcheReferentielRepository referentiel;
    private final TicketDemarcheRepository etats;
    private final DataroomDocumentLookup documents;
    private final CommentRepository comments;
    private final DemarcheJournalRepository journal;
    private final SaisieDossierLookup saisies;

    public DemarcheUseCases(TicketRepository tickets, DemarcheReferentielRepository referentiel,
                             TicketDemarcheRepository etats, DataroomDocumentLookup documents,
                             CommentRepository comments, DemarcheJournalRepository journal,
                             SaisieDossierLookup saisies) {
        this.tickets = tickets;
        this.referentiel = referentiel;
        this.etats = etats;
        this.documents = documents;
        this.comments = comments;
        this.journal = journal;
        this.saisies = saisies;
    }

    // =================================================================
    //  Vue
    // =================================================================

    /** Une demarche, telle qu'affichee : referentiel + etat sur ce ticket. */
    public record Ligne(UUID demarcheId, int ordre, String phaseCode, String phaseLibelle,
                        String libelle, TicketStatut statutTicket, String acteur, String organisme,
                        boolean obligatoire, String conditionApplication, String piecesEntrantes,
                        String documentProduit, String justificatifsTexte, String modeleJurika,
                        String delai, String coutIndicatif, String variablesAlimentees,
                        /**
                         * true si le parcours permet de CALCULER une echeance pour
                         * cette etape.
                         *
                         * <p>Une seule ligne du parcours du 9 septembre ne le permet
                         * pas : la 15, « dans les 30 jours de la signature du contrat »
                         * — aucune ligne cochable ne porte cette signature. L interface
                         * doit le dire, et surtout ne jamais fabriquer de date.
                         *
                         * <p>Les lignes 23 (taxe professionnelle) et 32 (CNSS) etaient
                         * dans le meme cas jusqu au lot B : leur depart, le debut
                         * d activite, n etait pas une donnee du dossier. Il l est.
                         *
                         * <p>(Le commentaire precedent citait les etapes 16, 19 et 26 :
                         * c etait la numerotation du referentiel a 36 lignes du lot 1,
                         * remplace par le parcours a 51 lignes.)
                         */
                        boolean delaiCalculable,
                        List<JustificatifAttendu> justificatifsAttendus,
                        DemarcheEtat etat, String motif, Instant cocheAt, List<UUID> documentsDeposes,
                        boolean actionnableMaintenant, boolean horsSequence,
                        /**
                         * Lot B — les deux lignes d une meme formalite, depot puis
                         * retrait, portent le meme code. Une ligne unique n en a pas.
                         */
                        String formaliteCode,
                        FormaliteVolet formaliteVolet,
                        /** Pour un RETRAIT : l ordre de la ligne de depot. */
                        Integer depotOrdre,
                        /**
                         * Pour un RETRAIT : la date a laquelle le depot a ete coche.
                         * C est elle qui repond a « depuis quand attendons-nous ? »,
                         * et c est elle qui fait courir le delai du retrait.
                         */
                        Instant deposeLe,
                        /**
                         * Le journal des gestes poses sur cette demarche. Un cochage
                         * annule y garde SES DEUX horodatages ; une demarche decochee
                         * puis recochee y garde les trois evenements.
                         */
                        List<DemarcheEvenement> journal,
                        /**
                         * Lot B — nom de la DONNEE qui ferait courir le delai et qui
                         * n est pas encore saisie ; {@code null} des qu elle l est, ou
                         * quand le delai ne depend d aucune saisie.
                         *
                         * <p>Sans ce champ, la taxe professionnelle et la CNSS
                         * afficheraient « Delai : dans les 30 jours du debut
                         * d activite » sans alerte et sans explication — le produit
                         * aurait l air de savoir calculer et de ne rien dire. Il sait
                         * calculer ; il attend la date.
                         */
                        String delaiDepartManquant) {}

    public record Phase(String code, String libelle, List<Ligne> demarches,
                        int traitees, int total) {}

    /**
     * Une echeance legale calculable qui approche ou est depassee.
     *
     * @param echeance date CIVILE limite (jamais un instant : un delai legal se
     *                 compte en jours de calendrier, pas en heures)
     */
    public record PointAttention(int ordre, String libelle, String delai,
                                  LocalDate echeance, long joursRestants, String severite) {}

    public record Avancement(TicketStatut statutCourant, int position, int totalStatuts,
                              int traitees, int applicables,
                              Integer prochainOrdre, String prochainLibelle,
                              List<PointAttention> pointsAttention) {}

    public record Vue(String workflowType, TicketStatut statutCourant,
                      List<Phase> phases, Avancement avancement) {}

    @Transactional(readOnly = true)
    public Vue vue(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);
        List<Demarche> toutes = referentiel.findByWorkflow(ticket.type().name());
        Map<UUID, TicketDemarche> parDemarche = etats.findByTicket(workspaceId, ticketId);
        Map<UUID, List<DemarcheEvenement>> journaux = journal.parDemarche(workspaceId,
                parDemarche.values().stream().map(TicketDemarche::id)
                        .filter(java.util.Objects::nonNull).toList());

        // Le depot d une formalite scindee, retrouve par son code : c est ce lien
        // qui permet a la ligne « retrait » de dire depuis quand elle attend.
        Map<String, Demarche> depotParFormalite = new LinkedHashMap<>();
        for (Demarche d : toutes) {
            if (d.estDepot()) depotParFormalite.put(d.formaliteCode(), d);
        }

        // Lot B — les dates saisies dont depend un delai. Resolues UNE fois ici :
        // elles servent a la fois a dire, ligne par ligne, qu'on attend encore la
        // date, et a calculer les echeances plus bas.
        Map<String, Optional<LocalDate>> datesSaisies = datesDesDelais(ticket, toutes);

        List<Ligne> lignes = new ArrayList<>(toutes.size());
        for (Demarche d : toutes) {
            TicketDemarche etat = parDemarche.get(d.id());
            DemarcheEtat e = etat == null ? DemarcheEtat.A_FAIRE : etat.etat();
            lignes.add(new Ligne(d.id(), d.ordre(), d.phaseCode(), d.phaseLibelle(), d.libelle(),
                    d.statutTicket(), d.acteur(), d.organisme(), d.obligatoire(),
                    d.conditionApplication(), d.piecesEntrantes(), d.documentProduit(),
                    d.justificatifsTexte(), d.modeleJurika(), d.delai(), d.coutIndicatif(),
                    d.variablesAlimentees(), d.delaiCalculable(), d.justificatifsAttendus(),
                    e, etat == null ? null : etat.motif(), etat == null ? null : etat.cocheAt(),
                    etat == null ? List.of() : etat.justificatifsDeposes(),
                    d.statutTicket() == ticket.statut(),
                    false,
                    d.formaliteCode(), d.formaliteVolet(),
                    depotOrdre(d, depotParFormalite),
                    deposeLe(d, depotParFormalite, parDemarche),
                    etat == null || etat.id() == null ? List.of()
                            : journaux.getOrDefault(etat.id(), List.of()),
                    departManquant(d, datesSaisies)));
        }
        lignes = marquerHorsSequence(lignes);

        // Groupement par phase, dans l'ordre du guide.
        Map<String, List<Ligne>> parPhase = new LinkedHashMap<>();
        for (Ligne l : lignes) parPhase.computeIfAbsent(l.phaseCode(), k -> new ArrayList<>()).add(l);
        List<Phase> phases = parPhase.entrySet().stream()
                .map(en -> new Phase(en.getKey(), en.getValue().get(0).phaseLibelle(), en.getValue(),
                        (int) en.getValue().stream().filter(l -> l.etat() != DemarcheEtat.A_FAIRE).count(),
                        en.getValue().size()))
                .toList();

        return new Vue(ticket.type().name(), ticket.statut(), phases,
                avancement(ticket, toutes, lignes, datesSaisies));
    }

    /**
     * Les dates saisies dont depend au moins un delai, resolues une seule fois.
     *
     * <p>Deux lignes du parcours partagent la meme donnee : lire deux fois serait
     * deux requetes pour la meme reponse. {@code Optional} en valeur pour
     * memoriser aussi l absence, qui est la reponse normale d un dossier jeune.
     */
    private Map<String, Optional<LocalDate>> datesDesDelais(Ticket ticket, List<Demarche> toutes) {
        Map<String, Optional<LocalDate>> out = new LinkedHashMap<>();
        for (Demarche d : toutes) {
            if (!d.delaiCalculable() || !d.delaiPartDuneDonnee()) continue;
            out.computeIfAbsent(d.delaiReferenceDonnee(),
                    nom -> saisies.dateSaisie(ticket.workspaceId(), ticket.id(), nom));
        }
        return out;
    }

    /** La donnee qu on attend pour faire courir ce delai, ou {@code null}. */
    private static String departManquant(Demarche d, Map<String, Optional<LocalDate>> dates) {
        if (!d.delaiCalculable() || !d.delaiPartDuneDonnee()) return null;
        return dates.getOrDefault(d.delaiReferenceDonnee(), Optional.empty()).isPresent()
                ? null : d.delaiReferenceDonnee();
    }

    /**
     * L ordre de la ligne de DEPOT d une formalite, pour sa ligne de RETRAIT.
     * {@code null} partout ailleurs : une ligne unique n a pas de depot separe.
     */
    private static Integer depotOrdre(Demarche d, Map<String, Demarche> depots) {
        if (!d.estRetrait()) return null;
        Demarche depot = depots.get(d.formaliteCode());
        return depot == null ? null : depot.ordre();
    }

    /**
     * LA DATE DU DEPOT, portee par la ligne de RETRAIT.
     *
     * <p>« Selon le delai du service de l enregistrement » ne se calcule pas : le
     * parcours ne donne aucune duree. Ce qu on peut dire, en revanche, c est
     * DEPUIS QUAND on attend — et c est ce que le cabinet demande. La date vient
     * du cochage du depot, jamais d une saisie.
     *
     * <p>Elle n est rendue que si le depot est effectivement COCHE : un retrait
     * dont le depot n a pas eu lieu n attend rien.
     */
    private static Instant deposeLe(Demarche d, Map<String, Demarche> depots,
                                     Map<UUID, TicketDemarche> parDemarche) {
        if (!d.estRetrait()) return null;
        Demarche depot = depots.get(d.formaliteCode());
        if (depot == null) return null;
        TicketDemarche etat = parDemarche.get(depot.id());
        if (etat == null || etat.etat() != DemarcheEtat.COCHEE) return null;
        return etat.cocheAt();
    }

    /**
     * Signale une demarche cochee alors qu'une precedente de la MEME phase ne
     * l'est pas. On ne l'interdit pas : la pratique reelle varie (l'onglet 5 du
     * guide signale que TP et RC sont parfois simultanes). On le signale.
     */
    private List<Ligne> marquerHorsSequence(List<Ligne> lignes) {
        List<Ligne> out = new ArrayList<>(lignes.size());
        Map<String, Boolean> trouAvant = new LinkedHashMap<>();
        for (Ligne l : lignes) {
            boolean trou = trouAvant.getOrDefault(l.phaseCode(), false);
            boolean traitee = l.etat() != DemarcheEtat.A_FAIRE;
            out.add(new Ligne(l.demarcheId(), l.ordre(), l.phaseCode(), l.phaseLibelle(), l.libelle(),
                    l.statutTicket(), l.acteur(), l.organisme(), l.obligatoire(),
                    l.conditionApplication(), l.piecesEntrantes(), l.documentProduit(),
                    l.justificatifsTexte(), l.modeleJurika(), l.delai(), l.coutIndicatif(),
                    l.variablesAlimentees(), l.delaiCalculable(), l.justificatifsAttendus(),
                    l.etat(), l.motif(),
                    l.cocheAt(), l.documentsDeposes(), l.actionnableMaintenant(),
                    traitee && trou,
                    l.formaliteCode(), l.formaliteVolet(), l.depotOrdre(), l.deposeLe(),
                    l.journal(), l.delaiDepartManquant()));
            if (!traitee) trouAvant.put(l.phaseCode(), true);
        }
        return out;
    }

    private Avancement avancement(Ticket ticket, List<Demarche> toutes, List<Ligne> lignes,
                                   Map<String, Optional<LocalDate>> datesSaisies) {
        List<Ligne> duStatut = lignes.stream()
                .filter(l -> l.statutTicket() == ticket.statut()).toList();
        int traitees = (int) duStatut.stream().filter(l -> l.etat() != DemarcheEtat.A_FAIRE).count();

        Optional<Ligne> prochaine = duStatut.stream()
                .filter(l -> l.etat() == DemarcheEtat.A_FAIRE).findFirst();

        return new Avancement(
                ticket.statut(),
                ticket.statut().position(),
                TicketStatut.parcours().size(),
                traitees,
                duStatut.size(),
                prochaine.map(Ligne::ordre).orElse(null),
                prochaine.map(Ligne::libelle).orElse(null),
                pointsAttention(toutes, lignes, datesSaisies));
    }

    /**
     * Echeances legales calculables. Une demarche n'entre dans la liste que si
     * son point de depart est POSE et qu'elle n'est pas elle-meme traitee. Les
     * etapes dont le guide ne permet pas de calculer un point de depart n'y
     * figurent jamais : mieux vaut aucune alerte qu'une fausse.
     *
     * <p>Le point de depart est POSE dans deux cas :
     *
     * <ul>
     *   <li>l'etape de reference est cochee — huit delais du parcours ;</li>
     *   <li>la donnee de reference est saisie — deux delais, la taxe
     *       professionnelle et l'affiliation CNSS, qui courent depuis le debut
     *       d'activite. Ils etaient aveugles jusqu'a ce que le champ existe.</li>
     * </ul>
     *
     * <p>Tant que la date n'est pas saisie, ces deux lignes se comportent
     * exactement comme une ligne dont l'etape de reference n'est pas cochee :
     * aucune echeance, aucune alerte, et AUCUNE DATE FABRIQUEE.
     */
    private List<PointAttention> pointsAttention(List<Demarche> toutes, List<Ligne> lignes,
                                                  Map<String, Optional<LocalDate>> datesSaisies) {
        Map<Integer, Ligne> parOrdre = new LinkedHashMap<>();
        for (Ligne l : lignes) parOrdre.put(l.ordre(), l);

        List<PointAttention> out = new ArrayList<>();
        LocalDate aujourdhui = EcheanceCalculator.aujourdhui();
        for (Demarche d : toutes) {
            if (!d.delaiCalculable()) continue;
            Ligne cible = parOrdre.get(d.ordre());
            if (cible == null || cible.etat() != DemarcheEtat.A_FAIRE) continue;

            LocalDate depart;
            if (d.delaiPartDuneDonnee()) {
                depart = datesSaisies.getOrDefault(d.delaiReferenceDonnee(), Optional.empty())
                        .orElse(null);
                if (depart == null) continue; // la date n'est pas saisie : rien a calculer
            } else {
                Ligne reference = parOrdre.get(d.delaiReferenceOrdre());
                if (reference == null || reference.cocheAt() == null
                        || reference.etat() != DemarcheEtat.COCHEE) {
                    continue; // le delai n'a pas commence a courir
                }
                depart = LocalDate.ofInstant(reference.cocheAt(), EcheanceCalculator.ZONE);
            }
            LocalDate echeance = EcheanceCalculator.echeance(
                    depart, d.delaiValeur(), d.delaiUnite());
            long jours = ChronoUnit.DAYS.between(aujourdhui, echeance);
            String severite = jours < 0 ? "DEPASSE"
                    : jours <= JOURS_CRITIQUE ? "CRITIQUE"
                    : jours <= JOURS_APPROCHE ? "APPROCHE" : null;
            if (severite == null) continue;
            out.add(new PointAttention(d.ordre(), d.libelle(), d.delai(), echeance, jours, severite));
        }
        out.sort((a, b) -> Long.compare(a.joursRestants(), b.joursRestants()));
        return out;
    }

    // =================================================================
    //  Gestes
    // =================================================================

    @Transactional
    @Auditable(action = "DEMARCHE_COCHEE", resourceType = "ticket", resourceIdExpr = "#ticketId")
    public Vue cocher(UUID workspaceId, UUID ticketId, int ordre,
                       List<UUID> documentIds, UUID acteurId) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);
        Demarche d = demarcheDuStatutCourant(ticket, ordre);

        List<TicketDemarcheRepository.JustificatifDepose> deposes =
                verifierJustificatifs(workspaceId, ticketId, d, documentIds);

        UUID ligneId = etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.COCHEE,
                null, acteurId, deposes);
        // Le journal enregistre la date, l heure et QUI a coche. C est lui qui
        // survit a un decochage — pas la colonne `coche_at`, qui ne porte que
        // l etat courant.
        journal.enregistrer(workspaceId, ligneId, DemarcheEvenement.Type.COCHAGE,
                null, acteurId, deposes.size());
        journaliser(workspaceId, ticketId, acteurId, d,
                "Demarche " + d.ordre() + " cochee : " + d.libelle(),
                Map.of("ordre", d.ordre(), "etat", DemarcheEtat.COCHEE.name(),
                        "justificatifs", deposes.size()));
        return vue(workspaceId, ticketId);
    }

    @Transactional
    @Auditable(action = "DEMARCHE_DECOCHEE", resourceType = "ticket", resourceIdExpr = "#ticketId")
    public Vue decocher(UUID workspaceId, UUID ticketId, int ordre, String motif, UUID acteurId) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);
        // Le decochage n'est possible que tant que le ticket n'a pas quitte le
        // statut dont releve la demarche.
        Demarche d = demarcheDuStatutCourant(ticket, ordre);

        // LE MOTIF EST OBLIGATOIRE. Decision du cabinet : l annulation d un cochage
        // est possible apres validation, mais elle doit se justifier. Annuler sans
        // dire pourquoi laisserait un trou dans le journal exactement la ou il est
        // le plus consulte.
        if (motif == null || motif.isBlank()) {
            throw new ValidationException("Motif obligatoire pour annuler le cochage de la "
                    + "demarche " + d.ordre() + " : " + d.libelle());
        }

        Map<UUID, TicketDemarche> avant = etats.findByTicket(workspaceId, ticketId);
        TicketDemarche precedent = avant.get(d.id());
        if (precedent == null || precedent.etat() == DemarcheEtat.A_FAIRE) {
            throw new ConflictException("La demarche " + d.ordre()
                    + " n'est ni cochee ni ecartee : il n'y a rien a annuler.");
        }
        // Annuler un cochage et reprendre une demarche ecartee sont deux gestes
        // differents, et le journal doit les distinguer : le premier revient sur un
        // travail fait, le second sur une decision de perimetre.
        DemarcheEvenement.Type type = precedent.etat() == DemarcheEtat.COCHEE
                ? DemarcheEvenement.Type.ANNULATION
                : DemarcheEvenement.Type.REPRISE;

        // Le motif reste sur la ligne : l ecran doit pouvoir dire, sans deplier le
        // journal, pourquoi cette demarche est redevenue « a faire ».
        UUID ligneId = etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.A_FAIRE,
                motif.trim(), acteurId, List.of());
        journal.enregistrer(workspaceId, ligneId, type, motif.trim(), acteurId, 0);
        journaliser(workspaceId, ticketId, acteurId, d,
                "Demarche " + d.ordre() + " decochee : " + motif.trim(),
                Map.of("ordre", d.ordre(), "etat", DemarcheEtat.A_FAIRE.name(),
                        "type", type.name()));
        return vue(workspaceId, ticketId);
    }

    @Transactional
    @Auditable(action = "DEMARCHE_NON_APPLICABLE", resourceType = "ticket", resourceIdExpr = "#ticketId")
    public Vue marquerNonApplicable(UUID workspaceId, UUID ticketId, int ordre,
                                     String motif, UUID acteurId) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);
        Demarche d = demarcheDuStatutCourant(ticket, ordre);

        if (d.obligatoire()) {
            throw new ConflictException("La demarche " + d.ordre()
                    + " est obligatoire dans tous les dossiers : elle ne peut pas etre "
                    + "marquee non applicable.");
        }
        if (motif == null || motif.isBlank()) {
            throw new ValidationException("Motif obligatoire pour ecarter la demarche "
                    + d.ordre() + ". Condition du guide : "
                    + (d.conditionApplication() == null ? "non precisee" : d.conditionApplication()));
        }
        UUID ligneId = etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.NON_APPLICABLE,
                motif.trim(), acteurId, List.of());
        journal.enregistrer(workspaceId, ligneId, DemarcheEvenement.Type.HORS_PERIMETRE,
                motif.trim(), acteurId, 0);
        journaliser(workspaceId, ticketId, acteurId, d,
                "Demarche " + d.ordre() + " ecartee : " + motif.trim(),
                Map.of("ordre", d.ordre(), "etat", DemarcheEtat.NON_APPLICABLE.name()));
        return vue(workspaceId, ticketId);
    }

    /**
     * Lot 5 (2026-09-07) — UNE RÉPONSE, TROIS DÉMARCHES.
     *
     * <p>« La gérance est-elle désignée dans les statuts ? » se répond une fois, à
     * l'étape 5 du workflow (case « Gérant statutaire », par gérant). Le référentiel,
     * lui, porte la MÊME condition sur trois démarches : établir l'acte de nomination
     * (9), le faire signer et légaliser (15), l'enregistrer (18). Les écarter une par
     * une, avec un motif à retaper trois fois, c'est demander trois fois la même
     * chose — et prendre le risque qu'une des trois reste ouverte et bloque la
     * transition de statut.
     *
     * <p>Gérance statutaire → les trois deviennent NON_APPLICABLE, avec un motif qui
     * cite la condition du guide. Gérance non statutaire → elles redeviennent
     * À_FAIRE, mais UNIQUEMENT si elles portaient ce motif système : un écartement
     * décidé par l'employé, ou une démarche déjà cochée, n'est jamais défait.
     *
     * <p>Le contrôle « démarche du statut courant » ne s'applique pas ici : les trois
     * démarches relèvent de deux statuts différents, et la réponse est donnée bien
     * avant qu'on y arrive. C'est une propagation système, pas un geste d'employé.
     *
     * <p>Sans effet hors du workflow CRÉATION : les huit autres n'ont pas de
     * référentiel chargé, et aucune démarche n'est trouvée.
     */
    @Transactional
    @Auditable(action = "DEMARCHES_CONDITION_GERANCE", resourceType = "ticket",
            resourceIdExpr = "#ticketId")
    public Vue appliquerConditionGerance(UUID workspaceId, UUID ticketId,
                                          boolean geranceStatutaire, UUID acteurId) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);

        Map<UUID, TicketDemarche> parDemarche = etats.findByTicket(workspaceId, ticketId);
        for (int ordre : ORDRES_ACTE_NOMINATION) {
            Optional<Demarche> trouvee =
                    referentiel.findByWorkflowAndOrdre(ticket.type().name(), ordre);
            if (trouvee.isEmpty()) continue;
            Demarche d = trouvee.get();
            if (d.obligatoire()) continue; // garde-fou : on n'écarte jamais une obligatoire
            TicketDemarche courant = parDemarche.get(d.id());

            if (geranceStatutaire) {
                if (courant != null && courant.etat() == DemarcheEtat.COCHEE) continue;
                if (courant != null && courant.etat() == DemarcheEtat.NON_APPLICABLE
                        && MOTIF_GERANCE_STATUTAIRE.equals(courant.motif())) continue;
                UUID ligneId = etats.upsert(workspaceId, ticketId, d.id(),
                        DemarcheEtat.NON_APPLICABLE, MOTIF_GERANCE_STATUTAIRE, acteurId, List.of());
                journal.enregistrer(workspaceId, ligneId, DemarcheEvenement.Type.HORS_PERIMETRE,
                        MOTIF_GERANCE_STATUTAIRE, acteurId, 0);
                journaliser(workspaceId, ticketId, acteurId, d,
                        "Demarche " + d.ordre() + " ecartee automatiquement : "
                                + MOTIF_GERANCE_STATUTAIRE,
                        Map.of("ordre", d.ordre(), "etat", DemarcheEtat.NON_APPLICABLE.name(),
                                "origine", "CONDITION_GERANCE"));
            } else {
                // On ne défait QUE notre propre écartement.
                if (courant == null || courant.etat() != DemarcheEtat.NON_APPLICABLE) continue;
                if (!MOTIF_GERANCE_STATUTAIRE.equals(courant.motif())) continue;
                UUID ligneId = etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.A_FAIRE,
                        MOTIF_REPRISE_GERANCE, acteurId, List.of());
                journal.enregistrer(workspaceId, ligneId, DemarcheEvenement.Type.REPRISE,
                        MOTIF_REPRISE_GERANCE, acteurId, 0);
                journaliser(workspaceId, ticketId, acteurId, d,
                        "Demarche " + d.ordre() + " redevenue applicable : la gerance "
                                + "n'est pas designee dans les statuts",
                        Map.of("ordre", d.ordre(), "etat", DemarcheEtat.A_FAIRE.name(),
                                "origine", "CONDITION_GERANCE"));
            }
        }
        return vue(workspaceId, ticketId);
    }

    /**
     * Les démarches du parcours qui portent la condition « la gérance n'est pas
     * désignée dans les statuts ».
     *
     * <p><b>Lot B — les numéros changent avec le parcours du 9 septembre.</b>
     * L'ancien référentiel portait cette condition sur les étapes 9, 15 et 18.
     * Le nouveau la porte sur la ligne <b>5</b> (acte de nomination du ou des
     * gérants) et sur les lignes <b>21</b> et <b>22</b> (enregistrement de cet
     * acte — dépôt, puis retrait), qui énoncent « si acte de nomination non
     * statutaire ».
     *
     * <p>La ligne 14 (légalisation des signatures) n'y figure pas : le parcours
     * l'a élargie aux statuts et au pouvoir, elle vaut « tous dossiers ». Le
     * garde-fou {@code d.obligatoire()} l'écarterait de toute façon — on ne met
     * jamais une démarche obligatoire hors périmètre.
     */
    private static final int[] ORDRES_ACTE_NOMINATION = {5, 21, 22};

    /**
     * Motif de la reprise automatique, quand la réponse à « la gérance est-elle
     * désignée dans les statuts ? » change. Le journal exige un motif sur une
     * reprise comme sur une annulation : un geste du système se justifie autant
     * qu'un geste de l'employé.
     */
    private static final String MOTIF_REPRISE_GERANCE =
            "La gerance n'est pas designee dans les statuts : la demarche redevient applicable.";

    /**
     * Motif système. Sert AUSSI de signature : seul un écartement portant ce motif
     * exact est défait quand la réponse change.
     */
    static final String MOTIF_GERANCE_STATUTAIRE =
            "Gerance designee dans les statuts : l'acte de nomination separe n'a pas lieu "
                    + "d'etre (condition du guide : « Si la gerance n'est pas designee dans "
                    + "les statuts »). Reponse donnee a l'etape 5 du workflow.";

    // =================================================================
    //  Verifications
    // =================================================================

    private Ticket charger(UUID workspaceId, UUID ticketId) {
        return tickets.findById(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
    }

    private Demarche demarcheDuStatutCourant(Ticket ticket, int ordre) {
        Demarche d = referentiel.findByWorkflowAndOrdre(ticket.type().name(), ordre)
                .orElseThrow(() -> new NotFoundException(
                        "Aucune demarche " + ordre + " au referentiel du workflow " + ticket.type()));
        if (d.statutTicket() != ticket.statut()) {
            throw new ConflictException("La demarche " + ordre + " releve du statut "
                    + d.statutTicket() + " ; le ticket est au statut " + ticket.statut() + ".");
        }
        return d;
    }

    /**
     * Verifie que les justificatifs ATTENDUS ont bien ete televerses.
     *
     * <p>Trois controles, tous bloquants :
     * <ol>
     *   <li>chaque document cite existe, appartient au workspace ET au ticket ;</li>
     *   <li>chaque document cite correspond a un type attendu par la demarche —
     *       sans quoi n'importe quel fichier validerait n'importe quelle etape ;</li>
     *   <li>chaque groupe d'alternatives est couvert (le groupe 1 « bail OU
     *       domiciliation OU titre de propriete » ET le groupe 2 « attestation
     *       d'enregistrement », par exemple).</li>
     * </ol>
     */
    private List<TicketDemarcheRepository.JustificatifDepose> verifierJustificatifs(
            UUID workspaceId, UUID ticketId, Demarche d, List<UUID> documentIds) {

        List<UUID> ids = documentIds == null ? List.of() : documentIds;

        if (d.sansJustificatifDocumentaire()) {
            if (!ids.isEmpty()) {
                throw new ValidationException("La demarche " + d.ordre()
                        + " n'attend aucune piece a televerser ("
                        + d.justificatifsTexte() + ").");
            }
            return List.of();
        }
        if (ids.isEmpty()) {
            throw new ValidationException("La demarche " + d.ordre()
                    + " exige un justificatif : " + d.justificatifsTexte());
        }

        List<DataroomDocumentLookup.DocumentVu> vus = documents.findByIds(workspaceId, ids);
        if (vus.size() != ids.size()) {
            throw new ValidationException(
                    "Un justificatif cite n'existe pas dans la Data Room de ce cabinet.");
        }

        List<TicketDemarcheRepository.JustificatifDepose> deposes = new ArrayList<>();
        for (DataroomDocumentLookup.DocumentVu doc : vus) {
            if (doc.ticketId() == null || !doc.ticketId().equals(ticketId)) {
                throw new ValidationException("Le document « " + doc.title()
                        + " » n'est pas rattache a ce ticket : il ne peut pas justifier la demarche "
                        + d.ordre() + ".");
            }
            Optional<JustificatifAttendu> attendu = d.justificatifsAttendus().stream()
                    .filter(a -> a.documentType().equals(doc.documentType()))
                    .findFirst();
            if (attendu.isEmpty()) {
                throw new ValidationException("Le document « " + doc.title() + " » est de type "
                        + doc.documentType() + ", qui n'est pas attendu par la demarche "
                        + d.ordre() + " (attendu : " + typesAttendus(d) + ").");
            }
            deposes.add(new TicketDemarcheRepository.JustificatifDepose(
                    doc.id(), doc.documentType(), attendu.get().alternativeGroupe()));
        }

        for (Integer groupe : d.groupesAttendus()) {
            boolean couvert = deposes.stream().anyMatch(j -> j.alternativeGroupe() == groupe);
            if (!couvert) {
                String manquants = d.justificatifsAttendus().stream()
                        .filter(a -> a.alternativeGroupe() == groupe)
                        .map(JustificatifAttendu::documentType)
                        .reduce((a, b) -> a + " ou " + b).orElse("?");
                throw new ValidationException("Justificatif manquant pour la demarche "
                        + d.ordre() + " : " + manquants + ".");
            }
        }
        return deposes;
    }

    private static String typesAttendus(Demarche d) {
        return d.justificatifsAttendus().stream()
                .map(JustificatifAttendu::documentType)
                .distinct()
                .reduce((a, b) -> a + ", " + b).orElse("aucun");
    }

    private void journaliser(UUID workspaceId, UUID ticketId, UUID acteurId, Demarche d,
                              String contenu, Map<String, Object> metadata) {
        // Le CHECK de `ticket_comments.type` n'autorise que quatre valeurs : on
        // reste sur COMMENTAIRE et on porte le detail dans la metadata, plutot
        // que d'ajouter un type pour un evenement de tracabilite.
        Map<String, Object> meta = new LinkedHashMap<>(metadata);
        meta.put("demarcheId", d.id().toString());
        comments.create(workspaceId, ticketId, acteurId, TicketCommentType.COMMENTAIRE, contenu, meta);
    }
}
