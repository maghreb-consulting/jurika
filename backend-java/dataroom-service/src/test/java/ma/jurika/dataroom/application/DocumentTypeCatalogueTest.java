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

    /** La migration qui porte la liste des types acceptes. */
    private static final Path V24 = Path.of(
            "src/main/resources/db/migration/V24__documents_types_creation_et_groupes.sql");

    private static Set<String> typesAcceptesEnBase() throws IOException {
        String sql = Files.readString(V24, StandardCharsets.UTF_8);
        // La contrainte CHECK enumere les valeurs autorisees.
        Matcher check = Pattern.compile(
                "CHECK\\s*\\(\\s*document_type\\s+IN\\s*\\(([\\s\\S]*?)\\)\\s*\\)",
                Pattern.CASE_INSENSITIVE).matcher(sql);
        assertThat(check.find())
                .as("la contrainte CHECK sur document_type doit etre lisible dans %s", V24)
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
}
