package ma.jurika.workflow.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LA MOITIÉ AMONT DE LA CHAÎNE DES SIGNATAIRES.
 *
 * <p>Le défaut le plus grave du lot tient en deux maillons :
 *
 * <ol>
 *   <li><b>ici</b> — le constructeur unique doit produire {@code signataires[]}
 *       à partir de la boucle {@code SIGNATAIRES} du magasin ;</li>
 *   <li>en aval — le résolveur doit l'honorer plutôt que de retomber sur les
 *       gérants. C'est établi par
 *       {@code SignatairesDesignesNonSubstituesTest} (ai-service), vert.</li>
 * </ol>
 *
 * <p>Le maillon rompu était le premier : le constructeur vivant du navigateur ne
 * transmettait jamais la clé. Le résolveur trouvait une liste vide, appliquait
 * son repli — « à défaut, chaque gérant est signataire, qualité <i>gérant</i> » —
 * et produisait un acte complet, cohérent et faux.
 *
 * <p>Le témoin est le dossier {@code 627c0190-5ffe-4249-ab53-…} : <b>un</b>
 * signataire désigné, « EL AMRANI Youssef », qualité « Gerant statutaire », face
 * à <b>deux</b> gérants.
 */
class SignatairesDuMagasinTest {

    /** Le magasin du dossier témoin : deux gérants, un signataire désigné. */
    private static Map<String, Object> magasinDuTemoin() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("DENOMINATION", "TEMOIN LOT C SARL");
        v.put("GERANTS", List.of(
                gerant("EL AMRANI", "Youssef"),
                gerant("BENNANI", "Karim")));
        v.put("SIGNATAIRES", List.of(
                Map.of("SIGNATAIRE_NOM", "EL AMRANI Youssef",
                        "SIGNATAIRE_QUALITE", "Gerant statutaire")));
        return v;
    }

    private static Map<String, Object> gerant(String nom, String prenom) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("GERANT_NOM", nom);
        g.put("GERANT_PRENOM", prenom);
        g.put("GERANT_CIVILITE", "M");
        return g;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> liste(Map<String, Object> charge, String cle) {
        Object o = charge.get(cle);
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @Test
    @DisplayName("Le constructeur transporte le signataire DÉSIGNÉ, et lui seul")
    void leConstructeurTransporteLesSignatairesDesignes() {
        Map<String, Object> charge =
                ConstructeurChargeUtileCreation.construire(magasinDuTemoin());
        List<Map<String, Object>> signataires = liste(charge, "signataires");

        assertThat(signataires)
                .describedAs("""
                        Un signataire a été désigné, deux gérants existent. La charge \
                        utile doit porter UN signataire — c'est la clé que le \
                        constructeur vivant ne transmettait jamais.""")
                .hasSize(1);
        assertThat(signataires.get(0))
                .containsEntry("nom", "EL AMRANI Youssef")
                .containsEntry("qualite", "Gerant statutaire");
    }

    @Test
    @DisplayName("Les gérants restent les gérants — la boucle n'est pas confondue")
    void lesGerantsRestentDistincts() {
        Map<String, Object> charge =
                ConstructeurChargeUtileCreation.construire(magasinDuTemoin());

        assertThat(liste(charge, "gerants")).hasSize(2);
        assertThat(liste(charge, "gerants").get(0)).containsEntry("nom", "EL AMRANI");
        assertThat(liste(charge, "gerants").get(1)).containsEntry("nom", "BENNANI");
    }

    @Test
    @DisplayName("Sans signataire désigné, la liste est VIDE — le repli reste au résolveur")
    void sansDesignationLaListeEstVide() {
        Map<String, Object> magasin = new LinkedHashMap<>(magasinDuTemoin());
        magasin.remove("SIGNATAIRES");

        Map<String, Object> charge = ConstructeurChargeUtileCreation.construire(magasin);

        assertThat(liste(charge, "signataires"))
                .describedAs("""
                        Le constructeur ne fabrique PAS de signataires par défaut. Le \
                        repli « à défaut, les gérants » appartient au résolveur, et il \
                        est juste — mais il ne doit s'appliquer qu'à une liste \
                        réellement vide.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Les dix champs de l'étape 9 atteignent la charge utile")
    void lesDixChampsDeLEtape9Passent() {
        Map<String, Object> magasin = new LinkedHashMap<>();
        magasin.put("LIEU_SIGNATURE", "Rabat");
        magasin.put("NOMBRE_ORIGINAUX", "8");
        magasin.put("HEURE_ACTE", "15 heures");
        magasin.put("EXERCICE_DEBUT", "1er avril");
        magasin.put("EXERCICE_FIN", "31 mars");
        magasin.put("PREMIER_EXERCICE_CLOTURE", "31 mars 2027");
        magasin.put("COMMISSAIRE_COMPTES_NOM", "Cabinet ALAMI");
        magasin.put("DUREE_MANDAT_CAC", "3");
        magasin.put("ENGAGEMENTS_MANDAT", "Aucun engagement particulier");
        magasin.put("ARTICLE_DESIGNATION_STATUTS", "Article 14");

        @SuppressWarnings("unchecked")
        Map<String, Object> societe = (Map<String, Object>)
                ConstructeurChargeUtileCreation.construire(magasin).get("societe");

        assertThat(societe)
                .describedAs("""
                        Ces dix champs étaient saisis à l'étape 9, persistés, et \
                        n'atteignaient AUCUN document : seul le constructeur mort les \
                        portait. $LIEU_SIGNATURE sert 21 gabarits sur 23.""")
                .containsEntry("lieuSignature", "Rabat")
                .containsEntry("villeSignature", "Rabat")
                .containsEntry("nombreOriginaux", "8")
                .containsEntry("heureActe", "15 heures")
                .containsEntry("exerciceDebut", "1er avril")
                .containsEntry("exerciceFin", "31 mars")
                .containsEntry("premierExerciceCloture", "31 mars 2027")
                .containsEntry("commissaireComptesNom", "Cabinet ALAMI")
                .containsEntry("dureeMandatCac", "3")
                .containsEntry("engagementsMandat", "Aucun engagement particulier")
                .containsEntry("articleDesignationStatuts", "Article 14");
    }

    @Test
    @DisplayName("$VILLE et $SIEGE_VILLE ne portent plus la même valeur")
    void villeEtSiegeVilleSontDistinctes() {
        Map<String, Object> magasin = new LinkedHashMap<>();
        magasin.put("SIEGE_VILLE", "Mohammedia");   // la ville de la société
        magasin.put("VILLE", "Casablanca");         // le bureau de la DGI
        magasin.put("RC_VILLE", "Casablanca");
        magasin.put("VILLE_GREFFE", "Casablanca");

        @SuppressWarnings("unchecked")
        Map<String, Object> societe = (Map<String, Object>)
                ConstructeurChargeUtileCreation.construire(magasin).get("societe");

        assertThat(societe.get("ville"))
                .describedAs("""
                        $VILLE désigne la ville du service de la DGI — elle figure sur \
                        la seule ligne « Subdivision : $SUBDIVISION — Ville : $VILLE » \
                        du cadre administratif des deux imprimés fiscaux. Elle ne peut \
                        plus être alimentée par la commune du siège.""")
                .isEqualTo("Casablanca");
        assertThat(societe.get("villeGreffe")).isEqualTo("Casablanca");
        assertThat(societe.get("rcVille")).isEqualTo("Casablanca");
    }
}
