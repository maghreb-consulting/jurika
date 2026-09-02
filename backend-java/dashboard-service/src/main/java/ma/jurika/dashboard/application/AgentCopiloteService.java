package ma.jurika.dashboard.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.dashboard.api.dto.AgentDtos.*;
import ma.jurika.dashboard.application.aggregator.AgentSignalsAggregator;
import ma.jurika.dashboard.infrastructure.ai.AiReasoningClient;
import ma.jurika.dashboard.infrastructure.persistence.AgentBriefingEntity;
import ma.jurika.dashboard.infrastructure.persistence.AgentBriefingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot IA-1 — Coeur de l'agent copilote : observe (SQL) -> raisonne (LLM) -> PROPOSE.
 *
 * <p><b>Sûreté (non négociable)</b> : l'agent ne modifie JAMAIS un ticket, une
 * demande, un dossier, et n'envoie aucun message automatique. Il ne produit que des
 * suggestions persistees dans {@code agent_briefings} + une notification.
 *
 * <p>Le plan du jour est TOUJOURS produit a base de regles (deterministe, utile
 * meme LLM absent). Le texte LLM n'est qu'une narration additionnelle, nullable.
 */
@Service
public class AgentCopiloteService {

    private static final Logger log = LoggerFactory.getLogger(AgentCopiloteService.class);

    private static final String SYSTEM_PROMPT = """
            Tu es JURIKA Copilote, l'assistant d'un juriste de cabinet marocain.
            Ton concis, professionnel, factuel, en francais. Tu ne fais que SUGGERER :
            jamais d'action irreversible, c'est l'humain qui valide.""";

    private static final String CONSIGNE = """
            Tu es un copilote d'un juriste. A partir de cet etat, produis (a) un PLAN DU
            JOUR priorise (3 a 7 actions, la plus urgente d'abord, avec justification
            courte), (b) les echeances a ne pas manquer. Sois concret, factuel,
            en francais. Ne propose aucune action
            irreversible ; tu ne fais que suggerer.""";

    // Rangs d'urgence (tri du plan du jour)
    private static final Map<String, Integer> URGENCE_RANK =
            Map.of("CRITIQUE", 0, "HAUTE", 1, "MOYENNE", 2, "BASSE", 3);
    private static final int MAX_PLAN_ITEMS = 7;
    private static final int DEMANDE_ANCIENNE_JOURS = 3;

    private final AgentSignalsAggregator aggregator;
    private final AiReasoningClient reasoningClient;
    private final AgentBriefingRepository repository;
    private final ObjectMapper mapper;

    public AgentCopiloteService(AgentSignalsAggregator aggregator,
                                AiReasoningClient reasoningClient,
                                AgentBriefingRepository repository,
                                ObjectMapper mapper) {
        this.aggregator = aggregator;
        this.reasoningClient = reasoningClient;
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Genere (et persiste) le briefing du jour d'un employe — regeneration COMPLETE :
     * re-agregation des signaux <b>et</b> nouvel appel LLM. C'est l'action explicite
     * "recalcule tout" derriere {@link #refresh} et le scheduler matinal.
     * Best-effort : une erreur LLM ou de persistance ne fait pas echouer l'appel.
     */
    public AgentBriefing generateBriefing(UUID workspaceId, UUID employeeId) {
        AgentSignals signals = aggregator.aggregate(workspaceId, employeeId);
        List<PlanItem> plan = buildPlan(signals);
        String summary = buildSummary(signals);
        String texteLlm = reason(signals).orElse(null);
        boolean genereParIa = texteLlm != null;

        AgentBriefingPayload payload = new AgentBriefingPayload(texteLlm, genereParIa, plan, signals);
        // Upsert : maj la ligne du jour si elle existe (id stable pour markSeen), sinon insere.
        AgentBriefingEntity saved = upsert(findTodayRow(workspaceId, employeeId).orElse(null),
                workspaceId, employeeId, summary, payload);

        return new AgentBriefing(saved.getId(), workspaceId, employeeId, summary,
                texteLlm, genereParIa, plan, signals, saved.getCreatedAt(), saved.getSeenAt());
    }

    /**
     * Renvoie le briefing du jour de l'employe en refletant l'<b>etat courant</b>.
     *
     * <p>Les signaux sont TOUJOURS relus en live ({@code aggregator.aggregate}) et le
     * plan/summary reconstruits : un item traite (ticket cloture, demande traitee,
     * echeance reglee) disparait immediatement, sans attendre le scheduler. La
     * narration LLM du jour est en revanche <b>reutilisee</b> si un briefing
     * d'aujourd'hui existe deja (1 appel LLM/jour max) ; sinon elle est generee une
     * fois puis persistee. Best-effort : une panne LLM/persistance ne casse pas l'appel.
     */
    public AgentBriefing getMyBriefing(UUID workspaceId, UUID employeeId) {
        // Signaux TOUJOURS frais -> le panneau reflete l'etat courant a chaque chargement.
        AgentSignals signals = aggregator.aggregate(workspaceId, employeeId);
        List<PlanItem> plan = buildPlan(signals);
        String summary = buildSummary(signals);

        Optional<AgentBriefingEntity> today = findTodayRow(workspaceId, employeeId);

        // Narration LLM : stable sur la journee (reutilisee), generee une seule fois.
        String texteLlm;
        boolean genereParIa;
        if (today.isPresent()) {
            AgentBriefingPayload prev = readPayload(today.get());
            texteLlm = prev.texteLlm();
            genereParIa = prev.genereParIa();
        } else {
            texteLlm = reason(signals).orElse(null);
            genereParIa = texteLlm != null;
        }

        AgentBriefingPayload payload = new AgentBriefingPayload(texteLlm, genereParIa, plan, signals);
        AgentBriefingEntity saved = upsert(today.orElse(null), workspaceId, employeeId, summary, payload);

        return new AgentBriefing(saved.getId(), workspaceId, employeeId, summary,
                texteLlm, genereParIa, plan, signals, saved.getCreatedAt(), saved.getSeenAt());
    }

    /** Regenere completement le briefing a la demande (re-agregation + nouveau LLM). */
    public AgentBriefing refresh(UUID workspaceId, UUID employeeId) {
        return generateBriefing(workspaceId, employeeId);
    }

    /**
     * Marque un briefing comme vu. Un employe ne peut toucher que SON briefing
     * (verification workspace + employee_id) -> sinon {@link Optional#empty()}.
     */
    public Optional<AgentBriefing> markSeen(UUID workspaceId, UUID employeeId, UUID briefingId) {
        Optional<AgentBriefingEntity> found = repository.findById(briefingId);
        if (found.isEmpty()) return Optional.empty();
        AgentBriefingEntity e = found.get();
        if (!e.getWorkspaceId().equals(workspaceId) || !e.getEmployeeId().equals(employeeId)) {
            return Optional.empty();
        }
        e.setSeenAt(Instant.now());
        return Optional.of(toDto(repository.save(e)));
    }

    private boolean isFromToday(Instant createdAt) {
        ZoneId zone = ZoneId.of("Africa/Casablanca");
        return createdAt.atZone(zone).toLocalDate().equals(LocalDate.now(zone));
    }

    /** La ligne du jour de l'employe (zone Casablanca), ou vide si absente/obsolete. */
    private Optional<AgentBriefingEntity> findTodayRow(UUID workspaceId, UUID employeeId) {
        return repository.findFirstByWorkspaceIdAndEmployeeIdOrderByCreatedAtDesc(workspaceId, employeeId)
                .filter(e -> isFromToday(e.getCreatedAt()));
    }

    private AgentBriefingPayload readPayload(AgentBriefingEntity e) {
        try {
            return mapper.readValue(e.getPayload(), AgentBriefingPayload.class);
        } catch (Exception ex) {
            log.warn("Deserialisation payload briefing {} echouee : {}", e.getId(), ex.getMessage());
            return new AgentBriefingPayload(null, false, List.of(),
                    new AgentSignals(List.of(), List.of(), new AgentCounts(0, 0, 0, 0)));
        }
    }

    private AgentBriefing toDto(AgentBriefingEntity e) {
        AgentBriefingPayload p = readPayload(e);
        return new AgentBriefing(e.getId(), e.getWorkspaceId(), e.getEmployeeId(), e.getSummary(),
                p.texteLlm(), p.genereParIa(), p.planDuJour(), p.signaux(),
                e.getCreatedAt(), e.getSeenAt());
    }

    // ---------------------------------------------------------------------
    // Raisonnement LLM (best-effort)
    // ---------------------------------------------------------------------
    private Optional<String> reason(AgentSignals signals) {
        try {
            String etat = mapper.writeValueAsString(signals);
            String userPrompt = "Etat courant (JSON) :\n" + etat + "\n\n" + CONSIGNE;
            return reasoningClient.reason(SYSTEM_PROMPT, userPrompt);
        } catch (Exception ex) {
            log.warn("Serialisation/raisonnement LLM echoue — fallback regles : {}", ex.getMessage());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------
    // Plan du jour a base de regles (deterministe : depasse > J-7 > demande
    // ancienne > ticket). Toujours present, meme sans LLM.
    // ---------------------------------------------------------------------
    private List<PlanItem> buildPlan(AgentSignals s) {
        record Scored(String urgence, long secondary, PlanItem item) {}
        List<Scored> scored = new ArrayList<>();

        for (EcheanceSignal e : s.echeances()) {
            String urgence = e.depassee() ? "CRITIQUE" : "HAUTE";
            String just = e.depassee()
                    ? "Echeance depassee de " + Math.abs(e.joursRestants()) + " j (" + e.dossierNom() + ")"
                    : "Echeance dans " + e.joursRestants() + " j (" + e.dossierNom() + ")";
            scored.add(new Scored(urgence, e.joursRestants(), new PlanItem(
                    0, urgence, "Traiter l'echeance : " + e.intitule(), just,
                    "ECHEANCE", e.refId(), e.dossierId(), e.lien())));
        }
        for (TacheSignal t : s.resteAFaire()) {
            boolean demande = "DEMANDE".equals(t.type());
            String urgence = demande
                    ? (t.ancienneteJours() > DEMANDE_ANCIENNE_JOURS ? "HAUTE" : "MOYENNE")
                    : "MOYENNE";
            String action = demande
                    ? "Repondre a la demande client : " + t.sujet()
                    : "Avancer le ticket " + (t.reference() == null ? "" : t.reference() + " ") + t.sujet();
            String just = (demande ? "Demande non traitee" : "Ticket " + t.statut())
                    + " depuis " + t.ancienneteJours() + " j (" + t.dossierNom() + ")";
            // secondary negatif => plus ancien = plus prioritaire dans un tri asc
            scored.add(new Scored(urgence, -t.ancienneteJours(), new PlanItem(
                    0, urgence, action, just, t.type(), t.refId(), t.dossierId(), t.lien())));
        }

        scored.sort(Comparator
                .comparingInt((Scored x) -> URGENCE_RANK.getOrDefault(x.urgence(), 9))
                .thenComparingLong(Scored::secondary));

        List<PlanItem> plan = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_PLAN_ITEMS, scored.size()); i++) {
            PlanItem it = scored.get(i).item();
            plan.add(new PlanItem(i + 1, it.urgence(), it.action(), it.justification(),
                    it.type(), it.targetId(), it.dossierId(), it.lien()));
        }
        return plan;
    }

    private String buildSummary(AgentSignals s) {
        AgentCounts c = s.counts();
        if (c.echeances() == 0 && c.tickets() == 0 && c.demandes() == 0) {
            return "Rien a signaler aujourd'hui — vos dossiers sont a jour.";
        }
        StringBuilder sb = new StringBuilder();
        if (c.echeances() > 0) {
            sb.append(c.echeances()).append(" echeance(s)");
            if (c.echeancesDepassees() > 0) sb.append(" (").append(c.echeancesDepassees()).append(" depassee(s))");
            sb.append(" · ");
        }
        if (c.tickets() > 0) sb.append(c.tickets()).append(" ticket(s) · ");
        if (c.demandes() > 0) sb.append(c.demandes()).append(" demande(s) · ");
        String out = sb.toString();
        return out.endsWith(" · ") ? out.substring(0, out.length() - 3) + " a traiter." : out + " a traiter.";
    }

    /**
     * Upsert du briefing du jour : met a jour la ligne existante (id/createdAt/seenAt
     * conserves) ou en insere une nouvelle. Best-effort : sur echec de persistance,
     * renvoie une entite transitoire pour que l'appel serve quand meme le briefing.
     */
    private AgentBriefingEntity upsert(AgentBriefingEntity existing, UUID workspaceId,
                                       UUID employeeId, String summary, AgentBriefingPayload payload) {
        AgentBriefingEntity e = existing != null ? existing : new AgentBriefingEntity();
        e.setWorkspaceId(workspaceId);
        e.setEmployeeId(employeeId);
        e.setSummary(summary);
        try {
            e.setPayload(mapper.writeValueAsString(payload));
            return repository.save(e);
        } catch (Exception ex) {
            log.warn("Persistance briefing echouee (ws={}, emp={}) : {}", workspaceId, employeeId, ex.getMessage());
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getCreatedAt() == null) e.setCreatedAt(Instant.now());
            return e; // le briefing reste servi en memoire pour cet appel
        }
    }
}
