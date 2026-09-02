# DEPLOY_LOCAL — Démo JURIKA sur serveur local accessible LAN

> **Sprint Beta (pricing-deploy) — TASK 8.**
> Cible : déployer JURIKA en démo directeur sur un poste/serveur Windows ou Linux
> accessible par les autres machines du même réseau local (LAN).

## TL;DR (10 minutes si tout est prêt)

```bash
# 1. Copie la config serveur, édite l'IP LAN et les secrets
cp .env.local.server.example .env.local
$EDITOR .env.local

# 2. Crée les produits Stripe (TEST mode) et patche .env.local automatiquement
node scripts/stripe-setup-products.mjs --write-env

# 3. Démarre la stack (build + up + wait 10/10 healthy + URLs)
./scripts/start-local.sh           # Linux/macOS
# OU
.\scripts\start-local.ps1          # Windows

# 4. Seed les données démo (workspace JUR-DEMO2 + 4 users + 10 dossiers)
node scripts/seed-demo.mjs

# 5. Smoke test : 10/10 services, signup, plan limit, Stripe, email
./scripts/smoke-test.sh            # ou .ps1 sous Windows
```

URL démo : `http://<IP-LAN-DU-SERVEUR>/` (ex `http://192.168.1.42/`).

---

## 1. Pré-requis

| Composant | Version min. | Vérif |
|-----------|--------------|-------|
| Docker Engine + Compose v2 | 24.0 / v2.20 | `docker compose version` |
| Node.js | 18 LTS | `node --version` |
| OpenSSL | 3.0 (Windows : `winget install OpenSSL.OpenSSL`) | `openssl version` |
| RAM dispo | 8 Go | — |
| Disque dispo | 12 Go | (images Docker + volumes) |
| Modèle Donut KIE local | `projet/models/donut-jurika-final/` (~700 Mo, config.json + model.safetensors + tokenizer*) | copier sur le serveur avant le `start-local`, sinon `kie-service` reste unhealthy |
| Ports libres sur le serveur | 80, 1025, 3000, 5432, 6379, 5672, 8025, 8080-8090, 8761, 9000-9001, 15672 | `netstat -tlnp` (Linux) ou `Get-NetTCPConnection` (Win) |

> Détail des ports exposés en LAN : 80 (frontend), 3000 (realtime-service WebSocket),
> 8080 (gateway), 8081-8087 (services Java auth/ticket/workflow/dataroom/ai/supervision/dashboard),
> 8088 (kie-service Donut), 8089 (ocr-service docTR), 8090 (billing-service),
> 8761 (Eureka), 8025/15672/9001 (UIs MailHog/RabbitMQ/MinIO), 5432/6379/5672/9000 (infra).

> ⚠️ Sur Windows, le contrôle de dossiers Windows Defender peut bloquer
> AF_UNIX dans `%TEMP%` (cf. `[[never-stop-winnat]]`). Les scripts utilisent
> `.tmp/` projet à la place — ne pas modifier.

## 2. Réseau LAN

1. Note l'IP du serveur (Windows : `ipconfig` ; Linux : `ip a`).
2. Renseigne `JURIKA_LAN_HOST=192.168.x.y` dans `.env.local`.
3. Ouvre le pare-feu pour les ports exposés au LAN :
   - **Windows** : `New-NetFirewallRule -DisplayName "JURIKA LAN" -Direction Inbound -Protocol TCP -LocalPort 80,3000,8080 -Action Allow`
   - **Linux ufw** : `sudo ufw allow 80/tcp && sudo ufw allow 3000/tcp && sudo ufw allow 8080/tcp`
   - Le port 3000 (realtime-service / Socket.io) est requis : le navigateur des
     postes clients ouvre une connexion WebSocket directe vers le serveur sur
     ce port. Sans la règle, les notifications et le chat resteront muets.
4. Optionnel — entrée DNS interne (`demo.jurika.local`) pour éviter
   d'écrire l'IP.

## 3. Configuration `.env.local`

À partir de `.env.local.server.example`, **OBLIGATOIRE** de personnaliser :

- `JURIKA_LAN_HOST` — IP/hostname du serveur tel que vu depuis les postes clients
- `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`, `MINIO_ROOT_PASSWORD` — mots de passe forts
- `AES_SECRET_KEY` — exactement 32 caractères
- `STRIPE_*` — créer un compte Stripe TEST puis lancer `stripe-setup-products.mjs`
- `SMTP_*` — choisir Brevo (recommandé) ou MailHog (par défaut, dev)

## 4. Génération des clés JWT RS256

Le script `start-local.{sh,ps1}` génère automatiquement la paire RSA 2048
dans `infrastructure/secrets/jwt/` au premier lancement.

Manuel (si nécessaire) :
```bash
mkdir -p infrastructure/secrets/jwt
openssl genrsa -out infrastructure/secrets/jwt/jwt-private.pem 2048
openssl pkcs8 -topk8 -inform PEM -in infrastructure/secrets/jwt/jwt-private.pem \
  -out infrastructure/secrets/jwt/jwt-private-pkcs8.pem -nocrypt
openssl rsa -in infrastructure/secrets/jwt/jwt-private.pem -pubout \
  -out infrastructure/secrets/jwt/jwt-public.pem
```

Les `.pem` sont **gitignorés**. Ne jamais commit. Si on regénère, tous les
JWT existants seront invalidés (les users devront se relogger).

## 5. Stripe (TEST mode pour la démo)

1. Crée un compte sur [https://dashboard.stripe.com](https://dashboard.stripe.com)
2. Bascule en **Test mode** (toggle haut-droit).
3. Récupère les clés : *Developers > API keys* → `pk_test_…` + `sk_test_…`.
   Renseigne-les dans `.env.local`.
4. Crée les 4 prix idempotamment :
   ```bash
   node scripts/stripe-setup-products.mjs --write-env
   ```
   Le script vérifie l'existence par `lookup_key` et écrit les `price_*` dans
   `.env.local`.
5. Lance le forwarder webhook (terminal dédié, à laisser ouvert pendant la
   démo) :
   ```bash
   stripe listen --forward-to http://localhost:8090/api/v1/billing/webhook/stripe
   ```
   Le `whsec_…` affiché va dans `STRIPE_WEBHOOK_SECRET`. **À refaire après
   chaque reboot** (session-bound).

## 6. Email — Brevo vs MailHog

### Option A — Brevo (recommandé)

1. Compte sur [https://brevo.com](https://brevo.com) (offre gratuite : 300 emails/jour).
2. *Settings > SMTP & API > SMTP* → génère les credentials SMTP.
3. *Senders & IP > Senders* → valide `noreply@jurika.ma` (DNS SPF + DKIM
   recommandés pour éviter le spam-folder).
4. Dans `.env.local` :
   ```env
   SMTP_HOST=smtp-relay.brevo.com
   SMTP_PORT=587
   SMTP_AUTH=true
   SMTP_STARTTLS=true
   SMTP_USER=<adresse-brevo>
   SMTP_PASSWORD=<api-key-brevo>
   SMTP_FROM=noreply@jurika.ma
   ```

### Option B — MailHog (fallback)

Aucune config requise — laisse les valeurs `mailhog` / `1025` / `auth=false`
du template. Les emails sont capturés et visibles dans la web UI :
`http://<LAN-HOST>:8025`. Utile pour vérifier le signup → email de
bienvenue → onboarding sans risque d'envoi externe.

## 7. Démarrage

### Linux/macOS

```bash
chmod +x scripts/*.sh
./scripts/start-local.sh
```

### Windows (PowerShell)

```powershell
.\scripts\start-local.ps1
```

Options :

- `--no-build` : skippe le `docker compose build` (utile si rien n'a changé)
- `--reset` : `down -v` (efface les volumes / données — repart vierge)
- `--logs` : tail des logs en sortie après le up

À la fin, le script vérifie les healthchecks de la stack complète
(4 infra + 9 services Java + realtime-service + ocr/kie + frontend)
et affiche les URLs LAN.

### Vérifier 10/10 healthy

```bash
docker compose \
  -f infrastructure/docker-compose.yml \
  -f infrastructure/docker-compose.services.yml \
  -f infrastructure/docker-compose.local.yml \
  -p jurika-local ps
```

Tous les containers doivent être en `healthy`. Si un `unhealthy` : voir
les logs (`docker logs jurika-<service>`) et le DEPANNAGE plus bas.

## 8. Seed démo

```bash
node scripts/seed-demo.mjs
```

Crée idempotemment un workspace `JUR-DEMO2` (plan **Business** actif), 4 users
(Superviseur, 2 Employés, 1 Client — mot de passe `Demo@2026`), 10 dossiers
entreprises variés (8 villes du Maroc, 3 formes juridiques) et 10 tickets
couvrant les 9 types métier et 4 statuts (NOUVEAU/EN_COURS/CLÔTURE/ANNULÉ).

Pour repartir vierge : `node scripts/seed-demo.mjs --reset`.

## 9. Pointer le frontend vers le serveur LAN

Le frontend SPA est servi par le service `jurika-frontend` (nginx) **sur le
serveur lui-même**, et il appelle le backend via `VITE_API_URL` injecté à
build. C'est déjà aligné sur `JURIKA_LAN_HOST` dans `.env.local`.

Les autres postes du LAN n'ont **rien à configurer** : ils ouvrent simplement
`http://<IP-LAN-DU-SERVEUR>/` dans Chrome.

⚠️ Si le frontend appelle `localhost:8080` au lieu de `<IP-LAN>:8080`,
le poste client aura une 404. Vérifier `VITE_API_URL` côté build avec :

```bash
docker exec jurika-frontend grep -r "api/v1" /usr/share/nginx/html/assets/ | head
```

## 10. Smoke test

```bash
./scripts/smoke-test.sh            # Linux/macOS
.\scripts\smoke-test.ps1           # Windows
```

Sortie attendue :

```
[PASS] 10/10 services healthy
[PASS] GET /api/v1/public/pricing (3 tiers, Starter/Business/Entreprise)
[PASS] Signup → trial 14j
[PASS] 3e user Starter → 402 PLAN_LIMIT_USERS
[PASS] POST /billing/checkout-session → Stripe URL
[PASS] Email de bienvenue capté (MailHog/Brevo)
```

## 11. Dépannage

| Symptôme | Cause probable | Fix |
|----------|----------------|-----|
| `jurika-billing unhealthy` 90s+ | `STRIPE_SECRET_KEY` invalide ou placeholder | Vérifier .env.local + rerun `stripe-setup-products.mjs` |
| `jurika-auth unhealthy` | JWT pem absent ou mauvais path | Vérifier `infrastructure/secrets/jwt/` (auto-régénéré par start-local) |
| `jurika-postgres` ne démarre pas | port 5432 déjà pris | `sudo systemctl stop postgresql` ou changer `POSTGRES_PORT` |
| Frontend 502 | gateway-service KO | `docker logs jurika-gateway` |
| Stripe Checkout retour 404 | `BILLING_CHECKOUT_SUCCESS_URL` pointe `localhost` au lieu LAN_HOST | Corriger .env.local |
| Pas d'email | SMTP_HOST=mailhog mais on attendait Brevo | Renseigner Brevo creds + rebuild |
| Port 8761 occupé (Hyper-V) | Voir `[[never-stop-winnat]]` | Réserver le port via Task Scheduler boot, NE PAS faire `net stop winnat` |

## 12. Ce qui reste manuel

Pour le **user (Oussama)** :
- Compte Stripe + clés `pk_test_*`/`sk_test_*` + lancer `stripe-setup-products.mjs`
- Compte Brevo + sender vérifié (ou laisser MailHog)
- Renseigner `.env.local` (IP LAN, mots de passe, etc.)
- Ouvrir le firewall du serveur sur les ports 80, 3000 et 8080
- Copier le dossier `projet/models/donut-jurika-final/` sur le serveur au
  même chemin relatif (sinon surcharger via `DONUT_MODEL_DIR_HOST=/abs/path`
  dans `.env.local`) — sans les poids, `kie-service` ne démarre pas et
  l'extraction CIN/CN reste en fallback vision.
- `stripe listen` dans un terminal dédié pendant la démo

Pour **Cowork** :
- Push de la branche `feat/sprint-beta-pricing-deploy` sur l'origin
- Merge + tag (la consigne ARRÊT NET interdit de le faire en local)

## 13. Tâches post-démo

Une fois la démo validée par le directeur :
- Migration V18 pour renommer `selected_plan` en `starter`/`business` (cf. décision archi T1)
- Stripe LIVE mode + DNS sender Brevo SPF/DKIM
- Move enforcement P0 storage (T3 P1) → bloquant upload
- Migration WorkspaceEntity.preferredTheme nullable=false pour cohérence avec V22
