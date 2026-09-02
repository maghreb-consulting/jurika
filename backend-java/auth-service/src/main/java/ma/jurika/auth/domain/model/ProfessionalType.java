package ma.jurika.auth.domain.model;

import java.util.Optional;

/**
 * Type de profil declare a l'inscription d'un workspace (RG-SU — onboarding 2026-06-24).
 *
 * <p>Jusqu'ici l'inscription ne gerait implicitement que le "cabinet". On
 * generalise en 8 categories metier couvrant les professions juridiques /
 * comptables marocaines plus l'entreprise cliente directe et un fourre-tout
 * {@link #AUTRE}.
 *
 * <p>La valeur est NULLABLE en base : les workspaces crees avant cette
 * evolution n'ont pas de type (legitime, cf migration V29). Le nouveau front
 * envoie toujours une valeur (defaut UI). La persistance se fait sous forme de
 * {@code name()} (chaine majuscule) dans {@code workspaces.professional_type},
 * miroir exact des valeurs du CHECK constraint Postgres.
 */
public enum ProfessionalType {

    COMPTABLE_AGREE("Comptable agree"),
    CONSEILLER_JURIDIQUE("Conseiller juridique"),
    CENTRE_AFFAIRES("Centre d'affaires"),
    EXPERT_COMPTABLE("Expert-comptable"),
    ENTREPRISE("Entreprise"),
    AVOCAT("Avocat"),
    NOTAIRE("Notaire"),
    AUTRE("Autre");

    private final String labelFr;

    ProfessionalType(String labelFr) {
        this.labelFr = labelFr;
    }

    /** Libelle francais pour l'API / l'affichage (optionnel). */
    public String labelFr() {
        return labelFr;
    }

    /**
     * Parse defensif d'une chaine venant du DTO ou de la base. Insensible a la
     * casse et tolere les espaces. Retourne {@link Optional#empty()} pour null,
     * vide ou valeur inconnue — l'appelant decide du traitement (rejet ou NULL).
     */
    public static Optional<ProfessionalType> fromString(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = raw.trim();
        if (normalized.isEmpty()) return Optional.empty();
        for (ProfessionalType t : values()) {
            if (t.name().equalsIgnoreCase(normalized)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /** True si la chaine correspond a l'une des 8 valeurs (insensible casse). */
    public static boolean isValid(String raw) {
        return fromString(raw).isPresent();
    }
}
