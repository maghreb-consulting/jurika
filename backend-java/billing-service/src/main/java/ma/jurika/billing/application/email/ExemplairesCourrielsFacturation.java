package ma.jurika.billing.application.email;

import ma.jurika.billing.application.facture.FacturePdf;
import ma.jurika.billing.application.facture.FactureService;
import ma.jurika.common.courriel.CourrielJurika;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recette R3 : exemplaire du courriel de facture, rendu avec des donnees FICTIVES explicites
 * (emetteur et cabinet "(fictif)", numero F-EXEMPLE-0001, liens inoperants sur le domaine reserve
 * exemple.invalid) et son PDF de facture fictif en piece jointe : la facture est le seul courriel
 * qui porte une piece jointe, c'est attendu. L'objet commence par "[Exemplaire fictif]".
 * Sert la route de test {@code POST /api/v1/test/courriels/facturation} (hors production).
 */
public final class ExemplairesCourrielsFacturation {

    public record Exemplaire(String code, String objet, String gabarit, Map<String, Object> variables,
                             List<CourrielJurika.PieceJointe> pieces) {}

    static final String SITE = "https://exemple.invalid";

    private ExemplairesCourrielsFacturation() {}

    public static List<Exemplaire> tous() {
        return List.of(facture());
    }

    static Exemplaire facture() {
        String numero = "F-EXEMPLE-0001";
        LocalDate emission = LocalDate.of(2026, 10, 1);
        long ht = 10_000L; // montant fictif (100,00 DH), sans rapport avec un forfait
        long tva = 2_000L;
        long ttc = ht + tva;
        Map<String, Object> v = new HashMap<>();
        v.put("contactName", "Cabinet Exemple (fictif)");
        v.put("invoiceNumber", numero);
        v.put("periodStart", "01/10/2026");
        v.put("periodEnd", "31/10/2026");
        v.put("amountHt", FacturePdf.dirhams(ht));
        v.put("amountTva", FacturePdf.dirhams(tva));
        v.put("amountTtc", FacturePdf.dirhams(ttc));
        v.put("invoicePdfUrl", SITE + "/parametres/abonnement");
        v.put("billingUrl", SITE + "/parametres/abonnement");
        byte[] pdf = new FacturePdf().rendre(new FacturePdf.Donnees(numero, emission,
                new FactureService.Emetteur("\u00c9diteur Exemple (fictif)", "SARL", "100 000 DH",
                        "1, rue de l'Exemple", "Casablanca", "000000", "Tribunal de commerce de Casablanca",
                        "000000000000000", "00000000", "00000000"),
                new FactureService.Client("Cabinet Exemple (fictif)", "000000000000000", null, null,
                        "2, avenue de l'Exemple", "Rabat"),
                "Abonnement JURIKA (exemple fictif) du 01/10/2026 au 31/10/2026",
                emission, emission.plusMonths(1).minusDays(1), "Carte bancaire (exemple)", ht,
                new BigDecimal("20"), tva, ttc, "EXEMPLE-FICTIF"));
        return new Exemplaire("facture", "[Exemplaire fictif] Votre facture JURIKA est disponible",
                BillingEmailService.GABARIT_FACTURE, v,
                List.of(new CourrielJurika.PieceJointe("Facture-" + numero + ".pdf", pdf, "application/pdf")));
    }
}
