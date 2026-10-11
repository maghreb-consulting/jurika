package ma.jurika.ticket.application.referentiel;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4d (E7) : article et statut lus dans le texte du corpus, sans invention ; ajustement du
 * cabinet applique aux seules lignes de depot au greffe de sa nature d'acte.
 */
class DelaisGreffeTest {

    @Test
    void article_cite_par_la_matrice() {
        assertThat(DelaisGreffe.article("1 mois \u2014 texte de la matrice : Inscription modificative au registre du "
                + "commerce du si\u00e8ge dans le mois (art. 75 du code de commerce)"))
                .isEqualTo("art. 75 du code de commerce (loi n\u00b0 15-95)");
        assertThat(DelaisGreffe.article("D\u00e9p\u00f4t au greffe dans les trente jours (art. 95 de la loi n\u00b0 5-96)"))
                .isEqualTo("art. 95 de la loi n\u00b0 5-96");
        assertThat(DelaisGreffe.article("1 mois \u2014 texte de la matrice : Inscription RC dans le mois")).isNull();
    }

    @Test
    void sans_article_ou_avec_reserve_le_delai_est_a_confirmer() {
        assertThat(DelaisGreffe.statut("Inscription RC dans le mois", null)).isEqualTo(DelaisGreffe.Statut.A_CONFIRMER);
        String probable = "D\u00e9p\u00f4t au greffe dans les trente jours (art. 95 de la loi n\u00b0 5-96) ; le code de "
                + "commerce pr\u00e9voit en outre, au niveau PROBABLE, ...";
        assertThat(DelaisGreffe.statut(probable, DelaisGreffe.article(probable))).isEqualTo(DelaisGreffe.Statut.A_CONFIRMER);
        String cite = "dans le mois (art. 75 du code de commerce)";
        assertThat(DelaisGreffe.statut(cite, DelaisGreffe.article(cite))).isEqualTo(DelaisGreffe.Statut.CITE_PAR_LE_CORPUS);
    }

    @Test
    void l_ajustement_du_cabinet_remplace_le_delai_des_lignes_de_depot() {
        Map<String, Object> ligne = Map.of("matrice", "M", "decision_code", "transfert_siege",
                "organisme", "Greffe du tribunal de commerce", "libelle", "D\u00e9p\u00f4t au greffe \u2014 d\u00e9p\u00f4t",
                "delai", "1 mois", "delai_valeur", 1, "delai_unite", "MOIS");
        Map<String, DelaisGreffe.Ajustement> a = Map.of("M|transfert_siege",
                new DelaisGreffe.Ajustement(15, "JOURS", "art. 75 du code de commerce", null, null, "2026-10-11"));
        Map<String, Object> ajustee = DelaisGreffe.appliquer(ligne, a);
        assertThat(ajustee).containsEntry("delai_valeur", 15).containsEntry("delai_unite", "JOURS");
        assertThat((String) ajustee.get("delai")).contains("ajust\u00e9 par le cabinet").contains("texte de la matrice : 1 mois");

        Map<String, Object> autre = Map.of("matrice", "M", "decision_code", "transfert_siege",
                "organisme", "Cabinet", "libelle", "G\u00e9n\u00e9ration du document", "delai_valeur", 1, "delai_unite", "MOIS");
        assertThat(DelaisGreffe.appliquer(autre, a)).isSameAs(autre);
    }
}
