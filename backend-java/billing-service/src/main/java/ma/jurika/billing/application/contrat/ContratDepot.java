package ma.jurika.billing.application.contrat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot L7 (E6) : acces aux tables du contrat (billing V13). Filtrage applicatif explicite par
 * {@code workspace_id} sur chaque requete d'un cabinet, en plus de la RLS. Les lectures
 * transverses (super-admin, taches planifiees, passerelle) passent par {@link #porteeGlobale()},
 * a appeler dans la transaction.
 */
@Repository
public class ContratDepot {

    /** {@code retiree} : version marquee retiree (billing V16), jamais presentee a un cabinet. */
    public record Version(UUID id, String code, String texteModele, String empreinteModele,
                          String empreinteCg, String empreinteDpa, LocalDate entreeEnVigueur,
                          String origine, UUID publieePar, Instant publieeLe, boolean retiree) {}

    /** Reponse a une nouvelle version des conditions generales (billing V16). */
    public record Reponse(UUID id, UUID workspaceId, UUID conditionsId, UUID versionId, String versionCode,
                          String reponse, UUID acceptationId, UUID signataireUserId, String signataireCompte,
                          String signataireRole, Instant reponduLe, String reponduLeCasablanca, String adresseIp,
                          String chaineIp) {}

    public record Conditions(UUID id, UUID workspaceId, String reference, String forfait,
                             String rythmePaiement, BigDecimal prixHt, LocalDate dateDebut,
                             LocalDate dateFin, String modePaiement, String origine, String statut,
                             LocalDate echeanceCourante, UUID creePar, Instant creeLe,
                             Instant statutModifieLe) {}

    public record Acceptation(UUID id, UUID workspaceId, UUID conditionsId, UUID versionId,
                              String versionCode, String objet, String texteAccepte,
                              String empreinteContrat, String empreinteCg, String empreinteDpa,
                              UUID signataireUserId, String signataireCompte, String signataireCivilite,
                              String signataireNom, String signatairePrenom, String signataireQualite,
                              String signataireRole, String cabinetDenomination, String cabinetEmail,
                              String cabinetForme, String cabinetRcVille,
                              Instant accepteLe, String accepteLeCasablanca, String adresseIp,
                              String chaineIp) {}

    public record Resiliation(UUID id, UUID workspaceId, UUID conditionsId, UUID demandeePar,
                              String demandeurRole, LocalDate dateEffet, String motif,
                              Instant enregistreeLe) {}

    public record Renouvellement(UUID id, UUID conditionsId, LocalDate ancienneEcheance,
                                 LocalDate nouvelleEcheance, Instant renouveleLe) {}

    public record Avis(UUID id, UUID workspaceId, String type, String cle, String destinataire,
                       String statut, String erreur, Instant envoyeLe) {}

    private final JdbcTemplate jdbc;

    public ContratDepot(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lecture ou ecriture transverse (tous cabinets), locale a la transaction courante. */
    public void porteeGlobale() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Portee globale du contrat demandee hors transaction");
        }
        jdbc.queryForObject("SELECT set_config('app.contrat_portee', 'globale', true)", String.class);
    }

    // -- Versions -------------------------------------------------------------------------

    private static final String VERSION_COLS = "v.id, v.code, v.texte_modele, v.empreinte_modele, v.empreinte_cg, "
            + "v.empreinte_dpa, v.entree_en_vigueur_le, v.origine, v.publiee_par, v.publiee_le, "
            + "EXISTS (SELECT 1 FROM contrat_versions_retraits r WHERE r.version_id = v.id) AS retiree";
    private static final String NON_RETIREE =
            " NOT EXISTS (SELECT 1 FROM contrat_versions_retraits r WHERE r.version_id = v.id) ";

    private static final RowMapper<Version> VERSION = (rs, i) -> new Version(
            rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4).trim(),
            rs.getString(5).trim(), rs.getString(6).trim(), rs.getObject(7, LocalDate.class), rs.getString(8),
            rs.getObject(9, UUID.class), instant(rs, 10), rs.getBoolean(11));

    /** Toutes les versions, retirees comprises (super-admin, RG-CTR-07 : toutes conservees). */
    public List<Version> versions() {
        return jdbc.query("SELECT " + VERSION_COLS + " FROM contrat_versions v "
                + "ORDER BY v.entree_en_vigueur_le DESC, v.publiee_le DESC", VERSION);
    }

    /** Versions presentables a un cabinet (sans les versions retirees). */
    public List<Version> versionsPresentees() {
        return jdbc.query("SELECT " + VERSION_COLS + " FROM contrat_versions v WHERE" + NON_RETIREE
                + "ORDER BY v.entree_en_vigueur_le DESC, v.publiee_le DESC", VERSION);
    }

    public Optional<Version> version(UUID id) {
        return jdbc.query("SELECT " + VERSION_COLS + " FROM contrat_versions v WHERE v.id = ?", VERSION, id)
                .stream().findFirst();
    }

    public Optional<Version> versionParCode(String code) {
        return jdbc.query("SELECT " + VERSION_COLS + " FROM contrat_versions v WHERE v.code = ?", VERSION, code)
                .stream().findFirst();
    }

    /** Version en vigueur a la date donnee (la plus recente deja entree en vigueur, hors retirees). */
    public Optional<Version> versionEnVigueur(LocalDate jour) {
        return jdbc.query("SELECT " + VERSION_COLS + " FROM contrat_versions v WHERE v.entree_en_vigueur_le <= ? AND"
                + NON_RETIREE + "ORDER BY v.entree_en_vigueur_le DESC, v.publiee_le DESC LIMIT 1", VERSION, jour)
                .stream().findFirst();
    }

    /** Version publiee dont l'entree en vigueur est posterieure a la date donnee (hors retirees). */
    public Optional<Version> versionAVenir(LocalDate jour) {
        return jdbc.query("SELECT " + VERSION_COLS + " FROM contrat_versions v WHERE v.entree_en_vigueur_le > ? AND"
                + NON_RETIREE + "ORDER BY v.entree_en_vigueur_le ASC, v.publiee_le DESC LIMIT 1", VERSION, jour)
                .stream().findFirst();
    }

    /** Nombre d'acceptations, tous cabinets (portee globale requise). */
    public int nombreAcceptations() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM contrat_acceptations", Integer.class);
        return n == null ? 0 : n;
    }

    public UUID insererVersion(String code, String texte, ContratTexte.Sections s, LocalDate entree,
                               String origine, UUID publieePar) {
        return jdbc.queryForObject("INSERT INTO contrat_versions (code, texte_modele, empreinte_modele, empreinte_cg, "
                        + "empreinte_dpa, entree_en_vigueur_le, origine, publiee_par) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                UUID.class, code, texte, s.empreinteModele(), s.empreinteCg(), s.empreinteDpa(), Date.valueOf(entree),
                origine, publieePar);
    }

    // -- Conditions particulieres ---------------------------------------------------------

    private static final String CP_COLS = "id, workspace_id, reference, forfait, rythme_paiement, prix_ht, date_debut, "
            + "date_fin, mode_paiement, origine, statut, echeance_courante, cree_par, cree_le, statut_modifie_le";

    private static final RowMapper<Conditions> CP = (rs, i) -> new Conditions(
            rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
            rs.getString(5), rs.getBigDecimal(6), rs.getObject(7, LocalDate.class), rs.getObject(8, LocalDate.class),
            rs.getString(9), rs.getString(10), rs.getString(11), rs.getObject(12, LocalDate.class),
            rs.getObject(13, UUID.class), instant(rs, 14), instant(rs, 15));

    public String prochaineReference(int annee) {
        Long n = jdbc.queryForObject("SELECT nextval('conditions_particulieres_numero')", Long.class);
        return String.format("CP-%d-%06d", annee, n);
    }

    public UUID insererConditions(UUID workspaceId, String reference, ForfaitContrat forfait, String rythme,
                                  BigDecimal prixHt, LocalDate debut, LocalDate fin, String modePaiement,
                                  String origine, UUID creePar) {
        return jdbc.queryForObject("INSERT INTO conditions_particulieres (workspace_id, reference, forfait, rythme_paiement, "
                        + "prix_ht, date_debut, date_fin, mode_paiement, origine, statut, echeance_courante, cree_par) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'EN_ATTENTE', ?, ?) RETURNING id",
                UUID.class, workspaceId, reference, forfait.name(), rythme, prixHt, Date.valueOf(debut),
                Date.valueOf(fin), modePaiement, origine, Date.valueOf(fin), creePar);
    }

    public Optional<Conditions> conditions(UUID workspaceId, UUID id) {
        return jdbc.query("SELECT " + CP_COLS + " FROM conditions_particulieres WHERE workspace_id = ? AND id = ?",
                CP, workspaceId, id).stream().findFirst();
    }

    public Optional<Conditions> conditionsParStatut(UUID workspaceId, String statut) {
        return jdbc.query("SELECT " + CP_COLS + " FROM conditions_particulieres WHERE workspace_id = ? AND statut = ? "
                + "ORDER BY cree_le DESC LIMIT 1", CP, workspaceId, statut).stream().findFirst();
    }

    public boolean enAttente(UUID workspaceId) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM conditions_particulieres "
                + "WHERE workspace_id = ? AND statut = 'EN_ATTENTE')", Boolean.class, workspaceId);
        return Boolean.TRUE.equals(b);
    }

    public List<Conditions> conditionsDuCabinet(UUID workspaceId) {
        return jdbc.query("SELECT " + CP_COLS + " FROM conditions_particulieres WHERE workspace_id = ? "
                + "ORDER BY cree_le DESC", CP, workspaceId);
    }

    /** Toutes les conditions (portee globale requise). */
    public List<Conditions> toutesConditions() {
        return jdbc.query("SELECT " + CP_COLS + " FROM conditions_particulieres ORDER BY workspace_id, cree_le DESC", CP);
    }

    /** Conditions acceptees dont l'echeance courante tombe au plus tard a la date (portee globale). */
    public List<Conditions> accepteesEcheanceAvant(LocalDate jour) {
        return jdbc.query("SELECT " + CP_COLS + " FROM conditions_particulieres WHERE statut = 'ACCEPTE' "
                + "AND echeance_courante <= ? ORDER BY echeance_courante", CP, Date.valueOf(jour));
    }

    public List<Conditions> acceptees() {
        return jdbc.query("SELECT " + CP_COLS + " FROM conditions_particulieres WHERE statut = 'ACCEPTE'", CP);
    }

    public void changerStatut(UUID workspaceId, UUID id, String depuis, String vers, Instant le) {
        int n = jdbc.update("UPDATE conditions_particulieres SET statut = ?, statut_modifie_le = ? "
                + "WHERE workspace_id = ? AND id = ? AND statut = ?", vers, Timestamp.from(le), workspaceId, id, depuis);
        if (n != 1) {
            throw ContratException.conflit("ETAT_CHANGE",
                    "L\u2019\u00e9tat du contrat a chang\u00e9 entre-temps : rechargez la page.");
        }
    }

    public void changerEcheance(UUID workspaceId, UUID id, LocalDate ancienne, LocalDate nouvelle) {
        int n = jdbc.update("UPDATE conditions_particulieres SET echeance_courante = ? "
                + "WHERE workspace_id = ? AND id = ? AND echeance_courante = ? AND statut = 'ACCEPTE'",
                Date.valueOf(nouvelle), workspaceId, id, Date.valueOf(ancienne));
        if (n != 1) {
            throw ContratException.conflit("ECHEANCE_CHANGEE", "L\u2019\u00e9ch\u00e9ance du contrat a chang\u00e9 entre-temps.");
        }
    }

    // -- Acceptations ---------------------------------------------------------------------

    private static final String ACC_COLS = "id, workspace_id, conditions_id, version_id, version_code, objet, texte_accepte, "
            + "empreinte_contrat, empreinte_cg, empreinte_dpa, signataire_user_id, signataire_compte, signataire_civilite, "
            + "signataire_nom, signataire_prenom, signataire_qualite, signataire_role, cabinet_denomination, cabinet_email, "
            + "cabinet_forme, cabinet_rc_ville, accepte_le, accepte_le_casablanca, adresse_ip, chaine_ip";

    private static final RowMapper<Acceptation> ACC = (rs, i) -> new Acceptation(
            rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
            rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8).trim(),
            rs.getString(9).trim(), rs.getString(10).trim(), rs.getObject(11, UUID.class), rs.getString(12),
            rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17),
            rs.getString(18), rs.getString(19), rs.getString(20), rs.getString(21), instant(rs, 22), rs.getString(23),
            rs.getString(24), rs.getString(25));

    public UUID insererAcceptation(Acceptation a) {
        return jdbc.queryForObject("INSERT INTO contrat_acceptations (workspace_id, conditions_id, version_id, version_code, "
                        + "objet, texte_accepte, empreinte_contrat, empreinte_cg, empreinte_dpa, signataire_user_id, "
                        + "signataire_compte, signataire_civilite, signataire_nom, signataire_prenom, signataire_qualite, "
                        + "signataire_role, cabinet_denomination, cabinet_email, cabinet_forme, cabinet_rc_ville, accepte_le, "
                        + "accepte_le_casablanca, adresse_ip, chaine_ip) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "RETURNING id",
                UUID.class, a.workspaceId(), a.conditionsId(), a.versionId(), a.versionCode(), a.objet(),
                a.texteAccepte(), a.empreinteContrat(), a.empreinteCg(), a.empreinteDpa(), a.signataireUserId(),
                a.signataireCompte(), a.signataireCivilite(), a.signataireNom(), a.signatairePrenom(),
                a.signataireQualite(), a.signataireRole(), a.cabinetDenomination(), a.cabinetEmail(),
                a.cabinetForme(), a.cabinetRcVille(), Timestamp.from(a.accepteLe()), a.accepteLeCasablanca(), a.adresseIp(), a.chaineIp());
    }

    public Optional<Acceptation> acceptation(UUID workspaceId, UUID id) {
        return jdbc.query("SELECT " + ACC_COLS + " FROM contrat_acceptations WHERE workspace_id = ? AND id = ?",
                ACC, workspaceId, id).stream().findFirst();
    }

    public List<Acceptation> acceptationsDuCabinet(UUID workspaceId) {
        return jdbc.query("SELECT " + ACC_COLS + " FROM contrat_acceptations WHERE workspace_id = ? "
                + "ORDER BY accepte_le DESC", ACC, workspaceId);
    }

    public Optional<Acceptation> derniereAcceptation(UUID workspaceId) {
        return jdbc.query("SELECT " + ACC_COLS + " FROM contrat_acceptations WHERE workspace_id = ? "
                + "ORDER BY accepte_le DESC LIMIT 1", ACC, workspaceId).stream().findFirst();
    }

    public Optional<Acceptation> derniereAcceptationDes(UUID workspaceId, UUID conditionsId) {
        return jdbc.query("SELECT " + ACC_COLS + " FROM contrat_acceptations WHERE workspace_id = ? AND conditions_id = ? "
                + "ORDER BY accepte_le DESC LIMIT 1", ACC, workspaceId, conditionsId).stream().findFirst();
    }

    // -- Reponses aux nouvelles versions (billing V16) ------------------------------------

    private static final String REP_COLS = "id, workspace_id, conditions_id, version_id, version_code, reponse, "
            + "acceptation_id, signataire_user_id, signataire_compte, signataire_role, repondu_le, "
            + "repondu_le_casablanca, adresse_ip, chaine_ip";

    private static final RowMapper<Reponse> REP = (rs, i) -> new Reponse(
            rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
            rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class),
            rs.getObject(8, UUID.class), rs.getString(9), rs.getString(10), instant(rs, 11), rs.getString(12),
            rs.getString(13), rs.getString(14));

    public UUID insererReponse(Reponse r) {
        return jdbc.queryForObject("INSERT INTO contrat_reponses_versions (workspace_id, conditions_id, version_id, "
                        + "version_code, reponse, acceptation_id, signataire_user_id, signataire_compte, signataire_role, "
                        + "repondu_le, repondu_le_casablanca, adresse_ip, chaine_ip) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                UUID.class, r.workspaceId(), r.conditionsId(), r.versionId(), r.versionCode(), r.reponse(),
                r.acceptationId(), r.signataireUserId(), r.signataireCompte(), r.signataireRole(),
                Timestamp.from(r.reponduLe()), r.reponduLeCasablanca(), r.adresseIp(), r.chaineIp());
    }

    public Optional<Reponse> reponse(UUID workspaceId, UUID conditionsId, UUID versionId) {
        return jdbc.query("SELECT " + REP_COLS + " FROM contrat_reponses_versions WHERE workspace_id = ? "
                + "AND conditions_id = ? AND version_id = ?", REP, workspaceId, conditionsId, versionId).stream().findFirst();
    }

    public List<Reponse> reponsesDuCabinet(UUID workspaceId) {
        return jdbc.query("SELECT " + REP_COLS + " FROM contrat_reponses_versions WHERE workspace_id = ? "
                + "ORDER BY repondu_le DESC", REP, workspaceId);
    }

    // -- Resiliations, renouvellements, avis ----------------------------------------------

    private static final RowMapper<Resiliation> RES = (rs, i) -> new Resiliation(
            rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
            rs.getObject(4, UUID.class), rs.getString(5), rs.getObject(6, LocalDate.class), rs.getString(7),
            instant(rs, 8));

    public void insererResiliation(UUID workspaceId, UUID conditionsId, UUID par, String role, LocalDate effet,
                                   String motif) {
        jdbc.update("INSERT INTO contrat_resiliations (workspace_id, conditions_id, demandee_par, demandeur_role, "
                + "date_effet, motif) VALUES (?, ?, ?, ?, ?, ?)", workspaceId, conditionsId, par, role,
                Date.valueOf(effet), motif);
    }

    public List<Resiliation> resiliationsDuCabinet(UUID workspaceId) {
        return jdbc.query("SELECT id, workspace_id, conditions_id, demandee_par, demandeur_role, date_effet, motif, "
                + "enregistree_le FROM contrat_resiliations WHERE workspace_id = ? ORDER BY enregistree_le DESC",
                RES, workspaceId);
    }

    public void insererRenouvellement(UUID workspaceId, UUID conditionsId, LocalDate ancienne, LocalDate nouvelle) {
        jdbc.update("INSERT INTO contrat_renouvellements (workspace_id, conditions_id, ancienne_echeance, "
                + "nouvelle_echeance) VALUES (?, ?, ?, ?)", workspaceId, conditionsId, Date.valueOf(ancienne),
                Date.valueOf(nouvelle));
    }

    public List<Renouvellement> renouvellementsDuCabinet(UUID workspaceId) {
        return jdbc.query("SELECT id, conditions_id, ancienne_echeance, nouvelle_echeance, renouvele_le "
                        + "FROM contrat_renouvellements WHERE workspace_id = ? ORDER BY renouvele_le DESC",
                (rs, i) -> new Renouvellement(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class), instant(rs, 5)),
                workspaceId);
    }

    public void insererAvis(UUID workspaceId, String type, String cle, String destinataire, String statut,
                            String erreur) {
        jdbc.update("INSERT INTO contrat_avis (workspace_id, type, cle, destinataire, statut, erreur) "
                + "VALUES (?, ?, ?, ?, ?, ?)", workspaceId, type, cle, destinataire, statut, erreur);
    }

    public boolean avisEnvoye(UUID workspaceId, String type, String cle, String destinataire) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM contrat_avis WHERE workspace_id = ? AND type = ? "
                + "AND cle = ? AND destinataire = ? AND statut = 'ENVOYE')", Boolean.class, workspaceId, type, cle, destinataire);
        return Boolean.TRUE.equals(b);
    }

    public List<Avis> avisDuCabinet(UUID workspaceId) {
        return jdbc.query("SELECT id, workspace_id, type, cle, destinataire, statut, erreur, envoye_le FROM contrat_avis "
                        + "WHERE workspace_id = ? ORDER BY envoye_le DESC LIMIT 200",
                (rs, i) -> new Avis(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), instant(rs, 8)),
                workspaceId);
    }

    private static Instant instant(ResultSet rs, int col) throws SQLException {
        OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }
}
