package ma.jurika.billing.application.editeur;

import ma.jurika.billing.application.editeur.EditeurParametresService.MentionsLegales;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.EditeurParametresJpaRepository;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lot L6b (E2) : mentions legales PUBLIQUES de l'editeur (pied de page de la vitrine). Seules les
 * mentions legales sortent, telles que saisies par le super-admin ; une mention absente est
 * nulle (jamais une valeur inventee) ; ni identifiant interne, ni auteur, ni date, ni TVA.
 */
class MentionsLegalesPubliquesTest {

    private final EditeurParametresJpaRepository repo = mock(EditeurParametresJpaRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EditeurParametresService service = new EditeurParametresService(repo, jdbc);

    @Test
    void les_mentions_saisies_sortent_telles_quelles() {
        EditeurParametresEntity e = new EditeurParametresEntity();
        e.setRaisonSociale("Maghreb Consulting");
        e.setFormeJuridique("SARL");
        e.setCapitalSocial("100 000");
        e.setSiegeSocial("12 rue de l'Exemple");
        e.setVille("Casablanca");
        e.setRcNumero("123456");
        e.setRcTribunal("Casablanca");
        e.setIce("001122334455667");
        e.setIdentifiantFiscal("12345678");
        e.setTaxeProfessionnelle("34567890");
        e.setRepresentantNom("Nom du g\u00e9rant");
        e.setRepresentantQualite("G\u00e9rant");
        e.setTauxTvaPourcent(new BigDecimal("20.00"));
        e.setModifiePar(UUID.randomUUID());
        e.setModifieLe(Instant.now());
        when(repo.unique()).thenReturn(Optional.of(e));

        assertThat(service.mentionsLegales()).isEqualTo(new MentionsLegales(
                "Maghreb Consulting", "SARL", "100 000", "12 rue de l'Exemple", "Casablanca",
                "123456", "Casablanca", "001122334455667", "12345678", "34567890",
                "Nom du g\u00e9rant", "G\u00e9rant"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void une_mention_absente_ou_blanche_est_nulle_jamais_inventee() {
        EditeurParametresEntity e = new EditeurParametresEntity();
        e.setRaisonSociale("Maghreb Consulting");
        e.setIce("   ");
        when(repo.unique()).thenReturn(Optional.of(e));

        MentionsLegales m = service.mentionsLegales();
        assertThat(m.raisonSociale()).isEqualTo("Maghreb Consulting");
        assertThat(m.ice()).as("blanc = non renseigne").isNull();
        assertThat(Arrays.asList(m.formeJuridique(), m.capitalSocial(), m.siegeSocial(), m.ville(), m.rcNumero(),
                m.rcTribunal(), m.identifiantFiscal(), m.taxeProfessionnelle(), m.representantNom(),
                m.representantQualite())).containsOnlyNulls();
    }

    @Test
    void ligne_vide_a_l_installation_toutes_les_mentions_sont_nulles() {
        when(repo.unique()).thenReturn(Optional.of(new EditeurParametresEntity()));
        MentionsLegales m = service.mentionsLegales();
        for (RecordComponent c : MentionsLegales.class.getRecordComponents()) {
            try {
                assertThat(c.getAccessor().invoke(m)).as(c.getName()).isNull();
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    @Test
    void seules_les_mentions_legales_sont_exposees() {
        assertThat(Arrays.stream(MentionsLegales.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("raisonSociale", "formeJuridique", "capitalSocial", "siegeSocial", "ville",
                        "rcNumero", "rcTribunal", "ice", "identifiantFiscal", "taxeProfessionnelle",
                        "representantNom", "representantQualite");
        assertThat(Arrays.stream(MentionsLegales.class.getRecordComponents()).map(RecordComponent::getType))
                .as("du texte seulement").allMatch(String.class::equals);
    }
}
