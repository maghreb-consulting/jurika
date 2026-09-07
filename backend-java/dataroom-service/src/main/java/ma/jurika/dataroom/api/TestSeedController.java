package ma.jurika.dataroom.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sprint 14 ter E2 -- endpoint seed pour les tests e2e Playwright.
 *
 * <p>Cree en une seule transaction un workspace + un utilisateur EMPLOYE
 * (email verifie, password connu, sans 2FA initial) + un dossier
 * entreprise. Le response retourne tous les IDs et le
 * mot de passe en clair pour permettre au test Playwright de se connecter
 * directement.
 *
 * <h2>Securite</h2>
 * <ul>
 *   <li><b>Profil</b> : actif uniquement hors {@code prod} (annotation
 *       {@code @Profile("!prod")}).</li>
 *   <li><b>Property</b> : opt-in explicite via
 *       {@code jurika.test.seed.enabled=true}. Defaut = OFF.</li>
 *   <li><b>Audit</b> : log INFO a chaque appel avec l'IP cliente (pas de
 *       PII en clair sauf password de demonstration).</li>
 * </ul>
 *
 * <p>En production, les 2 verrous (profil + property) garantissent que
 * l'endpoint ne sera jamais exposable meme via un mauvais routage gateway.
 *
 * <p>Le mot de passe par defaut peut etre surcharge via
 * {@code jurika.test.seed.password} (defaut = "DemoPwd2026!"). Le test
 * Playwright reutilise cette valeur via une env variable miroir.
 */
@RestController
@RequestMapping("/api/v1/test/seed")
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.test.seed.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Test Seed", description = "Sprint 14 ter E2 -- seed pour Playwright (hors prod uniquement)")
public class TestSeedController {

    private static final Logger log = LoggerFactory.getLogger(TestSeedController.class);
    private static final String DEFAULT_PASSWORD = "DemoPwd2026!";

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public TestSeedController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/workspace")
    @Operation(summary = "Seed un workspace + user + dossier + exercice + 4 documents (e2e Playwright)")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedWorkspace(
            // Sprint 11 TASK 7 : params trial optionnels pour e2e trial-lifecycle.spec.ts.
            @org.springframework.web.bind.annotation.RequestParam(required = false) String trialStatus,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer trialDaysOffset,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String selectedPlan,
            // 2026-06-04 (fix bug D) : permet aux scripts seed "cabinet demo amis" de
            // creer un SUPERVISEUR (admin cabinet : acces dashboard + billing) au lieu
            // du EMPLOYE par defaut conserve pour la compat tests Playwright.
            // Valeurs : EMPLOYE | SUPERVISEUR | SUPER_ADMIN | CLIENT.
            @org.springframework.web.bind.annotation.RequestParam(required = false) String role) {
        String adminRole = (role == null || role.isBlank()) ? "EMPLOYE" : role.trim().toUpperCase();
        if (!java.util.Set.of("EMPLOYE", "SUPERVISEUR", "SUPER_ADMIN", "CLIENT").contains(adminRole)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "BAD_REQUEST",
                    "message", "role invalide : " + role
                            + " (attendu EMPLOYE | SUPERVISEUR | SUPER_ADMIN | CLIENT)"));
        }

        UUID workspaceId = UUID.randomUUID();
        UUID employeId   = UUID.randomUUID();
        UUID clientId    = UUID.randomUUID();
        UUID dossierId   = UUID.randomUUID();

        String code = generateWorkspaceCode();
        String email = "demo-" + code.toLowerCase().replace("-", "") + "@jurika.test";
        String passwordHash = bcrypt.encode(DEFAULT_PASSWORD);

        // 1. Workspace.
        jdbc.update("""
                INSERT INTO workspaces(id, code, name, contact_email, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """,
                workspaceId, code, "Cabinet Demo " + code, email);

        // 2. User principal (role parametrable, defaut EMPLOYE).
        // 2026-06-04 (fix P2 boucle) : twofa_method='TOTP' + email_verified_at=NOW()
        // pour que User.requires2faSetup() retourne FALSE (= ce user a deja "choisi"
        // sa methode 2FA et a deja verifie son email, donc le Setup2faRequiredEnforcer
        // ne le bloque pas). totp_enabled reste FALSE pour que le login skip la
        // verification TOTP -- coherent avec le comportement "demo 2FA desactive".
        // BUG 7 (2026-06-08, V28) : INSERT EXPLICITE de login_email + contact_email.
        // Pour les seed e2e on garde 1:1 avec email (l'identifiant historique reste
        // utilisable, simplifie les fixtures Playwright qui pretappent "email"
        // dans le formulaire de login).
        jdbc.update("""
                INSERT INTO users(id, workspace_id, email, login_email, contact_email,
                                   password_hash, first_name, last_name,
                                   role, totp_enabled, twofa_method, email_verified_at,
                                   must_change_password, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 'TOTP', NOW(), FALSE, TRUE, NOW(), NOW())
                """,
                employeId, workspaceId, email, email, email,
                passwordHash, "Demo",
                adminRole.equals("EMPLOYE") ? "Employe"
                    : adminRole.equals("SUPERVISEUR") ? "Superviseur"
                    : adminRole.equals("SUPER_ADMIN") ? "SuperAdmin" : "Client",
                adminRole);

        // 3. User CLIENT (pour exercice du dashboard CLIENT en e2e).
        String clientEmail = "demo-client-" + code.toLowerCase().replace("-", "") + "@jurika.test";
        jdbc.update("""
                INSERT INTO users(id, workspace_id, email, login_email, contact_email,
                                   password_hash, first_name, last_name,
                                   role, totp_enabled, twofa_method, email_verified_at,
                                   must_change_password, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CLIENT', FALSE, 'TOTP', NOW(), FALSE, TRUE, NOW(), NOW())
                """,
                clientId, workspaceId, clientEmail, clientEmail, clientEmail,
                passwordHash, "Demo", "Client");

        // 4. Dossier entreprise.
        // Lot Q (scoping EMPLOYE) : on renseigne responsable_id = employeId (le
        // user principal du seed, proprietaire naturel). Sans ca le dossier
        // naissait avec responsable_id NULL -> invisible pour les employes (seul
        // le superviseur le voyait) et non rattrapable par le backfill V11 (qui
        // joint sur tickets, or le seed n'en cree pas).
        // fiche_structuree : associés + gérants + scalaires, pour que le workflow
        // MODIFICATION puisse pré-remplir sans re-saisie (démo / e2e).
        String ficheJson = """
                {
                  "source":"SEED","formeJuridique":"SARL","denomination":"SARL Demo %s",
                  "capitalSocial":100000,"valeurNominale":100,"nombreParts":1000,
                  "objetSocial":"Conseil, services et négoce","adresseSiege":"12 Rue Demo, Casablanca",
                  "associes":[
                    {"typePersonne":"PHYSIQUE","civilite":"M.","prenom":"Ahmed","nom":"Alaoui","cin":"BE123456","nationalite":"marocaine","adresse":"Casablanca","nombreParts":600},
                    {"typePersonne":"PHYSIQUE","civilite":"Mme","prenom":"Fatima","nom":"Bennani","cin":"BK987654","nationalite":"marocaine","adresse":"Rabat","nombreParts":400}
                  ],
                  "gerants":[
                    {"civilite":"M.","prenom":"Ahmed","nom":"Alaoui","cin":"BE123456","nationalite":"marocaine"}
                  ],
                  "dirigeants":[
                    {"civilite":"M.","prenom":"Ahmed","nom":"Alaoui","cin":"BE123456","nationalite":"marocaine"}
                  ]
                }
                """.formatted(code);
        jdbc.update("""
                INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique,
                                                  ice, rc_numero, statut, client_id, responsable_id,
                                                  capital_social_mad, adresse_siege, ville,
                                                  fiche_structuree, created_at, updated_at)
                VALUES (?, ?, ?, 'SARL', '001234567000001', 'RC-12345', 'ACTIVE', ?, ?,
                        100000, '12 Rue Demo, Casablanca', 'Casablanca',
                        CAST(? AS jsonb), NOW(), NOW())
                """,
                dossierId, workspaceId, "SARL Demo " + code, clientId, employeId, ficheJson);

        // Lot 1 (2026-09-04) -- les dossiers comptable et fiscal sont sortis du
        // perimetre produit : plus d'exercice fiscal ni de documents comptables /
        // fiscaux a semer. Le seed se limite au workspace, aux utilisateurs et au
        // dossier ; les scenarios e2e du dossier juridique deposent leurs propres
        // documents par l'API.

        // Sprint 11 TASK 7 : enrichissement trial OPT-IN si parametres fournis
        // (workspaces seed sans ces params restent dans l'etat Sprint 14 ter = legacy/null).
        java.time.Instant trialStartedAt = null;
        java.time.Instant trialEndsAt = null;
        String resolvedStatus = trialStatus;
        if (trialStatus != null && !trialStatus.isBlank()) {
            int offset = trialDaysOffset == null ? 0 : trialDaysOffset; // 0=now, negatif=passe, positif=futur
            trialStartedAt = java.time.Instant.now().minus(java.time.Duration.ofDays(14L + (offset < 0 ? -offset : 0)));
            trialEndsAt = java.time.Instant.now().plus(java.time.Duration.ofDays(offset));
            String plan = selectedPlan == null || selectedPlan.isBlank() ? "business" : selectedPlan;
            jdbc.update("""
                    UPDATE workspaces
                    SET trial_status = ?, trial_started_at = ?, trial_ends_at = ?,
                        selected_plan = ?, created_via_sprint11_wizard = TRUE
                    WHERE id = ?
                    """,
                    resolvedStatus, java.sql.Timestamp.from(trialStartedAt),
                    java.sql.Timestamp.from(trialEndsAt), plan, workspaceId);
            log.info("TestSeed trial enrichment : ws={} status={} endsAt={} plan={}",
                    workspaceId, resolvedStatus, trialEndsAt, plan);
        }

        log.info("TestSeed cree workspace={} code={} email={} dossier={}",
                workspaceId, code, email, dossierId);

        Map<String, Object> out = new HashMap<>();
        out.put("workspaceId", workspaceId);
        if (trialStartedAt != null) {
            out.put("trialStatus", resolvedStatus);
            out.put("trialStartedAt", trialStartedAt.toString());
            out.put("trialEndsAt", trialEndsAt.toString());
            out.put("selectedPlan", selectedPlan == null ? "business" : selectedPlan);
        }
        out.put("workspaceCode", code);
        // Aliases compatibles avec e2e/fixtures.ts (adminEmail/adminPassword)
        out.put("adminEmail", email);
        out.put("adminPassword", DEFAULT_PASSWORD);
        out.put("employeUserId", employeId);
        out.put("employeEmail", email);
        out.put("clientUserId", clientId);
        out.put("clientEmail", clientEmail);
        out.put("password", DEFAULT_PASSWORD);
        out.put("dossierId", dossierId);
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/cleanup/{workspaceId}")
    @Operation(summary = "Cleanup d'un workspace seeded (cascade FK supprime users + dossiers + documents)")
    @Transactional
    public ResponseEntity<Map<String, Object>> cleanup(@PathVariable UUID workspaceId) {
        // Le ON DELETE CASCADE sur les FK workspaces -> users -> dossiers ->
        // documents fait tout le menage. Les tables comptable / fiscal /
        // exercices / alertes ont ete supprimees par la migration V25.
        int ws = jdbc.update("DELETE FROM workspaces WHERE id = ?", workspaceId);
        log.info("TestSeed cleanup workspace={} workspaces={}", workspaceId, ws);
        return ResponseEntity.ok(Map.of(
                "workspaceId", workspaceId,
                "deleted", ws
        ));
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private String generateWorkspaceCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans 0/O/1/I pour lisibilite
        StringBuilder sb = new StringBuilder("JUR-");
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
