package ma.jurika.ticket.domain.model;

import java.util.Set;

public enum TicketStatut {
    NOUVEAU,
    EN_COURS,
    CLOTURE,
    ANNULE;

    // 2026-06-25 — Reprise/annulation generalisees : plus aucun statut n'est
    // definitivement terminal. Tout ticket peut etre ANNULE (y compris CLOTURE),
    // et tout ticket ANNULE peut etre REPRIS (EN_COURS) ou CLOTURE. Les
    // transitions sensibles (source ANNULE + CLOTURE->ANNULE) exigent un motif
    // (cf. handlers domain/state).
    private static final java.util.Map<TicketStatut, Set<TicketStatut>> ALLOWED = java.util.Map.of(
            NOUVEAU, Set.of(EN_COURS, ANNULE),
            EN_COURS, Set.of(CLOTURE, ANNULE),
            CLOTURE, Set.of(ANNULE),
            ANNULE, Set.of(EN_COURS, CLOTURE)
    );

    public boolean canTransitionTo(TicketStatut target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Un statut est terminal si plus aucune transition n'en sort. Depuis la
     * generalisation reprise/annulation, plus aucun statut ne l'est (CLOTURE et
     * ANNULE peuvent etre quittes) : la methode derive desormais de {@link #ALLOWED}
     * pour rester exacte au lieu de coder en dur CLOTURE/ANNULE.
     */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
