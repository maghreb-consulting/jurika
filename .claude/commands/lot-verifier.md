---
description: Vérifie un lot — mvn -o clean verify -Pit, git status, fichiers à relire ; rapport dans ~/docs/rapports
argument-hint: [lot]  (déduit de la branche si absent)
---

Vérifie le lot en cours. Tu rapportes des faits ; tu ne corriges rien dans cette commande.

## 0. Identifier le lot
- Si `$ARGUMENTS` est fourni, c'est l'identifiant du lot.
- Sinon, cherche dans `~/docs/rapports/*_perimetre.md` celui dont la ligne `Branche :` correspond à la branche courante (`git branch --show-current`). Aucun ou plusieurs résultats : demande l'identifiant et arrête-toi.

Note dans le rapport : branche, `git log --oneline main..HEAD`, `git status --short` de départ.

## 1. État de la pile avant
Note `docker compose -p jurika-local ps -a` et `docker ps -a --format '{{.Names}}'`. Les tests d'intégration lancent leurs propres conteneurs et ne doivent toucher ni la pile `jurika-local` ni ses volumes.

## 2. Build
Depuis `backend-java/` : `mvn -B -o clean verify -Pit`, sortie complète dans un fichier journal du scratchpad (le build dure plusieurs minutes : lance-le en arrière-plan).
- N'ajoute aucune variable d'environnement ni réglage (DOCKER_HOST, TESTCONTAINERS_*, -DskipTests…).
- Si un artefact manque hors ligne, rapporte-le ; ne relance pas en ligne sans le proposer.

## 3. Résultats
- Reactor Summary **brut**, copié tel quel.
- Par module : tests exécutés / échecs / erreurs / ignorés, séparément pour surefire et failsafe (« aucun test » quand c'est le cas).
- Pour chaque échec : classe, méthode, message exact et cause probable. Problème d'accès Docker (socket, permissions, Ryuk) : erreur exacte plus `docker info` et `id` comme contexte.
- Pour chaque test ignoré : la raison (`@Disabled`, `@EnabledIf`, assumption).

## 4. État de la pile après
Compare avec l'étape 1 : conteneurs, statuts, volumes, conteneurs résiduels.

## 5. git status et fichiers produits
- `git status --short` : sépare les fichiers modifiés volontairement par le lot de tout fichier non suivi apparu pendant le build. Un fichier non suivi apparu pendant le build est un défaut : identifie le test qui l'écrit et pourquoi.
- Vérifie qu'aucun nouveau fichier ignoré n'est apparu hors des `target/`.
- **Fichiers à relire** : liste les sorties destinées à une relecture humaine, écrites pendant ce build (`*/target/output/**`, `*/target/samples/**`, `*/target/sample_outputs/**`, `*/target/*-report.json`, et tout fichier que le périmètre désigne comme livrable), avec leur nombre et leur chemin.

## 6. Rapport
Écris `~/docs/rapports/<lot>_verification.md` (crée le dossier s'il n'existe pas) avec les sections 0 à 5, la date, et une conclusion d'une ligne : VERT, ou ROUGE avec la liste des échecs. Aucun chiffre arrondi, aucun résumé à la place du brut.

Affiche ensuite le chemin du rapport et la conclusion. Ne commite rien.
