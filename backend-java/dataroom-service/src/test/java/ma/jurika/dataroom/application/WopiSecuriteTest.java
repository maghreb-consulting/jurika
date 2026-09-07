package ma.jurika.dataroom.application;

import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WopiSessionEntity;
import ma.jurika.dataroom.infrastructure.persistence.WopiSessionJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WopiVerrouEntity;
import ma.jurika.dataroom.infrastructure.persistence.WopiVerrouJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LE CONTRÔLE D'ACCÈS DES POINTS D'ENTRÉE WOPI.
 *
 * <p>Lot 3 (2026-09-07). Les routes {@code /api/v1/dataroom/wopi/**} sont
 * ouvertes au niveau Spring Security : Collabora appelle le backend sans JWT, le
 * filtre ne peut rien authentifier. Le contrôle n'est pas supprimé, il est
 * DÉPLACÉ dans {@link WopiService} — et un contrôle déplacé doit être prouvé,
 * sinon le {@code permitAll()} est un trou.
 *
 * <p>Ces tests l'établissent en assertions POSITIVES : chacun décrit une
 * tentative précise et vérifie qu'elle est refusée, et — pour les écritures —
 * qu'AUCUN octet n'a été écrit. Un refus qui laisse passer l'effet de bord n'est
 * pas un refus.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WopiSecuriteTest {

    private static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WS_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DOC = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID AUTRE_DOC = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock DocumentJpaRepository documents;
    @Mock WopiSessionJpaRepository sessions;
    @Mock WopiVerrouJpaRepository verrous;
    @Mock DataroomJuridiqueService juridique;
    @Mock ObjectStorage storage;
    @Mock DossierArchiveGuard archiveGuard;
    @Mock ma.jurika.dataroom.infrastructure.wopi.CollaboraDiscovery discovery;

    private WopiService wopi;
    /** Index « empreinte -> séance », comme le ferait la table. */
    private Map<String, WopiSessionEntity> parEmpreinte;
    /** Index « document -> verrou » : la clé primaire de la table V29. */
    private Map<UUID, WopiVerrouEntity> verrouParDocument;

    @BeforeEach
    void setUp() {
        parEmpreinte = new HashMap<>();
        verrouParDocument = new HashMap<>();
        when(discovery.urlEditeurDocx()).thenReturn("http://localhost:9980/browser/abc/cool.html?");
        wopi = new WopiService(documents, sessions, verrous, juridique, storage, archiveGuard,
                discovery, Duration.ofHours(2), Duration.ofMinutes(3),
                "http://dataroom-service:8084", Duration.ofMinutes(30));

        // Le dépôt de verrous se comporte comme la table : un verrou par
        // document, dans son workspace, et rien ne traverse la cloison.
        when(verrous.findByWorkspaceIdAndDocumentId(any(), any())).thenAnswer(inv -> {
            WopiVerrouEntity v = verrouParDocument.get((UUID) inv.getArgument(1));
            return (v != null && v.getWorkspaceId().equals(inv.getArgument(0)))
                    ? Optional.of(v) : Optional.empty();
        });
        when(verrous.findBySessionId(any())).thenAnswer(inv -> verrouParDocument.values().stream()
                .filter(v -> v.getSessionId().equals(inv.getArgument(0))).findFirst());
        when(verrous.save(any(WopiVerrouEntity.class))).thenAnswer(inv -> {
            WopiVerrouEntity v = inv.getArgument(0);
            verrouParDocument.put(v.getDocumentId(), v);
            return v;
        });
        doAnswer(inv -> verrouParDocument.remove(
                ((WopiVerrouEntity) inv.getArgument(0)).getDocumentId()))
                .when(verrous).delete(any(WopiVerrouEntity.class));

        when(sessions.findByTokenHash(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(parEmpreinte.get(inv.getArgument(0))));
        // `save` indexe la séance comme le ferait la table : c'est ce qui permet
        // de vérifier ce qui a réellement été persisté après `ouvrir`.
        when(sessions.save(any(WopiSessionEntity.class))).thenAnswer(inv -> {
            WopiSessionEntity s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            parEmpreinte.put(s.getTokenHash(), s);
            return s;
        });
        // La fermeture de seance passe par l'identifiant, pas par le jeton.
        when(sessions.findById(any())).thenAnswer(inv -> parEmpreinte.values().stream()
                .filter(x -> x.getId().equals(inv.getArgument(0))).findFirst());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── fabriques ────────────────────────────────────────────────────

    private DocumentEntity document(UUID id, UUID workspace, boolean brouillon) {
        DocumentEntity d = new DocumentEntity();
        d.setId(id);
        d.setWorkspaceId(workspace);
        d.setDossierId(UUID.randomUUID());
        d.setTicketId(UUID.randomUUID());
        d.setDocumentType("STATUTS");
        d.setTitle("Statuts");
        d.setFilename("Statuts.docx");
        d.setVersion((short) 1);
        d.setCurrent(!brouillon);
        d.setBrouillon(brouillon);
        d.setObjectKey("ws/" + workspace + "/doc.docx");
        d.setSizeBytes(1234);
        d.setCreatedAt(Instant.now());
        return d;
    }

    /** Enregistre une séance et rend le jeton EN CLAIR, comme le ferait `ouvrir`. */
    private String seance(UUID workspace, UUID documentId, boolean canWrite,
                          Instant expiration, Instant fermeture, Instant graceJusqua) {
        String jeton = "jeton-" + UUID.randomUUID();
        WopiSessionEntity s = new WopiSessionEntity();
        s.setId(UUID.randomUUID());
        s.setTokenHash(WopiService.empreinte(jeton));
        s.setWorkspaceId(workspace);
        s.setDocumentId(documentId);
        s.setUserId(USER);
        s.setUserDisplayName("Karim");
        s.setCanWrite(canWrite);
        s.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        s.setExpiresAt(expiration);
        s.setClosedAt(fermeture);
        s.setGraceJusqua(graceJusqua);
        parEmpreinte.put(s.getTokenHash(), s);
        return jeton;
    }

    private String seanceVivante(UUID workspace, UUID documentId, boolean canWrite) {
        return seance(workspace, documentId, canWrite,
                Instant.now().plus(Duration.ofHours(1)), null, null);
    }

    /** Une séance vivante ouverte par QUELQU'UN D'AUTRE que {@code USER}. */
    private String seanceDe(UUID workspace, UUID documentId, UUID utilisateur, String nom) {
        String jeton = seanceVivante(workspace, documentId, true);
        WopiSessionEntity s = parEmpreinte.get(WopiService.empreinte(jeton));
        s.setUserId(utilisateur);
        s.setUserDisplayName(nom);
        return jeton;
    }

    /** Le dépôt ne rend un document que si le workspace demandé est le sien. */
    private void depotRepond(DocumentEntity... docs) {
        when(documents.findByWorkspaceIdAndId(any(), any())).thenAnswer(inv -> {
            UUID ws = inv.getArgument(0);
            UUID id = inv.getArgument(1);
            for (DocumentEntity d : docs) {
                if (d.getId().equals(id) && d.getWorkspaceId().equals(ws)) return Optional.of(d);
            }
            return Optional.empty();
        });
    }

    /** Pose un verrou déjà en place, comme l'aurait fait la séance d'un collègue. */
    private WopiVerrouEntity verrouEnPlace(UUID workspace, UUID documentId, String lockId,
                                            UUID detenteur, String nom, Instant expiration) {
        WopiVerrouEntity v = new WopiVerrouEntity();
        v.setDocumentId(documentId);
        v.setWorkspaceId(workspace);
        v.setLockId(lockId);
        v.setSessionId(UUID.randomUUID());
        v.setUserId(detenteur);
        v.setUserDisplayName(nom);
        v.setAcquisAt(Instant.now().minus(Duration.ofMinutes(10)));
        v.setExpireAt(expiration);
        verrouParDocument.put(documentId, v);
        return v;
    }

    private void aucuneEcriture() {
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
        verify(juridique, never()).replaceAsNewVersion(any(), any(), anyString(), any());
    }

    // ── 1. Jeton ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Le jeton, et lui seul, ouvre la porte")
    class Jeton {

        @Test
        @DisplayName("Jeton EXPIRÉ : lecture refusée, écriture refusée, rien n'est écrit")
        void jetonExpire() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seance(WS_A, DOC, true,
                    Instant.now().minus(Duration.ofMinutes(1)), null, null);

            assertThatThrownBy(() -> wopi.checkFileInfo(DOC, jeton, null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class)
                    .hasMessageContaining("expiree");
            assertThatThrownBy(() -> wopi.getFile(DOC, jeton))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            assertThatThrownBy(() -> wopi.putFile(DOC, jeton, null, "x".getBytes(), false, false))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            aucuneEcriture();
        }

        @Test
        @DisplayName("Jeton INCONNU : refusé sans que le document soit seulement cherché")
        void jetonInconnu() {
            assertThatThrownBy(() -> wopi.checkFileInfo(DOC, "jeton-invente", null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class)
                    .hasMessageContaining("inconnu");
            verify(documents, never()).findByWorkspaceIdAndId(any(), any());
        }

        @Test
        @DisplayName("Le controle d'acces passe AVANT la validation du contenu")
        void accesAvantContenu() {
            // Sinon un jeton invalide recoit une reponse differente selon le
            // corps envoye, ce qui renseigne sur ce qui a echoue.
            assertThatThrownBy(() -> wopi.putFile(DOC, "jeton-invente", null, new byte[0], false, false))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            aucuneEcriture();
        }

        @Test
        @DisplayName("Jeton ABSENT : refusé")
        void jetonAbsent() {
            assertThatThrownBy(() -> wopi.checkFileInfo(DOC, null, null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            assertThatThrownBy(() -> wopi.checkFileInfo(DOC, "   ", null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
        }

        @Test
        @DisplayName("Le jeton n'est jamais stocké en clair : la séance ne porte que son empreinte")
        void jetonJamaisEnClair() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            TenantContext.set(WS_A);
            when(documents.findByWorkspaceIdAndId(WS_A, DOC)).thenReturn(Optional.of(d));

            WopiService.SeanceEdition s = wopi.ouvrir(DOC, USER, "Karim", Role.EMPLOYE);

            assertThat(s.accessToken()).isNotBlank();
            WopiSessionEntity enBase = parEmpreinte.values().stream().findFirst().orElse(null);
            // La séance enregistrée ne contient nulle part le jeton lui-même.
            assertThat(enBase).isNotNull();
            assertThat(enBase.getTokenHash())
                    .isEqualTo(WopiService.empreinte(s.accessToken()))
                    .isNotEqualTo(s.accessToken())
                    .hasSize(64);
        }
    }

    // ── 2. Cloisonnement multi-tenant ────────────────────────────────

    @Nested
    @DisplayName("Le cloisonnement multi-tenant est vérifié à chaque appel")
    class MultiTenant {

        @Test
        @DisplayName("Jeton d'un AUTRE workspace : refusé, et rien n'est écrit")
        void jetonAutreWorkspace() {
            // Le document appartient au workspace A, la séance au workspace B.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jetonB = seanceVivante(WS_B, DOC, true);

            assertThatThrownBy(() -> wopi.checkFileInfo(DOC, jetonB, null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class)
                    .hasMessageContaining("hors du workspace");
            assertThatThrownBy(() -> wopi.getFile(DOC, jetonB))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            assertThatThrownBy(() -> wopi.putFile(DOC, jetonB, null, "x".getBytes(), false, false))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            aucuneEcriture();
        }

        @Test
        @DisplayName("La relecture du document passe TOUJOURS par le workspace de la séance")
        void relectureScopeeAuWorkspace() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.checkFileInfo(DOC, jeton, null);

            // Jamais findById(id) seul : le filtre applicatif est explicite,
            // parce que jurika_user a BYPASSRLS et que la RLS ne filtrera pas.
            verify(documents).findByWorkspaceIdAndId(WS_A, DOC);
            verify(documents, never()).findById(any());
        }
    }

    // ── 3. fileId ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Le fileId de l'URL ne porte aucune autorité")
    class FileId {

        @Test
        @DisplayName("fileId ne correspondant pas au jeton : refusé, et rien n'est écrit")
        void fileIdNeCorrespondPas() {
            DocumentEntity autre = document(AUTRE_DOC, WS_A, true);
            depotRepond(autre);
            // Séance ouverte sur DOC, appel sur AUTRE_DOC — même workspace.
            String jeton = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.checkFileInfo(AUTRE_DOC, jeton, null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class)
                    .hasMessageContaining("ne correspond pas");
            assertThatThrownBy(() -> wopi.getFile(AUTRE_DOC, jeton))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            assertThatThrownBy(() -> wopi.putFile(AUTRE_DOC, jeton, null, "x".getBytes(), false, false))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            aucuneEcriture();
            // Le document visé n'a même pas été lu : le refus est antérieur.
            verify(documents, never()).findByWorkspaceIdAndId(any(), any());
        }
    }

    // ── 4. Droit d'écriture ──────────────────────────────────────────

    @Nested
    @DisplayName("UserCanWrite reflète le RBAC, pas un bouton masqué")
    class DroitEcriture {

        @Test
        @DisplayName("Séance en LECTURE SEULE : PutFile refusé, aucun octet écrit")
        void lectureSeulePutRefuse() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seanceVivante(WS_A, DOC, false);

            assertThatThrownBy(() -> wopi.putFile(DOC, jeton, null, "modifie".getBytes(), false, false))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            aucuneEcriture();
        }

        @Test
        @DisplayName("Séance en lecture seule : la LECTURE reste possible, et CheckFileInfo le dit")
        void lectureSeuleLectureOk() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(storage.download(anyString())).thenReturn(
                    new ObjectStorage.DownloadResult(new ByteArrayInputStream(new byte[] {1}), 1, "x"));
            String jeton = seanceVivante(WS_A, DOC, false);

            WopiService.CheckFileInfo info = wopi.checkFileInfo(DOC, jeton, "http://localhost");

            assertThat(info.UserCanWrite()).isFalse();
            assertThat(info.SupportsUpdate()).isFalse();
            assertThat(wopi.getFile(DOC, jeton)).isNotNull();
        }

        @Test
        @DisplayName("Un SUPERVISEUR n'obtient jamais une séance en écriture")
        void superviseurEnLectureSeule() {
            DocumentEntity d = document(DOC, WS_A, false);
            TenantContext.set(WS_A);
            when(documents.findByWorkspaceIdAndId(WS_A, DOC)).thenReturn(Optional.of(d));

            assertThat(wopi.ouvrir(DOC, USER, "Sup", Role.SUPERVISEUR).canWrite()).isFalse();
            assertThat(wopi.ouvrir(DOC, USER, "Adm", Role.SUPER_ADMIN).canWrite()).isFalse();
            assertThat(wopi.ouvrir(DOC, USER, "Emp", Role.EMPLOYE).canWrite()).isTrue();
        }
    }

    // ── 5. Enregistrement tardif ─────────────────────────────────────

    @Nested
    @DisplayName("L'enregistrement tardif — le piège de la perte silencieuse")
    class EnregistrementTardif {

        @Test
        @DisplayName("Séance FERMÉE, dans la fenêtre de grâce : PutFile ACCEPTÉ")
        void tardifDansLaGraceAccepte() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            String jeton = seance(WS_A, DOC, true,
                    Instant.now().plus(Duration.ofHours(1)),
                    Instant.now().minus(Duration.ofSeconds(30)),   // fermée il y a 30 s
                    Instant.now().plus(Duration.ofMinutes(2)));    // grâce encore ouverte

            UUID ecritSur = wopi.putFile(DOC, jeton, null, "dernier mot".getBytes(), false, true);

            assertThat(ecritSur).isEqualTo(DOC);
            verify(storage).upload(anyString(), any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Séance fermée, grâce ÉCOULÉE : PutFile refusé")
        void tardifHorsGraceRefuse() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seance(WS_A, DOC, true,
                    Instant.now().plus(Duration.ofHours(1)),
                    Instant.now().minus(Duration.ofMinutes(10)),
                    Instant.now().minus(Duration.ofMinutes(7)));

            assertThatThrownBy(() -> wopi.putFile(DOC, jeton, null, "trop tard".getBytes(), false, true))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class)
                    .hasMessageContaining("grace");
            aucuneEcriture();
        }

        @Test
        @DisplayName("Séance fermée : la LECTURE est coupée aussitôt, sans attendre la grâce")
        void lectureCoupeeDesLaFermeture() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seance(WS_A, DOC, true,
                    Instant.now().plus(Duration.ofHours(1)),
                    Instant.now().minus(Duration.ofSeconds(5)),
                    Instant.now().plus(Duration.ofMinutes(2)));

            assertThatThrownBy(() -> wopi.checkFileInfo(DOC, jeton, null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            assertThatThrownBy(() -> wopi.getFile(DOC, jeton))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
        }
    }

    // ── 6. Ce que PutFile écrit ──────────────────────────────────────

    @Nested
    @DisplayName("PutFile : une version juridique par séance, jamais une par enregistrement")
    class Versionnement {

        @Test
        @DisplayName("Brouillon : chaque PutFile met à jour la copie de travail, hors lignage")
        void brouillonMisAJourEnPlace() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.putFile(DOC, jeton, null, "v1".getBytes(), true, false);
            wopi.putFile(DOC, jeton, null, "v2".getBytes(), true, false);
            wopi.putFile(DOC, jeton, null, "v3".getBytes(), false, true);

            // Trois écritures d'objet, AUCUNE version juridique créée.
            verify(storage, org.mockito.Mockito.times(3))
                    .upload(anyString(), any(), anyLong(), anyString());
            verify(juridique, never()).replaceAsNewVersion(any(), any(), anyString(), any());
            assertThat(d.getEditeManuellementAt()).isNotNull();
            assertThat(d.getEditePar()).isEqualTo(USER);
        }

        @Test
        @DisplayName("Document validé : le 1er PutFile crée UNE version, les suivants la mettent à jour")
        void uneVersionParSeance() {
            DocumentEntity valide = document(DOC, WS_A, false);
            UUID idNouvelleVersion = UUID.randomUUID();
            DocumentEntity nouvelle = document(idNouvelleVersion, WS_A, false);
            nouvelle.setVersion((short) 2);
            depotRepond(valide, nouvelle);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(juridique.replaceAsNewVersion(any(), any(), anyString(), any()))
                    .thenReturn(new ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary(
                            idNouvelleVersion, valide.getDossierId(), valide.getTicketId(),
                            "STATUTS", "Statuts", (short) 2, true,
                            "Statuts.docx", "docx", 10, Instant.now(), null, null));
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.putFile(DOC, jeton, null, "premiere frappe".getBytes(), true, false);
            wopi.putFile(DOC, jeton, null, "seconde frappe".getBytes(), true, false);
            wopi.putFile(DOC, jeton, null, "derniere frappe".getBytes(), false, true);

            // UNE seule nouvelle version juridique pour trois enregistrements.
            verify(juridique, org.mockito.Mockito.times(1))
                    .replaceAsNewVersion(any(), any(), anyString(), any());
            assertThat(nouvelle.getEditeManuellementAt()).isNotNull();
            assertThat(nouvelle.getEditePar()).isEqualTo(USER);
        }

        @Test
        @DisplayName("Après un PutFile, GetFile sert la version de la SÉANCE, pas la ligne d'origine")
        void relectureServeLaVersionDeLaSeance() {
            // Sans cela, une reconnexion de Collabora en cours de séance
            // rechargerait le contenu d'AVANT l'édition et l'écraserait au
            // PutFile suivant — une perte silencieuse de plus.
            DocumentEntity valide = document(DOC, WS_A, false);
            valide.setObjectKey("ws/A/avant.docx");
            UUID idV2 = UUID.randomUUID();
            DocumentEntity v2 = document(idV2, WS_A, false);
            v2.setVersion((short) 2);
            v2.setObjectKey("ws/A/apres.docx");
            depotRepond(valide, v2);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(juridique.replaceAsNewVersion(any(), any(), anyString(), any()))
                    .thenReturn(new ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary(
                            idV2, valide.getDossierId(), valide.getTicketId(),
                            "STATUTS", "Statuts", (short) 2, true,
                            "Statuts.docx", "docx", 10, Instant.now(), null, null));
            when(storage.download(anyString())).thenAnswer(inv ->
                    new ObjectStorage.DownloadResult(
                            new ByteArrayInputStream(new byte[] {1}), 1, inv.getArgument(0)));
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.putFile(DOC, jeton, null, "edite".getBytes(), false, false);
            ObjectStorage.DownloadResult relu = wopi.getFile(DOC, jeton);

            // Le contentType porte ici la clé demandée (cf. le mock) : on vérifie
            // que c'est bien l'objet de la VERSION qui a été lu.
            assertThat(relu.contentType()).isEqualTo(v2.getObjectKey());
            assertThat(wopi.checkFileInfo(DOC, jeton, null).Size()).isEqualTo(v2.getSizeBytes());
        }
    }

    // ── 7. Verrous d'édition (lot 4) ─────────────────────────

    /**
     * DEUX EMPLOYÉS SUR LE MÊME ACTE.
     *
     * <p>Avant le lot 4, chacun éditait la copie chargée dans son navigateur et
     * le dernier à enregistrer écrasait le travail du premier — sans erreur, sans
     * message, sans trace. La version juridique finale ne portait qu'une des deux
     * séries de corrections et rien ne disait laquelle avait disparu.
     *
     * <p>Comme pour les jetons, chaque test décrit une tentative précise, vérifie
     * qu'elle est refusée, ET qu'aucun octet n'a été écrit. Un refus qui laisse
     * passer l'effet de bord n'est pas un refus.
     */
    @Nested
    @DisplayName("Deux employes ne s'ecrasent pas l'un l'autre")
    class Verrous {

        private static final UUID AUTRE_USER =
                UUID.fromString("66666666-6666-6666-6666-666666666666");

        @Test
        @DisplayName("Verrou tenu par un AUTRE : PutFile refuse, et AUCUN octet n'est ecrit")
        void verrouDUnAutreRefusePutFile() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            String jeton = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.putFile(DOC, jeton, "verrou-de-karim", "mes corrections".getBytes(), false, false))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class);
            aucuneEcriture();
        }

        @Test
        @DisplayName("Sans en-tete de verrou non plus : un PutFile aveugle n'ecrase rien")
        void putFileSansVerrouRefuseQuandUnAutreTientLActe() {
            // Le cas réel : un client WOPI qui n'envoie pas X-WOPI-Lock. Il ne
            // doit pas passer devant celui qui a posé son verrou.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            String jeton = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.putFile(DOC, jeton, null, "aveugle".getBytes(), false, false))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class);
            aucuneEcriture();
        }

        @Test
        @DisplayName("Le conflit REND le verrou en place et NOMME son detenteur")
        void leConflitPorteLeVerrouEnPlace() {
            // Sans l'identifiant en place, l'editeur ne peut pas basculer en
            // lecture seule : il croirait que l'enregistrement a eu lieu.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            String jeton = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.lock(DOC, jeton, "verrou-de-karim", null))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class)
                    .extracting(e -> ((WopiService.WopiVerrouConflit) e).verrouCourant())
                    .isEqualTo("verrou-de-sara");
            assertThatThrownBy(() -> wopi.lock(DOC, jeton, "verrou-de-karim", null))
                    .extracting(e -> ((WopiService.WopiVerrouConflit) e).detenteur())
                    .isEqualTo("Sara");
        }

        @Test
        @DisplayName("Verrou EXPIRE : repris, et l'ecriture passe")
        void verrouExpireEstRepris() {
            // Un navigateur qui plante ne relache rien. Sans reprise, l'acte
            // resterait bloque jusqu'a une intervention manuelle.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            verrouEnPlace(WS_A, DOC, "verrou-abandonne", AUTRE_USER, "Sara",
                    Instant.now().minus(Duration.ofMinutes(1)));
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.lock(DOC, jeton, "verrou-de-karim", null);

            assertThat(wopi.getLock(DOC, jeton)).isEqualTo("verrou-de-karim");
            wopi.putFile(DOC, jeton, "verrou-de-karim", "mes corrections".getBytes(), false, false);
            verify(storage).upload(anyString(), any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Avec le BON verrou, l'ecriture passe")
        void leBonVerrouEcrit() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.lock(DOC, jeton, "verrou-de-karim", null);
            wopi.putFile(DOC, jeton, "verrou-de-karim", "mes corrections".getBytes(), false, false);

            verify(storage).upload(anyString(), any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Reposer SON PROPRE verrou reussit et le prolonge")
        void reposerSonPropreVerrouReussit() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.lock(DOC, jeton, "verrou-de-karim", null);
            Instant premiere = verrouParDocument.get(DOC).getExpireAt();
            wopi.lock(DOC, jeton, "verrou-de-karim", null);

            assertThat(verrouParDocument.get(DOC).getExpireAt())
                    .isAfterOrEqualTo(premiere);
            assertThat(verrouParDocument.get(DOC).getLockId()).isEqualTo("verrou-de-karim");
        }

        @Test
        @DisplayName("RefreshLock d'un verrou qui n'est pas le sien : refuse, le verrou ne bouge pas")
        void refreshDUnVerrouDAutrui() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            WopiVerrouEntity pose = verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            Instant expirationInitiale = pose.getExpireAt();
            String jeton = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.refreshLock(DOC, jeton, "verrou-de-karim"))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class);
            assertThat(verrouParDocument.get(DOC).getExpireAt()).isEqualTo(expirationInitiale);
            assertThat(verrouParDocument.get(DOC).getLockId()).isEqualTo("verrou-de-sara");
        }

        @Test
        @DisplayName("Unlock avec le mauvais identifiant : refuse, le verrou reste en place")
        void unlockDAutruiRefuse() {
            // Relacher le verrou d'un autre rouvrirait exactement la porte que
            // ce lot ferme.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            String jeton = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.unlock(DOC, jeton, "verrou-de-karim"))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class);
            assertThat(verrouParDocument).containsKey(DOC);
        }

        @Test
        @DisplayName("Unlock avec le bon identifiant : le verrou disparait")
        void unlockLegitime() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.lock(DOC, jeton, "verrou-de-karim", null);
            wopi.unlock(DOC, jeton, "verrou-de-karim");

            assertThat(verrouParDocument).doesNotContainKey(DOC);
            assertThat(wopi.getLock(DOC, jeton)).isEmpty();
        }

        @Test
        @DisplayName("UnlockAndRelock : l'ancien verrou doit correspondre")
        void unlockAndRelock() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seanceVivante(WS_A, DOC, true);
            wopi.lock(DOC, jeton, "verrou-1", null);

            wopi.lock(DOC, jeton, "verrou-2", "verrou-1");
            assertThat(verrouParDocument.get(DOC).getLockId()).isEqualTo("verrou-2");

            assertThatThrownBy(() -> wopi.lock(DOC, jeton, "verrou-3", "verrou-inconnu"))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class);
            assertThat(verrouParDocument.get(DOC).getLockId()).isEqualTo("verrou-2");
        }

        @Test
        @DisplayName("Un verrou d'un AUTRE workspace ne se voit pas et ne bloque pas")
        void leVerrouNeTraversePasLaCloison() {
            // La RLS ne filtre rien ici : jurika_user a BYPASSRLS. Le
            // cloisonnement est verifie explicitement, verrous compris.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            verrouEnPlace(WS_B, DOC, "verrou-d-un-autre-cabinet", AUTRE_USER, "Inconnu",
                    Instant.now().plus(Duration.ofMinutes(20)));
            String jeton = seanceVivante(WS_A, DOC, true);

            wopi.lock(DOC, jeton, "verrou-de-karim", null);
            wopi.putFile(DOC, jeton, "verrou-de-karim", "mes corrections".getBytes(), false, false);

            verify(storage).upload(anyString(), any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("La seance s'ouvre en LECTURE SEULE et nomme qui detient l'acte")
        void secondeSeanceEnLectureSeule() {
            // On le dit AVANT d'ouvrir l'editeur : decouvrir en fermant que son
            // travail n'a pas ete garde serait la pire facon de l'apprendre.
            DocumentEntity d = document(DOC, WS_A, false);
            depotRepond(d);
            verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            TenantContext.set(WS_A);

            WopiService.SeanceEdition seance = wopi.ouvrir(DOC, USER, "Karim", Role.EMPLOYE);

            assertThat(seance.canWrite()).isFalse();
            assertThat(seance.verrouPar()).isEqualTo("Sara");
            assertThat(seance.verrouDepuis()).isNotNull();
        }

        @Test
        @DisplayName("Fermer la seance relache son verrou : l'acte n'est pas bloque pour le suivant")
        void fermerRelacheLeVerrou() {
            DocumentEntity d = document(DOC, WS_A, false);
            depotRepond(d);
            TenantContext.set(WS_A);
            WopiService.SeanceEdition seance = wopi.ouvrir(DOC, USER, "Karim", Role.EMPLOYE);
            wopi.lock(DOC, seance.accessToken(), "verrou-de-karim", null);
            assertThat(verrouParDocument).containsKey(DOC);

            wopi.fermer(seance.sessionId(), USER);

            assertThat(verrouParDocument).doesNotContainKey(DOC);
        }

        @Test
        @DisplayName("L'enregistrement tardif passe encore : le verrou est parti avec la seance")
        void enregistrementTardifApresRelachement() {
            // Refuser ici ferait perdre la derniere sauvegarde de Collabora --
            // la meme perte silencieuse, prise par l'autre bout.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            String jeton = seance(WS_A, DOC, true,
                    Instant.now().plus(Duration.ofHours(1)),
                    Instant.now().minus(Duration.ofSeconds(1)),
                    Instant.now().plus(Duration.ofMinutes(2)));

            wopi.putFile(DOC, jeton, "verrou-relache", "dernier mot".getBytes(), false, true);

            verify(storage).upload(anyString(), any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Un verrou sans identifiant est refuse")
        void verrouSansIdentifiant() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jeton = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.lock(DOC, jeton, "  ", null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            assertThat(verrouParDocument).doesNotContainKey(DOC);
        }

        @Test
        @DisplayName("Un jeton d'un AUTRE workspace ne peut pas poser de verrou")
        void jetonDUnAutreWorkspaceNeVerrouillePas() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            String jetonB = seanceVivante(WS_B, DOC, true);

            assertThatThrownBy(() -> wopi.lock(DOC, jetonB, "verrou-intrus", null))
                    .isInstanceOf(WopiService.WopiAccesRefuse.class);
            assertThat(verrouParDocument).doesNotContainKey(DOC);
        }

        @Test
        @DisplayName("Connaitre l'identifiant du verrou ne suffit pas a le relacher")
        void identifiantConnuNeSuffitPas() {
            // GetLock PUBLIE l'identifiant du verrou -- le protocole l'exige.
            // Verifie dans l'application : un second employe pouvait le lire,
            // s'en servir pour relacher le verrou du premier, prendre sa place,
            // et ecraser son travail. L'identifiant designe une seance ; il ne
            // prouve rien.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            String jetonKarim = seanceVivante(WS_A, DOC, true);

            // Karim lit l'identifiant, comme n'importe quel client WOPI.
            assertThat(wopi.getLock(DOC, jetonKarim)).isEqualTo("verrou-de-sara");

            assertThatThrownBy(() -> wopi.unlock(DOC, jetonKarim, "verrou-de-sara"))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class);
            assertThat(verrouParDocument).containsKey(DOC);
            assertThat(verrouParDocument.get(DOC).getUserId()).isEqualTo(AUTRE_USER);
        }

        @Test
        @DisplayName("Ecrire avec l'identifiant du verrou d'un autre : refuse, rien n'est ecrit")
        void ecrireAvecLIdentifiantDUnAutre() {
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            verrouEnPlace(WS_A, DOC, "verrou-de-sara", AUTRE_USER, "Sara",
                    Instant.now().plus(Duration.ofMinutes(20)));
            String jetonKarim = seanceVivante(WS_A, DOC, true);

            assertThatThrownBy(() -> wopi.putFile(DOC, jetonKarim, "verrou-de-sara",
                    "je passe devant".getBytes(), false, false))
                    .isInstanceOf(WopiService.WopiVerrouConflit.class);
            aucuneEcriture();
        }

        @Test
        @DisplayName("Le MEME employe qui rouvre apres une coupure retrouve son verrou")
        void memeEmployeNouvelleSeance() {
            // La regle protege d'un AUTRE, pas de soi-meme : sinon une coupure
            // reseau bloquerait l'employe hors de son propre acte jusqu'a
            // l'expiration.
            DocumentEntity d = document(DOC, WS_A, true);
            depotRepond(d);
            when(documents.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
            String premiere = seanceDe(WS_A, DOC, AUTRE_USER, "Sara");
            wopi.lock(DOC, premiere, "verrou-de-sara", null);

            String seconde = seanceDe(WS_A, DOC, AUTRE_USER, "Sara");
            wopi.refreshLock(DOC, seconde, "verrou-de-sara");
            wopi.putFile(DOC, seconde, "verrou-de-sara", "la suite".getBytes(), false, false);

            verify(storage).upload(anyString(), any(), anyLong(), anyString());
        }
    }
}
