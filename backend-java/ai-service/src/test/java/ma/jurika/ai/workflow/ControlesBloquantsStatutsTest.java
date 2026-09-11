package ma.jurika.ai.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot B — LES TROIS CONTRÔLES BLOQUANTS DU § 18, ÉPROUVÉS SUR DES VALEURS.
 *
 * <p>Le parcours du 9 septembre a retiré trois lignes du référentiel — certificat
 * négatif, contrôle d'identité des associés, rapport du commissaire aux apports —
 * pour en faire des contrôles exécutés au lancement de la génération des statuts.
 *
 * <p>Chaque test vérifie deux choses : que la génération est bien refusée, et que
 * le message <b>nomme ce qui manque</b>. Un refus qui ne dit pas quoi compléter
 * renvoie l'employé chercher au hasard — c'est le défaut que le lot A avait
 * trouvé sur la déclaration d'existence, sortie avec un marqueur rouge imprimé.
 */
class ControlesBloquantsStatutsTest {

    /** Un jeu de variables complet : aucun contrôle ne doit bloquer. */
    private static Map<String, Object> complet() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("CERTIFICAT_NEGATIF_NUMERO", "CN2026/114532");
        v.put("CERTIFICAT_NEGATIF_DATE", "02/09/2026");
        v.put("ASSOCIES", List.of(associe("Yassine", "BENANI"), associe("Nour", "ALAMI")));
        v.put("GERANTS", List.of(gerant("Yassine", "BENANI")));
        v.put("COMMISSAIRE_APPORTS_DESIGNATION", "non requis");
        return v;
    }

    private static Map<String, Object> associe(String prenom, String nom) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("ASSOCIE_PRENOM", prenom);
        a.put("ASSOCIE_NOM", nom);
        a.put("ASSOCIE_TYPE", "personne physique");
        a.put("ASSOCIE_PIECE_TYPE", "CIN");
        a.put("ASSOCIE_PIECE_NUMERO", "BK" + nom.length() + "88421");
        return a;
    }

    private static Map<String, Object> gerant(String prenom, String nom) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("GERANT_PRENOM", prenom);
        g.put("GERANT_NOM", nom);
        g.put("GERANT_PIECE_TYPE", "CIN");
        g.put("GERANT_PIECE_NUMERO", "BK188421");
        return g;
    }

    @Test
    @DisplayName("Le contrôle ne porte QUE sur les statuts : les vingt et un autres modèles passent")
    void porteeLimiteeAuxStatuts() {
        assertThat(ControlesBloquantsStatuts.concerne("STATUTS_SARL")).isTrue();
        assertThat(ControlesBloquantsStatuts.concerne("STATUTS_SARL_AU")).isTrue();
        assertThat(ControlesBloquantsStatuts.concerne("CONTRAT_BAIL")).isFalse();
        assertThat(ControlesBloquantsStatuts.concerne("DECLARATION_EXISTENCE")).isFalse();
        assertThat(ControlesBloquantsStatuts.concerne(null)).isFalse();
    }

    @Test
    @DisplayName("Un dossier complet ne bloque pas")
    void dossierCompletPasse() {
        assertThat(ControlesBloquantsStatuts.motifDeRefus(complet())).isNull();
    }

    @Test
    @DisplayName("Certificat négatif absent : refus, et le message NOMME les deux variables")
    void certificatNegatifManquant() {
        Map<String, Object> v = complet();
        v.remove("CERTIFICAT_NEGATIF_NUMERO");
        v.put("CERTIFICAT_NEGATIF_DATE", "   ");

        String motif = ControlesBloquantsStatuts.motifDeRefus(v);

        assertThat(motif)
                .isNotNull()
                .contains("$CERTIFICAT_NEGATIF_NUMERO")
                .contains("$CERTIFICAT_NEGATIF_DATE")
                .contains("Génération des statuts bloquée");
    }

    @Test
    @DisplayName("Pièce d'identité manquante : le message dit DE QUEL associé il s'agit")
    void pieceIdentiteManquanteNommeLOccurrence() {
        Map<String, Object> v = complet();
        Map<String, Object> second = new LinkedHashMap<>(associe("Nour", "ALAMI"));
        second.remove("ASSOCIE_PIECE_NUMERO");
        v.put("ASSOCIES", List.of(associe("Yassine", "BENANI"), second));

        String motif = ControlesBloquantsStatuts.motifDeRefus(v);

        assertThat(motif)
                .as("« un associé » enverrait chercher au hasard parmi les associés")
                .isNotNull()
                .contains("2e associé")
                .contains("$ASSOCIE_PIECE_NUMERO")
                .doesNotContain("1er associé");
    }

    @Test
    @DisplayName("Un associé personne morale exige son RC et son représentant, pas une CIN")
    void associePersonneMorale() {
        Map<String, Object> pm = new LinkedHashMap<>();
        pm.put("ASSOCIE_DENOMINATION", "ATLAS HOLDING");
        pm.put("ASSOCIE_TYPE", "personne morale");
        pm.put("ASSOCIE_PIECE_TYPE", "Modèle J");
        pm.put("ASSOCIE_PIECE_NUMERO", "RC 445 221");
        Map<String, Object> v = complet();
        v.put("ASSOCIES", List.of(pm));

        String motif = ControlesBloquantsStatuts.motifDeRefus(v);

        assertThat(motif)
                .isNotNull()
                .contains("$ASSOCIE_RC_NUMERO")
                .contains("$ASSOCIE_REPRESENTANT_NOM")
                .contains("personne morale");
    }

    @Test
    @DisplayName("Pièce du gérant manquante : refus, et le gérant est nommé par son rang")
    void pieceGerantManquante() {
        Map<String, Object> sansPiece = new LinkedHashMap<>(gerant("Yassine", "BENANI"));
        sansPiece.remove("GERANT_PIECE_TYPE");
        Map<String, Object> v = complet();
        v.put("GERANTS", List.of(gerant("Nour", "ALAMI"), sansPiece));

        String motif = ControlesBloquantsStatuts.motifDeRefus(v);

        assertThat(motif).isNotNull().contains("2e gérant").contains("$GERANT_PIECE_TYPE");
    }

    @Test
    @DisplayName("Commissaire aux apports REQUIS sans rapport : refus")
    void commissaireRequisSansRapport() {
        Map<String, Object> v = complet();
        v.put("COMMISSAIRE_APPORTS_DESIGNATION", "requis");

        String motif = ControlesBloquantsStatuts.motifDeRefus(v);

        assertThat(motif)
                .isNotNull()
                .contains("$COMMISSAIRE_APPORTS_NOM")
                .contains("$COMMISSAIRE_APPORTS_DATE_RAPPORT");
    }

    @Test
    @DisplayName("Commissaire aux apports NON requis : le contrôle est sans objet")
    void commissaireNonRequisNeBloqueJamais() {
        // C'est le cas de la grande majorité des dossiers : aucun apport en nature.
        // Exiger un rapport là où la loi n'en demande pas les bloquerait tous.
        for (String designation : List.of("non requis", "dispensé", "", "   ")) {
            Map<String, Object> v = complet();
            v.put("COMMISSAIRE_APPORTS_DESIGNATION", designation);
            assertThat(ControlesBloquantsStatuts.motifDeRefus(v))
                    .as("designation = « %s »", designation)
                    .isNull();
        }
    }

    @Test
    @DisplayName("Plusieurs manques : le message les énumère tous, pas seulement le premier")
    void tousLesManquesSontNommes() {
        Map<String, Object> v = complet();
        v.remove("CERTIFICAT_NEGATIF_NUMERO");
        v.put("COMMISSAIRE_APPORTS_DESIGNATION", "requis");
        Map<String, Object> sansPiece = new LinkedHashMap<>(associe("Nour", "ALAMI"));
        sansPiece.remove("ASSOCIE_PIECE_TYPE");
        v.put("ASSOCIES", List.of(sansPiece));

        String motif = ControlesBloquantsStatuts.motifDeRefus(v);

        assertThat(motif)
                .as("corriger un manque pour en découvrir un autre fait trois allers-retours")
                .isNotNull()
                .contains("$CERTIFICAT_NEGATIF_NUMERO")
                .contains("$ASSOCIE_PIECE_TYPE")
                .contains("$COMMISSAIRE_APPORTS_NOM");
    }

    @Test
    @DisplayName("Aucun associé ni gérant renseigné : seul le certificat négatif est réclamé")
    void bouclesVidesNeReclamentRien() {
        // Une boucle vide ne produit aucune occurrence dans le document : il n'y a
        // donc rien à contrôler. Le certificat négatif, lui, est scalaire.
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("CERTIFICAT_NEGATIF_NUMERO", "CN2026/114532");
        v.put("CERTIFICAT_NEGATIF_DATE", "02/09/2026");

        assertThat(ControlesBloquantsStatuts.motifDeRefus(v)).isNull();
    }
}
