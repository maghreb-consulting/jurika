package ma.jurika.ai.document.format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Détecteur d'<b>assemblage fautif</b> dans un acte rendu (2026-08-17) — support de test
 * PARTAGÉ par tous les tests de rendu.
 *
 * <p>Complément indispensable aux assertions de non-vacuité : celles-ci exigent qu'une
 * variable laisse une valeur derrière elle, mais ne disent rien de la façon dont cette
 * valeur <b>se soude</b> au texte du modèle. D'où des documents « pleins » et pourtant
 * fautifs, en pleine en-tête d'acte :
 * <pre>
 *   « Fait à au siège social, le 20/09/2026 »        (préposition du modèle + article de la valeur)
 *   « PROCÈS-VERBAL DES DÉCISIONS DE le gérant unique »
 *   « GmbH DE DROIT Allemagne »                      (nom de pays au lieu d'un adjectif)
 * </pre>
 *
 * <p>Classe publique et sans état : appelée par chaque test de rendu sur le texte qu'il
 * vient de produire, ce qui rend la vérification indépendante de l'ordre d'exécution.
 */
public final class AssemblageFautif {

    private AssemblageFautif() {}

    /** Motifs de soudure ratée entre le texte du modèle et une valeur injectée. */
    public static final Map<String, Pattern> MOTIFS = new LinkedHashMap<>() {{
        // Préposition non contractée : « à au », « à le », « de le », « de les »…
        //
        // UNICODE_CHARACTER_CLASS est INDISPENSABLE : par défaut `\b` s'appuie sur
        // `\w` = [a-zA-Z0-9_], donc « à » n'est pas un caractère de mot et aucune limite
        // ne se forme autour — le motif ne mordait tout simplement pas sur « à au ».
        // Deux motifs distincts, et surtout PAS un produit cartésien : « à des tiers »,
        // « à des conditions normales », « à du personnel » sont du français correct
        // (article indéfini / partitif). Un motif unique « (à|de) + (le|les|du|des|au|aux) »
        // criait sur la prose même des modèles directeur. Seules sont fautives :
        //   après « à » → le / les (doivent contracter) et au / aux (préposition doublée) ;
        //   après « de » → le / les (doivent contracter) et du / des (préposition doublée).
        // (a) Préposition DOUBLÉE — aucune lecture correcte possible : « à au », « à aux »,
        //     « de du », « de des ». C'est la signature exacte de « Fait à » + « au siège ».
        //     Chaque préposition a SES formes doublées, et croiser les deux listes crée des
        //     faux positifs : « à des tiers », « à du personnel » sont corrects (article
        //     indéfini / partitif), tout comme « de du » n'existe pas après « à ».
        put("préposition doublée",
                Pattern.compile("\\bà\\s+(?:au|aux)\\b|\\bde\\s+(?:du|des)\\b",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                                | Pattern.UNICODE_CHARACTER_CLASS));
        // (b) Article défini non contracté — « DE le gérant unique ».
        //
        //     Piège : « le » / « les » peuvent aussi être des PRONOMS, et la séquence est
        //     alors parfaitement correcte (« en vue de les annuler », « commencer à le
        //     faire »). Ces tournures existent dans la prose même des modèles directeur.
        //     On écarte donc le cas où le mot suivant est un INFINITIF (terminaison -er,
        //     -ir, -re, -oir), ce qui distingue le pronom de l'article. Le compromis est
        //     assumé : un faux négatif (« de le livre ») est préférable à un détecteur
        //     qui crie sur du texte juste — on cesserait vite de l'écouter.
        put("article non contracté après préposition",
                Pattern.compile("\\b(?:à|de)\\s+(?:le|les)\\s+(?!\\p{L}*(?:er|ir|re|oir)\\b)\\p{L}",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                                | Pattern.UNICODE_CHARACTER_CLASS));
        // « de droit Allemagne » : nom de pays là où il faut un adjectif. On repère une
        // MAJUSCULE initiale après « de droit » — les adjectifs de nationalité sont en
        // minuscules, les noms de pays portent une capitale.
        //
        // La locution est cherchée sans égard à la casse (le titre du modèle l'écrit
        // « DE DROIT »), mais l'initiale qui suit doit rester SENSIBLE à la casse : un
        // CASE_INSENSITIVE global ferait matcher `\p{Lu}` sur une minuscule et le motif
        // crierait sur « de droit allemand », qui est pourtant la forme correcte. D'où
        // les groupes à drapeau local (?i:…) / (?-i:…).
        //     La capitale seule ne suffit plus à trancher depuis que les valeurs d'en-tête
        //     passent en majuscules (« GmbH DE DROIT ALLEMAND », forme CORRECTE) : on exige
        //     donc une capitale SUIVIE D'UNE MINUSCULE, qui est la signature d'un nom
        //     propre (« Allemagne », « Royaume-Uni ») et jamais celle d'un mot tout en
        //     capitales.
        put("nom de pays au lieu d'un adjectif (« de droit <Pays> »)",
                Pattern.compile("(?i:de\\s+droit\\s+)(?-i:(?!étranger)\\p{Lu}\\p{Ll})",
                        Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS));
        // Espaces multiples : trace d'une variable vide absorbée entre deux mots.
        put("espaces multiples", Pattern.compile("\\S {2,}\\S"));
        // Virgule orpheline / doublée.
        put("virgule orpheline", Pattern.compile(",\\s*,|\\s+,\\s*,"));
        // « n° , » — numéro annoncé sans valeur.
        put("numéro vide (« n° , »)", Pattern.compile("n[°o]\\s*[,;.]"));
    }};

    /**
     * Liste les assemblages fautifs d'un texte rendu, ligne par ligne.
     *
     * @return liste vide si le texte est sain ; sinon un libellé par faute, citant la
     *         ligne en cause — un test qui dirait seulement « ce document est fautif »
     *         ne servirait à rien à qui doit le corriger
     */
    public static List<String> dans(String texte) {
        List<String> defauts = new ArrayList<>();
        if (texte == null) return defauts;
        for (String ligne : texte.split("\\R")) {
            if (ligne.isBlank()) continue;
            for (Map.Entry<String, Pattern> e : MOTIFS.entrySet()) {
                Matcher m = e.getValue().matcher(ligne);
                if (m.find()) {
                    defauts.add(e.getKey() + " → « " + ligne.trim() + " »");
                }
            }
            String casse = casseIncoherente(ligne);
            if (casse != null) defauts.add(casse + " → « " + ligne.trim() + " »");
        }
        return defauts;
    }

    /**
     * Valeur injectée restée en MINUSCULES au milieu d'un titre en MAJUSCULES
     * (2026-08-17). Aucune expression régulière ne peut trancher seule : il faut peser
     * les mots de la ligne les uns contre les autres.
     *
     * <p><b>Règle</b> : la ligne est tenue pour un en-tête si ses mots tout en majuscules
     * sont <b>strictement plus nombreux</b> que ses mots tout en minuscules ; dans ce cas,
     * tout mot intégralement minuscule est signalé.
     *
     * <p><b>Ce que la comparaison protège.</b> Une simple présence de mots majuscules ne
     * suffirait pas : le corps d'un PV contient des raisons sociales en capitales
     * (« … de la société MEDITERRANEA GMBH, GmbH de droit allemand au capital de … »)
     * et serait signalé à tort. La domination des majuscules distingue un titre d'une
     * phrase qui cite un nom d'entreprise.
     *
     * <p>Les mots à casse MIXTE (« GmbH », « München ») ne comptent dans aucun camp :
     * ce sont des sigles ou des noms propres, dont la casse est voulue.
     */
    private static String casseIncoherente(String ligne) {
        boolean premiereMajusculeVue = false;
        String coupable = null;
        int majuscules = 0;
        int minuscules = 0;
        for (String mot : ligne.split("[^\\p{L}]+")) {
            if (mot.length() < 2) continue;          // « à », « n », initiales : neutres
            boolean aMaj = false;
            boolean aMin = false;
            for (int i = 0; i < mot.length(); i++) {
                if (Character.isUpperCase(mot.charAt(i))) aMaj = true;
                else if (Character.isLowerCase(mot.charAt(i))) aMin = true;
            }
            boolean toutMaj = aMaj && !aMin;
            boolean toutMin = aMin && !aMaj;

            if (!premiereMajusculeVue) {
                // Une minuscule AVANT toute majuscule => phrase ordinaire, pas un titre.
                // Écarte « L'an DEUX MILLE VINGT-SIX, … » et « Report à nouveau : … ».
                if (toutMin) return null;
                if (toutMaj) {
                    premiereMajusculeVue = true;
                    majuscules++;   // le 1er mot capitalisé compte, lui aussi
                }
                continue;
            }
            // Après la 1re majuscule, un mot à casse MIXTE trahit une phrase, pas un titre :
            // « SIÈGE SOCIAL : 101 bd Zerktouni, Casablanca », « Mme Salma IDRISSI, 8 rue B,
            // Rabat » — lignes légitimes qu'un contrôle plus lâche signalait à tort.
            if (!toutMaj && !toutMin) return null;
            if (toutMaj) majuscules++;
            if (toutMin) minuscules++;
            // Seul un mot d'au moins 3 lettres constitue un indice : « bd », « an »,
            // « rue » abrégés dans une adresse ne prouvent rien.
            if (toutMin && mot.length() >= 3 && coupable == null) coupable = mot;
        }
        // Dans un TITRE, les minuscules sont l'exception ; dans une phrase, la règle.
        // Sans ce dernier critère, « Karim BENNANI, agissant en qualité de gérant. » ou
        // « HOLDING ATLAS apporte à la société … (QUARANTE MILLE). » seraient signalées :
        // un nom de famille ou un montant en lettres y suffit à poser une majuscule.
        // On exige donc au moins deux mots capitalisés, et STRICTEMENT moins de minuscules.
        // L'égalité ne suffit pas : « HOLDING ATLAS …… 400 parts (parts n° 601 à 1000) »
        // est une ligne de tableau des statuts — 2 capitales contre 2 minuscules — où la
        // raison sociale est simplement écrite en capitales.
        boolean titre = majuscules >= 2 && minuscules >= 1 && minuscules < majuscules;
        return (titre && coupable != null)
                ? "valeur en minuscules dans un titre en majuscules (« " + coupable + " »)"
                : null;
    }
}
