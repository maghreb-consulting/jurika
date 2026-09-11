package ma.jurika.ticket.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Demarche;
import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.DemarcheEvenement;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.JustificatifAttendu;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DemarcheJournalRepository;
import ma.jurika.ticket.domain.port.DemarcheReferentielRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketDemarcheRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lot B (2026-09-11) — LE RÉCAPITULATIF DU TICKET, AVANT DE LE CLORE.
 *
 * <p>Le statut « Clôture de dossier » ne se contente pas d'un bouton : il montre
 * d'abord ce que le dossier contient. Quatre choses, et le parcours les nomme
 * (ligne 47) :
 *
 * <ul>
 *   <li>les documents produits et leur état ;</li>
 *   <li>les démarches accomplies, avec leurs dates ;</li>
 *   <li>les justificatifs archivés, <b>et ceux qui manquent</b> ;</li>
 *   <li>les identifiants obtenus : RC, IF, ICE, TP, CNSS.</li>
 * </ul>
 *
 * <p><b>D'où vient la liste des pièces attendues.</b> La ligne 47 du parcours les
 * énumère en toutes lettres — vingt-deux, dans une seule cellule. On ne parse pas
 * cette phrase : on lit {@code demarches_justificatifs}, qui est sa version
 * mécanisée, ligne par ligne, avec ses groupes d'alternatives. Le texte de la
 * ligne 47 est néanmoins rendu tel quel, pour que le cabinet puisse confronter
 * les deux.
 *
 * <p><b>Ce qui est exclu du décompte.</b> Une démarche explicitement écartée avec
 * motif n'attend aucun justificatif : réclamer la pièce d'une formalité qu'on a
 * décidé de ne pas faire serait un faux manque, et un faux manque fait qu'on
 * cesse de lire la liste.
 *
 * <p>Le service est en LECTURE SEULE. Clore reste une transition de statut,
 * décidée explicitement — ce récapitulatif l'éclaire, il ne la déclenche pas.
 */
@Service
public class RecapitulatifClotureService {

    /** La ligne du parcours qui porte l'énumération des pièces à archiver. */
    private static final int LIGNE_ARCHIVAGE = 47;

    private final TicketRepository tickets;
    private final DemarcheReferentielRepository referentiel;
    private final TicketDemarcheRepository etats;
    private final DemarcheJournalRepository journal;
    private final DataroomDocumentLookup documents;
    private final DossierRepository dossiers;

    public RecapitulatifClotureService(TicketRepository tickets,
                                        DemarcheReferentielRepository referentiel,
                                        TicketDemarcheRepository etats,
                                        DemarcheJournalRepository journal,
                                        DataroomDocumentLookup documents,
                                        DossierRepository dossiers) {
        this.tickets = tickets;
        this.referentiel = referentiel;
        this.etats = etats;
        this.journal = journal;
        this.documents = documents;
        this.dossiers = dossiers;
    }

    // =====================================================================
    //  Ce que le récapitulatif dit
    // =====================================================================

    /** Un document rattaché au ticket, tel qu'il est dans la Data Room. */
    public record DocumentProduit(UUID id, String documentType, String titre,
                                   boolean visibleClient) {}

    /**
     * Une démarche traitée, et quand.
     *
     * @param journal les gestes successifs — cochage, annulation, reprise. Un
     *                cochage annulé garde SES DEUX horodatages : le récapitulatif
     *                les montre tous les deux plutôt qu'un état lissé.
     */
    public record DemarcheAccomplie(int ordre, String libelle, DemarcheEtat etat,
                                     Instant cocheAt, String motif,
                                     List<DemarcheEvenement> journal) {}

    /**
     * Une pièce attendue par le référentiel.
     *
     * @param alternatives les types qui satisferaient le même groupe — « bail OU
     *                     domiciliation OU titre de propriété ». Un seul suffit ;
     *                     annoncer les trois comme manquantes serait faux.
     */
    public record JustificatifAttendu2(int ordreDemarche, String libelleDemarche,
                                        int groupe, List<String> alternatives,
                                        String libelle, boolean archive, UUID documentId) {}

    /** Les identifiants que la création fait obtenir, et qui alimentent la fiche société. */
    public record Identifiants(String rcNumero, String identifiantFiscal, String ice,
                                String taxeProfessionnelle, String cnss) {

        /** Combien des cinq sont renseignés. Le récapitulatif l'affiche tel quel. */
        public int obtenus() {
            int n = 0;
            for (String s : List.of(nz(rcNumero), nz(identifiantFiscal), nz(ice),
                    nz(taxeProfessionnelle), nz(cnss))) {
                if (!s.isBlank()) n++;
            }
            return n;
        }

        private static String nz(String s) {
            return s == null ? "" : s;
        }
    }

    public record Recapitulatif(UUID ticketId, String reference, TicketStatut statut,
                                 boolean clotureEnvisageable,
                                 List<DocumentProduit> documents,
                                 List<DemarcheAccomplie> demarches,
                                 List<JustificatifAttendu2> justificatifs,
                                 List<String> manquants,
                                 /** La cellule « Justificatif » de la ligne 47, verbatim. */
                                 String piecesSelonLeParcours,
                                 Identifiants identifiants) {}

    // =====================================================================

    @Transactional(readOnly = true)
    public Recapitulatif recapitulatif(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        Ticket ticket = tickets.findById(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));

        List<Demarche> toutes = referentiel.findByWorkflow(ticket.type().name());
        Map<UUID, TicketDemarche> parDemarche = etats.findByTicket(workspaceId, ticketId);
        Map<UUID, List<DemarcheEvenement>> journaux = journal.parDemarche(workspaceId,
                parDemarche.values().stream().map(TicketDemarche::id)
                        .filter(java.util.Objects::nonNull).toList());

        // --- Les documents produits -------------------------------------
        List<DocumentProduit> produits = documents.findByTicket(workspaceId, ticketId).stream()
                .map(d -> new DocumentProduit(d.id(), d.documentType(), d.title(),
                        d.visibleClient()))
                .toList();
        // Les types réellement présents : c'est par eux que se lit ce qui manque.
        Set<String> typesPresents = new LinkedHashSet<>();
        Map<String, UUID> premierParType = new LinkedHashMap<>();
        for (DocumentProduit d : produits) {
            typesPresents.add(d.documentType());
            premierParType.putIfAbsent(d.documentType(), d.id());
        }

        // --- Les démarches accomplies ------------------------------------
        List<DemarcheAccomplie> accomplies = new ArrayList<>();
        List<JustificatifAttendu2> attendus = new ArrayList<>();
        List<String> manquants = new ArrayList<>();
        String texteLigne47 = null;

        for (Demarche d : toutes) {
            if (d.ordre() == LIGNE_ARCHIVAGE) texteLigne47 = d.justificatifsTexte();

            TicketDemarche etat = parDemarche.get(d.id());
            DemarcheEtat e = etat == null ? DemarcheEtat.A_FAIRE : etat.etat();
            if (e != DemarcheEtat.A_FAIRE) {
                accomplies.add(new DemarcheAccomplie(d.ordre(), d.libelle(), e,
                        etat.cocheAt(), etat.motif(),
                        etat.id() == null ? List.of()
                                : journaux.getOrDefault(etat.id(), List.of())));
            }

            // Une démarche écartée avec motif n'attend rien : ce n'est pas un manque.
            if (e == DemarcheEtat.NON_APPLICABLE) continue;
            if (d.sansJustificatifDocumentaire()) continue;

            // Un groupe d'alternatives est satisfait dès qu'UN de ses types est là.
            Map<Integer, List<JustificatifAttendu>> parGroupe = new LinkedHashMap<>();
            for (JustificatifAttendu j : d.justificatifsAttendus()) {
                parGroupe.computeIfAbsent(j.alternativeGroupe(), k -> new ArrayList<>()).add(j);
            }
            for (Map.Entry<Integer, List<JustificatifAttendu>> g : parGroupe.entrySet()) {
                List<String> types = g.getValue().stream()
                        .map(JustificatifAttendu::documentType).toList();
                String libelle = g.getValue().get(0).libelle();
                String trouve = types.stream().filter(typesPresents::contains).findFirst()
                        .orElse(null);
                boolean archive = trouve != null;
                attendus.add(new JustificatifAttendu2(d.ordre(), d.libelle(), g.getKey(),
                        types, libelle, archive, archive ? premierParType.get(trouve) : null));
                if (!archive) {
                    manquants.add("Ligne " + d.ordre() + " — " + d.libelle() + " : "
                            + (libelle == null || libelle.isBlank()
                               ? String.join(" ou ", types) : libelle)
                            + " (" + String.join(" ou ", types) + ")");
                }
            }
        }

        // --- Les identifiants --------------------------------------------
        Identifiants identifiants = dossiers
                .findById(workspaceId, ticket.dossierId())
                .map(RecapitulatifClotureService::identifiants)
                .orElse(new Identifiants(null, null, null, null, null));

        return new Recapitulatif(ticket.id(), ticket.reference(), ticket.statut(),
                manquants.isEmpty(), produits, accomplies, attendus, manquants,
                texteLigne47, identifiants);
    }

    private static Identifiants identifiants(EntrepriseDossier d) {
        return new Identifiants(d.rcNumero(), d.identifiantFiscal(), d.ice(),
                d.taxeProfessionnelle(), d.cnss());
    }
}
