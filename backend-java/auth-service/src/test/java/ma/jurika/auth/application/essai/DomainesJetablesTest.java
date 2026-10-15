package ma.jurika.auth.application.essai;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lot L7 (E4), RG-ESS-02 : adresses jetables refusees (liste en ressource). */
class DomainesJetablesTest {

    private final DomainesJetables jetables = new DomainesJetables();

    @Test
    void la_ressource_est_chargee_et_contient_les_domaines_connus() {
        assertThat(jetables.domaines()).hasSizeGreaterThan(50)
                .contains("yopmail.com", "mailinator.com", "guerrillamail.com", "10minutemail.com", "jetable.org");
    }

    @Test
    void adresse_jetable_refusee_y_compris_sous_domaine_et_casse() {
        assertThat(jetables.estJetable("karim@yopmail.com")).isTrue();
        assertThat(jetables.estJetable("karim@YOPMAIL.FR")).isTrue();
        assertThat(jetables.estJetable("karim@eu.mailinator.com")).isTrue();
    }

    @Test
    void adresse_ordinaire_acceptee() {
        assertThat(jetables.estJetable("karim@gmail.com")).isFalse();
        assertThat(jetables.estJetable("contact@cabinet-benali.ma")).isFalse();
        // Le domaine parent "com" seul n'est jamais compare.
        assertThat(jetables.estJetable("karim@notyopmail.com")).isFalse();
        assertThat(jetables.estJetable(null)).isFalse();
        assertThat(jetables.estJetable("sans-arobase")).isFalse();
    }

    @Test
    void liste_vide_refusee_au_demarrage() {
        assertThatThrownBy(() -> new DomainesJetables(Set.of())).isInstanceOf(IllegalStateException.class);
    }
}
