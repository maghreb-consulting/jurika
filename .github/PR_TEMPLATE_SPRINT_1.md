## Sprint 1 — Sécurisation production

Plan source : [`docs/v2/PLAN_SPRINT_1_SECURITE_PROD.md`](docs/v2/PLAN_SPRINT_1_SECURITE_PROD.md)
Roadmap : [`docs/v2/ROADMAP_PRODUCTION_SAAS.md § 4 Sprint 1`](docs/v2/ROADMAP_PRODUCTION_SAAS.md)

### Périmètre livré

- [x] **TASK 1** Génération paire RSA 2048 (`infrastructure/secrets/jwt/`)
- [x] **TASK 2** `JwtKeyConfig` multi-source PEM/HMAC + `JwtProperties` (binding `jurika.jwt.*`)
- [x] **TASK 3** Split `JwtSigningKeyProvider` (auth) vs `JwtPublicKeyProvider` (6 services)
- [x] **TASK 4** Bascule HS256 → RS256 par défaut + 2 tests RSA (`JwtTokenIssuerRsaTest`, `JwtAuthFilterRsaTest`)
- [x] **TASK 5** Refresh max 5 sessions actives (`SessionLimitEnforcer`) + cleanup job nuit 03h (RG-SAAS-12)
- [x] **TASK 6** Rate limiting Gateway (Redis) : 500/min user + 10/min routes sensibles (RG-SAAS-03)
- [x] **TASK 7** Headers OWASP : HSTS, CSP, X-Frame-Options DENY, X-Content-Type-Options, Referrer-Policy, Permissions-Policy
- [x] **TASK 8** HTTPS/TLS 1.3 Nginx + mkcert dev + procédure Let's Encrypt prod
- [x] **TASK 9** Doppler dev secrets + AWS Secrets Manager prod + gitleaks pre-commit (`.githooks/pre-commit`)
- [x] **TASK 10** CORS dynamique table `workspace_allowed_origins` + DynamicCorsConfigurationSource (poll 5 min)
- [x] **TASK 11** Smoke tests `tests/integration/sprint-1-security.http` (11 étapes)

### Tests

```
mvn test  (jurika-common + auth-service + gateway-service)
  jurika-common      → 13/13 verts
  auth-service       → 106/106 verts
  gateway-service    →   3/3 verts
  Total              → 122/122 verts
```

### Migrations DB

- `V6__refresh_token_indexes.sql` (auth) — index `(user_id, workspace_id)` + `(expires_at)` partiels
- `V7__workspace_allowed_origins.sql` (auth) — table + seed origines `localhost:5173` et `app.jurika.ma`

### Breaking changes

⚠️ Les anciens JWT HS256 émis avant le déploiement seront invalidés à la rotation. Les utilisateurs devront se reconnecter une fois.

Pour transition douce : `JWT_ALGORITHM=HS256` réactive le fallback (legacy dev uniquement).

### Sécurité

- Les `*.pem` / `*.key` / `*.crt` / `*.cer` sont gitignorés.
- Aucun secret n'est committé (gitleaks vérifié).
- Documentation rotation 12 mois pour clés JWT (cf. `infrastructure/secrets/jwt/README.md`).

### Démarrage local après merge

```powershell
# 1. Générer la paire RSA (une fois)
cd infrastructure/secrets/jwt
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
openssl pkcs8 -topk8 -inform PEM -in jwt-private.pem -outform PEM -nocrypt -out jwt-private-pkcs8.pem

# 2. Activer le hook gitleaks
git config core.hooksPath .githooks

# 3. Démarrer
.\scripts\start-with-doppler.ps1   # ou ./scripts/start-all.ps1 si pas Doppler
```

### Validation post-déploiement (prod)

- [ ] Login → access RS256 (vérifier `alg: RS256` sur jwt.io)
- [ ] 6 logins → max 5 refresh actifs en DB
- [ ] 11ème POST `/api/v1/auth/login` → `429 Too Many Requests`
- [ ] `curl -I https://api.jurika.ma` → 6 headers sécurité présents
- [ ] Mozilla Observatory : grade ≥ A
- [ ] SSL Labs : A+
- [ ] Origine non whitelistée → bloquée (pas de `Access-Control-Allow-Origin`)
