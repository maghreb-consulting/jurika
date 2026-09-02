package ma.jurika.ai.document.format;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Passe de relecture TRANSVERSE sur les échantillons persistés (2026-08-17).
 *
 * <p>Les tests de rendu écrivent leurs sorties dans {@code target/echantillons-*}. Ce
 * test les relit TOUS et leur applique le détecteur d'assemblage fautif — doubles
 * prépositions, nom de pays employé comme adjectif, espaces multiples, virgules
 * orphelines, numéros vides.
 *
 * <p><b>Pourquoi une passe globale plutôt que des assertions par document</b> : les
 * quatre fautes relevées le 2026-08-16 se ressemblaient (une préposition du modèle
 * soudée à une valeur qui portait déjà son article) mais vivaient dans trois workflows
 * différents. Un contrôle par document aurait exigé de deviner à l'avance où chercher.
 * Ici, tout nouvel échantillon produit par n'importe quel test de rendu est
 * automatiquement inspecté — y compris ceux qui n'existent pas encore.
 *
 * <p><b>Dépendance d'ordre assumée</b> : ce test lit ce que les tests de rendu ont
 * écrit. Lancé seul sur un {@code target/} vide, il ne trouve rien et se met en
 * ABANDON explicite plutôt que de passer au vert sans rien vérifier — un test qui
 * n'inspecte aucun fichier ne doit jamais se déclarer satisfait.
 */
class EchantillonsAssemblageTest {

    private static final Path TARGET = Path.of("target");

    @Test
    @DisplayName("Aucun assemblage fautif dans les échantillons régénérés (tous workflows)")
    void aucunAssemblageFautifDansLesEchantillons() throws Exception {
        List<Path> docx = new ArrayList<>();
        if (Files.isDirectory(TARGET)) {
            try (Stream<Path> dirs = Files.list(TARGET)) {
                for (Path d : dirs.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("echantillons-"))
                        .toList()) {
                    try (Stream<Path> f = Files.walk(d)) {
                        docx.addAll(f.filter(p -> p.toString().endsWith(".docx")).toList());
                    }
                }
            }
        }

        assumeThat(docx)
                .describedAs("Aucun échantillon dans target/echantillons-* : lancer d'abord les "
                        + "tests de rendu (`mvn -pl ai-service test`). Ce test ne peut rien "
                        + "affirmer sans fichier à inspecter.")
                .isNotEmpty();

        List<String> defauts = new ArrayList<>();
        for (Path p : docx) {
            String texte = texteDe(p);
            for (String d : AssemblageFautif.dans(texte)) {
                defauts.add("  • " + TARGET.relativize(p) + " — " + d);
            }
        }

        assertThat(defauts)
                .describedAs("Assemblages fautifs détectés dans %d échantillon(s) :%n%s",
                        docx.size(), String.join("\n", defauts))
                .isEmpty();
    }

    private static String texteDe(Path docxFile) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(docxFile);
             XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
            doc.getTables().forEach(t -> t.getRows().forEach(
                    r -> r.getTableCells().forEach(c -> sb.append(c.getText()).append('\n'))));
        }
        return sb.toString();
    }
}
