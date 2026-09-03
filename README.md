# JURIKA

**Plateforme SaaS multi-tenant d'automatisation documentaire juridique** — production d'actes de
société conformes, traçables et versionnés, à partir d'une saisie unique.

Projet de fin d'études — EMSI · Réalisé au sein du cabinet **Maghreb Consulting** (Maroc).

---

## Le problème traité

La vie juridique d'une société impose des actes à la forme strictement encadrée (loi n° 5-96,
Code de commerce) : statuts, procès-verbaux d'assemblée, annonces légales, rapports. Ces documents
sont aujourd'hui produits manuellement, ce qui entraîne trois difficultés :

- **la ressaisie** des mêmes données d'un acte à l'autre, source d'incohérences ;
- **le risque juridique** des mentions conditionnelles (un transfert de siège ne se rédige pas de la
  même façon selon qu'il reste ou non dans la même préfecture) ;
- **l'absence de traçabilité** : savoir quels statuts sont réellement en vigueur après plusieurs
  modifications.

JURIKA répond à ces trois points par une génération **déterministe** : à données identiques, acte
identique — exigence juridique avant d'être technique.

---

## Fonctionnalités

**Neuf parcours documentaires** couvrant le cycle de vie complet d'une société :

| Parcours | Portée |
|---|---|
| Création | SARL et SARL à associé unique |
| Import | reprise de sociétés existantes |
| Modification statutaire | 31 types de décisions |
| Dissolution | PV, annonce légale, nomination du liquidateur |
| Liquidation | clôture, rapport, radiation |
| Succursale Maroc | société mère locale |
| Succursale étrangère | dossier dédié à la société mère |
| Fermeture de succursale | radiation au registre du commerce |
| Approbation des comptes | assemblée générale ordinaire annuelle |

**Fonctions transverses** — Data Room documentaire versionnée (une seule version en vigueur par
document, garantie par contrainte en base) · extraction automatique des pièces d'identité (CIN,
certificat négatif) · assistant juridique avec réponses sourcées · copilote de briefing quotidien.

---

## Architecture

Architecture microservices — **18 conteneurs** orchestrés par Docker Compose
(+ MailHog, optionnel, via le profil Compose `mailhog`).

```
Frontend React  →  API Gateway  →  services métier
                                    │
        auth · ticket · workflow · dataroom · ai · dashboard · billing · supervision
                                    │
     PostgreSQL (RLS) · Redis · RabbitMQ · MinIO · OCR/KIE · temps réel · Eureka
```

| Couche | Technologies |
|---|---|
| Backend | Spring Boot 3.4 · Java 21 · Maven multi-modules |
| Frontend | React 19 · TypeScript · Vite · Tailwind |
| Données | PostgreSQL 16 (JSONB + Row Level Security) · Flyway |
| Documents | Apache POI (moteur de gabarits maison) · LibreOffice (PDF) |
| Vision | Donut (extraction structurée) · docTR (OCR) |
| Infrastructure | Docker Compose · Eureka · RabbitMQ · MinIO |

### Le moteur documentaire

Cœur technique du projet : un moteur de gabarits développé spécifiquement, qui remplit des modèles
Word validés par le cabinet **sans jamais en altérer le contenu**.

```
$DENOMINATION                          variable
▼ DÉBUT BOUCLE — ASSOCIES              répétition
    $ASSOCIE_NOM — $PARTS parts
▲ FIN BOUCLE — ASSOCIES
◇ SI : $SIEGE_MEME_PREFECTURE = « oui »   condition
◆ FIN SI
```

Plus de 30 modèles officiels intégrés, 252 variables recensées dans un dictionnaire unique, et des
contrôles automatiques garantissant qu'aucun marqueur ni champ vide ne subsiste dans le document
produit.

---

## Démarrage local

**Prérequis** — Docker Engine 24+ avec Compose v2 · Node.js 18+ · 8 Go de RAM · 12 Go de disque.

### Éléments non versionnés (à obtenir séparément)

Cinq éléments sont volontairement absents du dépôt : ils contiennent des secrets
réels ou dépassent les limites de taille de GitHub. Sans eux, la pile démarre en
apparence mais reste partiellement inopérante.

| Élément | Taille | Conséquence de son absence |
|---|---|---|
| `.env` (racine) | ~5 Ko | Mots de passe d'infrastructure vides → services incapables de s'authentifier |
| `.env.local` | ~10 Ko | La pile ne démarre pas (`start-local` s'arrête en pré-vol) |
| `models/donut-jurika-final/` | 777 Mo | Extraction d'identité inactive — `kie-service` se déclare pourtant *healthy* |
| `backend-python/kie-service/.venv` | 1 458 Mo | Extraction inactive **en mode hôte** uniquement |
| `backend-python/ocr-service/.venv` | 1 042 Mo | OCR inactif **en mode hôte** uniquement |

Les deux `.venv` ne concernent que le mode hôte (`scripts/start-all.ps1`) : en mode
conteneurisé, les dépendances Python sont installées dans les images. Les clés JWT
RS256 n'ont pas à être fournies — `start-local.ps1` les génère si elles manquent.

### Démarrage (mode conteneurisé)

```powershell
# 1. Configuration
copy .env.local.server.example .env.local
#    renseigner JURIKA_LAN_HOST (IP du poste), les mots de passe et AES_SECRET_KEY (32 caracteres)

# 2. Dependances Node de la racine (module `pg`, requis par seed-demo)
npm ci

# 3. Demarrage de la pile complete (build ~30 min la premiere fois)
.\scripts\start-local.ps1

# 4. Jeu de donnees de demonstration
node scripts/seed-demo.mjs

# 5. Verification
.\scripts\smoke-test.ps1
```

Interface : `http://<JURIKA_LAN_HOST>/` — par défaut `http://localhost/`

> **Vérifié le 2026-09-03 sur Windows 11 / Docker Desktop** : `start-local.ps1`
> retourne le code 0 avec 17/17 services *healthy* (18 conteneurs au total),
> `seed-demo.mjs` peuple le workspace `JUR-DEMO2`, et `smoke-test.ps1` renvoie
> 8 PASS / 0 FAIL.
>
> Les équivalents POSIX (`start-local.sh`, `smoke-test.sh`) existent dans le dépôt
> mais **n'ont pas été exécutés** lors de cette vérification.

### Contrôler que l'extraction fonctionne réellement

`kie-service` répond *healthy* même sans les poids du modèle : un `docker ps`
entièrement vert ne prouve donc rien sur l'extraction. Le seul contrôle qui
distingue une pile complète d'une pile silencieusement dégradée :

```powershell
curl http://localhost:8088/health
#    attendu : {"status":"UP","model_loaded":true}

curl -X POST http://localhost:8088/api/v1/kie/extract `
  -F "file=@scripts/demo-video/assets/CIN_specimen_recto.png" `
  -F "doc_type=cin_nouv_recto"
#    attendu : des champs renseignes + "source":"kie"
```

Une réponse 200 avec `model_loaded: false` ou des champs vides signale que
`models/donut-jurika-final/` est absent ou mal monté.

### Comptes de démonstration

| Rôle | Identifiant | Mot de passe |
|---|---|---|
| Superviseur | `superviseur@demo.jurika.ma` | `Demo@2026` |
| Employé | `employe1@demo.jurika.ma` | `Demo@2026` |
| Client | `client@demo.jurika.ma` | `Demo@2026` |

Code workspace : `JUR-DEMO2`.

> Le compte `employe1@demo.jurika.ma` a la double authentification **TOTP** active :
> le jeton renvoyé par `/auth/login` est refusé (403) jusqu'à l'appel de
> `/auth/verify-2fa`. Prévoir l'application d'authentification associée.

---

## Tests

```bash
mvn -f backend-java/pom.xml test          # tests unitaires (backend)
mvn -f backend-java/pom.xml verify -Pit   # + tests d'intégration (TestContainers, Docker requis)
cd frontend-react && npm run test:coverage && npm run build
```

**Environ 1 400 tests** répartis entre services et interface, rejoués à chaque intégration.

Au-delà des tests automatisés, une **simulation en conditions réelles** a été menée : les neuf
parcours, pour les deux formes juridiques, exécutés dans l'interface comme le ferait un utilisateur,
avec relecture de chaque document produit. Elle a mis en évidence 43 défauts qu'aucun test ne
détectait — notamment des documents générés avec des champs vides, que les contrôles validaient
puisqu'ils ne vérifiaient que l'absence de marqueurs, jamais la présence de valeurs.

---

## Sécurité

| Mécanisme | Mise en œuvre |
|---|---|
| Authentification | JWT signés RS256 (asymétrique) |
| Double authentification | TOTP (RFC 6238) |
| Mots de passe | BCrypt, coût 12 |
| Autorisation | RBAC — 4 rôles, contrôlé côté serveur |
| Cloisonnement multi-tenant | Row Level Security PostgreSQL **et** filtrage applicatif |
| Secrets | hors dépôt, scan gitleaks en intégration continue |

---

## Structure du dépôt

```
backend-java/        10 services Spring Boot + bibliothèque commune
backend-python/      kie-service (Donut) · ocr-service (docTR)
backend-node/        service temps réel (WebSocket)
frontend-react/      interface React + TypeScript
infrastructure/      composition Docker, secrets, déploiement
scripts/             démarrage, jeu de données, tests de fumée, outillage
models/              modèle d'extraction (non versionné)
docs/                documentation technique et fonctionnelle
```

---

## État d'avancement

Les neuf parcours sont livrés et validés en conditions réelles. Le moteur documentaire couvre
l'ensemble des modèles fournis par le cabinet.

**Perspectives** — extension aux sociétés anonymes et SAS · signature électronique · télétransmission
au greffe · fédération d'identité (OIDC).

---

*Développé par Oussama BENATIK — EMSI, Génie Informatique — 2025/2026.*
