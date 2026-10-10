package ma.jurika.ai.workflow.identity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L3 (RG-VAR-03, RG-FIC-03) : reprise automatique des donnees de la societe dans les
 * actes, et plus d'identite avalee en cas d'erreur (motif 9).
 */
class RepriseFicheSocieteTest {

    @Test
    void les_variables_canoniques_de_la_fiche_sont_reprises_sans_ecraser_le_mapper() {
        Map<String, Object> societe = new LinkedHashMap<>();
        societe.put("ville", "Rabat");
        societe.put("ice", "001234567000089");
        societe.put("ifNumero", "40123456");
        societe.put("identifiantTp", "TP-77");
        societe.put("cnssNumero", "CNSS-88");
        societe.put("rcNumero", "123456");
        societe.put("rcVille", "Rabat");
        societe.put("villeGreffe", "Rabat");
        societe.put("denomination", "ACME");
        Map<String, Object> variables = new HashMap<>();
        variables.put("DENOMINATION", "ACME SARL (valeur du mapper)");
        variables.put("SIEGE_VILLE", "");

        Map<String, Object> out = RepriseFicheSociete.completer(variables, Map.of("societe", societe));

        assertThat(out).containsEntry("SIEGE_VILLE", "Rabat")
                .containsEntry("ICE", "001234567000089")
                .containsEntry("IDENTIFIANT_FISCAL", "40123456")
                .containsEntry("IDENTIFIANT_TP", "TP-77")
                .containsEntry("CNSS_NUMERO", "CNSS-88")
                .containsEntry("RC_NUMERO", "123456")
                .containsEntry("RC_VILLE", "Rabat")
                .containsEntry("TRIBUNAL_VILLE", "Rabat")
                .containsEntry("DENOMINATION", "ACME SARL (valeur du mapper)");
    }

    @Test
    void sans_donnee_de_la_societe_rien_n_est_invente() {
        Map<String, Object> out = RepriseFicheSociete.completer(new HashMap<>(), Map.of());
        assertThat(out).doesNotContainKeys("SIEGE_VILLE", "ICE", "TRIBUNAL_VILLE");
    }

    @Test
    void une_identite_illisible_fait_echouer_au_lieu_d_une_map_vide() {
        WorkflowIdentityClient client = org.mockito.Mockito.mock(WorkflowIdentityClient.class);
        org.mockito.Mockito.when(client.identity(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("workflow-service indisponible"));
        RemoteSocieteIdentityProvider provider = new RemoteSocieteIdentityProvider(client);
        assertThatThrownBy(() -> provider.loadIdentity(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IdentiteSocieteIndisponibleException.class);
    }
}
