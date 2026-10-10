package ma.jurika.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fournit l'<b>identité société complète</b> d'un dossier immatriculé, à partir de
 * la table {@code entreprise_dossiers} (colonnes) <b>et</b> de son {@code fiche_structuree}
 * JSONB (nombre de parts, valeur nominale, associés, gérants).
 *
 * <p>Sert de <b>source de vérité BD</b> pour l'en-tête des PV / actes : ai-service (qui
 * ne lit aucune base) enrichit son objet {@code societe} en appelant l'endpoint interne
 * {@link ma.jurika.workflow.api.InternalDossierController} qui délègue ici. Les clés
 * produites sont directement celles que lit {@code SeancePvVarsBuilder} côté ai-service
 * (double nommage {@code villeGreffe}/{@code rcVille}, {@code capitalChiffres}/{@code capitalSocial},
 * {@code siegeSocial}/{@code adresseSiege}) pour un merge trivial.
 *
 * <p>Best-effort : toute erreur SQL / de parse renvoie une map vide — la génération
 * reste possible en mode dégradé (l'appelant retombe sur ce que porte déjà le payload).
 * Filtre {@code workspace_id} explicite, en plus de la RLS (rôle {@code jurika_app},
 * lot L0) : les deux sont toujours exigés.
 */
@Service
public class DossierIdentityQueryService {

    private static final Logger log = LoggerFactory.getLogger(DossierIdentityQueryService.class);
    private static final ObjectMapper FICHE_MAPPER = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    /**
     * Charge l'identité société d'un dossier, aplatie pour l'objet {@code societe}.
     * Renvoie {@link Map#of()} si le dossier est introuvable dans le workspace.
     * Lot L3 (motif 9) : une erreur de lecture n'est plus avalee -- une map vide
     * faisait partir l'acte sans les donnees de la societe.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> identity(UUID workspaceId, UUID dossierId) {
        if (workspaceId == null || dossierId == null) return Map.of();
        // Lot L0 (E16, WF1) : le workspace courant est pose par l'appelant AVANT
        // la transaction (InternalDossierController) ; pose ici, il arrivait trop
        // tard pour la RLS, et le clear() final effacait le contexte de l'appelant.
        {
            Object[] row = (Object[]) em.createNativeQuery("""
                    SELECT raison_sociale, forme_juridique, ice, identifiant_fiscal,
                           rc_numero, rc_tribunal, capital_social_mad, adresse_siege,
                           ville, fiche_structuree, statut, date_dissolution,
                           taxe_professionnelle, cnss
                    FROM entreprise_dossiers
                    WHERE id = ?1 AND workspace_id = ?2
                    """)
                    .setParameter(1, dossierId)
                    .setParameter(2, workspaceId)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (row == null) return Map.of();

            Map<String, Object> s = new LinkedHashMap<>();
            putIfPresent(s, "denomination", row[0]);
            putIfPresent(s, "formeJuridique", row[1]);
            putIfPresent(s, "ice", row[2]);
            putIfPresent(s, "ifNumero", row[3]);
            putIfPresent(s, "rcNumero", row[4]);
            // rc_tribunal = ville du tribunal de commerce (greffe). Double nommage
            // pour couvrir les deux clés lues par le builder.
            putIfPresent(s, "villeGreffe", row[5]);
            putIfPresent(s, "rcVille", row[5]);
            if (row[6] != null) {
                // capital_social_mad (BigDecimal) exposé sous les deux clés lues par le builder.
                s.put("capitalChiffres", row[6]);
                s.put("capitalSocial", row[6]);
            }
            putIfPresent(s, "siegeSocial", row[7]);
            putIfPresent(s, "adresseSiege", row[7]);
            putIfPresent(s, "ville", row[8]);
            putIfPresent(s, "statut", row[10]);
            // Lot Liquidation 4 etapes (2026-08-13) : la date de dissolution est en BASE
            // (ecrite a la completion du workflow DISSOLUTION) — la liquidation la LIT,
            // elle ne la re-saisit jamais.
            if (row[11] != null) s.put("dateDissolution", String.valueOf(row[11]));
            // Lot L3 (RG-VAR-08) : identifiants obtenus en fin de parcours, servis aux actes suivants.
            putIfPresent(s, "identifiantTp", row[12]);
            putIfPresent(s, "cnssNumero", row[13]);

            Map<String, Object> fiche = ficheFromJson(row[9]);
            if (fiche != null) {
                putIfPresent(s, "nombreParts", fiche.get("nombreParts"));
                putIfPresent(s, "valeurNominalePart", fiche.get("valeurNominale"));
                // Listes brutes — l'appelant ne s'en sert que pour amorcer la présence
                // quand le formulaire de séance n'a rien saisi (jamais pour écraser).
                Object associes = fiche.get("associes");
                if (associes != null) s.put("associes", associes);
                Object gerants = firstNonNull(fiche.get("gerants"), fiche.get("gerance"),
                        fiche.get("dirigeants"));
                if (gerants != null) s.put("gerants", gerants);
                // Liquidateur NOMME A LA DISSOLUTION + siege de la liquidation : source
                // unique de verite pour le workflow LIQUIDATION (zero re-saisie).
                Object liquidateur = fiche.get("liquidateur");
                if (liquidateur != null) s.put("liquidateur", liquidateur);
                putIfPresent(s, "siegeLiquidation", fiche.get("siegeLiquidation"));
            }
            return s;
        }
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object raw) {
        if (raw == null) return;
        if (raw instanceof String s && s.isBlank()) return;
        m.put(key, raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ficheFromJson(Object raw) {
        if (raw == null) return null;
        String s = raw.toString();
        if (s.isBlank()) return null;
        try {
            return FICHE_MAPPER.readValue(s, Map.class);
        } catch (Exception e) {
            // Lot L3 (motif 9) : une fiche illisible n'est plus traitee comme absente
            // (associes et gerants disparaissaient des actes sans un mot).
            throw new IllegalStateException("fiche_structuree illisible : " + e.getMessage(), e);
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }
}
