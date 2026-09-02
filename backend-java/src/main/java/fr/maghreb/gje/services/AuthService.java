package fr.maghreb.gje.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.warrenstrange.googleauth.*;
import fr.maghreb.gje.config.TenantContext;
import fr.maghreb.gje.dto.auth.*;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import fr.maghreb.gje.security.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final HistoriqueRepository historiqueRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EncryptionUtil encryptionUtil;
    private final TokenSessionService tokenSessionService;
    private final SessionActivityService sessionActivityService;
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    private final Map<String, CacheEntry> fallbackCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        int attempts;
        LocalDateTime expiresAt;
        CacheEntry(int attempts, LocalDateTime expiresAt) {
            this.attempts = attempts;
            this.expiresAt = expiresAt;
        }
    }

    private int getAttempts(String userId) {
        String key = "otp_attempts:" + userId;
        CacheEntry entry = fallbackCache.get(key);
        if (entry != null && entry.expiresAt.isAfter(LocalDateTime.now())) {
            return entry.attempts;
        }
        return 0;
    }

    private int incrementAndGetAttempts(String userId) {
        String key = "otp_attempts:" + userId;
        fallbackCache.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(LocalDateTime.now()));
        CacheEntry entry = fallbackCache.getOrDefault(key, new CacheEntry(0, LocalDateTime.now().plusMinutes(10)));
        entry.attempts++;
        fallbackCache.put(key, entry);
        return entry.attempts;
    }

    private void resetAttempts(String userId) {
        String key = "otp_attempts:" + userId;
        fallbackCache.remove(key);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Generate unique workspace code
        String code = generateWorkspaceCode(request.getWorkspaceName());

        // Create workspace
        Workspace workspace = Workspace.builder()
                .name(request.getWorkspaceName())
                .codeWorkspace(code)
                .isActive(true)
                .build();
        workspace = workspaceRepository.save(workspace);

        // Set tenant context
        TenantContext.setTenantId(workspace.getId().toString());

        // Create first superviseur
        User user = User.builder()
                .workspaceId(workspace.getId())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.SUPERVISEUR)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        String tempToken = jwtService.generateTempToken(user);

        return AuthResponse.builder()
                .tempToken(tempToken)
                .requires2fa(false)
                .requires2faSetup(true)
                .userId(user.getId().toString())
                .workspaceId(workspace.getId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(300L)
                .build();
    }

    public Map<String, Object> verifyWorkspace(String code) {
        Optional<Workspace> ws = workspaceRepository.findByCodeWorkspace(code);
        Map<String, Object> result = new HashMap<>();
        if (ws.isPresent() && ws.get().getIsActive()) {
            result.put("valid", true);
            result.put("workspace_id", ws.get().getId().toString());
            result.put("name", ws.get().getName());
        } else {
            result.put("valid", false);
            result.put("message", "Workspace inconnu ou inactif");
        }
        return result;
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(noRollbackFor = RuntimeException.class)
    public AuthResponse login(LoginRequest request) {
        UUID workspaceId = null;
        if (request.getWorkspaceId() != null && !request.getWorkspaceId().isEmpty()) {
            workspaceId = UUID.fromString(request.getWorkspaceId());
        } else if (request.getWorkspaceCode() != null && !request.getWorkspaceCode().isEmpty()) {
            workspaceId = workspaceRepository.findByCodeWorkspace(request.getWorkspaceCode())
                .map(Workspace::getId)
                .orElseThrow(() -> new RuntimeException("Workspace inconnu"));
        } else {
            throw new RuntimeException("Workspace non spécifié");
        }

        TenantContext.setTenantId(workspaceId.toString());

        User user = userRepository
            .findByEmailAndWorkspaceId(request.getEmail(), workspaceId)
            .orElseThrow(() -> new RuntimeException("Identifiants incorrects"));

        // Sécurité supplémentaire : Vérifier que l'utilisateur appartient bien au workspace demandé
        if (!user.getWorkspaceId().equals(workspaceId)) {
            throw new RuntimeException("Accès interdit : l'utilisateur n'appartient pas à ce workspace");
        }

        // Check if locked
        if (user.getLockedUntil() != null &&
            LocalDateTime.now().isBefore(user.getLockedUntil())) {
            throw new RuntimeException("LOCKED:" + user.getLockedUntil());
        }

        // Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            
            // Set Tenant ID for RLS natively in this transaction
            entityManager.createNativeQuery("SET LOCAL app.current_tenant_id = '" + workspaceId.toString() + "'")
                    .executeUpdate();

            if (attempts >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                user.setFailedAttempts(0);
                
                entityManager.createNativeQuery("UPDATE users SET failed_attempts = :attempts, locked_until = :lockedUntil WHERE id = :userId")
                    .setParameter("attempts", user.getFailedAttempts())
                    .setParameter("lockedUntil", user.getLockedUntil())
                    .setParameter("userId", user.getId())
                    .executeUpdate();
                    
                throw new RuntimeException("LOCKED:" + user.getLockedUntil());
            } else {
                user.setFailedAttempts(attempts);
                
                entityManager.createNativeQuery("UPDATE users SET failed_attempts = :attempts WHERE id = :userId")
                    .setParameter("attempts", attempts)
                    .setParameter("userId", user.getId())
                    .executeUpdate();
                    
                throw new RuntimeException("INVALID_PASSWORD:" + (5 - attempts));
            }
        }

        // Set Tenant ID for RLS natively for successful login updates
        entityManager.createNativeQuery("SET LOCAL app.current_tenant_id = '" + workspaceId.toString() + "'")
                .executeUpdate();

        // Reset failed attempts
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        entityManager.createNativeQuery("UPDATE users SET failed_attempts = 0, last_login = :lastLogin WHERE id = :userId")
            .setParameter("lastLogin", user.getLastLogin())
            .setParameter("userId", user.getId())
            .executeUpdate();

        // Reset OTP attempts on new login
        resetAttempts(user.getId().toString());

        String tempToken = jwtService.generateTempToken(user);

        // If 2FA enabled return temp token
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            String decryptedSecret = encryptionUtil.decrypt(user.getTotpSecret());
            
            // Re-generate QR for login convenience (User's request)
            String qrCode = generateQrCode(GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
                "GJE Platform", user.getEmail(), 
                new GoogleAuthenticatorKey.Builder(decryptedSecret).build()));

            return AuthResponse.builder()
                    .tempToken(tempToken)
                    .requires2fa(true)
                    .requires2faSetup(false)
                    .qrCode(qrCode)
                    .expiresIn(300L)
                    .build();
        }

        // No 2FA — force setup
        return AuthResponse.builder()
                .tempToken(tempToken)
                .requires2fa(false)
                .requires2faSetup(true)
                .message("Vous devez configurer la 2FA")
                .userId(user.getId().toString())
                .workspaceId(workspaceId.toString())
                .email(user.getEmail())
                .role(user.getRole().name())
                .expiresIn(300L)
                .build();
    }

    @Transactional
    public Map<String, String> setup2FA(String userId, String workspaceId) {
        TenantContext.setTenantId(workspaceId);
        entityManager.createNativeQuery("SET LOCAL app.current_tenant_id = '" + workspaceId + "'")
                .executeUpdate();

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();
        user.setTotpSecret(encryptionUtil.encrypt(secret));
        userRepository.save(user);

        String otpAuthUrl = GoogleAuthenticatorQRGenerator
            .getOtpAuthTotpURL(
                "GJE Platform",
                user.getEmail(),
                key
            );

        String qrCode = generateQrCode(otpAuthUrl);

        Map<String, String> result = new HashMap<>();
        result.put("qr_code_base64", qrCode);
        result.put("secret", secret);
        result.put("message",
            "Scannez ce QR code avec Google/Microsoft Authenticator");
        return result;
    }

    @Transactional
    public AuthResponse verify2FA(VerifyTotpRequest request) {
        Claims claims = jwtService.extractAllClaims(request.getTempToken());
        String tokenType = claims.get("type", String.class);

        if (!"TEMP".equals(tokenType) && !"ACCESS".equals(tokenType)) {
            throw new RuntimeException("Token invalide");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String workspaceId = claims.get("workspace_id", String.class);

        int failures = getAttempts(userId.toString());
        if (failures >= 2) {
            throw new RuntimeException("MAX_OTP_ATTEMPTS:Nombre maximum de tentatives OTP atteint. Reconnectez-vous.");
        }

        TenantContext.setTenantId(workspaceId);
        entityManager.createNativeQuery("SET LOCAL app.current_tenant_id = '" + workspaceId + "'")
                .executeUpdate();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Sécurité supplémentaire : Vérifier que le token appartient au même workspace que l'utilisateur
        if (!user.getWorkspaceId().toString().equals(workspaceId)) {
            throw new RuntimeException("Accès interdit : le token n'appartient pas à ce workspace");
        }

        String decryptedSecret = encryptionUtil
            .decrypt(user.getTotpSecret());
        
        boolean valid = gAuth.authorize(
            decryptedSecret,
            Integer.parseInt(request.getOtpCode())
        );

        if (!valid) {
            int currentFailures = incrementAndGetAttempts(userId.toString());
            if (currentFailures >= 2) {
                throw new RuntimeException("MAX_OTP_ATTEMPTS:Nombre maximum de tentatives OTP atteint. Reconnectez-vous.");
            }
            throw new RuntimeException("Code OTP invalide");
        }

        resetAttempts(userId.toString());

        // Activate 2FA if first time
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            user.setTotpEnabled(true);
            userRepository.save(user);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // ** INVALIDE ANCIENS TOKENS ET ENREGISTRE NOUVEAUX **
        tokenSessionService.registerSession(user.getId().toString(), accessToken, refreshToken);
        
        // ** INITIALIZE SLIDING SESSION ACTIVITY **
        sessionActivityService.touch(user.getId().toString());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .requires2fa(false)
                .userId(user.getId().toString())
                .workspaceId(user.getWorkspaceId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(1800L)
                .build();
    }

    public void logout(String userId) {
        tokenSessionService.invalidateSession(userId);
        sessionActivityService.invalidate(userId);
    }

    @Transactional
    public AuthResponse refreshToken(String incomingRefreshToken) {
        Claims claims = jwtService.extractAllClaims(incomingRefreshToken);
        String tokenType = claims.get("type", String.class);
        
        if (!"REFRESH".equals(tokenType)) {
            throw new RuntimeException("Refresh token invalide");
        }
        
        String userId = claims.getSubject();
        
        // Verify token is active in Redis (Fail-open managed internally)
        if (!tokenSessionService.isTokenValid(userId, incomingRefreshToken, true)) {
            throw new RuntimeException("Session invalide, reconnectez-vous");
        }
        
        String workspaceId = claims.get("workspace_id", String.class);
        TenantContext.setTenantId(workspaceId);
        entityManager.createNativeQuery("SET LOCAL app.current_tenant_id = '" + workspaceId + "'").executeUpdate();
        
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new RuntimeException("User not found"));
            
        // Issue new token pair
        String newAccessToken = jwtService.generateAccessToken(user);
        // Sometimes only access is refreshed, but CDC says: "invalider l'ancien accessToken, émettre un nouveau, mettre à jour Redis"
        // I will reissue both to slide the session duration securely, OR just keep the same refreshToken. 
        // Best practice: Reissue both for optimal security (refresh token rotation)
        String newRefreshToken = jwtService.generateRefreshToken(user);
        
        tokenSessionService.registerSession(userId, newAccessToken, newRefreshToken);
        
        // ** REFRESH SLIDING SESSION ACTIVITY **
        sessionActivityService.touch(userId);
        
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .requires2fa(false)
                .userId(user.getId().toString())
                .workspaceId(user.getWorkspaceId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(1800L)
                .build();
    }

    private String generateQrCode(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(
                content, BarcodeFormat.QR_CODE, 300, 300);
            BufferedImage image = new BufferedImage(
                300, 300, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 300; x++)
                for (int y = 0; y < 300; y++)
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," +
                Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("QR Code generation error", e);
        }
    }

    private String generateWorkspaceCode(String name) {
        String prefix = name.substring(0, Math.min(2, name.length()))
            .toUpperCase();
        String year = String.valueOf(
            LocalDateTime.now().getYear());
        String code = prefix + "-" + year;
        int counter = 1;
        while (workspaceRepository.existsByCodeWorkspace(code)) {
            code = prefix + "-" + year + "-" + counter++;
        }
        return code;
    }

    @Transactional
    public Map<String, Object> reset2FA(UUID targetUserId, User requestingUser, String ipAddress) {
        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new RuntimeException("Utilisateur cible non trouvé"));

        if (requestingUser.getId().equals(targetUserId)) {
            throw new RuntimeException("FORBIDDEN:Vous ne pouvez pas réinitialiser votre propre 2FA");
        }

        if (requestingUser.getRole() == User.Role.SUPER_ADMIN) {
            if (targetUser.getRole() != User.Role.SUPERVISEUR) {
                throw new RuntimeException("FORBIDDEN:SUPER_ADMIN ne peut réinitialiser que le 2FA des SUPERVISEUR");
            }
        } else if (requestingUser.getRole() == User.Role.SUPERVISEUR) {
            if (targetUser.getRole() != User.Role.EMPLOYE) {
                throw new RuntimeException("FORBIDDEN:SUPERVISEUR ne peut réinitialiser que le 2FA des EMPLOYE");
            }
            if (!requestingUser.getWorkspaceId().equals(targetUser.getWorkspaceId())) {
                throw new RuntimeException("FORBIDDEN:Vous ne pouvez réinitialiser que les employés de votre workspace");
            }
        } else {
            throw new RuntimeException("FORBIDDEN:Accès non autorisé");
        }

        TenantContext.setTenantId(targetUser.getWorkspaceId().toString());
        entityManager.createNativeQuery("SET LOCAL app.current_tenant_id = '" + targetUser.getWorkspaceId().toString() + "'")
                .executeUpdate();

        targetUser.setTotpEnabled(false);
        targetUser.setTotpSecret(null);
        userRepository.save(targetUser);

        tokenSessionService.invalidateSession(targetUserId.toString());
        sessionActivityService.invalidate(targetUserId.toString());

        Historique historique = Historique.builder()
            .workspaceId(targetUser.getWorkspaceId())
            .userId(targetUserId)
            .action("2FA_RESET")
            .ancienneValeur("RESET_BY:" + requestingUser.getId())
            .nouvelleValeur("TOTP_DISABLED")
            .ipAddress(ipAddress)
            .build();
        historiqueRepository.save(historique);

        return Map.of(
            "success", true,
            "message", "2FA réinitialisé. L'utilisateur devra reconfigurer son authenticator à la prochaine connexion."
        );
    }
}
