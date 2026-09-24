package ma.jurika.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowStatut;
import ma.jurika.workflow.domain.model.WorkflowType;
import ma.jurika.workflow.domain.port.WorkflowProgressRepository;
import ma.jurika.workflow.domain.strategy.StepContext;
import ma.jurika.workflow.domain.strategy.StepResult;
import ma.jurika.workflow.domain.strategy.WorkflowOrchestrator;
import ma.jurika.workflow.domain.strategy.WorkflowStrategy;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowUseCases {

    private static final Logger log = LoggerFactory.getLogger(WorkflowUseCases.class);

    /** Sérialisation de la fiche_structuree JSONB (backbone refonte statuts 2026-06-24). */
    private static final ObjectMapper FICHE_MAPPER = new ObjectMapper();

    private final WorkflowProgressRepository progressRepository;
    private final WorkflowOrchestrator orchestrator;
    private final WorkflowProgressLookup progressLookup;
    /** Sprint Beta (pricing-deploy) — TASK 3. Optionnel : bean absent si
     * {@code jurika.plan-limits.enabled=false} (tests d'integration). */
    private final PlanLimitsService planLimitsService;
    /** Fix 2026-07-19 — isole l'INSERT du dossier de fin de workflow CREATION
     * dans une transaction {@code REQUIRES_NEW} (cf. {@link WorkflowFinalizationService}).
     * Optionnel : {@code null} dans les tests unitaires qui n'exercent pas
     * {@link #executeStep}. */
    private final WorkflowFinalizationService finalizationService;
    /** Lot C — alimente {@code dossier_variables}. Optionnel dans les tests unitaires. */
    private final ProjecteurVariablesCreation projecteurCreation;

    @PersistenceContext
    private EntityManager em;

    public WorkflowUseCases(WorkflowProgressRepository progressRepository,
                            WorkflowOrchestrator orchestrator,
                            WorkflowProgressLookup progressLookup,
                            @Autowired(required = false) PlanLimitsService planLimitsService,
                            WorkflowFinalizationService finalizationService,
                            @Autowired(required = false) ProjecteurVariablesCreation projecteurCreation) {
        this.progressRepository = progressRepository;
        this.orchestrator = orchestrator;
        this.progressLookup = progressLookup;
        this.planLimitsService = planLimitsService;
        this.finalizationService = finalizationService;
        this.projecteurCreation = projecteurCreation;
    }

    @Transactional
    @Auditable(action = "WORKFLOW_STARTED", resourceType = "ticket", resourceIdExpr = "#ticketId")
    public WorkflowProgress startOrResume(UUID workspaceId, UUID ticketId, WorkflowType type, UUID userId) {
        TenantContext.set(workspaceId);
        log.info("workflow.start workspace={} ticket={} type={} user={}",
                workspaceId, ticketId, type, userId);
        try {
            return progressRepository.findByTicket(workspaceId, ticketId)
                    .map(existing -> {
                        log.info("workflow.start.resume existing progress id={} currentStep={} statut={}",
                                existing.id(), existing.currentStep(), existing.statut());
                        return existing;
                    })
                    .orElseGet(() -> {
                        log.info("workflow.start.create new progress totalSteps={}", type.totalSteps());
                        // Fix 2026-06-11 — INSERT isole en REQUIRES_NEW : un conflit ne
                        // contamine plus la transaction parent (cf. WorkflowProgressLookup).
                        WorkflowProgress fresh = progressLookup.createInNewTransaction(
                                workspaceId, ticketId, type, type.totalSteps(), userId);
                        // 2026-06-24 — DISSOCIATION « creer un ticket » / « prendre en charge ».
                        // OUVRIR le wizard de workflow ne fait PLUS passer le ticket
                        // NOUVEAU -> EN_COURS. Un ticket cree mais pas encore travaille DOIT
                        // rester visible en NOUVEAU (colonne Kanban + compteur dashboard).
                        // La prise en charge implicite se fait desormais a la VALIDATION de
                        // l'etape 1 (cf. executeStep -> "WORKFLOW_STEP1_VALIDATED"), ou
                        // explicitement via le bouton « Prendre en charge » (transition
                        // manuelle EN_COURS cote ticket-service). Aucune auto-transition ici.
                        return fresh;
                    });
        } catch (org.springframework.dao.DataIntegrityViolationException
                | org.springframework.transaction.UnexpectedRollbackException ex) {
            // Cas typique : course concurrente (React StrictMode dev double-invoke ou
            // double-clic) sur la creation du workflow_progress. La transaction outer
            // peut etre marquee rollback-only ; on recupere le row commit par l'appel
            // concurrent via une transaction NEUVE (REQUIRES_NEW) — sinon la session
            // JPA en rollback retourne empty.
            //
            // NB 2026-06-24 : l'auto-transition NOUVEAU -> EN_COURS a la creation a ete
            // RETIREE (dissociation creer/prendre-en-charge). On conserve neanmoins le
            // catch sur UnexpectedRollbackException par defense : une course sur l'INSERT
            // du workflow_progress peut toujours marquer la TX rollback-only sans propager
            // de DataIntegrityViolationException directe a ce niveau.
            log.warn("workflow.start.conflict workspace={} ticket={} : {} (cause={})",
                    workspaceId, ticketId, ex.getClass().getSimpleName(),
                    ex.getMostSpecificCause() != null
                            ? ex.getMostSpecificCause().getMessage()
                            : ex.getMessage());
            // Fix 2026-06-11 — Petit retry pour absorber la fenetre entre la commit
            // de la transaction concurrente et la visibilite snapshot REPEATABLE READ.
            // Sans cela, le lookup REQUIRES_NEW peut retourner empty si le commit de
            // l'autre transaction n'a pas encore ete visible quand notre snapshot a
            // ete pris. 3 essais espaces de 25/50 ms = ~75 ms max -- imperceptible
            // pour le user, tres au-dessus du temps de commit Postgres en local.
            for (int attempt = 0; attempt < 3; attempt++) {
                Optional<WorkflowProgress> found =
                        progressLookup.findInNewTransaction(workspaceId, ticketId);
                if (found.isPresent()) {
                    log.info("workflow.start.recovered (attempt {}) progress id={}",
                            attempt + 1, found.get().id());
                    return found.get();
                }
                try {
                    Thread.sleep(25L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            throw new ConflictException(
                    "Le workflow n'a pas pu etre demarre — un autre workflow existe "
                            + "deja pour ce ticket ou la societe n'est pas accessible "
                            + "depuis votre espace. Rechargez la page.");
        } catch (Exception ex) {
            log.error("workflow.start.failed workspace={} ticket={} type={} user={}",
                    workspaceId, ticketId, type, userId, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public WorkflowProgress get(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        return progressRepository.findByTicket(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Aucun workflow en cours pour ce ticket"));
    }

    @Transactional
    public WorkflowProgress save(UUID workspaceId, UUID ticketId, int currentStep,
                                  Map<String, Object> data, UUID userId) {
        TenantContext.set(workspaceId);
        WorkflowProgress p = progressRepository.findByTicket(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Aucun workflow en cours"));
        Map<String, Object> merged = new HashMap<>(p.data());
        merged.putAll(data);
        // 2026-06-04 : currentStep persiste la "high-water mark" (etape la plus avancee atteinte).
        // On NE REGRESSE JAMAIS : un saveDraft sur une etape anterieure (navigation arriere)
        // ne doit pas reduire la progression -- sinon le user perd l'acces aux steps suivants.
        int newCurrentStep = Math.max(p.currentStep(), currentStep);
        WorkflowProgress sauve =
                progressRepository.save(p.id(), newCurrentStep, merged, p.statut(), p.completedAt());
        projeterAuMagasin(workspaceId, ticketId, p.type(), merged, userId);
        return sauve;
    }

    /**
     * ALIMENTE LE MAGASIN DE VARIABLES — décision 2 du cabinet (lot C).
     *
     * <p>{@code workflow_progress.data} garde l'état des formulaires, ce que
     * l'écran réaffiche. Les VARIABLES DE DOCUMENT vivent au magasin, et la
     * génération ne lit que lui. Ce point-ci est le SEUL endroit où une saisie
     * devient une variable : c'est ce qui permet d'affirmer qu'il n'existe pas
     * deux chemins pour la même valeur.
     *
     * <p><b>Best-effort volontaire.</b> Un échec de projection ne doit pas faire
     * perdre à l'employé la saisie qu'il vient de valider : la donnée est déjà
     * persistée dans {@code data}, et la projection est idempotente — la
     * sauvegarde suivante la rattrape. On journalise en WARN plutôt que de
     * remonter, et le défaut se voit au contrôle de complétude, pas par une
     * perte de travail.
     */
    private void projeterAuMagasin(UUID workspaceId, UUID ticketId, WorkflowType type,
                                    Map<String, Object> data, UUID userId) {
        if (projecteurCreation == null || type != WorkflowType.CREATION || userId == null) return;
        try {
            projecteurCreation.projeter(workspaceId, ticketId, data, userId);
        } catch (Exception ex) {
            log.warn("magasin.projection.echouee ticket={} : {}", ticketId, ex.getMessage());
        }
    }

    @Transactional
    @Auditable(action = "WORKFLOW_STEP_EXECUTED", resourceType = "ticket", resourceIdExpr = "#ticketId")
    public StepExecutionResult executeStep(UUID workspaceId, UUID ticketId, int step,
                                            Map<String, Object> payload, UUID userId) {
        TenantContext.set(workspaceId);
        WorkflowProgress p = progressRepository.findByTicket(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Aucun workflow en cours"));
        WorkflowStrategy strategy = orchestrator.strategyFor(p.type());

        // Injection des faits du dossier d'entreprise (best-effort) dans existingData
        // sous la cle "dossier" pour que les strategies puissent valider RG-M02/M06
        // (coherence decisionType/forme), RG-M08 (capital), RG-DI/LI (statut).
        Map<String, Object> existingWithDossier = new HashMap<>(p.data());
        try {
            Map<String, Object> dossierFacts = loadDossierFactsByTicket(workspaceId, ticketId);
            if (!dossierFacts.isEmpty()) {
                existingWithDossier.put("dossier", dossierFacts);
            }
        } catch (Exception ex) {
            log.debug("loadDossierFactsByTicket failed ticket={} : {}", ticketId, ex.getMessage());
        }
        // PROMPT F (2026-06-23) — surcharge par le dossier CHOISI à l'étape 1
        // du workflow MODIFICATION (et workflows similaires) : l'employé peut
        // sélectionner une société différente de celle attachée au ticket.
        // On lit step1.dossierId persisté, ou payload.dossierId pour le tout
        // premier passage à l'étape 1. Si présent, on remplace les faits
        // injectés depuis le ticket par ceux du dossier choisi.
        try {
            String chosenDossierIdStr = readChosenDossierId(p.data(), step, payload);
            if (chosenDossierIdStr != null) {
                UUID chosen = UUID.fromString(chosenDossierIdStr);
                Map<String, Object> chosenFacts = loadDossierFactsById(workspaceId, chosen);
                if (!chosenFacts.isEmpty()) {
                    existingWithDossier.put("dossier", chosenFacts);
                }
            }
        } catch (IllegalArgumentException ignore) {
            // dossierId mal formé → la stratégie le bloquera proprement
        } catch (Exception ex) {
            log.debug("loadDossierFactsById failed : {}", ex.getMessage());
        }

        // Lot DIVERS (2026-08-13) — LIQUIDATION : la stratégie doit REFUSER la clôture
        // tant que la société exploite des succursales. Le domaine ne lit pas la base :
        // on lui injecte la liste des succursales encore ACTIVE. Requête limitée à ce
        // seul type de workflow (aucun coût sur les autres).
        if (p.type() == WorkflowType.LIQUIDATION) {
            try {
                UUID cible = parseUuid(readChosenDossierId(p.data(), step, payload));
                if (cible == null) cible = fetchDossierId(workspaceId, ticketId);
                existingWithDossier.put("succursalesOuvertes",
                        listSuccursalesOuvertes(workspaceId, cible));
            } catch (Exception ex) {
                log.debug("listSuccursalesOuvertes failed ticket={} : {}", ticketId, ex.getMessage());
            }
        }

        StepResult result = strategy.executeStep(new StepContext(
                workspaceId, ticketId, userId, step, payload, existingWithDossier));

        if (!result.canAdvance()) {
            throw new ValidationException(result.message());
        }

        // Lot DIVERS §C (2026-08-13) — SUCCURSALE_ETR : la Data Room de la societe mere
        // ETRANGERE est creee DES la validation de l'etape 1, pas a la finalisation.
        // Raison : l'etape 4 « Pieces jointes » depose en Data Room ; sans dossier mere
        // deja existant, ces depots n'auraient aucune destination. L'id resolu est injecte
        // dans les donnees de l'etape 1 -> le front l'utilise immediatement, et la
        // persistance de la succursale le relit a la finalisation (aucune 2e creation).
        Map<String, Object> stepData = result.stepData();
        if (step == 1 && p.type() == WorkflowType.SUCCURSALE_ETR) {
            stepData = withDossierMereEtrangere(workspaceId, stepData);
        }
        // Lot DIVERS §D (2026-08-13) — fin de la « fermeture fantome » : une succursale
        // saisie a la main (absente du referentiel) est creee en base des l'etape 1, pour
        // que sa fermeture soit REELLE et non seulement documentaire.
        if (step == 1 && p.type() == WorkflowType.FERMETURE_SUCCURSALE) {
            stepData = withSuccursaleCreeeSiSaisieManuelle(workspaceId, stepData);
        }

        Map<String, Object> merged = new HashMap<>(p.data());
        merged.put("step" + step, stepData);
        // Backbone refonte statuts (2026-06-24) : on PERSISTE les faits dossier
        // injectés (dont fiche_structuree) dans `data` pour que le front les reçoive
        // via GET (jusqu'ici "dossier" n'était que transitoire dans existingData).
        // Permet à buildPayload(front) d'envoyer l'ÉTAT STRUCTURÉ COMPLET (ancien_*).
        Object injectedDossier = existingWithDossier.get("dossier");
        if (injectedDossier != null) {
            merged.put("dossier", injectedDossier);
        }

        // 2026-06-04 : permet la re-soumission d'une etape DEJA validee (navigation arriere
        // pour corriger une donnee) sans regresser la progression. nextStep est la max
        // entre la position actuelle et step+1 -- la HWM (high-water mark) ne recule jamais.
        // Sauf cas final (step == totalSteps) ou on reste sur la derniere etape.
        int candidateNext = step == p.totalSteps() ? step : step + 1;
        int nextStep = Math.max(p.currentStep(), candidateNext);
        // Le statut TERMINE n'est emis QUE quand on valide effectivement la derniere etape,
        // pas lorsqu'on re-soumet une etape anterieure d'un workflow deja termine (defense).
        boolean finalStepValidated = step == p.totalSteps();
        WorkflowStatut newStatut = finalStepValidated ? WorkflowStatut.TERMINE : p.statut();
        Instant completedAt = (finalStepValidated && p.completedAt() == null) ? Instant.now() : p.completedAt();
        // P3 2026-06-04 : a la 1ere validation effective, on s'assure que le ticket a
        // quitte le statut d'ouverture. Idempotent.
        // Lot 1 (2026-09-04) : NOUVEAU -> EN_COURS devient CREATION_TICKET ->
        // GENERATION_DOCUMENTS, qui designe le meme moment du parcours.
        if (step == 1 && p.currentStep() <= 1) {
            autoTransitionTicket(workspaceId, ticketId, userId,
                    "CREATION_TICKET", "GENERATION_DOCUMENTS", "WORKFLOW_STEP1_VALIDATED");
        }
        // Lot « Liquidation 4 etapes » (2026-08-13) — CAS DE SECOURS. Un dossier dissous
        // AVANT la persistance du liquidateur n'en porte aucun en base : l'etape 1 de la
        // liquidation autorise alors sa selection/saisie UNE SEULE FOIS. On la persiste
        // des la validation de l'etape (et non a la finalisation) pour qu'elle devienne
        // lecture seule des le passage suivant. Sur un dossier deja pourvu, la strategie
        // renvoie le liquidateur BD : l'ecriture est idempotente.
        if (step == 1 && p.type() == WorkflowType.LIQUIDATION) {
            try {
                UUID cible = parseUuid(strOrNull(stepData.get("dossierId")));
                if (cible == null) cible = fetchDossierId(workspaceId, ticketId);
                persistLiquidateur(workspaceId, cible, result.stepData().get("liquidateur"),
                        strOrNull(result.stepData().get("siegeLiquidation")));
            } catch (Exception ex) {
                log.warn("LIQUIDATION step1 : persistance du liquidateur echouee ticket={} : {}",
                        ticketId, ex.getMessage());
            }
        }
        // Lot DIVERS §D (2026-08-13) — FERMETURE_SUCCURSALE, CAS DE SECOURS. Depuis la
        // refonte des ouvertures (§B/§C), le RC de la succursale n'est plus saisi a la
        // creation : il est attribue par le greffe APRES le depot. Il peut donc manquer en
        // base. L'etape 1 accepte alors sa saisie UNE fois ; on le fige des la validation
        // (et non a la finalisation) pour qu'il devienne lecture seule au passage suivant,
        // meme si le workflow est ensuite abandonne. COALESCE : jamais d'ecrasement.
        if (step == 1 && p.type() == WorkflowType.FERMETURE_SUCCURSALE) {
            try {
                persistRcSuccursale(workspaceId, stepData);
            } catch (Exception ex) {
                log.warn("FERMETURE_SUCCURSALE step1 : persistance du RC succursale echouee "
                        + "ticket={} : {}", ticketId, ex.getMessage());
            }
        }
        // P3 : a la VRAIE finalisation, on fait avancer le ticket. L'UPDATE est
        // conditionnee au statut de depart, donc un ticket ANNULE reste annule.
        //
        // Lot 1 (2026-09-04) — le workflow CREATION ne produit que les ACTES
        // (etapes 4 a 12 du guide). Les etapes 13 a 33 (signature, enregistrement,
        // immatriculation, publications, CNSS) restent a accomplir : la fin du
        // workflow fait donc passer le ticket au statut 3 « Deroulement de la
        // demarche », et non a la cloture. Les autres workflows, qui n'ont pas de
        // referentiel de demarches, conservent leur comportement : fin de workflow
        // = ticket cloture.
        if (finalStepValidated && p.statut() != WorkflowStatut.TERMINE) {
            String cible = p.type() == WorkflowType.CREATION
                    ? "DEROULEMENT_DEMARCHE"
                    : "CLOTURE_DOSSIER";
            autoTransitionTicket(workspaceId, ticketId, userId,
                    "GENERATION_DOCUMENTS", cible, "WORKFLOW_COMPLETED");
        }

        WorkflowProgress updated = progressRepository.save(
                p.id(), nextStep, merged, newStatut, completedAt);

        // Lot C — l'etape validee alimente le magasin de variables du dossier.
        projeterAuMagasin(workspaceId, ticketId, p.type(), merged, userId);

        // RG-C23 / RG-S01 : a la fin du workflow CREATION, on cree
        // automatiquement l'entreprise_dossier (= base du Data Room).
        //
        // Fix 2026-07-19 — l'INSERT est delegue a un bean distinct en
        // REQUIRES_NEW (WorkflowFinalizationService). Une violation d'unicite
        // ICE / raison sociale (index partiels ticket-service V6) ou un quota
        // atteint (RG-BL-QUOTA) n'annule alors QUE la transaction fille : la
        // finalisation du workflow (statut TERMINE, deja persistee ci-dessus)
        // reste committee et on ne leve plus UnexpectedRollbackException (500).
        // Le lien ticket->dossier reste dans CETTE transaction : il mute la
        // ligne `tickets` deja verrouillee par autoTransitionTicket(CLOTURE) ;
        // le rapatrier en REQUIRES_NEW provoquerait un lock-wait sur la meme ligne.
        if (newStatut == WorkflowStatut.TERMINE && p.type() == WorkflowType.CREATION) {
            try {
                UUID dossierId = finalizationService != null
                        ? finalizationService.createEntrepriseDossierInNewTransaction(
                                workspaceId, ticketId, userId, merged)
                        : createEntrepriseDossier(workspaceId, ticketId, userId, merged);
                if (dossierId != null) {
                    linkTicketToDossier(workspaceId, ticketId, dossierId);
                    log.info("CREATION workflow termine -- dossier {} cree/lie au ticket {}",
                            dossierId, ticketId);
                }
            } catch (Exception ex) {
                // La transaction fille a deja rollback proprement ; la transaction
                // principale est intacte. On journalise la CAUSE RACINE exacte
                // (message de la contrainte violee / du quota) pour diagnostic.
                Throwable root = ex;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                log.warn("Echec creation auto du dossier en fin de workflow ticket={} : {} (cause={})",
                        ticketId, ex.getMessage(), root.getMessage());
            }
        }

        // Phase 3 : a la fin DISSOLUTION / LIQUIDATION / MODIFICATION,
        // mettre a jour le statut du dossier d'entreprise.
        if (newStatut == WorkflowStatut.TERMINE) {
            try {
                applyPostCompletion(p.type(), workspaceId, ticketId, merged);
            } catch (Exception ex) {
                log.warn("Echec post-completion {} ticket={} : {}", p.type(), ticketId, ex.getMessage());
            }
        }

        return new StepExecutionResult(updated, result);
    }

    /**
     * Cree un entreprise_dossier a partir des donnees accumulees du workflow CREATION.
     * RG-C13 : ICE 15 chiffres. RG-C09 : capital libere ≥ 25%.
     * Differencie SARL vs SARL_AU via le champ formeJuridique de la step 1.
     *
     * <p><b>Visibilite package</b> (et non {@code private}) : appele via le proxy
     * Spring de {@link WorkflowFinalizationService} pour beneficier de la
     * transaction {@code REQUIRES_NEW} (une auto-invocation privee n'aurait aucune
     * semantique transactionnelle propre — c'est precisement la cause du 500 corrige
     * le 2026-07-19).
     *
     * <p><b>Idempotence</b> : la finalisation d'un workflow CREATION peut etre
     * rejouee (re-soumission de la derniere etape d'un workflow deja TERMINE).
     * Si le ticket est deja lie a un dossier, on retourne cet id sans reinserer
     * (evite un doublon et la violation d'unicite ICE / raison sociale).
     */
    @SuppressWarnings("unchecked")
    UUID createEntrepriseDossier(UUID workspaceId, UUID ticketId, UUID initiatorUserId,
                                 Map<String, Object> data) {
        // Idempotence (RG : 1 dossier par ticket CREATION). Si le ticket porte
        // deja un dossier_id, la finalisation a deja cree la societe lors d'un
        // passage anterieur -> on relie simplement (no-op cote UPDATE) sans INSERT.
        UUID alreadyLinked = fetchDossierId(workspaceId, ticketId);
        if (alreadyLinked != null) {
            log.info("createEntrepriseDossier idempotent : dossier {} deja lie au ticket {} -- skip INSERT",
                    alreadyLinked, ticketId);
            return alreadyLinked;
        }

        Map<String, Object> denominationStep = pickStep(data, "denomination");
        Map<String, Object> siegeStep = pickStep(data, "siege");
        Map<String, Object> capitalStep = pickStep(data, "capital");

        String denomination = pickString(denominationStep, "denomination");
        if (denomination == null || denomination.isBlank()) {
            log.debug("Pas de denomination dans le workflow -- skip creation dossier");
            return null;
        }

        // Sprint Beta — enforce maxDossiers du plan AVANT INSERT (RG-BL-QUOTA).
        // Si limite atteinte -> 402 PaymentRequired remonte au front qui affiche
        // l'upsell vers le plan superieur.
        if (planLimitsService != null) {
            planLimitsService.enforceDossierLimit(workspaceId);
        }

        // Forme juridique : SARL par defaut, SARL_AU si specifie
        String forme = pickString(denominationStep, "formeJuridique");
        if (forme == null) forme = pickString(data, "formeJuridique");
        if (forme == null) forme = "SARL";
        if (!forme.equals("SARL") && !forme.equals("SARL_AU") && !forme.equals("SA")
                && !forme.equals("SAS") && !forme.equals("SCS") && !forme.equals("GIE")) {
            forme = "SARL";
        }

        // Fix 2026-06-04 : front envoie `ice`, ancien contrat `icenumero`. CreationSarlWorkflow
        // normalise desormais les deux. On reste tolerant ici pour les workflows deja en cours
        // anterieurs au fix.
        String ice = pickString(denominationStep, "ice");
        if (ice == null) ice = pickString(denominationStep, "icenumero");
        String adresse = pickString(siegeStep, "adresse");
        String ville = pickString(siegeStep, "commune");
        if (ville == null) ville = pickString(siegeStep, "ville");
        BigDecimal capital = pickBigDecimal(capitalStep, "capitalSocialMad");

        UUID dossierId = UUID.randomUUID();
        // Lot Q (scoping EMPLOYE) : le dossier DOIT naitre avec un responsable_id,
        // sinon il est invisible pour l'employe qui ne voit que responsable_id = userId.
        // Miroir de la regle V9 (ticket-service) : assigne du ticket, a defaut son
        // createur, a defaut l'initiateur qui pilote le workflow.
        UUID responsableId = resolveDossierResponsable(workspaceId, ticketId, initiatorUserId);
        // Backbone refonte statuts : on capture l'état structuré complet du wizard
        // CREATION dans fiche_structuree (objet/gérance/associés/parts inclus), jamais vide.
        String ficheJson = ficheToJson(buildFicheFromCreation(data));
        em.createNativeQuery("""
                INSERT INTO entreprise_dossiers
                  (id, workspace_id, raison_sociale, forme_juridique, ice,
                   adresse_siege, ville, capital_social_mad, date_constitution, statut,
                   responsable_id, fiche_structuree)
                VALUES
                  (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, 'EN_CONSTITUTION',
                   ?11, CAST(?10 AS jsonb))
                """)
                .setParameter(1, dossierId)
                .setParameter(2, workspaceId)
                .setParameter(3, denomination)
                .setParameter(4, forme)
                .setParameter(5, ice)
                .setParameter(6, adresse)
                .setParameter(7, ville)
                .setParameter(8, capital)
                .setParameter(9, LocalDate.now())
                .setParameter(10, ficheJson == null ? "{}" : ficheJson)
                .setParameter(11, responsableId)
                .executeUpdate();

        return dossierId;
    }

    /**
     * Resout le responsable durable (owner) du dossier cree en fin de workflow
     * CREATION. Miroir de la regle de backfill V9 (ticket-service) :
     * {@code COALESCE(assigne_id, cree_par_id)} du ticket declencheur, avec
     * fallback ultime sur l'initiateur authentifie qui pilote le workflow.
     *
     * <p>Sans ce renseignement, le dossier naissait avec {@code responsable_id
     * NULL} et devenait invisible pour l'employe depuis le scoping Lot Q
     * (EMPLOYE ne voit que ses dossiers ou {@code responsable_id = userId}).
     */
    private UUID resolveDossierResponsable(UUID workspaceId, UUID ticketId, UUID initiatorUserId) {
        try {
            Object result = em.createNativeQuery(
                    "SELECT COALESCE(assigne_id, cree_par_id) FROM tickets " +
                    "WHERE id = ?1 AND workspace_id = ?2")
                    .setParameter(1, ticketId)
                    .setParameter(2, workspaceId)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (result != null) {
                return (result instanceof UUID u) ? u : UUID.fromString(result.toString());
            }
        } catch (Exception ex) {
            log.debug("resolveDossierResponsable failed ticket={} : {}", ticketId, ex.getMessage());
        }
        return initiatorUserId;
    }

    /**
     * Defense-in-depth multi-tenant : filtre workspace_id obligatoire.
     * RLS Postgres n'est PAS un garde-fou ici (jurika_user a BYPASSRLS dans le conteneur
     * officiel, cf memory dashboard-bypassrls-fix-2026-06-05). Sans ce filtre, un EMPLOYE
     * authentifie pouvait corrompre le ticket d'un workspace tiers en l'aiguillant vers
     * un dossierId du sien (cross-tenant write).
     */
    private void linkTicketToDossier(UUID workspaceId, UUID ticketId, UUID dossierId) {
        em.createNativeQuery("UPDATE tickets SET dossier_id = ?1 WHERE id = ?2 AND workspace_id = ?3")
                .setParameter(1, dossierId)
                .setParameter(2, ticketId)
                .setParameter(3, workspaceId)
                .executeUpdate();
    }

    /**
     * Dispatch les actions post-completion par type de workflow (Phase 3+).
     * <ul>
     *   <li>DISSOLUTION   : dossier.statut = DISSOUTE</li>
     *   <li>LIQUIDATION   : dossier.statut = LIQUIDEE</li>
     *   <li>MODIFICATION  : update des champs societe selon types coches</li>
     *   <li>SUCCURSALE_MA / SUCCURSALE_ETR : INSERT ligne succursales (statut ACTIVE)</li>
     *   <li>FERMETURE_SUCCURSALE : succursale selectionnee -> statut FERMEE + closed_at</li>
     *   <li>PV_AGO : pas d'action V1</li>
     * </ul>
     */
    private void applyPostCompletion(WorkflowType type, UUID workspaceId, UUID ticketId,
                                      Map<String, Object> data) {
        // Lot succursales (2026-07-05) : la persistance/cloture des succursales NE
        // DEPEND PAS du dossier_id du ticket (le parent est la societe mere choisie
        // a l'etape 1, pas le dossier du ticket qui est generalement nul pour ces
        // workflows). On traite donc ces types AVANT le garde-fou fetchDossierId
        // (qui sortirait en amont sur dossierId == null).
        switch (type) {
            case SUCCURSALE_MA -> { persistSuccursaleMa(workspaceId, data); return; }
            case SUCCURSALE_ETR -> { persistSuccursaleEtr(workspaceId, data); return; }
            case FERMETURE_SUCCURSALE -> { closeSuccursale(workspaceId, data); return; }
            default -> { /* suite : logique basee sur le dossier du ticket */ }
        }
        // Defense-in-depth : variante workspace-scoped pour eviter d'appliquer
        // les UPDATE statut/raison_sociale/capital sur un dossier appartenant a
        // un autre workspace (cf chaine d'exploitation RG-SAAS-01).
        UUID dossierId = fetchDossierId(workspaceId, ticketId);
        // 2026-08-16 (fix D2) — MODIFICATION / DISSOLUTION / LIQUIDATION operent sur
        // une societe CHOISIE a l'etape 1 (step1.dossierId), SANS lier le ticket :
        // tickets.dossier_id reste NULL. Sans ce repli, applyPostCompletion sortait
        // en amont et les effets metier (statut DISSOUTE/LIQUIDEE, modifications de
        // la fiche, persistance du liquidateur) n'etaient JAMAIS appliques -- les
        // documents etaient generes mais l'etat de la societe restait inchange, ce
        // qui bloquait notamment la LIQUIDATION (qui exige un dossier DISSOUTE).
        // Les UPDATE cibles restent workspace-scoped (AND workspace_id = ?), donc
        // un dossierId d'un autre tenant ne modifierait aucune ligne.
        if (dossierId == null) {
            dossierId = parseUuid(readChosenDossierId(data, 0, null));
        }
        if (dossierId == null) {
            log.debug("Pas de dossier_id sur ticket {}, skip post-completion {}", ticketId, type);
            return;
        }
        switch (type) {
            // Lot W4 (2026-07-04) : a la COMPLETION du workflow CREATION, la societe
            // passe EN_CONSTITUTION -> ACTIVE (le dossier vient d'etre cree + lie juste
            // avant, cf. bloc CREATION de executeStep). Idempotent : re-soumettre la
            // derniere etape d'un workflow deja termine repose ACTIVE sans effet. Les
            // creations ENCORE EN COURS n'ont pas encore de dossier -> restent invisibles.
            // Fix 2026-08-12 — le dossier est cree en STUB par ticket-service des la
            // creation du ticket (DossierIdempotenceLookup), donc `createEntrepriseDossier`
            // fait un skip d'idempotence et `fiche_structuree` restait NULL a jamais :
            // les associes / gerants saisis dans le wizard n'atterrissaient JAMAIS en base
            // (=> pre-remplissage impossible dans MODIFICATION). On consolide donc ici,
            // comme l'IMPORT le fait deja.
            case CREATION -> applyCreationConsolidation(workspaceId, dossierId, data);
            // Lot Liquidation 4 etapes (2026-08-13) : la dissolution persiste desormais
            // AUSSI le liquidateur qu'elle nomme (+ le siege de la liquidation). Sans
            // cela, le workflow LIQUIDATION devait le re-saisir integralement — ce qui
            // viole l'interdiction de re-saisir une donnee deja connue.
            case DISSOLUTION -> {
                updateDossierDissolution(workspaceId, dossierId, resolveDateDissolution(data));
                persistLiquidateurFromStep1(workspaceId, dossierId, data);
            }
            // Lot DIVERS (2026-08-13) — la cloture de la liquidation n'est atteinte QUE si
            // toutes les succursales ont ete fermees au prealable : la garde vit dans
            // LiquidationWorkflow.stepSynthese, qui refuse la finalisation sinon. Aucune
            // radiation en cascade ici — fermer une succursale est un acte a part entiere
            // (PV + annonce + radiation au RC de son lieu d'exploitation), pas un effet de
            // bord silencieux de la liquidation de la mere.
            case LIQUIDATION -> updateDossierStatut(workspaceId, dossierId, "LIQUIDEE");
            case MODIFICATION -> applyModifications(workspaceId, dossierId, data);
            case IMPORT -> applyImportConsolidation(workspaceId, dossierId, data);
            default -> { /* Autres types : pas d'action V1 */ }
        }
        log.info("post-completion {} applied dossier={} workflow.ticket={}", type, dossierId, ticketId);
    }

    // ========================================================================
    //  Lot succursales (2026-07-05) — persistance a la completion des workflows
    //  de creation, cloture a la completion de la fermeture.
    //  Table `succursales` : possedee par ticket-service, meme base Postgres
    //  partagee (INSERT/UPDATE natifs, comme pour entreprise_dossiers).
    //  NOTE LEGACY : les succursales creees AVANT ce lot ne sont pas en base
    //  (jamais stockees) -> non listables, pas de backfill possible.
    // ========================================================================

    /**
     * SUCCURSALE_MA : INSERT une ligne succursales rattachee a la societe mere
     * (step1.societeMereId). Denomination/activite/adresse/ville/rc + directeur
     * lus depuis les etapes du wizard.
     */
    private void persistSuccursaleMa(UUID workspaceId, Map<String, Object> data) {
        // Lot DIVERS §B (2026-08-13) — refonte 11 -> 5 etapes : la societe mere est
        // a l'etape 1 et TOUTE la succursale au bloc `step2.succursale`. Le RC de la
        // succursale n'est plus saisi (attribue par le greffe APRES le depot).
        Map<String, Object> s1 = pickStep(data, "step1");
        Map<String, Object> s2 = pickStep(data, "step2");
        UUID parentId = parseUuid(firstNonNull(
                pickString(s1, "dossierId"),
                pickString(s1, "societeMereId"), pickString(s1, "dossierMereId")));
        if (parentId == null) {
            log.warn("SUCCURSALE_MA sans societe mere exploitable -> succursale non persistee (non listable)");
            return;
        }
        Map<String, Object> succ = pickMap(s2, "succursale");
        Map<String, Object> resp = pickMap(succ, "responsable");
        insertSuccursale(workspaceId, parentId, "MA",
                pickString(succ, "enseigne"),
                pickString(succ, "activite"),
                pickString(succ, "adresse"),
                pickString(succ, "ville"),
                null, // RC secondaire : attribue par le greffe apres depot
                pickString(resp, "nom"),
                pickString(resp, "prenom"),
                firstNonNull(pickString(resp, "pieceNumero"), pickString(resp, "cin")),
                null);
    }

    /**
     * SUCCURSALE_ETR : cree (ou reutilise) le <b>dossier de la societe mere
     * etrangere</b>, puis INSERT la succursale rattachee a ce dossier.
     *
     * <p>Lot DIVERS §C (2026-08-13). Jusqu'ici, faute de dossier local, AUCUNE ligne
     * `succursales` n'etait creee (contrainte {@code parent_dossier_id NOT NULL}) : la
     * succursale etrangere etait invisible, non fermable par le workflow de fermeture,
     * et la societe mere n'avait aucune Data Room de destination pour ses actes.
     *
     * <p>La mere etrangere devient un {@code entreprise_dossiers} a part entiere
     * ({@code origine = 'ETRANGERE'}, cf. migration V16) : elle porte donc sa Data Room,
     * ses documents et sa tracabilite comme n'importe quel dossier — sans table nouvelle.
     */
    private void persistSuccursaleEtr(UUID workspaceId, Map<String, Object> data) {
        Map<String, Object> s1 = pickStep(data, "step1");
        Map<String, Object> s2 = pickStep(data, "step2");
        // Cas nominal : le dossier mere a ete cree DES la validation de l'etape 1
        // (pour que sa Data Room existe avant l'etape « Pieces jointes ») ; son id
        // est donc deja persiste. Le resolve n'est ici qu'un filet pour les workflows
        // commences avant ce lot.
        UUID parentId = parseUuid(pickString(s1, "dossierMereEtrangereId"));
        if (parentId == null) {
            try {
                parentId = resolveOrCreateDossierMereEtrangere(workspaceId, s1);
            } catch (Exception ex) {
                log.warn("SUCCURSALE_ETR : dossier mere etrangere non resolu a la finalisation : {}",
                        ex.getMessage());
            }
        }
        if (parentId == null) {
            log.warn("SUCCURSALE_ETR sans societe mere exploitable (denomination absente) -> "
                    + "succursale non persistee, aucune Data Room mere creee.");
            return;
        }
        Map<String, Object> succ = pickMap(s2, "succursale");
        Map<String, Object> resp = pickMap(succ, "responsable");
        Map<String, Object> mere = pickMap(s1, "societeMere");
        insertSuccursale(workspaceId, parentId, "ETR",
                pickString(succ, "enseigne"),
                pickString(succ, "activite"),
                pickString(succ, "adresse"),
                pickString(succ, "ville"),
                null, // RC secondaire : attribue par le greffe apres depot
                pickString(resp, "nom"),
                pickString(resp, "prenom"),
                firstNonNull(pickString(resp, "pieceNumero"), pickString(resp, "cin")),
                pickString(mere, "pays"));
    }

    /**
     * Resout le dossier de la societe mere ETRANGERE, dans cet ordre :
     * <ol>
     *   <li>{@code step1.dossierMereEtrangereId} — la mere a ete SELECTIONNEE dans la
     *       liste des meres etrangeres deja enregistrees (spec §C : « si une mere
     *       etrangere deja enregistree est reutilisee -> la selectionner ») ;</li>
     *   <li>a defaut, recherche par denomination (insensible a la casse) parmi les
     *       dossiers {@code origine = 'ETRANGERE'} du workspace — idempotence : deux
     *       succursales du meme groupe ne creent qu'un seul dossier mere ;</li>
     *   <li>a defaut, INSERT d'un nouveau dossier mere.</li>
     * </ol>
     *
     * <p><b>Les exceptions ne sont PAS avalees ici</b> : l'appelant les traduit. Elle est
     * invoquee via {@code WorkflowFinalizationService} ({@code REQUIRES_NEW}) parce que
     * l'INSERT peut violer l'index unique partiel {@code uq_dossier_workspace_raison_alive}
     * (workspace + raison sociale, statuts vivants) — une mere etrangere homonyme d'une
     * societe marocaine deja au dossier. Sans transaction fille, cet echec marquerait la
     * transaction de l'etape <i>rollback-only</i> (500 au commit) au lieu d'un refus propre.
     */
    UUID resolveOrCreateDossierMereEtrangere(UUID workspaceId, Map<String, Object> s1) {
        UUID selected = parseUuid(firstNonNull(
                pickString(s1, "dossierMereEtrangereId"), pickString(s1, "dossierLocalId")));
        if (selected != null) return selected;

        Map<String, Object> mere = pickMap(s1, "societeMere");
        String denomination = pickString(mere, "denomination");
        if (denomination == null || denomination.isBlank()) return null;

        Object existing = em.createNativeQuery("""
                SELECT id FROM entreprise_dossiers
                WHERE workspace_id = ?1
                  AND origine = 'ETRANGERE'
                  AND lower(raison_sociale) = lower(?2)
                ORDER BY created_at ASC
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, denomination.trim())
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            UUID id = existing instanceof UUID u ? u : UUID.fromString(existing.toString());
            log.info("SUCCURSALE_ETR : mere etrangere « {} » deja enregistree -> reutilisation {}",
                    denomination, id);
            return id;
        }

        // UNE SOCIETE ETRANGERE N'EST PAS UNE SARL : `forme_juridique` vaut 'ETRANGERE'
        // (valeur ajoutee au CHECK par la migration V16), la forme REELLE du pays
        // d'origine allant dans `forme_juridique_origine` — c'est elle qui est publiee.
        // Le choix du modele SARL vs SARL AU ne depend PAS de cette colonne : il vient du
        // caractere uni/pluripersonnel de l'ORGANE qui decide (step1.associeUnique).
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO entreprise_dossiers
                  (id, workspace_id, raison_sociale, forme_juridique, statut,
                   origine, pays, forme_juridique_origine, registre_etranger,
                   registre_etranger_numero, loi_applicable, capital_origine,
                   adresse_siege)
                VALUES (?1, ?2, ?3, ?4, 'ACTIVE', 'ETRANGERE', ?5, ?6, ?7, ?8, ?9, ?10, ?11)
                """)
                .setParameter(1, id)
                .setParameter(2, workspaceId)
                .setParameter(3, denomination.trim())
                .setParameter(4, "ETRANGERE")
                .setParameter(5, pickString(mere, "pays"))
                .setParameter(6, pickString(mere, "forme"))
                .setParameter(7, pickString(mere, "registre"))
                .setParameter(8, pickString(mere, "registreNumero"))
                .setParameter(9, pickString(mere, "loiApplicable"))
                .setParameter(10, pickString(mere, "capital"))
                .setParameter(11, pickString(mere, "siege"))
                .executeUpdate();
        log.info("SUCCURSALE_ETR : Data Room dediee creee pour la mere etrangere « {} » -> dossier {}",
                denomination, id);
        return id;
    }

    /** Sous-bloc Map d'un payload d'etape (jamais null). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> pickMap(Map<String, Object> src, String key) {
        Object v = src == null ? null : src.get(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /**
     * Enrichit les donnees de l'etape 1 de SUCCURSALE_ETR avec l'id du dossier de la
     * societe mere ETRANGERE, cree ou reutilise a la volee (lot DIVERS §C, 2026-08-13).
     *
     * <p>La creation passe par {@link WorkflowFinalizationService} ({@code REQUIRES_NEW}) :
     * l'INSERT peut violer l'index unique partiel {@code uq_dossier_workspace_raison_alive}
     * si une societe VIVANTE porte deja cette denomination dans le workspace. Dans une
     * transaction fille, cet echec n'empoisonne pas l'etape — on le traduit en refus clair
     * (l'employe corrige la denomination) plutot qu'en 500 au commit.
     *
     * <p>Aucune denomination exploitable (cas theorique : la strategie l'exige deja) =
     * on n'invente rien et on laisse l'etape passer telle quelle.
     */
    private Map<String, Object> withDossierMereEtrangere(UUID workspaceId,
                                                          Map<String, Object> stepData) {
        if (finalizationService == null) return stepData; // contexte de test sans proxy
        UUID mereId;
        try {
            mereId = finalizationService
                    .createOrReuseDossierMereEtrangereInNewTransaction(workspaceId, stepData);
        } catch (DataIntegrityViolationException | ConstraintViolationException ex) {
            // On ne DEDUIT PAS la cause : toute violation d'integrite n'est pas un
            // doublon de denomination. La version precedente l'affirmait pour n'importe
            // quelle contrainte — quand le CHECK sur `forme_juridique` refusait la valeur
            // 'ETRANGERE' (migration editee apres coup, cf. V18), l'employe lisait « cette
            // denomination existe deja » et cherchait un doublon inexistant. On distingue
            // donc l'index d'unicite du reste, et on reste factuel dans le cas general.
            String cause = rootMessage(ex);
            String denomination = pickString(pickMap(stepData, "societeMere"), "denomination");
            if (cause.contains("uq_dossier_workspace_raison_alive")) {
                throw new ValidationException(
                        "Une societe vivante porte deja la denomination « " + denomination
                                + " » dans cet espace de travail : impossible d'ouvrir un second "
                                + "dossier a ce nom. Verifiez la denomination de la societe mere, "
                                + "ou selectionnez la mere etrangere deja enregistree.");
            }
            log.error("SUCCURSALE_ETR step1 : violation d'integrite a la creation du dossier "
                    + "mere etrangere « {} » : {}", denomination, cause);
            throw new ValidationException(
                    "Le dossier de la societe mere etrangere n'a pas pu etre enregistre "
                            + "(contrainte de base de donnees). Signalez ce message au support : "
                            + cause);
        } catch (Exception ex) {
            log.warn("SUCCURSALE_ETR step1 : creation du dossier mere etrangere echouee : {}",
                    ex.getMessage());
            throw new ValidationException(
                    "La Data Room de la societe mere etrangere n'a pas pu etre creee : "
                            + ex.getMessage());
        }
        if (mereId == null) return stepData;
        Map<String, Object> enriched = new HashMap<>(stepData);
        enriched.put("dossierMereEtrangereId", mereId.toString());
        return enriched;
    }

    /**
     * Message de la cause RACINE : Spring emballe l'exception JDBC, et c'est le message
     * PostgreSQL du fond de pile qui nomme la contrainte violee.
     */
    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        return m == null ? ex.getClass().getSimpleName() : m;
    }

    /** INSERT native dans succursales (statut ACTIVE). workspace_id explicite. */
    private void insertSuccursale(UUID workspaceId, UUID parentDossierId, String type,
                                  String denomination, String activite, String adresse,
                                  String ville, String rcSecondaire, String directeurNom,
                                  String directeurPrenom, String directeurCin, String paysOrigine) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO succursales
                  (id, workspace_id, parent_dossier_id, type, denomination, activite,
                   adresse, ville, rc_secondaire, directeur_nom, directeur_prenom,
                   directeur_cin, pays_origine, statut)
                VALUES
                  (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, 'ACTIVE')
                """)
                .setParameter(1, id)
                .setParameter(2, workspaceId)
                .setParameter(3, parentDossierId)
                .setParameter(4, type)
                .setParameter(5, denomination)
                .setParameter(6, activite)
                .setParameter(7, adresse)
                .setParameter(8, ville)
                .setParameter(9, rcSecondaire)
                .setParameter(10, directeurNom)
                .setParameter(11, directeurPrenom)
                .setParameter(12, directeurCin)
                .setParameter(13, paysOrigine)
                .executeUpdate();
        log.info("succursale persistee id={} type={} parent={} workspace={}",
                id, type, parentDossierId, workspaceId);
    }

    /**
     * FERMETURE_SUCCURSALE : passe la succursale en base a statut='FERMEE' (+ closed_at,
     * date d'effet, motif).
     *
     * <p>Depuis la fin de la « fermeture fantome » (lot DIVERS §D, 2026-08-13), toute
     * succursale saisie a la main est CREEE en base des l'etape 1
     * ({@link #withSuccursaleCreeeSiSaisieManuelle}) : {@code step1.succursaleDbId} est donc
     * toujours renseigne en fonctionnement normal. Un identifiant absent ici signale que
     * cette creation a echoue — on le journalise en WARN et non plus en DEBUG, car le
     * dossier se retrouve alors avec un PV et une annonce de fermeture sans succursale
     * fermee en base.
     */
    private void closeSuccursale(UUID workspaceId, Map<String, Object> data) {
        Map<String, Object> s1 = pickStep(data, "step1");
        UUID succId = parseUuid(pickString(s1, "succursaleDbId"));
        if (succId == null) {
            log.warn("FERMETURE_SUCCURSALE finalisee SANS succursaleDbId : aucune ligne "
                    + "`succursales` n'a ete marquee FERMEE. La succursale restera proposee "
                    + "a la fermeture. Verifier l'echec de creation a l'etape 1.");
            return;
        }
        // Lot DIVERS §D (2026-08-13) — on ne se contente plus de `statut = FERMEE` :
        // la spec exige de marquer la succursale « fermée/radiée en base (date + motif) ».
        // `closed_at` horodate l'action applicative ; `date_fermeture` porte la date
        // d'EFFET juridique decidee par l'assemblee (migration V17). Le motif est publie
        // dans l'annonce : le conserver permet de rejouer/justifier l'avis apres coup.
        Map<String, Object> succ = pickMap(s1, "succursale");
        LocalDate dateFermeture = parseDate(firstNonNull(
                pickString(succ, "dateFermeture"), pickString(s1, "dateFermeture")));
        String motif = firstNonNull(pickString(succ, "motif"), pickString(s1, "motif"));
        // RC de secours : depuis la refonte §B/§C il n'est plus saisi a l'ouverture
        // (attribue par le greffe apres depot). S'il vient d'etre saisi a la fermeture,
        // on le fige ici pour qu'il ne soit plus jamais redemande. COALESCE : on
        // n'ecrase jamais un RC deja connu.
        String rcNumero = firstNonNull(pickString(succ, "rcNumero"), pickString(s1, "rcNumero"));

        int updated = em.createNativeQuery("""
                UPDATE succursales
                   SET statut = 'FERMEE',
                       closed_at = NOW(),
                       date_fermeture = COALESCE(CAST(?3 AS DATE), date_fermeture),
                       motif_fermeture = COALESCE(?4, motif_fermeture),
                       rc_secondaire = COALESCE(rc_secondaire, ?5)
                 WHERE id = ?1 AND workspace_id = ?2 AND statut <> 'FERMEE'
                """)
                .setParameter(1, succId)
                .setParameter(2, workspaceId)
                .setParameter(3, dateFermeture)
                .setParameter(4, motif)
                .setParameter(5, rcNumero)
                .executeUpdate();
        log.info("succursale {} -> FERMEE (effet={}, motif={}, rows={})",
                succId, dateFermeture, motif == null ? "-" : "renseigne", updated);
    }

    /** Succursale ACTIVE de meme enseigne chez la meme mere, ou {@code null}. */
    private UUID findSuccursaleActive(UUID workspaceId, UUID parentId, String enseigne) {
        List<?> rows = em.createNativeQuery("""
                SELECT id FROM succursales
                 WHERE workspace_id = ?1 AND parent_dossier_id = ?2
                   AND statut <> 'FERMEE'
                   AND LOWER(TRIM(denomination)) = LOWER(TRIM(?3))
                 LIMIT 1
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, parentId)
                .setParameter(3, enseigne)
                .getResultList();
        return rows.isEmpty() ? null : parseUuid(String.valueOf(rows.get(0)));
    }

    /**
     * Libelles des succursales encore ACTIVE d'une societe mere (lot DIVERS, 2026-08-13).
     *
     * <p>Injecte en {@code existingData} pour le seul workflow LIQUIDATION : la strategie
     * REFUSE la cloture tant que la liste n'est pas vide. Une succursale n'a pas de
     * personnalite juridique distincte de sa societe — elle ne peut pas survivre a la
     * radiation de celle-ci, et doit donc etre fermee AVANT, par son propre acte
     * (PV + annonce + radiation au RC de son lieu d'exploitation).
     *
     * <p>Le libelle sert au message d'erreur : l'employe doit savoir LESQUELLES fermer.
     */
    private List<String> listSuccursalesOuvertes(UUID workspaceId, UUID dossierId) {
        if (dossierId == null) return List.of();
        List<?> rows = em.createNativeQuery("""
                SELECT COALESCE(denomination, 'Succursale')
                       || COALESCE(' (' || ville || ')', '')
                       || COALESCE(' RC ' || rc_secondaire, '')
                  FROM succursales
                 WHERE workspace_id = ?1 AND parent_dossier_id = ?2 AND statut <> 'FERMEE'
                 ORDER BY created_at ASC
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, dossierId)
                .getResultList();
        List<String> out = new ArrayList<>(rows.size());
        for (Object r : rows) {
            if (r != null) out.add(r.toString());
        }
        return out;
    }

    /**
     * FERMETURE_SUCCURSALE — fin de la « fermeture fantome » (lot DIVERS, 2026-08-13).
     *
     * <p><b>Le defaut.</b> Quand la succursale n'existait pas en base (creee avant sa
     * persistance, juillet 2026), l'employe la saisissait a la main : le PV et l'annonce
     * etaient generes, le workflow se cloturait… et {@code closeSuccursale} sortait en
     * {@code return} faute d'{@code succursaleDbId}. AUCUNE ligne n'etait marquee FERMEE :
     * la succursale restait ACTIVE, donc re-proposee a la fermeture indefiniment. La
     * fermeture n'existait que sur le papier.
     *
     * <p><b>La correction.</b> Des la validation de l'etape 1, une succursale saisie
     * manuellement est CREEE en base (statut ACTIVE) a partir des donnees saisies, et son
     * id est injecte dans les donnees de l'etape. La suite du workflow se deroule alors
     * exactement comme pour une succursale listee, et la finalisation la ferme reellement.
     * Effet de bord voulu : la succursale legacy entre au passage dans le referentiel et
     * devient visible dans l'historique de sa societe mere.
     *
     * <p>{@code succursales} ne porte aucun index unique : l'INSERT ne peut pas violer de
     * contrainte, il reste donc dans la transaction de l'etape (pas de {@code REQUIRES_NEW}).
     */
    private Map<String, Object> withSuccursaleCreeeSiSaisieManuelle(UUID workspaceId,
                                                                     Map<String, Object> stepData) {
        if (pickString(stepData, "succursaleDbId") != null) return stepData; // deja en base
        UUID parentId = parseUuid(firstNonNull(
                pickString(stepData, "dossierId"), pickString(stepData, "societeMereId")));
        Map<String, Object> succ = pickMap(stepData, "succursale");
        String enseigne = pickString(succ, "enseigne");
        if (parentId == null || enseigne == null || enseigne.isBlank()) {
            log.warn("FERMETURE_SUCCURSALE : saisie manuelle inexploitable (parent ou enseigne "
                    + "absent) -> la succursale ne sera pas marquee FERMEE en base.");
            return stepData;
        }
        try {
            // Idempotence : l'etape 1 peut etre re-validee (navigation arriere pour corriger
            // une donnee). Sans cette relecture, chaque passage creerait un doublon — et un
            // seul serait ferme. `succursales` ne porte aucun index unique : la garde est
            // ici, pas dans le schema.
            UUID existant = findSuccursaleActive(workspaceId, parentId, enseigne);
            if (existant != null) {
                Map<String, Object> reuse = new HashMap<>(stepData);
                reuse.put("succursaleDbId", existant.toString());
                return reuse;
            }
            UUID id = UUID.randomUUID();
            em.createNativeQuery("""
                    INSERT INTO succursales
                      (id, workspace_id, parent_dossier_id, type, denomination, activite,
                       adresse, ville, rc_secondaire, statut)
                    VALUES (?1, ?2, ?3, 'MA', ?4, ?5, ?6, ?7, ?8, 'ACTIVE')
                    """)
                    .setParameter(1, id)
                    .setParameter(2, workspaceId)
                    .setParameter(3, parentId)
                    .setParameter(4, enseigne.trim())
                    .setParameter(5, pickString(succ, "activite"))
                    .setParameter(6, pickString(succ, "adresse"))
                    .setParameter(7, pickString(succ, "ville"))
                    .setParameter(8, pickString(succ, "rcNumero"))
                    .executeUpdate();
            log.info("FERMETURE_SUCCURSALE : succursale « {} » saisie manuellement -> creee en "
                    + "base ({}) pour que sa fermeture soit reelle", enseigne, id);
            Map<String, Object> enriched = new HashMap<>(stepData);
            enriched.put("succursaleDbId", id.toString());
            enriched.put("succursaleCreeeALaVolee", true);
            return enriched;
        } catch (Exception ex) {
            log.warn("FERMETURE_SUCCURSALE : creation de la succursale saisie manuellement "
                    + "echouee : {}", ex.getMessage());
            return stepData;
        }
    }

    /**
     * Fige le RC de la succursale saisi a l'etape 1 de la FERMETURE (cas de secours :
     * RC absent en base parce qu'attribue par le greffe apres l'ouverture). {@code COALESCE}
     * garantit qu'un RC deja connu n'est JAMAIS ecrase par la saisie.
     */
    private void persistRcSuccursale(UUID workspaceId, Map<String, Object> stepData) {
        UUID succId = parseUuid(pickString(stepData, "succursaleDbId"));
        String rc = firstNonNull(
                pickString(pickMap(stepData, "succursale"), "rcNumero"),
                pickString(stepData, "rcNumero"));
        if (succId == null || rc == null || rc.isBlank()) return;
        em.createNativeQuery("""
                UPDATE succursales SET rc_secondaire = COALESCE(rc_secondaire, ?3)
                 WHERE id = ?1 AND workspace_id = ?2
                """)
                .setParameter(1, succId)
                .setParameter(2, workspaceId)
                .setParameter(3, rc.trim())
                .executeUpdate();
    }

    /** Parse tolerant d'une date ISO (null / vide / mal formee -> null). */
    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    /** Parse tolerant d'un UUID (null / vide / mal forme -> null). */
    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Fin de workflow IMPORT (RG-IM07) : consolide la fiche juridique sur le
     * dossier auto-cree au depart du ticket (importStub -> statut ACTIVE).
     * Fix 2026-06-06 : avant ce patch, la fiche etait calculee cote workflow
     * mais jamais ecrite dans entreprise_dossiers, donc l'ICE / RC / IF / capital
     * saisis dans le wizard restaient invisibles dans le dossier.
     */
    @SuppressWarnings("unchecked")
    private void applyImportConsolidation(UUID workspaceId, UUID dossierId, Map<String, Object> data) {
        // Refonte 2026-06-25 : l'IMPORT reutilise le MEME tronc de saisie que la
        // CREATION. On lit donc les scalaires depuis les memes step bags
        // (denomination / siege / capital), et plus depuis l'ancien step1.info plat.
        Map<String, Object> denom = pickStep(data, "denomination");
        Map<String, Object> siege = pickStep(data, "siege");
        Map<String, Object> capitalStep = pickStep(data, "capital");
        if (denom.isEmpty()) {
            log.debug("IMPORT post-completion : step1.denomination absent, skip dossier={}", dossierId);
            return;
        }
        String raisonSociale = pickString(denom, "denomination");
        String ice = firstNonNull(pickString(denom, "ice"), pickString(denom, "icenumero"));
        String rcNumero = pickString(denom, "rcNumero");
        String ifNumero = pickString(denom, "ifNumero");
        String adresse = pickString(siege, "adresse");
        String ville = firstNonNull(pickString(siege, "commune"), pickString(siege, "ville"));
        BigDecimal capital = pickBigDecimal(capitalStep, "capitalSocialMad");

        // Backbone refonte statuts : consolide l'état structuré importé COMPLET
        // (objet/duree/gérance/dirigeants/associés/parts inclus) dans
        // fiche_structuree. COALESCE pour ne pas écraser une fiche déjà
        // renseignée si l'import ne fournit rien d'exploitable.
        String ficheJson = ficheToJson(buildFicheFromImport(data));

        em.createNativeQuery("""
                UPDATE entreprise_dossiers
                SET raison_sociale     = COALESCE(?1, raison_sociale),
                    ice                = COALESCE(?2, ice),
                    rc_numero          = COALESCE(?3, rc_numero),
                    identifiant_fiscal = COALESCE(?4, identifiant_fiscal),
                    adresse_siege      = COALESCE(?5, adresse_siege),
                    ville              = COALESCE(?6, ville),
                    capital_social_mad = COALESCE(?7, capital_social_mad),
                    fiche_structuree   = COALESCE(CAST(?10 AS jsonb), fiche_structuree),
                    statut             = 'ACTIVE',
                    updated_at         = NOW()
                WHERE id = ?8 AND workspace_id = ?9
                """)
                .setParameter(1, raisonSociale)
                .setParameter(2, ice)
                .setParameter(3, rcNumero)
                .setParameter(4, ifNumero)
                .setParameter(5, adresse)
                .setParameter(6, ville)
                .setParameter(7, capital)
                .setParameter(8, dossierId)
                .setParameter(9, workspaceId)
                .setParameter(10, ficheJson)
                .executeUpdate();
    }

    /**
     * Consolide le dossier a la COMPLETION du workflow CREATION (fix 2026-08-12).
     *
     * <p><b>Pourquoi</b> : le dossier est cree en <em>stub</em> par ticket-service des la
     * creation du ticket ({@code DossierIdempotenceLookup.getOrCreateAlive}) — donc
     * {@link #createEntrepriseDossier} sort en idempotence (<em>skip INSERT</em>) et la
     * {@code fiche_structuree} n'etait <b>jamais</b> ecrite : les associes et gerants
     * saisis aux etapes 5/6 du wizard restaient invisibles en base, rendant impossible le
     * pre-remplissage dans le workflow MODIFICATION (associes / gerance / presence).
     *
     * <p>On ecrit donc ici l'etat structure complet (associes, gerants + alias
     * {@code gerants}, objet, duree, parts…) + les scalaires (ICE, adresse, ville,
     * capital) sur le dossier existant, et on passe le statut a {@code ACTIVE}
     * (comportement precedent conserve). Idempotent : re-soumettre la derniere etape
     * reecrit simplement les memes valeurs.
     */
    private void applyCreationConsolidation(UUID workspaceId, UUID dossierId,
                                            Map<String, Object> data) {
        Map<String, Object> denom = pickStep(data, "denomination");
        Map<String, Object> siege = pickStep(data, "siege");
        Map<String, Object> capitalStep = pickStep(data, "capital");

        String raisonSociale = pickString(denom, "denomination");
        String ice = firstNonNull(pickString(denom, "ice"), pickString(denom, "icenumero"));
        String adresse = pickString(siege, "adresse");
        String ville = firstNonNull(pickString(siege, "commune"), pickString(siege, "ville"));
        BigDecimal capital = pickBigDecimal(capitalStep, "capitalSocialMad");

        // La fiche n'est ecrite que si elle porte de VRAIES donnees (au-dela de la seule
        // cle technique "source") — sinon on laisse la fiche existante intacte (COALESCE).
        Map<String, Object> fiche = buildFicheFromCreation(data);
        String ficheJson = (fiche != null && fiche.size() > 1) ? ficheToJson(fiche) : null;

        em.createNativeQuery("""
                UPDATE entreprise_dossiers
                SET raison_sociale     = COALESCE(?1, raison_sociale),
                    ice                = COALESCE(?2, ice),
                    adresse_siege      = COALESCE(?3, adresse_siege),
                    ville              = COALESCE(?4, ville),
                    capital_social_mad = COALESCE(?5, capital_social_mad),
                    fiche_structuree   = COALESCE(CAST(?6 AS jsonb), fiche_structuree),
                    statut             = 'ACTIVE',
                    updated_at         = NOW()
                WHERE id = ?7 AND workspace_id = ?8
                """)
                .setParameter(1, raisonSociale)
                .setParameter(2, ice)
                .setParameter(3, adresse)
                .setParameter(4, ville)
                .setParameter(5, capital)
                .setParameter(6, ficheJson)
                .setParameter(7, dossierId)
                .setParameter(8, workspaceId)
                .executeUpdate();
        log.info("CREATION consolidation dossier={} : fiche_structuree {} (associes/gerants persistes)",
                dossierId, ficheJson != null ? "ecrite" : "inchangee");
    }

    /**
     * @deprecated Defense-in-depth : preferer {@link #fetchDossierId(UUID, UUID)}
     *             qui ajoute le filtre {@code workspace_id} explicite. La RLS
     *             Postgres n'est pas fiable ici car {@code jurika_user} a
     *             BYPASSRLS dans le conteneur officiel — un ticket d'un autre
     *             workspace pouvait fuiter via cette query non scopee.
     *             Conserve pour ne pas casser d'eventuels appelants legacy.
     */
    @Deprecated
    private UUID fetchDossierId(UUID ticketId) {
        // 2026-08-16 (fix D2) — getResultStream().findFirst() lève une NPE quand la
        // ligne existe mais que dossier_id vaut NULL (Optional ne peut contenir null) :
        // c'est le cas des tickets MODIFICATION / DISSOLUTION / LIQUIDATION qui ne
        // portent pas de dossier lié. getResultList() tolère l'élément nul.
        List<?> rows = em.createNativeQuery("SELECT dossier_id FROM tickets WHERE id = ?1")
                .setParameter(1, ticketId)
                .getResultList();
        Object result = rows.isEmpty() ? null : rows.get(0);
        if (result == null) return null;
        return (result instanceof UUID u) ? u : UUID.fromString(result.toString());
    }

    /**
     * Variante workspace-scoped (defense-in-depth) : ne renvoie le dossier_id
     * que si le ticket appartient effectivement au workspace de l'actor.
     * Ferme la chaine d'exploitation cross-tenant decrite en RG-SAAS-01.
     */
    private UUID fetchDossierId(UUID workspaceId, UUID ticketId) {
        // 2026-08-16 (fix D2) — cf. surcharge ci-dessus : dossier_id NULL sur une
        // ligne existante faisait NPE via findFirst(). getResultList() renvoie
        // proprement l'élément nul, on retourne alors null (repli step1.dossierId
        // géré par applyPostCompletion).
        List<?> rows = em.createNativeQuery(
                "SELECT dossier_id FROM tickets WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, ticketId)
                .setParameter(2, workspaceId)
                .getResultList();
        Object result = rows.isEmpty() ? null : rows.get(0);
        if (result == null) return null;
        return (result instanceof UUID u) ? u : UUID.fromString(result.toString());
    }

    /**
     * Charge les faits dossier (denomination, formeJuridique, ICE, RC, capital,
     * adresse, ville, statut) pour le dossier lie au ticket. Resultat injecte
     * dans {@code existingData.dossier} pour les validations RG des strategies
     * MODIFICATION / DISSOLUTION / LIQUIDATION.
     *
     * <p>Best-effort : retourne map vide si pas de dossier lie, ou si erreur SQL.
     */
    /**
     * Lit le dossierId choisi par l'employé à l'étape 1 (PROMPT F 2026-06-23).
     * Priorité au step1 persisté ; fallback sur le payload courant si on est
     * dans l'exécution de l'étape 1 elle-même.
     */
    @SuppressWarnings("unchecked")
    private static String readChosenDossierId(Map<String, Object> persisted, int step,
                                              Map<String, Object> payload) {
        Object step1 = persisted == null ? null : persisted.get("step1");
        if (step1 instanceof Map<?, ?> m1) {
            Object v = ((Map<String, Object>) m1).get("dossierId");
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        if (step == 1 && payload != null) {
            Object v = payload.get("dossierId");
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    /**
     * Charge les faits dossier directement par id (PROMPT F 2026-06-23). Utilisé
     * quand l'utilisateur a explicitement sélectionné une société à l'étape 1,
     * en remplacement (ou complément) de {@link #loadDossierFactsByTicket}.
     *
     * <p>Best-effort identique : retourne Map.of() en cas de pépin SQL pour ne
     * pas empoisonner la transaction du save() qui suit.
     */
    private Map<String, Object> loadDossierFactsById(UUID workspaceId, UUID dossierId) {
        if (dossierId == null) return Map.of();
        try {
            Object[] row = (Object[]) em.createNativeQuery("""
                    SELECT raison_sociale, forme_juridique, ice, rc_numero, rc_tribunal,
                           capital_social_mad, adresse_siege, ville, statut,
                           fiche_structuree, date_dissolution
                    FROM entreprise_dossiers
                    WHERE id = ?1 AND workspace_id = ?2
                    """)
                    .setParameter(1, dossierId)
                    .setParameter(2, workspaceId)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (row == null) return Map.of();
            return toDossierFacts(dossierId, row);
        } catch (Exception ex) {
            log.warn("loadDossierFactsById SQL failed dossier={} : {}",
                    dossierId, ex.getMessage());
            try { em.clear(); } catch (Exception ignore) { /* defensive */ }
            return Map.of();
        }
    }

    /**
     * Aplatit une ligne {@code entreprise_dossiers} en « faits dossier » injectes dans
     * {@code existingData.dossier} — le contrat que lisent les strategies de workflow.
     *
     * <p><b>Lot Liquidation 4 etapes (2026-08-13)</b> : ces faits portent desormais aussi
     * la <b>date de dissolution</b> et le <b>liquidateur</b> (+ siege de la liquidation)
     * issus de la fiche structuree. Sans eux, {@code LiquidationWorkflow} ne pouvait pas
     * relire ce que la Dissolution avait persiste et bloquait a l'etape 1
     * (« La date de dissolution est introuvable en base »). Detecte par l'E2E sur stack
     * reelle : les tests unitaires de la strategie fabriquaient ces faits a la main et ne
     * pouvaient donc pas voir que le producteur ne les fournissait pas.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toDossierFacts(UUID dossierId, Object[] row) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("dossierId", dossierId.toString());
        if (row[0] != null) facts.put("denomination", row[0].toString());
        if (row[1] != null) facts.put("formeJuridique", row[1].toString());
        if (row[2] != null) facts.put("ice", row[2].toString());
        if (row[3] != null) facts.put("rcNumero", row[3].toString());
        if (row[4] != null) facts.put("rcTribunal", row[4].toString());
        if (row[5] != null) facts.put("capitalSocial", row[5]);
        if (row[6] != null) facts.put("adresseSiege", row[6].toString());
        if (row[7] != null) facts.put("ville", row[7].toString());
        if (row[8] != null) facts.put("statut", row[8].toString());
        Map<String, Object> fiche = ficheFromJson(row[9]);
        if (fiche != null) {
            facts.put("ficheStructuree", fiche);
            // Nomme a la DISSOLUTION, relu par la LIQUIDATION (zero re-saisie).
            Object liquidateur = fiche.get("liquidateur");
            if (liquidateur instanceof Map<?, ?> lm && !lm.isEmpty()) {
                facts.put("liquidateur", (Map<String, Object>) lm);
            }
            Object siege = fiche.get("siegeLiquidation");
            if (siege != null && !String.valueOf(siege).isBlank()) {
                facts.put("siegeLiquidation", String.valueOf(siege));
            }
        }
        // Colonne dediee, ecrite a la completion du workflow DISSOLUTION.
        if (row.length > 10 && row[10] != null) {
            facts.put("dateDissolution", String.valueOf(row[10]));
        }
        return facts;
    }

    private Map<String, Object> loadDossierFactsByTicket(UUID workspaceId, UUID ticketId) {
        // Defense-in-depth : fetchDossierId(workspaceId, ticketId) ne renvoie le
        // dossier_id QUE si le ticket appartient au workspace. Sans ce filtre,
        // un user A peut amorcer un workflow_progress sur un ticket de B
        // (cf RG-SAAS-01 — la RLS ne couvre pas car jurika_user a BYPASSRLS).
        UUID dossierId = fetchDossierId(workspaceId, ticketId);
        if (dossierId == null) return Map.of();
        // NB : on isole strictement la SELECT du reste de la transaction. Une erreur
        // SQL ici (colonne absente, ...) marquerait la transaction outer rollback-only
        // et casserait le progressRepository.save() qui suit. Le catch retourne un
        // map vide -- les strategies fonctionnent en mode degrade sans dossier injecte.
        try {
            // Colonnes alignees sur la table publique entreprise_dossiers
            // (cf migration V*__dossier_schema.sql). rc_ville n'existe pas ;
            // le pendant est rc_tribunal (ville du tribunal de commerce).
            // Filtre workspace_id explicite (defense-in-depth) : la RLS peut etre
            // contournee par BYPASSRLS sur le role jurika_user du conteneur.
            Object[] row = (Object[]) em.createNativeQuery("""
                    SELECT raison_sociale, forme_juridique, ice, rc_numero, rc_tribunal,
                           capital_social_mad, adresse_siege, ville, statut,
                           fiche_structuree, date_dissolution
                    FROM entreprise_dossiers
                    WHERE id = ?1 AND workspace_id = ?2
                    """)
                    .setParameter(1, dossierId)
                    .setParameter(2, workspaceId)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (row == null) return Map.of();
            return toDossierFacts(dossierId, row);
        } catch (Exception ex) {
            log.warn("loadDossierFactsByTicket SQL failed dossier={} : {}",
                    dossierId, ex.getMessage());
            // L'exception JPA a marque la transaction rollback-only. On force un clear
            // pour eviter que le `progressRepository.save` suivant ne propage l'etat
            // casse de la session (-> 500). Le contexte transactionnel reste intact ;
            // seule la session est videe.
            try { em.clear(); } catch (Exception ignore) { /* defensive */ }
            return Map.of();
        }
    }

    /**
     * Defense-in-depth multi-tenant : filtre {@code workspace_id} obligatoire
     * sur tout UPDATE entreprise_dossiers (RLS Postgres non fiable -- jurika_user
     * a BYPASSRLS dans le conteneur officiel). Sans ce filtre, un EMPLOYE
     * authentifie workspace A pouvait muter le statut d'un dossier workspace B
     * en aiguillant son ticket vers un dossierId externe (cross-tenant write
     * — cf chaine d'exploitation RG-SAAS-01).
     */
    private void updateDossierStatut(UUID workspaceId, UUID dossierId, String newStatut) {
        em.createNativeQuery(
                "UPDATE entreprise_dossiers SET statut = ?1, updated_at = NOW() " +
                        "WHERE id = ?2 AND workspace_id = ?3")
                .setParameter(1, newStatut)
                .setParameter(2, dossierId)
                .setParameter(3, workspaceId)
                .executeUpdate();
    }

    /**
     * Lot W2 (2026-07-04) : a la cloture du workflow DISSOLUTION, on passe le
     * dossier en statut DISSOUTE ET on persiste la {@code date_dissolution}
     * (date d'effet de l'AGE de dissolution). Cette date est ensuite exposee par
     * dataroom-service pour afficher, dans la selection LIQUIDATION, le badge
     * « delai 16 j » (RG-LI03) et pre-remplir l'etape sans re-saisie manuelle.
     * Meme garde-fou multi-tenant que {@link #updateDossierStatut} (filtre
     * workspace_id, RLS non fiable sous jurika_user BYPASSRLS).
     */
    private void updateDossierDissolution(UUID workspaceId, UUID dossierId, LocalDate dateDissolution) {
        em.createNativeQuery(
                "UPDATE entreprise_dossiers SET statut = 'DISSOUTE', " +
                        "date_dissolution = ?1, updated_at = NOW() " +
                        "WHERE id = ?2 AND workspace_id = ?3")
                .setParameter(1, dateDissolution)
                .setParameter(2, dossierId)
                .setParameter(3, workspaceId)
                .executeUpdate();
    }

    /**
     * Extrait la date d'effet de la dissolution des donnees accumulees du
     * workflow (step1.dateAGE, saisie a l'etape 1 de DISSOLUTION). A defaut de
     * date exploitable, retombe sur {@link LocalDate#now()} pour garantir une
     * valeur non nulle en base.
     */
    private LocalDate resolveDateDissolution(Map<String, Object> data) {
        Map<String, Object> step1 = pickStep(data, "step1");
        String raw = pickString(step1, "dateAGE");
        if (raw == null) raw = pickString(step1, "dateDissolution");
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDate.parse(raw.trim());
            } catch (java.time.format.DateTimeParseException ignore) {
                log.debug("resolveDateDissolution : date non parsable '{}', fallback now()", raw);
            }
        }
        return LocalDate.now();
    }

    // ========================================================================
    //  Lot « Liquidation 4 etapes » (2026-08-13) — LIQUIDATEUR PERSISTE
    //
    //  Le liquidateur est nomme A LA DISSOLUTION (etape 1 du workflow Dissolution).
    //  Jusqu'ici seuls `statut = DISSOUTE` et `date_dissolution` etaient ecrits : la
    //  liquidation devait donc RE-SAISIR integralement le liquidateur (nom, CIN,
    //  nationalite, adresse, acceptation), en violation de l'interdiction de re-saisir
    //  une donnee deja connue.
    //
    //  On le persiste desormais dans `fiche_structuree` (JSONB) — coherent avec le
    //  backbone existant (associes / gerants / capital y vivent deja) et sans migration
    //  de schema. Cles canoniques : `liquidateur` (objet) + `siegeLiquidation` (scalaire).
    //  Le workflow LIQUIDATION les relit via DossierIdentityQueryService et les affiche
    //  en LECTURE SEULE.
    // ========================================================================

    /**
     * Persiste le liquidateur (et le siege de la liquidation) saisis a l'etape 1 d'un
     * workflow, dans {@code fiche_structuree}. Best-effort : sans liquidateur exploitable
     * dans les donnees du workflow, no-op (la fiche existante reste intacte).
     */
    private void persistLiquidateurFromStep1(UUID workspaceId, UUID dossierId,
                                             Map<String, Object> data) {
        Map<String, Object> step1 = pickStep(data, "step1");
        persistLiquidateur(workspaceId, dossierId, step1.get("liquidateur"),
                pickString(step1, "siegeLiquidation"));
    }

    /**
     * Ecrit {@code liquidateur} + {@code siegeLiquidation} dans la {@code fiche_structuree}
     * du dossier (merge : les autres cles de la fiche sont preservees).
     *
     * <p>Best-effort et non bloquant : une erreur ici ne doit jamais faire echouer la
     * finalisation du workflow (l'appelant est deja sous {@code try/catch}, on protege
     * neanmoins la session JPA par un {@code em.clear()}).
     */
    @SuppressWarnings("unchecked")
    void persistLiquidateur(UUID workspaceId, UUID dossierId, Object liquidateurRaw,
                            String siegeLiquidation) {
        if (dossierId == null) return;
        if (!(liquidateurRaw instanceof Map<?, ?> liqMap) || liqMap.isEmpty()) {
            log.debug("persistLiquidateur : aucun liquidateur exploitable dossier={} -> no-op",
                    dossierId);
            return;
        }
        Map<String, Object> liquidateur = new HashMap<>((Map<String, Object>) liqMap);
        // Le siege de la liquidation voyage a la fois dans le bloc liquidateur (cle
        // `siege`, consommee telle quelle par les mappers ai-service) et a la racine.
        String siege = firstNonNull(
                blankToNull(siegeLiquidation),
                blankToNull(pickString(liquidateur, "siege")));
        if (siege != null) liquidateur.put("siege", siege);

        try {
            Map<String, Object> fiche = new HashMap<>();
            Map<String, Object> cur = ficheFromJson(readFicheJson(workspaceId, dossierId));
            if (cur != null) fiche.putAll(cur);
            fiche.put("liquidateur", liquidateur);
            if (siege != null) fiche.put("siegeLiquidation", siege);
            String json = ficheToJson(fiche);
            if (json == null) return;
            em.createNativeQuery(
                            "UPDATE entreprise_dossiers SET fiche_structuree = CAST(?1 AS jsonb), "
                                    + "updated_at = NOW() WHERE id = ?2 AND workspace_id = ?3")
                    .setParameter(1, json).setParameter(2, dossierId).setParameter(3, workspaceId)
                    .executeUpdate();
            log.info("liquidateur persiste dossier={} (siege liquidation {})",
                    dossierId, siege != null ? "renseigne" : "absent");
        } catch (Exception ex) {
            log.warn("persistLiquidateur echoue dossier={} : {}", dossierId, ex.getMessage());
            try { em.clear(); } catch (Exception ignore) { /* defensive */ }
        }
    }

    /**
     * Lit le {@code fiche_structuree} brut d'un dossier, ou {@code null}.
     *
     * <p><b>Ne PAS utiliser {@code getResultStream().findFirst()} ici</b> :
     * {@link java.util.stream.Stream#findFirst()} leve une {@code NullPointerException}
     * quand le premier element est {@code null} — ce qui est precisement le cas d'un
     * dossier dont la fiche est encore {@code NULL} en base. Le bug etait silencieux
     * (exception avalee par le {@code catch} appelant) et empechait toute premiere
     * ecriture de fiche. Detecte par {@code LiquidateurPersistenceIT} (2026-08-13) ;
     * {@code getResultList()} tolere, lui, les valeurs nulles.
     */
    private Object readFicheJson(UUID workspaceId, UUID dossierId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT fiche_structuree FROM entreprise_dossiers "
                                + "WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, dossierId).setParameter(2, workspaceId)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank() || "null".equals(s)) ? null : s.trim();
    }

    /** Chaine non vide d'une valeur brute, ou {@code null} (vide / "null" inclus). */
    private static String strOrNull(Object v) {
        return v == null ? null : blankToNull(String.valueOf(v));
    }

    /**
     * Applique les modifications cochees sur le dossier d'entreprise.
     * Pour chaque type de modification, met a jour le ou les champs correspondants.
     */
    @SuppressWarnings("unchecked")
    private void applyModifications(UUID workspaceId, UUID dossierId, Map<String, Object> data) {
        Map<String, Object> step2 = pickStep(data, "step2");
        if (step2.isEmpty() && data.get("valeurs") instanceof Map<?, ?> direct) {
            step2 = (Map<String, Object>) direct;
        }
        Map<String, Map<String, Object>> valeurs = new HashMap<>();
        Object valeursObj = step2.get("valeurs");
        if (valeursObj instanceof Map<?, ?> valeursMap) {
            for (Map.Entry<?, ?> e : valeursMap.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> v) {
                    valeurs.put(String.valueOf(e.getKey()), (Map<String, Object>) v);
                }
            }
        } else {
            log.debug("Pas de valeurs trouvees pour appliquer la modification dossier={}", dossierId);
        }
        // 1) Colonnes scalaires (raison_sociale / adresse / capital / forme).
        for (Map.Entry<String, Map<String, Object>> entry : valeurs.entrySet()) {
            applyOneModification(workspaceId, dossierId, entry.getKey(), entry.getValue());
        }
        // 2) Backbone refonte statuts (2026-06-24) : persiste l'ETAT STRUCTURE COMPLET
        // (objet/duree/gerance/associes inclus) dans fiche_structuree, pour que les
        // modifications suivantes partent d'un état à jour.
        try {
            applyFicheStructureeUpdate(workspaceId, dossierId, data, valeurs);
        } catch (Exception ex) {
            log.warn("MAJ fiche_structuree post-modif échouée dossier={} : {}", dossierId, ex.getMessage());
            try { em.clear(); } catch (Exception ignore) { /* defensive */ }
        }
    }

    /**
     * Recalcule et persiste {@code fiche_structuree} après une MODIFICATION :
     * état courant (DB) ⊕ saisies preflight ({@code step3.ficheOverrides}) ⊕
     * overlay des nouvelles valeurs des modifications. Clés canoniques camelCase
     * (identiques à {@link #buildFicheFromCreation}).
     */
    @SuppressWarnings("unchecked")
    private void applyFicheStructureeUpdate(UUID workspaceId, UUID dossierId,
                                            Map<String, Object> data,
                                            Map<String, Map<String, Object>> valeurs) {
        Map<String, Object> fiche = new HashMap<>();
        // (a) état courant en base (lecture tolérante au JSONB NULL — cf. readFicheJson).
        Map<String, Object> cur = ficheFromJson(readFicheJson(workspaceId, dossierId));
        if (cur != null) fiche.putAll(cur);
        // (b) saisies preflight (champs structurés complétés à l'étape 3).
        Map<String, Object> step3 = pickStep(data, "step3");
        Object ovr = step3.get("ficheOverrides");
        if (ovr instanceof Map<?, ?> ovrMap) {
            for (Map.Entry<?, ?> e : ovrMap.entrySet()) {
                if (e.getValue() != null && !String.valueOf(e.getValue()).isBlank()) {
                    fiche.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        // (c) overlay des nouvelles valeurs (mêmes règles que l'assembleur ai-service).
        for (Map.Entry<String, Map<String, Object>> entry : valeurs.entrySet()) {
            overlayFiche(entry.getKey(), entry.getValue(), fiche);
        }
        if (fiche.isEmpty()) return;
        String json = ficheToJson(fiche);
        if (json == null) return;
        em.createNativeQuery(
                        "UPDATE entreprise_dossiers SET fiche_structuree = CAST(?1 AS jsonb), "
                                + "updated_at = NOW() WHERE id = ?2 AND workspace_id = ?3")
                .setParameter(1, json).setParameter(2, dossierId).setParameter(3, workspaceId)
                .executeUpdate();
    }

    /** Applique la nouvelle valeur d'UNE modification sur la fiche (clés canoniques). */
    @SuppressWarnings("unchecked")
    private static void overlayFiche(String type, Map<String, Object> v, Map<String, Object> fiche) {
        if (type == null || v == null) return;
        switch (type) {
            case "CHANGEMENT_DENOMINATION" -> putIfNotNull(fiche, "denomination", strVal(v.get("nouvelleDenomination")));
            case "CHANGEMENT_OBJET" -> putIfNotNull(fiche, "objetSocial", strVal(v.get("nouvelObjet")));
            case "TRANSFERT_SIEGE" -> {
                putIfNotNull(fiche, "adresseSiege", strVal(v.get("nouvelleAdresse")));
                putIfNotNull(fiche, "ville", strVal(v.get("nouvelleVille")));
            }
            case "PROROGATION_DUREE" -> {
                BigDecimal ans = numVal(v.get("annees"));
                if (ans != null) fiche.put("dureeAnnees", ans.longValue());
            }
            case "AUGMENTATION_CAPITAL" -> {
                BigDecimal nc = numVal(v.get("nouveauCapital"));
                if (nc != null) fiche.put("capitalSocial", nc);
                BigDecimal np = numVal(v.get("nouvellesParts"));
                if (np != null) {
                    BigDecimal cur = numVal(fiche.get("nombreParts"));
                    fiche.put("nombreParts", (cur == null ? BigDecimal.ZERO : cur).add(np));
                }
            }
            case "AUGMENTATION_CAPITAL_RESERVES" -> {
                BigDecimal inc = numVal(v.get("montantIncorporation"));
                BigDecimal cur = numVal(fiche.get("capitalSocial"));
                if (inc != null && cur != null) fiche.put("capitalSocial", cur.add(inc));
            }
            case "REDUCTION_CAPITAL" -> {
                BigDecimal red = numVal(v.get("montantReduction"));
                BigDecimal cur = numVal(fiche.get("capitalSocial"));
                if (red != null && cur != null) fiche.put("capitalSocial", cur.subtract(red).max(BigDecimal.ZERO));
            }
            case "MODIF_VALEUR_NOMINALE" -> {
                BigDecimal vn = numVal(v.get("nouvelleValeurNominale"));
                if (vn != null) fiche.put("valeurNominale", vn);
            }
            case "TRANSFORMATION" -> putIfNotNull(fiche, "formeJuridique", strVal(v.get("nouvelleForme")));
            case "DESIGNATION_GERANT" -> {
                Map<String, Object> g = new HashMap<>();
                putIfNotNull(g, "nom", strVal(v.get("nom")));
                putIfNotNull(g, "prenom", strVal(v.get("prenom")));
                putIfNotNull(g, "cinNumero", strVal(v.get("cin")));
                putIfNotNull(g, "nationalite", strVal(v.get("nationalite")));
                if (!g.isEmpty()) {
                    List<Object> gerants = fiche.get("gerants") instanceof List<?> l
                            ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
                    gerants.add(g);
                    fiche.put("gerants", gerants);
                }
            }
            case "REVOCATION_GERANT" -> {
                String ident = strVal(v.get("identite"));
                if (ident != null && fiche.get("gerants") instanceof List<?> l) {
                    String low = ident.toLowerCase(java.util.Locale.ROOT);
                    List<Object> kept = new ArrayList<>();
                    for (Object o : l) {
                        String nom = o instanceof Map<?, ?> gm ? strVal(((Map<String, Object>) gm).get("nom")) : null;
                        if (nom == null || nom.isBlank() || !low.contains(nom.toLowerCase(java.util.Locale.ROOT))) {
                            kept.add(o);
                        }
                    }
                    fiche.put("gerants", kept);
                }
            }
            case "CESSION_PARTIELLE", "CESSION_TOTALE", "TRANSMISSION_PARTS" ->
                    // Phase E2 — nouvelle répartition des parts. Best-effort : le
                    // formulaire ne capture pas l'identité du cédant (seulement le
                    // cessionnaire + le nombre de parts), donc la soustraction cédant −N
                    // n'est pas dérivable ici ; on crédite le cessionnaire (cédant −N /
                    // cessionnaire +N impossible sans évolution du modèle de données).
                    crediterCessionnaire(fiche, strVal(v.get("cessionnaire")), numVal(v.get("nombreParts")));
            default -> { /* clauses / pacte / formalités : pas de champ structuré d'en-tête impacté */ }
        }
    }

    /** Crédite {@code parts} au cessionnaire dans {@code fiche.associes} (apparié par nom, sinon ajouté). */
    @SuppressWarnings("unchecked")
    private static void crediterCessionnaire(Map<String, Object> fiche, String cessionnaire, BigDecimal parts) {
        if (cessionnaire == null || cessionnaire.isBlank() || parts == null || parts.signum() <= 0) return;
        String low = cessionnaire.toLowerCase(java.util.Locale.ROOT).trim();
        // Copie mutable de chaque associé (les maps issues du JSON peuvent être immuables).
        List<Map<String, Object>> associes = new ArrayList<>();
        if (fiche.get("associes") instanceof List<?> l) {
            for (Object o : l) {
                associes.add(o instanceof Map<?, ?> mm ? new HashMap<>((Map<String, Object>) mm) : new HashMap<>());
            }
        }
        Map<String, Object> cible = null;
        for (Map<String, Object> a : associes) {
            String prenomNom = ((a.get("prenom") == null ? "" : a.get("prenom") + " ")
                    + (a.get("nom") == null ? "" : a.get("nom"))).trim();
            for (Object cand : new Object[]{a.get("nom"), a.get("denomination"), prenomNom}) {
                if (cand == null) continue;
                String c = cand.toString().toLowerCase(java.util.Locale.ROOT).trim();
                if (!c.isBlank() && (c.equals(low) || low.contains(c) || c.contains(low))) { cible = a; break; }
            }
            if (cible != null) break;
        }
        if (cible == null) {
            cible = new HashMap<>();
            cible.put("nom", cessionnaire);
            associes.add(cible);
        }
        BigDecimal cur = numVal(cible.get("nombreParts"));
        if (cur == null) cur = numVal(cible.get("partsChiffres"));
        cible.put("nombreParts", (cur == null ? BigDecimal.ZERO : cur).add(parts));
        fiche.put("associes", associes);
    }

    private static String strVal(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal numVal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(o.toString().trim().replace(" ", "").replace("_", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Defense-in-depth multi-tenant : tous les UPDATE entreprise_dossiers sont
     * filtres par {@code workspace_id = ?} en plus de l'{@code id}. Sans ce
     * garde-fou, un EMPLOYE workspace A pouvait muter le dossier d'un cabinet
     * tiers B en aiguillant son ticket vers un dossierId externe (chaine
     * d'exploitation cross-tenant, RG-SAAS-01). La RLS Postgres n'est PAS
     * fiable ici : {@code jurika_user} a BYPASSRLS dans le conteneur officiel.
     */
    private void applyOneModification(UUID workspaceId, UUID dossierId, String type, Map<String, Object> v) {
        switch (type) {
            case "CHANGEMENT_DENOMINATION" -> {
                String nouvelle = pickString(v, "nouvelleDenomination");
                if (nouvelle != null && !nouvelle.isBlank()) {
                    em.createNativeQuery("UPDATE entreprise_dossiers SET raison_sociale = ?1, updated_at = NOW() WHERE id = ?2 AND workspace_id = ?3")
                            .setParameter(1, nouvelle.trim()).setParameter(2, dossierId).setParameter(3, workspaceId).executeUpdate();
                }
            }
            case "TRANSFERT_SIEGE" -> {
                String adresse = pickString(v, "nouvelleAdresse");
                String ville = pickString(v, "nouvelleVille");
                if (adresse != null || ville != null) {
                    em.createNativeQuery("""
                            UPDATE entreprise_dossiers
                            SET adresse_siege = COALESCE(?1, adresse_siege),
                                ville = COALESCE(?2, ville),
                                updated_at = NOW()
                            WHERE id = ?3 AND workspace_id = ?4
                            """)
                            .setParameter(1, adresse).setParameter(2, ville).setParameter(3, dossierId).setParameter(4, workspaceId)
                            .executeUpdate();
                }
            }
            case "AUGMENTATION_CAPITAL", "AUGMENTATION_CAPITAL_NATURE", "AUGMENTATION_CAPITAL_RESERVES" -> {
                BigDecimal nouveauCapital = pickBigDecimal(v, "nouveauCapital");
                if (nouveauCapital == null) nouveauCapital = pickBigDecimal(v, "valeurApport");
                if (nouveauCapital != null) {
                    em.createNativeQuery("UPDATE entreprise_dossiers SET capital_social_mad = ?1, updated_at = NOW() WHERE id = ?2 AND workspace_id = ?3")
                            .setParameter(1, nouveauCapital).setParameter(2, dossierId).setParameter(3, workspaceId).executeUpdate();
                }
            }
            case "REDUCTION_CAPITAL" -> {
                BigDecimal reduction = pickBigDecimal(v, "montantReduction");
                if (reduction != null) {
                    em.createNativeQuery("""
                            UPDATE entreprise_dossiers
                            SET capital_social_mad = GREATEST(capital_social_mad - ?1, 0),
                                updated_at = NOW()
                            WHERE id = ?2 AND workspace_id = ?3
                            """)
                            .setParameter(1, reduction).setParameter(2, dossierId).setParameter(3, workspaceId).executeUpdate();
                }
            }
            case "MISE_EN_SOMMEIL" -> updateDossierStatut(workspaceId, dossierId, "EN_LIQUIDATION");
            case "TRANSFORMATION" -> {
                String nouvForme = pickString(v, "nouvelleForme");
                if (nouvForme != null && isValidForme(nouvForme)) {
                    em.createNativeQuery(
                            "UPDATE entreprise_dossiers SET forme_juridique = ?1, updated_at = NOW() WHERE id = ?2 AND workspace_id = ?3")
                            .setParameter(1, nouvForme).setParameter(2, dossierId).setParameter(3, workspaceId).executeUpdate();
                }
            }
            // RG-M11 : cession dans une SARL_AU -> passage automatique en SARL pluri.
            // applique uniquement si le dossier est encore SARL_AU au moment du commit.
            case "CESSION_PARTIELLE", "CESSION_TOTALE", "TRANSMISSION_PARTS" ->
                    em.createNativeQuery("""
                            UPDATE entreprise_dossiers
                            SET forme_juridique = 'SARL', updated_at = NOW()
                            WHERE id = ?1 AND workspace_id = ?2 AND forme_juridique = 'SARL_AU'
                            """)
                            .setParameter(1, dossierId).setParameter(2, workspaceId).executeUpdate();
            default -> log.debug("Modification {} : pas d'update auto du dossier (champ libre)", type);
        }
    }

    private static boolean isValidForme(String forme) {
        return forme.equals("SARL") || forme.equals("SARL_AU")
                || forme.equals("SA") || forme.equals("SAS")
                || forme.equals("SCS") || forme.equals("GIE");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pickStep(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        // Fallback : data is the step N container with {"denomination": {...}}
        for (Object value : data.values()) {
            if (value instanceof Map<?, ?> m && ((Map<String, Object>) m).containsKey(key)) {
                Object inner = ((Map<String, Object>) m).get(key);
                if (inner instanceof Map<?, ?> im) return (Map<String, Object>) im;
            }
        }
        return Map.of();
    }

    private String pickString(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private BigDecimal pickBigDecimal(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========================================================================
    //  Fiche structuree JSONB (backbone refonte statuts 2026-06-24)
    //  Etat structure complet (objet, duree, gerance, associes, parts, capital,
    //  siege, denomination...) persiste sur entreprise_dossiers.fiche_structuree.
    //  Source UNIQUE de verite pour generer le statut COMPLET refondu en
    //  MODIFICATION (jamais le scan). Schema canonique en cles camelCase.
    //  Alimentee a la CREATION + a l'IMPORT ; mise a jour apres MODIFICATION.
    // ========================================================================

    /** Sérialise une fiche en JSON, ou {@code null} si vide / erreur (best-effort). */
    private static String ficheToJson(Map<String, Object> fiche) {
        if (fiche == null || fiche.isEmpty()) return null;
        try {
            return FICHE_MAPPER.writeValueAsString(fiche);
        } catch (Exception e) {
            log.warn("fiche_structuree serialization failed : {}", e.getMessage());
            return null;
        }
    }

    /** Parse le JSONB lu en base vers une Map (best-effort, {@code null} si vide / erreur). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> ficheFromJson(Object raw) {
        if (raw == null) return null;
        String s = raw.toString();
        if (s.isBlank()) return null;
        try {
            return FICHE_MAPPER.readValue(s, Map.class);
        } catch (Exception e) {
            log.warn("fiche_structuree parse failed : {}", e.getMessage());
            return null;
        }
    }

    /** Récupère une valeur (Map ou List) par clé : top-level puis dans les maps step{N}. */
    @SuppressWarnings("unchecked")
    private static Object pickWrappedRaw(Map<String, Object> data, String key) {
        if (data == null) return null;
        Object direct = data.get(key);
        if (direct != null) return direct;
        for (Object v : data.values()) {
            if (v instanceof Map<?, ?> m && ((Map<String, Object>) m).containsKey(key)) {
                return ((Map<String, Object>) m).get(key);
            }
        }
        return null;
    }

    private static void putIfNotNull(Map<String, Object> m, String key, Object v) {
        if (v != null) m.put(key, v);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }

    /**
     * Assemble la fiche structurée canonique depuis les données accumulées du
     * workflow CREATION (étapes denomination / siege / capital / activite +
     * blocs gerance / dirigeants / associes). {@code pickStep} déballe déjà les
     * étapes (stockées sous {@code step{N}}).
     */
    private Map<String, Object> buildFicheFromCreation(Map<String, Object> data) {
        Map<String, Object> denom = pickStep(data, "denomination");
        Map<String, Object> siege = pickStep(data, "siege");
        Map<String, Object> capital = pickStep(data, "capital");
        Map<String, Object> activite = pickStep(data, "activite");

        Map<String, Object> fiche = new HashMap<>();
        putIfNotNull(fiche, "denomination", pickString(denom, "denomination"));
        putIfNotNull(fiche, "formeJuridique",
                firstNonNull(pickString(denom, "formeJuridique"), pickString(data, "formeJuridique")));
        putIfNotNull(fiche, "ice", firstNonNull(pickString(denom, "ice"), pickString(denom, "icenumero")));
        putIfNotNull(fiche, "adresseSiege", pickString(siege, "adresse"));
        putIfNotNull(fiche, "ville", firstNonNull(pickString(siege, "commune"), pickString(siege, "ville")));
        putIfNotNull(fiche, "capitalSocial", pickBigDecimal(capital, "capitalSocialMad"));
        putIfNotNull(fiche, "valeurNominale", pickBigDecimal(capital, "valeurNominaleMad"));
        // L'objet social = description de l'activité (handleActivite exige "description").
        putIfNotNull(fiche, "objetSocial", firstNonNull(pickString(activite, "objet"), pickString(activite, "description")));
        putIfNotNull(fiche, "dureeAnnees", pickWrappedRaw(data, "dureeAnnees"));
        // Blocs structurants conservés tels quels (nécessaires à la refonte + au
        // pré-remplissage des associés/gérants dans le workflow MODIFICATION).
        Object dirigeants = pickWrappedRaw(data, "dirigeants");
        putIfNotNull(fiche, "gerance", pickWrappedRaw(data, "gerance"));
        putIfNotNull(fiche, "dirigeants", dirigeants);
        // Fix 2026-08-11 — alias `gerants` (clé canonique attendue par les lecteurs stricts
        // et par le pré-remplissage Modification), MANQUANT jusqu'ici côté CREATION alors
        // qu'IMPORT l'écrivait déjà (ImportWorkflow.handleSynthese) → 0 gérant en base.
        putIfNotNull(fiche, "gerants", dirigeants);
        putIfNotNull(fiche, "associes", pickWrappedRaw(data, "associes"));
        fiche.put("source", "CREATION");
        return fiche;
    }

    /**
     * Assemble la fiche structurée canonique depuis l'IMPORT. Refonte 2026-06-25 :
     * l'IMPORT partageant désormais le même tronc de saisie que la CREATION
     * (étapes denomination / siege / capital / activite + blocs gerance /
     * dirigeants / associes), on RÉUTILISE {@link #buildFicheFromCreation} pour
     * obtenir une fiche COMPLÈTE (objet/durée/gérance/associés/parts...), puis on
     * l'enrichit des identifiants propres aux sociétés existantes (RC + IF) et on
     * marque la source IMPORT. La fiche juridique consolidée à l'étape Synthèse
     * ({@code step11.synthese.ficheJuridique}) sert de filet de secours.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFicheFromImport(Map<String, Object> data) {
        Map<String, Object> fiche = buildFicheFromCreation(data);
        Map<String, Object> denom = pickStep(data, "denomination");

        Object fjRaw = pickWrappedRaw(data, "ficheJuridique");
        Map<String, Object> fj = fjRaw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();

        // Identifiants propres à une société immatriculée (absents de la Création).
        putIfNotNull(fiche, "rcNumero", firstNonNull(pickString(denom, "rcNumero"), pickString(fj, "rcNumero")));
        putIfNotNull(fiche, "identifiantFiscal",
                firstNonNull(pickString(denom, "ifNumero"), pickString(fj, "ifNumero")));
        // Filet de secours si une étape de saisie n'a pas été (re)validée.
        if (fiche.get("gerants") == null && fj.get("gerants") != null) fiche.put("gerants", fj.get("gerants"));
        if (fiche.get("associes") == null && fj.get("associes") != null) fiche.put("associes", fj.get("associes"));
        if (fiche.get("nombreParts") == null) {
            putIfNotNull(fiche, "nombreParts", pickBigDecimal(pickStep(data, "capital"), "nombreParts"));
        }
        fiche.put("source", "IMPORT");
        return fiche;
    }

    public record StepExecutionResult(WorkflowProgress progress, StepResult result) {}

    // ========================================================================
    //  P2 2026-06-04 — Pieces jointes persistantes cross-step
    //  Registry stocke dans workflow_progress.data.pieces[code] = { ... metadata }
    //  Le code (CN, JUSTIFICATIF_SIEGE, CIN_DIRIGEANTS, ...) est unique par ticket.
    //  Le fichier physique reste a charge de dataroom-service / MinIO (out of scope ici).
    // ========================================================================

    @Transactional
    @Auditable(action = "WORKFLOW_PIECE_REGISTERED", resourceType = "ticket", resourceIdExpr = "#ticketId")
    @SuppressWarnings("unchecked")
    public WorkflowProgress registerPiece(UUID workspaceId, UUID ticketId,
                                           ma.jurika.workflow.api.WorkflowController.RegisterPieceRequest req) {
        TenantContext.set(workspaceId);
        WorkflowProgress p = progressRepository.findByTicket(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Aucun workflow en cours"));
        Map<String, Object> merged = new HashMap<>(p.data());
        Map<String, Object> pieces = (Map<String, Object>) merged.getOrDefault("pieces", new HashMap<>());
        // Mutation defensive : on copie le sous-map pour eviter de muter une instance partagee.
        Map<String, Object> piecesCopy = new HashMap<>(pieces);
        Map<String, Object> entry = new HashMap<>();
        entry.put("code", req.code());
        entry.put("label", req.label());
        entry.put("filename", req.filename());
        entry.put("sizeBytes", req.sizeBytes());
        entry.put("contentType", req.contentType());
        entry.put("uploadedAtStep", req.uploadedAtStep());
        entry.put("uploadedAt", Instant.now().toString());
        piecesCopy.put(req.code(), entry);
        merged.put("pieces", piecesCopy);
        log.info("workflow.piece.register ticket={} code={} step={}",
                ticketId, req.code(), req.uploadedAtStep());
        return progressRepository.save(p.id(), p.currentStep(), merged, p.statut(), p.completedAt());
    }

    @Transactional
    @Auditable(action = "WORKFLOW_PIECE_UNREGISTERED", resourceType = "ticket", resourceIdExpr = "#ticketId")
    @SuppressWarnings("unchecked")
    public WorkflowProgress unregisterPiece(UUID workspaceId, UUID ticketId, String code) {
        TenantContext.set(workspaceId);
        WorkflowProgress p = progressRepository.findByTicket(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Aucun workflow en cours"));
        Map<String, Object> merged = new HashMap<>(p.data());
        Object piecesObj = merged.get("pieces");
        if (piecesObj instanceof Map<?, ?> piecesMap) {
            Map<String, Object> piecesCopy = new HashMap<>((Map<String, Object>) piecesMap);
            piecesCopy.remove(code);
            merged.put("pieces", piecesCopy);
        }
        log.info("workflow.piece.unregister ticket={} code={}", ticketId, code);
        return progressRepository.save(p.id(), p.currentStep(), merged, p.statut(), p.completedAt());
    }

    // ========================================================================
    //  P3 2026-06-04 — Transitions auto du ticket (NOUVEAU -> EN_COURS -> CLOTURE)
    //  Implementation : UPDATE direct sur la table tickets (meme DB Postgres),
    //  identique au pattern existant (createEntrepriseDossier, linkTicketToDossier).
    //  Idempotent : seule la transition autorisee {from -> to} mute le statut.
    //  Audit : INSERT dans audit_log avec action distincte + metadata.reason.
    //  Erreurs : log warn, ne JAMAIS throw (le workflow lui-meme ne doit pas casser
    //  parce que la transition auto a echoue -- le user peut transitionner manuellement).
    // ========================================================================

    /**
     * Transitionne le ticket de {@code expectedFrom} -> {@code newStatut} via une
     * UPDATE conditionnelle. Si le ticket n'est pas dans {@code expectedFrom}, rien
     * ne se passe (no-op). Trace systematiquement la tentative en audit_log avec
     * le compte de lignes affectees (utile pour distinguer transition reelle de no-op).
     */
    private void autoTransitionTicket(UUID workspaceId, UUID ticketId, UUID userId,
                                       String expectedFrom, String newStatut, String reason) {
        try {
            int updated = em.createNativeQuery(
                    "UPDATE tickets SET statut = ?1, " +
                            "cloture_at = CASE WHEN ?1 = 'CLOTURE_DOSSIER' THEN NOW() ELSE cloture_at END, " +
                            "updated_at = NOW() " +
                            "WHERE id = ?2 AND workspace_id = ?3 AND statut = ?4")
                    .setParameter(1, newStatut)
                    .setParameter(2, ticketId)
                    .setParameter(3, workspaceId)
                    .setParameter(4, expectedFrom)
                    .executeUpdate();
            if (updated == 1) {
                log.info("ticket.auto-transition {} {} -> {} (reason={})",
                        ticketId, expectedFrom, newStatut, reason);
                // Audit -- best-effort, ne pas faire echouer la transition si l'audit casse.
                try {
                    em.createNativeQuery(
                            "INSERT INTO audit_log (workspace_id, user_id, action, entity_type, entity_id, metadata) " +
                                    "VALUES (?1, ?2, ?3, 'ticket', ?4, CAST(?5 AS jsonb))")
                            .setParameter(1, workspaceId)
                            .setParameter(2, userId)
                            .setParameter(3, "TICKET_AUTO_TRANSITION")
                            .setParameter(4, ticketId)
                            .setParameter(5, "{\"from\":\"" + expectedFrom + "\",\"to\":\"" + newStatut
                                    + "\",\"reason\":\"" + reason + "\"}")
                            .executeUpdate();
                } catch (Exception auditEx) {
                    log.warn("audit_log insert failed for ticket auto-transition: {}", auditEx.getMessage());
                }
            } else {
                log.debug("ticket.auto-transition no-op ticket={} expectedFrom={} (statut deja different)",
                        ticketId, expectedFrom);
            }
        } catch (Exception ex) {
            // Best-effort -- ne casse jamais le workflow.
            log.warn("ticket.auto-transition failed ticket={} {}->{} : {}",
                    ticketId, expectedFrom, newStatut, ex.getMessage());
        }
    }
}
