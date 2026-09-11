package ma.jurika.ticket.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le referentiel des demarches est en DONNEES : il n'est verifie par aucun
 * compilateur. Ces tests lisent la migration telle qu'elle sera jouee et
 * verifient les invariants que l'affichage suppose — avant deploiement, pas
 * apres.
 *
 * <p><b>Lot B (2026-09-11) — ce test change de sujet avec le referentiel.</b>
 * Il portait sur les 36 etapes de V20 et sur les neuf phases {@code P0}…{@code P8}
 * du guide du 4 septembre, dont le libelle unique par code avait ete corrige par
 * V23. Le parcours du 9 septembre remplace ce referentiel : 51 lignes, plus
 * aucune phase, un regroupement par STATUT de ticket.
 *
 * <p>Les anciennes assertions ne decrivaient plus rien de vivant : elles
 * passaient parce qu'elles lisaient V20, une migration figee que plus aucune
 * ligne active ne reflete. Un test vert sur une donnee morte est pire qu'un test
 * absent — il rassure.
 */
class DemarchesReferentielMigrationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final String V24 = "V24__referentiel_parcours_creation_51.sql";
    private static final String V26 = "V26__delai_depuis_une_donnee_du_dossier.sql";

    /**
     * `VALUES ('CREATION', 12, 'S2', 'Génération des documents',` — l'en-tete de
     * chaque INSERT du referentiel, la ou vivent le workflow, l'ordre, le code de
     * phase et son libelle.
     */
    private static final Pattern INSERT_REFERENTIEL = Pattern.compile(
            "VALUES\\s*\\(\\s*'([A-Z_]+)'\\s*,\\s*(\\d+)\\s*,\\s*'([^']+)'\\s*,\\s*'((?:[^']|'')*)'");

    /** La fin de chaque INSERT : `…, 'ENREGISTREMENT_STATUTS', 'DEPOT', TRUE);` */
    private static final Pattern FIN_INSERT = Pattern.compile(
            ",\\s*(NULL|'([A-Z_]+)')\\s*,\\s*(NULL|'(DEPOT|RETRAIT)')\\s*,\\s*(TRUE|FALSE)\\s*\\)\\s*;");

    private record Ligne(int ordre, String phaseCode, String phaseLibelle,
                          String formaliteCode, String volet) {}

    /** Les lignes ACTIVES du referentiel, telles que V24 les charge. */
    private static List<Ligne> lignes() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve(V24), StandardCharsets.UTF_8);
        List<Ligne> out = new ArrayList<>();

        // On decoupe par INSERT pour rapprocher chaque en-tete de sa propre fin :
        // une recherche globale apparierait la formalite d'une ligne avec l'ordre
        // d'une autre, et le test dirait n'importe quoi sans echouer.
        for (String bloc : sql.split("INSERT INTO demarches_referentiel")) {
            Matcher m = INSERT_REFERENTIEL.matcher(bloc);
            if (!m.find()) continue;
            int ordre = Integer.parseInt(m.group(2));
            // La ligne d'archive (ordre > 9000) et la ligne tampon ne sont pas du
            // parcours : elles portent les cochages d'un referentiel retire.
            if (ordre > 51) continue;
            Matcher f = FIN_INSERT.matcher(bloc);
            String formalite = null;
            String volet = null;
            if (f.find()) {
                formalite = f.group(2);
                volet = f.group(4);
            }
            out.add(new Ligne(ordre, m.group(3), m.group(4).replace("''", "'"),
                    formalite, volet));
        }
        return out;
    }

    @Test
    @DisplayName("Le parcours du 9 septembre compte 51 lignes, numerotees 1 a 51 sans trou")
    void cinquanteEtUneLignes() throws IOException {
        List<Ligne> lignes = lignes();

        assertThat(lignes)
                .as("le classeur du cabinet porte 51 lignes de parcours")
                .hasSize(51);
        assertThat(lignes.stream().map(Ligne::ordre).sorted().toList())
                .as("un trou dans la numerotation ferait disparaitre une demarche de l'ecran")
                .isEqualTo(java.util.stream.IntStream.rangeClosed(1, 51).boxed().toList());
    }

    @Test
    @DisplayName("Cinq statuts, cinq codes de phase, et un seul libelle par code")
    void cinqStatuts() throws IOException {
        Map<String, Set<String>> parCode = new LinkedHashMap<>();
        for (Ligne l : lignes()) {
            parCode.computeIfAbsent(l.phaseCode(), k -> new LinkedHashSet<>()).add(l.phaseLibelle());
        }

        assertThat(parCode.keySet())
                .as("le parcours regroupe par STATUT : S1 a S5, et rien d'autre")
                .containsExactlyInAnyOrder("S1", "S2", "S3", "S4", "S5");

        // L'API regroupe par CODE et ne retient qu'un libelle : en declarer
        // plusieurs en fait disparaitre un, sans erreur et sans trace. C'est le
        // defaut qu'avait eu la phase P4 du guide precedent.
        parCode.forEach((code, libelles) -> assertThat(libelles)
                .as("Phase %s : l'API regroupe par CODE et ne garde qu'un libelle. "
                        + "Libelles trouves : %s", code, libelles)
                .hasSize(1));
    }

    @Test
    @DisplayName("Le premier statut porte le libelle du parcours, collecte d'information comprise")
    void premierStatutRenomme() throws IOException {
        String libelle = lignes().stream()
                .filter(l -> "S1".equals(l.phaseCode()))
                .map(Ligne::phaseLibelle)
                .findFirst()
                .orElseThrow();

        assertThat(libelle).isEqualTo("Création du ticket et collecte d'information");
    }

    @Test
    @DisplayName("Douze formalites figurent sur deux lignes, et le retrait suit toujours le depot")
    void depotEtRetraitApparies() throws IOException {
        Map<String, Map<String, Integer>> parFormalite = new LinkedHashMap<>();
        for (Ligne l : lignes()) {
            if (l.formaliteCode() == null) continue;
            parFormalite.computeIfAbsent(l.formaliteCode(), k -> new LinkedHashMap<>())
                    .put(l.volet(), l.ordre());
        }

        assertThat(parFormalite)
                .as("le parcours scinde douze formalites : enregistrement du bail, depot du "
                        + "capital, statuts, acte de nomination, taxe professionnelle, "
                        + "declaration d'existence, immatriculation, CNSS, livres legaux, "
                        + "CNDP, agrements, SIMPL")
                .hasSize(12);

        parFormalite.forEach((code, volets) -> {
            assertThat(volets.keySet())
                    .as("Formalite %s : un depot ET un retrait, sinon la ligne de retrait ne "
                            + "sait pas depuis quand elle attend", code)
                    .containsExactlyInAnyOrder("DEPOT", "RETRAIT");
            assertThat(volets.get("RETRAIT"))
                    .as("Formalite %s : on ne retire pas avant d'avoir depose", code)
                    .isGreaterThan(volets.get("DEPOT"));
        });

        assertThat(parFormalite.values().stream().mapToInt(Map::size).sum())
                .as("douze formalites scindees font vingt-quatre lignes")
                .isEqualTo(24);
    }

    @Test
    @DisplayName("Le retrait du modele J court depuis le depot au greffe, et lui seul")
    void delaiDuRetraitCourtDepuisLeDepot() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve(V24), StandardCharsets.UTF_8);

        // La ligne 28 (« retrait du modele J ») est la SEULE ligne de retrait a
        // porter une duree chiffree : « 24 a 72 heures apres le depot ». Sa
        // reference doit etre la ligne 27, son depot — pas l'ouverture du dossier.
        Matcher m = Pattern.compile(
                "-- -- Ligne 28 —[\\s\\S]*?delai_valeur[\\s\\S]*?VALUES[\\s\\S]*?,\\s*"
                        + "(\\d+)\\s*,\\s*'(JOURS|MOIS)'\\s*,\\s*(\\d+)\\s*,").matcher(sql);
        boolean trouve = false;
        for (String bloc : sql.split("-- -- Ligne ")) {
            if (!bloc.startsWith("28 ")) continue;
            Matcher d = Pattern.compile(
                    ",\\s*(\\d+)\\s*,\\s*'(JOURS|MOIS)'\\s*,\\s*(\\d+)\\s*,").matcher(bloc);
            assertThat(d.find()).as("la ligne 28 doit porter un delai calculable").isTrue();
            assertThat(d.group(3))
                    .as("le delai du retrait court depuis la date du DEPOT (ligne 27)")
                    .isEqualTo("27");
            trouve = true;
            break;
        }
        assertThat(trouve).as("ligne 28 introuvable dans %s", V24).isTrue();
        assertThat(m).isNotNull();
    }

    @Test
    @DisplayName("Aucune ligne ne porte un delai chiffre sans point de depart mecanisable")
    void aucuneDateFabriquee() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve(V24), StandardCharsets.UTF_8);
        Set<String> delaisSansOrdre = new LinkedHashSet<>();

        // La contrainte `chk_delai_complet` (V20) impose deja les trois colonnes
        // ensemble ou aucune. Ce test verifie l'autre sens : qu'aucun INSERT ne
        // pose une duree sans reference — ce qui produirait une echeance fabriquee.
        for (String bloc : sql.split("INSERT INTO demarches_referentiel")) {
            if (!bloc.contains("VALUES ('CREATION'")) continue;
            // Le triple du delai se lit par la FIN de l'INSERT, jamais au milieu :
            // `'Dans les 30 jours de l''acte', NULL, NULL, 30, 'JOURS', 13, …`
            // contient un motif qui ressemble au triple (« NULL, NULL, 30 ») et
            // le ferait declarer incomplet. On ancre donc sur les colonnes qui le
            // suivent — formalite, volet, actif.
            Matcher d = Pattern.compile(
                    ",\\s*(\\d+|NULL)\\s*,\\s*('JOURS'|'MOIS'|NULL)\\s*,\\s*(\\d+|NULL)\\s*,"
                            + "\\s*(NULL|'[A-Z_]+')\\s*,\\s*(?:NULL|'(?:DEPOT|RETRAIT)')"
                            + "\\s*,\\s*TRUE\\s*\\)\\s*;")
                    .matcher(bloc);
            if (!d.find()) continue;
            boolean valeur = !"NULL".equals(d.group(1));
            boolean unite = !"NULL".equals(d.group(2));
            boolean reference = !"NULL".equals(d.group(3));
            String formalite = d.group(4).replace("'", "");

            if (valeur && unite && !reference) {
                // Lot B — DEUX lignes portent une duree sans ordre de reference,
                // et c'est VOULU : leur point de depart n'est pas un cochage mais
                // une donnee du dossier. V24 ne peut pas le dire — la colonne
                // n'existe pas encore quand elle s'execute —, c'est V26 qui
                // l'attache. Ces deux-la sont donc legitimes A CONDITION que V26
                // leur donne effectivement un depart : c'est verifie plus bas.
                delaisSansOrdre.add(formalite);
                continue;
            }
            assertThat(valeur == unite && unite == reference)
                    .as("delai incomplet : %s / %s / %s — une echeance sans point de depart "
                            + "serait fabriquee", d.group(1), d.group(2), d.group(3))
                    .isTrue();
        }

        // Aucune ligne ne reste orpheline : chaque duree sans ordre de reference
        // doit trouver son point de depart dans V26. Si l'une des deux migrations
        // change sans l'autre, le delai devient soit aveugle, soit fabrique.
        String v26 = Files.readString(MIGRATIONS.resolve(V26), StandardCharsets.UTF_8);
        assertThat(delaisSansOrdre)
                .as("les seules durees sans ordre de reference sont celles que V26 "
                        + "rattache a une donnee du dossier")
                .containsExactlyInAnyOrder("TAXE_PROFESSIONNELLE", "AFFILIATION_CNSS");
        for (String formalite : delaisSansOrdre) {
            assertThat(v26)
                    .as("V26 doit donner un point de depart a %s", formalite)
                    .contains(formalite);
        }
    }

    @Test
    @DisplayName("V26 rattache les deux delais a une DONNEE, et refuse d'en rattacher d'autres")
    void deuxDelaisPartentDuneDonnee() throws IOException {
        String v26 = Files.readString(MIGRATIONS.resolve(V26), StandardCharsets.UTF_8);

        assertThat(v26)
                .as("la colonne est ajoutee de facon idempotente")
                .contains("ADD COLUMN IF NOT EXISTS delai_reference_donnee");
        assertThat(v26)
                .as("un delai a UN point de depart : deux le rendraient ambigu, et "
                        + "l'ambiguite se resoudrait en silence")
                .contains("CHECK (delai_reference_ordre IS NULL OR delai_reference_donnee IS NULL)");
        assertThat(v26)
                .as("le filtre porte sur le CODE de formalite, pas sur l'ordre : un numero "
                        + "de ligne est un rang dans un classeur, il bougera")
                .contains("formalite_code IN ('TAXE_PROFESSIONNELLE', 'AFFILIATION_CNSS')");
        assertThat(v26)
                .as("30 jours, en JOURS — jamais convertis")
                .contains("delai_valeur = 30")
                .contains("delai_unite = 'JOURS'");
        assertThat(v26)
                .as("un garde-fou refuse la migration si le parcours a bouge sous nos pieds")
                .contains("RAISE EXCEPTION");
    }

    @Test
    @DisplayName("La migration conserve les demarches cochees plutot que de les supprimer")
    void lesCochagesSurvivent() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve(V24), StandardCharsets.UTF_8);

        assertThat(sql)
                .as("les lignes sans equivalent sont rapportees, pas jetees")
                .contains("demarches_migration_orphelines");
        assertThat(sql)
                .as("une ligne retiree du parcours reste en base tant qu'un cochage la reference")
                .contains("actif");
        assertThat(sql)
                .as("un garde-fou doit refuser la migration si un cochage perd sa demarche")
                .contains("cochage(s) orphelin(s) de referentiel");
        assertThat(sql)
                .as("aucun DELETE ne doit viser les cochages eux-memes")
                .doesNotContain("DELETE FROM ticket_demarches");
    }
}
