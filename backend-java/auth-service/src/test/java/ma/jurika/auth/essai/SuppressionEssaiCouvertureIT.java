package ma.jurika.auth.essai;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E10), RG-ESS-10 : la suppression definitive d'un essai
 * ({@code essai_supprimer_definitivement}, auth V45) nomme ses tables une a une,
 * sans SQL dynamique. Ce garde permanent applique les migrations de TOUS les
 * services de jurika_db (ordre reel : {@link SchemaJurikaDbComplet}) et ECHOUE
 * si une table du cabinet n'est pas videe par la fonction.
 *
 * <p>Source unique de la liste couverte : le corps de la fonction lui-meme
 * ({@code pg_get_functiondef}), dont le test lit les instructions
 * {@code DELETE FROM public.<table>} et {@code UPDATE public.<table>} dans leur
 * ordre. Aucune seconde liste a tenir a jour en parallele : ce que le test
 * compare est exactement ce que la fonction execute.
 *
 * <p>Exclusions declarees ici, avec leur raison (seul endroit ou une table peut
 * etre laissee de cote).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuppressionEssaiCouvertureIT {

    /** Tables portant workspace_id que la fonction ne vide PAS, et pourquoi. */
    static final Map<String, String> EXCLUES = Map.of(
            "essais_registre",
            "registre des essais conserve (un essai par cabinet, RG-ESS-02 ; empreintes seulement), marque supprime");

    /** Tables sans workspace_id qui ne portent aucune donnee d'un cabinet, et pourquoi. */
    static final Map<String, String> SANS_DONNEE_DU_CABINET = Map.of(
            "demarches_referentiel", "referentiel commun des demarches (corpus), sans cabinet",
            "demarches_justificatifs", "justificatifs du referentiel commun, sans cabinet",
            "referentiel_decisions", "referentiel commun des decisions (corpus), sans cabinet",
            "referentiel_imports", "journal des imports du corpus commun (super-admin), sans cabinet",
            "referentiel_renvois", "renvois du referentiel commun, sans cabinet",
            "subscriptions", "ancien catalogue des forfaits (workspaces.subscription_id), aucune ligne propre a un essai",
            "workspaces", "le cabinet lui-meme : supprime par son id, en dernier");

    /** Prefixe des historiques Flyway : schema, sans donnee de cabinet. */
    static final String PREFIXE_HISTORIQUE = "flyway_history_";

    /** Workspace et employe semes par auth V2 et V3 (gabarits des clones). */
    static final UUID WORKSPACE_SEME = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID EMPLOYE_SEME = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final Pattern INSTRUCTION =
            Pattern.compile("(?i)\\b(?:DELETE\\s+FROM|UPDATE)\\s+public\\.([a-z0-9_]+)");
    private static final AtomicInteger COMPTEUR = new AtomicInteger();

    private final PostgreSQLContainer<?> pg = SchemaJurikaDbComplet.conteneur("jurika_it_essai_couverture");
    private JdbcTemplate proprietaire;
    private JdbcTemplate app;

    @BeforeAll
    void demarrer() {
        pg.start();
        SchemaJurikaDbComplet.migrer(pg);
        proprietaire = SchemaJurikaDbComplet.proprietaire(pg);
        app = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), "jurika_app", "jurika_app_it"));
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    // ===================================================== couverture

    @Test
    void chaque_table_du_cabinet_est_videe_par_la_fonction() {
        verifierCouverture();
    }

    @Test
    void le_garde_echoue_et_nomme_une_table_ajoutee_sans_etre_couverte() {
        // Caracterisation du garde lui-meme : une table de cabinet ajoutee par
        // une migration future, absente de la fonction, le fait echouer.
        proprietaire.execute("CREATE TABLE l7_temoin_non_couverte (id uuid PRIMARY KEY, workspace_id uuid NOT NULL)");
        proprietaire.execute("CREATE TABLE l7_temoin_enfant (id uuid PRIMARY KEY, "
                + "parent_id uuid NOT NULL REFERENCES l7_temoin_non_couverte(id))");
        try {
            assertThatThrownBy(this::verifierCouverture)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("l7_temoin_non_couverte")
                    .hasMessageContaining("l7_temoin_enfant")
                    .hasMessageContaining("nouvelle migration d'auth-service");
        } finally {
            proprietaire.execute("DROP TABLE l7_temoin_enfant");
            proprietaire.execute("DROP TABLE l7_temoin_non_couverte");
        }
    }

    @Test
    void les_tables_nommees_existent_et_l_ordre_respecte_les_cles_etrangeres() {
        List<String> ordre = tablesDeLaFonction();
        assertThat(new LinkedHashSet<>(ordre)).as("table nommee deux fois dans la fonction").hasSameSizeAs(ordre);
        Set<String> presentes = toutesLesTables();
        // Une entree perimee (table renommee ou supprimee) serait sautee par
        // to_regclass sans bruit : sur le schema complet, chacune doit exister.
        assertThat(ordre).as("tables nommees par la fonction mais absentes du schema complet de jurika_db")
                .allMatch(presentes::contains);
        assertThat(ordre.get(ordre.size() - 1)).as("workspaces doit etre supprime en dernier").isEqualTo("workspaces");

        // Enfants d'abord : pour toute cle etrangere entre deux tables nommees
        // (hors ON DELETE SET NULL, qui ne bloque jamais), l'enfant passe avant.
        List<String> fautes = new ArrayList<>();
        proprietaire.query("""
                SELECT e.relname AS enfant, p.relname AS parent, k.conname
                FROM pg_constraint k
                JOIN pg_class e ON e.oid = k.conrelid
                JOIN pg_class p ON p.oid = k.confrelid
                WHERE k.contype = 'f' AND k.connamespace = 'public'::regnamespace
                  AND k.confdeltype <> 'n' AND e.oid <> p.oid
                """, rs -> {
            String enfant = rs.getString("enfant");
            String parent = rs.getString("parent");
            int ie = ordre.indexOf(enfant);
            int ip = ordre.indexOf(parent);
            if (ie >= 0 && ip >= 0 && ie > ip) {
                fautes.add(enfant + " (rang " + (ie + 1) + ") apres son parent " + parent + " (rang " + (ip + 1)
                        + ", cle " + rs.getString("conname") + ")");
            }
        });
        assertThat(fautes).as("ordre de suppression contraire aux cles etrangeres (enfants d'abord)").isEmpty();
    }

    @Test
    void fonction_sans_sql_dynamique_securite_et_droits_inchanges() {
        Map<String, Object> f = proprietaire.queryForMap("""
                SELECT p.prosecdef, array_to_string(p.proconfig, '|') AS config, pg_get_userbyid(p.proowner) AS owner
                FROM pg_proc p WHERE p.oid = 'essai_supprimer_definitivement(uuid)'::regprocedure
                """);
        assertThat(f.get("prosecdef")).isEqualTo(true);
        assertThat((String) f.get("config")).isEqualTo("search_path=public, pg_temp");
        String corps = corps();
        assertThat(corps).as("aucun SQL dynamique").doesNotContainPattern("(?i)\\bEXECUTE\\b")
                .doesNotContainPattern("(?i)\\bformat\\s*\\(").doesNotContainPattern("(?i)pg_attribute");
        assertThat(corps).as("aucune boucle de reessai").doesNotContainPattern("(?i)\\bLOOP\\b")
                .doesNotContainPattern("(?i)foreign_key_violation");
        // EXECUTE : proprietaire et jurika_app seulement (jamais PUBLIC).
        List<String> beneficiaires = proprietaire.queryForList("""
                SELECT CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END
                FROM pg_proc p, aclexplode(p.proacl) a
                WHERE p.oid = 'essai_supprimer_definitivement(uuid)'::regprocedure AND a.privilege_type = 'EXECUTE'
                """, String.class);
        assertThat(beneficiaires).containsExactlyInAnyOrder((String) f.get("owner"), "jurika_app");
    }

    // ============================================== suppression reelle

    @Test
    void suppression_reelle_d_un_essai_peuple_dans_toutes_les_tables() {
        Cabinet essai = peupler();
        Cabinet autre = peupler();
        rendreSupprimable(essai.ws());

        // Le jeu de donnees peuple VRAIMENT chaque table du cabinet.
        Map<String, Long> avant = lignesDuCabinet(essai);
        List<String> vides = avant.entrySet().stream().filter(e -> e.getValue() == 0).map(Map.Entry::getKey).toList();
        assertThat(vides).as("tables du cabinet sans ligne dans le jeu de donnees du test : completer peupler()")
                .isEmpty();
        Map<String, Long> autreAvant = lignesDuCabinet(autre);
        Map<String, Long> globalesAvant = lignesDesTablesSansCabinet();

        // Appel comme en production : role d'execution jurika_app (SECURITY DEFINER).
        List<Map<String, Object>> rendu = app.queryForList(
                "SELECT table_nom, lignes FROM essai_supprimer_definitivement(?)", essai.ws());

        List<String> ordre = tablesDeLaFonction();
        assertThat(rendu).extracting(l -> l.get("table_nom")).containsExactlyElementsOf(ordre);
        assertThat(rendu).as("toutes les tables existent : aucune ligne rendue a NULL")
                .allSatisfy(l -> assertThat(l.get("lignes")).isNotNull());
        Map<String, Long> apres = lignesDuCabinet(essai);
        assertThat(apres).as("lignes du cabinet supprime restantes").allSatisfy((t, n) -> assertThat(n).as(t).isZero());
        assertThat(proprietaire.queryForObject(
                "SELECT supprime_le IS NOT NULL FROM essais_registre WHERE workspace_id = ?", Boolean.class, essai.ws()))
                .as("registre conserve et marque supprime").isTrue();
        assertThat(proprietaire.queryForObject("SELECT COUNT(*) FROM marketing_leads WHERE id = ?", Long.class,
                essai.lead())).as("le prospect (donnee de l'editeur) reste, sans lien").isOne();

        // Les autres cabinets et les referentiels sont intacts.
        assertThat(lignesDuCabinet(autre)).isEqualTo(autreAvant);
        assertThat(lignesDesTablesSansCabinet()).isEqualTo(globalesAvant);
    }

    @Test
    void la_fonction_refuse_si_un_garde_fou_manque_et_ne_supprime_rien() {
        record Cas(String sql, String message) { }
        List<Cas> cas = List.of(
                new Cas("DELETE FROM essais_registre WHERE workspace_id = ?", "n'est pas un essai gratuit"),
                new Cas("UPDATE essais_registre SET supprime_le = now() WHERE workspace_id = ?",
                        "n'est pas un essai gratuit"),
                new Cas("UPDATE workspaces SET trial_status = 'TRIAL_ACTIVE' WHERE id = ?", "lecture seule"),
                new Cas("UPDATE workspaces SET trial_status = 'CONVERTED' WHERE id = ?", "lecture seule"),
                new Cas("UPDATE workspaces SET subscription_status = 'cancelled' WHERE id = ?", "abonnement"),
                new Cas("UPDATE workspaces SET essai_avertissement_le = NULL WHERE id = ?", "averti"),
                new Cas("UPDATE workspaces SET essai_expire_le = now() - interval '29 days' WHERE id = ?", "30 jours"),
                new Cas("UPDATE workspaces SET essai_expire_le = NULL WHERE id = ?", "30 jours"));
        for (Cas c : cas) {
            Cabinet cabinet = peupler();
            rendreSupprimable(cabinet.ws());
            proprietaire.update(c.sql(), cabinet.ws());
            Map<String, Long> avant = lignesDuCabinet(cabinet);
            assertThatThrownBy(() -> app.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", cabinet.ws()))
                    .as(c.sql()).hasMessageContaining(c.message());
            assertThat(lignesDuCabinet(cabinet)).as("rien supprime : " + c.sql()).isEqualTo(avant);
        }
        assertThatThrownBy(() -> app.queryForList("SELECT * FROM essai_supprimer_definitivement(?)", UUID.randomUUID()))
                .hasMessageContaining("inconnu");
    }

    // ======================================================= garde

    /**
     * Compare le catalogue aux tables de la fonction. Echoue en nommant chaque
     * table non couverte et en disant quoi faire.
     */
    void verifierCouverture() {
        Set<String> couvertes = new LinkedHashSet<>(tablesDeLaFonction());
        Set<String> avecWorkspace = tablesAvecColonne("workspace_id");
        Map<String, String> fautes = new TreeMap<>();

        for (String t : avecWorkspace) {
            if (!couvertes.contains(t) && !EXCLUES.containsKey(t)) {
                fautes.put(t, "porte workspace_id");
            }
        }
        for (String t : toutesLesTables()) {
            if (avecWorkspace.contains(t) || couvertes.contains(t) || t.startsWith(PREFIXE_HISTORIQUE)
                    || SANS_DONNEE_DU_CABINET.containsKey(t)) {
                continue;
            }
            fautes.put(t, "sans workspace_id, non classee (rattachee a un cabinet par une table parente ?)");
        }
        // Reference a un cabinet sous un autre nom (cle etrangere vers workspaces,
        // colonne *workspace*, cabinet*) : la table doit etre couverte.
        proprietaire.query("""
                SELECT DISTINCT c.relname, a.attname
                FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
                WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
                  AND a.attnum > 0 AND NOT a.attisdropped AND a.attname <> 'workspace_id'
                  AND (a.attname LIKE '%workspace%' OR a.attname LIKE 'cabinet%'
                       OR EXISTS (SELECT 1 FROM pg_constraint k
                                   WHERE k.conrelid = c.oid AND k.contype = 'f'
                                     AND k.confrelid = 'public.workspaces'::regclass
                                     AND a.attnum = ANY (k.conkey)))
                """, rs -> {
            String t = rs.getString(1);
            if (!couvertes.contains(t) && !EXCLUES.containsKey(t) && !"workspaces".equals(t)
                    && !SANS_DONNEE_DU_CABINET.containsKey(t)) {
                fautes.merge(t, "reference un cabinet par la colonne " + rs.getString(2), (x, y) -> x + " ; " + y);
            }
        });
        if (!fautes.isEmpty()) {
            StringBuilder m = new StringBuilder("Table(s) de jurika_db non couverte(s) par essai_supprimer_definitivement :\n");
            fautes.forEach((t, raison) -> m.append("  - ").append(t).append(" (").append(raison).append(")\n"));
            m.append("Quoi faire : ajouter chaque table a la fonction par une nouvelle migration d'auth-service ")
                    .append("(CREATE OR REPLACE FUNCTION essai_supprimer_definitivement, en reprenant V45), a sa ")
                    .append("place dans l'ordre des cles etrangeres (enfants d'abord) ; ou, si elle ne porte aucune ")
                    .append("donnee d'un cabinet, la declarer avec sa raison dans EXCLUES ou SANS_DONNEE_DU_CABINET ")
                    .append("de SuppressionEssaiCouvertureIT.");
            throw new AssertionError(m.toString());
        }
    }

    private String corps() {
        return proprietaire.queryForObject(
                "SELECT pg_get_functiondef('essai_supprimer_definitivement(uuid)'::regprocedure)", String.class);
    }

    /** Tables videes (DELETE) ou deliees (UPDATE) par la fonction, dans l'ordre du corps. */
    List<String> tablesDeLaFonction() {
        List<String> tables = new ArrayList<>();
        Matcher m = INSTRUCTION.matcher(corps());
        while (m.find()) tables.add(m.group(1));
        return tables;
    }

    private Set<String> toutesLesTables() {
        return new TreeSet<>(proprietaire.queryForList("""
                SELECT c.relname FROM pg_class c
                WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
                """, String.class));
    }

    private Set<String> tablesAvecColonne(String colonne) {
        return new TreeSet<>(proprietaire.queryForList("""
                SELECT c.relname FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid
                WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
                  AND a.attname = ? AND NOT a.attisdropped
                """, String.class, colonne));
    }

    // ================================================ jeu de donnees

    record Cabinet(UUID ws, UUID ticket, UUID lead) { }

    /** Lignes du cabinet dans chaque table qui le porte (ou s'y rattache). */
    private Map<String, Long> lignesDuCabinet(Cabinet c) {
        Map<String, Long> n = new TreeMap<>();
        for (String t : tablesAvecColonne("workspace_id")) {
            if (EXCLUES.containsKey(t)) continue;
            n.put(t, proprietaire.queryForObject("SELECT COUNT(*) FROM " + t + " WHERE workspace_id = ?",
                    Long.class, c.ws()));
        }
        n.put("workspaces", proprietaire.queryForObject("SELECT COUNT(*) FROM workspaces WHERE id = ?",
                Long.class, c.ws()));
        n.put("demarches_migration_orphelines", proprietaire.queryForObject(
                "SELECT COUNT(*) FROM demarches_migration_orphelines WHERE ticket_id = ?", Long.class, c.ticket()));
        n.put("marketing_leads", proprietaire.queryForObject(
                "SELECT COUNT(*) FROM marketing_leads WHERE converted_workspace_id = ?", Long.class, c.ws()));
        return n;
    }

    private Map<String, Long> lignesDesTablesSansCabinet() {
        Map<String, Long> n = new LinkedHashMap<>();
        for (String t : new TreeSet<>(SANS_DONNEE_DU_CABINET.keySet())) {
            if ("workspaces".equals(t)) continue;
            n.put(t, proprietaire.queryForObject("SELECT COUNT(*) FROM " + t, Long.class));
        }
        return n;
    }

    private void rendreSupprimable(UUID ws) {
        proprietaire.update("""
                INSERT INTO essais_registre(workspace_id, workspace_code, email_empreinte, telephone_empreinte)
                SELECT id, code, repeat('a', 64), repeat('b', 64) FROM workspaces WHERE id = ?
                """, ws);
        proprietaire.update("""
                UPDATE workspaces SET trial_status = 'TRIAL_EXPIRED', subscription_status = NULL,
                       essai_expire_le = now() - interval '31 days', essai_avertissement_le = now() - interval '31 days'
                WHERE id = ?
                """, ws);
    }

    /**
     * Un cabinet (clone du workspace seme) avec au moins une ligne dans CHAQUE
     * table qui porte un cabinet, liens de cles etrangeres compris (dont les
     * RESTRICT et NO ACTION, qui imposent l'ordre de suppression).
     */
    private Cabinet peupler() {
        int n = COMPTEUR.incrementAndGet();
        Map<String, String> v = new LinkedHashMap<>();
        UUID ws = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        v.put("{ws}", ws.toString());
        v.put("{u}", UUID.randomUUID().toString());
        v.put("{d}", UUID.randomUUID().toString());
        v.put("{t}", ticket.toString());
        v.put("{doc}", UUID.randomUUID().toString());
        v.put("{td}", UUID.randomUUID().toString());
        v.put("{c}", UUID.randomUUID().toString());
        v.put("{r}", UUID.randomUUID().toString());
        v.put("{tr}", UUID.randomUUID().toString());
        v.put("{s}", UUID.randomUUID().toString());
        v.put("{lead}", lead.toString());
        v.put("{n}", String.valueOf(n));
        v.put("{code}", "JUR-S" + String.format("%04d", n));
        v.put("{seme_ws}", WORKSPACE_SEME.toString());
        v.put("{seme_u}", EMPLOYE_SEME.toString());
        String sql = JEU_DE_DONNEES;
        for (Map.Entry<String, String> e : v.entrySet()) sql = sql.replace(e.getKey(), e.getValue());
        proprietaire.execute(sql);
        return new Cabinet(ws, ticket, lead);
    }

    private static final String JEU_DE_DONNEES = """
            -- auth
            CREATE TEMP TABLE l7_w AS SELECT * FROM workspaces WHERE id = '{seme_ws}';
            UPDATE l7_w SET id = '{ws}', code = '{code}', name = 'Cabinet essai {n}',
                   contact_email = 'cabinet{n}@essai.test', trial_status = NULL, subscription_status = NULL;
            INSERT INTO workspaces SELECT * FROM l7_w;
            DROP TABLE l7_w;
            CREATE TEMP TABLE l7_u AS SELECT * FROM users WHERE id = '{seme_u}';
            UPDATE l7_u SET id = '{u}', workspace_id = '{ws}', email = 'u{n}@essai.test',
                   login_email = 'u{n}@essai.test', contact_email = 'u{n}@essai.test';
            INSERT INTO users SELECT * FROM l7_u;
            DROP TABLE l7_u;
            INSERT INTO refresh_tokens(workspace_id, user_id, token_hash, expires_at)
                VALUES ('{ws}', '{u}', 'rt-{n}', now() + interval '1 day');
            INSERT INTO password_reset_tokens(workspace_id, user_id, token_hash, expires_at)
                VALUES ('{ws}', '{u}', 'prt-{n}', now() + interval '1 day');
            INSERT INTO email_verification_tokens(workspace_id, user_id, token_hash, email, expires_at)
                VALUES ('{ws}', '{u}', 'evt-{n}', 'u{n}@essai.test', now() + interval '1 day');
            INSERT INTO sms_otp_codes(workspace_id, user_id, code_hash, phone_e164, purpose, expires_at)
                VALUES ('{ws}', '{u}', 'otp-{n}', '+212661000000', 'PHONE_VERIFICATION', now() + interval '1 day');
            INSERT INTO recovery_codes(workspace_id, user_id, code_hash) VALUES ('{ws}', '{u}', 'rc-{n}');
            INSERT INTO workspace_allowed_origins(workspace_id, origin) VALUES ('{ws}', 'https://cabinet{n}.example.ma');
            INSERT INTO trial_email_log(workspace_id, email_type) VALUES ('{ws}', 'AVERTISSEMENT');
            INSERT INTO alertes_quota_notifiees(workspace_id, quota, periode) VALUES ('{ws}', 'DOSSIERS', '2026-10');
            INSERT INTO aides_masquees(user_id, workspace_id, cle) VALUES ('{u}', '{ws}', 'aide.essai');
            INSERT INTO essai_conditions_acceptations(workspace_id, user_id, version, texte, empreinte, acceptees_le)
                VALUES ('{ws}', '{u}', 'v1', 'Conditions', repeat('c', 64), now());
            INSERT INTO essai_demandes(nom, prenom, organisation, telephone, email, jeton_email_empreinte,
                    conditions_version, conditions_empreinte, conditions_acceptees_le, expire_le, workspace_id)
                VALUES ('Benali', 'Karim', 'Cabinet', '+212661000000', 'u{n}@essai.test', lpad('{n}', 64, 'j'),
                    'v1', repeat('c', 64), now(), now() + interval '1 day', '{ws}');
            INSERT INTO audit_log(workspace_id, user_id, action) VALUES ('{ws}', '{u}', 'ESSAI_TEST');
            INSERT INTO marketing_leads(id, email, raison_sociale, converted_workspace_id)
                VALUES ('{lead}', 'lead{n}@essai.test', 'Prospect {n}', '{ws}');
            -- ticket
            INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id)
                VALUES ('{d}', '{ws}', 'ESSAI {n} SARL', 'SARL', '{u}');
            INSERT INTO tickets(id, workspace_id, reference, titre, type, statut, dossier_id, cree_par_id, annule_par_id)
                VALUES ('{t}', '{ws}', 'T-2026-{n}', 'Creation', 'CREATION', 'DEROULEMENT_DEMARCHE', '{d}', '{u}', '{u}');
            UPDATE entreprise_dossiers SET created_by_ticket_id = '{t}' WHERE id = '{d}';
            INSERT INTO ticket_debours(workspace_id, ticket_id, libelle, categorie, montant_mad, created_by_id)
                VALUES ('{ws}', '{t}', 'Greffe', 'AUTRE', 10, '{u}');
            INSERT INTO ticket_comments(workspace_id, ticket_id, auteur_id, contenu) VALUES ('{ws}', '{t}', '{u}', 'Note');
            INSERT INTO documents(workspace_id, type, titre, storage_key, ticket_id, dossier_id, uploaded_by_id)
                VALUES ('{ws}', 'AUTRE', 'Piece', 'ws/{n}/piece', '{t}', '{d}', '{u}');
            INSERT INTO deadlines(workspace_id, title, due_at, source, ticket_id, dossier_id, assigne_id, cree_par_id)
                VALUES ('{ws}', 'Depot', now() + interval '3 days', 'MANUAL', '{t}', '{d}', '{u}', '{u}');
            INSERT INTO dossier_transfert_requests(id, workspace_id, dossier_id, from_user_id, to_user_id)
                VALUES ('{tr}', '{ws}', '{d}', '{u}', '{u}');
            INSERT INTO dossier_reaffectations(workspace_id, dossier_id, ancien_responsable_id, nouveau_responsable_id,
                    nature, auteur_id, transfert_id)
                VALUES ('{ws}', '{d}', '{u}', '{u}', 'ACCEPTEE', '{u}', '{tr}');
            INSERT INTO succursales(workspace_id, parent_dossier_id, type) VALUES ('{ws}', '{d}', 'MA');
            INSERT INTO ticket_demarches(id, workspace_id, ticket_id, demarche_id, acteur_id)
                VALUES ('{td}', '{ws}', '{t}', (SELECT id FROM demarches_referentiel ORDER BY id LIMIT 1), '{u}');
            INSERT INTO ticket_demarche_evenements(workspace_id, ticket_demarche_id, type, acteur_id)
                VALUES ('{ws}', '{td}', 'COCHAGE', '{u}');
            INSERT INTO ticket_demarche_delais(workspace_id, ticket_id, demarche_id, echeance)
                VALUES ('{ws}', '{t}', (SELECT id FROM demarches_referentiel ORDER BY id LIMIT 1), current_date);
            INSERT INTO ticket_notes(ticket_id, workspace_id, contenu, modifie_par) VALUES ('{t}', '{ws}', 'Note', '{u}');
            INSERT INTO dossier_tp_versions(workspace_id, dossier_id, numero, saisi_par) VALUES ('{ws}', '{d}', 'TP{n}', '{u}');
            INSERT INTO ticket_decisions(workspace_id, ticket_id, matrice, decision_code)
                VALUES ('{ws}', '{t}', 'MODIFICATIONS_SARL', 'transfert_siege');
            INSERT INTO delais_greffe_cabinet(workspace_id, matrice, decision_code, delai_valeur, delai_unite)
                VALUES ('{ws}', 'MODIFICATIONS_SARL', 'transfert_siege', 5, 'JOURS');
            INSERT INTO dossier_identifiants_historique(workspace_id, dossier_id, champ, origine, ticket_id, auteur_id)
                VALUES ('{ws}', '{d}', 'ice', 'SAISIE', '{t}', '{u}');
            INSERT INTO alertes_delais_notifiees(workspace_id, ticket_id, ordre, echeance, severite)
                VALUES ('{ws}', '{t}', 1, current_date, 'APPROCHE');
            INSERT INTO demarches_migration_orphelines(ticket_demarche_id, ticket_id, ancien_ordre, ancien_libelle, etat,
                    justificatifs, motif)
                VALUES ('{td}', '{t}', 1, 'Ancienne ligne', 'COCHEE', 0, 'Sans equivalent');
            -- dataroom
            INSERT INTO dataroom_documents(id, workspace_id, dossier_id, ticket_id, document_type, title, object_key,
                    filename, size_bytes, uploaded_by)
                VALUES ('{doc}', '{ws}', '{d}', '{t}', 'AUTRE', 'Statuts', 'ws/{n}/statuts.pdf', 'statuts.pdf', 10, '{u}');
            INSERT INTO ticket_demarche_justificatifs(workspace_id, ticket_demarche_id, document_id, document_type,
                    alternative_groupe)
                VALUES ('{ws}', '{td}', '{doc}', 'AUTRE', 1);
            INSERT INTO ticket_document_snapshots(workspace_id, ticket_id, document_id, snapshot_kind)
                VALUES ('{ws}', '{t}', '{doc}', 'GENERATED');
            INSERT INTO dataroom_settings(dossier_id, workspace_id) VALUES ('{d}', '{ws}');
            INSERT INTO dataroom_client_access_log(workspace_id, dossier_id, user_id, document_id, action)
                VALUES ('{ws}', '{d}', '{u}', '{doc}', 'VIEW_DOSSIER');
            INSERT INTO dataroom_demandes_client(workspace_id, dossier_id, soumis_par, sujet, description, ticket_id)
                VALUES ('{ws}', '{d}', '{u}', 'Piece', 'Merci', '{t}');
            INSERT INTO dataroom_depots(workspace_id, dossier_id, filename, object_key, uploaded_by)
                VALUES ('{ws}', '{d}', 'cin.pdf', 'ws/{n}/depot/cin.pdf', '{u}');
            INSERT INTO dataroom_visibilite_evenements(workspace_id, document_id, visible, origine, acteur_id)
                VALUES ('{ws}', '{doc}', true, 'DATAROOM', '{u}');
            INSERT INTO dataroom_acces_client_historique(workspace_id, dossier_id, acteur_id, nature)
                VALUES ('{ws}', '{d}', '{u}', 'PERMISSIONS');
            INSERT INTO dataroom_wopi_sessions(id, token_hash, workspace_id, document_id, user_id, can_write, expires_at)
                VALUES ('{s}', lpad('{n}', 64, 'w'), '{ws}', '{doc}', '{u}', true, now() + interval '1 hour');
            INSERT INTO dataroom_wopi_verrous(document_id, workspace_id, lock_id, session_id, user_id, expire_at)
                VALUES ('{doc}', '{ws}', 'verrou-{n}', '{s}', '{u}', now() + interval '1 hour');
            INSERT INTO dataroom_objets_supprimes_v25(origine, workspace_id, dossier_id, object_key, filename)
                VALUES ('COMPTABLE', '{ws}', '{d}', 'ws/{n}/ancien.pdf', 'ancien.pdf');
            -- workflow
            INSERT INTO workflow_progress(workspace_id, ticket_id, workflow_type, total_steps, started_by_id)
                VALUES ('{ws}', '{t}', 'CREATION_SARL', 7, '{u}');
            INSERT INTO fiche_juridique(workspace_id, dossier_id) VALUES ('{ws}', '{d}');
            INSERT INTO representants(id, workspace_id, dossier_id, type_personne, nom)
                VALUES ('{r}', '{ws}', '{d}', 'PP', 'Alami');
            INSERT INTO associes(workspace_id, dossier_id, nombre_parts, representant_id) VALUES ('{ws}', '{d}', 10, '{r}');
            INSERT INTO evenements_juridiques(workspace_id, dossier_id, ticket_id, type_evenement, created_by_id)
                VALUES ('{ws}', '{d}', '{t}', 'MODIFICATION', '{u}');
            INSERT INTO dossier_variables(workspace_id, ticket_id, dossier_id, variable, origine)
                VALUES ('{ws}', '{t}', '{d}', 'RAISON_SOCIALE', 'BASE');
            INSERT INTO donnees_attendues(workspace_id, ticket_id, workflow_code, template_code, variable, libelle)
                VALUES ('{ws}', '{t}', 'CREATION_SARL', 'STATUTS_SARL', 'NUMERO_RC', 'Numero RC');
            -- ai
            INSERT INTO chatbot_conversations(id, workspace_id, user_id, titre) VALUES ('{c}', '{ws}', '{u}', 'Question');
            INSERT INTO chatbot_echanges(conversation_id, workspace_id, user_id, question, reponse)
                VALUES ('{c}', '{ws}', '{u}', 'Question ?', 'Reponse.');
            INSERT INTO chatbot_questions_essai(workspace_id, nombre) VALUES ('{ws}', 3);
            INSERT INTO rag_corpus_chunks(source, chunk_text, workspace_id, portee, uploaded_by)
                VALUES ('Note du cabinet', 'Texte', '{ws}', 'ARCHIVEE', '{u}');
            -- dashboard
            INSERT INTO agent_briefings(workspace_id, employee_id, summary, payload) VALUES ('{ws}', '{u}', 'Resume', '{}');
            INSERT INTO dashboard_snapshots(workspace_id, scope, actor_id, payload, valid_until)
                VALUES ('{ws}', 'EMPLOYE', '{u}', '{}', now() + interval '1 hour');
            -- supervision
            INSERT INTO business_events(workspace_id, event_type) VALUES ('{ws}', 'ESSAI_TEST');
            """;
}
