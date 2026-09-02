# JWT Keypair — infrastructure/secrets/jwt

Ce dossier contient la paire de clés RSA 2048 utilisée pour la signature/vérification des JWT JURIKA.

| Fichier | Rôle | Lu par |
|---|---|---|
| `jwt-private-pkcs8.pem` | Clé privée (PKCS8, signature) | `auth-service` uniquement |
| `jwt-public.pem` | Clé publique (vérification) | les 6 autres services + auth-service local |
| `jwt-private.pem` | Forme PKCS1 (legacy, non utilisée par l'app) | — |

⚠️ **Les fichiers `*.pem` sont gitignorés.** Seuls `.gitkeep` et ce `README.md` sont versionnés.

## Régénération locale (dev)

```bash
cd infrastructure/secrets/jwt
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
openssl pkcs8 -topk8 -inform PEM -in jwt-private.pem -outform PEM -nocrypt -out jwt-private-pkcs8.pem
```

## Vérification

```bash
openssl rsa -in jwt-private-pkcs8.pem -check -noout       # → RSA key ok
openssl rsa -in jwt-public.pem -pubin -noout -text        # → Public-Key: (2048 bit)
```

## Production

- **NE PAS** committer les `.pem`.
- Stocker via Doppler / AWS Secrets Manager / HashiCorp Vault (cf. TASK 9 du Sprint 1).
- Montage volume Docker en lecture seule : `./infrastructure/secrets/jwt:/run/secrets/jwt:ro`.
- Rotation prévue : **12 mois** (cf. ROADMAP_PRODUCTION_SAAS.md § 6 decisions log).

## Variables d'environnement consommatrices

```yaml
jurika:
  jwt:
    algorithm: RS256
    public-key-path: /run/secrets/jwt/jwt-public.pem
    private-key-path: /run/secrets/jwt/jwt-private-pkcs8.pem  # auth-service uniquement
```
