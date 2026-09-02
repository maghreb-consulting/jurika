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

Architecture microservices — **15 conteneurs** orchestrés par Docker Compose.

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

```bash
# 1. Configuration
cp .env.local.server.example .env.local
#    renseigner JURIKA_LAN_HOST (IP du poste), les mots de passe et AES_SECRET_KEY (32 caractères)

# 2. Démarrage de la pile complète
./scripts/start-local.sh          # Linux / macOS
.\scripts\start-local.ps1         # Windows

# 3. Jeu de données de démonstration
node scripts/seed-demo.mjs

# 4. Vérification
./scripts/smoke-test.sh
```

Interface : `http://<JURIKA_LAN_HOST>/`

### Comptes de démonstration

| Rôle | Identifiant | Mot de passe |
|---|---|---|
| Superviseur | `superviseur@demo.jurika.ma` | `Demo@2026` |
| Employé | `employe1@demo.jurika.ma` | `Demo@2026` |
| Client | `client@demo.jurika.ma` | `Demo@2026` |

> Le modèle d'extraction (`models/donut-jurika-final/`, ~700 Mo) n'est pas versionné. Sans lui, le
> service `kie-service` démarre en mode dégradé et l'extraction des pièces d'identité est indisponible.

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

*Développé par Abdellah MAAGOUL — EMSI, Génie Informatique — 2025/2026.*
