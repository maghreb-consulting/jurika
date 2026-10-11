package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lot L4a (E5, RG-TKT-04) : documents d'ANNULATION d'un ticket, tous parcours.
 *
 * <p>La charge vient de workflow-service ({@code ChargeUtileAnnulation}) : les variables y
 * sont deja sous leur nom du dictionnaire (faits du ticket, donnees derivees, saisies de
 * l'ecran d'annulation, demarches interrompues). Le mapper :
 * <ul>
 *   <li>reprend ces variables (dates ISO mises au format des actes) ;</li>
 *   <li>pose l'identite de la societe lue sur sa fiche (dossier) quand le magasin ne la
 *       porte pas (forme juridique au libelle des modeles) ;</li>
 *   <li>pour la LETTRE DE RETRAIT, une par demarche interrompue : retient la demarche de
 *       rang {@code retraitIndex} et en tire l'administration, la date et la reference du
 *       depot. Sans demarche interrompue, pas de lettre : refus nomme.</li>
 * </ul>
 * Creation : modeles de la creation ; autres parcours : modeles {@code _SOCIETE_EXISTANTE}.
 */
@Component
public class AnnulationMapper implements WorkflowDocumentMapper {

    public static final String WORKFLOW = "ANNULATION";

    static final Set<String> LETTRES = Set.of("LETTRE_RETRAIT_DEPOT", "LETTRE_RETRAIT_DEPOT_SOCIETE_EXISTANTE");
    private static final Set<String> TEMPLATES = Set.of(
            "NOTE_ANNULATION_DOSSIER", "LETTRE_RETRAIT_DEPOT", "BORDEREAU_REMISE_DOSSIER",
            "NOTE_ANNULATION_DOSSIER_SOCIETE_EXISTANTE", "LETTRE_RETRAIT_DEPOT_SOCIETE_EXISTANTE",
            "BORDEREAU_REMISE_DOSSIER_SOCIETE_EXISTANTE");
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public String workflowCode() {
        return WORKFLOW;
    }

    @Override
    public Set<String> supportedTemplates() {
        return TEMPLATES;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
        if (templateCode == null || !TEMPLATES.contains(templateCode)) {
            throw new IllegalArgumentException("Template non support\u00e9 par AnnulationMapper : " + templateCode);
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> v = new LinkedHashMap<>();
        Object brutes = safe.get("variables");
        if (brutes instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).forEach((k, val) -> v.put(k, formate(val)));
        }
        identite(v, safe.get("societe") instanceof Map<?, ?> s ? (Map<String, Object>) s : Map.of());
        gerance(v, safe.get("gerants") instanceof List<?> g ? (List<Map<String, Object>>) g : List.of());

        if (LETTRES.contains(templateCode)) {
            List<Map<String, Object>> interrompues = v.get("DEMARCHES_INTERROMPUES") instanceof List<?> l
                    ? (List<Map<String, Object>>) l : List.of();
            int rang = rang(safe.get("retraitIndex"), interrompues.size());
            if (rang < 0 || rang >= interrompues.size()) {
                throw new GenerationRefuseeException(templateCode, "Aucune d\u00e9marche interrompue \u00e0 retirer",
                        List.of(new GenerationRefuseeException.DonneeManquante("DEMARCHES_INTERROMPUES",
                                "D\u00e9marche d\u00e9pos\u00e9e aupr\u00e8s d'une administration (\u00e0 retirer)", null)));
            }
            Map<String, Object> d = interrompues.get(rang);
            putSi(v, "RETRAIT_ADMINISTRATION_DESIGNATION", d.get("DEMARCHE_ADMINISTRATION"));
            putSi(v, "RETRAIT_DEPOT_DATE", d.get("DEMARCHE_DATE_DEPOT"));
            putSi(v, "RETRAIT_DEPOT_REFERENCE", d.get("DEMARCHE_REFERENCE"));
            // Saisies propres a cette lettre (adresse, objet, pieces...), portees par la demarche.
            d.forEach((k, val) -> {
                if (k.startsWith("RETRAIT_")) v.put(k, val);
            });
        }
        return v;
    }

    /** Identite de la societe, lue sur la fiche (enrichissement du dossier), si le magasin ne la porte pas. */
    private static void identite(Map<String, Object> v, Map<String, Object> societe) {
        putSi(v, "DENOMINATION", societe.get("denomination"));
        putSi(v, "SIEGE_SOCIAL", premier(societe.get("siegeSocial"), societe.get("adresseSiege")));
        putSi(v, "SIEGE_VILLE", societe.get("ville"));
        putSi(v, "ICE", premier(societe.get("ice"), societe.get("iceNumero")));
        putSi(v, "RC_NUMERO", societe.get("rcNumero"));
        putSi(v, "RC_VILLE", premier(societe.get("rcVille"), societe.get("villeGreffe")));
        putSi(v, "CAPITAL_CHIFFRES", premier(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        String forme = String.valueOf(premier(societe.get("formeJuridique"), v.get("FORME_JURIDIQUE"), "")).trim();
        if (forme.equalsIgnoreCase("SARL_AU") || forme.equalsIgnoreCase("SARL AU")) {
            v.put("FORME_JURIDIQUE", "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e \u00e0 associ\u00e9 unique");
        } else if (forme.equalsIgnoreCase("SARL")) {
            v.put("FORME_JURIDIQUE", "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e");
        }
    }

    /**
     * Gerance en fonction (fiche du dossier) : boucle GERANTS et accord grammatical, derive du
     * nombre de gerants et de leur civilite ; civilite inconnue : l'accord reste absent et la
     * generation le nomme.
     */
    private static void gerance(Map<String, Object> v, List<Map<String, Object>> gerants) {
        if (v.get("GERANTS") instanceof List<?> l && !l.isEmpty()) return;
        List<Map<String, Object>> boucle = new ArrayList<>();
        for (Map<String, Object> g : gerants) {
            if (g == null || premier(g.get("nom")) == null) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            putSi(it, "GERANT_CIVILITE", g.get("civilite"));
            putSi(it, "GERANT_PRENOM", g.get("prenom"));
            putSi(it, "GERANT_NOM", g.get("nom"));
            boucle.add(it);
        }
        if (boucle.isEmpty()) return;
        v.put("GERANTS", boucle);
        if (boucle.size() > 1) {
            putSi(v, "GERANT_ACCORD", "plusieurs");
        } else {
            String c = String.valueOf(boucle.get(0).getOrDefault("GERANT_CIVILITE", "")).trim().toLowerCase(java.util.Locale.ROOT);
            if (c.startsWith("mme") || c.startsWith("mlle") || c.startsWith("madame")) putSi(v, "GERANT_ACCORD", "un f\u00e9minin");
            else if (c.equals("m") || c.equals("m.") || c.startsWith("monsieur")) putSi(v, "GERANT_ACCORD", "un masculin");
        }
    }

    /** Une seule demarche : rang 0 ; plusieurs : le rang est exige (une lettre par administration). */
    private static int rang(Object brut, int taille) {
        if (brut == null) return taille == 1 ? 0 : -1;
        try {
            return Integer.parseInt(String.valueOf(brut).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object formate(Object val) {
        if (val instanceof String s && s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(s).format(FR);
        }
        if (val instanceof List<?> l) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> it = new LinkedHashMap<>();
                    ((Map<String, Object>) m).forEach((k, x) -> it.put(k, formate(x)));
                    out.add(it);
                }
            }
            return out;
        }
        return val;
    }

    private static Object premier(Object... valeurs) {
        for (Object o : valeurs) {
            if (o != null && !String.valueOf(o).isBlank()) return o;
        }
        return null;
    }

    private static void putSi(Map<String, Object> v, String k, Object val) {
        if (val != null && !String.valueOf(val).isBlank() && (v.get(k) == null || String.valueOf(v.get(k)).isBlank())) {
            v.put(k, val);
        }
    }
}
