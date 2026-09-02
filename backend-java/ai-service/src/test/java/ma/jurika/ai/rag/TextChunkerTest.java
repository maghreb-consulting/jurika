package ma.jurika.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void emptyOrBlankYieldsNoChunks() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    void shortTextProducesSingleChunkPreservingContent() {
        String text = "La SARL doit liberer au moins 25% du capital a la souscription.";
        List<String> chunks = chunker.chunk(text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(text);
    }

    @Test
    void longTextIsSplitAndEveryChunkRespectsMaxPlusOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("Paragraphe numero ").append(i)
              .append(" relatif au droit des societes au Maroc et a la loi 5-96.\n\n");
        }
        int max = 500;
        int recouvrement = Math.round(max * TextChunker.DEFAULT_OVERLAP_RATIO);
        List<String> chunks = chunker.chunk(sb.toString(), max, 100);
        assertThat(chunks.size()).isGreaterThan(1);
        // Le recouvrement s'ajoute en tete : la borne utile est max + recouvrement
        // (+ le separateur « […] » insere entre les deux).
        assertThat(chunks).allSatisfy(c ->
                assertThat(c.length()).isLessThanOrEqualTo(max + recouvrement + 8));
        // Aucun contenu perdu : un mot distinctif du dernier paragraphe est present.
        assertThat(String.join(" ", chunks)).contains("Paragraphe numero 59");
    }

    @Test
    void withoutOverlapEveryChunkStaysUnderMax() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("Paragraphe numero ").append(i).append(" du memo juridique.\n\n");
        }
        List<String> chunks = chunker.chunk(sb.toString(), 500, 100, 0);
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(500));
    }

    @Test
    void paragraphLongerThanMaxIsHardSplit() {
        String huge = "x".repeat(3000); // une "phrase" sans ponctuation
        List<String> chunks = chunker.chunk(huge, 1000, 200, 0);
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(1000));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Recouvrement — une clause a cheval sur deux passages doit rester
    //  retrouvable entiere (sinon le modele repond en signalant lui-meme que
    //  son contexte est tronque).
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void chaquePassageRependLaFinDuPrecedent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("Paragraphe ").append(i)
              .append(" : le consentement de la majorite des associes est requis.\n\n");
        }
        List<String> chunks = chunker.chunk(sb.toString(), 400, 100);
        assertThat(chunks.size()).isGreaterThan(1);

        for (int i = 1; i < chunks.size(); i++) {
            String precedent = chunks.get(i - 1);
            String courant = chunks.get(i);
            assertThat(courant).as("le passage %d doit reprendre la fin du precedent", i)
                    .contains("[…]");
            // Les derniers mots du passage precedent se retrouvent en tete du suivant.
            String derniersMots = precedent.substring(Math.max(0, precedent.length() - 30));
            String queue = derniersMots.substring(derniersMots.indexOf(' ') + 1);
            assertThat(courant).contains(queue);
        }
    }

    @Test
    void recouvrementDesactivableExplicitement() {
        String texte = ("Paragraphe unique et suffisamment long pour depasser la taille cible "
                + "du passage retenue par le test.\n\n").repeat(10);
        List<String> avec = chunker.chunk(texte, 300, 80);
        List<String> sans = chunker.chunk(texte, 300, 80, 0);
        assertThat(avec.get(1)).contains("[…]");
        assertThat(sans).noneSatisfy(c -> assertThat(c).contains("[…]"));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Frontieres juridiques — un article ne doit pas etre coupe en son
    //  milieu quand le texte arrive d'un PDF, c'est-a-dire en un seul bloc
    //  sans ligne vide.
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void unEnTeteDArticleOuvreUnNouveauPassage() {
        // Texte « a plat », tel que l'extraction PDF le rend : aucune ligne vide.
        String blob = "ARTICLE 6 - CAPITAL SOCIAL Le capital social est fixe a la somme de "
                + "deux cent cinquante mille dirhams. ARTICLE 7 - CESSION DE PARTS SOCIALES "
                + "Les parts sont librement cessibles entre associes.";
        List<String> chunks = chunker.chunk(blob, 120, 40, 0);

        // Chaque article ouvre son propre passage : aucun ne commence en plein
        // milieu d'une phrase de l'article precedent.
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).startsWith("ARTICLE 6").doesNotContain("ARTICLE 7");
        assertThat(chunks.get(1)).startsWith("ARTICLE 7");
    }

    @Test
    void uneReferenceEnMinusculesNeCoupePas() {
        // « conformement a l'article 56 de la loi » est une reference dans le corps
        // du texte : la couper produirait des passages absurdes.
        String texte = "Les parts ne peuvent etre cedees a des tiers qu'avec le consentement "
                + "de la majorite des associes representant les trois quarts des parts, "
                + "conformement a l'article 56 de la loi n 5-96.";
        List<String> chunks = chunker.chunk(texte, 1200, 200, 0);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(texte);
    }
}
