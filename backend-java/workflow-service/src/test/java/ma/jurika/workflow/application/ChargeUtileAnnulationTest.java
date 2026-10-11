package ma.jurika.workflow.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4a (E5, RG-TKT-04) : la charge des documents d'annulation -- faits du ticket,
 * donnees derivees du parcours, demarches interrompues completees par les saisies.
 */
class ChargeUtileAnnulationTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> variables(Map<String, Object> charge) {
        return (Map<String, Object>) charge.get("variables");
    }

    @Test
    void faits_du_ticket_et_derivees_d_une_modification_annulee() {
        FaitsDuTicket.Faits faits = new FaitsDuTicket.Faits("T-2026-00042", "2026-09-01", "Salma IDRISSI",
                "2026-10-05", "Le client renonce au transfert du siege.", "MODIFICATION", "DEROULEMENT_DEMARCHE");
        UUID dossier = UUID.randomUUID();
        Map<String, Object> charge = ChargeUtileAnnulation.assembler(faits, Map.of(), List.of(), dossier);
        Map<String, Object> v = variables(charge);
        assertThat(v).containsEntry("DOSSIER_NUMERO", "T-2026-00042")
                .containsEntry("DOSSIER_DATE_ANNULATION", "2026-10-05")
                .containsEntry("DOSSIER_MOTIF_ANNULATION", "Le client renonce au transfert du siege.")
                .containsEntry("ANNULATION_STATUT_ATTEINT", "D\u00e9roulement de la d\u00e9marche")
                .containsEntry("ANNULATION_FAMILLE", "Modification statutaire ou vie sociale")
                .containsEntry("SUCCURSALE_SOCIETE_ETRANGERE", "non")
                .containsEntry("SOCIETE_EN_LIQUIDATION", "non")
                .containsEntry("BORDEREAU_OBJET", "restitution")
                .containsEntry("ANNULATION_DEPOTS_EN_COURS", "non");
        assertThat(charge).containsEntry("dossierId", dossier.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void demarches_suivies_completees_par_les_saisies_rang_par_rang() {
        FaitsDuTicket.Faits faits = new FaitsDuTicket.Faits("T-1", "2026-09-01", "S", "2026-10-05", "Motif",
                "CREATION", "DEROULEMENT_DEMARCHE");
        List<Map<String, Object>> suivies = List.of(Map.of("DEMARCHE_DESIGNATION", "D\u00e9p\u00f4t au CRI",
                "DEMARCHE_ADMINISTRATION", "CRI Casablanca-Settat", "DEMARCHE_DATE_DEPOT", "2026-09-25"));
        Map<String, Object> magasin = Map.of("DEMARCHES_INTERROMPUES", List.of(Map.of(
                "DEMARCHE_REFERENCE", "CRI-2026-55821", "RETRAIT_ADMINISTRATION_ADRESSE", "Avenue Hassan II",
                "DEMARCHE_ADMINISTRATION", "ne doit pas recouvrir le suivi")));
        Map<String, Object> v = variables(ChargeUtileAnnulation.assembler(faits, magasin, suivies, null));
        List<Map<String, Object>> d = (List<Map<String, Object>>) v.get("DEMARCHES_INTERROMPUES");
        assertThat(d).singleElement().satisfies(it -> assertThat(it)
                .containsEntry("DEMARCHE_ADMINISTRATION", "CRI Casablanca-Settat")
                .containsEntry("DEMARCHE_REFERENCE", "CRI-2026-55821")
                .containsEntry("RETRAIT_ADMINISTRATION_ADRESSE", "Avenue Hassan II"));
        assertThat(v).containsEntry("ANNULATION_DEPOTS_EN_COURS", "oui")
                .doesNotContainKey("SUCCURSALE_SOCIETE_ETRANGERE");
    }

    @Test
    void statut_ou_famille_inconnus_ne_sont_pas_supposes() {
        FaitsDuTicket.Faits faits = new FaitsDuTicket.Faits("T-1", null, null, null, null, null, null);
        Map<String, Object> v = variables(ChargeUtileAnnulation.assembler(faits, Map.of(), List.of(), null));
        assertThat(v).doesNotContainKeys("ANNULATION_STATUT_ATTEINT", "ANNULATION_FAMILLE", "DOSSIER_DATE_OUVERTURE");
    }
}
