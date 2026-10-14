package ma.jurika.auth.infrastructure.essai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot L7 (E4) : acces aux tables de l'essai gratuit (auth V40). Les lectures
 * transverses (unicite globale, origines communes, liste du super-admin,
 * cycle planifie) passent par les fonctions SECURITY DEFINER de V40.
 */
@Repository
public class EssaiJdbc {

    private final JdbcTemplate jdbc;

    public EssaiJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ demandes

    public record Demande(UUID id, String nom, String prenom, String organisation, String telephone,
                          String email, Instant emailVerifieLe, String codeSmsEmpreinte,
                          Instant codeSmsEnvoyeLe, Instant codeSmsExpireLe, int codeSmsTentatives,
                          Instant telephoneVerifieLe, String conditionsVersion, String conditionsEmpreinte,
                          Instant conditionsAccepteesLe, String adresseIp, String appareil, String userAgent,
                          Instant creeLe, Instant expireLe, UUID workspaceId) {
        public boolean estExpiree(Instant maintenant) { return maintenant.isAfter(expireLe); }
        public boolean estVerifiee() { return emailVerifieLe != null && telephoneVerifieLe != null; }
    }

    public record NouvelleDemande(String nom, String prenom, String organisation, String telephone, String email,
                                  String jetonEmailEmpreinte, String codeSmsEmpreinte, Instant codeSmsEnvoyeLe,
                                  Instant codeSmsExpireLe, String conditionsVersion, String conditionsEmpreinte,
                                  Instant conditionsAccepteesLe, String adresseIp, String appareil,
                                  String userAgent, Instant expireLe) {}

    private static final String COLONNES_DEMANDE = """
            id, nom, prenom, organisation, telephone, email, email_verifie_le, code_sms_empreinte,
            code_sms_envoye_le, code_sms_expire_le, code_sms_tentatives, telephone_verifie_le,
            conditions_version, conditions_empreinte, conditions_acceptees_le, adresse_ip, appareil,
            user_agent, cree_le, expire_le, workspace_id
            """;

    private static final RowMapper<Demande> DEMANDE = (rs, i) -> new Demande(
            (UUID) rs.getObject("id"), rs.getString("nom"), rs.getString("prenom"),
            rs.getString("organisation"), rs.getString("telephone"), rs.getString("email"),
            instant(rs, "email_verifie_le"), rs.getString("code_sms_empreinte"),
            instant(rs, "code_sms_envoye_le"), instant(rs, "code_sms_expire_le"),
            rs.getInt("code_sms_tentatives"), instant(rs, "telephone_verifie_le"),
            rs.getString("conditions_version"), rs.getString("conditions_empreinte"),
            instant(rs, "conditions_acceptees_le"), rs.getString("adresse_ip"), rs.getString("appareil"),
            rs.getString("user_agent"), instant(rs, "cree_le"), instant(rs, "expire_le"),
            (UUID) rs.getObject("workspace_id"));

    public UUID creerDemande(NouvelleDemande d) {
        return jdbc.queryForObject("""
                INSERT INTO essai_demandes (nom, prenom, organisation, telephone, email,
                    jeton_email_empreinte, code_sms_empreinte, code_sms_envoye_le, code_sms_expire_le,
                    conditions_version, conditions_empreinte, conditions_acceptees_le,
                    adresse_ip, appareil, user_agent, expire_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class,
                d.nom(), d.prenom(), d.organisation(), d.telephone(), d.email(),
                d.jetonEmailEmpreinte(), d.codeSmsEmpreinte(), ts(d.codeSmsEnvoyeLe()), ts(d.codeSmsExpireLe()),
                d.conditionsVersion(), d.conditionsEmpreinte(), ts(d.conditionsAccepteesLe()),
                d.adresseIp(), d.appareil(), d.userAgent(), ts(d.expireLe()));
    }

    public Optional<Demande> demande(UUID id) {
        return jdbc.query("SELECT " + COLONNES_DEMANDE + " FROM essai_demandes WHERE id = ?", DEMANDE, id)
                .stream().findFirst();
    }

    public Optional<Demande> demandeParJeton(String empreinte) {
        return jdbc.query("SELECT " + COLONNES_DEMANDE + " FROM essai_demandes WHERE jeton_email_empreinte = ?",
                DEMANDE, empreinte).stream().findFirst();
    }

    public void nouveauCodeSms(UUID id, String empreinte, Instant envoyeLe, Instant expireLe) {
        jdbc.update("""
                UPDATE essai_demandes SET code_sms_empreinte = ?, code_sms_envoye_le = ?,
                       code_sms_expire_le = ?, code_sms_tentatives = 0
                WHERE id = ? AND workspace_id IS NULL
                """, empreinte, ts(envoyeLe), ts(expireLe), id);
    }

    public void tentativeSmsEchouee(UUID id) {
        jdbc.update("UPDATE essai_demandes SET code_sms_tentatives = code_sms_tentatives + 1 WHERE id = ?", id);
    }

    public void telephoneVerifie(UUID id, Instant le) {
        jdbc.update("""
                UPDATE essai_demandes SET telephone_verifie_le = ?, code_sms_empreinte = NULL
                WHERE id = ? AND telephone_verifie_le IS NULL
                """, ts(le), id);
    }

    public void emailVerifie(UUID id, Instant le) {
        jdbc.update("UPDATE essai_demandes SET email_verifie_le = ? WHERE id = ? AND email_verifie_le IS NULL",
                ts(le), id);
    }

    /** Reserve la demande pour UNE creation : {@code false} si elle a deja servi (requete concurrente). */
    public boolean reserverCreation(UUID id, UUID workspaceId) {
        return jdbc.update("UPDATE essai_demandes SET workspace_id = ? WHERE id = ? AND workspace_id IS NULL",
                workspaceId, id) == 1;
    }

    public int purgerDemandes(Instant limite) {
        Integer n = jdbc.queryForObject("SELECT essai_purger_demandes(?)", Integer.class, ts(limite));
        return n == null ? 0 : n;
    }

    // ------------------------------------------------- unicite, signalements

    public record IdentiteUtilisee(boolean email, boolean telephone) {}

    public IdentiteUtilisee identiteDejaUtilisee(String email, String telephone,
                                                 String emailEmpreinte, String telephoneEmpreinte) {
        return jdbc.queryForObject(
                "SELECT email_utilise, telephone_utilise FROM essai_identite_deja_utilisee(?, ?, ?, ?)",
                (rs, i) -> new IdentiteUtilisee(rs.getBoolean(1), rs.getBoolean(2)),
                email, telephone, emailEmpreinte, telephoneEmpreinte);
    }

    public record OriginesCommunes(long memeReseau, long memeAppareil) {}

    public OriginesCommunes originesCommunes(String adresseIp, String appareil) {
        return jdbc.queryForObject("SELECT meme_reseau, meme_appareil FROM essai_origines_communes(?, ?)",
                (rs, i) -> new OriginesCommunes(rs.getLong(1), rs.getLong(2)), adresseIp, appareil);
    }

    // ----------------------------------------------------- registre, preuve

    public void inscrireAuRegistre(UUID workspaceId, String code, String emailEmpreinte, String telephoneEmpreinte,
                                   String adresseIp, String appareil, String signalement, Instant creeLe) {
        jdbc.update("""
                INSERT INTO essais_registre (workspace_id, workspace_code, email_empreinte, telephone_empreinte,
                    adresse_ip, appareil, signalement, cree_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, workspaceId, code, emailEmpreinte, telephoneEmpreinte, adresseIp, appareil, signalement,
                ts(creeLe));
    }

    public void enregistrerAcceptation(UUID workspaceId, UUID userId, String version, String texte,
                                       String empreinte, Instant accepteesLe, String adresseIp, String userAgent) {
        jdbc.update("""
                INSERT INTO essai_conditions_acceptations (workspace_id, user_id, version, texte, empreinte,
                    acceptees_le, adresse_ip, user_agent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, workspaceId, userId, version, texte, empreinte, ts(accepteesLe), adresseIp, userAgent);
    }

    public int signalementExamine(UUID workspaceId, Instant le) {
        return jdbc.update("""
                UPDATE essais_registre SET signalement_examine_le = ?
                WHERE workspace_id = ? AND signalement IS NOT NULL
                """, ts(le), workspaceId);
    }

    // --------------------------------------------------------- super-admin

    public record EssaiAdmin(UUID workspaceId, String code, String nom, String contactEmail, String statut,
                             String statutEssai, Instant debut, Instant fin, Instant expireLe,
                             Instant avertissementLe, Instant prolongeLe, Integer prolongationJours,
                             String finMotif, String abonnement, String adresseIp, String appareil,
                             String signalement, Instant signalementExamineLe, Instant creeLe,
                             Instant supprimeLe) {}

    public List<EssaiAdmin> listeEssais() {
        return jdbc.query("SELECT * FROM admin_liste_essais()", (rs, i) -> new EssaiAdmin(
                (UUID) rs.getObject("workspace_id"), rs.getString("workspace_code"), rs.getString("name"),
                rs.getString("contact_email"), rs.getString("status"), rs.getString("trial_status"),
                instant(rs, "trial_started_at"), instant(rs, "trial_ends_at"), instant(rs, "essai_expire_le"),
                instant(rs, "essai_avertissement_le"), instant(rs, "essai_prolonge_le"),
                (Integer) rs.getObject("essai_prolongation_jours"), rs.getString("essai_fin_motif"),
                rs.getString("subscription_status"), rs.getString("adresse_ip"), rs.getString("appareil"),
                rs.getString("signalement"), instant(rs, "signalement_examine_le"), instant(rs, "cree_le"),
                instant(rs, "supprime_le")));
    }

    public List<UUID> workspacesATraiter() {
        return jdbc.queryForList("SELECT id FROM essai_workspaces_a_traiter()", UUID.class);
    }

    // ------------------------------------------------------------- outils

    static Instant instant(ResultSet rs, String colonne) throws SQLException {
        Timestamp t = rs.getTimestamp(colonne);
        return t == null ? null : t.toInstant();
    }

    static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
