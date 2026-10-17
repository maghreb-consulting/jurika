package ma.jurika.common.courriel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette R3 : version texte brut de chaque courriel, derivee du HTML rendu (lisible sans HTML :
 * paragraphes, liens en clair, entites decodees, ni styles ni commentaires).
 */
class TexteBrutCourrielTest {

    @Test
    void paragraphes_liens_entites_et_rien_du_decor() {
        String html = "<!DOCTYPE html><html><head><title>T</title><style>p{color:red}</style></head><body>"
                + "<!-- commentaire -->"
                + "<table><tr><td>&#9670;&#9671;</td></tr></table>"
                + "<p>Bonjour <strong>Karim</strong>,</p>"
                + "<p>Votre r&eacute;f&eacute;rence&nbsp;: <b>JUR-A4F2K</b> &mdash; c&rsquo;est not&eacute;.</p>"
                + "<a href=\"https://exemple.test/activer?t=1&amp;u=2\">Activer mon compte</a>"
                + "<a href=\"mailto:support@exemple.test\">support@exemple.test</a>"
                + "<img src=\"cid:logo-jurika\" alt=\"JURIKA\">"
                + "</body></html>";
        String t = TexteBrut.depuisHtml(html);
        assertThat(t).contains("Bonjour Karim,")
                .contains("Votre r\u00e9f\u00e9rence\u00a0: JUR-A4F2K \u2014 c\u2019est not\u00e9.")
                .contains("Activer mon compte : https://exemple.test/activer?t=1&u=2")
                .contains("support@exemple.test")
                .doesNotContain("support@exemple.test : mailto")
                .doesNotContain("p{color").doesNotContain("commentaire").doesNotContain("<")
                .doesNotContain("\u25c6").doesNotContain("&amp;").doesNotContain("&nbsp;");
        assertThat(t).doesNotContain("\n\n\n");
    }

    @Test
    void toutes_les_entites_nommees_connues_sont_decodees() {
        for (String e : TexteBrut.ENTITES.keySet()) {
            assertThat(TexteBrut.depuisHtml("<p>a&" + e + ";b</p>")).as(e).doesNotContain("&" + e + ";");
        }
        assertThat(TexteBrut.depuisHtml("<p>&#233;&#xE9;</p>")).isEqualTo("\u00e9\u00e9");
    }
}
