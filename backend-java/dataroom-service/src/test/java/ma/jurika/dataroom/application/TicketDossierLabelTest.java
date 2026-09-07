package ma.jurika.dataroom.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enonce, test 6 : le libelle du dossier de ticket doit etre conforme a la regle
 * de composition, pour la creation ET pour une modification a plusieurs types.
 */
class TicketDossierLabelTest {

    private static Instant jour(int annee, int mois, int jour) {
        return LocalDate.of(annee, mois, jour)
                .atStartOfDay(ZoneId.of("Africa/Casablanca")).toInstant();
    }

    @Test
    @DisplayName("Creation : type, identifiant, date")
    void creation() {
        String libelle = TicketDossierLabel.compose(
                "CREATION", "T-2026-00841", jour(2026, 6, 15), List.of());

        assertThat(libelle).isEqualTo("Création — T-2026-00841 — 15/06/2026");
    }

    @Test
    @DisplayName("Modification a plusieurs types : les types retenus sont listes, dans l'ordre")
    void modificationPlusieursTypes() {
        String libelle = TicketDossierLabel.compose(
                "MODIFICATION", "T-2026-00902", jour(2026, 9, 4),
                List.of("Transfert de siège", "Changement de gérant"));

        assertThat(libelle).isEqualTo(
                "Modification — Transfert de siège, Changement de gérant — T-2026-00902 — 04/09/2026");
    }

    @Test
    @DisplayName("Dissolution : aucun sous-type n'est insere")
    void dissolution() {
        String libelle = TicketDossierLabel.compose(
                "DISSOLUTION", "T-2026-00915", jour(2026, 9, 20), List.of());

        assertThat(libelle).isEqualTo("Dissolution — T-2026-00915 — 20/09/2026");
    }

    @Test
    @DisplayName("Un sous-type sur un type non-MODIFICATION est ignore")
    void sousTypeIgnoreHorsModification() {
        String libelle = TicketDossierLabel.compose(
                "CREATION", "T-2026-00841", jour(2026, 6, 15),
                List.of("Transfert de siège"));

        assertThat(libelle).isEqualTo("Création — T-2026-00841 — 15/06/2026");
    }

    @Test
    @DisplayName("Modification sans sous-type retenu : le segment est saute, pas laisse vide")
    void modificationSansSousType() {
        assertThat(TicketDossierLabel.compose(
                "MODIFICATION", "T-2026-00902", jour(2026, 9, 4), List.of()))
                .isEqualTo("Modification — T-2026-00902 — 04/09/2026");

        assertThat(TicketDossierLabel.compose(
                "MODIFICATION", "T-2026-00902", jour(2026, 9, 4), Arrays.asList("  ", null)))
                .as("des sous-types vides ne doivent pas produire de separateur orphelin")
                .isEqualTo("Modification — T-2026-00902 — 04/09/2026");
    }

    @Test
    @DisplayName("Un type inconnu s'affiche brut plutot que de masquer le dossier")
    void typeInconnu() {
        assertThat(TicketDossierLabel.compose(
                "FUSION_ABSORPTION", "T-2026-01000", jour(2026, 10, 1), List.of()))
                .isEqualTo("FUSION_ABSORPTION — T-2026-01000 — 01/10/2026");

        assertThat(TicketDossierLabel.compose(null, "T-2026-01001", jour(2026, 10, 2), null))
                .isEqualTo("Opération — T-2026-01001 — 02/10/2026");
    }

    @Test
    @DisplayName("La date est lue dans le fuseau du cabinet")
    void fuseauDuCabinet() {
        // 15/06/2026 a 23h30 a Casablanca : la date affichee reste le 15/06.
        Instant tard = LocalDate.of(2026, 6, 15).atTime(23, 30)
                .atZone(ZoneId.of("Africa/Casablanca")).toInstant();

        assertThat(TicketDossierLabel.compose("CREATION", "T-2026-00841", tard, List.of()))
                .isEqualTo("Création — T-2026-00841 — 15/06/2026");
    }

    @Test
    @DisplayName("Les codes herites d'un ancien formulaire rendent le meme libelle")
    void codesHerites() {
        // Constate a l'audit du lot 1 sur T-2026-00616 : le dossier s'affichait
        // « Modification — modification denomination, transfert siege — ... ».
        // Le repli se contentait de retirer les tirets bas : sans accent, sans
        // majuscule, et en repetant le mot « Modification » que le libelle porte
        // deja.
        assertThat(ModificationSousTypes.libelle("modification_denomination"))
                .isEqualTo("changement de dénomination");
        assertThat(ModificationSousTypes.libelle("modification_objet"))
                .isEqualTo("changement de l'objet social");
        assertThat(ModificationSousTypes.libelle("transfert_siege"))
                .isEqualTo("transfert du siège social");
        // Le code courant, en majuscules, rend exactement le meme libelle.
        assertThat(ModificationSousTypes.libelle("TRANSFERT_SIEGE"))
                .isEqualTo(ModificationSousTypes.libelle("transfert_siege"));
    }

    @Test
    @DisplayName("Un code reellement inconnu reste affiche en clair")
    void codeInconnuResteLisible() {
        // Un dossier au nom approximatif vaut mieux qu'un dossier sans nom.
        assertThat(ModificationSousTypes.libelle("MODIF_INEDITE_2027"))
                .isEqualTo("modif inedite 2027");
        assertThat(ModificationSousTypes.libelle("   ")).isNull();
    }

    @Test
    @DisplayName("Une modification a plusieurs types les nomme TOUS, dans l'ordre")
    void plusieursTypes() {
        assertThat(TicketDossierLabel.compose("MODIFICATION", "T-2026-00902",
                jour(2026, 9, 4),
                List.of("transfert du siège social", "nomination d'un gérant")))
                .isEqualTo("Modification — transfert du siège social, "
                        + "nomination d'un gérant — T-2026-00902 — 04/09/2026");
    }
}
