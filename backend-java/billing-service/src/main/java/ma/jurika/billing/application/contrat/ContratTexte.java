package ma.jurika.billing.application.contrat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L7 (E6, E9) : texte du contrat d'abonnement. Le modele (presentation, pieces A, B et C) est
 * le texte valide par la direction, repris tel quel ; ses champs a remplir sont des variables
 * {@code $MAJUSCULES}. Le document source (version 1 : {@code contrat/CONTRAT_ABONNEMENT_JURIKA_V1.md},
 * copie exacte du document valide) porte en plus une ligne de publication en tete et la piece D
 * (conditions de l'essai, acceptees a l'inscription a l'essai, RG-CTR-10) : {@link #modeleDepuisSource}
 * les retire, sans rien changer d'autre. Le rendu est deterministe : a donnees identiques, texte identique, donc
 * empreinte identique (RG-CTR-02).
 *
 * <p>Le paragraphe "Acceptation electronique" ne peut etre rempli qu'apres l'acceptation (il
 * cite l'empreinte du texte accepte) : le texte lu et accepte le porte avec la mention
 * {@link #A_COMPLETER} ; l'exemplaire (RG-CTR-04) le remplace par les donnees de l'acceptation.
 */
public final class ContratTexte {

    public static final String A_COMPLETER = "[compl\u00e9t\u00e9 lors de l\u2019acceptation]";
    static final String DEBUT_ACCEPTATION = "**Acceptation \u00e9lectronique.**";
    private static final Pattern VARIABLE = Pattern.compile("\\$([A-Z][A-Z_]*)");
    /**
     * Ligne de publication du document source ("*Version 1 du ..., validee par la direction...
     * Les mentions en $MAJUSCULES sont remplies automatiquement...*") : note d'edition, pas une
     * clause ; "$MAJUSCULES" y designe la convention, ce n'est pas une variable.
     */
    private static final Pattern EN_TETE_PUBLICATION = Pattern.compile("\\A\\*Version [^\\n]*\\*\\n");
    private static final Pattern CROCHETS = Pattern.compile("\\[[^\\]\\n]*\\]");

    /** Variables remplies apres l'acceptation (paragraphe "Acceptation electronique"). */
    public static final Set<String> VARIABLES_ACCEPTATION = Set.of(
            "ACCEPTATION_DATE", "ACCEPTATION_HEURE", "ACCEPTATION_IP", "CONTRAT_EMPREINTE");

    /** Toutes les variables connues du modele. */
    public static final Set<String> VARIABLES = Set.of(
            "EDITEUR_DENOMINATION", "EDITEUR_REPRESENTANT_NOM", "EDITEUR_REPRESENTANT_QUALITE",
            // EDITEUR_REPRESENTANT (nom et qualite d'un seul tenant) : projet du 12 octobre 2026, retire.
            "EDITEUR_FORME", "EDITEUR_CAPITAL", "EDITEUR_SIEGE", "EDITEUR_RC_VILLE", "EDITEUR_RC_NUMERO",
            "EDITEUR_ICE", "EDITEUR_IF", "EDITEUR_REPRESENTANT",
            "CABINET_DENOMINATION", "CABINET_FORME", "CABINET_SIEGE", "CABINET_RC_VILLE", "CABINET_RC_NUMERO",
            "CABINET_ICE", "CABINET_EMAIL",
            "SIGNATAIRE_CIVILITE", "SIGNATAIRE_NOM", "SIGNATAIRE_PRENOM", "SIGNATAIRE_QUALITE", "SIGNATAIRE_COMPTE",
            "ABONNEMENT_REFERENCE", "FORFAIT_NOM", "FORFAIT_DOSSIERS", "FORFAIT_UTILISATEURS", "FORFAIT_STOCKAGE",
            "RYTHME_PAIEMENT", "PRIX_HT", "PRIX_PERIODE", "PRIX_ANNUEL_HT", "DATE_DEBUT", "DATE_ECHEANCE",
            "MODE_PAIEMENT", "CG_VERSION",
            "ACCEPTATION_DATE", "ACCEPTATION_HEURE", "ACCEPTATION_IP", "CONTRAT_EMPREINTE");

    /** Sections du modele et leurs empreintes. */
    public record Sections(String conditionsGenerales, String accordTraitement,
                           String empreinteModele, String empreinteCg, String empreinteDpa) {}

    private ContratTexte() {}

    public static String sha256(String texte) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Modele d'une version a partir de son document source : la ligne de publication en tete et la
     * piece D (essai) sont retirees ; le reste est repris tel quel (fins de ligne normalisees).
     */
    public static String modeleDepuisSource(String source) {
        String m = normaliser(source == null ? "" : source);
        Matcher entete = EN_TETE_PUBLICATION.matcher(m);
        if (entete.find()) m = m.substring(entete.end());
        int d = debutTitre(m, "# D.");
        if (d >= 0) m = m.substring(0, d);
        return normaliser(m);
    }

    /**
     * Verifie la structure d'un modele (pieces A, B, C, paragraphe d'acceptation, variables
     * connues) et calcule ses empreintes. Un modele non conforme est refuse (donnees nommees).
     */
    public static Sections sections(String modele) {
        if (modele == null || modele.isBlank()) {
            throw ContratException.invalide("MODELE_VIDE", "Le texte du contrat est vide.");
        }
        String m = normaliser(modele);
        List<String> defauts = new ArrayList<>();
        int a = debutTitre(m, "# A.");
        int b = debutTitre(m, "# B.");
        int c = debutTitre(m, "# C.");
        if (a < 0) defauts.add("titre \u00ab # A. Conditions particuli\u00e8res \u00bb");
        if (b < 0) defauts.add("titre \u00ab # B. Conditions g\u00e9n\u00e9rales d\u2019abonnement \u00bb");
        if (c < 0) defauts.add("titre \u00ab # C. Accord de traitement des donn\u00e9es personnelles \u00bb");
        if (a >= 0 && b >= 0 && c >= 0 && !(a < b && b < c)) defauts.add("ordre des pi\u00e8ces A, B, C");
        if (debutTitre(m, "# D.") >= 0) defauts.add("pi\u00e8ce D (conditions de l\u2019essai) hors du contrat d\u2019abonnement");
        // Mention entre crochets = passage encore a completer ou a arbitrer (projet) : jamais presentee.
        Set<String> crochets = new LinkedHashSet<>();
        Matcher cr = CROCHETS.matcher(m);
        while (cr.find()) crochets.add(cr.group());
        if (!crochets.isEmpty()) defauts.add("mentions entre crochets \u00e0 compl\u00e9ter " + String.join(", ", crochets));
        if (paragrapheAcceptation(m) == null) defauts.add("paragraphe \u00ab " + DEBUT_ACCEPTATION + " \u00bb");
        Set<String> inconnues = new LinkedHashSet<>();
        Matcher v = VARIABLE.matcher(m);
        while (v.find()) {
            if (!VARIABLES.contains(v.group(1))) inconnues.add("$" + v.group(1));
        }
        if (!inconnues.isEmpty()) defauts.add("variables inconnues " + String.join(", ", inconnues));
        if (!defauts.isEmpty()) {
            throw new ContratException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "MODELE_NON_CONFORME",
                    "Le texte du contrat n\u2019est pas conforme : " + String.join(" ; ", defauts) + ".", defauts);
        }
        String cg = m.substring(b, c).strip() + "\n";
        String dpa = m.substring(c).strip() + "\n";
        return new Sections(cg, dpa, sha256(m), sha256(cg), sha256(dpa));
    }

    /** Fins de ligne normalisees (LF), espaces de fin de ligne retires, une fin de ligne finale. */
    public static String normaliser(String texte) {
        String t = texte.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder();
        for (String ligne : t.split("\n", -1)) {
            sb.append(ligne.stripTrailing()).append('\n');
        }
        return sb.toString().strip() + "\n";
    }

    /**
     * Remplace chaque variable par sa valeur. Les valeurs arrivent deja composees (donnee absente
     * = mention "[non renseigne : ...]" posee par l'appelant). Une variable sans valeur est une
     * erreur de programmation (jamais un blanc silencieux).
     */
    public static String remplir(String modele, Map<String, String> valeurs) {
        Matcher v = VARIABLE.matcher(modele);
        StringBuilder sb = new StringBuilder();
        while (v.find()) {
            String nom = v.group(1);
            String val = valeurs.get(nom);
            if (val == null) {
                throw new IllegalStateException("Variable du contrat sans valeur : $" + nom);
            }
            v.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        v.appendTail(sb);
        return sb.toString();
    }

    /** Paragraphe "Acceptation electronique" (bloc de lignes jusqu'a la ligne vide), ou null. */
    public static String paragrapheAcceptation(String texte) {
        int i = texte.indexOf(DEBUT_ACCEPTATION);
        if (i < 0 || texte.indexOf(DEBUT_ACCEPTATION, i + 1) >= 0) return null;
        int fin = texte.indexOf("\n\n", i);
        return fin < 0 ? texte.substring(i).strip() : texte.substring(i, fin);
    }

    private static int debutTitre(String m, String prefixe) {
        if (m.startsWith(prefixe)) return 0;
        int i = m.indexOf("\n" + prefixe);
        return i < 0 ? -1 : i + 1;
    }
}
