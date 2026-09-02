# JURIKA — Guide de test simplifie

**Objectif** : tester ce qui est developpe en Phase 1 (Auth) et Phase 2 (Tickets + Workflows) en 10 minutes, sans melange.

---

## Avant tout : une stack PROPRE

1. **Ferme tous les terminaux** qui tournent (mvn, npm, etc.)
2. Dans PowerShell, depuis la racine du projet :

```powershell
.\scripts\reset-db.ps1     # tape OUI pour wipe la DB
.\scripts\start-all.ps1    # demarre tout dans l'ordre
```

3. **Attends 60 secondes** que tous les services montent. Verifie sur `http://localhost:8761` que tu vois 5 services UP dans Eureka (auth, gateway, ticket, workflow, ai).

---

## Test API (10 etapes en chaine)

**Pre-requis** : installer l'extension **REST Client** dans VS Code (`humao.rest-client`).

1. Ouvre `scripts/smoke-tests.http` dans VS Code
2. Clique "Send Request" au-dessus du **###** de l'etape 0.1 (health check Eureka)
3. Si vert (200 OK) → passe a 0.2, puis 0.3
4. Continue etape par etape, dans l'ordre

A chaque etape :
- Tu vois la requete envoyee + la reponse a droite
- Tu compares avec le `### ATTENDU :` au-dessus
- Si KO → stop, copie-moi la reponse + le log du service concerne

---

## Verification DB en parallele

Dans DBeaver, ouvre `scripts/verify-db.sql` et execute les blocs **au fur et a mesure** :

- Apres etape 1 → bloc 1 (verifie les migrations + seeds)
- Apres etape 2 (register) → bloc 2 (workspace + user crees)
- Apres etape 3 (login) → bloc 3 (refresh_token + audit log)
- Apres etape 5 (ticket) → bloc 4
- Apres etape 7 (workflow) → bloc 5
- Apres etape 9 (debours) → bloc 6
- Apres etape 10 (annulation) → bloc 7

---

## En cas d'erreur

### 403 Forbidden
- Cause probable : rate limiter gateway (fix applique, redemarre le gateway)
- Verifie tu envoies aucun `Authorization` header sur les endpoints publics

### 401 Unauthorized
- JWT_SECRET mismatch entre auth-service et le service qui valide
- Tous doivent lire le meme `JWT_SECRET` depuis `.env`

### 404 Not Found
- Le service n'est pas demarre (verifier Eureka)
- L'URL est mauvaise

### 500 Internal Server Error
- Va voir le terminal du service concerne, copie la stacktrace

### Le service ne demarre pas
- "jurika-common non trouve" → `mvn -pl jurika-common install -DskipTests` puis relance
- "Port already in use" → `.\scripts\stop-all.ps1` puis `.\scripts\start-all.ps1`
- "Flyway error" → `.\scripts\reset-db.ps1` (wipe DB)

---

## Si TOUT passe (les 10 etapes vertes)

Phase 1 + Phase 2 sont validees. On peut attaquer Phase 3 (Modification, Dissolution, Liquidation).

## Si quelque chose casse

Dis-moi **exactement** :
- Quelle etape (numero)
- Code HTTP recu
- Body de la reponse
- Les logs du service concerne (auth/gateway/ticket/workflow)

Je corrige cible et on continue.
