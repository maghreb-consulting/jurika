package ma.jurika.ticket.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le referentiel des demarches est en DONNEES : il n'est donc verifie par aucun
 * compilateur. Ces tests lisent les migrations telles qu'elles seront jouees et
 * verifient les invariants que l'affichage suppose — avant deploiement, pas
 * apres.
 *
 * <p>Lot 2 (2026-09-07) — motive par un defaut reel : le code de phase {@code P4}
 * portait DEUX libelles (« P4 Capital » pour l'etape 16, « P4 Enregistrement »
 * pour les etapes 17-18). L'API regroupant les demarches par CODE, un des deux
 * libelles disparaissait purement et simplement de l'ecran, sans erreur et sans
 * trace : les etapes 17 et 18 s'affichaient sous « P4 Capital » et la phase
 * « Enregistrement » n'existait nulle part. Le guide, lui, n'annonce qu'une
 * phase — « PHASE 4 — CAPITAL ET ENREGISTREMENT ». V23 unifie le libelle ; ce
 * test interdit la reapparition de l'ecart.
 */
class DemarchesReferentielMigrationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /**
     * `VALUES ('CREATION', 12, 'P2', 'P2 Rédaction',` — on ne lit que l'en-tete
     * de chaque INSERT du referentiel, la ou vivent le workflow, l'ordre, le
     * code de phase et son libelle.
     */
    private static final Pattern INSERT_REFERENTIEL = Pattern.compile(
            "VALUES\\s*\\(\\s*'([A-Z_]+)'\\s*,\\s*(\\d+)\\s*,\\s*'([^']+)'\\s*,\\s*'((?:[^']|'')*)'");

    /** `SET phase_libelle = 'X' WHERE ... phase_code = 'P4'` — les corrections ulterieures. */
    private static final Pattern UPDATE_LIBELLE = Pattern.compile(
            "SET\\s+phase_libelle\\s*=\\s*'((?:[^']|'')*)'[\\s\\S]*?phase_code\\s*=\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE);

    /**
     * Etat du referentiel tel qu'il sera EN BASE : les INSERT de V20, puis les
     * UPDATE des migrations suivantes appliques dans l'ordre des versions.
     */
    private static Map<String, Set<String>> libellesParPhase() throws IOException {
        Map<String, Set<String>> parCode = new LinkedHashMap<>();

        String v20 = Files.readString(MIGRATIONS.resolve("V20__referentiel_demarches_creation.sql"),
                StandardCharsets.UTF_8);
        Matcher m = INSERT_REFERENTIEL.matcher(v20);
        while (m.find()) {
            String code = m.group(3);
            String libelle = m.group(4).replace("''", "'");
            parCode.computeIfAbsent(code, k -> new LinkedHashSet<>()).add(libelle);
        }

        // Les migrations posterieures peuvent renommer une phase entiere.
        try (var fichiers = Files.list(MIGRATIONS)) {
            fichiers.filter(p -> p.getFileName().toString().matches("V(2[1-9]|[3-9]\\d)__.*\\.sql"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            Matcher u = UPDATE_LIBELLE.matcher(Files.readString(p, StandardCharsets.UTF_8));
                            while (u.find()) {
                                String nouveau = u.group(1).replace("''", "'");
                                String code = u.group(2);
                                parCode.put(code, new LinkedHashSet<>(Set.of(nouveau)));
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Migration illisible : " + p, e);
                        }
                    });
        }
        return parCode;
    }

    @Test
    @DisplayName("Un code de phase ne porte qu'un seul libelle — sinon l'affichage en perd un")
    void unSeulLibelleParCodeDePhase() throws IOException {
        Map<String, Set<String>> parCode = libellesParPhase();

        assertThat(parCode)
                .as("le referentiel doit declarer au moins les 9 phases du guide")
                .hasSizeGreaterThanOrEqualTo(9);

        parCode.forEach((code, libelles) -> assertThat(libelles)
                .as("Phase %s : l'API regroupe par CODE et ne garde qu'un libelle. "
                        + "En declarer plusieurs en fait disparaitre en silence. Libelles trouves : %s",
                        code, libelles)
                .hasSize(1));
    }

    @Test
    @DisplayName("P4 porte le libelle unique du guide (banniere « CAPITAL ET ENREGISTREMENT »)")
    void phaseP4UnifieeParV23() throws IOException {
        assertThat(libellesParPhase().get("P4"))
                .containsExactly("P4 Capital et enregistrement");
    }

    @Test
    @DisplayName("Le libelle d'une phase commence par son propre code")
    void libelleCoherentAvecLeCode() throws IOException {
        libellesParPhase().forEach((code, libelles) -> {
            String libelle = libelles.iterator().next();
            assertThat(libelle)
                    .as("Phase %s : le libelle affiche doit porter son code, sinon deux phases "
                            + "voisines deviennent indiscernables dans le panneau des demarches", code)
                    .startsWith(code + " ");
        });
    }
}
