package ma.jurika.billing.application.editeur;

import ma.jurika.billing.infrastructure.persistence.BillingRepositories.EditeurParametresJpaRepository;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lot L7 (E6) : parametres de l'editeur (identite de Maghreb Consulting, taux de TVA), saisis
 * une fois par le super-admin. Aucune valeur n'est inventee : un champ vide reste vide, et la
 * vue dit ce qui manque et ou c'est utilise (factures, contrat d'abonnement).
 *
 * <p>Trace : chaque modification est tracee champ par champ par le declencheur de billing V14
 * (auteur = {@code modifie_par}).
 */
@Service
public class EditeurParametresService {

    /** Ou une mention est imprimee. */
    public enum Usage { FACTURES, CONTRAT }

    /** Definition d'une mention : cle d'API, libelle affiche, usages, lecture sur l'entite. */
    public record Mention(String champ, String libelle, List<Usage> usages,
                          Function<EditeurParametresEntity, String> lecture) {}

    public static final List<Mention> MENTIONS = List.of(
            new Mention("raisonSociale", "Raison sociale", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getRaisonSociale),
            new Mention("formeJuridique", "Forme juridique", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getFormeJuridique),
            new Mention("capitalSocial", "Capital social (en dirhams)", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getCapitalSocial),
            new Mention("siegeSocial", "Adresse du si\u00e8ge", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getSiegeSocial),
            new Mention("ville", "Ville du si\u00e8ge", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getVille),
            new Mention("rcNumero", "Num\u00e9ro du registre du commerce", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getRcNumero),
            new Mention("rcTribunal", "Ville du registre du commerce", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getRcTribunal),
            new Mention("ice", "ICE", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getIce),
            new Mention("identifiantFiscal", "Identifiant fiscal (IF)", List.of(Usage.FACTURES, Usage.CONTRAT),
                    EditeurParametresEntity::getIdentifiantFiscal),
            new Mention("taxeProfessionnelle", "Taxe professionnelle (TP)", List.of(Usage.FACTURES),
                    EditeurParametresEntity::getTaxeProfessionnelle),
            new Mention("representantNom", "Nom du repr\u00e9sentant l\u00e9gal", List.of(Usage.CONTRAT),
                    EditeurParametresEntity::getRepresentantNom),
            new Mention("representantQualite", "Qualit\u00e9 du repr\u00e9sentant l\u00e9gal", List.of(Usage.CONTRAT),
                    EditeurParametresEntity::getRepresentantQualite));

    /** Libelles des colonnes tracees par V14 (nom SQL -> libelle). */
    private static final Map<String, String> LIBELLES_COLONNES = Map.ofEntries(
            Map.entry("raison_sociale", "Raison sociale"),
            Map.entry("forme_juridique", "Forme juridique"),
            Map.entry("capital_social", "Capital social (en dirhams)"),
            Map.entry("siege_social", "Adresse du si\u00e8ge"),
            Map.entry("ville", "Ville du si\u00e8ge"),
            Map.entry("rc_numero", "Num\u00e9ro du registre du commerce"),
            Map.entry("rc_tribunal", "Ville du registre du commerce"),
            Map.entry("ice", "ICE"),
            Map.entry("identifiant_fiscal", "Identifiant fiscal (IF)"),
            Map.entry("taxe_professionnelle", "Taxe professionnelle (TP)"),
            Map.entry("representant_nom", "Nom du repr\u00e9sentant l\u00e9gal"),
            Map.entry("representant_qualite", "Qualit\u00e9 du repr\u00e9sentant l\u00e9gal"),
            Map.entry("taux_tva_pourcent", "Taux de TVA (%)"));

    public record Commande(String raisonSociale, String formeJuridique, String capitalSocial,
                           String siegeSocial, String ville, String rcNumero, String rcTribunal,
                           String ice, String identifiantFiscal, String taxeProfessionnelle,
                           String representantNom, String representantQualite,
                           BigDecimal tauxTvaPourcent) {}

    public record Manquant(String champ, String libelle, List<Usage> usages) {}

    public record Vue(Map<String, String> valeurs, BigDecimal tauxTvaPourcent,
                      List<Manquant> manquants, Instant modifieLe, UUID modifiePar) {}

    public record Trace(String champ, String libelle, String ancienneValeur, String nouvelleValeur,
                        UUID modifiePar, Instant modifieLe) {}

    private final EditeurParametresJpaRepository repo;
    private final JdbcTemplate jdbc;
    public EditeurParametresService(EditeurParametresJpaRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Vue lire() {
        return vue(ligne());
    }

    /** Identite courante (lecture seule) pour les documents (contrat, factures). */
    @Transactional(readOnly = true)
    public EditeurParametresEntity identite() {
        return ligne();
    }

    @Transactional
    public Vue modifier(Commande c, UUID auteur) {
        if (auteur == null) {
            throw new IllegalArgumentException("Auteur de la modification absent");
        }
        EditeurParametresEntity e = ligne();
        e.setRaisonSociale(propre(c.raisonSociale()));
        e.setFormeJuridique(propre(c.formeJuridique()));
        e.setCapitalSocial(propre(c.capitalSocial()));
        e.setSiegeSocial(propre(c.siegeSocial()));
        e.setVille(propre(c.ville()));
        e.setRcNumero(propre(c.rcNumero()));
        e.setRcTribunal(propre(c.rcTribunal()));
        e.setIce(propre(c.ice()));
        e.setIdentifiantFiscal(propre(c.identifiantFiscal()));
        e.setTaxeProfessionnelle(propre(c.taxeProfessionnelle()));
        e.setRepresentantNom(propre(c.representantNom()));
        e.setRepresentantQualite(propre(c.representantQualite()));
        if (c.tauxTvaPourcent() == null) {
            throw new EditeurException("TAUX_TVA_ABSENT", "Le taux de TVA est obligatoire (il est imprim\u00e9 sur chaque facture).");
        }
        if (c.tauxTvaPourcent().stripTrailingZeros().scale() > 2) {
            throw new EditeurException("TAUX_TVA_INVALIDE", "Le taux de TVA s\u2019\u00e9crit avec deux d\u00e9cimales au plus.");
        }
        BigDecimal taux = c.tauxTvaPourcent().setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (taux.signum() < 0 || taux.compareTo(new BigDecimal("30")) > 0) {
            throw new EditeurException("TAUX_TVA_INVALIDE", "Le taux de TVA doit \u00eatre compris entre 0 et 30 %.");
        }
        e.setTauxTvaPourcent(taux);
        if (e.getIce() != null && !e.getIce().matches("\\d{15}")) {
            throw new EditeurException("ICE_INVALIDE", "L\u2019ICE compte 15 chiffres.");
        }
        e.setModifiePar(auteur);
        e.setModifieLe(Instant.now());
        return vue(repo.saveAndFlush(e));
    }

    @Transactional(readOnly = true)
    public List<Trace> historique() {
        return jdbc.query("SELECT champ, ancienne_valeur, nouvelle_valeur, modifie_par, modifie_le "
                        + "FROM editeur_parametres_historique ORDER BY modifie_le DESC, id DESC LIMIT 500",
                (rs, i) -> new Trace(rs.getString(1),
                        LIBELLES_COLONNES.getOrDefault(rs.getString(1), rs.getString(1)),
                        rs.getString(2), rs.getString(3),
                        rs.getObject(4, UUID.class),
                        rs.getObject(5, OffsetDateTime.class).toInstant()));
    }

    private EditeurParametresEntity ligne() {
        return repo.unique().orElseThrow(() -> new IllegalStateException(
                "Ligne editeur_parametres (id = 1) absente : migration billing V6 non appliquee"));
    }

    private static Vue vue(EditeurParametresEntity e) {
        Map<String, String> valeurs = new java.util.LinkedHashMap<>();
        List<Manquant> manquants = new ArrayList<>();
        for (Mention m : MENTIONS) {
            String v = m.lecture().apply(e);
            valeurs.put(m.champ(), v);
            if (v == null || v.isBlank()) {
                manquants.add(new Manquant(m.champ(), m.libelle(), m.usages()));
            }
        }
        return new Vue(valeurs, e.getTauxTvaPourcent(), manquants, e.getModifieLe(), e.getModifiePar());
    }

    private static String propre(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /** Refus nomme (422). */
    public static class EditeurException extends RuntimeException {
        private final String code;
        public EditeurException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String code() { return code; }
    }
}
