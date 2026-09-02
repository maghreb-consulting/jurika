package ma.jurika.ai.document.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grammaire d'assemblage des actes (2026-08-17) — <b>contrôle anti-assemblage fautif</b>.
 *
 * <p>Complète les assertions de NON-VACUITÉ. Celles-ci exigent que les variables
 * laissent une valeur derrière elles ; elles ne disent rien de la façon dont cette
 * valeur <b>se raccorde</b> au texte du modèle. D'où des rendus « pleins » mais fautifs,
 * en pleine en-tête d'acte :
 * <pre>
 *   « Fait à au siège social, le 20/09/2026 »
 *   « PROCÈS-VERBAL DES DÉCISIONS DE le gérant unique »
 *   « GmbH DE DROIT Allemagne »
 * </pre>
 *
 * <p>Le détecteur lui-même vit dans {@link AssemblageFautif} (classe publique partagée
 * par tous les tests de rendu). Ce fichier vérifie qu'il MORD sur les quatre rendus
 * réellement observés, qu'il ne crie pas sur les formes correctes, et couvre les deux
 * utilitaires de grammaire ({@link FrenchContraction}, {@link NationaliteFrancaise}).
 */
class GrammaireAssemblageTest {

    // =====================================================================
    //  Le détecteur MORD sur les 4 cas réellement observés
    // =====================================================================

    @Nested
    @DisplayName("Le détecteur attrape les 4 rendus fautifs du 2026-08-16")
    class DetecteLesCasReels {

        @Test
        @DisplayName("Les 4 extraits d'échantillons sont bien signalés")
        void quatreCas() {
            List<String> fautifs = List.of(
                    "les associés se sont réunis en assemblée générale à au siège social,",
                    "Fait à au siège social, le 20/09/2026.",
                    "PROCÈS-VERBAL DES DÉCISIONS DE le gérant unique",
                    "GmbH DE DROIT Allemagne");
            for (String ligne : fautifs) {
                assertThat(AssemblageFautif.dans(ligne))
                        .describedAs("Le détecteur DOIT signaler : « %s »", ligne)
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("Aucun faux positif sur les rendus corrects attendus")
        void pasDeFauxPositifs() {
            assertThat(AssemblageFautif.dans(String.join("\n",
                    "les associés se sont réunis en assemblée générale au siège social,",
                    "Fait au siège social, le 20/09/2026.",
                    "Fait à Munich, le 12/09/2026.",
                    // 2026-08-17 — ATTENTE CORRIGÉE : cette ligne figurait ici comme
                    // « correcte » au lot précédent. Elle ne l'est plus : une valeur en
                    // minuscules au milieu d'un titre en majuscules est précisément le
                    // défaut de casse que ce lot corrige.
                    "PROCÈS-VERBAL DES DÉCISIONS DU GÉRANT UNIQUE",
                    "PROCÈS-VERBAL DES DÉCISIONS DE L'ASSOCIÉ UNIQUE",
                    "GmbH de droit allemand",
                    "société de droit étranger (Norvège)",
                    "le conseil d'administration de la société GLOBAL TRADING LTD",
                    "titulaire de la CIN n° BK987654,",
                    "au capital de 100 000 dirhams (CENT MILLE)")))
                    .isEmpty();
        }
    }

    // =====================================================================
    //  Contraction française — utilitaire réutilisable
    // =====================================================================

    @Nested
    @DisplayName("FrenchContraction : préposition du modèle + article de la valeur")
    class Contraction {

        /** Applique la fusion comme le fait le moteur, et renvoie la phrase complète. */
        private String assembler(String avant, String valeur) {
            StringBuilder sb = new StringBuilder(avant);
            FrenchContraction.Fusion f = FrenchContraction.fusionner(sb, valeur);
            if (f.caracteresARetirer() > 0) sb.setLength(sb.length() - f.caracteresARetirer());
            sb.append(f.valeur());
            return sb.toString();
        }

        @ParameterizedTest(name = "« {0} » + « {1} » → « {2} »")
        @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
                // Valeur DÉJÀ contractée : la préposition du modèle fait doublon.
                "Fait à |au siège social|Fait au siège social",
                "réunis à |aux bureaux|réunis aux bureaux",
                "décisions de |du gérant|décisions du gérant",
                // Contraction obligatoire.
                "DÉCISIONS DE |le gérant unique|DÉCISIONS DU gérant unique",
                "décisions de |le gérant unique|décisions du gérant unique",
                "PROCÈS-VERBAL DE |les associés|PROCÈS-VERBAL DES associés",
                "réunis à |le siège|réunis au siège",
                "réunis à |les bureaux|réunis aux bureaux",
                // Aucune contraction due : féminin, élision, nom propre, chiffre.
                "décisions de |la gérance|décisions de la gérance",
                "DÉCISIONS DE |l'associé unique|DÉCISIONS DE l'associé unique",
                "Fait à |Munich|Fait à Munich",
                "Fait à |12 RUE DES FOULES|Fait à 12 RUE DES FOULES",
                // Pas de préposition avant : on ne touche à rien.
                "Siège social : |le siège|Siège social : le siège",
                // « de droit » : le mot avant le placeholder est « droit », pas « de ».
                "société de droit |allemand|société de droit allemand",
        })
        void fusionne(String avant, String valeur, String attendu) {
            assertThat(assembler(avant, valeur)).isEqualTo(attendu);
        }

        @Test
        @DisplayName("Ne confond pas un suffixe « de » avec la préposition (« grande de »)")
        void pasDeFauxPositifSurSuffixe() {
            // « ...grande » se termine par « de » mais n'est pas la préposition.
            assertThat(assembler("une grande ", "le siège")).isEqualTo("une grande le siège");
        }

        @Test
        @DisplayName("Valeur vide ou absente : aucune modification du texte")
        void valeurVide() {
            assertThat(assembler("Fait à ", "")).isEqualTo("Fait à ");
            assertThat(assembler("Fait à ", null)).isEqualTo("Fait à null");
        }
    }

    // =====================================================================
    //  Nationalité — pays → adjectif
    // =====================================================================

    @Nested
    @DisplayName("NationaliteFrancaise : « de droit … » appelle un adjectif")
    class Nationalite {

        @ParameterizedTest(name = "{0} → de droit {1}")
        @CsvSource({
                "Allemagne,allemand",
                "Espagne,espagnol",
                "France,français",
                "Royaume-Uni,britannique",
                "Italie,italien",
                "Portugal,portugais",
                "Belgique,belge",
                "Pays-Bas,néerlandais",
                "Suisse,suisse",
                "États-Unis,américain",
                "Turquie,turc",
                "Chine,chinois",
                "Émirats arabes unis,émirati",
                "maroc,marocain",
        })
        void adjectifs(String pays, String attendu) {
            assertThat(NationaliteFrancaise.adjectifDeDroit(pays)).isEqualTo(attendu);
        }

        @Test
        @DisplayName("Pays inconnu : repli grammatical, JAMAIS « de droit <Pays> »")
        void paysInconnu() {
            String rendu = "société de droit " + NationaliteFrancaise.adjectifDeDroit("Norvégie-du-Nord");
            assertThat(rendu).isEqualTo("société de droit étranger (Norvégie-du-Nord)");
            // Et le détecteur ne doit PAS le signaler : le repli est correct.
            assertThat(AssemblageFautif.dans(rendu)).isEmpty();
        }

        @Test
        @DisplayName("Pays absent : « de droit étranger »")
        void paysAbsent() {
            assertThat(NationaliteFrancaise.adjectifDeDroit(null)).isEqualTo("étranger");
            assertThat(NationaliteFrancaise.adjectifDeDroit("  ")).isEqualTo("étranger");
        }
    }

    // =====================================================================
    //  Casse en position d'en-tete / titre (2026-08-17)
    // =====================================================================

    @Nested
    @DisplayName("CasseEnTete : une valeur injectée suit la casse de sa position")
    class Casse {

        @Test
        @DisplayName("Le détecteur signale les 2 rendus fautifs relevés dans les échantillons")
        void detecteLesDeuxCas() {
            for (String ligne : List.of(
                    "GmbH DE DROIT allemand",
                    "PROCÈS-VERBAL DES DÉCISIONS DU gérant unique")) {
                assertThat(AssemblageFautif.dans(ligne))
                        .describedAs("Le détecteur DOIT signaler : « %s »", ligne)
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("Aucun faux positif : corps de texte, sigles, noms propres étrangers")
        void pasDeFauxPositifs() {
            assertThat(AssemblageFautif.dans(String.join("\n",
                    // Rendus corrects attendus APRÈS correctif.
                    "GmbH DE DROIT ALLEMAND",
                    "PROCÈS-VERBAL DES DÉCISIONS DU GÉRANT UNIQUE",
                    "SIÈGE SOCIAL : Hauptstrasse 5, Munich",
                    "IMMATRICULÉE AU REGISTRE Handelsregister München SOUS LE N° HRB-112233",
                    "AU CAPITAL DE 250 000 EUR",
                    // CORPS de texte : les minuscules y sont correctes, même quand la
                    // phrase cite une raison sociale en capitales.
                    "Le 12/09/2026, à 09H00, le gérant unique de la société MEDITERRANEA GMBH,",
                    "GmbH de droit allemand au capital de 250 000 EUR, dont le siège social",
                    "les associés de la société PARACOSME, société à responsabilité limitée")))
                    .isEmpty();
        }

        @Test
        @DisplayName("En-tête : une valeur tout en minuscules passe en majuscules")
        void enTeteMajusculeLaValeur() {
            assertThat(CasseEnTete.estEnTete(" DE DROIT ")).isTrue();
            assertThat(CasseEnTete.harmoniser("allemand", true)).isEqualTo("ALLEMAND");
            assertThat(CasseEnTete.harmoniser("le gérant unique", true))
                    .isEqualTo("LE GÉRANT UNIQUE");
        }

        @Test
        @DisplayName("En-tête : sigles et noms propres déjà casés restent intacts")
        void enTetePreserveLesNomsPropres() {
            // Une majuscule déjà présente = casse voulue par quelqu'un. Sans cette garde,
            // « GmbH » deviendrait « GMBH » dans le MÊME paragraphe que « allemand ».
            assertThat(CasseEnTete.harmoniser("GmbH", true)).isEqualTo("GmbH");
            assertThat(CasseEnTete.harmoniser("Handelsregister München", true))
                    .isEqualTo("Handelsregister München");
            assertThat(CasseEnTete.harmoniser("MEDITERRANEA GMBH", true))
                    .isEqualTo("MEDITERRANEA GMBH");
            assertThat(CasseEnTete.harmoniser("250 000 EUR", true)).isEqualTo("250 000 EUR");
        }

        @Test
        @DisplayName("Corps de texte : la valeur n'est jamais touchée")
        void corpsDeTexteInchange() {
            assertThat(CasseEnTete.estEnTete(" de droit  au capital de ")).isFalse();
            assertThat(CasseEnTete.harmoniser("allemand", false)).isEqualTo("allemand");
            assertThat(CasseEnTete.harmoniser("le gérant unique", false))
                    .isEqualTo("le gérant unique");
        }

        @Test
        @DisplayName("Texte littéral trop court : on ne conclut pas (paragraphe = 1 placeholder)")
        void litteralTropCourtNeTranchePas() {
            // Un paragraphe réduit au seul placeholder n'a AUCUN texte littéral : rien ne
            // permet d'affirmer qu'il s'agit d'un titre.
            assertThat(CasseEnTete.estEnTete("")).isFalse();
            assertThat(CasseEnTete.estEnTete(" : ")).isFalse();
            assertThat(CasseEnTete.estEnTete("N° ")).isFalse();   // 1 lettre < 3
        }
    }
}
