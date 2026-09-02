## Sprint 3 — Auth Hardening Phase 1.5 (finition)

Plan source : [`docs/v2/PLAN_SPRINT_3_AUTH_HARDENING.md`](../docs/v2/PLAN_SPRINT_3_AUTH_HARDENING.md)
Roadmap : [`docs/v2/ROADMAP_PRODUCTION_SAAS.md § 4 Sprint 3`](../docs/v2/ROADMAP_PRODUCTION_SAAS.md)

> 💡 Sprint de **finition** sur la Phase 1.5 Auth — ~80 % du périmètre était
> déjà livré côté code (DnsMxValidator, RegisterWorkspaceUseCase V2 strict,
> TOTP, SMS OTP, recovery codes BCrypt, ChangePasswordEnforcer, pages React).
> **3-4 jours réels** au lieu de la semaine initialement budgetée.

> Empilement : cette PR se base sur `feat/sprint-2-observabilite` (non encore
> mergée sur `main`). À reviewer / merger **après** Sprint 2.

---

### Périmètre livré (6 commits + docs)

- [x] **TASK 1** TwilioSmsSender prod (`@ConditionalOnProperty provider=twilio`),
      fallback `LoggerSmsSender` dev, métriques Prometheus
      `jurika_auth_sms_{sent,failed}_total` taggées provider + reason,
      `SmsDeliveryException`, validation init SID/Token/from-number,
      `SECRETS_INVENTORY.md` + `.env.example` mis à jour.
- [x] **TASK 2** Branding Maghreb Consulting : fragment Thymeleaf `_base.html`
      (header + footer mentions légales + warning anti-phishing + unsubscribe
      CNDP). Refonte `welcome` + `verify-email`. **5 nouveaux templates** :
      `password-changed`, `login-new-device`, `2fa-enabled`,
      `recovery-codes-regenerated`, `ticket-assigned` (RG-AU30/33/34 +
      RG-SAAS-08). Détection nouvelle IP/UA via
      `RefreshTokenRepository.hasRecentSessionFromDevice` (lookback 30j) →
      email + audit `LOGIN_NEW_DEVICE`. Emails best-effort dans
      `ChangePasswordUseCase`, `Setup2faUseCase.confirm()`,
      `GenerateRecoveryCodesUseCase` (uniquement à la régénération).
- [x] **TASK 3** UI recovery codes one-shot (RG-AU33) : composant
      `RecoveryCodesDisplay` réutilisable (grille 10 codes + download
      `.txt` + copy clipboard + **checkbox obligatoire**) + intégration
      dans `Setup2FAPage` (parcours 3 états) et `ProfileSecurityPage`
      (Drawer avec `onClose` verrouillé pour forcer la sauvegarde).
- [x] **TASK 4** Tests e2e `OnboardingFlowE2ETest` (MockMvc + PostgreSQL
      TestContainers) — **5 scénarios** : happy path TOTP complet, SMS
      PHONE_VERIFICATION, recovery codes regen email, token email expiré,
      DnsMxValidator KO. Mocks `EmailSender` / `SmsSender` /
      `EventPublisher` / `DnsMxValidator`. JWT HS256 en test.
- [x] **TASK 5** Guide PDF onboarding cabinet :
      `docs/v2/Guide_Onboarding_Cabinet.{md,pdf}` (8 pages A4, non-tech)
      destiné à la direction Maghreb Consulting. Script
      `scripts/build_onboarding_pdf.py` (reportlab, sans pandoc).
- [x] **TASK 6** Mise à jour ROADMAP (avancement 35 % → 40 %, decision log)
      + CLAUDE.md (bloc Sprint 3 livré + Phase 1.5 strikethrough).

### Statistiques

```
mvn test -pl jurika-common,auth-service
  jurika-common      →  11/11 verts (+3 nouveaux SMS metrics)
  auth-service       → 130/130 verts (125 unit + 5 e2e TestContainers)
  Total              → 141/141 verts

npx tsc -b (frontend-react) : exit 0
vite build                  : ✓ 2528 modules transformes (4.61s)
```

### Configuration nouvelle

```yaml
jurika:
  sms:
    provider: ${SMS_PROVIDER:logger}   # logger | twilio
    twilio:
      account-sid: ${TWILIO_ACCOUNT_SID:}
      auth-token: ${TWILIO_AUTH_TOKEN:}
      from-number: ${TWILIO_FROM_NUMBER:}
  auth:
    new-device-lookback-days: 30
  email:
    security-page-url: ${FRONTEND_URL}/account/security
```

Secrets à provisionner en prod via Doppler / AWS Secrets Manager :
`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`,
`SMS_PROVIDER=twilio`.

### Nouvelles métriques Prometheus

- `jurika_auth_sms_sent_total{provider="twilio|logger"}`
- `jurika_auth_sms_failed_total{provider="twilio", reason="api_error_<code>|unexpected"}`

### Nouveaux audits

- `LOGIN_NEW_DEVICE` — connexion depuis IP+UA inconnue (lookback 30j)
- `RECOVERY_CODES_REGENERATED` — distinct de `RECOVERY_CODES_GENERATED`

### Dette technique connue (hors scope Sprint 3)

- `LoginUseCase` ligne 164 : `Result(false, false, false, ...)` hardcode
  `mustChangePassword=false` dans le body de `/login`. Le claim JWT `mcp`
  porte la bonne valeur et `ChangePasswordEnforcer` couvre la contrainte
  via le filtre 403 — donc **non bloquant**, mais incohérent côté API à
  corriger Sprint 4+.
- Pas de framework de test JS dans `frontend-react` (`vitest` absent) — la
  couverture du composant `RecoveryCodesDisplay` est exercée indirectement
  via les e2e backend. À ajouter Sprint 14 (CI/CD).
- Endpoint `/auth/verify-recovery-code` (login avec code de récupération)
  non implémenté côté backend — nice-to-have V2.

### Test plan (smoke)

- [ ] `mvn -pl auth-service test` → 130/130 verts (Docker daemon requis pour
      OnboardingFlowE2ETest TestContainers)
- [ ] `mvn -pl jurika-common test` → 11/11 verts
- [ ] `npx tsc -b` dans `frontend-react/` → exit 0
- [ ] Workflow d'inscription manuel via UI : signup → email Maghreb
      Consulting brandé reçu → activation → 1er login → change MDP → 2FA
      TOTP → écran 10 codes + download .txt + checkbox → continuer →
      logout → relogin avec 2FA → /me OK
- [ ] Vérifier l'email `login-new-device` envoyé sur 2nd login depuis un
      nouveau navigateur
- [ ] Régénérer les codes depuis Profile → Sécurité → vérifier email
      `recovery-codes-regenerated` reçu
- [ ] (Prod) Tester `SMS_PROVIDER=twilio` avec sandbox Twilio sur un
      numéro test

### Migrations DB

Aucune migration nouvelle dans ce sprint. Seul `refresh_tokens` (déjà
existant V6) est interrogé via nouveau JPQL pour la détection nouveau
device.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
