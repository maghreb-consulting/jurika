package ma.jurika.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.jurika.auth.api.dto.SignupCabinetRequest;
import ma.jurika.auth.application.SignupCabinetUseCase;
import ma.jurika.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sprint 11 TASK 2 — Endpoint public de signup self-service "cabinet".
 *
 * Whitelist gateway : /api/v1/public/** (rate-limit Redis 30/min defense
 * en profondeur en plus du rate-limit applicatif IP 5/h - RG-SU03).
 *
 * Body : {@link SignupCabinetRequest} (Bean Validation @Valid).
 * Reponse : 201 + { workspaceCode, adminEmail, trialEndsAt, verifyEmailSent }.
 */
@RestController
@RequestMapping("/api/v1/public/signup")
public class PublicSignupController {

    private static final Logger log = LoggerFactory.getLogger(PublicSignupController.class);

    private final SignupCabinetUseCase signupCabinetUseCase;
    /**
     * Fix 2026-06-07 : flag operationnel pour forcer l'envoi du mail welcome
     * AU SIGNUP (sans attendre la validation du paiement). Par defaut TRUE
     * (= comportement billing actuel : mail differe jusqu'au paiement).
     * En dev, mettre {@code JURIKA_SIGNUP_DEFER_CREDENTIALS_ALLOWED=false}
     * dans {@code .env.local} pour que le mail parte au signup quoi qu'envoie
     * le frontend -- evite de devoir aller jusqu'a la Step Paiement pour
     * tester Brevo / le contenu du welcome.html.
     */
    private final boolean deferCredentialsAllowed;
    /** Lot L0 (E13a) : le workspace cree devient le workspace courant avant la transaction. */
    private final ma.jurika.auth.application.ContexteWorkspacePublic contextePublic;

    public PublicSignupController(SignupCabinetUseCase signupCabinetUseCase,
                                   @Value("${jurika.signup.defer-credentials-allowed:true}") boolean deferCredentialsAllowed,
                                   ma.jurika.auth.application.ContexteWorkspacePublic contextePublic) {
        this.signupCabinetUseCase = signupCabinetUseCase;
        this.contextePublic = contextePublic;
        this.deferCredentialsAllowed = deferCredentialsAllowed;
        if (!deferCredentialsAllowed) {
            log.warn("Signup defer-credentials DESACTIVE par config -- le mail welcome partira AU SIGNUP "
                    + "meme si le wizard demande de le differer. Ne pas activer en prod.");
        }
    }

    @PostMapping("/cabinet")
    public ResponseEntity<Map<String, Object>> signupCabinet(
            @Valid @RequestBody SignupCabinetRequest body,
            HttpServletRequest request) {

        if (!body.isCguAccepted()) {
            throw new ValidationException("CGU non acceptees.");
        }

        String ip = resolveClientIp(request);
        String ua = request.getHeader("User-Agent");

        // Fix 2026-06-07 : si jurika.signup.defer-credentials-allowed=false (dev),
        // on FORCE deferCredentials=false cote use case quoi qu'envoie le wizard
        // -> RegisterWorkspaceUseCase declenche l'envoi du mail welcome
        // immediatement via SmtpEmailSender (Brevo en preprod, MailHog en dev).
        boolean effectiveDefer = body.isDeferCredentials() && deferCredentialsAllowed;
        if (body.isDeferCredentials() && !deferCredentialsAllowed) {
            log.info("Signup defer-credentials demande par le front mais DESACTIVE par config -- mail welcome envoye immediatement.");
        }

        contextePublic.poserNouveauWorkspace();
        SignupCabinetUseCase.Result result = signupCabinetUseCase.execute(
                new SignupCabinetUseCase.Command(
                        body.getWorkspaceName(),
                        body.getFirstName(),
                        body.getLastName(),
                        body.getPhone(),
                        body.getEmail(),
                        body.getIce(),
                        body.getCity(),
                        body.getSelectedPlan(),
                        body.getProfessionalType(),
                        ip,
                        ua,
                        effectiveDefer
                )
        );

        // HIGH-4 (audit 2026-06-02) : verifyEmailSent reflete maintenant l'envoi reel
        // de l'email welcome (etait hardcode true -> frontend invitait l'utilisateur a
        // consulter sa boite alors que SMTP avait pu silencieusement echouer).
        // Si false, le frontend affiche un encart "Email non envoye — contactez support".
        // BUG 14 (2026-06-07) — message variant si paiement defere ; tokens
        // transitoires en bonus pour permettre /billing/prepare-payment depuis
        // le wizard Step Paiement.
        // BUG 7 (2026-06-08) — la reponse expose loginEmail (identifiant
        // @jurika.ma genere) + contactEmail (email perso). adminEmail conserve
        // pour compat e2e.
        String message;
        if (effectiveDefer) {
            message = "Cabinet cree. Vos identifiants seront envoyes apres validation du paiement.";
        } else if (result.emailDelivered()) {
            message = "Cabinet cree. Verifiez votre email pour activer votre compte.";
        } else {
            message = "Cabinet cree mais l'email d'activation n'a pas pu etre envoye. "
                    + "Contactez support@jurika.ma en mentionnant votre code workspace : " + result.workspaceCode();
        }
        java.util.HashMap<String, Object> resp = new java.util.HashMap<>();
        resp.put("workspaceCode", result.workspaceCode());
        resp.put("workspaceId", result.workspaceId().toString());
        resp.put("adminUserId", result.userId().toString());
        resp.put("adminEmail", body.getEmail()); // historique : email perso fourni
        resp.put("loginEmail", result.loginEmail());
        resp.put("contactEmail", result.contactEmail());
        resp.put("professionalType", body.getProfessionalType()); // echo (null si non fourni)
        resp.put("verifyEmailSent", result.emailDelivered());
        resp.put("message", message);
        if (result.accessToken() != null) {
            resp.put("accessToken", result.accessToken());
            resp.put("refreshToken", result.refreshToken());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) return xRealIp.trim();
        return request.getRemoteAddr();
    }
}
