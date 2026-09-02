# Secrets Inventory — JURIKA

> Inventaire de tous les secrets manipulés par la plateforme.
> Source de vérité : Doppler (dev/staging) et AWS Secrets Manager (prod).
> Voir aussi [`infrastructure/secrets/jwt/README.md`](jwt/README.md) et [`tls/README.md`](tls/README.md).

| Variable | Service(s) | Sensibilité | Rotation cible |
|---|---|---|---|
| `JWT_PRIVATE_KEY_PATH` | auth-service | Critique | 12 mois |
| `JWT_PUBLIC_KEY_PATH` | tous services | Faible (publique) | 12 mois (avec la privée) |
| `JWT_SECRET` (HS256 legacy) | tous | Critique (si HS256 actif) | 6 mois |
| `POSTGRES_PASSWORD` | tous | Critique | 6 mois |
| `AES_SECRET_KEY` / `AES_MASTER_KEY` | auth, ticket, dataroom | **Critique — jamais rotater** | Jamais (chiffrement at-rest) |
| `RABBITMQ_PASSWORD` | tous | Élevée | 6 mois |
| `REDIS_PASSWORD` | gateway, realtime, auth, supervision | Moyenne | 6 mois |
| `MINIO_ROOT_PASSWORD` | dataroom, ai | Élevée | 6 mois |
| `SMTP_PASSWORD` | auth | Élevée | 6 mois |
| `OPENAI_API_KEY` | ai | Élevée | Sur fuite uniquement |
| `STRIPE_SECRET_KEY` | (sprint 12 — billing) | Critique | À la demande |
| `TWILIO_ACCOUNT_SID` | auth (sprint 3 SMS OTP) | Élevée | 12 mois |
| `TWILIO_AUTH_TOKEN` | auth (sprint 3 SMS OTP) | Critique | 6 mois |
| `TWILIO_FROM_NUMBER` | auth (sprint 3 SMS OTP) | Faible | À la demande |
| `SMS_PROVIDER` | auth | Faible (`logger` / `twilio`) | À la demande |
| `FIREBASE_PRIVATE_KEY` | (sprint 16 push mobile) | Élevée | 12 mois |
| `CORS_ALLOWED_ORIGINS` | gateway | Faible | Configurable |

## Procédure dev (Doppler)

```powershell
# Installation
choco install doppler

# Authentification
doppler login

# Setup local
cd projet
doppler setup    # selectionner projet "jurika" / env "dev"

# Renseigner les secrets
doppler secrets set JWT_ALGORITHM=RS256
doppler secrets set POSTGRES_PASSWORD=<...>
doppler secrets set AES_SECRET_KEY=<...>
# ... cf. .env.example pour la liste complete
```

Démarrage du stack :

```powershell
doppler run -- docker compose -f infrastructure/docker-compose.yml up -d
# ou
.\scripts\start-with-doppler.ps1
```

## Procédure prod (AWS Secrets Manager)

1. Créer un secret par variable critique : `jurika/prod/jwt-private-key`, `jurika/prod/db-password`, etc.
2. Le service consommateur le récupère au démarrage via IAM role ECS/EKS (jamais via `.env`).
3. Rotation orchestrée par Lambda + EventBridge (cf. ROADMAP Sprint 14 — CI/CD).
4. Audit log d'accès activé sur tous les secrets (CloudTrail).

## Anti-fuite : gitleaks pre-commit

Voir [`.husky/pre-commit`](../../.husky/pre-commit). Bloque tout commit contenant un pattern de secret connu (AWS keys, JWT, RSA, OpenAI, Stripe, etc.).

```powershell
# Verification manuelle
gitleaks detect --no-banner --redact
gitleaks protect --staged --no-banner --redact
```

## Règles

- **JAMAIS** committer un fichier `.env` (uniquement `.env.example` avec placeholders).
- **JAMAIS** committer un fichier `*.pem`, `*.key`, `*.crt`.
- Toute fuite avérée → rotation immédiate + audit log + déclaration RGPD/CNDP si données utilisateur impactées (RG-SAAS-05).
