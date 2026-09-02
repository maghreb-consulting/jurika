package ma.jurika.auth.application;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.auth.infrastructure.persistence.WorkspaceLogoEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceLogoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Papier à en-tête V31 — validation du téléversement du logo (format, taille,
 * image réelle) + stockage / suppression.
 */
class WorkspaceLogoServiceTest {

    private final WorkspaceLogoJpaRepository repo = mock(WorkspaceLogoJpaRepository.class);
    private WorkspaceLogoService service;
    private final UUID ws = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WorkspaceLogoService(repo);
    }

    private static byte[] pngBytes(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "png", b);
        return b.toByteArray();
    }

    @Test
    void upload_png_valide_stocke_les_octets() throws Exception {
        WorkspaceLogoEntity e = mock(WorkspaceLogoEntity.class);
        when(repo.findById(ws)).thenReturn(Optional.of(e));
        byte[] png = pngBytes(120, 48);

        service.upload(ws, png, "image/png", "logo.png");

        verify(e).setLogoBytes(png);
        verify(e).setLogoContentType("image/png");
        verify(repo).save(e);
    }

    @Test
    void rejette_un_format_non_autorise() {
        assertThatThrownBy(() ->
                service.upload(ws, new byte[]{1, 2, 3}, "application/pdf", "doc.pdf"))
                .isInstanceOf(ValidationException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void rejette_un_fichier_trop_volumineux() {
        byte[] big = new byte[1024 * 1024 + 1]; // > 1 Mo
        assertThatThrownBy(() -> service.upload(ws, big, "image/png", "logo.png"))
                .isInstanceOf(ValidationException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void rejette_une_image_corrompue() {
        // Content-type image mais octets non décodables -> refus.
        byte[] notAnImage = "pas une image".getBytes();
        assertThatThrownBy(() -> service.upload(ws, notAnImage, "image/png", "logo.png"))
                .isInstanceOf(ValidationException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void delete_efface_le_logo() {
        WorkspaceLogoEntity e = mock(WorkspaceLogoEntity.class);
        when(repo.findById(ws)).thenReturn(Optional.of(e));

        service.delete(ws);

        verify(e).setLogoBytes(null);
        verify(e).setLogoContentType(null);
        verify(repo).save(e);
    }

    @Test
    void get_retourne_le_logo_si_present() {
        WorkspaceLogoEntity e = mock(WorkspaceLogoEntity.class);
        when(e.getLogoBytes()).thenReturn(new byte[]{9, 8, 7});
        when(e.getLogoContentType()).thenReturn("image/png");
        when(repo.findById(ws)).thenReturn(Optional.of(e));

        Optional<WorkspaceLogoService.Logo> logo = service.get(ws);
        assertThat(logo).isPresent();
        assertThat(logo.get().contentType()).isEqualTo("image/png");
    }
}
