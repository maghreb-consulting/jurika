package ma.jurika.ticket.application.referentiel;

import ma.jurika.ticket.application.referentiel.ReferentielCorpus.Ligne;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4b (E4, RG-DEM-02) : depot et retrait d'une formalite, deux lignes liees du corpus.
 * Corpus du 2026-10-03 (lecture independante) : 630 depots, 422 retraits apparies.
 */
class FormalitesCorpusTest {

    private static Ligne l(int ordre, String decision, String libelle) {
        return new Ligne("MATRICE_COMPTES_RESULTAT_SARL", "F", "SARL", ordre, decision, null, "DEROULEMENT_DEMARCHE",
                libelle, null, null, true, null, null, null, null, null, null);
    }

    @Test
    void depot_et_retrait_de_la_meme_formalite_partagent_un_code() {
        List<String[]> f = ImportReferentielDemarches.formalites(List.of(
                l(12, "depot_etats_synthese", "D\u00e9p\u00f4t des \u00e9tats au greffe \u2014 d\u00e9p\u00f4t"),
                l(13, "depot_etats_synthese", "D\u00e9p\u00f4t des \u00e9tats au greffe \u2014 retrait du r\u00e9c\u00e9piss\u00e9 de d\u00e9p\u00f4t"),
                l(14, "autre", "Inscription \u2014 retrait du mod\u00e8le 7"),
                l(15, "x", "Signature du proc\u00e8s-verbal")));
        assertThat(f.get(0)).containsExactly("R3-12", "DEPOT");
        assertThat(f.get(1)).containsExactly("R3-12", "RETRAIT");
        assertThat(f.get(2)).as("retrait sans depot de la meme decision : ligne simple").isNull();
        assertThat(f.get(3)).isNull();
    }

    @Test
    void un_retrait_d_une_autre_decision_n_est_pas_apparie() {
        List<String[]> f = ImportReferentielDemarches.formalites(List.of(
                l(1, "nomination_cac", "Inscription modificative \u2014 d\u00e9p\u00f4t"),
                l(2, "fin_mission_cac", "Inscription modificative \u2014 retrait du mod\u00e8le 7")));
        assertThat(f.get(0)).containsExactly("R3-1", "DEPOT");
        assertThat(f.get(1)).isNull();
    }
}
