package ma.jurika.common.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Libelles des actions tracees (deplace de dashboard-service au lot L6b, E9 :
 * auth et dataroom rendent aussi le libelle).
 */
class AuditActionLabelsTest {

    @Test
    void codes_connus_traduits() {
        assertThat(AuditActionLabels.label("TICKET_CREATED")).isEqualTo("Ticket cr\u00e9\u00e9");
        assertThat(AuditActionLabels.label("DOSSIER_TRANSFERE")).isEqualTo("Dossier transf\u00e9r\u00e9");
        assertThat(AuditActionLabels.label("TICKET_ASSIGNED")).isEqualTo("Ticket r\u00e9assign\u00e9");
        assertThat(AuditActionLabels.label("TICKET_UPDATED")).isEqualTo("Ticket modifi\u00e9");
        assertThat(AuditActionLabels.label("FISCAL_UPLOADED")).isEqualTo("Document fiscal d\u00e9pos\u00e9");
    }

    @Test
    void codes_vus_a_l_ecran_ont_un_libelle_metier() {
        // Defaut vu a l'ecran (L6b) : "login success", "document generated", "ticket status changed",
        // "payment validated". Les codes reellement emis sont ceux-ci.
        assertThat(AuditActionLabels.label("LOGIN_SUCCESS")).isEqualTo("Connexion r\u00e9ussie");
        assertThat(AuditActionLabels.label("AI_DOCUMENT_GENERATED")).isEqualTo("Document g\u00e9n\u00e9r\u00e9");
        assertThat(AuditActionLabels.label("TICKET_TRANSITIONED")).isEqualTo("Statut du ticket modifi\u00e9");
        assertThat(AuditActionLabels.label("BILLING_PAIEMENT_VALIDE"))
                .isEqualTo("R\u00e9ception du paiement confirm\u00e9e");
        assertThat(AuditActionLabels.label("DEBOURS_AJOUTE")).isEqualTo("D\u00e9bours ajout\u00e9");
    }

    @Test
    void echec_d_une_methode_tracee_reprend_le_libelle_de_l_action() {
        assertThat(AuditActionLabels.label("TICKET_CREATED_FAILED"))
                .isEqualTo("\u00c9chec de l\u2019action \u00ab\u00a0Ticket cr\u00e9\u00e9\u00a0\u00bb");
        // Un echec qui a son propre libelle le garde.
        assertThat(AuditActionLabels.label("LOGIN_FAILED")).isEqualTo("Connexion \u00e9chou\u00e9e");
        assertThat(AuditActionLabels.estConnu("INCONNU_FAILED")).isFalse();
    }

    @Test
    void code_inconnu_nomme_et_jamais_deguise() {
        // Avant L6b : "Some new action" (le code humanise passait pour un libelle).
        assertThat(AuditActionLabels.label("SOME_NEW_ACTION"))
                .isEqualTo("Action enregistr\u00e9e (code SOME_NEW_ACTION)");
        assertThat(AuditActionLabels.estConnu("SOME_NEW_ACTION")).isFalse();
        assertThat(AuditActionLabels.libelleAccesClient("AUTRE"))
                .isEqualTo("Action enregistr\u00e9e (code AUTRE)");
    }

    @Test
    void null_ou_vide_donne_chaine_vide() {
        assertThat(AuditActionLabels.label(null)).isEmpty();
        assertThat(AuditActionLabels.label("  ")).isEmpty();
        assertThat(AuditActionLabels.libelleAccesClient(null)).isEmpty();
    }

    @Test
    void actions_du_client_traduites() {
        assertThat(AuditActionLabels.libelleAccesClient("VIEW_DOSSIER")).isEqualTo("Consultation de la Data Room");
        assertThat(AuditActionLabels.libelleAccesClient("DOWNLOAD_DOC"))
                .isEqualTo("T\u00e9l\u00e9chargement d\u2019un document");
    }
}
