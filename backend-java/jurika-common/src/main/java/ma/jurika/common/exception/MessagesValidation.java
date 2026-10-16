package ma.jurika.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import ma.jurika.common.dto.ErrorResponse;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lot L6b (E13) : messages francais des refus de validation (400), qui NOMMENT le champ a corriger.
 *
 * <p>Avant : "Donn\u00e9es invalides" sans champ, et un detail en anglais ("must not be blank")
 * quand la JVM n'est pas en francais (messages par defaut de Bean Validation). Ici, le message
 * d'une contrainte standard est redige en francais d'apres l'annotation, quelle que soit la langue
 * de la JVM ; un message propre au DTO ({@code @NotBlank(message = "...")}) est garde tel quel.
 * Le nom technique du champ reste dans {@code errors[].field} (le front y rattache ses libelles).
 */
public final class MessagesValidation {

    private MessagesValidation() { }

    /** Libelles des noms de champs les plus courants (dernier segment du chemin). */
    private static final Map<String, String> LIBELLES = Map.ofEntries(
            Map.entry("workspaceCode", "code du cabinet"),
            Map.entry("email", "adresse \u00e9lectronique"),
            Map.entry("loginEmail", "identifiant de connexion"),
            Map.entry("contactEmail", "adresse \u00e9lectronique de contact"),
            Map.entry("password", "mot de passe"),
            Map.entry("currentPassword", "mot de passe actuel"),
            Map.entry("newPassword", "nouveau mot de passe"),
            Map.entry("firstName", "pr\u00e9nom"),
            Map.entry("lastName", "nom"),
            Map.entry("prenom", "pr\u00e9nom"),
            Map.entry("nom", "nom"),
            Map.entry("phone", "t\u00e9l\u00e9phone"),
            Map.entry("telephone", "t\u00e9l\u00e9phone"),
            Map.entry("denomination", "d\u00e9nomination"),
            Map.entry("raisonSociale", "raison sociale"),
            Map.entry("formeJuridique", "forme juridique"),
            Map.entry("capitalSocial", "capital social"),
            Map.entry("adresseSiege", "adresse du si\u00e8ge"),
            Map.entry("objetSocial", "objet social"),
            Map.entry("ville", "ville"),
            Map.entry("cin", "num\u00e9ro de CIN"),
            Map.entry("title", "titre"),
            Map.entry("titre", "titre"),
            Map.entry("description", "description"),
            Map.entry("dossierId", "dossier"),
            Map.entry("ticketId", "ticket"),
            Map.entry("clientId", "client"),
            Map.entry("code", "code"));

    /** Libelle lisible d'un chemin de champ ({@code associes[0].nom} : "nom (associes[0])"). */
    static String libelle(String chemin) {
        if (chemin == null || chemin.isBlank()) return "champ";
        int point = chemin.lastIndexOf('.');
        String dernier = point >= 0 ? chemin.substring(point + 1) : chemin;
        String parent = point >= 0 ? chemin.substring(0, point) : null;
        String nom = LIBELLES.getOrDefault(dernier, dernier);
        return parent == null ? nom : nom + " (" + parent + ")";
    }

    private static boolean messageParDefaut(String gabarit) {
        return gabarit == null
                || gabarit.startsWith("{jakarta.validation.constraints.")
                || gabarit.startsWith("{org.hibernate.validator.constraints.");
    }

    /** Message francais d'une violation : le message du DTO s'il en a un, sinon redige d'apres l'annotation. */
    static String message(ConstraintViolation<?> v) {
        if (!messageParDefaut(v.getMessageTemplate())) return v.getMessage();
        Annotation a = v.getConstraintDescriptor().getAnnotation();
        Map<String, Object> attr = v.getConstraintDescriptor().getAttributes();
        boolean liste = v.getInvalidValue() instanceof Collection<?> || v.getInvalidValue() instanceof Map<?, ?>
                || (v.getInvalidValue() != null && v.getInvalidValue().getClass().isArray());
        String unite = liste ? " \u00e9l\u00e9ments" : " caract\u00e8res";
        return switch (a.annotationType().getSimpleName()) {
            case "NotBlank", "NotNull", "NotEmpty" -> "Ce champ est obligatoire.";
            case "Email" -> "Adresse \u00e9lectronique invalide.";
            case "Pattern" -> "Format invalide.";
            case "Size", "Length" -> {
                int min = ((Number) attr.getOrDefault("min", 0)).intValue();
                int max = ((Number) attr.getOrDefault("max", Integer.MAX_VALUE)).intValue();
                if (min > 0 && max < Integer.MAX_VALUE) yield "Entre " + min + " et " + max + unite + ".";
                if (max < Integer.MAX_VALUE) yield max + unite + " au plus.";
                yield min + unite + " au moins.";
            }
            case "Min", "DecimalMin" -> "La valeur doit \u00eatre au moins " + attr.get("value") + ".";
            case "Max", "DecimalMax" -> "La valeur doit \u00eatre au plus " + attr.get("value") + ".";
            case "Positive" -> "La valeur doit \u00eatre strictement positive.";
            case "PositiveOrZero" -> "La valeur doit \u00eatre positive ou nulle.";
            case "Negative" -> "La valeur doit \u00eatre strictement n\u00e9gative.";
            case "NegativeOrZero" -> "La valeur doit \u00eatre n\u00e9gative ou nulle.";
            case "Past" -> "La date doit \u00eatre pass\u00e9e.";
            case "PastOrPresent" -> "La date ne peut pas \u00eatre dans le futur.";
            case "Future" -> "La date doit \u00eatre dans le futur.";
            case "FutureOrPresent" -> "La date ne peut pas \u00eatre pass\u00e9e.";
            case "Digits" -> "Nombre de chiffres invalide.";
            case "AssertTrue" -> "Cette case doit \u00eatre coch\u00e9e.";
            default -> v.getMessage();
        };
    }

    /** "Corrigez le champ << x >> : ..." ou la liste des champs a corriger. */
    static String synthese(List<ErrorResponse.FieldError> erreurs) {
        if (erreurs.isEmpty()) return "Donn\u00e9es invalides : v\u00e9rifiez les champs du formulaire.";
        if (erreurs.size() == 1) {
            ErrorResponse.FieldError e = erreurs.get(0);
            return "Donn\u00e9es invalides. Corrigez le champ \u00ab\u00a0" + libelle(e.field()) + "\u00a0\u00bb : "
                    + minuscule(e.message());
        }
        return "Donn\u00e9es invalides. Corrigez les champs suivants : " + erreurs.stream()
                .map(e -> "\u00ab\u00a0" + libelle(e.field()) + "\u00a0\u00bb : " + sansPoint(minuscule(e.message())))
                .collect(Collectors.joining(" ; ")) + ".";
    }

    private static String minuscule(String m) {
        if (m == null || m.length() < 2) return m == null ? "" : m;
        // "Ce champ..." -> "ce champ..." ; un sigle ("ICE ...") reste tel quel.
        return Character.isUpperCase(m.charAt(0)) && Character.isLowerCase(m.charAt(1))
                ? Character.toLowerCase(m.charAt(0)) + m.substring(1) : m;
    }

    private static String sansPoint(String m) {
        return m.endsWith(".") ? m.substring(0, m.length() - 1) : m;
    }

    /** Chemin d'un champ Jackson ({@code associes[0].nom}). */
    static String chemin(JsonMappingException e) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference r : e.getPath()) {
            if (r.getFieldName() != null) {
                if (!sb.isEmpty()) sb.append('.');
                sb.append(r.getFieldName());
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    /** Type attendu, en francais. */
    static String typeAttendu(Class<?> t) {
        if (t == null) return "une valeur d'un autre type";
        if (t == LocalDate.class) return "une date au format AAAA-MM-JJ";
        if (t == LocalDateTime.class || t == Instant.class || t == OffsetDateTime.class || t == ZonedDateTime.class) {
            return "une date et une heure au format ISO 8601 (AAAA-MM-JJTHH:MM:SS)";
        }
        if (t == LocalTime.class) return "une heure au format HH:MM";
        if (t == UUID.class) return "un identifiant (UUID)";
        if (t == Integer.class || t == int.class || t == Long.class || t == long.class
                || t == Short.class || t == short.class || t == BigInteger.class) return "un nombre entier";
        if (t == BigDecimal.class || t == Double.class || t == double.class || t == Float.class || t == float.class) {
            return "un nombre";
        }
        if (t == Boolean.class || t == boolean.class) return "vrai ou faux (true ou false)";
        if (t == String.class) return "un texte";
        if (t.isArray() || Collection.class.isAssignableFrom(t)) return "une liste";
        if (t.isEnum()) return "l'une des valeurs " + Arrays.toString(t.getEnumConstants());
        return "un objet " + t.getSimpleName();
    }

    /** Message et champ d'un corps JSON illisible. */
    static ErrorResponse corpsIllisible(Throwable cause, String messageSpring) {
        Throwable c = cause;
        while (c != null && !(c instanceof MismatchedInputException)) {
            c = c.getCause() == c ? null : c.getCause();
        }
        if (c instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String champ = chemin(mie);
            String nom = libelle(champ);
            Class<?> type = mie.getTargetType();
            String detail;
            if (mie instanceof InvalidFormatException ife && type != null && type.isEnum()) {
                detail = "Valeur invalide pour le champ \u00ab\u00a0" + nom + "\u00a0\u00bb : \"" + ife.getValue()
                        + "\". Valeurs accept\u00e9es : " + Arrays.toString(type.getEnumConstants()) + ".";
            } else if (mie instanceof InvalidFormatException ife) {
                detail = "Format invalide pour le champ \u00ab\u00a0" + nom + "\u00a0\u00bb : \"" + ife.getValue()
                        + "\" (attendu : " + typeAttendu(type) + ").";
            } else {
                detail = "Type de donn\u00e9e invalide pour le champ \u00ab\u00a0" + nom + "\u00a0\u00bb (attendu : "
                        + typeAttendu(type) + ").";
            }
            return new ErrorResponse("BAD_REQUEST", "Donn\u00e9es invalides. " + detail,
                    List.of(new ErrorResponse.FieldError(champ, detail)));
        }
        boolean absent = (messageSpring != null && messageSpring.startsWith("Required request body is missing"))
                || (c instanceof MismatchedInputException mie2 && mie2.getMessage() != null
                    && mie2.getMessage().startsWith("No content to map"));
        return new ErrorResponse("BAD_REQUEST", absent
                ? "La requ\u00eate est illisible : le corps de la requ\u00eate est absent."
                : "La requ\u00eate est illisible : les donn\u00e9es envoy\u00e9es ne sont pas au format attendu (JSON).");
    }
}
