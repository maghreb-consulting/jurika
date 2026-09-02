package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.ExerciceFiscalSummary;
import ma.jurika.dataroom.application.fiscal.EcheancesGenerator;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.ComptableJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalEntity;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 8 -- Service de gestion du cycle de vie des exercices fiscaux.
 * Transitions OUVERT -> CLOTURE -> VERROUILLE et generation auto des
 * echeances DGI (RG-DF20) a l'ouverture.
 *
 * RG-DF25 : transitions exclusives + audit log obligatoire.
 * RG-DF26 : deverrouillage reserve ROLE_SUPERVISEUR + motif >= 20 chars.
 */
@Service
public class ExerciceFiscalService {

    private final ExerciceFiscalJpaRepository exercices;
    private final AlerteEcheanceJpaRepository alertes;
    private final EcheancesGenerator generator;
    private final ComptableJpaRepository comptable;
    /** Lot DIVERS §A — pas de nouvel exercice (donc pas de nouvelles echeances) sur une societe archivee. */
    private final DossierArchiveGuard archiveGuard;

    public ExerciceFiscalService(ExerciceFiscalJpaRepository exercices,
                                  AlerteEcheanceJpaRepository alertes,
                                  EcheancesGenerator generator,
                                  ComptableJpaRepository comptable,
                                  DossierArchiveGuard archiveGuard) {
        this.exercices = exercices;
        this.alertes = alertes;
        this.generator = generator;
        this.comptable = comptable;
        this.archiveGuard = archiveGuard;
    }

    // ----- Lectures -----

    @Transactional(readOnly = true)
    public List<ExerciceFiscalSummary> listForDossier(UUID dossierId) {
        return exercices.findAllByDossierIdOrderByAnneeDesc(dossierId)
                .stream().map(this::toSummary).toList();
    }

    /**
     * Prompt H (2026-06-23) — résout l'exercice fiscal d'une année donnée pour
     * un dossier, ou le crée à la volée s'il n'existe pas (statut OUVERT,
     * dates Jan 1 → Dec 31, échéances DGI générées en regime TVA mensuel par
     * défaut). Idempotent : aucun risque de double création grâce à la clause
     * UNIQUE(workspace_id, dossier_id, annee) côté schema.
     *
     * <p>Utilisé par les imports d'anciens dossiers (Prompt G/H) où l'employé
     * fournit une année brute sans avoir ouvert au préalable l'exercice.
     */
    @Transactional
    public ExerciceFiscalEntity findOrCreateByAnnee(UUID dossierId, short annee, UUID byUserId) {
        return exercices.findByDossierIdAndAnnee(dossierId, annee)
                .orElseGet(() -> {
                    open(dossierId, annee, null, null, true, byUserId);
                    return exercices.findByDossierIdAndAnnee(dossierId, annee)
                            .orElseThrow(() -> new NotFoundException(
                                    "Echec creation exercice " + annee));
                });
    }

    // ----- Transitions -----

    /**
     * Ouverture LENIENT (rétrocompatible) : crée l'ancre comptable de l'année si
     * absente puis génère les échéances fiscales. Utilisé par les imports
     * ({@link #findOrCreateByAnnee}) et par les tests historiques où l'exercice
     * est créé de toutes pièces. N'impose PAS la conformité comptable préalable.
     */
    @Transactional
    @Auditable(action = "EXERCICE_OPENED", resourceType = "exercice_fiscal",
            resourceIdExpr = "#dossierId")
    public ExerciceFiscalSummary open(UUID dossierId, short annee,
                                       LocalDate dateDebut, LocalDate dateFin,
                                       boolean regimeTvaMensuel, UUID byUserId) {
        return doOpen(dossierId, annee, dateDebut, dateFin, regimeTvaMensuel, false);
    }

    /**
     * Ouverture CONFORME au comptable (RG-DF03). Le COMPTABLE est la timeline
     * maîtresse : un exercice fiscal pour l'année Y exige qu'une année comptable Y
     * existe (sinon {@link ValidationException}), et REPREND les dates de l'exercice
     * comptable correspondant (conformité des périodes) tout en gardant ses
     * attributs propres (échéances DGI générées selon {@code regimeTvaMensuel}).
     *
     * @param autoCreateComptable si {@code true} (finalisation import/création), l'ancre
     *        comptable de l'année est créée à la volée AVANT d'attacher le fiscal,
     *        afin de ne jamais bloquer la finalisation. Si {@code false} (ouverture
     *        manuelle depuis l'onglet Fiscal), l'absence d'année comptable est rejetée.
     */
    @Transactional
    @Auditable(action = "EXERCICE_OPENED", resourceType = "exercice_fiscal",
            resourceIdExpr = "#dossierId")
    public ExerciceFiscalSummary open(UUID dossierId, short annee,
                                       LocalDate dateDebut, LocalDate dateFin,
                                       boolean regimeTvaMensuel, boolean autoCreateComptable,
                                       UUID byUserId) {
        return doOpen(dossierId, annee, dateDebut, dateFin, regimeTvaMensuel, !autoCreateComptable);
    }

    private ExerciceFiscalSummary doOpen(UUID dossierId, short annee,
                                         LocalDate dateDebut, LocalDate dateFin,
                                         boolean regimeTvaMensuel, boolean requireComptable) {
        if (annee < 2000 || annee > 2100) {
            throw new ValidationException("Annee invalide");
        }
        // Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : elle
        // ne declare plus. On refuse d'ouvrir un nouvel exercice, ce qui empeche a
        // la source la generation de nouvelles echeances DGI.
        archiveGuard.assertWritable(dossierId);

        // Le COMPTABLE est la timeline maîtresse : on attache le fiscal à l'exercice
        // (ancre comptable) de l'année. Si la ligne existe déjà (créée par un upload
        // comptable ou une ouverture comptable antérieure), on la RÉUTILISE — c'est ce
        // qui rend le fiscal "conforme" (mêmes dates) et corrige l'ancien bug où un
        // upload comptable préalable faisait échouer l'ouverture fiscale (EXERCICE_EXISTS).
        ExerciceFiscalEntity row = exercices.findByDossierIdAndAnnee(dossierId, annee).orElse(null);
        if (row == null) {
            if (requireComptable && !comptableYearExists(dossierId, annee)) {
                throw new ValidationException(
                        "RG-DF03 : l'exercice fiscal doit etre conforme aux annees comptables. "
                                + "L'annee " + annee + " n'est pas tenue en comptabilite "
                                + "(annees comptables disponibles : " + describeComptableYears(dossierId)
                                + "). Ouvrez d'abord l'annee comptable " + annee + ".");
            }
            // Ancre comptable absente -> on la crée (autoCreateComptable, ou backfill
            // d'une année comptable existante sans ligne). Dates alignées sur la demande
            // ou, à défaut, année civile.
            LocalDate db = dateDebut != null ? dateDebut : LocalDate.of(annee, 1, 1);
            LocalDate df = dateFin != null ? dateFin : LocalDate.of(annee, 12, 31);
            if (!df.isAfter(db)) {
                throw new ValidationException("date_fin doit etre apres date_debut");
            }
            row = new ExerciceFiscalEntity();
            row.setWorkspaceId(TenantContext.get());
            row.setDossierId(dossierId);
            row.setAnnee(annee);
            row.setDateDebut(db);
            row.setDateFin(df);
            row.setStatut("OUVERT");
            row.setDateOuverture(Instant.now());
            exercices.save(row);
        }
        // Conformité des périodes : le fiscal REPREND les dates de l'ancre comptable
        // (déjà portées par `row`) — on ne force jamais 01/01–31/12 aveuglément.

        // RG-DF20 : EXERCICE_EXISTS si les échéances DGI ont déjà été générées
        // (l'exercice fiscal est déjà ouvert). On distingue ainsi une ligne
        // comptable "nue" (à enrichir) d'un exercice fiscal déjà ouvert (doublon).
        if (!alertes.findByExerciceFiscalId(row.getId()).isEmpty()) {
            throw new BusinessException("EXERCICE_EXISTS",
                    "Un exercice fiscal " + annee + " existe deja pour ce dossier "
                            + "(echeances DGI deja generees).");
        }
        List<AlerteEcheanceEntity> echeances = generator.generateForExercice(row, regimeTvaMensuel);
        alertes.saveAll(echeances);

        return toSummary(row);
    }

    /**
     * Année "tenue en comptabilité" : soit une ligne exercice existe déjà (ancre
     * comptable ou exercice ouvert), soit au moins un document comptable porte cette
     * année (cas legacy d'un document sans ligne d'exercice).
     */
    private boolean comptableYearExists(UUID dossierId, short annee) {
        return exercices.findByDossierIdAndAnnee(dossierId, annee).isPresent()
                || comptable.findDistinctYears(dossierId).contains(annee);
    }

    /** Liste lisible des années comptables disponibles pour les messages d'erreur. */
    private String describeComptableYears(UUID dossierId) {
        java.util.TreeSet<Short> years = new java.util.TreeSet<>(comptable.findDistinctYears(dossierId));
        exercices.findAllByDossierIdOrderByAnneeDesc(dossierId)
                .forEach(e -> years.add(e.getAnnee()));
        return years.isEmpty() ? "aucune" : years.toString();
    }

    @Transactional
    @Auditable(action = "EXERCICE_CLOTURED", resourceType = "exercice_fiscal",
            resourceIdExpr = "#exerciceId")
    public ExerciceFiscalSummary cloturer(UUID exerciceId, UUID byUserId) {
        ExerciceFiscalEntity e = mustExist(exerciceId);
        if (!"OUVERT".equals(e.getStatut())) {
            throw new BusinessException("INVALID_TRANSITION",
                    "RG-DF25 : seul un exercice OUVERT peut etre CLOTURE.");
        }
        e.setStatut("CLOTURE");
        e.setDateCloture(Instant.now());
        e.setCloturePar(byUserId);
        return toSummary(e);
    }

    @Transactional
    @Auditable(action = "EXERCICE_LOCKED", resourceType = "exercice_fiscal",
            resourceIdExpr = "#exerciceId")
    public ExerciceFiscalSummary verrouiller(UUID exerciceId, UUID byUserId) {
        ExerciceFiscalEntity e = mustExist(exerciceId);
        if (!"OUVERT".equals(e.getStatut()) && !"CLOTURE".equals(e.getStatut())) {
            throw new BusinessException("INVALID_TRANSITION",
                    "RG-DF25 : seul un exercice OUVERT ou CLOTURE peut etre VERROUILLE.");
        }
        e.setStatut("VERROUILLE");
        return toSummary(e);
    }

    /**
     * RG-DF26 : deverrouillage reserve ROLE_SUPERVISEUR. RBAC verifie cote controller.
     * Motif obligatoire min 20 chars + audit log.
     */
    @Transactional
    @Auditable(action = "EXERCICE_UNLOCKED", resourceType = "exercice_fiscal",
            resourceIdExpr = "#exerciceId")
    public ExerciceFiscalSummary deverrouiller(UUID exerciceId, String motif, UUID byUserId) {
        if (motif == null || motif.trim().length() < 20) {
            throw new ValidationException(
                    "RG-DF26 : motif obligatoire (>= 20 caracteres) pour deverrouiller un exercice.");
        }
        ExerciceFiscalEntity e = mustExist(exerciceId);
        if (!"VERROUILLE".equals(e.getStatut())) {
            throw new BusinessException("EXERCICE_NOT_LOCKED",
                    "RG-DF26 : exercice non VERROUILLE.");
        }
        e.setStatut("OUVERT");
        String prev = e.getNote() == null ? "" : e.getNote() + "\n";
        e.setNote(prev + "Deverrouillage " + Instant.now() + " par=" + byUserId
                + " : " + motif.trim());
        return toSummary(e);
    }

    private ExerciceFiscalEntity mustExist(UUID id) {
        return exercices.findById(id)
                .orElseThrow(() -> new NotFoundException("Exercice fiscal inconnu : " + id));
    }

    private ExerciceFiscalSummary toSummary(ExerciceFiscalEntity e) {
        return new ExerciceFiscalSummary(
                e.getId(), e.getAnnee(),
                e.getDateDebut() != null ? e.getDateDebut().toString() : null,
                e.getDateFin() != null ? e.getDateFin().toString() : null,
                e.getStatut(), e.getDateOuverture(), e.getDateCloture());
    }
}
