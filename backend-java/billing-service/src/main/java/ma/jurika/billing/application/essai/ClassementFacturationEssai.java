package ma.jurika.billing.application.essai;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lot L7 (E14) : sort de chaque table de jurika_billing qui porte un cabinet ({@code workspace_id}),
 * ou qui est liee a une telle table, quand un essai est supprime definitivement (RG-ESS-10).
 *
 * <p>Decision de l'utilisateur du 2026-10-14 : les paiements non aboutis sont supprimes ; les
 * paiements aboutis et les factures sont conserves 10 ans (obligation comptable), puis purges.
 * Les preuves du contrat et le journal des impayes (inalterables) sont conserves, duree A_DECIDER.
 *
 * <p>Ce classement est celui de la fonction {@code essai_facturation_supprimer} (billing V18) ;
 * la garde {@code SuppressionEssaiFacturationIT} echoue si une table de la base portant un cabinet
 * n'y figure pas, ou si la fonction et ce classement divergent. Le service le controle aussi a
 * chaque appel (une table rendue par la fonction et absente d'ici annule la suppression).
 */
public final class ClassementFacturationEssai {

    public enum Sort {
        /** Supprimee avec l'essai. */
        SUPPRIMEE,
        /** Piece comptable : conservee 10 ans apres la suppression, puis purgee. */
        CONSERVEE_10_ANS,
        /** Preuve inalterable : conservee ; duree A_DECIDER (directeur). */
        CONSERVEE_DUREE_A_DECIDER,
        /** Registre de la conservation (identifiant du cabinet, dates, nombres). */
        TRACE
    }

    public record Regle(Set<Sort> sorts, String raison) {}

    public static final Map<String, Regle> TABLES = Map.ofEntries(
            Map.entry("payments", new Regle(EnumSet.of(Sort.SUPPRIMEE, Sort.CONSERVEE_10_ANS),
                    "PENDING, FAILED, CANCELLED (non aboutis) supprimes ; COMPLETED (aboutis) conserves 10 ans")),
            Map.entry("cmi_retours", new Regle(EnumSet.of(Sort.SUPPRIMEE, Sort.CONSERVEE_10_ANS),
                    "sans workspace_id : suivent leur paiement (payment_id, ou oid quand le lien est absent)")),
            Map.entry("factures", new Regle(EnumSet.of(Sort.CONSERVEE_10_ANS),
                    "piece comptable : mentions figees et PDF conserves dans la ligne")),
            Map.entry("invoices", new Regle(EnumSet.of(Sort.CONSERVEE_10_ANS),
                    "factures de l'ancien Stripe (historique) : piece comptable")),
            Map.entry("subscriptions", new Regle(EnumSet.of(Sort.SUPPRIMEE),
                    "ni piece comptable ni preuve du contrat (refus si un abonnement est actif)")),
            Map.entry("payment_methods", new Regle(EnumSet.of(Sort.SUPPRIMEE),
                    "references de carte de l'ancien Stripe : ni piece comptable ni preuve")),
            Map.entry("conditions_particulieres", new Regle(EnumSet.of(Sort.CONSERVEE_DUREE_A_DECIDER),
                    "preuve du contrat, suppression refusee par declencheur (V13)")),
            Map.entry("contrat_acceptations", new Regle(EnumSet.of(Sort.CONSERVEE_DUREE_A_DECIDER),
                    "preuve de l'acceptation, inalterable (V13)")),
            Map.entry("contrat_resiliations", new Regle(EnumSet.of(Sort.CONSERVEE_DUREE_A_DECIDER),
                    "historique du contrat, inalterable (V13)")),
            Map.entry("contrat_renouvellements", new Regle(EnumSet.of(Sort.CONSERVEE_DUREE_A_DECIDER),
                    "historique du contrat, inalterable (V13)")),
            Map.entry("contrat_avis", new Regle(EnumSet.of(Sort.CONSERVEE_DUREE_A_DECIDER),
                    "journal des avis du contrat, inalterable (V13)")),
            Map.entry("contrat_reponses_versions", new Regle(EnumSet.of(Sort.CONSERVEE_DUREE_A_DECIDER),
                    "reponses aux nouvelles versions, inalterables (V16)")),
            Map.entry("impaye_journal", new Regle(EnumSet.of(Sort.CONSERVEE_DUREE_A_DECIDER),
                    "sequence d'une echeance impayee, inalterable (V15)")),
            Map.entry("conservation_comptable", new Regle(EnumSet.of(Sort.TRACE),
                    "registre de la conservation et de la purge (V18)")));

    private ClassementFacturationEssai() {
    }
}
