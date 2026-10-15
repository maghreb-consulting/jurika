package ma.jurika.auth.api;

import ma.jurika.auth.application.WorkspaceLogoService;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

/**
 * Papier à en-tête V31 (2026-07-14) — logo du cabinet (workspace courant).
 *
 * <p>POST / DELETE : SUPERVISEUR uniquement (403 pour EMPLOYE / CLIENT), audité.
 * GET : tout membre authentifié (aperçu dans les paramètres). Isolation : le
 * workspace est toujours celui du token (jamais fourni par le client).
 */
@RestController
@RequestMapping("/api/v1/workspace/profile/logo")
public class WorkspaceLogoController {

    private final WorkspaceLogoService service;

    public WorkspaceLogoController(WorkspaceLogoService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<Void> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        service.upload(user.workspaceId(), file.getBytes(), file.getContentType(),
                file.getOriginalFilename());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedUser user,
                                 jakarta.servlet.http.HttpServletRequest requete) {
        Optional<WorkspaceLogoService.Logo> logo = service.get(user.workspaceId());
        if (logo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        WorkspaceLogoService.Logo l = logo.get();
        // Decision du 2026-10-14 (RG-ESS-05) : pendant l'essai, aucun octet d'un fichier depose
        // ne parvient au navigateur. Le logo s'affiche encore : le serveur le redessine en PNG
        // (image d'affichage produite par le serveur, declaree au filtre de l'essai).
        if (enEssai(user.workspaceId())) {
            byte[] png = redessiner(l.bytes());
            if (png == null) {
                return ResponseEntity.unprocessableEntity().contentType(MediaType.APPLICATION_JSON)
                        .body(java.util.Map.of("code", "LOGO_ILLISIBLE",
                                "message", "Le logo du cabinet n\u2019a pas pu \u00eatre affich\u00e9."));
            }
            ma.jurika.common.trial.AffichageEssai.declarer(requete);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
        }
        String ct = (l.contentType() == null || l.contentType().isBlank())
                ? MediaType.IMAGE_PNG_VALUE : l.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ct))
                .body(l.bytes());
    }

    /** Etat de l'essai du cabinet (auth-service : lecture locale). */
    private org.springframework.beans.factory.ObjectProvider<ma.jurika.common.trial.TrialAccessChecker> essais;

    @org.springframework.beans.factory.annotation.Autowired
    public void setEssais(org.springframework.beans.factory.ObjectProvider<ma.jurika.common.trial.TrialAccessChecker> e) {
        this.essais = e;
    }

    /** Etat illisible : traite comme un essai (logo redessine), jamais l'inverse. */
    boolean enEssai(java.util.UUID workspaceId) {
        ma.jurika.common.trial.TrialAccessChecker c = essais == null ? null : essais.getIfAvailable();
        if (c == null || workspaceId == null) return false;
        try {
            return c.getState(workspaceId).map(ma.jurika.common.trial.TrialAccessChecker.TrialState::enEssai)
                    .orElse(false);
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /** Le logo redessine par le serveur en PNG, ou null s'il est illisible. */
    static byte[] redessiner(byte[] octets) {
        try {
            java.awt.image.BufferedImage source = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(octets));
            if (source == null) return null;
            java.awt.image.BufferedImage copie = new java.awt.image.BufferedImage(source.getWidth(),
                    source.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = copie.createGraphics();
            try {
                g.drawImage(source, 0, 0, null);
            } finally {
                g.dispose();
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(copie, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(user.workspaceId());
        return ResponseEntity.noContent().build();
    }
}
