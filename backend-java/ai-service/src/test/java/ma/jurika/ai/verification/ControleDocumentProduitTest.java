package ma.jurika.ai.verification;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4a (E1) : ce que le controle des documents produits doit voir. Avant, le controle
 * ne cherchait que les variables residuelles ; un acte portant "VALEUR MANQUANTE" ou un
 * blanc "de ," passait pour conforme (motif 9).
 */
class ControleDocumentProduitTest {

    @Test
    void valeur_manquante_est_un_defaut() {
        assertThat(ControleDocumentProduit.defauts(
                "Le \u2039 VALEUR MANQUANTE : DATE_AG \u203a, les associes...", Set.of()))
                .anySatisfy(d -> assertThat(d).contains("DATE_AG"));
    }

    @Test
    void a_obtenir_attendu_passe_non_attendu_echoue() {
        String t = "ICE : \u2039 \u00c0 OBTENIR : ICE \u203a ; RC : \u2039 \u00c0 OBTENIR : RC_NUMERO \u203a";
        assertThat(ControleDocumentProduit.defauts(t, Set.of("ICE", "RC_NUMERO"))).isEmpty();
        assertThat(ControleDocumentProduit.defauts(t, Set.of("ICE")))
                .singleElement().asString().contains("RC_NUMERO");
    }

    @Test
    void blanc_dans_une_phrase_est_un_defaut() {
        assertThat(ControleDocumentProduit.defauts("immatriculee au registre du commerce de , sous le numero", Set.of()))
                .isNotEmpty();
        assertThat(ControleDocumentProduit.defauts("demeurant a , titulaire", Set.of())).isNotEmpty();
        assertThat(ControleDocumentProduit.defauts("Fait \u00e0 , le 12 juin 2026", Set.of())).isNotEmpty();
        assertThat(ControleDocumentProduit.defauts("le capital ( ) est", Set.of())).isNotEmpty();
        assertThat(ControleDocumentProduit.defauts("Casablanca, , Maroc", Set.of())).isNotEmpty();
    }

    @Test
    void phrase_complete_ne_declenche_rien() {
        assertThat(ControleDocumentProduit.defauts(
                "Immatriculee au registre du commerce de Casablanca, sous le numero 12345. "
                        + "Fait \u00e0 Rabat, le 12 juin 2026. Les associes presents (deux) ont signe.",
                Set.of())).isEmpty();
    }

    @Test
    void drapeau_oui_non_imprime_seul_est_un_defaut() {
        assertThat(ControleDocumentProduit.defauts("L'associe unique\noui\nSignature : ....", Set.of())).isNotEmpty();
        assertThat(ControleDocumentProduit.defauts("Quorum atteint : oui", Set.of())).isEmpty();
    }

    @Test
    void la_documentation_non_publiee_d_un_modele_ne_s_imprime_pas() {
        assertThat(ControleDocumentProduit.defauts("Pour avis.\nNOTES D'EMPLOI (non publi\u00e9es)\nCoh\u00e9rence.", java.util.Set.of()))
                .contains("documentation du modele imprimee : NOTES D'EMPLOI");
    }
}
