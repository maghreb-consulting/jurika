package ma.jurika.billing.application.facture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.application.cabinet.CabinetsPort.IdentiteCabinet;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.EditeurParametresJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.FactureJpaRepository;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import ma.jurika.billing.infrastructure.persistence.FactureEntity;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lot L7 (E5), RG-PAY-03 : facture generee automatiquement a chaque paiement acquis.
 *
 * <ul>
 *   <li>Numerotation SANS RUPTURE : compteur par annee ({@code factures_sequence}, billing V8)
 *       incremente DANS la transaction qui cree la facture ; le verrou de ligne serialise les
 *       emissions concurrentes ; une transaction annulee annule son numero. Numero
 *       {@code F<annee>-<rang sur six chiffres>}.</li>
 *   <li>Mentions obligatoires (CGI marocain, art. 145, et mission du lot) : identite, ICE, IF, RC
 *       et TP de l'editeur (parametres de l'editeur), identite et ICE du cabinet, date, numero,
 *       designation, periode, HT, taux et montant de TVA, TTC. Une mention obligatoire absente :
 *       AUCUNE facture, et les mentions manquantes sont NOMMEES (l'editeur ou le cabinet les
 *       saisit, la facture est emise ensuite, numero pris a ce moment).</li>
 *   <li>Une facture par paiement (unicite de {@code payment_id}) : une confirmation recue deux fois
 *       ne produit qu'une facture (RG-PAY-05).</li>
 * </ul>
 * Doit etre appele dans une transaction.
 */
@Service
public class FactureService {

    private static final Logger log = LoggerFactory.getLogger(FactureService.class);

    private final FactureJpaRepository factures;
    private final EditeurParametresJpaRepository editeurs;
    private final CabinetsPort cabinets;
    private final FacturePdf pdf;
    private final ObjectMapper json;

    @PersistenceContext
    private EntityManager em;

    public FactureService(FactureJpaRepository factures, EditeurParametresJpaRepository editeurs,
                          CabinetsPort cabinets, FacturePdf pdf, ObjectMapper json) {
        this.factures = factures;
        this.editeurs = editeurs;
        this.cabinets = cabinets;
        this.pdf = pdf;
        this.json = json;
    }

    /** Identite de l'emetteur figee sur la facture. */
    public record Emetteur(String raisonSociale, String formeJuridique, String capitalSocial, String siegeSocial,
                           String ville, String rcNumero, String rcTribunal, String ice, String identifiantFiscal,
                           String taxeProfessionnelle) {}

    /** Identite du client (le cabinet) figee sur la facture. */
    public record Client(String denomination, String ice, String identifiantFiscal, String rc, String adresse,
                         String ville) {}

    /** Resultat : la facture, ou les mentions manquantes nommees. */
    public record Emission(Optional<FactureEntity> facture, List<String> manquants) {}

    /**
     * Emet la facture du paiement acquis, ou nomme ce qui manque. Idempotent : la facture deja
     * emise est rendue telle quelle.
     */
    public Emission emettre(PaymentEntity p, LocalDate dateEmission) {
        Optional<FactureEntity> existante = factures.findByPaymentId(p.getId());
        if (existante.isPresent()) return new Emission(existante, List.of());
        if (!"COMPLETED".equals(p.getStatus())) {
            throw new IllegalStateException("Facture d'un paiement non acquis : " + p.getId());
        }
        EditeurParametresEntity e = editeurs.unique()
                .orElseThrow(() -> new IllegalStateException("Parametres de l'editeur absents (billing V6)"));
        List<String> manquants = new ArrayList<>(mentionsManquantesEditeur(e));
        IdentiteCabinet cab;
        try {
            cab = cabinets.identite(p.getWorkspaceId());
        } catch (CabinetsPort.CabinetsIndisponiblesException ex) {
            manquants.add("Identit\u00e9 du cabinet momentan\u00e9ment indisponible (service des comptes injoignable)");
            return new Emission(Optional.empty(), manquants);
        }
        if (vide(cab.denomination())) manquants.add("D\u00e9nomination du cabinet");
        if (vide(cab.ice())) manquants.add("ICE du cabinet");
        if (p.getMontantHtCentimes() == null || p.getMontantTvaCentimes() == null || p.getTauxTva() == null) {
            manquants.add("Montants HT et TVA du paiement");
        }
        if (!manquants.isEmpty()) {
            return new Emission(Optional.empty(), manquants);
        }

        int annee = dateEmission.getYear();
        Number rang = (Number) em.createNativeQuery("""
                INSERT INTO factures_sequence (annee, dernier_numero) VALUES (?1, 1)
                ON CONFLICT (annee) DO UPDATE SET dernier_numero = factures_sequence.dernier_numero + 1
                RETURNING dernier_numero
                """).setParameter(1, annee).getSingleResult();
        String numero = String.format(Locale.ROOT, "F%d-%06d", annee, rang.intValue());

        Emetteur emetteur = new Emetteur(e.getRaisonSociale(), e.getFormeJuridique(), e.getCapitalSocial(),
                e.getSiegeSocial(), e.getVille(), e.getRcNumero(), e.getRcTribunal(), e.getIce(),
                e.getIdentifiantFiscal(), e.getTaxeProfessionnelle());
        Client client = new Client(cab.denomination(), cab.ice(), cab.identifiantFiscal(), cab.rc(),
                cab.adresse(), cab.ville());
        String designation = designation(p);
        long ttc = p.getMontantHtCentimes() + p.getMontantTvaCentimes();
        byte[] contenu = pdf.rendre(new FacturePdf.Donnees(numero, dateEmission, emetteur, client, designation,
                p.getPeriodeDebut(), p.getPeriodeFin(), libelleMode(p.getMethod()), p.getMontantHtCentimes(),
                p.getTauxTva(), p.getMontantTvaCentimes(), ttc, reference(p)));
        FactureEntity f = new FactureEntity(numero, annee, rang.intValue(), p.getWorkspaceId(), p.getId(), dateEmission,
                designation, p.getPeriodeDebut(), p.getPeriodeFin(), p.getMethod(), p.getMontantHtCentimes(),
                p.getTauxTva(), p.getMontantTvaCentimes(), ttc, versJson(emetteur), versJson(client), contenu);
        FactureEntity enregistree = factures.save(f);
        log.info("Facture {} emise workspace={} paiement={}", numero, p.getWorkspaceId(), p.getId());
        return new Emission(Optional.of(enregistree), List.of());
    }

    /** Mentions obligatoires de l'editeur absentes, nommees (RG-PAY-03). */
    public static List<String> mentionsManquantesEditeur(EditeurParametresEntity e) {
        List<String> m = new ArrayList<>();
        if (vide(e.getRaisonSociale())) m.add("Raison sociale de l\u2019\u00e9diteur");
        if (vide(e.getSiegeSocial())) m.add("Si\u00e8ge social de l\u2019\u00e9diteur");
        if (vide(e.getIce())) m.add("ICE de l\u2019\u00e9diteur");
        if (vide(e.getIdentifiantFiscal())) m.add("Identifiant fiscal (IF) de l\u2019\u00e9diteur");
        if (vide(e.getRcNumero())) m.add("Num\u00e9ro de registre du commerce (RC) de l\u2019\u00e9diteur");
        if (vide(e.getTaxeProfessionnelle())) m.add("Num\u00e9ro de taxe professionnelle (TP) de l\u2019\u00e9diteur");
        return m;
    }

    /** Designation de la prestation (francais affiche). */
    public static String designation(PaymentEntity p) {
        Plan plan = PlanCatalog.exiger(p.getPlanCode());
        String rythme = "yearly".equals(p.getBillingPeriod()) ? "paiement annuel" : "paiement mensuel";
        String periode = p.getPeriodeDebut() == null ? ""
                : " du " + date(p.getPeriodeDebut()) + " au " + date(p.getPeriodeFin().minusDays(1));
        return switch (p.getObjet() == null ? "SOUSCRIPTION" : p.getObjet()) {
            case "CHANGEMENT_FORFAIT" -> "Abonnement JURIKA : passage du forfait "
                    + PlanCatalog.findByCode(p.getPlanPrecedent()).map(Plan::displayLabel).orElse("pr\u00e9c\u00e9dent")
                    + " au forfait " + plan.displayLabel() + ", diff\u00e9rence au prorata des mois restants";
            default -> "Abonnement JURIKA, forfait " + plan.displayLabel() + " (" + rythme + ")" + periode;
        };
    }

    public static String libelleMode(String method) {
        return switch (method) {
            case "CARD" -> "Carte bancaire (CMI)";
            case "BANK_TRANSFER" -> "Virement bancaire";
            case "CHEQUE" -> "Ch\u00e8que";
            case "CASH" -> "Esp\u00e8ces";
            default -> method;
        };
    }

    private static String reference(PaymentEntity p) {
        if (p.getCmiTransaction() != null) return "Transaction CMI " + p.getCmiTransaction();
        if (p.getBankReference() != null && !p.getBankReference().isBlank()) return p.getBankReference();
        return null;
    }

    static String date(LocalDate d) {
        return String.format(Locale.FRANCE, "%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    private String versJson(Object o) {
        try {
            Map<String, Object> m = json.convertValue(o, LinkedHashMap.class);
            return json.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
