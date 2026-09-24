package ma.jurika.workflow.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LA RÈGLE DE NOMMAGE DE L'ÉTAPE 7, ET SA RÉCIPROQUE.
 *
 * <p>Le catalogue des 180 champs est généré : sa clé d'écran est dérivée du nom
 * de la variable par une règle explicite du script de dérivation.
 *
 * <pre>const cleDe = (nom) =&gt; nom.toLowerCase().replace(/_([a-z0-9])/g, (_, c) =&gt; c.toUpperCase());</pre>
 *
 * <p>Le projecteur applique l'inverse. Ce n'est pas un rapprochement par
 * ressemblance — c'est la réciproque d'une fonction connue, et ces cas le
 * vérifient sur des variables réelles du corpus.
 */
class ProjecteurVariablesCreationTest {

    @Test
    @DisplayName("bailleurNom redonne BAILLEUR_NOM — et les cas du corpus avec")
    void laCleRedonneSaVariable() {
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("bailleurNom"))
                .isEqualTo("BAILLEUR_NOM");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("domiciliationRedevanceChiffres"))
                .isEqualTo("DOMICILIATION_REDEVANCE_CHIFFRES");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("retraitDepotReference"))
                .isEqualTo("RETRAIT_DEPOT_REFERENCE");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("commissaireApportsDateDesignation"))
                .isEqualTo("COMMISSAIRE_APPORTS_DATE_DESIGNATION");
    }

    @Test
    @DisplayName("Un segment déjà en capitales ou un mot simple traversent sans dommage")
    void lesCasLimites() {
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("subdivision")).isEqualTo("SUBDIVISION");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("fax")).isEqualTo("FAX");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("")).isEmpty();
        assertThat(ProjecteurVariablesCreation.variableDeLaCle(null)).isEmpty();
    }
}
