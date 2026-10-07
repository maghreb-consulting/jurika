package ma.jurika.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.jurika.auth.api.dto.ChangePasswordRequest;
import ma.jurika.auth.api.dto.Choose2faMethodRequest;
import ma.jurika.auth.api.dto.Confirm2faRequest;
import ma.jurika.auth.api.dto.LoginRequest;
import ma.jurika.auth.api.dto.LoginResponse;
import ma.jurika.auth.api.dto.LoginSmsChallengeRequest;
import ma.jurika.auth.api.dto.InviteEmployeRequest;
import ma.jurika.auth.api.dto.LogoutRequest;
import ma.jurika.auth.api.dto.PasswordResetConfirm;
import ma.jurika.auth.api.dto.PasswordResetRequest;
import ma.jurika.auth.api.dto.RefreshRequest;
import ma.jurika.auth.api.dto.RegisterRequest;
import ma.jurika.auth.api.dto.RecoveryCodesResponse;
import ma.jurika.auth.api.dto.RegisterResponse;
import ma.jurika.auth.api.dto.ResendVerificationRequest;
import ma.jurika.auth.api.dto.Setup2faOptionsResponse;
import ma.jurika.auth.api.dto.Setup2faResponse;
import ma.jurika.auth.api.dto.SmsOtpSendRequest;
import ma.jurika.auth.api.dto.SmsOtpSendResponse;
import ma.jurika.auth.api.dto.SmsOtpVerifyRequest;
import ma.jurika.auth.api.dto.TokenResponse;
import ma.jurika.auth.api.dto.Verify2faRequest;
import ma.jurika.auth.api.dto.VerifyEmailRequest;
import ma.jurika.auth.api.dto.VerifyEmailResponse;
import ma.jurika.auth.api.dto.VerifyRecoveryCodeRequest;
import ma.jurika.auth.api.dto.WorkspaceCheckRequest;
import ma.jurika.auth.api.dto.WorkspaceCheckResponse;
import ma.jurika.auth.application.ChangePasswordUseCase;
import ma.jurika.auth.application.CheckWorkspaceUseCase;
import ma.jurika.auth.application.Choose2faMethodUseCase;
import ma.jurika.auth.application.GenerateRecoveryCodesUseCase;
import ma.jurika.auth.application.GetDossierClientUseCase;
import ma.jurika.auth.application.InviteClientUseCase;
import ma.jurika.auth.application.InviteEmployeUseCase;
import ma.jurika.auth.application.ListWorkspaceUsersUseCase;
import ma.jurika.auth.application.RemoveClientAccessUseCase;
import ma.jurika.auth.application.LoginUseCase;
import ma.jurika.auth.application.LogoutUseCase;
import ma.jurika.auth.application.RefreshTokenUseCase;
import ma.jurika.auth.application.RegisterWorkspaceUseCase;
import ma.jurika.auth.application.ResendVerificationUseCase;
import ma.jurika.auth.application.ResetPasswordUseCase;
import ma.jurika.auth.application.SendSmsOtpUseCase;
import ma.jurika.auth.application.SetUserStatusUseCase;
import ma.jurika.auth.application.UpdateContactEmailUseCase;
import ma.jurika.auth.application.Setup2faUseCase;
import ma.jurika.auth.application.Verify2faUseCase;
import ma.jurika.auth.application.VerifyEmailUseCase;
import ma.jurika.auth.application.VerifyRecoveryCodeUseCase;
import ma.jurika.auth.application.VerifySmsOtpUseCase;
import ma.jurika.auth.api.dto.DossierClientResponse;
import ma.jurika.auth.api.dto.InviteClientRequest;
import ma.jurika.auth.api.dto.InviteClientResponse;
import ma.jurika.auth.api.dto.SetUserStatusRequest;
import ma.jurika.auth.api.dto.UpdateContactEmailRequest;
import ma.jurika.auth.api.dto.WorkspaceUserDto;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterWorkspaceUseCase registerWorkspaceUseCase;
    private final CheckWorkspaceUseCase checkWorkspaceUseCase;
    private final LoginUseCase loginUseCase;
    private final Verify2faUseCase verify2faUseCase;
    private final Setup2faUseCase setup2faUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final ResendVerificationUseCase resendVerificationUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final Choose2faMethodUseCase choose2faMethodUseCase;
    private final SendSmsOtpUseCase sendSmsOtpUseCase;
    private final VerifySmsOtpUseCase verifySmsOtpUseCase;
    private final GenerateRecoveryCodesUseCase generateRecoveryCodesUseCase;
    private final VerifyRecoveryCodeUseCase verifyRecoveryCodeUseCase;
    private final InviteClientUseCase inviteClientUseCase;
    private final InviteEmployeUseCase inviteEmployeUseCase;
    private final RemoveClientAccessUseCase removeClientAccessUseCase;
    private final GetDossierClientUseCase getDossierClientUseCase;
    private final SetUserStatusUseCase setUserStatusUseCase;
    private final ListWorkspaceUsersUseCase listWorkspaceUsersUseCase;
    private final UpdateContactEmailUseCase updateContactEmailUseCase;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    /** Lot L0 (E13a) : workspace des routes publiques, pose avant la transaction. */
    private final ma.jurika.auth.application.ContexteWorkspacePublic contextePublic;

    public AuthController(RegisterWorkspaceUseCase registerWorkspaceUseCase,
                          CheckWorkspaceUseCase checkWorkspaceUseCase,
                          LoginUseCase loginUseCase,
                          Verify2faUseCase verify2faUseCase,
                          Setup2faUseCase setup2faUseCase,
                          RefreshTokenUseCase refreshTokenUseCase,
                          LogoutUseCase logoutUseCase,
                          ResetPasswordUseCase resetPasswordUseCase,
                          VerifyEmailUseCase verifyEmailUseCase,
                          ResendVerificationUseCase resendVerificationUseCase,
                          ChangePasswordUseCase changePasswordUseCase,
                          Choose2faMethodUseCase choose2faMethodUseCase,
                          SendSmsOtpUseCase sendSmsOtpUseCase,
                          VerifySmsOtpUseCase verifySmsOtpUseCase,
                          GenerateRecoveryCodesUseCase generateRecoveryCodesUseCase,
                          VerifyRecoveryCodeUseCase verifyRecoveryCodeUseCase,
                          InviteClientUseCase inviteClientUseCase,
                          InviteEmployeUseCase inviteEmployeUseCase,
                          RemoveClientAccessUseCase removeClientAccessUseCase,
                          GetDossierClientUseCase getDossierClientUseCase,
                          SetUserStatusUseCase setUserStatusUseCase,
                          ListWorkspaceUsersUseCase listWorkspaceUsersUseCase,
                          UpdateContactEmailUseCase updateContactEmailUseCase,
                          WorkspaceRepository workspaceRepository,
                          UserRepository userRepository,
                          ma.jurika.auth.application.ContexteWorkspacePublic contextePublic) {
        this.registerWorkspaceUseCase = registerWorkspaceUseCase;
        this.checkWorkspaceUseCase = checkWorkspaceUseCase;
        this.loginUseCase = loginUseCase;
        this.verify2faUseCase = verify2faUseCase;
        this.setup2faUseCase = setup2faUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.verifyEmailUseCase = verifyEmailUseCase;
        this.resendVerificationUseCase = resendVerificationUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
        this.choose2faMethodUseCase = choose2faMethodUseCase;
        this.sendSmsOtpUseCase = sendSmsOtpUseCase;
        this.verifySmsOtpUseCase = verifySmsOtpUseCase;
        this.generateRecoveryCodesUseCase = generateRecoveryCodesUseCase;
        this.verifyRecoveryCodeUseCase = verifyRecoveryCodeUseCase;
        this.inviteClientUseCase = inviteClientUseCase;
        this.inviteEmployeUseCase = inviteEmployeUseCase;
        this.removeClientAccessUseCase = removeClientAccessUseCase;
        this.getDossierClientUseCase = getDossierClientUseCase;
        this.setUserStatusUseCase = setUserStatusUseCase;
        this.listWorkspaceUsersUseCase = listWorkspaceUsersUseCase;
        this.updateContactEmailUseCase = updateContactEmailUseCase;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.contextePublic = contextePublic;
    }

    /**
     * Cree un compte CLIENT et l'attache a un dossier (RG-DR Client Access V2 -- mode A).
     * Reserve aux EMPLOYE / SUPERVISEUR du workspace courant.
     */
    @PostMapping("/invite-client")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public InviteClientResponse inviteClient(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @RequestBody InviteClientRequest req,
                                              HttpServletRequest http) {
        String wsCode = workspaceRepository.findById(user.workspaceId())
                .map(w -> w.code()).orElse("JUR-?????");
        var r = inviteClientUseCase.execute(new InviteClientUseCase.Command(
                user.workspaceId(), wsCode, req.dossierId(),
                req.email(), req.firstName(), req.lastName(), req.phone(),
                user.userId(), http.getRemoteAddr(), http.getHeader("User-Agent")));
        String msg = r.userCreated()
                ? "Compte client cree. Un email a ete envoye avec les identifiants."
                : "Client existant attache au dossier. Aucun email envoye.";
        return new InviteClientResponse(r.userId(), r.userCreated(), r.tempPassword(),
                r.loginEmail(), r.contactEmail(), msg);
    }

    /**
     * 2026-07-01 — Renvoie l'identite du CLIENT lie a un dossier (resolu
     * depuis {@code entreprise_dossiers.client_id -> users}) pour la vue
     * Data Room cote cabinet. Permet d'afficher "Client : Prenom Nom (email)"
     * et de rendre le retrait d'acces nominatif.
     *
     * <p>Reponse 200 avec {@code {"client": {...}}} si un client est lie, ou
     * {@code {"client": null}} si aucun. 404 si le dossier n'existe pas dans
     * ce workspace.
     *
     * <p>RBAC : EMPLOYE / SUPERVISEUR du workspace courant.
     */
    @GetMapping("/dossiers/{dossierId}/client")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public DossierClientResponse getDossierClient(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable java.util.UUID dossierId) {
        var info = getDossierClientUseCase.execute(
                new GetDossierClientUseCase.Command(user.workspaceId(), dossierId));
        return DossierClientResponse.of(info);
    }

    /**
     * Fix 2026-06-07 (BUG 6) — Retire DEFINITIVEMENT l'acces d'un client
     * a un dossier (UPDATE entreprise_dossiers.client_id = NULL).
     *
     * <p>Difference avec la suspension dataroom-service (qui est reversible) :
     * apres ce DELETE, le client perd l'acces au dossier jusqu'a re-invitation.
     * Le compte user du client n'est pas supprime (autres dossiers + historique).
     *
     * <p>Idempotent : si le dossier n'avait pas de client, on retourne 200 OK
     * avec {@code alreadyDetached=true}.
     *
     * <p>RBAC : EMPLOYE / SUPERVISEUR. Audit : action CLIENT_ACCESS_REMOVED.
     */
    @DeleteMapping("/dossiers/{dossierId}/client")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public ResponseEntity<java.util.Map<String, Object>> removeClientAccess(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable java.util.UUID dossierId,
            HttpServletRequest http) {
        var r = removeClientAccessUseCase.execute(new RemoveClientAccessUseCase.Command(
                user.workspaceId(), dossierId, user.userId(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return ResponseEntity.ok(java.util.Map.of(
                "dossierId", r.dossierId().toString(),
                "previousClientId", r.previousClientId() == null ? "" : r.previousClientId().toString(),
                "alreadyDetached", r.alreadyDetached(),
                "message", r.alreadyDetached()
                        ? "Le dossier n'avait pas de client lie. Aucune action effectuee."
                        : "Acces client retire du dossier. Le client peut etre reinvite a tout moment."
        ));
    }

    /**
     * HIGH-13 (audit 2026-06-02) — Invite un collaborateur interne du cabinet
     * (EMPLOYE ou SUPERVISEUR). Reserve aux SUPERVISEUR du workspace courant.
     */
    @PostMapping("/invite-employee")
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<java.util.Map<String, Object>> inviteEmployee(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody InviteEmployeRequest req,
            HttpServletRequest http) {
        var r = inviteEmployeUseCase.execute(new InviteEmployeUseCase.Command(
                user.workspaceId(),
                req.email(), req.firstName(), req.lastName(), req.phone(),
                ma.jurika.common.security.Role.valueOf(req.role()),
                user.userId(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        // BUG 6 (2026-06-07) — On expose tempPassword (parite avec InviteClient)
        // pour permettre les flux e2e + le superviseur peut communiquer le MDP
        // par un autre canal quand emailDelivered=false. Affichage UI : strict
        // contrôle (TeamPage ne stocke jamais le MDP en clair cote front).
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", r.userId().toString());
        body.put("created", r.created());
        body.put("emailDelivered", r.emailDelivered());
        body.put("tempPassword", r.tempPassword());
        // BUG 7 (2026-06-08) — identifiant @jurika.ma + email contact distincts.
        body.put("loginEmail", r.loginEmail());
        body.put("contactEmail", r.contactEmail());
        body.put("message", r.emailDelivered()
                ? "Invitation envoyee a " + req.email() + "."
                : "Compte cree mais l'email n'a pas pu etre envoye — communiquez le code workspace au collaborateur.");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * BUG 6 (2026-06-07) — Liste les membres internes (EMPLOYE + SUPERVISEUR)
     * du workspace courant pour la page Equipe du superviseur. CLIENT exclu :
     * il se gere via le flux dataroom dedie.
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public java.util.List<WorkspaceUserDto> listWorkspaceUsers(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listWorkspaceUsersUseCase.execute(user.workspaceId()).stream()
                .map(WorkspaceUserDto::from)
                .toList();
    }

    /**
     * Traçabilité (2026-07-15) — annuaire COMPLET du workspace pour la page
     * Traçabilité : internes (EMPLOYE + SUPERVISEUR) ET clients, tous statuts
     * (ACTIVE / PENDING / INACTIVE). Permet de resoudre par leur NOM tous les
     * acteurs d'un event audit — y compris un client ou un compte desactive —
     * et d'alimenter le filtre "acteur" (groupes Employes / Clients).
     *
     * <p>Distinct de {@code /users} (internes seulement) et de
     * {@code /users/contacts} (internes ACTIVE, hors caller) : ces deux endpoints
     * restent inchanges car utilises par la vue Equipe et le chat (qui doivent
     * continuer d'EXCLURE les clients). Reserve SUPERVISEUR / SUPER_ADMIN, scope
     * au workspace du JWT.
     */
    @GetMapping("/users/workspace-directory")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')")
    public java.util.List<WorkspaceUserDto> listWorkspaceDirectory(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listWorkspaceUsersUseCase.executeDirectory(user.workspaceId()).stream()
                .map(WorkspaceUserDto::from)
                .toList();
    }

    /**
     * BUG 12 (2026-06-08) — Liste des contacts internes pour le chat. Memes
     * regles que {@code /users} (memes EMPLOYE+SUPERVISEUR du meme workspace,
     * pas de CLIENT) mais accessible aussi a l'EMPLOYE, et :
     *  - n'inclut QUE les comptes ACTIVE (les PENDING / INACTIVE sont filtres)
     *  - exclut le caller lui-meme (impossible de chatter avec soi-meme).
     *
     * Utilise par {@code ChatPage} pour remplacer la saisie manuelle d'ID
     * utilisateur. Cote serveur {@code chat:send} doit lui aussi verifier que
     * la cible appartient au meme workspace (defense en profondeur, cf realtime-
     * service / index.js).
     */
    @GetMapping("/users/contacts")
    @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR')")
    public java.util.List<WorkspaceUserDto> listChatContacts(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listWorkspaceUsersUseCase.execute(user.workspaceId()).stream()
                .filter(m -> "ACTIVE".equals(m.status().name()))
                .filter(m -> !m.userId().equals(user.userId()))
                .map(WorkspaceUserDto::from)
                .toList();
    }

    /**
     * BUG 6 (2026-06-07) — Active ou desactive un compte membre du workspace
     * courant. Reserve aux SUPERVISEUR. {@code active=true} repasse le compte
     * ACTIVE et re-applique le quota plan pour un EMPLOYE. {@code active=false}
     * passe INACTIVE : LoginUseCase refusera ensuite l'authentification (401).
     *
     * <p>Erreurs :
     * <ul>
     *   <li>403 si l'appelant essaie de se desactiver lui-meme.</li>
     *   <li>404 si la cible n'existe pas ou n'est pas dans le meme workspace.</li>
     *   <li>409 si la cible est un CLIENT (utiliser le flux dataroom).</li>
     *   <li>402 PLAN_LIMIT_USERS si la reactivation d'un EMPLOYE depasse le forfait.</li>
     * </ul>
     */
    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<java.util.Map<String, Object>> setUserStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable java.util.UUID userId,
            @Valid @RequestBody SetUserStatusRequest req,
            HttpServletRequest http) {
        var r = setUserStatusUseCase.execute(new SetUserStatusUseCase.Command(
                user.workspaceId(), user.userId(), userId,
                Boolean.TRUE.equals(req.active()),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return ResponseEntity.ok(java.util.Map.of(
                "userId", r.userId().toString(),
                "previousStatus", r.previousStatus().name(),
                "status", r.newStatus().name(),
                "changed", r.changed(),
                "message", r.changed()
                        ? (r.newStatus().name().equals("ACTIVE")
                            ? "Compte reactive — l'utilisateur peut a nouveau se connecter."
                            : "Compte desactive — l'utilisateur ne peut plus se connecter.")
                        : "Statut deja a la valeur demandee."
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req,
                                                      HttpServletRequest http) {
        contextePublic.poserNouveauWorkspace();
        var result = registerWorkspaceUseCase.execute(new RegisterWorkspaceUseCase.Command(
                req.workspaceName(), req.contactEmail(), req.subscriptionId(),
                req.firstName(), req.lastName(), req.phone(),
                req.email(), req.password(),
                http.getRemoteAddr(), http.getHeader("User-Agent"),
                null /* selectedPlan : route legacy /register sans wizard, pas de forfait */));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(result.workspaceId(), result.workspaceCode(),
                        result.userId(), result.emailDelivered(),
                        result.loginEmail(), result.contactEmail()));
    }

    @PostMapping("/workspace-check")
    public WorkspaceCheckResponse workspaceCheck(@Valid @RequestBody WorkspaceCheckRequest req) {
        contextePublic.poserParCode(req.workspaceCode());
        var r = checkWorkspaceUseCase.execute(req.workspaceCode());
        return new WorkspaceCheckResponse(r.workspaceId(), r.name());
    }

    @PostMapping("/verify-email")
    public VerifyEmailResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest req, HttpServletRequest http) {
        contextePublic.poserParJetonVerification(req.token());
        var r = verifyEmailUseCase.execute(new VerifyEmailUseCase.Command(
                req.token(), http.getRemoteAddr(), http.getHeader("User-Agent")));
        return new VerifyEmailResponse(r.workspaceId(), r.userId(), r.email(),
                "Email verifie avec succes. Vous pouvez maintenant vous connecter.");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest req,
                                                    HttpServletRequest http) {
        contextePublic.poserParCode(req.workspaceCode());
        resendVerificationUseCase.execute(new ResendVerificationUseCase.Command(
                req.workspaceCode(), req.email(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return ResponseEntity.accepted().build();
    }

    /**
     * Hotfix 2026-06-04 : renvoie desormais un NOUVEAU couple access+refresh
     * token (claim {@code mcp=false}). Le frontend DOIT remplacer son token
     * stocke par celui-ci AVANT d'appeler le moindre endpoint suivant — sinon
     * {@link ma.jurika.auth.infrastructure.security.ChangePasswordEnforcer}
     * relit {@code mcp=true} de l'ancien token et renvoie 403 PASSWORD_CHANGE_REQUIRED.
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public TokenResponse changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                         @Valid @RequestBody ChangePasswordRequest req,
                                         HttpServletRequest http) {
        AuthTokens t = changePasswordUseCase.execute(new ChangePasswordUseCase.Command(
                user.userId(), user.workspaceId(),
                req.oldPassword(), req.newPassword(), req.confirmPassword(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return new TokenResponse(t.userId(), t.workspaceId(),
                t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
    }

    @PostMapping("/2fa/choose-method")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> choose2faMethod(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody Choose2faMethodRequest req,
                                                 HttpServletRequest http) {
        choose2faMethodUseCase.execute(new Choose2faMethodUseCase.Command(
                user.userId(), user.workspaceId(), req.method(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/sms/send")
    @PreAuthorize("isAuthenticated()")
    public SmsOtpSendResponse sendSmsOtp(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody SmsOtpSendRequest req,
                                          HttpServletRequest http) {
        var r = sendSmsOtpUseCase.execute(new SendSmsOtpUseCase.Command(
                user.userId(), user.workspaceId(), req.purpose(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return new SmsOtpSendResponse(r.otpId(), r.maskedPhone(), r.expiresAt());
    }

    /**
     * Hotfix 2026-06-04 : si {@code purpose=2FA_SETUP}, renvoie un NOUVEAU couple
     * access+refresh (r2s=false) pour debloquer immediatement le user — meme
     * raisonnement que /change-password et /setup-2fa/confirm. Pour les autres
     * purposes (PHONE_VERIFICATION, PASSWORD_RESET), renvoie 204 No Content.
     */
    @PostMapping("/2fa/sms/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TokenResponse> verifySmsOtp(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @Valid @RequestBody SmsOtpVerifyRequest req,
                                                       HttpServletRequest http) {
        AuthTokens t = verifySmsOtpUseCase.execute(new VerifySmsOtpUseCase.Command(
                user.userId(), user.workspaceId(), req.purpose(), req.code(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        if (t == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new TokenResponse(t.userId(), t.workspaceId(),
                t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt()));
    }

    /**
     * Renvoie le PNG (binaire) du QR code TOTP pour usage direct dans &lt;img src="..."&gt;.
     * Genere le secret au 1er appel s'il n'existe pas.
     */
    @GetMapping(value = "/2fa/totp/qr-code", produces = "image/png")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getTotpQrCode(@AuthenticationPrincipal AuthenticatedUser user) {
        byte[] png = setup2faUseCase.getQrCodePng(user.userId());
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"jurika-totp-qr.png\"")
                .header("Cache-Control", "no-store")
                .body(png);
    }

    /**
     * Genere 10 codes de recuperation 2FA et les affiche UNE SEULE FOIS.
     * Tout appel ulterieur va remplacer les codes existants par de nouveaux.
     */
    @PostMapping("/2fa/recovery-codes/generate")
    @PreAuthorize("isAuthenticated()")
    public RecoveryCodesResponse generateRecoveryCodes(@AuthenticationPrincipal AuthenticatedUser user,
                                                        HttpServletRequest http) {
        var r = generateRecoveryCodesUseCase.execute(new GenerateRecoveryCodesUseCase.Command(
                user.userId(), user.workspaceId(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return RecoveryCodesResponse.of(r.plainCodes());
    }

    /**
     * Sprint 14 bis / B4 — verifie un code de recuperation 2FA et emet directement
     * les tokens (court-circuit du TOTP/SMS). RG-AU41 single-use + RG-AU42 rate
     * limit 5 / 15min applicatif (sur cle workspace+email+ip).
     */
    @PostMapping("/verify-recovery-code")
    public TokenResponse verifyRecoveryCode(@Valid @RequestBody VerifyRecoveryCodeRequest req,
                                             HttpServletRequest http) {
        contextePublic.poserParCode(req.workspaceCode());
        AuthTokens t = verifyRecoveryCodeUseCase.execute(new VerifyRecoveryCodeUseCase.Command(
                req.workspaceCode(), req.email(), req.code(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return new TokenResponse(t.userId(), t.workspaceId(),
                t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        contextePublic.poserParCode(req.workspaceCode());
        var r = loginUseCase.execute(new LoginUseCase.Command(
                req.workspaceCode(), req.email(), req.password(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        if (r.requires2fa()) {
            return LoginResponse.twoFactorRequired(r.userId(), r.workspaceId(), r.twofaMethod());
        }
        AuthTokens t = r.tokens();
        if (r.requires2faSetup()) {
            return LoginResponse.mustSetup2fa(r.userId(), r.workspaceId(), r.mustChangePassword(),
                    t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
        }
        return LoginResponse.fromTokens(r.userId(), r.workspaceId(),
                r.mustChangePassword(), r.twofaMethod(),
                t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
    }

    /**
     * CRIT-3 (audit 2026-06-02) — Endpoint public pour declencher l'envoi du code
     * SMS pendant le login (post-credentials, pre-2FA). Sans cet endpoint, un
     * user avec methode SMS etait bloque car {@code /2fa/sms/send} exige un JWT.
     *
     * <p>Securite : rate-limit cote gateway (auth-sensitive 10/min/IP) + le caller
     * doit fournir un userId+workspaceId valides. Si user.twofaMethod != SMS ou
     * user verrouille, on retourne 200 OK silencieux (anti-enumeration) sans
     * envoyer de SMS — l'attaquant ne peut donc pas mapper userIds -> phones.
     */
    @PostMapping("/login/sms/challenge")
    public org.springframework.http.ResponseEntity<Void> loginSmsChallenge(
            @Valid @RequestBody LoginSmsChallengeRequest req,
            HttpServletRequest http) {
        contextePublic.poserWorkspace(req.workspaceId());
        try {
            sendSmsOtpUseCase.execute(new SendSmsOtpUseCase.Command(
                    req.userId(), req.workspaceId(), "2FA_LOGIN",
                    http.getRemoteAddr(), http.getHeader("User-Agent")));
        } catch (RuntimeException ignored) {
            // Best-effort silencieux : on ne leak pas l'existence du user / sa methode 2FA.
            // L'echec reel est trace en audit log SMS_OTP_FAILED par les couches plus bas.
        }
        return org.springframework.http.ResponseEntity.accepted().build();
    }

    @PostMapping("/verify-2fa")
    public TokenResponse verify2fa(@Valid @RequestBody Verify2faRequest req, HttpServletRequest http) {
        // Lot L0 (E10d, meme mecanisme qu'E13a/P8) : route publique, le workspace
        // vient de la requete et doit etre pose AVANT la transaction pour que la
        // RLS s'applique sous jurika_app. La RLS limite alors la recherche de
        // l'utilisateur a ce workspace. JwtAuthFilter vide le contexte en sortie.
        contextePublic.poserWorkspace(req.workspaceId());
        AuthTokens t = verify2faUseCase.execute(new Verify2faUseCase.Command(
                req.userId(), req.workspaceId(), req.code(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return new TokenResponse(t.userId(), t.workspaceId(),
                t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
    }

    @PostMapping("/setup-2fa")
    @PreAuthorize("isAuthenticated()")
    public Setup2faResponse setup2fa(@AuthenticationPrincipal AuthenticatedUser user) {
        var r = setup2faUseCase.initiate(user.userId());
        return new Setup2faResponse(r.secret(), r.otpAuthUri(), r.qrCodePngBase64());
    }

    /**
     * 2FA obligatoire au 1er login (RG-AU30) : permet au frontend de savoir si
     * le bouton "SMS" doit s'afficher (uniquement si telephone enregistre pendant
     * l'inscription). TOTP est toujours disponible. Path sous {@code /2fa/...}
     * donc whiteliste par {@code Setup2faRequiredEnforcer} (le user est encore
     * dans l'etat requires_2fa_setup=TRUE et doit pouvoir consulter ses options).
     */
    @GetMapping("/2fa/setup-options")
    @PreAuthorize("isAuthenticated()")
    public Setup2faOptionsResponse getSetup2faOptions(@AuthenticationPrincipal AuthenticatedUser user) {
        var r = setup2faUseCase.getSetupOptions(user.userId());
        return new Setup2faOptionsResponse(r.totpAvailable(), r.smsAvailable(), r.maskedPhone());
    }

    /**
     * Hotfix 2026-06-04 : renvoie desormais un NOUVEAU couple access+refresh
     * token (claim {@code r2s=false}). Le frontend DOIT remplacer son token
     * stocke par celui-ci AVANT d'appeler le moindre endpoint suivant — sinon
     * {@link ma.jurika.auth.infrastructure.security.Setup2faRequiredEnforcer}
     * relit {@code r2s=true} de l'ancien token et renvoie 403 SETUP_2FA_REQUIRED.
     */
    @PostMapping("/setup-2fa/confirm")
    @PreAuthorize("isAuthenticated()")
    public TokenResponse confirm2fa(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody Confirm2faRequest req,
                                     HttpServletRequest http) {
        AuthTokens t = setup2faUseCase.confirm(user.userId(), req.code(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return new TokenResponse(t.userId(), t.workspaceId(),
                t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        contextePublic.poserParJetonRefresh(req.refreshToken());
        AuthTokens t = refreshTokenUseCase.execute(req.refreshToken(),
                http.getHeader("User-Agent"), http.getRemoteAddr());
        return new TokenResponse(t.userId(), t.workspaceId(),
                t.accessToken(), t.refreshToken(), t.accessExpiresAt(), t.refreshExpiresAt());
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestBody(required = false) LogoutRequest req,
                                        HttpServletRequest http) {
        String token = req == null ? null : req.refreshToken();
        logoutUseCase.execute(token, user.userId(), user.workspaceId(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> resetRequest(@Valid @RequestBody PasswordResetRequest req,
                                              HttpServletRequest http) {
        contextePublic.poserParCode(req.workspaceCode());
        resetPasswordUseCase.request(req.workspaceCode(), req.email(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> resetConfirm(@Valid @RequestBody PasswordResetConfirm req,
                                              HttpServletRequest http) {
        contextePublic.poserParJetonReset(req.token());
        resetPasswordUseCase.confirm(req.token(), req.newPassword(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    /**
     * BUG 7 (2026-06-08 chore finitions) — Met a jour l'email de notifications
     * (contact_email) du user courant. Le login_email reste immuable cote API.
     */
    @org.springframework.web.bind.annotation.PatchMapping("/me/contact-email")
    @PreAuthorize("isAuthenticated()")
    public java.util.Map<String, Object> updateContactEmail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateContactEmailRequest req,
            HttpServletRequest http) {
        var r = updateContactEmailUseCase.execute(new UpdateContactEmailUseCase.Command(
                user.userId(), user.workspaceId(), req.contactEmail(),
                http.getRemoteAddr(), http.getHeader("User-Agent")));
        return java.util.Map.of(
                "userId", r.userId().toString(),
                "loginEmail", r.loginEmail(),
                "contactEmail", r.contactEmail(),
                "previousContactEmail", r.previousContactEmail() == null ? "" : r.previousContactEmail(),
                "changed", r.changed(),
                "message", r.changed()
                        ? "Email de notifications mis a jour."
                        : "Aucun changement (l'email fourni est deja celui en place)."
        );
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public java.util.Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
        // BUG 7 (2026-06-08) — la reponse expose loginEmail (identifiant @jurika.ma,
        // lecture seule cote front) ET contactEmail (modifiable plus tard via un
        // endpoint dedie). Fallback : si la DB n'a pas encore le user (cas rare
        // d'incident RLS), on retourne ce que le claim JWT contient.
        var maybe = userRepository.findById(user.userId());
        if (maybe.isEmpty()) {
            return java.util.Map.of(
                    "userId", user.userId(),
                    "workspaceId", user.workspaceId(),
                    "email", user.email(),
                    "loginEmail", user.email(),
                    "contactEmail", user.email(),
                    "role", user.role());
        }
        var u = maybe.get();
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", user.userId());
        body.put("workspaceId", user.workspaceId());
        body.put("email", u.email()); // historique
        body.put("loginEmail", u.loginEmail());
        body.put("contactEmail", u.contactEmail());
        body.put("firstName", u.firstName());
        body.put("lastName", u.lastName());
        body.put("phone", u.phone());
        body.put("role", user.role());
        body.put("status", u.status().name());
        return body;
    }
}
