package ma.jurika.ticket.domain.model;

import java.util.Set;

/**
 * Les cinq statuts du cycle de vie d'un ticket, tels que definis par le guide du
 * cabinet (GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx, onglet
 * « 2. Workflow ticket »).
 *
 * <pre>
 *   1 CREATION_TICKET       etapes 1 a 3    qualification, KYC, forme sociale
 *   2 GENERATION_DOCUMENTS  etapes 4 a 12   certificat negatif, siege, actes
 *   3 DEROULEMENT_DEMARCHE  etapes 13 a 33  signature -> publications -> CNSS
 *   4 CLOTURE_DOSSIER       etapes 34 a 36  completude, remise, fiche societe
 *   5 ANNULE                sortie laterale
 * </pre>
 *
 * <p>ANNULE n'est pas la fin du parcours mais une SORTIE LATERALE : il est
 * atteignable depuis n'importe quel statut, y compris CLOTURE_DOSSIER.
 *
 * <p>La REPRISE d'un ticket annule (ANNULE -> l'un des quatre autres) est
 * conservee : elle existe dans le produit depuis 2026-06-25 et le guide, qui
 * decrit le parcours nominal, ne la contredit pas. Elle exige un motif
 * (cf. handlers de {@code domain.state}).
 */
public enum TicketStatut {
    CREATION_TICKET,
    GENERATION_DOCUMENTS,
    DEROULEMENT_DEMARCHE,
    CLOTURE_DOSSIER,
    ANNULE;

    private static final java.util.Map<TicketStatut, Set<TicketStatut>> ALLOWED = java.util.Map.of(
            CREATION_TICKET, Set.of(GENERATION_DOCUMENTS, ANNULE),
            GENERATION_DOCUMENTS, Set.of(DEROULEMENT_DEMARCHE, ANNULE),
            DEROULEMENT_DEMARCHE, Set.of(CLOTURE_DOSSIER, ANNULE),
            CLOTURE_DOSSIER, Set.of(ANNULE),
            ANNULE, Set.of(CREATION_TICKET, GENERATION_DOCUMENTS,
                           DEROULEMENT_DEMARCHE, CLOTURE_DOSSIER)
    );

    /** Ordre d'affichage dans la progression (ANNULE est hors parcours : -1). */
    public int position() {
        return this == ANNULE ? -1 : ordinal() + 1;
    }

    /** Les quatre statuts du parcours nominal, dans l'ordre. */
    public static java.util.List<TicketStatut> parcours() {
        return java.util.List.of(CREATION_TICKET, GENERATION_DOCUMENTS,
                                 DEROULEMENT_DEMARCHE, CLOTURE_DOSSIER);
    }

    public boolean canTransitionTo(TicketStatut target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Un statut est terminal si plus aucune transition n'en sort. Aucun ne l'est :
     * CLOTURE_DOSSIER peut encore etre annule, et ANNULE peut etre repris. La
     * methode derive de {@link #ALLOWED} pour rester exacte plutot que de coder
     * en dur une liste qui se desynchroniserait.
     */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
