package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vérifie l'aplatissement de l'identité société : colonnes {@code entreprise_dossiers}
 * + {@code fiche_structuree} JSONB → map aux clés attendues par {@code SeancePvVarsBuilder}
 * (côté ai-service). L'{@link EntityManager} est mocké (pas de base réelle).
 */
class DossierIdentityQueryServiceTest {

    private static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DOSSIER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private DossierIdentityQueryService withRow(Object[] row) {
        EntityManager em = mock(EntityManager.class);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyInt(), any())).thenReturn(q);
        // Cast en (Object) pour un Stream A UN element (l'Object[] entier), sinon
        // Stream.of(Object[]) l'eclate en varargs (10 elements) et le cast (Object[]) casse.
        when(q.getResultStream()).thenReturn(row == null ? Stream.empty() : Stream.of((Object) row));
        DossierIdentityQueryService svc = new DossierIdentityQueryService();
        ReflectionTestUtils.setField(svc, "em", em);
        return svc;
    }

    @Test
    void aplatit_colonnes_et_fiche_vers_les_cles_du_builder() {
        String fiche = "{\"nombreParts\":1000,\"valeurNominale\":100,"
                + "\"associes\":[{\"nom\":\"BENALI\",\"nombreParts\":1000}],"
                + "\"gerance\":[{\"nom\":\"BENALI\",\"prenom\":\"Karim\",\"civilite\":\"M.\"}]}";
        Object[] row = {
                "ACME MAROC SARL", "SARL", "001234567000089", "40012345",
                "RC 123456", "Casablanca", new BigDecimal("100000.00"),
                "12 rue de la Liberté, Casablanca", "Casablanca", fiche,
                "ACTIVE", null
        };

        Map<String, Object> id = withRow(row).identity(WS, DOSSIER);

        assertThat(id.get("denomination")).isEqualTo("ACME MAROC SARL");
        assertThat(id.get("formeJuridique")).isEqualTo("SARL");
        assertThat(id.get("ice")).isEqualTo("001234567000089");
        assertThat(id.get("ifNumero")).isEqualTo("40012345");
        assertThat(id.get("rcNumero")).isEqualTo("RC 123456");
        // rc_tribunal exposé sous les deux clés lues par le builder.
        assertThat(id.get("villeGreffe")).isEqualTo("Casablanca");
        assertThat(id.get("rcVille")).isEqualTo("Casablanca");
        assertThat(id.get("capitalChiffres")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(id.get("capitalSocial")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(id.get("siegeSocial")).isEqualTo("12 rue de la Liberté, Casablanca");
        assertThat(id.get("adresseSiege")).isEqualTo("12 rue de la Liberté, Casablanca");
        assertThat(id.get("nombreParts")).isEqualTo(1000);
        assertThat(id.get("valeurNominalePart")).isEqualTo(100);
        assertThat(id).containsKeys("associes", "gerants");
        assertThat(id.get("statut")).isEqualTo("ACTIVE");
        // Societe non dissoute : ni date de dissolution ni liquidateur.
        assertThat(id).doesNotContainKeys("dateDissolution", "liquidateur", "siegeLiquidation");
    }

    /**
     * Lot « Liquidation 4 etapes » (2026-08-13) : une societe DISSOUTE expose la date de
     * dissolution (colonne) ET le liquidateur nomme a la dissolution + le siege de la
     * liquidation (fiche structuree). C'est la source unique du workflow LIQUIDATION,
     * qui ne re-saisit donc jamais ces donnees.
     */
    @Test
    @SuppressWarnings("unchecked")
    void expose_liquidateur_siege_et_date_de_dissolution_pour_une_societe_dissoute() {
        String fiche = "{\"nombreParts\":1000,"
                + "\"liquidateur\":{\"source\":\"BD\",\"civilite\":\"M.\",\"prenom\":\"Ahmed\","
                + "\"nom\":\"ALAOUI\",\"adresse\":\"45 BD ZERKTOUNI, CASABLANCA\"},"
                + "\"siegeLiquidation\":\"12 RUE DES FOULES, CASABLANCA\"}";
        Object[] row = {
                "ACME MAROC SARL", "SARL", "001234567000089", "40012345",
                "RC 123456", "Casablanca", new BigDecimal("100000.00"),
                "12 rue de la Liberté, Casablanca", "Casablanca", fiche,
                "DISSOUTE", java.time.LocalDate.of(2026, 5, 15)
        };

        Map<String, Object> id = withRow(row).identity(WS, DOSSIER);

        assertThat(id.get("statut")).isEqualTo("DISSOUTE");
        assertThat(id.get("dateDissolution")).isEqualTo("2026-05-15");
        assertThat(id.get("siegeLiquidation")).isEqualTo("12 RUE DES FOULES, CASABLANCA");
        Map<String, Object> liquidateur = (Map<String, Object>) id.get("liquidateur");
        assertThat(liquidateur).containsEntry("nom", "ALAOUI");
        assertThat(liquidateur).containsEntry("prenom", "Ahmed");
        assertThat(liquidateur).containsEntry("source", "BD");
    }

    @Test
    void dossier_introuvable_renvoie_map_vide() {
        assertThat(withRow(null).identity(WS, DOSSIER)).isEmpty();
    }

    @Test
    void parametres_nuls_renvoient_map_vide_sans_requete() {
        DossierIdentityQueryService svc = new DossierIdentityQueryService();
        assertThat(svc.identity(null, DOSSIER)).isEmpty();
        assertThat(svc.identity(WS, null)).isEmpty();
    }
}
