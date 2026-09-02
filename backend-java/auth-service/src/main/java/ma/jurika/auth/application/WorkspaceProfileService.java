package ma.jurika.auth.application;

import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Simplification inscription (2026-07-13) — lecture / mise a jour des
 * informations legales du cabinet (workspace) apres inscription.
 *
 * <p>Motivation : l'inscription simplifiee ne collecte plus que la
 * denomination, la ville et un ICE OPTIONNEL. L'ICE devant servir dans les
 * dossiers, il doit rester completable depuis les parametres du cabinet. Ce
 * service porte cette completion (SUPERVISEUR only cote controller) et la
 * lecture pour le bandeau de rappel "ICE manquant".
 */
@Service
public class WorkspaceProfileService {

    private final WorkspaceJpaRepository workspaceRepository;

    public WorkspaceProfileService(WorkspaceJpaRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Vue lecture des infos cabinet. {@code iceMissing} pilote le bandeau de rappel.
     * {@code nomAfficheDocuments} = valeur brute (nullable) ; {@code documentDisplayName}
     * = valeur effective de l'en-tete PDF (nom affiche -> sinon denomination).
     * {@code hasLogo} = true si un logo est stocke (papier a en-tete V31).
     */
    public record View(String name, String professionalType, String ice, String city,
                       String contactEmail, boolean iceMissing,
                       String nomAfficheDocuments, String documentDisplayName,
                       String adresse, String telephone, String siteWeb,
                       String rcNumber, String ifFiscal, boolean hasLogo) {}

    /** Champs modifiables du profil cabinet (null = inchange, "" = effacement). */
    public record UpdateCommand(String ice, String city, String nomAfficheDocuments,
                                String adresse, String telephone, String siteWeb,
                                String rcNumber, String ifFiscal) {}

    @Transactional(readOnly = true)
    public View get(UUID workspaceId) {
        WorkspaceEntity w = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace introuvable : " + workspaceId));
        return toView(w);
    }

    @Transactional
    @Auditable(action = "WORKSPACE_PROFILE_UPDATE", resourceType = "workspace")
    public View update(UUID workspaceId, UpdateCommand cmd) {
        WorkspaceEntity w = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace introuvable : " + workspaceId));

        // ICE : null => champ non fourni (inchange). "" => effacement. Sinon 15 chiffres.
        if (cmd.ice() != null) {
            String trimmed = cmd.ice().isBlank() ? null : cmd.ice().trim();
            if (trimmed != null && !trimmed.matches("\\d{15}")) {
                throw new ValidationException("ICE : 15 chiffres requis.");
            }
            w.setIce(trimmed);
        }

        // Ville : null => inchange. Non-vide => 2..80 caracteres.
        if (cmd.city() != null && !cmd.city().isBlank()) {
            String c = cmd.city().trim();
            if (c.length() < 2 || c.length() > 80) {
                throw new ValidationException("Ville : 2 a 80 caracteres.");
            }
            w.setCity(c);
        }

        // Nom affiche documents : null => inchange. "" => effacement (repli sur name).
        if (cmd.nomAfficheDocuments() != null) {
            String n = blankToNull(cmd.nomAfficheDocuments());
            if (n != null && n.length() > 150) {
                throw new ValidationException("Nom affiche : 150 caracteres max.");
            }
            w.setNomAfficheDocuments(n);
        }

        // Coordonnees papier a en-tete (toutes optionnelles ; "" efface).
        if (cmd.adresse() != null) {
            w.setAdresse(trimTo(cmd.adresse(), 300, "Adresse"));
        }
        if (cmd.telephone() != null) {
            w.setTelephone(trimTo(cmd.telephone(), 30, "Telephone"));
        }
        if (cmd.siteWeb() != null) {
            w.setSiteWeb(trimTo(cmd.siteWeb(), 200, "Site web"));
        }
        if (cmd.rcNumber() != null) {
            w.setRcNumber(trimTo(cmd.rcNumber(), 20, "RC"));
        }
        if (cmd.ifFiscal() != null) {
            w.setIfFiscal(trimTo(cmd.ifFiscal(), 8, "Identifiant fiscal"));
        }

        workspaceRepository.save(w);
        return toView(w);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String trimTo(String s, int max, String label) {
        String v = blankToNull(s);
        if (v != null && v.length() > max) {
            throw new ValidationException(label + " : " + max + " caracteres max.");
        }
        return v;
    }

    private static View toView(WorkspaceEntity w) {
        boolean iceMissing = w.getIce() == null || w.getIce().isBlank();
        String raw = w.getNomAfficheDocuments();
        String effective = (raw != null && !raw.isBlank()) ? raw : w.getName();
        boolean hasLogo = w.getLogoContentType() != null && !w.getLogoContentType().isBlank();
        return new View(w.getName(), w.getProfessionalType(), w.getIce(), w.getCity(),
                w.getContactEmail(), iceMissing, raw, effective,
                w.getAdresse(), w.getTelephone(), w.getSiteWeb(),
                w.getRcNumber(), w.getIfFiscal(), hasLogo);
    }
}
