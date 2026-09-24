package ma.jurika.workflow.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Une variable du magasin, telle que l'interface la montre.
 *
 * <p>La provenance n'est pas décorative : la décision 2 du cabinet veut qu'une
 * valeur déjà connue s'affiche <b>en lecture, avec sa provenance</b>, plutôt
 * qu'elle ne soit redemandée. L'employé doit voir que la donnée existe, et d'où
 * elle vient, sans avoir à la retrouver.
 *
 * @param variable  nom SANS le {@code $}
 * @param boucle    nom de la boucle, ou {@code null} hors boucle
 * @param rang      rang 0-based dans la boucle, ou {@code null}
 * @param valeur    la valeur, telle qu'elle s'imprimera
 * @param origine   {@link Origine} — d'où elle vient
 * @param saisiePar auteur de la saisie ({@code null} si {@code BASE} ou {@code DERIVEE})
 * @param saisieLe  horodatage
 * @param occasion  l'écran ou le document à l'occasion duquel elle a été posée
 */
public record VariableDuDossier(
        String variable,
        String boucle,
        Short rang,
        String valeur,
        Origine origine,
        UUID saisiePar,
        Instant saisieLe,
        String occasion) {

    /** D'où vient une valeur. Contrainte en base par un {@code CHECK}. */
    public enum Origine {
        /** Un humain l'a tapée. Porte toujours son auteur. */
        SAISIE,
        /** La plateforme la détenait déjà — dossier, ticket, société. */
        BASE,
        /** Calculée depuis d'autres variables. Jamais demandée à personne. */
        DERIVEE;

        public static Origine of(String raw) {
            return raw == null ? SAISIE : valueOf(raw);
        }
    }

    public boolean estRenseignee() {
        return valeur != null && !valeur.isBlank();
    }
}
