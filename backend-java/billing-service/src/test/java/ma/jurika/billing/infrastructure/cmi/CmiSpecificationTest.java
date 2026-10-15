package ma.jurika.billing.infrastructure.cmi;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E5), RG-PAY-04 et RG-PAY-05 : signature "ver3" du CMI (voir l'en-tete de
 * {@link CmiSpecification} pour la source et les points A_DECIDER).
 */
class CmiSpecificationTest {

    static final String CLE = "cle-de-test-du-magasin";

    /** Calcul attendu, reecrit independamment de l'implementation : valeurs triees, echappees, cle a la fin. */
    @Test
    void signature_selon_la_methode_ver3() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("oid", "JUR1");
        p.put("amount", "450.00");
        p.put("BillToName", "Cabinet A|B");
        p.put("clientid", "600000000");
        p.put("encoding", "UTF-8");
        String chaine = "450.00|Cabinet A\\|B|600000000|JUR1|" + CLE;
        String attendue = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-512").digest(chaine.getBytes(StandardCharsets.UTF_8)));

        assertThat(CmiSpecification.signer(p, CLE)).isEqualTo(attendue);
    }

    @Test
    void hash_et_encoding_exclus_quelle_que_soit_la_casse() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("oid", "JUR1");
        String sans = CmiSpecification.signer(p, CLE);
        p.put("HASH", "n'importe quoi");
        p.put("Encoding", "UTF-8");
        assertThat(CmiSpecification.signer(p, CLE)).isEqualTo(sans);
    }

    @Test
    void signature_verifiee_et_alteration_detectee() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("oid", "JUR1");
        p.put("amount", "450.00");
        p.put("ProcReturnCode", "00");
        p.put("HASH", CmiSpecification.signer(p, CLE));
        assertThat(CmiSpecification.signatureValide(p, CLE)).isTrue();

        p.put("amount", "1.00");
        assertThat(CmiSpecification.signatureValide(p, CLE)).isFalse();
        p.put("amount", "450.00");
        assertThat(CmiSpecification.signatureValide(p, "autre-cle")).isFalse();
        p.remove("HASH");
        assertThat(CmiSpecification.signatureValide(p, CLE)).isFalse();
    }

    @Test
    void montant_au_format_du_cmi() {
        assertThat(CmiSpecification.montant(45000)).isEqualTo("450.00");
        assertThat(CmiSpecification.montant(5)).isEqualTo("0.05");
        assertThat(CmiSpecification.centimes("450.00")).isEqualTo(45000);
        assertThat(CmiSpecification.centimes("450.5")).isEqualTo(45050);
        assertThatThrownBy(() -> CmiSpecification.centimes("450,00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cle_absente_refusee() {
        assertThatThrownBy(() -> CmiSpecification.signer(Map.of("oid", "1"), "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retour_du_simulateur_signe_comme_le_cmi() {
        Map<String, String> commande = new LinkedHashMap<>();
        commande.put("oid", "JUR9");
        commande.put("amount", "90.00");
        commande.put("HASH", "ancienne");
        Map<String, String> retour = CmiSimulateurController.retourServeur(commande, true, CLE);
        assertThat(retour.get("ProcReturnCode")).isEqualTo("00");
        assertThat(retour.get("HASH")).isNotEqualTo("ancienne");
        assertThat(CmiSpecification.signatureValide(retour, CLE)).isTrue();
        assertThat(CmiSimulateurController.retourServeur(commande, false, CLE).get("ProcReturnCode")).isNotEqualTo("00");
    }
}
