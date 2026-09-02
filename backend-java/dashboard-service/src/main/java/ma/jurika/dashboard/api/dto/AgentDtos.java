package ma.jurika.dashboard.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lot IA-1 — DTOs de l'agent copilote (records immuables, miroir front
 * {@code types/agent.ts}).
 *
 * <p>Distinction importante :
 * <ul>
 *   <li>{@link AgentSignals} = signaux BRUTS agreges en SQL (toujours renseignes).</li>
 *   <li>{@link PlanItem} = plan du jour PRIORISE a base de regles (toujours present,
 *       meme sans LLM).</li>
 *   <li>{@code texteLlm} = narration optionnelle du LLM (null si indisponible).</li>
 * </ul>
 * L'agent PROPOSE, l'humain VALIDE : aucun de ces objets ne declenche d'action.
 */
public final class AgentDtos {

    private AgentDtos() {}

    // ---------------------------------------------------------------------
    // Signaux bruts
    // ---------------------------------------------------------------------

    /** Une echeance imminente (<= J+7) ou deja depassee sur un dossier de l'employe. */
    public record EcheanceSignal(
            String source,        // "DEADLINE" | "ALERTE_FISCALE"
            UUID refId,
            String intitule,
            LocalDate date,
            String severite,      // INFO | WARNING | CRITICAL
            boolean depassee,
            long joursRestants,   // negatif si depassee
            UUID dossierId,
            String dossierNom,
            String lien) {}

    /** Un reste-a-faire : ticket ouvert ou demande client non traitee. */
    public record TacheSignal(
            String type,          // "TICKET" | "DEMANDE"
            UUID refId,
            String reference,     // reference ticket (null pour une demande)
            String sujet,
            String statut,
            long ancienneteJours,
            UUID dossierId,
            String dossierNom,
            String lien) {}

    /** Compteurs de synthese (pour la notification + les badges front). */
    public record AgentCounts(
            int echeances,
            int echeancesDepassees,
            int tickets,
            int demandes) {}

    /** L'etat brut agrege pour un (workspace, employe). */
    public record AgentSignals(
            List<EcheanceSignal> echeances,
            List<TacheSignal> resteAFaire,
            AgentCounts counts) {}

    // ---------------------------------------------------------------------
    // Plan du jour (regles) + briefing complet
    // ---------------------------------------------------------------------

    /** Une action priorisee du "plan du jour" (deep-linkable cote front). */
    public record PlanItem(
            int ordre,
            String urgence,       // CRITIQUE | HAUTE | MOYENNE | BASSE
            String action,
            String justification,
            String type,          // TICKET | DEMANDE | ECHEANCE
            UUID targetId,
            UUID dossierId,
            String lien) {}

    /**
     * Le briefing complet renvoye au front / persiste (le {@code payload} JSONB
     * agrege {@code texteLlm} + {@code genereParIa} + {@code planDuJour} +
     * {@code signaux} ; {@code summary} est stocke a part).
     */
    public record AgentBriefing(
            UUID id,
            UUID workspaceId,
            UUID employeeId,
            String summary,
            String texteLlm,      // null si LLM indisponible -> plan a base de regles
            boolean genereParIa,
            List<PlanItem> planDuJour,
            AgentSignals signaux,
            Instant createdAt,
            Instant seenAt) {}    // null = non consulte

    /** Contenu serialise dans la colonne JSONB {@code payload}. */
    public record AgentBriefingPayload(
            String texteLlm,
            boolean genereParIa,
            List<PlanItem> planDuJour,
            AgentSignals signaux) {}
}
