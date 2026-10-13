package ma.jurika.dashboard.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActionLabelsTest {

    @Test
    void mapsKnownActionsToFrench() {
        assertThat(AuditActionLabels.label("TICKET_CREATED")).isEqualTo("Ticket cr\u00e9\u00e9");
        assertThat(AuditActionLabels.label("DOSSIER_TRANSFERE")).isEqualTo("Dossier transf\u00e9r\u00e9");
        assertThat(AuditActionLabels.label("TICKET_ASSIGNED")).isEqualTo("Ticket r\u00e9assign\u00e9");
        assertThat(AuditActionLabels.label("TICKET_UPDATED")).isEqualTo("Ticket modifi\u00e9");
        assertThat(AuditActionLabels.label("FISCAL_UPLOADED")).isEqualTo("Document fiscal d\u00e9pos\u00e9");
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
