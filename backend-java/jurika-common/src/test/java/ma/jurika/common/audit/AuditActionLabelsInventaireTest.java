package ma.jurika.common.audit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6b (E9) : garde des libelles des actions tracees.
 *
 * <p>Defaut vu a l'ecran : le panneau "Activite du dossier" affichait "login success",
 * "document generated"... Le libelle venait d'un repli qui humanisait le code inconnu
 * (motif 9 : la donnee absente etait deguisee). Cette garde :
 * <ol>
 *   <li>fige l'INVENTAIRE exhaustif des codes emis par la plateforme (liste ecrite ici) ;</li>
 *   <li>le compare, dans les deux sens, aux codes relus dans les sources de tous les
 *       services ({@link SourcesActionsAudit}) : un code nouveau ou disparu la fait echouer ;</li>
 *   <li>exige un libelle pour chaque code de l'inventaire et des codes historiques ;</li>
 *   <li>exige que chaque code lu par une requete existe (sinon le filtre ne trouve rien) ;</li>
 *   <li>exige que chaque action du journal d'acces du client soit admise par la contrainte
 *       CHECK de la table (sinon l'insertion est rejetee et la trace perdue).</li>
 * </ol>
 */
class AuditActionLabelsInventaireTest {

    /** Codes poses par @Auditable (l'echec CODE_FAILED est ecrit par AuditAspect). */
    static final Set<String> AUDITABLES = Set.of(
            // ai-service
            "AI_DOCUMENT_GENERATED", "CHATBOT_QUERY", "CHATBOT_SOURCE_ADDED", "CHATBOT_SOURCE_DELETED",
            // auth-service
            "CABINET_SIGNUP", "REFRESH_TOKEN_USED", "WORKSPACE_LOGO_DELETED", "WORKSPACE_LOGO_UPDATED",
            "WORKSPACE_PROFILE_UPDATE", "WORKSPACE_THEME_CHANGED",
            // billing-service
            "BILLING_PAIEMENT_ENREGISTRE", "BILLING_PAIEMENT_REJETE", "BILLING_PAIEMENT_VALIDE",
            // dashboard-service
            "DASHBOARD_ADMIN_VIEW", "DASHBOARD_VIEW",
            // dataroom-service
            "DEPOT_DELETED", "DEPOT_UPLOADED", "DOCUMENTS_DELETED_BULK", "DOCUMENT_DELETED",
            "DOCUMENT_DOWNLOADED", "DOCUMENT_PREVIEWED", "DOCUMENT_UPLOADED", "DOCUMENT_VERSIONS_LISTED",
            "DOCUMENT_VERSION_DOWNLOADED", "DOCUMENT_VERSION_REPLACED", "DOCUMENT_VERSION_RESTORED",
            "FICHE_CLIENT_GENERATED", "FICHE_DOSSIER_GENERATED", "IDENTITY_EXTRACTED",
            // ticket-service
            "DEMARCHES_CONDITION_GERANCE", "DEMARCHE_COCHEE", "DEMARCHE_DECOCHEE", "DEMARCHE_DELAI_SAISI",
            "DEMARCHE_NON_APPLICABLE", "DOSSIER_IDENTIFIANTS_UPDATED", "TICKET_CREATED", "TICKET_TRANSITIONED",
            // workflow-service
            "CLAUSES_LIBRES_MODIFIEES", "WORKFLOW_PIECE_REGISTERED", "WORKFLOW_PIECE_UNREGISTERED",
            "WORKFLOW_STARTED", "WORKFLOW_STEP_EXECUTED");

    /** Codes ecrits directement (AuditLogger d'auth, AuditEventEmitter, SQL). */
    static final Set<String> DIRECTS = Set.of(
            // auth-service (AuditLogger)
            "2FA_ENABLED", "2FA_METHOD_CHOSEN", "2FA_SETUP_INITIATED", "ACCOUNT_CHANGE_CANCELLED",
            "ADMIN_RESEND_WELCOME", "CLIENT_ACCESS_REMOVED", "CLIENT_INVITED", "CONTACT_EMAIL_CHANGED",
            "CONTACT_EMAIL_CHANGE_REQUESTED", "CREDENTIALS_ISSUED", "DROIT_SUPPRESSION_DATAROOM_ACCORDE",
            "DROIT_SUPPRESSION_DATAROOM_RETIRE", "EMAIL_VERIFICATION_RESENT", "EMAIL_VERIFIED",
            "EMPLOYE_INVITED", "ESSAI_CREE", "ESSAI_LECTURE_SEULE", "ESSAI_PROLONGE",
            "ESSAI_SIGNALEMENT_EXAMINE", "ESSAI_SUPPRIME_DEFINITIVEMENT", "ESSAI_TERMINE_PAR_JURIKA",
            "LOGIN_2FA_FAILED", "LOGIN_2FA_LOCKED", "LOGIN_2FA_REQUIRED", "LOGIN_EMAIL_CHANGED",
            "LOGIN_EMAIL_CHANGE_REQUESTED", "LOGIN_FAILED", "LOGIN_NEW_DEVICE", "LOGIN_SUCCESS",
            "LOGIN_SUCCESS_2FA", "LOGOUT", "LOGOUT_ALL", "PASSWORD_CHANGED", "PASSWORD_RESET_CONFIRMED",
            "PASSWORD_RESET_REQUESTED", "RECOVERY_CODES_GENERATED", "RECOVERY_CODES_REGENERATED",
            "RECOVERY_CODE_FAILED", "RECOVERY_CODE_USED", "SMS_OTP_FAILED", "SMS_OTP_SENT",
            "SMS_OTP_VERIFIED", "USER_ACTIVATED", "USER_ACTIVATED_BY_ONBOARDING", "USER_DEACTIVATED",
            "USER_NAME_CHANGED", "USER_REACTIVATED", "USER_SUSPENDED", "WORKSPACE_ACTIVATED",
            "WORKSPACE_ARCHIVED", "WORKSPACE_REGISTER", "WORKSPACE_SUSPENDED",
            // dataroom-service (AuditEventEmitter)
            "ACCES_CLIENT_REACTIVE", "ACCES_CLIENT_SUSPENDU", "PERMISSIONS_CLIENT_MODIFIEES",
            // ticket-service (AuditEventEmitter)
            "DEBOURS_AJOUTE", "DEBOURS_MODIFIE", "DEBOURS_SUPPRIME", "DOSSIER_REAFFECTE", "DOSSIER_TRANSFERE",
            "RATTRAPAGE_VERIFIE", "TICKET_NOTE_MODIFIEE", "TICKET_UPDATED",
            // ticket-service (DemoDataSeeder, SQL de demonstration)
            "COMPTABLE_UPLOADED", "JURIDIQUE_UPLOADED");

    /**
     * Codes historiques : plus emis par aucun service, mais presents dans des lignes
     * anciennes du journal ou lus par une requete ; leur libelle reste du.
     */
    static final Set<String> HISTORIQUES = Set.of(
            "DATAROOM_DELETED", "DATAROOM_SUSPENSION_TOGGLED", "ECHEANCE_MARQUEE_TRAITEE", "EXERCICE_CLOTURED",
            "EXERCICE_LOCKED", "EXERCICE_OPENED", "EXERCICE_UNLOCKED", "FISCAL_DELETED", "FISCAL_EXPORTED_ZIP",
            "FISCAL_UPLOADED", "PERMISSIONS_CHANGED", "REPORT_JURIDIQUE_GENERATED", "TICKET_ASSIGNED",
            "WORKSPACE_DEACTIVATED");

    /** Actions du journal d'acces du client (dataroom_client_access_log). */
    static final Set<String> ACCES_CLIENT = Set.of(
            "DEMANDE", "DEPOT_DOC", "DOWNLOAD_DOC", "DOWNLOAD_VERSION", "PREVIEW_DOC", "PREVIEW_VERSION",
            "PRINT_DOC", "VIEW_DOSSIER");

    private static SourcesActionsAudit sources;

    @BeforeAll
    static void lire() {
        sources = SourcesActionsAudit.lire();
    }

    static Set<String> inventaire() {
        Set<String> s = new TreeSet<>(DIRECTS);
        for (String a : AUDITABLES) {
            s.add(a);
            s.add(a + AuditActionLabels.SUFFIXE_ECHEC);
        }
        return s;
    }

    @Test
    void la_relecture_des_sources_porte_sur_tous_les_emetteurs() {
        // Garde contre une relecture vide (motif pattern trop etroit) : seuils sous l'etat connu.
        assertThat(sources.fichiersJava).isGreaterThan(800);
        assertThat(sources.sitesAuditable).isGreaterThanOrEqualTo(40);
        assertThat(sources.sitesAuditLogger).isGreaterThanOrEqualTo(45);
        assertThat(sources.sitesAuditEmitter).isGreaterThanOrEqualTo(7);
        assertThat(sources.sitesAccesClient).isGreaterThanOrEqualTo(10);
        assertThat(sources.migrationCheck).as("migration qui pose le CHECK du journal d'acces").isNotNull();
        assertThat(sources.nonResolus).as("sites d'emission dont le code ne se resout pas").isEmpty();
    }

    @Test
    void les_codes_poses_par_auditable_sont_ceux_de_l_inventaire() {
        assertThat(sources.auditables.keySet())
                .as("codes @Auditable relus dans les sources : %s", sources.auditables)
                .containsExactlyInAnyOrderElementsOf(AUDITABLES);
    }

    @Test
    void les_codes_emis_par_la_plateforme_sont_ceux_de_l_inventaire() {
        Set<String> absentsInventaire = new TreeSet<>(sources.emis.keySet());
        absentsInventaire.removeAll(inventaire());
        Set<String> plusEmis = new TreeSet<>(inventaire());
        plusEmis.removeAll(sources.emis.keySet());
        assertThat(absentsInventaire)
                .as("codes emis absents de l'inventaire (ajouter le code ET son libelle) : %s",
                        absentsInventaire.stream().collect(Collectors.toMap(c -> c, sources.emis::get)))
                .isEmpty();
        assertThat(plusEmis).as("codes de l'inventaire qui ne sont plus emis (les passer en HISTORIQUES)")
                .isEmpty();
    }

    @Test
    void chaque_code_de_l_inventaire_et_chaque_code_historique_a_un_libelle() {
        Set<String> sansLibelle = Stream.concat(inventaire().stream(), HISTORIQUES.stream())
                .filter(c -> !AuditActionLabels.estConnu(c))
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(sansLibelle).as("codes sans libelle").isEmpty();
    }

    @Test
    void chaque_code_lu_par_une_requete_existe() {
        Set<String> inconnus = new TreeSet<>(sources.lus.keySet());
        inconnus.removeAll(inventaire());
        inconnus.removeAll(HISTORIQUES);
        assertThat(inconnus)
                .as("codes lus par une requete mais jamais ecrits (filtre inoperant) : %s",
                        inconnus.stream().collect(Collectors.toMap(c -> c, sources.lus::get)))
                .isEmpty();
    }

    @Test
    void le_fichier_des_libelles_ne_nomme_aucun_code_invente() {
        Set<String> orphelins = new TreeSet<>(AuditActionLabels.codesAvecLibelle());
        orphelins.removeAll(inventaire());
        orphelins.removeAll(HISTORIQUES);
        assertThat(orphelins).as("libelles sans code emis ni historique").isEmpty();
        assertThat(DIRECTS).as("un code est soit direct, soit historique").doesNotContainAnyElementsOf(HISTORIQUES);
    }

    @Test
    void journal_d_acces_du_client_inventaire_libelles_et_contrainte() {
        assertThat(sources.accesEmis.keySet()).as("actions emises : %s", sources.accesEmis)
                .containsExactlyInAnyOrderElementsOf(ACCES_CLIENT);
        Set<String> refusees = new TreeSet<>(ACCES_CLIENT);
        refusees.removeAll(sources.accesCheck);
        assertThat(refusees)
                .as("actions emises que la contrainte CHECK de %s rejette (trace perdue)", sources.migrationCheck)
                .isEmpty();
        assertThat(ACCES_CLIENT.stream().filter(a -> !AuditActionLabels.estConnueAccesClient(a)).toList())
                .as("actions sans libelle").isEmpty();
        assertThat(AuditActionLabels.actionsAccesClientAvecLibelle())
                .containsExactlyInAnyOrderElementsOf(ACCES_CLIENT);
    }
}
