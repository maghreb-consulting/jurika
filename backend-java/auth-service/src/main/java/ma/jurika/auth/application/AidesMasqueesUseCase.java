package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AidesMasqueesRegistre;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lot L6 (E6, CDC 15.4) : "Une aide deja vue peut etre masquee et reaffichee."
 * Le choix suit l'utilisateur (memorise cote serveur), quel que soit le poste.
 * Une cle designe un encadre d'aide de l'interface ou la visite guidee d'un role
 * ({@code visite-<role>}). Chaque operation porte sur le compte de l'appelant.
 */
@Service
public class AidesMasqueesUseCase {

    /** Meme regle que la contrainte CHECK de V39. */
    static final Pattern CLE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,119}$");

    /** Garde-fou : aucun ecran n'approche ce nombre d'aides. */
    static final int MAXIMUM = 500;

    private final AidesMasqueesRegistre registre;

    public AidesMasqueesUseCase(AidesMasqueesRegistre registre) {
        this.registre = registre;
    }

    @Transactional(readOnly = true)
    public List<String> cles(UUID userId, UUID workspaceId) {
        TenantContext.set(workspaceId);
        return registre.cles(userId, workspaceId);
    }

    @Transactional
    public void masquer(UUID userId, UUID workspaceId, String cle) {
        String c = valider(cle);
        TenantContext.set(workspaceId);
        if (registre.compter(userId, workspaceId) >= MAXIMUM) {
            throw new BusinessException("AIDES_TROP_NOMBREUSES",
                    "Trop d\u2019aides masqu\u00e9es : r\u00e9affichez les aides depuis \u00ab Mon compte \u00bb avant d\u2019en masquer d\u2019autres.");
        }
        registre.masquer(userId, workspaceId, c);
    }

    @Transactional
    public void reafficher(UUID userId, UUID workspaceId, String cle) {
        String c = valider(cle);
        TenantContext.set(workspaceId);
        registre.reafficher(userId, workspaceId, c);
    }

    @Transactional
    public void reafficherTout(UUID userId, UUID workspaceId) {
        TenantContext.set(workspaceId);
        registre.reafficherTout(userId, workspaceId);
    }

    private static String valider(String cle) {
        if (cle == null || !CLE.matcher(cle).matches()) {
            throw new ValidationException("Aide inconnue : la cl\u00e9 de l\u2019aide n\u2019est pas valide.");
        }
        return cle;
    }
}
