package ma.jurika.dashboard.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionLabelsTest {

    @Test
    void mapsKnownActionsToFrench() {
        assertThat(AuditActionLabels.label("TICKET_CREATED")).isEqualTo("Ticket cree");
        assertThat(AuditActionLabels.label("DOSSIER_TRANSFERE")).isEqualTo("Dossier transfere");
        assertThat(AuditActionLabels.label("TICKET_ASSIGNED")).isEqualTo("Ticket reassigne");
        assertThat(AuditActionLabels.label("TICKET_UPDATED")).isEqualTo("Ticket modifie");
        assertThat(AuditActionLabels.label("FISCAL_UPLOADED")).isEqualTo("Document fiscal depose");
    }

    @Test
    void humanizesUnknownCodes() {
        assertThat(AuditActionLabels.label("SOME_NEW_ACTION")).isEqualTo("Some new action");
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(AuditActionLabels.label(null)).isEmpty();
        assertThat(AuditActionLabels.label("  ")).isEmpty();
    }
}
