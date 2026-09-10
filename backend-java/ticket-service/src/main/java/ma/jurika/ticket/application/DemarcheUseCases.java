package ma.jurika.ticket.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.JustificatifAttendu;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketCommentType;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
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

    public DemarcheUseCases(TicketRepository tickets, DemarcheReferentielRepository referentiel,
                             TicketDemarcheRepository etats, DataroomDocumentLookup documents,
                             CommentRepository comments) {
        this.tickets = tickets;
        this.referentiel = referentiel;
        this.etats = etats;
        this.documents = documents;
        this.comments = comments;
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
                         * true si le guide permet de CALCULER une echeance pour cette
                         * etape. Les etapes 16, 19 et 26 portent un delai chiffre mais
                         * aucun point de depart mecanisable : l interface doit le dire,
                         * et surtout ne jamais fabriquer de date.
                         */
                        boolean delaiCalculable,
                        List<JustificatifAttendu> justificatifsAttendus,
                        DemarcheEtat etat, String motif, Instant cocheAt, List<UUID> documentsDeposes,
                        boolean actionnableMaintenant, boolean horsSequence) {}

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
                    false));
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
                avancement(ticket, toutes, lignes));
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
                    traitee && trou));
            if (!traitee) trouAvant.put(l.phaseCode(), true);
        }
        return out;
    }

    private Avancement avancement(Ticket ticket, List<Demarche> toutes, List<Ligne> lignes) {
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
                pointsAttention(toutes, lignes));
    }

    /**
     * Echeances legales calculables. Une demarche n'entre dans la liste que si
     * son point de depart est POSE (l'etape de reference est cochee) et qu'elle
     * n'est pas elle-meme traitee. Les etapes dont le guide ne permet pas de
     * calculer un point de depart n'y figurent jamais : mieux vaut aucune alerte
     * qu'une fausse.
     */
    private List<PointAttention> pointsAttention(List<Demarche> toutes, List<Ligne> lignes) {
        Map<Integer, Ligne> parOrdre = new LinkedHashMap<>();
        for (Ligne l : lignes) parOrdre.put(l.ordre(), l);

        List<PointAttention> out = new ArrayList<>();
        LocalDate aujourdhui = EcheanceCalculator.aujourdhui();
        for (Demarche d : toutes) {
            if (!d.delaiCalculable()) continue;
            Ligne cible = parOrdre.get(d.ordre());
            if (cible == null || cible.etat() != DemarcheEtat.A_FAIRE) continue;

            Ligne reference = parOrdre.get(d.delaiReferenceOrdre());
            if (reference == null || reference.cocheAt() == null
                    || reference.etat() != DemarcheEtat.COCHEE) {
                continue; // le delai n'a pas commence a courir
            }
            LocalDate echeance = EcheanceCalculator.echeance(
                    reference.cocheAt(), d.delaiValeur(), d.delaiUnite());
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

        etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.COCHEE, null, acteurId, deposes);
        journaliser(workspaceId, ticketId, acteurId, d,
                "Demarche " + d.ordre() + " cochee : " + d.libelle(),
                Map.of("ordre", d.ordre(), "etat", DemarcheEtat.COCHEE.name(),
                        "justificatifs", deposes.size()));
        return vue(workspaceId, ticketId);
    }

    @Transactional
    @Auditable(action = "DEMARCHE_DECOCHEE", resourceType = "ticket", resourceIdExpr = "#ticketId")
    public Vue decocher(UUID workspaceId, UUID ticketId, int ordre, UUID acteurId) {
        TenantContext.set(workspaceId);
        Ticket ticket = charger(workspaceId, ticketId);
        // Le decochage n'est possible que tant que le ticket n'a pas quitte le
        // statut dont releve la demarche.
        Demarche d = demarcheDuStatutCourant(ticket, ordre);

        etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.A_FAIRE, null, acteurId, List.of());
        journaliser(workspaceId, ticketId, acteurId, d,
                "Demarche " + d.ordre() + " decochee : " + d.libelle(),
                Map.of("ordre", d.ordre(), "etat", DemarcheEtat.A_FAIRE.name()));
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
        etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.NON_APPLICABLE,
                motif.trim(), acteurId, List.of());
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
                etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.NON_APPLICABLE,
                        MOTIF_GERANCE_STATUTAIRE, acteurId, List.of());
                journaliser(workspaceId, ticketId, acteurId, d,
                        "Demarche " + d.ordre() + " ecartee automatiquement : "
                                + MOTIF_GERANCE_STATUTAIRE,
                        Map.of("ordre", d.ordre(), "etat", DemarcheEtat.NON_APPLICABLE.name(),
                                "origine", "CONDITION_GERANCE"));
            } else {
                // On ne défait QUE notre propre écartement.
                if (courant == null || courant.etat() != DemarcheEtat.NON_APPLICABLE) continue;
                if (!MOTIF_GERANCE_STATUTAIRE.equals(courant.motif())) continue;
                etats.upsert(workspaceId, ticketId, d.id(), DemarcheEtat.A_FAIRE,
                        null, acteurId, List.of());
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
     * Les trois démarches du référentiel CRÉATION qui portent la condition « acte de
     * nomination non statutaire » : établissement (9), signature et légalisation (15),
     * enregistrement (18).
     */
    private static final int[] ORDRES_ACTE_NOMINATION = {9, 15, 18};

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
