package ma.jurika.workflow.domain.strategy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Etape « saisie des donnees de la succursale », <b>partagee</b> par les workflows
 * SUCCURSALE_MA et SUCCURSALE_ETR — lot DIVERS §B/§C (2026-08-13).
 *
 * <p>La spec est explicite : l'etape 2 de la succursale ETRANGERE est « identique au
 * §B etape 2 ». La dupliquer aurait garanti la derive : deux jeux de regles pour la
 * meme donnee, celle d'un workflow corrigee sans l'autre. Elle vit donc ici, en un
 * seul exemplaire, et produit le bloc {@code succursale} consomme a l'identique par
 * le PV et par l'annonce legale.
 *
 * <p>Regles portees :
 * <ul>
 *   <li>enseigne / adresse / ville / activite / date d'ouverture obligatoires ;</li>
 *   <li><b>ville du greffe de la succursale</b> : distincte de celle du siege (loi
 *       15-95, art. 37 et 40) ; a defaut de saisie, on retient la ville ;</li>
 *   <li><b>dotation</b> facultative : si annoncee, le montant devient obligatoire
 *       (l'avis publie « une dotation de X DH (X en lettres) ») ; si decochee, tout
 *       residu de saisie est PURGE pour ne pas fuir dans les documents ;</li>
 *   <li><b>responsable</b> facultatif : si annonce, identite + adresse + piece +
 *       pouvoirs obligatoires (ils figurent nommement dans l'avis).</li>
 * </ul>
 */
final class SuccursaleSaisieStep {

    private SuccursaleSaisieStep() {}

    static StepResult execute(Map<String, Object> payload, Set<String> responsableSources) {
        Map<String, Object> p = payload == null ? Map.of() : payload;
        Map<String, Object> succ = buildSuccursale(p);

        String enseigne = strOrNull(succ.get("enseigne"));
        if (enseigne == null) return StepResult.blocked("L'enseigne de la succursale est obligatoire.");
        String adresse = strOrNull(succ.get("adresse"));
        if (adresse == null) return StepResult.blocked("L'adresse de la succursale est obligatoire.");
        String ville = strOrNull(succ.get("ville"));
        if (ville == null) return StepResult.blocked("La ville de la succursale est obligatoire.");
        String activite = strOrNull(succ.get("activite"));
        if (activite == null) return StepResult.blocked("L'activite de la succursale est obligatoire.");

        String dateOuvRaw = strOrNull(succ.get("dateOuverture"));
        if (dateOuvRaw == null) {
            return StepResult.blocked("La date d'ouverture de la succursale est obligatoire.");
        }
        if (parseDateOrNull(dateOuvRaw) == null) {
            return StepResult.blocked("Date d'ouverture invalide. Format attendu : AAAA-MM-JJ.");
        }

        succ.put("villeGreffe", strOrNull(first0(succ.get("villeGreffe"), ville)));

        boolean dotationPresente = truthy(succ.get("dotationPresente"));
        Long dotation = toLong(first0(succ.get("dotationMontant"), succ.get("dotationChiffres")));
        if (dotationPresente && (dotation == null || dotation <= 0)) {
            return StepResult.blocked(
                    "Dotation annoncee : precisez son montant (en dirhams). Le montant en "
                            + "lettres est calcule automatiquement.");
        }
        if (!dotationPresente) {
            succ.remove("dotationMontant");
            succ.remove("dotationChiffres");
            dotation = null;
        }
        succ.put("dotationPresente", dotationPresente);
        if (dotation != null) succ.put("dotationMontant", dotation);

        boolean responsablePresent = truthy(succ.get("responsablePresent"));
        Map<String, Object> resp = new LinkedHashMap<>(asMap(succ.get("responsable")));
        if (responsablePresent) {
            String source = strOrNull(resp.get("source"));
            if (source != null && !responsableSources.contains(source.toUpperCase(Locale.ROOT))) {
                return StepResult.blocked(
                        "Origine du responsable invalide : attendu « BD » (gerant / associe du "
                                + "dossier) ou « EXTERNE » (saisie manuelle).");
            }
            if (source != null) resp.put("source", source.toUpperCase(Locale.ROOT));
            if (strOrNull(resp.get("nom")) == null) {
                return StepResult.blocked(
                        "Designez le responsable de la succursale : choisissez un gerant ou un "
                                + "associe existant, ou saisissez un responsable externe.");
            }
            if (strOrNull(resp.get("adresse")) == null) {
                return StepResult.blocked("L'adresse du responsable de la succursale est obligatoire.");
            }
            if (strOrNull(resp.get("pieceNumero")) == null && strOrNull(resp.get("cin")) == null) {
                return StepResult.blocked(
                        "Le numero de piece d'identite du responsable est obligatoire "
                                + "(depot CIN recto-verso + OCR a l'appui).");
            }
            if (strOrNull(resp.get("pouvoirs")) == null) {
                return StepResult.blocked(
                        "Precisez les pouvoirs conferes au responsable de la succursale : "
                                + "ils sont publies dans l'annonce legale.");
            }
            succ.put("responsable", resp);
        } else {
            succ.remove("responsable");
        }
        succ.put("responsablePresent", responsablePresent);

        Map<String, Object> out = new HashMap<>();
        out.put("succursale", succ);
        if (p.get("formalitesMandataireNom") != null) {
            out.put("formalitesMandataireNom", p.get("formalitesMandataireNom"));
        }
        // Le representant resident (succursale etrangere) et le responsable de la
        // succursale designent la MEME personne : on expose le bloc sous les deux
        // cles attendues par les mappers, sans jamais le faire saisir deux fois.
        if (responsablePresent) {
            out.put("representant", resp);
        }
        return StepResult.ok(out);
    }

    /**
     * Assemble le bloc {@code succursale} : accepte un bloc deja structure (front
     * refondu) ou des champs plats (retro-compatibilite scripts / anciens payloads).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildSuccursale(Map<String, Object> p) {
        Object nested = p.get("succursale");
        Map<String, Object> succ = nested instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        for (String k : new String[]{
                "enseigne", "adresse", "ville", "villeGreffe", "activite",
                "dateOuverture", "dotationPresente", "dotationMontant", "dotationChiffres",
                "responsablePresent", "responsable"}) {
            if (!succ.containsKey(k) && p.get(k) != null) {
                succ.put(k, p.get(k));
            }
        }
        return succ;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    private static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.startsWith("o") || s.startsWith("y");
    }

    private static Object first0(Object... vals) {
        for (Object o : vals) {
            if (o != null && !(o instanceof String s && s.isBlank())) return o;
        }
        return null;
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "");
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }
}
