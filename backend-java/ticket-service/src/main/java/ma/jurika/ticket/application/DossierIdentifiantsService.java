package ma.jurika.ticket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.api.dto.DossierIdentifiantsDtos.DossierIdentifiantsView;
import ma.jurika.ticket.api.dto.DossierIdentifiantsDtos.UpdateIdentifiantsRequest;
import ma.jurika.ticket.infrastructure.persistence.DossierEntity;
import ma.jurika.ticket.infrastructure.persistence.DossierJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import ma.jurika.ticket.domain.model.DeadlineRule;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Fiche client (2026-07-14) — edition des identifiants post-immatriculation
 * d'une societe (RC, identifiant fiscal, patente, CNSS, adresse, capital...).
 *
 * <p>ticket-service est le proprietaire transactionnel de {@code entreprise_dossiers}
 * (le dataroom-service n'en a qu'une vue read-only), d'ou la localisation ici.
 *
 * <p>Acces (RG-U02-04) : reserve a l'EMPLOYE <b>responsable du dossier</b>. Le
 * SUPERVISEUR consulte tout en lecture seule (403 sur ce PATCH), de meme que le
 * SUPER_ADMIN et le CLIENT. La generation de la Fiche client / de l'etat des
 * debours (lecture) reste ouverte au superviseur.
 */
@Service
public class DossierIdentifiantsService {

    private static final Logger log = LoggerFactory.getLogger(DossierIdentifiantsService.class);
    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    /** Sérialisation du miroir {@code fiche_structuree} (mêmes clés camelCase que workflow-service). */
    private static final ObjectMapper FICHE_MAPPER = new ObjectMapper();

    private final DossierJpaRepository dossiers;
    private final DeadlineUseCase deadlineUseCase;

    @PersistenceContext
    private EntityManager em;

    public DossierIdentifiantsService(DossierJpaRepository dossiers, DeadlineUseCase deadlineUseCase) {
        this.dossiers = dossiers;
        this.deadlineUseCase = deadlineUseCase;
    }

    @Transactional
    @Auditable(action = "DOSSIER_IDENTIFIANTS_UPDATED", resourceType = "dossier",
               resourceIdExpr = "#dossierId")
    public DossierIdentifiantsView update(UUID workspaceId, Role role, UUID userId,
                                          UUID dossierId, UpdateIdentifiantsRequest req) {
        TenantContext.set(workspaceId);
        DossierEntity d = dossiers.findByWorkspaceIdAndId(workspaceId, dossierId)
                .orElseThrow(() -> new NotFoundException("Dossier introuvable"));

        // RG-U02-04 : edition reservee a l'EMPLOYE responsable. Defense-in-depth :
        // le controller filtre deja par ROLE_EMPLOYE, on re-verrouille ici.
        if (role != Role.EMPLOYE) {
            throw new AccessDeniedException(
                    "L'édition des identifiants est réservée à l'employé responsable "
                            + "(RG-U02-04 : le superviseur est en lecture seule).");
        }
        UUID resp = d.getResponsableId();
        if (resp == null || !resp.equals(userId)) {
            throw new AccessDeniedException(
                    "Seul le responsable du dossier peut modifier ses identifiants.");
        }

        BigDecimal capital = req.capitalSocialMad();
        if (capital != null && capital.signum() < 0) {
            throw new ValidationException("Le capital social ne peut pas etre negatif.");
        }

        // "RC obtenu" : detection de l'immatriculation (numero RC passant de vide a renseigne).
        // C'est le seul evenement metier disponible ici pour ancrer l'echeance CNSS.
        boolean rcJustObtained = norm(d.getRcNumero()) == null && norm(req.rcNumero()) != null;

        d.setIce(norm(req.ice()));
        d.setRcNumero(norm(req.rcNumero()));
        d.setRcTribunal(norm(req.rcTribunal()));
        d.setIdentifiantFiscal(norm(req.identifiantFiscal()));
        d.setTaxeProfessionnelle(norm(req.taxeProfessionnelle()));
        d.setCnss(norm(req.cnss()));
        d.setAdresseSiege(norm(req.adresseSiege()));
        d.setVille(norm(req.ville()));
        d.setCapitalSocialMad(capital);
        d.setDateConstitution(req.dateConstitution());
        DossierEntity saved = dossiers.save(d);

        // Miroir obligatoire dans fiche_structuree (cf. syncFicheStructuree).
        syncFicheStructuree(workspaceId, dossierId, saved);

        if (rcJustObtained) {
            wireCnssDeadline(saved, userId);
        }

        log.info("Identifiants du dossier {} mis a jour par {} ({})", dossierId, userId, role);
        return view(saved);
    }

    /**
     * Répercute les identifiants édités ici dans le miroir {@code fiche_structuree} (JSONB).
     *
     * <p><b>Pourquoi c'est indispensable</b> : l'identité société existe sous DEUX
     * représentations — les colonnes scalaires (source d'autorité) et les clés homonymes de
     * {@code fiche_structuree}, écrites par les workflows CRÉATION / IMPORT / MODIFICATION.
     * Or {@code RefonteStatutsVarsBuilder} (ai-service), qui régénère les <b>statuts
     * refondus</b>, lit la fiche <b>EN PRIORITÉ</b> et ne retombe sur l'identité issue des
     * colonnes qu'à défaut. Sans cette synchronisation, corriger un RC / ICE / IF / capital /
     * siège ici laissait la fiche périmée — et c'est la valeur périmée qui se retrouvait dans
     * un acte juridique.
     *
     * <p>Une seule saisie met donc à jour les deux représentations, ce qui évite au reste du
     * code d'avoir à arbitrer entre elles. Les autres clés de la fiche (associés, gérants,
     * objet social, parts…) sont préservées : c'est un merge, jamais un remplacement.
     *
     * <p>Best-effort : une erreur ici ne doit pas annuler la mise à jour des identifiants
     * elle-même (les colonnes restent la source d'autorité pour l'en-tête des actes).
     */
    private void syncFicheStructuree(UUID workspaceId, UUID dossierId, DossierEntity d) {
        try {
            java.util.List<?> rows = em.createNativeQuery(
                            "SELECT fiche_structuree FROM entreprise_dossiers "
                                    + "WHERE id = ?1 AND workspace_id = ?2")
                    .setParameter(1, dossierId).setParameter(2, workspaceId)
                    .getResultList();
            Object raw = rows.isEmpty() ? null : rows.get(0);

            Map<String, Object> fiche = new java.util.HashMap<>();
            if (raw != null && !raw.toString().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cur = FICHE_MAPPER.readValue(raw.toString(), Map.class);
                fiche.putAll(cur);
            }

            // Clés canoniques camelCase — identiques à celles écrites par workflow-service.
            putOrRemove(fiche, "ice", d.getIce());
            putOrRemove(fiche, "rcNumero", d.getRcNumero());
            putOrRemove(fiche, "identifiantFiscal", d.getIdentifiantFiscal());
            putOrRemove(fiche, "adresseSiege", d.getAdresseSiege());
            putOrRemove(fiche, "ville", d.getVille());
            putOrRemove(fiche, "denomination", d.getRaisonSociale());
            putOrRemove(fiche, "capitalSocial", d.getCapitalSocialMad());

            em.createNativeQuery(
                            "UPDATE entreprise_dossiers SET fiche_structuree = CAST(?1 AS jsonb), "
                                    + "updated_at = NOW() WHERE id = ?2 AND workspace_id = ?3")
                    .setParameter(1, FICHE_MAPPER.writeValueAsString(fiche))
                    .setParameter(2, dossierId)
                    .setParameter(3, workspaceId)
                    .executeUpdate();
        } catch (Exception ex) {
            log.warn("Synchronisation fiche_structuree echouee dossier={} : {}",
                    dossierId, ex.getMessage());
        }
    }

    /** Pose la valeur, ou RETIRE la clé si la valeur est vide (pas de miroir fantome). */
    private static void putOrRemove(Map<String, Object> fiche, String key, Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            fiche.remove(key);
        } else {
            fiche.put(key, value);
        }
    }

    /**
     * A l'obtention du RC, cree l'echeance CNSS (declaration d'affiliation dans les 30 jours
     * suivant l'immatriculation, {@link DeadlineRule#CNSS_DECL_30D}).
     *
     * <p>Ancrage : la date de constitution (= date RC) si saisie, sinon l'instant courant.
     * L'echeance est rattachee au ticket d'origine du dossier ({@code created_by_ticket_id})
     * afin (1) d'apparaitre dans le panneau "Echeances du ticket" de la creation et
     * (2) de rester idempotente (dedup par (ticket, regle) dans {@link DeadlineUseCase}).
     * Si le dossier n'a pas de ticket d'origine, on s'abstient (pas de rattachement fiable).
     * Best-effort : un echec n'invalide pas la mise a jour des identifiants.
     */
    private void wireCnssDeadline(DossierEntity d, UUID userId) {
        UUID ticketId = d.getCreatedByTicketId();
        if (ticketId == null) {
            log.debug("Dossier {} sans ticket d'origine : echeance CNSS non rattachee", d.getId());
            return;
        }
        Instant anchor = d.getDateConstitution() != null
                ? d.getDateConstitution().atStartOfDay(CASABLANCA).toInstant()
                : Instant.now();
        try {
            deadlineUseCase.computeAuto(new DeadlineUseCase.AutoComputeCommand(
                    d.getWorkspaceId(), ticketId, d.getId(),
                    DeadlineRule.CNSS_DECL_30D, anchor, userId,
                    Map.of("trigger", "rc_obtenu", "rc", d.getRcNumero())));
        } catch (Exception ex) {
            log.warn("Echec calcul echeance CNSS pour dossier {}: {}", d.getId(), ex.getMessage());
        }
    }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static DossierIdentifiantsView view(DossierEntity d) {
        return new DossierIdentifiantsView(
                d.getId(), d.getRaisonSociale(), d.getFormeJuridique(), d.getIce(),
                d.getRcNumero(), d.getRcTribunal(), d.getIdentifiantFiscal(),
                d.getTaxeProfessionnelle(), d.getCnss(), d.getAdresseSiege(), d.getVille(),
                d.getCapitalSocialMad(), d.getDateConstitution(), d.getStatut());
    }
}
