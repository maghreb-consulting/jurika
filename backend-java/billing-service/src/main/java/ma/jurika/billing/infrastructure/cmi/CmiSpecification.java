package ma.jurika.billing.infrastructure.cmi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lot L7 (E5), RG-PAY-04 et RG-PAY-05 : TOUT ce qui depend de la specification du CMI est isole
 * ici (noms des champs, valeurs fixes, calcul de la signature, codes de retour, reponse attendue).
 *
 * <p><b>Source</b> : kit d'integration "3D Pay Hosting" du CMI (Centre Monetique Interbancaire,
 * plateforme de paiement en ligne des banques marocaines), tel que diffuse aux marchands avec leurs
 * exemples (formulaire POST vers la page hebergee, signature "ver3" en SHA-512, retour serveur
 * {@code callbackUrl} puis reponse texte). La specification officielle n'est pas disponible hors
 * ligne : les points marques <b>A_DECIDER</b> sont a confirmer sur le guide remis par la banque avec
 * le contrat de commerce electronique (CDC 18, action 1) AVANT le branchement au vrai CMI.
 *
 * <p><b>Signature "ver3"</b> : tous les parametres envoyes (ou recus), sauf {@code hash} et
 * {@code encoding} (casse ignoree), tries par NOM sans tenir compte de la casse ; chaque VALEUR est
 * echappee ({@code \} en {@code \\}, {@code |} en {@code \|}) ; les valeurs sont jointes par
 * {@code |}, suivies de {@code |} et de la cle du magasin (echappee de meme) ; SHA-512 de la chaine
 * en UTF-8 ; empreinte binaire encodee en Base64.
 * <br>A_DECIDER : l'ordre du tri des exemples du CMI est un tri "naturel" insensible a la casse
 * (natcasesort) ; il ne differe du tri retenu ici que pour des noms contenant des chiffres (aucun
 * dans les champs envoyes par JURIKA).
 *
 * <p><b>Retour serveur</b> : le CMI poste au {@code callbackUrl} les parametres de la commande et
 * le resultat ({@code ProcReturnCode}, {@code Response}, {@code TransId}, {@code HASH}...). La
 * reponse texte attendue : {@code ACTION=POSTAUTH} quand la signature est valide et le paiement
 * accepte ({@code ProcReturnCode = 00}) ; {@code APPROVED} quand la signature est valide et le
 * paiement refuse (accuse de reception) ; {@code FAILURE} quand la signature est invalide.
 * <br>A_DECIDER : confirmer ces trois reponses et le type de transaction ({@code PreAuth} suivi de
 * {@code ACTION=POSTAUTH}, ou {@code Auth}) sur le guide du CMI.
 */
public final class CmiSpecification {

    private CmiSpecification() {}

    /** Code ISO 4217 numerique du dirham marocain. */
    public static final String DEVISE_MAD = "504";
    /** Type de magasin : page de paiement hebergee par le CMI, avec 3-D Secure. */
    public static final String STORE_TYPE = "3D_PAY_HOSTING";
    /** Version de signature. */
    public static final String HASH_ALGORITHM = "ver3";
    /** A_DECIDER : PreAuth (puis ACTION=POSTAUTH au retour serveur) ou Auth. */
    public static final String TRAN_TYPE = "PreAuth";
    public static final String ENCODING = "UTF-8";

    /** Code de retour d'une autorisation acceptee. */
    public static final String CODE_ACCEPTE = "00";

    /** Reponses texte au retour serveur (A_DECIDER : a confirmer sur le guide du CMI). */
    public static final String REPONSE_ACCEPTE = "ACTION=POSTAUTH";
    public static final String REPONSE_REFUS_ACQUITTE = "APPROVED";
    public static final String REPONSE_SIGNATURE_INVALIDE = "FAILURE";

    // Noms des champs.
    public static final String CLIENT_ID = "clientid";
    public static final String AMOUNT = "amount";
    public static final String CURRENCY = "currency";
    public static final String OID = "oid";
    public static final String OK_URL = "okUrl";
    public static final String FAIL_URL = "failUrl";
    public static final String CALLBACK_URL = "callbackUrl";
    public static final String SHOP_URL = "shopurl";
    public static final String RND = "rnd";
    public static final String HASH_ALGO_FIELD = "hashAlgorithm";
    public static final String STORE_TYPE_FIELD = "storetype";
    public static final String TRAN_TYPE_FIELD = "TranType";
    public static final String LANG = "lang";
    public static final String ENCODING_FIELD = "encoding";
    public static final String BILL_TO_NAME = "BillToName";
    public static final String EMAIL = "email";
    public static final String REFRESH_TIME = "refreshtime";
    public static final String CALLBACK_RESPONSE = "CallbackResponse";
    public static final String HASH = "HASH";
    // Champs du retour.
    public static final String PROC_RETURN_CODE = "ProcReturnCode";
    public static final String RESPONSE = "Response";
    public static final String TRANS_ID = "TransId";

    private static final Set<String> EXCLUS_DE_LA_SIGNATURE = Set.of("hash", "encoding");

    /** Montant au format du CMI : point decimal, deux decimales (ex. 450.00). */
    public static String montant(long centimes) {
        return BigDecimal.valueOf(centimes).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** Montant du CMI en centimes ; refus d'un format inattendu. */
    public static long centimes(String montant) {
        if (montant == null || !montant.trim().matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("Montant du CMI illisible : " + montant);
        }
        return new BigDecimal(montant.trim()).movePointRight(2).longValueExact();
    }

    /** Signature "ver3" des parametres (voir l'en-tete de la classe). */
    public static String signer(Map<String, String> parametres, String cleMagasin) {
        if (cleMagasin == null || cleMagasin.isBlank()) {
            throw new IllegalStateException("Cle du magasin CMI absente (CMI_STORE_KEY)");
        }
        List<String> noms = new ArrayList<>();
        for (String nom : parametres.keySet()) {
            if (!EXCLUS_DE_LA_SIGNATURE.contains(nom.toLowerCase(Locale.ROOT))) noms.add(nom);
        }
        noms.sort(String.CASE_INSENSITIVE_ORDER);
        StringBuilder chaine = new StringBuilder();
        for (String nom : noms) {
            String v = parametres.get(nom);
            chaine.append(echapper(v == null ? "" : v.trim())).append('|');
        }
        chaine.append(echapper(cleMagasin.trim()));
        try {
            byte[] empreinte = MessageDigest.getInstance("SHA-512").digest(chaine.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(empreinte);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Verifie la signature recue ({@code HASH}, casse du nom ignoree), en temps constant. */
    public static boolean signatureValide(Map<String, String> recus, String cleMagasin) {
        String recue = null;
        for (Map.Entry<String, String> e : recus.entrySet()) {
            if ("hash".equalsIgnoreCase(e.getKey())) recue = e.getValue();
        }
        if (recue == null || recue.isBlank()) return false;
        String attendue = signer(recus, cleMagasin);
        return MessageDigest.isEqual(attendue.getBytes(StandardCharsets.US_ASCII),
                recue.trim().getBytes(StandardCharsets.US_ASCII));
    }

    private static String echapper(String v) {
        return v.replace("\\", "\\\\").replace("|", "\\|");
    }
}
