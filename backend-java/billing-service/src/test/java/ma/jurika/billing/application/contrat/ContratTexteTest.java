package ma.jurika.billing.application.contrat;

import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E6) : texte du contrat (modele valide repris tel quel, variables, empreintes,
 * mentions manquantes nommees) et exemplaire PDF (RG-CTR-04). La premiere page de
 * l'exemplaire est rendue en PNG dans target/ pour la relecture a l'ecran.
 */
class ContratTexteTest {

    private static final String MODELE = ContratPlanification.modeleInitial();
    private static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");

    static ContratDepot.Conditions conditions(String rythme, String prix, String mode) {
        return new ContratDepot.Conditions(UUID.fromString("22222222-2222-2222-2222-222222222222"), WS, "CP-2026-000001",
                "PROFESSIONNEL", rythme, new BigDecimal(prix), LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 1), mode,
                "SUPER_ADMIN", "EN_ATTENTE", LocalDate.of(2027, 11, 1), null, Instant.EPOCH, null);
    }

    static IdentiteCabinetSource.Identite cabinet() {
        return new IdentiteCabinetSource.Identite("Cabinet Atlas Conseil", "12 boulevard d\u2019Anfa, Casablanca",
                "123456", "001234567000089", "contact@atlas.ma", "Bennani", "Salma", "salma.bennani@jurika.ma");
    }

    @Test
    void le_modele_initial_reprend_le_texte_valide_et_ses_quatre_pieces() {
        ContratTexte.Sections s = ContratTexte.sections(MODELE);
        assertThat(MODELE).startsWith("# Pr\u00e9sentation des pi\u00e8ces du contrat");
        assertThat(MODELE).contains("# A. Conditions particuli\u00e8res", "# B. Conditions g\u00e9n\u00e9rales d'abonnement",
                "# C. Accord de traitement des donn\u00e9es personnelles",
                "## Article 18 \u2014 Droit applicable et litiges",
                "1.1. Les pr\u00e9sentes conditions g\u00e9n\u00e9rales r\u00e9gissent l'acc\u00e8s \u00e0 la plateforme **JURIKA**");
        // L'essai (piece D) releve de RG-CTR-10 ; les points a arbitrer ne sont pas du contrat.
        assertThat(MODELE).doesNotContain("# D. Conditions", "Points \u00e0 arbitrer", "PROJET \u00c0 VALIDER");
        // Champs de l'editeur devenus variables ; aucun crochet d'identite ne reste en piece A.
        assertThat(MODELE).contains("**Maghreb Consulting**, $EDITEUR_FORME au capital de $EDITEUR_CAPITAL dirhams");
        assertThat(MODELE).doesNotContain("[forme sociale]", "[nom et qualit\u00e9]");
        assertThat(s.empreinteCg()).hasSize(64).isEqualTo(ContratTexte.sha256(s.conditionsGenerales()));
        assertThat(s.conditionsGenerales()).startsWith("# B.").doesNotContain("# C.");
        assertThat(s.accordTraitement()).startsWith("# C.");
    }

    @Test
    void un_modele_sans_piece_ou_avec_variable_inconnue_est_refuse_avec_les_defauts_nommes() {
        assertThatThrownBy(() -> ContratTexte.sections(MODELE.replace("# C. Accord", "# X. Accord")))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).donnees()).anyMatch(d -> d.contains("# C.")));
        assertThatThrownBy(() -> ContratTexte.sections(MODELE + "\n$PRIX_INVENTE\n"))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).donnees()).anyMatch(d -> d.contains("$PRIX_INVENTE")));
    }

    @Test
    void redaction_deterministe_et_mentions_manquantes_nommees_sans_rien_inventer() {
        EditeurParametresEntity vide = new EditeurParametresEntity();
        ContratRedaction.Signataire sig = new ContratRedaction.Signataire("Mme", "g\u00e9rante", null, null);
        ContratRedaction.Redaction r1 = ContratRedaction.rediger(MODELE, "2026-10-12", conditions("MENSUEL", "750", null),
                vide, cabinet(), sig);
        ContratRedaction.Redaction r2 = ContratRedaction.rediger(MODELE, "2026-10-12", conditions("MENSUEL", "750", null),
                vide, cabinet(), sig);
        assertThat(r1.texte()).isEqualTo(r2.texte());
        assertThat(r1.empreinte()).isEqualTo(r2.empreinte()).isEqualTo(ContratTexte.sha256(r1.texte()));
        assertThat(r1.texte()).doesNotContain("$");
        assertThat(r1.texte()).contains("| Forfait | Professionnel |", "| Prix | 750,00 dirhams HT (par mois), soit 9 000,00 dirhams HT sur l\u2019ann\u00e9e |"
                .replace("l\u2019ann\u00e9e", "l'ann\u00e9e"), "| Date de d\u00e9but | 1er novembre 2026 |",
                "Cabinet Atlas Conseil", "repr\u00e9sent\u00e9 par Mme Bennani Salma, en qualit\u00e9 de\ng\u00e9rante");
        // Editeur vide : chaque mention nommee, aucune valeur inventee.
        assertThat(r1.manquants()).contains("forme juridique de l\u2019\u00c9diteur", "ICE de l\u2019\u00c9diteur",
                "nom du repr\u00e9sentant de l\u2019\u00c9diteur", "forme juridique du Cabinet",
                "ville du registre du commerce du Cabinet", "mode de paiement");
        assertThat(r1.texte()).contains("[non renseign\u00e9 : ICE de l\u2019\u00c9diteur]");
        // Paragraphe d'acceptation : rempli seulement apres l'acceptation.
        assertThat(ContratTexte.paragrapheAcceptation(r1.texte())).contains(ContratTexte.A_COMPLETER);
        // Une donnee qui change change l'empreinte.
        ContratRedaction.Redaction r3 = ContratRedaction.rediger(MODELE, "2026-10-12", conditions("MENSUEL", "750", "VIREMENT"),
                vide, cabinet(), sig);
        assertThat(r3.empreinte()).isNotEqualTo(r1.empreinte());
        assertThat(r3.texte()).contains("| Mode de paiement choisi | virement bancaire |");
    }

    @Test
    void exemplaire_pdf_a_la_charte_avec_mention_de_l_acceptation() throws Exception {
        EditeurParametresEntity editeur = new EditeurParametresEntity();
        editeur.setFormeJuridique("SARL");
        editeur.setRepresentantNom("M. Exemple");
        ContratRedaction.Redaction r = ContratRedaction.rediger(MODELE, "2026-10-12", conditions("ANNUEL", "7500", "CARTE"),
                editeur, cabinet(), new ContratRedaction.Signataire("Mme", "g\u00e9rante", "SARL", "Casablanca"));
        ContratDepot.Acceptation a = new ContratDepot.Acceptation(UUID.randomUUID(), WS,
                conditions("ANNUEL", "7500", "CARTE").id(), UUID.randomUUID(), "2026-10-12", ContratService.OBJET_CP,
                r.texte(), r.empreinte(), "c".repeat(64), "d".repeat(64), UUID.randomUUID(), "salma.bennani@jurika.ma",
                "Mme", "Bennani", "Salma", "g\u00e9rante", "SUPERVISEUR", "Cabinet Atlas Conseil", "contact@atlas.ma",
                "SARL", "Casablanca", Instant.parse("2026-10-14T09:32:05Z"), "2026-10-14 10:32:05", "196.200.1.2", null);
        String texte = ContratRedaction.exemplaire(MODELE, a);
        assertThat(texte).doesNotContain(ContratTexte.A_COMPLETER)
                .contains("accept\u00e9 en ligne le 14 octobre 2026 \u00e0\n10:32:05 (heure de Casablanca)",
                        "(adresse IP 196.200.1.2)", r.empreinte());

        List<String> manquants = ContratService.manquantsDe(r.texte());
        byte[] pdf = ContratPdf.generer(texte, new ContratPdf.Entete("Cabinet Atlas Conseil", "CP-2026-000001",
                "2026-10-12", LocalDate.of(2026, 10, 14), manquants));
        byte[] encore = ContratPdf.generer(texte, new ContratPdf.Entete("Cabinet Atlas Conseil", "CP-2026-000001",
                "2026-10-12", LocalDate.of(2026, 10, 14), manquants));
        assertThat(pdf).as("memes donnees, memes octets").isEqualTo(encore);

        Path dossier = Path.of("target", "contrat");
        Files.createDirectories(dossier);
        Files.write(dossier.resolve("exemplaire.pdf"), pdf);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(3);
            // Le lecteur de texte rend l'apostrophe typographique en apostrophe droite.
            String extrait = new PDFTextStripper().getText(doc).replace('\u2019', '\'');
            assertThat(extrait).contains("Contrat d'abonnement JURIKA", "Conditions particuli\u00e8res",
                    "Article 18", "Accord de traitement", "Acceptation \u00e9lectronique.", "Cabinet Atlas Conseil",
                    "non renseign\u00e9 : ICE de l'\u00c9diteur");
            assertThat(extrait).doesNotContain("**", "$EDITEUR");
            PDFRenderer rendu = new PDFRenderer(doc);
            for (int p = 0; p < 2; p++) {
                BufferedImage img = rendu.renderImageWithDPI(p, 80);
                ImageIO.write(img, "png", dossier.resolve("exemplaire-page-" + (p + 1) + ".png").toFile());
            }
        }
    }
}
