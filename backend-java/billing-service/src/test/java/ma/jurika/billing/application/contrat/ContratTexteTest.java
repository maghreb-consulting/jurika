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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E6, E9) : texte du contrat (version 1 validee reprise telle quelle, variables,
 * empreintes, mentions manquantes nommees) et exemplaire PDF (RG-CTR-04). Les deux premieres
 * pages de l'exemplaire sont rendues en PNG dans target/contrat pour la relecture a l'ecran.
 */
class ContratTexteTest {

    /**
     * Empreinte SHA-256 de ~/docs/CONTRAT_ABONNEMENT_JURIKA_V1.md (version 1 du 14 octobre 2026,
     * validee par la direction) : la ressource en est la copie octet pour octet.
     */
    static final String EMPREINTE_SOURCE_V1 = "ea69585b677d97e03b90274a26616ddbf7b2553f4b12465348dfbb289283e881";

    private static final String SOURCE = ContratPlanification.sourceInitiale();
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

    /** Parametres de l'editeur tous renseignes (valeurs d'essai, distinctes champ par champ). */
    static EditeurParametresEntity editeurComplet() {
        EditeurParametresEntity e = new EditeurParametresEntity();
        e.setRaisonSociale("Maghreb Consulting");
        e.setFormeJuridique("SARL");
        e.setCapitalSocial("100 000");
        e.setSiegeSocial("Tour Essai, boulevard d\u2019Essai");
        e.setVille("Rabat");
        e.setRcTribunal("Casablanca");
        e.setRcNumero("RC-ESSAI-1");
        e.setIce("000000000000099");
        e.setIdentifiantFiscal("IF-ESSAI-2");
        e.setTaxeProfessionnelle("TP-ESSAI-3");
        e.setRepresentantNom("M. Essai Representant");
        e.setRepresentantQualite("g\u00e9rant");
        return e;
    }

    @Test
    void la_ressource_est_la_copie_exacte_de_la_version_1_validee() {
        assertThat(ContratTexte.sha256(SOURCE)).isEqualTo(EMPREINTE_SOURCE_V1);
        assertThat(SOURCE.getBytes(StandardCharsets.UTF_8)).doesNotContain((byte) '\r');
    }

    @Test
    void le_modele_v1_est_le_document_valide_sans_la_ligne_de_publication_ni_la_piece_d() {
        List<String> lignes = SOURCE.lines().toList();
        // Ligne de publication : note d'edition ; "$MAJUSCULES" y designe la convention, pas une variable.
        assertThat(lignes.get(0)).startsWith("*Version 1 du 14 octobre 2026").contains("$MAJUSCULES").endsWith("*");
        assertThat(lignes.get(1)).isEmpty();
        int d = lignes.indexOf("# D. Conditions d'utilisation de l'essai gratuit");
        assertThat(d).isPositive();
        String attendu = String.join("\n", lignes.subList(2, d)).strip() + "\n";
        assertThat(MODELE).isEqualTo(attendu);

        ContratTexte.Sections s = ContratTexte.sections(MODELE);
        assertThat(MODELE).startsWith("# Pr\u00e9sentation des pi\u00e8ces du contrat");
        assertThat(MODELE).contains("# A. Conditions particuli\u00e8res", "# B. Conditions g\u00e9n\u00e9rales d'abonnement",
                "# C. Accord de traitement des donn\u00e9es personnelles",
                "## Article 18 \u2014 Droit applicable et litiges", "## Article 10 \u2014 Contr\u00f4le",
                "**$EDITEUR_DENOMINATION**, $EDITEUR_FORME au capital de $EDITEUR_CAPITAL dirhams",
                "repr\u00e9sent\u00e9e par $EDITEUR_REPRESENTANT_NOM,\nen qualit\u00e9 de $EDITEUR_REPRESENTANT_QUALITE");
        assertThat(MODELE).doesNotContain("# D. Conditions", "$MAJUSCULES", "*Version 1", "PROJET", "[");
        assertThat(s.empreinteCg()).hasSize(64).isEqualTo(ContratTexte.sha256(s.conditionsGenerales()));
        assertThat(s.conditionsGenerales()).startsWith("# B.").doesNotContain("# C.");
        assertThat(s.accordTraitement()).startsWith("# C.").endsWith("r\u00e9pond \u00e0 ses demandes raisonnables d'information.\n");
        // Le document complet colle tel quel donne le meme modele (publication par le super-admin).
        assertThat(ContratTexte.modeleDepuisSource(SOURCE.replace("\n", "\r\n"))).isEqualTo(MODELE);
    }

    @Test
    void un_modele_sans_piece_avec_variable_inconnue_ou_avec_crochets_est_refuse_avec_les_defauts_nommes() {
        assertThatThrownBy(() -> ContratTexte.sections(MODELE.replace("# C. Accord", "# X. Accord")))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).donnees()).anyMatch(d -> d.contains("# C.")));
        assertThatThrownBy(() -> ContratTexte.sections(MODELE + "\n$PRIX_INVENTE\n"))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).donnees()).anyMatch(d -> d.contains("$PRIX_INVENTE")));
        // Un projet (mentions entre crochets a completer) n'est jamais presente a un cabinet.
        assertThatThrownBy(() -> ContratTexte.sections(MODELE.replace("15 jours apr\u00e8s", "[15] jours apr\u00e8s")))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).donnees()).anyMatch(d -> d.contains("[15]")));
        // La ligne de publication, si elle restait dans un modele, serait refusee ($MAJUSCULES inconnue).
        assertThatThrownBy(() -> ContratTexte.sections(SOURCE))
                .isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).donnees())
                        .anyMatch(d -> d.contains("$MAJUSCULES"))
                        .anyMatch(d -> d.contains("pi\u00e8ce D")));
    }

    @Test
    void les_variables_de_l_editeur_viennent_des_parametres_de_l_editeur_colonne_par_colonne() {
        ContratRedaction.Redaction r = ContratRedaction.rediger(MODELE, "1", conditions("ANNUEL", "7500", "CARTE"),
                editeurComplet(), cabinet(), new ContratRedaction.Signataire("Mme", "g\u00e9rante", "SARL", "Casablanca"));
        assertThat(r.texte()).contains(
                "**Maghreb Consulting**, SARL au capital de 100 000 dirhams, dont le si\u00e8ge est \u00e0\n"
                        + "Tour Essai, boulevard d\u2019Essai, Rabat, immatricul\u00e9e au registre du commerce de Casablanca "
                        + "sous le n\u00b0 RC-ESSAI-1,\nICE 000000000000099, identifiant fiscal IF-ESSAI-2, repr\u00e9sent\u00e9e "
                        + "par M. Essai Representant,\nen qualit\u00e9 de g\u00e9rant, \u00e9ditrice de la plateforme JURIKA");
        assertThat(r.texte()).contains("| Version des conditions g\u00e9n\u00e9rales applicables | 1 |");
        // Aucune variable brute, aucun crochet hors du paragraphe d'acceptation (rempli a l'acceptation).
        assertThat(r.texte()).doesNotContain("$").doesNotContain("[non renseign\u00e9");
        assertThat(r.texte().replace(ContratTexte.A_COMPLETER, "")).doesNotContain("[");
        assertThat(r.manquants()).isEmpty();
    }

    @Test
    void parametre_de_l_editeur_manquant_mention_nommee_rien_d_invente() {
        EditeurParametresEntity e = editeurComplet();
        e.setRcTribunal(null);
        e.setRepresentantQualite(" ");
        ContratRedaction.Redaction r = ContratRedaction.rediger(MODELE, "1", conditions("ANNUEL", "7500", "CARTE"),
                e, cabinet(), new ContratRedaction.Signataire("Mme", "g\u00e9rante", "SARL", "Casablanca"));
        assertThat(r.manquants()).containsExactly("ville du registre du commerce de l\u2019\u00c9diteur",
                "qualit\u00e9 du repr\u00e9sentant de l\u2019\u00c9diteur");
        assertThat(r.texte()).contains("registre du commerce de [non renseign\u00e9 : ville du registre du commerce de "
                + "l\u2019\u00c9diteur] sous le n\u00b0 RC-ESSAI-1");
        // La ville du siege n'est jamais prise pour la ville du registre du commerce.
        assertThat(r.texte()).doesNotContain("registre du commerce de Rabat");
    }

    @Test
    void redaction_deterministe_et_mentions_manquantes_nommees_sans_rien_inventer() {
        EditeurParametresEntity vide = new EditeurParametresEntity();
        ContratRedaction.Signataire sig = new ContratRedaction.Signataire("Mme", "g\u00e9rante", null, null);
        ContratRedaction.Redaction r1 = ContratRedaction.rediger(MODELE, "1", conditions("MENSUEL", "750", null),
                vide, cabinet(), sig);
        ContratRedaction.Redaction r2 = ContratRedaction.rediger(MODELE, "1", conditions("MENSUEL", "750", null),
                vide, cabinet(), sig);
        assertThat(r1.texte()).isEqualTo(r2.texte());
        assertThat(r1.empreinte()).isEqualTo(r2.empreinte()).isEqualTo(ContratTexte.sha256(r1.texte()));
        assertThat(r1.texte()).doesNotContain("$");
        assertThat(r1.texte()).contains("| Forfait | Professionnel |", "| Prix | 750,00 dirhams HT (par mois), soit 9 000,00 dirhams HT sur l'ann\u00e9e |",
                "| Date de d\u00e9but | 1er novembre 2026 |",
                "Cabinet Atlas Conseil", "repr\u00e9sent\u00e9 par Mme Bennani Salma, en qualit\u00e9 de\ng\u00e9rante");
        // Editeur vide : chaque mention nommee, aucune valeur inventee.
        assertThat(r1.manquants()).contains("raison sociale de l\u2019\u00c9diteur", "forme juridique de l\u2019\u00c9diteur",
                "ICE de l\u2019\u00c9diteur", "nom du repr\u00e9sentant de l\u2019\u00c9diteur",
                "qualit\u00e9 du repr\u00e9sentant de l\u2019\u00c9diteur", "ville du si\u00e8ge de l\u2019\u00c9diteur",
                "forme juridique du Cabinet", "ville du registre du commerce du Cabinet", "mode de paiement");
        assertThat(r1.texte()).contains("[non renseign\u00e9 : ICE de l\u2019\u00c9diteur]");
        // Paragraphe d'acceptation : rempli seulement apres l'acceptation.
        assertThat(ContratTexte.paragrapheAcceptation(r1.texte())).contains(ContratTexte.A_COMPLETER);
        // Une donnee qui change change l'empreinte.
        ContratRedaction.Redaction r3 = ContratRedaction.rediger(MODELE, "1", conditions("MENSUEL", "750", "VIREMENT"),
                vide, cabinet(), sig);
        assertThat(r3.empreinte()).isNotEqualTo(r1.empreinte());
        assertThat(r3.texte()).contains("| Mode de paiement choisi | virement bancaire |");
    }

    @Test
    void exemplaire_pdf_a_la_charte_avec_mention_de_l_acceptation() throws Exception {
        ContratRedaction.Redaction r = ContratRedaction.rediger(MODELE, "1", conditions("ANNUEL", "7500", "CARTE"),
                editeurComplet(), cabinet(), new ContratRedaction.Signataire("Mme", "g\u00e9rante", "SARL", "Casablanca"));
        ContratDepot.Acceptation a = new ContratDepot.Acceptation(UUID.randomUUID(), WS,
                conditions("ANNUEL", "7500", "CARTE").id(), UUID.randomUUID(), "1", ContratService.OBJET_CP,
                r.texte(), r.empreinte(), "c".repeat(64), "d".repeat(64), UUID.randomUUID(), "salma.bennani@jurika.ma",
                "Mme", "Bennani", "Salma", "g\u00e9rante", "SUPERVISEUR", "Cabinet Atlas Conseil", "contact@atlas.ma",
                "SARL", "Casablanca", Instant.parse("2026-10-14T09:32:05Z"), "2026-10-14 10:32:05", "196.200.1.2", null);
        String texte = ContratRedaction.exemplaire(MODELE, a);
        assertThat(texte).doesNotContain(ContratTexte.A_COMPLETER).doesNotContain("[").doesNotContain("$")
                .contains("accept\u00e9 en ligne le 14 octobre 2026 \u00e0\n10:32:05 (heure de Casablanca)",
                        "(adresse IP 196.200.1.2)", r.empreinte());

        List<String> manquants = ContratService.manquantsDe(r.texte());
        assertThat(manquants).isEmpty();
        byte[] pdf = ContratPdf.generer(texte, new ContratPdf.Entete("Cabinet Atlas Conseil", "CP-2026-000001",
                "1", LocalDate.of(2026, 10, 14), manquants));
        byte[] encore = ContratPdf.generer(texte, new ContratPdf.Entete("Cabinet Atlas Conseil", "CP-2026-000001",
                "1", LocalDate.of(2026, 10, 14), manquants));
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
                    "Maghreb Consulting, SARL au capital de 100 000 dirhams", "RC-ESSAI-1", "M. Essai Representant");
            // La piece D (essai) n'est que citee dans la presentation : son texte n'est pas au contrat.
            assertThat(extrait).doesNotContain("**", "$", "[", "non renseign", "L'essai est gratuit, sans moyen de paiement");
            PDFRenderer rendu = new PDFRenderer(doc);
            for (int p = 0; p < 2; p++) {
                BufferedImage img = rendu.renderImageWithDPI(p, 80);
                ImageIO.write(img, "png", dossier.resolve("exemplaire-page-" + (p + 1) + ".png").toFile());
            }
        }
    }
}
