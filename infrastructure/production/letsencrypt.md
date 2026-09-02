# Production — Certificats Let's Encrypt

> Procédure de génération et renouvellement automatique pour `app.jurika.ma`, `api.jurika.ma`.

## Pré-requis serveur

- Ubuntu 22.04+ ou Debian 12
- Ports 80 et 443 ouverts publiquement
- DNS `A` records configurés pour `app.jurika.ma` et `api.jurika.ma` pointant sur l'IP du serveur
- Nginx déjà installé

## 1. Installation certbot

```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
```

## 2. Première émission

Méthode `webroot` (sans interrompre nginx) :

```bash
sudo mkdir -p /var/www/letsencrypt
sudo certbot certonly \
    --webroot -w /var/www/letsencrypt \
    --email security@jurika.ma \
    --agree-tos --no-eff-email \
    -d app.jurika.ma -d api.jurika.ma
```

Les certificats émis se trouvent dans :
- `/etc/letsencrypt/live/app.jurika.ma/fullchain.pem`
- `/etc/letsencrypt/live/app.jurika.ma/privkey.pem`

## 3. Mise à jour nginx

Dans `infrastructure/nginx/default.conf` (ou montage Docker équivalent) :

```nginx
ssl_certificate     /etc/letsencrypt/live/app.jurika.ma/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/app.jurika.ma/privkey.pem;
```

Puis :

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 4. Renouvellement automatique

certbot installe un systemd timer (`certbot.timer`) qui tente le renouvellement deux fois par jour. Pour vérifier :

```bash
sudo systemctl list-timers | grep certbot
sudo certbot renew --dry-run
```

Hook post-renouvellement (recharger nginx) :

```bash
sudo mkdir -p /etc/letsencrypt/renewal-hooks/deploy
sudo tee /etc/letsencrypt/renewal-hooks/deploy/nginx-reload.sh <<'EOF'
#!/bin/sh
nginx -s reload
EOF
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/nginx-reload.sh
```

## 5. Validation post-déploiement

```bash
curl -I https://app.jurika.ma                # 200 + headers HSTS/CSP
curl -I http://app.jurika.ma                 # 301 -> https://
sslyze --regular app.jurika.ma               # TLS 1.2/1.3 seulement, pas de RC4
```

Grade attendu sur https://www.ssllabs.com/ssltest/ : **A+**.

## 6. Rate limit Let's Encrypt

- 5 émissions / semaine / nom de domaine (production).
- 50 émissions / semaine / second-level domain.
- Préférer l'endpoint staging `--server https://acme-staging-v02.api.letsencrypt.org/directory` pour les tests.

## 7. Rotation manuelle (urgence)

```bash
sudo certbot renew --force-renewal -d app.jurika.ma -d api.jurika.ma
sudo systemctl reload nginx
```

## 8. Révocation

```bash
sudo certbot revoke --cert-path /etc/letsencrypt/live/app.jurika.ma/cert.pem
```
