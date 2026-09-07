package ma.jurika.dashboard.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.jurika.dashboard.api.dto.AgentDtos.*;
import ma.jurika.dashboard.application.aggregator.AgentSignalsAggregator;
import ma.jurika.dashboard.infrastructure.ai.AiReasoningClient;
import ma.jurika.dashboard.infrastructure.persistence.AgentBriefingEntity;
import ma.jurika.dashboard.infrastructure.persistence.AgentBriefingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lot IA-1 — Tests unitaires du coeur de l'agent copilote (sans DB/LLM reel).
 *
 * <p>Couvre : fallback sans LLM produit un briefing priorise ; {@code getMyBriefing}
 * relit TOUJOURS les signaux en live (un item traite disparait immediatement) tout
 * en reutilisant la narration LLM du jour (1 appel LLM/jour max) ; {@code refresh}
 * regenere completement (nouvel appel LLM) ; un employe ne lit pas le briefing d'un autre.
 */
class AgentCopiloteServiceTest {

    private AgentSignalsAggregator aggregator;
    private AiReasoningClient reasoning;
    private AgentBriefingRepository repository;
    private AgentCopiloteService service;

    private final UUID ws = UUID.randomUUID();
    private final UUID emp = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aggregator = mock(AgentSignalsAggregator.class);
        reasoning = mock(AiReasoningClient.class);
        repository = mock(AgentBriefingRepository.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new AgentCopiloteService(aggregator, reasoning, repository, mapper);

        when(aggregator.aggregate(ws, emp)).thenReturn(sampleSignals());
        // save renvoie l'entite avec un id genere (comme la vraie couche JPA)
        when(repository.save(any(AgentBriefingEntity.class))).thenAnswer(inv -> {
            AgentBriefingEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getCreatedAt() == null) e.setCreatedAt(Instant.now());
            return e;
        });
    }

    @Test
    void fallbackSansLlmProduitUnBriefingPriorise() {
        when(reasoning.reason(any(), any())).thenReturn(Optional.empty());

        AgentBriefing b = service.generateBriefing(ws, emp);

        assertThat(b.genereParIa()).isFalse();
        assertThat(b.texteLlm()).isNull();
        // Le plan du jour reste utile meme sans IA.
        assertThat(b.planDuJour()).isNotEmpty();
        // L'echeance depassee doit passer en tete (urgence CRITIQUE).
        assertThat(b.planDuJour().get(0).urgence()).isEqualTo("CRITIQUE");
        assertThat(b.planDuJour().get(0).ordre()).isEqualTo(1);
        // Les signaux bruts sont toujours renseignes.
        assertThat(b.signaux().counts().echeances()).isEqualTo(2);
        assertThat(b.signaux().counts().echeancesDepassees()).isEqualTo(1);
        assertThat(b.summary()).contains("echeance");
        verify(repository).save(any(AgentBriefingEntity.class));
    }

    @Test
    void avecLlmRenseigneLeTexte() {
        when(reasoning.reason(any(), any())).thenReturn(Optional.of("Plan du jour : ..."));

        AgentBriefing b = service.generateBriefing(ws, emp);

        assertThat(b.genereParIa()).isTrue();
        assertThat(b.texteLlm()).isEqualTo("Plan du jour : ...");
        // Les listes structurees restent presentes EN PLUS du texte LLM.
        assertThat(b.planDuJour()).isNotEmpty();
    }

    /**
     * Coeur du fix : deux chargements successifs. Entre les deux, un ticket a ete
     * cloture (l'aggregator relit moins d'items). Le 2e {@code getMyBriefing} doit
     * refleter l'etat FRAIS (ticket disparu) tout en REUTILISANT la narration LLM du
     * jour — donc UN SEUL appel LLM sur les deux chargements.
     */
    @Test
    void getMyBriefingRelitLesSignauxEnLiveEtReutiliseLaNarrationDuJour() {
        // Etat qui evolue : 1er appel voit le ticket, 2e appel ne le voit plus.
        when(aggregator.aggregate(ws, emp)).thenReturn(sampleSignals(), reducedSignals());
        when(reasoning.reason(any(), any())).thenReturn(Optional.of("Narration stable"));

        // Repo stateful : findFirst renvoie la derniere ligne sauvegardee.
        final AgentBriefingEntity[] holder = new AgentBriefingEntity[1];
        when(repository.save(any(AgentBriefingEntity.class))).thenAnswer(inv -> {
            AgentBriefingEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getCreatedAt() == null) e.setCreatedAt(Instant.now());
            holder[0] = e;
            return e;
        });
        when(repository.findFirstByWorkspaceIdAndEmployeeIdOrderByCreatedAtDesc(ws, emp))
                .thenAnswer(inv -> Optional.ofNullable(holder[0]));

        AgentBriefing first = service.getMyBriefing(ws, emp);   // cree la ligne + narration
        AgentBriefing second = service.getMyBriefing(ws, emp);  // signaux frais, narration reutilisee

        // Narration LLM du jour : identique et stable.
        assertThat(first.texteLlm()).isEqualTo("Narration stable");
        assertThat(second.texteLlm()).isEqualTo("Narration stable");
        assertThat(second.genereParIa()).isTrue();
        // Signaux relus live : le ticket traite a disparu au 2e chargement.
        assertThat(first.signaux().counts().tickets()).isEqualTo(1);
        assertThat(second.signaux().counts().tickets()).isZero();
        assertThat(second.planDuJour()).noneMatch(p -> "TICKET".equals(p.type()));
        // UN SEUL appel LLM sur les deux chargements.
        verify(reasoning, times(1)).reason(any(), any());
        // Meme ligne mise a jour (id stable -> markSeen inchange).
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void getMyBriefingSansBriefingDuJourGenereLaNarrationUneFois() {
        when(repository.findFirstByWorkspaceIdAndEmployeeIdOrderByCreatedAtDesc(ws, emp))
                .thenReturn(Optional.empty());
        when(reasoning.reason(any(), any())).thenReturn(Optional.of("Premiere narration"));

        AgentBriefing b = service.getMyBriefing(ws, emp);

        verify(aggregator).aggregate(ws, emp);        // signaux relus live
        verify(reasoning).reason(any(), any());        // narration generee une fois
        assertThat(b.texteLlm()).isEqualTo("Premiere narration");
        verify(repository).save(any(AgentBriefingEntity.class));
    }

    @Test
    void refreshRegenereCompletementEtRappelleLeLlm() {
        when(reasoning.reason(any(), any())).thenReturn(Optional.of("Nouvelle narration"));

        AgentBriefing b = service.refresh(ws, emp);

        verify(aggregator).aggregate(ws, emp);
        verify(reasoning).reason(any(), any()); // recalcule tout, y compris le LLM
        assertThat(b.texteLlm()).isEqualTo("Nouvelle narration");
        assertThat(b.genereParIa()).isTrue();
    }

    @Test
    void unEmployeNePeutPasMarquerVuLeBriefingDunAutre() throws Exception {
        AgentBriefingEntity autre = persistedToday();
        autre.setEmployeeId(UUID.randomUUID()); // appartient a un AUTRE employe
        when(repository.findById(autre.getId())).thenReturn(Optional.of(autre));

        Optional<AgentBriefing> res = service.markSeen(ws, emp, autre.getId());

        assertThat(res).isEmpty();
        verify(repository, never()).save(any());
    }

    // ------------------------------------------------------------------
    private AgentBriefingEntity persistedToday() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AgentBriefingEntity e = new AgentBriefingEntity();
        e.setId(UUID.randomUUID());
        e.setWorkspaceId(ws);
        e.setEmployeeId(emp);
        e.setSummary("resume");
        e.setPayload(mapper.writeValueAsString(
                new AgentBriefingPayload(null, false, List.of(), sampleSignals())));
        e.setCreatedAt(Instant.now());
        return e;
    }

    private AgentSignals sampleSignals() {
        EcheanceSignal depassee = new EcheanceSignal(
                "DEADLINE", UUID.randomUUID(), "Depot RC", LocalDate.now().minusDays(2),
                "CRITICAL", true, -2, UUID.randomUUID(), "SARL Alpha", "/workflows/x");
        EcheanceSignal proche = new EcheanceSignal(
                "ALERTE_FISCALE", UUID.randomUUID(), "TVA", LocalDate.now().plusDays(5),
                "INFO", false, 5, UUID.randomUUID(), "SARL Beta", "/data-rooms?dossier=y");
        TacheSignal ticket = new TacheSignal(
                "TICKET", UUID.randomUUID(), "T-010", "Modif statuts", "GENERATION_DOCUMENTS", 4,
                UUID.randomUUID(), "SARL Alpha", "/workflows/z");
        TacheSignal demande = new TacheSignal(
                "DEMANDE", UUID.randomUUID(), null, "Question TVA", "NON_TRAITEE", 6,
                UUID.randomUUID(), "SARL Beta", "/data-rooms?dossier=y&tab=demandes");
        AgentCounts counts = new AgentCounts(2, 1, 1, 1);
        return new AgentSignals(List.of(depassee, proche), List.of(ticket, demande), counts);
    }

    /** Meme etat que {@link #sampleSignals()} mais le ticket a ete cloture (retire). */
    private AgentSignals reducedSignals() {
        EcheanceSignal depassee = new EcheanceSignal(
                "DEADLINE", UUID.randomUUID(), "Depot RC", LocalDate.now().minusDays(2),
                "CRITICAL", true, -2, UUID.randomUUID(), "SARL Alpha", "/workflows/x");
        EcheanceSignal proche = new EcheanceSignal(
                "ALERTE_FISCALE", UUID.randomUUID(), "TVA", LocalDate.now().plusDays(5),
                "INFO", false, 5, UUID.randomUUID(), "SARL Beta", "/data-rooms?dossier=y");
        TacheSignal demande = new TacheSignal(
                "DEMANDE", UUID.randomUUID(), null, "Question TVA", "NON_TRAITEE", 6,
                UUID.randomUUID(), "SARL Beta", "/data-rooms?dossier=y&tab=demandes");
        AgentCounts counts = new AgentCounts(2, 1, 0, 1);
        return new AgentSignals(List.of(depassee, proche), List.of(demande), counts);
    }
}
