package ma.jurika.ai.document.corpus;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lot L2 : lecture, en lecture seule, des classeurs de pilotage du corpus
 * ({@code INDEX_CORPUS.xlsx} et {@code 00_COMMUN/DICTIONNAIRE_UNIQUE_VARIABLES.xlsx}).
 * Les colonnes sont reperees par le libelle de leur en-tete, jamais par leur
 * position. Toute incoherence est une {@link CorpusException} (bloquante).
 */
public final class LecteurCorpus {

    static final String INDEX = "INDEX_CORPUS.xlsx";
    static final String DICTIONNAIRE = "00_COMMUN/DICTIONNAIRE_UNIQUE_VARIABLES.xlsx";

    static final String ONGLET_MODELES = "Mod\u00e8les";
    static final String COL_FAMILLE_DOSSIER = "Famille (dossier)";
    static final String COL_FAMILLE = "Famille";
    static final String COL_MODELE = "Mod\u00e8le";
    static final String COL_FORME = "Forme";
    static final String COL_MODELE_BASE = "Mod\u00e8le de base";
    static final String COL_JUMEAU = "Jumeau SARL / SARL AU";
    static final String COL_GABARIT = "Gabarit Word";

    static final String ONGLET_VARIABLES = "Variables";
    static final String COL_VARIABLE = "Variable";
    static final String ONGLET_ALIAS = "Alias";
    static final String COL_ALIAS = "Alias (nom employ\u00e9 par un mod\u00e8le)";
    static final String COL_CANONIQUE = "Nom canonique retenu";

    private LecteurCorpus() {
    }

    /** Modeles de l'index, dans l'ordre du classeur. */
    public static List<ModeleCorpus> lireIndex(Path racine) {
        List<Map<String, String>> lignes = lireOnglet(racine.resolve(INDEX), ONGLET_MODELES,
                List.of(COL_FAMILLE_DOSSIER, COL_FAMILLE, COL_MODELE, COL_FORME,
                        COL_MODELE_BASE, COL_JUMEAU, COL_GABARIT));
        List<ModeleCorpus> modeles = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (Map<String, String> l : lignes) {
            String code = l.get(COL_MODELE);
            if (code.isEmpty()) {
                continue;
            }
            if (!codes.add(code)) {
                throw new CorpusException(INDEX + " : modele en double : " + code);
            }
            String gabarit = l.get(COL_GABARIT);
            if (gabarit.isEmpty()) {
                throw new CorpusException(INDEX + " : modele sans gabarit Word : " + code);
            }
            modeles.add(new ModeleCorpus(code, l.get(COL_FAMILLE_DOSSIER), l.get(COL_FAMILLE),
                    l.get(COL_FORME), l.get(COL_MODELE_BASE), l.get(COL_JUMEAU), gabarit));
        }
        if (modeles.isEmpty()) {
            throw new CorpusException(INDEX + " : aucun modele");
        }
        return modeles;
    }

    /** Dictionnaire unique : variables et alias (tout alias doit viser une variable connue). */
    public static DictionnaireUnique lireDictionnaire(Path racine) {
        Path fichier = racine.resolve(DICTIONNAIRE);
        Set<String> variables = new HashSet<>();
        for (Map<String, String> l : lireOnglet(fichier, ONGLET_VARIABLES, List.of(COL_VARIABLE))) {
            if (!l.get(COL_VARIABLE).isEmpty()) {
                variables.add(l.get(COL_VARIABLE));
            }
        }
        if (variables.isEmpty()) {
            throw new CorpusException(DICTIONNAIRE + " : aucune variable");
        }
        Map<String, String> alias = new HashMap<>();
        for (Map<String, String> l : lireOnglet(fichier, ONGLET_ALIAS, List.of(COL_ALIAS, COL_CANONIQUE))) {
            String a = l.get(COL_ALIAS);
            if (a.isEmpty()) {
                continue;
            }
            String canonique = l.get(COL_CANONIQUE);
            if (!variables.contains(canonique)) {
                throw new CorpusException(DICTIONNAIRE + " : l'alias " + a
                        + " vise une variable inconnue : " + canonique);
            }
            if (alias.put(a, canonique) != null) {
                throw new CorpusException(DICTIONNAIRE + " : alias en double : " + a);
            }
        }
        return new DictionnaireUnique(variables, alias);
    }

    /** Lignes d'un onglet (en-tete exclu), colonnes requises reperees par leur libelle. */
    static List<Map<String, String>> lireOnglet(Path fichier, String onglet, List<String> colonnes) {
        if (!Files.isRegularFile(fichier)) {
            throw new CorpusException("Fichier du corpus introuvable : " + fichier);
        }
        DataFormatter format = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(fichier.toFile(), null, true)) {
            Sheet feuille = wb.getSheet(onglet);
            if (feuille == null) {
                throw new CorpusException(fichier.getFileName() + " : onglet absent : " + onglet);
            }
            Row entete = feuille.getRow(feuille.getFirstRowNum());
            Map<String, Integer> index = new LinkedHashMap<>();
            if (entete != null) {
                for (Cell c : entete) {
                    index.putIfAbsent(format.formatCellValue(c).trim(), c.getColumnIndex());
                }
            }
            for (String col : colonnes) {
                if (!index.containsKey(col)) {
                    throw new CorpusException(fichier.getFileName() + " / " + onglet
                            + " : colonne absente : " + col);
                }
            }
            List<Map<String, String>> lignes = new ArrayList<>();
            for (int r = feuille.getFirstRowNum() + 1; r <= feuille.getLastRowNum(); r++) {
                Row ligne = feuille.getRow(r);
                if (ligne == null) {
                    continue;
                }
                Map<String, String> valeurs = new HashMap<>();
                for (String col : colonnes) {
                    Cell c = ligne.getCell(index.get(col));
                    valeurs.put(col, c == null ? "" : format.formatCellValue(c).trim());
                }
                lignes.add(valeurs);
            }
            return lignes;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof CorpusException ce) {
                throw ce;
            }
            throw new CorpusException("Lecture impossible : " + fichier + " : " + ex.getMessage(), ex);
        }
    }
}
