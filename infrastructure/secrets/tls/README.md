# TLS certificates — infrastructure/secrets/tls

| Fichier attendu | Rôle | Source |
|---|---|---|
| `jurika-local.pem` | Certificat (chaîne) | mkcert (dev) / Let's Encrypt (prod) |
| `jurika-local-key.pem` | Clé privée | mkcert (dev) / Let's Encrypt (prod) |

⚠️ **Les fichiers `*.pem` / `*.key` / `*.crt` sont gitignorés.** Ne JAMAIS committer un certificat ou une clé privée.

## Dev local (mkcert)

```powershell
# 1) Installer mkcert (Windows : choco install mkcert)
choco install mkcert

# 2) Installer la CA racine locale dans le store de confiance
mkcert -install

# 3) Generer le certificat (depuis la racine du repo)
cd infrastructure/secrets/tls
mkcert -cert-file jurika-local.pem -key-file jurika-local-key.pem `
    jurika.local "*.jurika.local" localhost 127.0.0.1 ::1
```

Ajouter à `C:\Windows\System32\drivers\etc\hosts` :

```
127.0.0.1   jurika.local app.jurika.local api.jurika.local
```

Puis :

```powershell
docker compose -f infrastructure/docker-compose.yml --profile webserver up -d nginx
```

Vérifier : `https://jurika.local/` doit s'ouvrir sans warning navigateur.

## Production (Let's Encrypt)

Voir [`infrastructure/production/letsencrypt.md`](../../production/letsencrypt.md) — procédure complète (certbot, renouvellement systemd timer, nginx reload hook).
