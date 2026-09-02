package ma.jurika.auth.application;

import ma.jurika.auth.infrastructure.persistence.WorkspaceLogoEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceLogoJpaRepository;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Papier à en-tête V31 (2026-07-14) — logo du cabinet, stocké EN BASE
 * (colonnes {@code workspaces.logo_bytes / logo_content_type}). Réservé au
 * SUPERVISEUR (contrôleur), audité. Validation : PNG/JPG, ~1 Mo max, image
 * réelle et dimensions raisonnables.
 */
@Service
public class WorkspaceLogoService {

    /** ~1 Mo. */
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final int MAX_DIM = 5000;
    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/jpg");

    private final WorkspaceLogoJpaRepository repo;

    public WorkspaceLogoService(WorkspaceLogoJpaRepository repo) {
        this.repo = repo;
    }

    public record Logo(byte[] bytes, String contentType) {}

    @Transactional
    @Auditable(action = "WORKSPACE_LOGO_UPDATED", resourceType = "workspace")
    public void upload(UUID workspaceId, byte[] bytes, String contentType, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("Fichier vide.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ValidationException("Logo trop volumineux (1 Mo maximum).");
        }
        String ct = normalizeContentType(contentType, originalFilename);
        if (ct == null) {
            throw new ValidationException("Format non autorisé : PNG ou JPG uniquement.");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            img = null;
        }
        if (img == null) {
            throw new ValidationException("Image illisible ou corrompue.");
        }
        if (img.getWidth() < 1 || img.getHeight() < 1
                || img.getWidth() > MAX_DIM || img.getHeight() > MAX_DIM) {
            throw new ValidationException("Dimensions du logo non valides (" + MAX_DIM + " px max).");
        }

        WorkspaceLogoEntity e = repo.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace introuvable : " + workspaceId));
        e.setLogoBytes(bytes);
        e.setLogoContentType("image/jpg".equals(ct) ? "image/jpeg" : ct);
        repo.save(e);
    }

    @Transactional(readOnly = true)
    public Optional<Logo> get(UUID workspaceId) {
        return repo.findById(workspaceId)
                .filter(e -> e.getLogoBytes() != null && e.getLogoBytes().length > 0)
                .map(e -> new Logo(e.getLogoBytes(), e.getLogoContentType()));
    }

    @Transactional
    @Auditable(action = "WORKSPACE_LOGO_DELETED", resourceType = "workspace")
    public void delete(UUID workspaceId) {
        repo.findById(workspaceId).ifPresent(e -> {
            e.setLogoBytes(null);
            e.setLogoContentType(null);
            repo.save(e);
        });
    }

    private static String normalizeContentType(String contentType, String filename) {
        if (contentType != null) {
            String c = contentType.toLowerCase();
            if (ALLOWED.contains(c)) return c;
        }
        if (filename != null) {
            String f = filename.toLowerCase();
            if (f.endsWith(".png")) return "image/png";
            if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        }
        return null;
    }
}
