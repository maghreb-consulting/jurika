# JURIKA Mobile

Application mobile de la plateforme **JURIKA** (Expo SDK 52 + Expo Router + TypeScript).

## Stack

- Expo SDK 52 / React Native 0.76 / React 18.3
- Expo Router 4 (navigation file-based)
- TypeScript strict
- Zustand (state management)
- Axios (HTTP) avec interceptors auto-refresh
- expo-secure-store (stockage chiffre des tokens)
- `@expo/vector-icons` (Ionicons)
- StyleSheet natif (pas de Tailwind / NativeWind sur mobile)

## Pre-requis

- Node.js >= 18
- npm ou yarn
- Expo Go (sur smartphone) ou un simulateur iOS / emulateur Android
- **Backend JURIKA en marche** (cf section dependances)

## Setup

```bash
cd frontend-mobile
npm install
```

Copier la config d'environnement :

```bash
cp .env.example .env
```

Editer `.env` selon votre environnement :

```env
# Pour un simulateur (meme machine que le backend)
EXPO_PUBLIC_API_URL=http://localhost:8080/api/v1

# Pour un telephone physique (remplacer par votre IP LAN)
# EXPO_PUBLIC_API_URL=http://192.168.1.42:8080/api/v1
```

> **Important** : un appareil physique ne peut pas resoudre `localhost`. Trouver votre IP LAN avec `ipconfig` (Windows) ou `ifconfig` (macOS/Linux). Le gateway doit ecouter sur `0.0.0.0:8080`.

## Lancement

```bash
npx expo start
```

- Scanner le QR code avec **Expo Go** (Android) ou la camera (iOS)
- Appuyer sur `i` pour iOS, `a` pour Android, `w` pour web

## Comptes demo

| Workspace | Email             | Password    | Role    |
|-----------|-------------------|-------------|---------|
| JUR-DEMO1 | admin@jurika.ma   | Admin@2026  | SUPER_ADMIN |
| JUR-DEMO1 | karim@jurika.ma   | Admin@2026  | EMPLOYE     |

## Dependances backend

L'application consomme le gateway Java a `http://localhost:8080/api/v1`. Les services suivants doivent etre demarres :

- `discovery-service` (Eureka, port 8761)
- `gateway-service` (port 8080)
- `auth-service` (port 8081)
- `ticket-service` (port 8082)
- `dataroom-service` (port 8084)
- `ai-service` (port 8085) — pour le chatbot
- `supervision-service` (port 8086) — pour les KPIs du dashboard

Egalement requis : PostgreSQL 16, Redis 7.4, RabbitMQ 4.0, MinIO (via `docker-compose up`).

Au moins, depuis `Plateforme SaaS GJE Multi-Workspace/` :

```bash
# Infrastructure
docker compose up -d
# Services Java (ou utiliser scripts/start-all.ps1 a la racine du repo)
```

## Architecture des dossiers

```
frontend-mobile/
├── app/                          # Routes Expo Router (file-based)
│   ├── _layout.tsx               # Auth gate + navigation root
│   ├── index.tsx                 # Redirect entry
│   ├── (auth)/                   # Stack non authentifie
│   │   ├── _layout.tsx
│   │   ├── workspace.tsx         # Etape 1 : code workspace
│   │   ├── login.tsx             # Etape 2 : email + password
│   │   └── twofa.tsx             # Etape 3 : 2FA TOTP
│   └── (tabs)/                   # Stack authentifie (5 onglets)
│       ├── _layout.tsx
│       ├── index.tsx             # Dashboard
│       ├── tickets/              # Sub-stack
│       │   ├── _layout.tsx
│       │   ├── index.tsx         # Liste + filtres
│       │   └── [id].tsx          # Detail + transitions
│       ├── dataroom/             # Sub-stack
│       │   ├── _layout.tsx
│       │   ├── index.tsx         # Liste dossiers
│       │   └── [id].tsx          # Detail dossier (2 onglets)
│       ├── chatbot.tsx           # Assistant IA RAG
│       └── profile.tsx           # Profil + Logout
├── components/                   # UI primitives (Button, TextField, Card, Badge, Header, EmptyState)
├── lib/
│   ├── api.ts                    # axios instance + helpers
│   └── secureStorage.ts          # wrapper expo-secure-store (+ fallback web)
├── store/
│   └── authStore.ts              # zustand
├── theme/
│   └── colors.ts                 # couleurs, spacing, radius
├── types/
│   └── index.ts                  # TS types alignes sur backend
├── assets/                       # icons, splash (a fournir)
├── app.json
├── babel.config.js
├── metro.config.js
├── tsconfig.json
├── package.json
└── .env.example
```

## Flux d'authentification

1. `app/_layout.tsx` hydrate le token depuis `expo-secure-store`
2. Si pas de token -> redirige vers `(auth)/workspace`
3. Workspace check -> login -> 2FA (si requis) -> stockage token + navigation `(tabs)`
4. `axios.interceptors` injecte automatiquement `Authorization: Bearer <token>` sur chaque requete
5. Sur 401, le client tente `/auth/refresh` une fois ; en cas d'echec, purge le storage et le user est redirige

## TODO / Compromis vs web

- Pas de NativeWind : styles via `StyleSheet`
- Pas de telechargement de PDF : le bouton est present mais `expo-file-system` + `expo-sharing` ne sont pas encore branches (TODO marque dans le code Data Room)
- Pas de chat temps reel : la couche Socket.io de `realtime-service` n'est pas branchee en mobile (TODO Phase 5)
- Pas de creation de ticket depuis le mobile : l'ecran liste est en lecture/transition uniquement
- Pas encore de notifications push (TODO `expo-notifications` + token enregistre cote backend)
- Les assets binaires (icon, splash) doivent etre fournis dans `assets/`

## Scripts

```bash
npm run start    # expo start
npm run android  # expo start --android
npm run ios      # expo start --ios
npm run web      # expo start --web
```
