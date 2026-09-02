package ma.jurika.common.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Modele de tableau agnostique du moteur de rendu, consomme par
 * {@link JurikaPdfDocument#table(PdfTable)}. Le rendu (mesure, retour a la ligne,
 * saut de page avec repetition de l'en-tete, lignes alternees, alignement) est
 * assure par {@link JurikaPdfDocument}. Ce modele ne depend d'aucune librairie PDF.
 */
public final class PdfTable {

    public enum Align { LEFT, RIGHT, CENTER }

    /** Style logique d'une ligne de texte (mappe en police/taille/couleur par le rendu). */
    public enum Style { NORMAL, STRONG, MUTED, ACCENT, SUCCESS }

    /** Une ligne de texte dans une cellule (une cellule peut en empiler plusieurs). */
    public record Line(String text, Style style) {}

    /** Une cellule = une pile de lignes + un alignement (null = alignement de la colonne). */
    public static final class Cell {
        private final List<Line> lines = new ArrayList<>();
        private Align align; // null -> herite de la colonne

        public Cell add(String text, Style style) {
            lines.add(new Line(text == null ? "" : text, style));
            return this;
        }

        public Cell align(Align a) {
            this.align = a;
            return this;
        }

        public List<Line> lines() { return lines; }
        public Align align() { return align; }

        /** Cellule mono-ligne NORMAL. */
        public static Cell of(String text) {
            return new Cell().add(text, Style.NORMAL);
        }

        public static Cell of(String text, Style style) {
            return new Cell().add(text, style);
        }
    }

    public record Column(String header, float weight, Align align) {}

    private final List<Column> columns = new ArrayList<>();
    private final List<List<Cell>> rows = new ArrayList<>();

    public PdfTable column(String header, float weight, Align align) {
        columns.add(new Column(header, weight, align));
        return this;
    }

    public PdfTable row(Cell... cells) {
        rows.add(List.of(cells));
        return this;
    }

    public List<Column> columns() { return columns; }
    public List<List<Cell>> rows() { return rows; }
    public boolean isEmpty() { return rows.isEmpty(); }
}
