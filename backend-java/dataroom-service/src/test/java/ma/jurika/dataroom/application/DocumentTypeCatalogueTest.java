package ma.jurika.dataroom.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le catalogue des types de document est la SOURCE UNIQUE du menu de depot.
 *
 * <p>Lot 2 (2026-09-07) — motive par un defaut reel : la liste vivait en dur
 * dans le frontend et proposait 16 types quand la base en acceptait 44. Un
 * certificat negatif, un contrat de domiciliation ou un pouvoir depose a la
 * main tombait donc sous « AUTRE » : introuvable par type, et non reconnu comme
 * le justificatif attendu par la demarche correspondante.
 *
 * <p>Deplacer la liste cote serveur ne suffit pas : encore faut-il qu'elle
 * reste alignee sur ce que la base accepte. Ces tests comparent le catalogue a
 * la contrainte CHECK de la migration, dans les deux sens — un type accepte en
 * base mais absent du menu reste indeposeable, un type au menu mais refuse en
 * base fait echouer le depot au dernier moment, devant l'employe.
 */
class DocumentTypeCatalogueTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /**
     * Le CHECK sur {@code document_type} est REPOSE en entier par chaque migration
     * qui l'etend (V23, V24, V30...) : la regle du projet interdit d'editer une
     * migration deja appliquee, on la remplace donc par un sur-ensemble.
     *
     * <p>Lot 5 (2026-09-07) — ce test lisait V24 EN DUR. V30 a ajoute trois types,
     * et le test a echoue en denoncant comme « refuses par la base » des types que
     * la base accepte : la source de verite avait vieilli sans que rien ne le dise.
     * On lit desormais la DERNIERE migration qui porte ce CHECK, quel que soit son
     * numero — le test suit les migrations au lieu de les dater.
     */
    private static Path derniereMigrationPortantLeCheck() throws IOException {
        Pattern version = Pattern.compile("^V(\\d+)__");
        Path derniere = null;
        int max = -1;
        try (var fichiers = Files.list(MIGRATIONS)) {
            for (Path f : fichiers.toList()) {
                Matcher v = version.matcher(f.getFileName().toString());
                if (!v.find()) continue;
                if (!CHECK_DOCUMENT_TYPE.matcher(
                        Files.readString(f, StandardCharsets.UTF_8)).find()) continue;
                int n = Integer.parseInt(v.group(1));
                if (n > max) { max = n; derniere = f; }
            }
        }
        assertThat(derniere)
                .as("aucune migration de %s ne porte le CHECK sur document_type", MIGRATIONS)
                .isNotNull();
        return derniere;
    }

    private static final Pattern CHECK_DOCUMENT_TYPE = Pattern.compile(
            "CHECK\\s*\\(\\s*document_type\\s+IN\\s*\\(([\\s\\S]*?)\\)\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static Set<String> typesAcceptesEnBase() throws IOException {
        Path migration = derniereMigrationPortantLeCheck();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        Matcher check = CHECK_DOCUMENT_TYPE.matcher(sql);
        assertThat(check.find())
                .as("la contrainte CHECK sur document_type doit etre lisible dans %s", migration)
                .isTrue();
        Matcher valeurs = Pattern.compile("'([A-Z0-9_]+)'").matcher(check.group(1));
        Set<String> out = new java.util.LinkedHashSet<>();
        while (valeurs.find()) out.add(valeurs.group(1));
        return out;
    }

    @Test
    @DisplayName("Aucun doublon, aucun libelle vide")
    void catalogueBienForme() {
        List<DocumentTypeCatalogue.TypeDocument> types = DocumentTypeCatalogue.tous();

        assertThat(types).isNotEmpty();
        assertThat(types.stream().map(DocumentTypeCatalogue.TypeDocument::code).distinct().count())
                .as("un code ne doit apparaitre qu'une fois dans le menu")
                .isEqualTo(types.size());
        assertThat(types)
                .allSatisfy(t -> assertThat(t.libelle())
                        .as("le type %s doit porter un libelle lisible, pas son code brut", t.code())
                        .isNotBlank());
    }

    @Test
    @DisplayName("Tout type du catalogue est accepte par la base")
    void aucunTypeQueLaBaseRefuserait() throws IOException {
        Set<String> enBase = typesAcceptesEnBase();
        Set<String> auMenu = DocumentTypeCatalogue.tous().stream()
                .map(DocumentTypeCatalogue.TypeDocument::code)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertThat(auMenu)
                .as("un type propose puis refuse a l'INSERT fait echouer le depot "
                        + "au dernier moment, apres que l'employe a choisi son fichier")
                .isSubsetOf(enBase);
    }

    @Test
    @DisplayName("Tout type accepte par la base est proposable")
    void aucunTypeIndeposeable() throws IOException {
        Set<String> enBase = typesAcceptesEnBase();
        Set<String> auMenu = DocumentTypeCatalogue.tous().stream()
                .map(DocumentTypeCatalogue.TypeDocument::code)
                .collect(Collectors.toSet());

        assertThat(enBase)
                .as("c'est exactement le defaut corrige : des types acceptes en base "
                        + "mais absents du menu, donc rangeables seulement sous AUTRE")
                .allSatisfy(code -> assertThat(auMenu).contains(code));
    }

    @Test
    @DisplayName("Le rangement en groupe est celui de GroupeDocument, sans exception")
    void groupeCoherentAvecLeRangement() {
        for (DocumentTypeCatalogue.TypeDocument t : DocumentTypeCatalogue.tous()) {
            GroupeDocument attendu = GroupeDocument.deduire(t.code());
            assertThat(t.groupe())
                    .as("type %s : le menu et le rangement du dossier de ticket doivent "
                            + "dire la meme chose", t.code())
                    .isEqualTo(attendu == null ? null : attendu.name());
        }
    }

    @Test
    @DisplayName("Les types introduits par le lot 1 sont proposables au depot manuel")
    void typesDuLot1Presents() {
        Set<String> auMenu = DocumentTypeCatalogue.tous().stream()
                .map(DocumentTypeCatalogue.TypeDocument::code)
                .collect(Collectors.toSet());

        // Ceux que l'audit avait releves comme manquants — ils retombaient en AUTRE.
        assertThat(auMenu).contains(
                "CN", "CONTRAT_DOMICILIATION", "TITRE_PROPRIETE", "ATTESTATION_ENREGISTREMENT",
                "POUVOIR", "RAPPORT_COMMISSAIRE_APPORTS", "ETAT_ACTES_FORMATION");
    }

    @Test
    @DisplayName("Lot 5 : les trois formulaires DEPOSES ne se confondent pas avec ce qu'ils font obtenir")
    void formulairesDuLot5DistinctsDesJustificatifsRecus() {
        Set<String> auMenu = DocumentTypeCatalogue.tous().stream()
                .map(DocumentTypeCatalogue.TypeDocument::code)
                .collect(Collectors.toSet());

        // Les imprimes que le cabinet DEPOSE...
        assertThat(auMenu).contains("DEMANDE_TAXE_PROFESSIONNELLE", "DECLARATION_EXISTENCE",
                "DECLARATION_IMMATRICULATION_RC");
        // ... et les documents qu'il RECOIT en retour, qui restent des types distincts.
        assertThat(auMenu).contains("TP", "BULLETIN_IF", "RC");

        // Les trois formulaires sont produits par JURIKA : ils se rangent avec les
        // actes generes, jamais avec les justificatifs de l'administration.
        for (String code : List.of("DEMANDE_TAXE_PROFESSIONNELLE", "DECLARATION_EXISTENCE",
                "DECLARATION_IMMATRICULATION_RC")) {
            assertThat(GroupeDocument.deduire(code))
                    .as("%s est produit par la plateforme", code)
                    .isEqualTo(GroupeDocument.ACTES_GENERES);
        }
        assertThat(GroupeDocument.deduire("TP"))
                .isEqualTo(GroupeDocument.JUSTIFICATIFS_ADMINISTRATIFS);
    }
}
