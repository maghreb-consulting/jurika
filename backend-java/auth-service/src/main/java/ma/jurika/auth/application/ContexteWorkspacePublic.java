package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.ResolveurWorkspaceHorsContexte;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Lot L0 (E13a) : pose le workspace d'une requete PUBLIQUE dans
 * {@link TenantContext}, AVANT l'appel du cas d'usage {@code @Transactional}
 * (le gestionnaire de transactions de jurika-common le lit a l'ouverture).
 *
 * <p>Si rien n'est trouve, un workspace INEXISTANT est pose : sous la RLS, le
 * cas d'usage ne trouve alors rien et leve son erreur habituelle (code
 * inconnu, jeton invalide), avec le meme statut qu'avant, sans erreur 500 et
 * sans reveler si le code ou le jeton existe ailleurs.
 */
@Component
public class ContexteWorkspacePublic {

    private final ResolveurWorkspaceHorsContexte resolveur;
    private final VerificationTokenHasher hasherVerification;
    private final TokenIssuer tokenIssuer;

    public ContexteWorkspacePublic(ResolveurWorkspaceHorsContexte resolveur,
                                   VerificationTokenHasher hasherVerification,
                                   TokenIssuer tokenIssuer) {
        this.resolveur = resolveur;
        this.hasherVerification = hasherVerification;
        this.tokenIssuer = tokenIssuer;
    }

    /** Connexion, code de secours, reinitialisation (demande), renvoi du lien, controle du code. */
    public void poserParCode(String codeWorkspace) {
        poser(resolveur.parCode(codeWorkspace));
    }

    /** Confirmation de reinitialisation : meme empreinte que ResetPasswordUseCase. */
    public void poserParJetonReset(String jeton) {
        poser(jeton == null ? Optional.empty()
                : resolveur.parEmpreinteJetonReset(ResetPasswordUseCase.empreinte(jeton)));
    }

    /** Verification de l'email : meme empreinte que VerifyEmailUseCase. */
    public void poserParJetonVerification(String jeton) {
        poser(jeton == null ? Optional.empty()
                : resolveur.parEmpreinteJetonVerification(hasherVerification.hash(jeton)));
    }

    /** Renouvellement de session : meme empreinte que RefreshTokenUseCase. */
    public void poserParJetonRefresh(String jeton) {
        Optional<UUID> ws;
        try {
            ws = resolveur.parEmpreinteJetonRefresh(tokenIssuer.parseRefreshToken(jeton).hash());
        } catch (RuntimeException jetonIllisible) {
            ws = Optional.empty();
        }
        poser(ws);
    }

    /** Route publique qui transporte deja le workspace (2FA, defi SMS). */
    public void poserWorkspace(UUID workspaceId) {
        poser(Optional.ofNullable(workspaceId));
    }

    /**
     * Inscription : l'identifiant du workspace a creer est genere ici et devient
     * le workspace courant, pour que l'INSERT passe la politique RLS
     * (workspace_self_access : id = workspace courant).
     */
    public UUID poserNouveauWorkspace() {
        UUID id = UUID.randomUUID();
        TenantContext.set(id);
        return id;
    }

    private static void poser(Optional<UUID> workspace) {
        TenantContext.set(workspace.orElseGet(UUID::randomUUID));
    }
}
