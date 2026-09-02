package ma.jurika.ai.workflow.generator;

import ma.jurika.ai.workflow.generator.ResolutionTextGenerator.ResolutionInput;
import ma.jurika.ai.workflow.generator.ResolutionTextGenerator.ResolutionOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre les 18 types juridiques structurés + le fallback "annexe à fournir"
 * et le rejet d'un type inconnu. Identifiants alignés sur le front
 * {@code MODIFICATIONS} et {@code ModificationWorkflow.TYPES}.
 */
class ResolutionTextGeneratorTest {

    private final ResolutionTextGenerator generator = new ResolutionTextGenerator();

    // ── Identité société ─────────────────────────────────────────────────

    @Test
    @DisplayName("CHANGEMENT_DENOMINATION : ancienne (depuis société) + nouvelle + article 2")
    void changement_denomination() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "CHANGEMENT_DENOMINATION",
                Map.of(
                        "ancienneDenomination", "ATLAS CONSEIL SARL",
                        "nouvelleDenomination", "ATLAS PARTNERS SARL"
                )
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Changement de dénomination sociale");
        assertThat(out.resolutionTexte())
                .contains("\"ATLAS CONSEIL SARL\"")
                .contains("\"ATLAS PARTNERS SARL\"")
                .contains("article 2")
                .contains("DÉNOMINATION SOCIALE");
    }

    @Test
    @DisplayName("CHANGEMENT_OBJET : nouvel objet + article 3")
    void changement_objet() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "CHANGEMENT_OBJET",
                Map.of("nouvelObjet", "Conseil juridique et fiscal aux entreprises")
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Changement de l'objet social");
        assertThat(out.resolutionTexte())
                .contains("Conseil juridique et fiscal aux entreprises")
                .contains("article 3")
                .contains("OBJET SOCIAL");
    }

    @Test
    @DisplayName("TRANSFERT_SIEGE : nouvelle adresse + ville + date d'effet + article 4")
    void transfert_siege() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "TRANSFERT_SIEGE",
                Map.of(
                        "nouvelleAdresse", "Twin Center, Tour Ouest, 8ème étage",
                        "nouvelleVille", "Casablanca",
                        "dateEffet", "2026-08-01"
                )
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Transfert du siège social");
        assertThat(out.resolutionTexte())
                .contains("Twin Center, Tour Ouest, 8ème étage")
                .contains("Casablanca")
                .contains("2026-08-01")
                .contains("article 4")
                .contains("SIÈGE SOCIAL");
    }

    @Test
    @DisplayName("PROROGATION_DUREE : durée en chiffres + lettres + date d'effet + article 5")
    void prorogation_duree() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "PROROGATION_DUREE",
                Map.of("annees", 99L, "dateEffet", "2026-12-31")
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Prorogation de la durée de la société");
        assertThat(out.resolutionTexte())
                .contains("99")
                .contains("QUATRE-VINGT-DIX-NEUF")
                .contains("2026-12-31")
                .contains("article 5")
                .contains("DURÉE");
    }

    // ── Capital ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("AUGMENTATION_CAPITAL : chiffres + lettres + diff + nouvelles parts + articles 6/7")
    void augmentation_capital() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "AUGMENTATION_CAPITAL",
                Map.of(
                        "ancienCapital", 100_000L,
                        "nouveauCapital", 500_000L,
                        "nouvellesParts", 4000L,
                        "dateVersement", "2026-07-05"
                )
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Augmentation de capital social");
        assertThat(out.resolutionTexte())
                .contains("100 000 dirhams")
                .contains("CENT MILLE DIRHAMS")
                .contains("500 000 dirhams")
                .contains("CINQ CENT MILLE DIRHAMS")
                .contains("400 000 dirhams")
                .contains("4000 nouvelles parts sociales")
                .contains("2026-07-05")
                .contains("articles 6")
                .contains("CAPITAL SOCIAL")
                .contains("PARTS SOCIALES");
    }

    @Test
    @DisplayName("AUGMENTATION_CAPITAL : refuse si nouveau <= ancien")
    void augmentation_capital_refuse_diff_negative() {
        ResolutionInput input = new ResolutionInput(
                "AUGMENTATION_CAPITAL",
                Map.of(
                        "ancienCapital", 100_000L,
                        "nouveauCapital", 100_000L,
                        "nouvellesParts", 1L
                )
        );
        assertThatThrownBy(() -> generator.generate(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nouveauCapital");
    }

    @Test
    @DisplayName("AUGMENTATION_CAPITAL_RESERVES : montant + ancien capital + articles 6/7")
    void augmentation_capital_reserves() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "AUGMENTATION_CAPITAL_RESERVES",
                Map.of(
                        "ancienCapital", 100_000L,
                        "montantIncorporation", 150_000L
                )
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Augmentation de capital par incorporation de réserves");
        assertThat(out.resolutionTexte())
                .contains("incorporation de réserves")
                .contains("150 000 dirhams")
                .contains("100 000")
                .contains("250 000")
                .contains("CAPITAL SOCIAL");
    }

    @Test
    @DisplayName("REDUCTION_CAPITAL : montant + motif + articles 6/7")
    void reduction_capital() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "REDUCTION_CAPITAL",
                Map.of(
                        "ancienCapital", 500_000L,
                        "montantReduction", 100_000L,
                        "motif", "Apurement des pertes antérieures"
                )
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Réduction de capital social");
        assertThat(out.resolutionTexte())
                .contains("réduire le capital social")
                .contains("100 000 dirhams")
                .contains("500 000")
                .contains("400 000")
                .contains("Apurement des pertes antérieures")
                .contains("CAPITAL SOCIAL");
    }

    @Test
    @DisplayName("MODIF_VALEUR_NOMINALE : nouvelle valeur + article 7")
    void modif_valeur_nominale() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "MODIF_VALEUR_NOMINALE",
                Map.of("nouvelleValeurNominale", 50L)
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Modification de la valeur nominale des parts sociales");
        assertThat(out.resolutionTexte())
                .contains("valeur nominale")
                .contains("50 dirhams")
                .contains("CINQUANTE DIRHAMS")
                .contains("article 7");
    }

    // ── Gérance ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DESIGNATION_GERANT : prénom + nom + CIN + nationalité + date effet + article 12")
    void designation_gerant() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "DESIGNATION_GERANT",
                Map.of(
                        "nom", "EL FASSI",
                        "prenom", "Salma",
                        "cin", "BE123456",
                        "dateEffet", "2026-09-01",
                        "nationalite", "marocaine"
                )
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Nomination d'un gérant");
        assertThat(out.resolutionTexte())
                .contains("Salma EL FASSI")
                .contains("BE123456")
                .contains("marocaine")
                .contains("2026-09-01")
                .contains("accepte expressément")
                .contains("incompatibilité")
                .contains("GÉRANCE");
    }

    @Test
    @DisplayName("REVOCATION_GERANT : identité + date effet + motif + article 12")
    void revocation_gerant() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "REVOCATION_GERANT",
                Map.of(
                        "identite", "Monsieur Karim BENNANI",
                        "dateEffet", "2026-09-01",
                        "motif", "Perte de confiance des associés"
                )
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Révocation d'un gérant");
        assertThat(out.resolutionTexte())
                .contains("révoquer")
                .contains("Monsieur Karim BENNANI")
                .contains("2026-09-01")
                .contains("Perte de confiance des associés")
                .contains("GÉRANCE");
    }

    @Test
    @DisplayName("MODIF_NOMBRE_GERANTS : details + article 12")
    void modif_nombre_gerants() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "MODIF_NOMBRE_GERANTS",
                Map.of("details", "Passage à deux cogérants pour une durée de 3 ans")
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Modification du nombre / de la durée des fonctions des gérants");
        assertThat(out.resolutionTexte())
                .contains("nombre et à la durée")
                .contains("Passage à deux cogérants")
                .contains("GÉRANCE");
    }

    @Test
    @DisplayName("MODIF_POUVOIRS_GERANT : details + article 12")
    void modif_pouvoirs_gerant() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "MODIF_POUVOIRS_GERANT",
                Map.of("details", "Plafond d'engagement bancaire porté à 1 000 000 MAD")
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Modification des pouvoirs ou de la rémunération du gérant");
        assertThat(out.resolutionTexte())
                .contains("pouvoirs et/ou la rémunération")
                .contains("Plafond d'engagement bancaire")
                .contains("GÉRANCE");
    }

    // ── Fonctionnement ───────────────────────────────────────────────────

    @Test
    @DisplayName("CLAUSE_AGREMENT : details + article 10")
    void clause_agrement() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "CLAUSE_AGREMENT",
                Map.of("details", "Agrément à la majorité des 3/4 du capital pour toute cession à un tiers")
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Clause d'agrément");
        assertThat(out.resolutionTexte())
                .contains("clause d'agrément")
                .contains("majorité des 3/4")
                .contains("article 10");
    }

    @Test
    @DisplayName("CLAUSE_PREEMPTION : details + droit de préemption")
    void clause_preemption() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "CLAUSE_PREEMPTION",
                Map.of("details", "Délai d'exercice du droit de préemption fixé à 30 jours")
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Droit de préemption / clause d'inaliénabilité");
        assertThat(out.resolutionTexte())
                .contains("droit de préemption")
                .contains("30 jours");
    }

    @Test
    @DisplayName("MODALITES_DECISIONS : details + modalités collectives")
    void modalites_decisions() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "MODALITES_DECISIONS",
                Map.of("details", "Décisions ordinaires à la majorité simple, extraordinaires aux 3/4")
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Modification des modalités de prise de décisions");
        assertThat(out.resolutionTexte())
                .contains("décisions collectives")
                .contains("majorité simple");
    }

    // ── Décisions structurantes ─────────────────────────────────────────

    @Test
    @DisplayName("CONTINUATION_PERTES : référence Loi 5-96 article 86")
    void continuation_pertes() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "CONTINUATION_PERTES",
                Map.of("details", "Plan de redressement présenté par la gérance")
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Continuation de l'activité malgré les pertes");
        assertThat(out.resolutionTexte())
                .contains("article 86")
                .contains("loi 5-96")
                .contains("continuation")
                .contains("Plan de redressement");
    }

    @Test
    @DisplayName("CREATION_SUCCURSALE : details + article 17")
    void creation_succursale() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "CREATION_SUCCURSALE",
                Map.of("details", "Ouverture d'une succursale à Rabat, avenue Mohammed V")
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Création / transfert / suppression de succursale");
        assertThat(out.resolutionTexte())
                .contains("succursale")
                .contains("Rabat")
                .contains("article 17");
    }

    @Test
    @DisplayName("POUVOIRS_FORMALITES : pouvoirs registre du commerce, fallback mandataire si absent")
    void pouvoirs_formalites_default_mandataire() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "POUVOIRS_FORMALITES",
                Map.of()
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Pouvoirs pour formalités");
        assertThat(out.resolutionTexte())
                .contains("tous pouvoirs")
                .contains("porteur d'un original")
                .contains("registre du commerce");
    }

    // ── Fallback "annexe à fournir" ──────────────────────────────────────

    @Test
    @DisplayName("CESSION_PARTIELLE : fallback 'annexe à fournir' avec ODJ explicite")
    void fallback_cession_partielle() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "CESSION_PARTIELLE",
                Map.of("details", "Cession de 200 parts à un tiers entrant")
        ));
        assertThat(out.ordreDuJourPoint()).isEqualTo("Cession partielle de parts sociales");
        assertThat(out.resolutionTexte())
                .contains("annexe à fournir")
                .contains("Cession de 200 parts");
    }

    @Test
    @DisplayName("AUGMENTATION_CAPITAL_NATURE : fallback 'annexe à fournir' sans details")
    void fallback_apport_nature_sans_details() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "AUGMENTATION_CAPITAL_NATURE",
                Map.of()
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Augmentation de capital par apport en nature");
        assertThat(out.resolutionTexte())
                .contains("annexe à fournir");
    }

    @Test
    @DisplayName("FUSION_SCISSION : fallback 'annexe à fournir'")
    void fallback_fusion_scission() {
        ResolutionOutput out = generator.generate(new ResolutionInput(
                "FUSION_SCISSION",
                Map.of()
        ));
        assertThat(out.ordreDuJourPoint())
                .isEqualTo("Fusion / scission / apport partiel d'actif");
        assertThat(out.resolutionTexte()).contains("annexe à fournir");
    }

    // ── Rejet d'un type inconnu ──────────────────────────────────────────

    @Test
    @DisplayName("typeId inconnu → UnsupportedOperationException")
    void unsupported_type_throws() {
        assertThatThrownBy(() -> generator.generate(new ResolutionInput("CHANGEMENT_INVENTE", Map.of())))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("CHANGEMENT_INVENTE");
    }
}
