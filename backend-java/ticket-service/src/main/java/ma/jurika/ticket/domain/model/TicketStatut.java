package ma.jurika.ticket.domain.model;

import java.util.Set;

/**
 * Les cinq statuts du cycle de vie d'un ticket, tels que le cabinet les definit.
 *
 * <p><b>Lot B (2026-09-11)</b> — les plages de lignes changent avec le parcours
 * du 9 septembre (« 1. Parcours creation.xlsx », 51 lignes). Le CODE de chaque
 * statut, lui, ne change pas : il est porte par la base, par l'API et par neuf
 * autres workflows. Seul le LIBELLE du premier est refait, cote affichage.
 *
 * <pre>
 *   1 CREATION_TICKET       ligne  1        ouverture et collecte d'information
 *   2 GENERATION_DOCUMENTS  lignes 2 a 12   les dix documents, puis le controle
 *   3 DEROULEMENT_DEMARCHE  lignes 13 a 46  signature -> depots/retraits -> remise
 *   4 CLOTURE_DOSSIER       lignes 47 et 48 archivage, puis cloture
 *   5 ANNULE                lignes 49 a 51  sortie laterale
 * </pre>
 *
 * <p>Le certificat negatif, le controle d'identite des associes et le rapport du
 * commissaire aux apports ne sont plus des etapes : ce sont des CONTROLES
 * BLOQUANTS executes au lancement de la generation des statuts (§ 18 du
 * dictionnaire des variables).
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
