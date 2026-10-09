package ma.jurika.ai.document.corpus;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lot L2 : charge un corpus date en LECTURE SEULE (jamais modifie) : index,
 * dictionnaire unique et alias, gabarits Word avec leur empreinte SHA-256, et
 * controles d'integration (annexe technique, section 2).
 *
 * <p>Erreurs BLOQUANTES (CorpusException : le service refuse de demarrer) :
 * classeur ou gabarit absent ou illisible, en-tete manquant, modele en double,
 * alias orphelin. Structure d'un gabarit defaillante : gabarit non rendable,
 * signale au rapport. Variables hors dictionnaire et marqueurs residuels :
 * avertissements, signales au rapport.
 */
public final class ChargeurCorpus {

    private ChargeurCorpus() {
    }

    public static CorpusCharge charger(Path racine) {
        if (racine == null || !Files.isDirectory(racine)) {
            throw new CorpusException("Racine du corpus introuvable : " + racine);
        }
        Path absolue = racine.toAbsolutePath().normalize();
        List<ModeleCorpus> modeles = LecteurCorpus.lireIndex(absolue);
        DictionnaireUnique dictionnaire = LecteurCorpus.lireDictionnaire(absolue);
        Map<String, GabaritCorpus> gabarits = new LinkedHashMap<>();
        for (ModeleCorpus m : modeles) {
            Path fichier = absolue.resolve(m.gabarit()).normalize();
            if (!fichier.startsWith(absolue)) {
                throw new CorpusException("Gabarit hors du corpus : " + m.code() + " -> " + m.gabarit());
            }
            if (!Files.isRegularFile(fichier)) {
                throw new CorpusException("Gabarit introuvable : " + m.code() + " -> " + fichier);
            }
            byte[] contenu;
            try {
                contenu = Files.readAllBytes(fichier);
            } catch (IOException ex) {
                throw new CorpusException("Gabarit illisible : " + fichier, ex);
            }
            ControlesIntegration.Resultat r = ControlesIntegration.controler(paragraphes(m.code(), contenu), dictionnaire);
            gabarits.put(m.code(), new GabaritCorpus(m, fichier, sha256(contenu), r.variables(),
                    r.erreursStructure(), r.avertissements()));
        }
        return new CorpusCharge(absolue.getFileName().toString(), absolue, Instant.now(), dictionnaire, gabarits);
    }

    /** Texte de chaque paragraphe : corps (tableaux compris), en-tetes et pieds de page. */
    static List<String> paragraphes(String code, byte[] contenu) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(contenu))) {
            List<String> out = new ArrayList<>();
            for (XWPFHeader h : doc.getHeaderList()) elements(h.getBodyElements(), out);
            elements(doc.getBodyElements(), out);
            for (XWPFFooter f : doc.getFooterList()) elements(f.getBodyElements(), out);
            return out;
        } catch (IOException | RuntimeException ex) {
            throw new CorpusException("Gabarit Word illisible : " + code + " : " + ex.getMessage(), ex);
        }
    }

    private static void elements(List<IBodyElement> elements, List<String> out) {
        for (IBodyElement e : elements) {
            if (e instanceof XWPFParagraph p) {
                out.add(p.getText());
            } else if (e instanceof XWPFTable t) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        elements(cell.getBodyElements(), out);
                    }
                }
            }
        }
    }

    static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
