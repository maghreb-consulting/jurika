package ma.jurika.dataroom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheClientView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheDossierView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Identifiant;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture.TicketLigne;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture.TraceLigne;
import ma.jurika.dataroom.infrastructure.persistence.FicheSocieteLecture.VersionTpLigne;
import ma.jurika.dataroom.infrastructure.persistence.UserViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewJpaRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.File;
import java.time.Clock;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L5 (E7) : fiche client et fiche dossier -- acces, contenu (identifiants seuls dans la
 * fiche client, operations dans la fiche dossier), provenance lue dans la trace, versions de
 * la taxe professionnelle, rendu PDF a la charte lu (lot L5b E3 : plus de Word), conservation
 * dans la Data Room. Les PDF et leurs pages en PNG sont ranges dans target/output/l5b-pdf.
 */
class FicheClientServiceTest {

    private final DossierViewJpaRepository dossiers = mock(DossierViewJpaRepository.class);
    private final WorkflowProgressViewJpaRepository workflows = mock(WorkflowProgressViewJpaRepository.class);
    private final UserViewJpaRepository users = mock(UserViewJpaRepository.class);
    private final WorkspaceViewJpaRepository workspaces = mock(WorkspaceViewJpaRepository.class);
    private final FicheSocieteLecture lecture = mock(FicheSocieteLecture.class);
    private final DataroomJuridiqueService juridique = mock(DataroomJuridiqueService.class);
    private final DossierArchiveGuard archiveGuard = mock(DossierArchiveGuard.class);

    private FicheClientService service;

    private final UUID ws = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();
    private final UUID responsable = UUID.randomUUID();
    private final UUID autreEmploye = UUID.randomUUID();
    private final UUID ticketCreation = UUID.randomUUID();
    private final UUID ticketModif = UUID.randomUUID();
    private final UUID ticketAnnule = UUID.randomUUID();

    private static final Instant J1 = Instant.parse("2026-01-10T10:00:00Z");
    private static final Instant J2 = Instant.parse("2026-03-05T10:00:00Z");
    private static final Instant J3 = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant J4 = Instant.parse("2026-07-01T10:00:00Z");
    /** Horloge figee : 13/10/2026 a Casablanca. */
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-13T09:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new FicheClientService(dossiers, workflows, users, workspaces, lecture, juridique,
                archiveGuard, new ObjectMapper(), HORLOGE);
        TenantContext.set(ws);
        WorkspaceViewEntity w = mock(WorkspaceViewEntity.class);
        lenient().when(w.getName()).thenReturn("Maghreb Consulting");
        lenient().when(workspaces.findById(ws)).thenReturn(Optional.of(w));
        DossierViewEntity d = dossier();
        lenient().when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        UserViewEntity u1 = user(responsable, "Salma Alaoui");
        UserViewEntity u2 = user(autreEmploye, "Karim Bennani");
        lenient().when(users.findAllByWorkspaceIdAndIdIn(eq(ws), any())).thenReturn(List.of(u1, u2));
        lenient().when(workflows.findRowsByWorkspaceAndTickets(any(), any())).thenReturn(List.of());
        lenient().when(lecture.typeDuTicketOrigine(ws, dossierId)).thenReturn("IMPORT");
        lenient().when(lecture.versionsTp(ws, dossierId)).thenReturn(List.of(
                new VersionTpLigne("TP-1111", null, responsable, J1),
                new VersionTpLigne("TP-2222", LocalDate.of(2026, 3, 1), responsable, J2)));
        lenient().when(lecture.traces(ws, dossierId)).thenReturn(List.of(
                new TraceLigne("raison_sociale", null, "ACME SARL", "OUVERTURE", null, null, J1),
                new TraceLigne("ice", null, "001234567000089", "OUVERTURE", null, null, J1),
                new TraceLigne("rc_numero", null, "45678", "FIN_PARCOURS", ticketCreation, responsable, J2),
                new TraceLigne("identifiant_fiscal", null, "IF-77", "EXTRAITE", ticketCreation, responsable, J2),
                new TraceLigne("taxe_professionnelle", "TP-1111", "TP-2222", "SAISIE", null, responsable, J2),
                new TraceLigne("capital_social_mad", "100000.00", "250000.00", "PARCOURS", ticketModif, null, J3)));
        TicketLigne creation = new TicketLigne(ticketCreation, "T-0001", "CREATION", "CLOTURE_DOSSIER",
                J1, J2, null, responsable);
        TicketLigne modif = new TicketLigne(ticketModif, "T-0002", "MODIFICATION", "DEROULEMENT_DEMARCHE",
                J3, null, null, autreEmploye);
        TicketLigne annule = new TicketLigne(ticketAnnule, "T-0003", "PV_AGO", "ANNULE", J3, null, J4, null);
        lenient().when(lecture.ticketsParId(eq(ws), any())).thenReturn(Map.of(
                ticketCreation, creation, ticketModif, modif));
        lenient().when(lecture.tickets(ws, dossierId)).thenReturn(List.of(creation, modif, annule));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ acces

    @Test
    void employe_non_responsable_refuse() {
        assertThatThrownBy(() -> service.ficheClient(dossierId, Role.EMPLOYE, autreEmploye))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.genererFicheDossier(dossierId, Role.EMPLOYE, autreEmploye))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void client_refuse_meme_en_service() {
        assertThatThrownBy(() -> service.ficheDossier(dossierId, Role.CLIENT, responsable))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superviseur_lit_sans_pouvoir_modifier_et_le_responsable_peut_modifier() {
        assertThat(service.ficheClient(dossierId, Role.SUPERVISEUR, autreEmploye).modifiable()).isFalse();
        assertThat(service.ficheClient(dossierId, Role.EMPLOYE, responsable).modifiable()).isTrue();
    }

    // ------------------------------------------------------------------ contenu

    @Test
    void fiche_client_porte_les_neuf_identifiants_et_leur_provenance() {
        FicheClientView f = service.ficheClient(dossierId, Role.EMPLOYE, responsable);
        Map<String, Identifiant> ids = f.identifiants().stream()
                .collect(Collectors.toMap(Identifiant::code, Function.identity()));

        assertThat(ids.keySet()).containsExactlyInAnyOrder(
                "DENOMINATION", "FORME", "CAPITAL", "SIEGE", "RC", "ICE", "IF", "TP", "CNSS");
        assertThat(ids.get("DENOMINATION").provenance()).isEqualTo("Reprise à l’import du dossier");
        assertThat(ids.get("RC").valeur()).isEqualTo("45678 (tribunal : Casablanca)");
        assertThat(ids.get("RC").provenance()).isEqualTo("Obtenu en fin de parcours (ticket T-0001)");
        assertThat(ids.get("IF").provenance()).isEqualTo("Lu sur une pièce et confirmé (ticket T-0001)");
        assertThat(ids.get("TP").valeur()).isEqualTo("TP-2222");
        assertThat(ids.get("TP").provenance()).isEqualTo("Saisie manuelle par Salma Alaoui, le 05/03/2026");
        assertThat(ids.get("CAPITAL").valeur()).isEqualTo("250 000,00 dirhams");
        assertThat(ids.get("CAPITAL").provenance())
                .isEqualTo("Parcours « Modification statutaire » (ticket T-0002)");
        assertThat(ids.get("SIEGE").origine()).as("aucune trace : jamais attribuee").isEqualTo("NON_TRACEE");
        assertThat(ids.get("CNSS").valeur()).isNull();
        assertThat(ids.get("CNSS").provenance()).isEqualTo("Donnée manquante");
    }

    @Test
    void une_trace_qui_ne_porte_pas_la_valeur_en_vigueur_ne_donne_pas_sa_provenance() {
        // L'ICE en vigueur differe de celle tracee : sa provenance n'est pas connue.
        DossierViewEntity d = dossier();
        when(d.getIce()).thenReturn("009999999000011");
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        Identifiant ice = service.ficheClient(dossierId, Role.EMPLOYE, responsable).identifiants().stream()
                .filter(i -> "ICE".equals(i.code())).findFirst().orElseThrow();
        assertThat(ice.origine()).isEqualTo("NON_TRACEE");
    }

    @Test
    void versions_de_tp_toutes_presentes_la_derniere_en_vigueur_date_inconnue_vide() {
        FicheClientView f = service.ficheClient(dossierId, Role.EMPLOYE, responsable);
        assertThat(f.versionsTp()).hasSize(2);
        assertThat(f.versionsTp().get(0).dateEffet()).isNull();
        assertThat(f.versionsTp().get(0).enVigueur()).isFalse();
        assertThat(f.versionsTp().get(1).enVigueur()).isTrue();
        assertThat(f.versionsTp().get(1).dateEffet()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void trace_des_modifications_la_plus_recente_d_abord_avec_ancienne_et_nouvelle_valeur() {
        FicheClientView f = service.ficheClient(dossierId, Role.SUPERVISEUR, autreEmploye);
        assertThat(f.modifications()).hasSize(6);
        assertThat(f.modifications().get(0).champ()).isEqualTo("capital_social_mad");
        assertThat(f.modifications().get(1).ancienneValeur()).isEqualTo("TP-1111");
        assertThat(f.modifications().get(1).nouvelleValeur()).isEqualTo("TP-2222");
        assertThat(f.modifications().get(1).auteur()).isEqualTo("Salma Alaoui");
    }

    @Test
    void fiche_dossier_toutes_les_operations_annulees_comprises() {
        FicheDossierView v = service.ficheDossier(dossierId, Role.EMPLOYE, responsable);
        assertThat(v.operations()).extracting(Operation::reference).containsExactly("T-0001", "T-0002", "T-0003");
        Operation creation = v.operations().get(0);
        assertThat(creation.natureFin()).isEqualTo("CLOTURE");
        assertThat(creation.dateFin()).isEqualTo(J2);
        assertThat(creation.responsable()).isEqualTo("Salma Alaoui");
        Operation modif = v.operations().get(1);
        assertThat(modif.dateFin()).isNull();
        assertThat(modif.responsable()).isEqualTo("Karim Bennani");
        Operation annule = v.operations().get(2);
        assertThat(annule.natureFin()).isEqualTo("ANNULATION");
        assertThat(annule.dateFin()).isEqualTo(J4);
        assertThat(annule.statutLabel()).isEqualTo("Annulé");
        assertThat(annule.responsable()).isEqualTo("Non renseigné");
        assertThat(v.derniereCloture()).isEqualTo(J2);
    }

    // ------------------------------------------------------------------ PDF

    @Test
    void fiche_client_pdf_lue_identifiants_seuls_aucun_historique() throws Exception {
        when(juridique.uploadVersion(eq(dossierId), anyString(), anyString(), isNull(), any(MultipartFile.class),
                eq(responsable))).thenReturn(resume((short) 1));
        byte[] pdf = service.genererFicheClient(dossierId, Role.EMPLOYE, responsable).contenu();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String texte = texte(pdf, "fiche-client");

        assertThat(texte).contains("Fiche client", "Maghreb Consulting", "ACME SARL",
                "Société à responsabilité limitée (SARL)", "250 000,00 dirhams",
                "45678 (tribunal : Casablanca)", "001234567000089", "IF-77", "TP-2222", "TP-1111",
                "En vigueur", "Remplacée", "01/03/2026", "Non renseignée",
                "Saisie manuelle par Salma Alaoui, le 05/03/2026", "Donnée manquante");
        assertThat(texte).contains("Identifiants de la soci\u00e9t\u00e9 en vigueur au 13/10/2026",
                "Provenance : Reprise \u00e0 l'import du dossier", "\u00c9dit\u00e9 par JURIKA le 13/10/2026",
                "Provenance non trac\u00e9e (valeur ant\u00e9rieure au suivi)");
        assertThat(texte).doesNotContain("Provenance : Provenance", "\u02bc");
        // RG-FIC-01 : aucun historique (ni operations, ni trace des modifications).
        assertThat(texte).doesNotContain("T-0003", "Op\u00e9rations juridiques", "Annul\u00e9 le",
                "Cl\u00f4tur\u00e9 le", "Karim", "TP-1111 \u2192", "Ancienne valeur", "100000");
        assertThat(texte).doesNotContain("$");
    }

    @Test
    void fiche_dossier_pdf_lue_identifiants_et_operations() throws Exception {
        when(juridique.uploadVersion(eq(dossierId), anyString(), anyString(), isNull(), any(MultipartFile.class),
                eq(responsable))).thenReturn(resume((short) 2));
        byte[] pdf = service.genererFicheDossier(dossierId, Role.EMPLOYE, responsable).contenu();
        String texte = texte(pdf, "fiche-dossier").replace('\n', ' ');

        assertThat(texte).contains("Fiche dossier", "ACME SARL", "TP-2222",
                "Opérations juridiques du dossier",
                "À jour de la dernière clôture, le 05/03/2026.",
                "Création de société", "T-0001", "10/01/2026", "Clôturé le 05/03/2026",
                "Modification statutaire", "T-0002", "En cours", "Karim Bennani",
                "Assemblée générale ordinaire", "T-0003", "Annulé le 01/07/2026");
        assertThat(texte).doesNotContain("$");
    }

    @Test
    void rendu_deterministe_memes_donnees_memes_octets() throws Exception {
        byte[] a = service.genererFicheDossier(dossierId, Role.SUPERVISEUR, autreEmploye).contenu();
        Thread.sleep(1100); // l'horloge du systeme avance : aucun effet sur le document
        byte[] b = service.genererFicheDossier(dossierId, Role.SUPERVISEUR, autreEmploye).contenu();
        assertThat(a).isEqualTo(b);
        byte[] c = service.genererFicheClient(dossierId, Role.SUPERVISEUR, autreEmploye).contenu();
        byte[] d = service.genererFicheClient(dossierId, Role.SUPERVISEUR, autreEmploye).contenu();
        assertThat(c).isEqualTo(d);
    }

    @Test
    void fiche_client_et_fiche_dossier_sont_deux_documents_distincts() throws Exception {
        String client = texte(service.genererFicheClient(dossierId, Role.SUPERVISEUR, autreEmploye).contenu(), null);
        String dossier = texte(service.genererFicheDossier(dossierId, Role.SUPERVISEUR, autreEmploye).contenu(), null);
        assertThat(client).contains("Fiche client").doesNotContain("Fiche dossier");
        assertThat(dossier).contains("Fiche dossier").doesNotContain("Fiche client");
    }

    @Test
    void sans_operation_ni_tp_les_tableaux_le_disent() throws Exception {
        when(lecture.tickets(ws, dossierId)).thenReturn(List.of());
        when(lecture.versionsTp(ws, dossierId)).thenReturn(List.of());
        String texte = texte(service.genererFicheDossier(dossierId, Role.SUPERVISEUR, autreEmploye).contenu(),
                "fiche-dossier-vide");
        assertThat(texte).contains("Aucune opération enregistrée", "Aucune taxe professionnelle enregistrée",
                "Aucune opération clôturée à ce jour.");
        assertThat(texte).doesNotContain("$");
    }

    // ------------------------------------------------------------------ conservation

    @Test
    void le_responsable_conserve_chaque_generation_en_version_dans_la_data_room() {
        when(juridique.uploadVersion(eq(dossierId), eq("FICHE_CLIENT"), eq("Fiche client"), isNull(),
                any(MultipartFile.class), eq(responsable))).thenReturn(resume((short) 3));
        FicheClientService.FichePdf w = service.genererFicheClient(dossierId, Role.EMPLOYE, responsable);
        assertThat(w.conservee()).isTrue();
        assertThat(w.version()).isEqualTo((short) 3);
        assertThat(w.nomFichier()).isEqualTo("Fiche_client_ACME_SARL.pdf");
        // Conservee en PDF : le fichier depose porte le type application/pdf et les octets rendus.
        org.mockito.ArgumentCaptor<MultipartFile> f = org.mockito.ArgumentCaptor.forClass(MultipartFile.class);
        verify(juridique).uploadVersion(eq(dossierId), eq("FICHE_CLIENT"), eq("Fiche client"), isNull(),
                f.capture(), eq(responsable));
        assertThat(f.getValue().getContentType()).isEqualTo("application/pdf");
        assertThat(f.getValue().getOriginalFilename()).isEqualTo("Fiche_client_ACME_SARL.pdf");
        try {
            assertThat(f.getValue().getBytes()).isEqualTo(w.contenu());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void le_superviseur_telecharge_sans_ecrire() {
        FicheClientService.FichePdf w = service.genererFicheClient(dossierId, Role.SUPERVISEUR, autreEmploye);
        assertThat(w.conservee()).isFalse();
        verify(juridique, never()).uploadVersion(any(), any(), any(), any(), any(MultipartFile.class), any());
    }

    @Test
    void societe_archivee_fiche_produite_mais_non_conservee() {
        when(archiveGuard.isReadOnly(dossierId)).thenReturn(true);
        FicheClientService.FichePdf w = service.genererFicheDossier(dossierId, Role.EMPLOYE, responsable);
        assertThat(w.conservee()).isFalse();
        assertThat(w.contenu()).isNotEmpty();
        verify(juridique, never()).uploadVersion(any(), any(), any(), any(), any(MultipartFile.class), any());
    }

    // ------------------------------------------------------------------ doubles

    private DossierViewEntity dossier() {
        DossierViewEntity d = mock(DossierViewEntity.class);
        lenient().when(d.getId()).thenReturn(dossierId);
        lenient().when(d.getRaisonSociale()).thenReturn("ACME SARL");
        lenient().when(d.getFormeJuridique()).thenReturn("SARL");
        lenient().when(d.getStatut()).thenReturn("ACTIVE");
        lenient().when(d.getResponsableId()).thenReturn(responsable);
        lenient().when(d.getCapitalSocialMad()).thenReturn(new BigDecimal("250000.00"));
        lenient().when(d.getAdresseSiege()).thenReturn("12 rue des Orangers");
        lenient().when(d.getVille()).thenReturn("Casablanca");
        lenient().when(d.getRcNumero()).thenReturn("45678");
        lenient().when(d.getRcTribunal()).thenReturn("Casablanca");
        lenient().when(d.getIce()).thenReturn("001234567000089");
        lenient().when(d.getIdentifiantFiscal()).thenReturn("IF-77");
        lenient().when(d.getTaxeProfessionnelle()).thenReturn("TP-2222");
        return d;
    }

    private static UserViewEntity user(UUID id, String nom) {
        UserViewEntity u = mock(UserViewEntity.class);
        lenient().when(u.getId()).thenReturn(id);
        lenient().when(u.displayName()).thenReturn(nom);
        return u;
    }

    private DocumentSummary resume(short version) {
        return new DocumentSummary(UUID.randomUUID(), dossierId, null, "FICHE_CLIENT", "Fiche client",
                version, true, "f.pdf", FicheClientService.PDF_MIME, 10L, J1, null, null, null, false);
    }

    /** Texte du PDF (PDFBox) ; le PDF et ses pages en PNG sont copies dans target/output/l5b-pdf. */
    private static String texte(byte[] pdf, String copie) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (copie != null) {
                Path dir = Path.of("target", "output", "l5b-pdf");
                Files.createDirectories(dir);
                Files.write(dir.resolve(copie + ".pdf"), pdf);
                PDFRenderer r = new PDFRenderer(doc);
                for (int p = 0; p < doc.getNumberOfPages(); p++) {
                    ImageIO.write(r.renderImageWithDPI(p, 110, ImageType.RGB), "png",
                            new File(dir.toFile(), copie + "-p" + (p + 1) + ".png"));
                }
            }
            return new PDFTextStripper().getText(doc);
        }
    }
}
