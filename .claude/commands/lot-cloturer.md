---
description: Clôture un lot — prépare les commits, montre les diffs, attend le feu vert, pousse, donne le lien de PR (ne fusionne jamais)
argument-hint: [lot]
---

Clôture le lot en cours. Cette commande **ne fusionne jamais** : pas de `git merge` vers `main`, pas de `gh pr merge`, pas de push sur `main`.

## 1. Préconditions
- Branche courante : une branche de lot, jamais `main`. Sinon arrête-toi.
- Le rapport `~/docs/rapports/<lot>_verification.md` existe, est VERT et date d'après la dernière modification du code. Sinon dis-le et propose de lancer `/lot-verifier` d'abord.
- `git status --short` : aucun fichier non suivi inattendu. S'il y en a, liste-les et arrête-toi.

## 2. Préparer les commits
- Propose un découpage en commits cohérents (un sujet par commit), au format du dépôt : `type(portée): résumé` en français (`build:`, `fix(ticket):`, `test:`, `docs(claude):`…). Le corps dit pourquoi et ce qui a été vérifié.
- Chaque message se termine par la ligne d'attribution `Co-Authored-By` en vigueur dans la session.
- Utilise l'identité git configurée dans le dépôt, sans la modifier.
- Indexe fichier par fichier, ou hunk par hunk quand un fichier relève de deux commits (`git apply --cached` sur un patch extrait : `git add -p` est interactif). Jamais `git add -A` ni `git add .`.

## 3. Montrer et attendre
Pour chaque commit prévu : message complet, liste des fichiers, diff complet.

**Arrête-toi ici.** Ne commite et ne pousse qu'après un feu vert explicite dans un message ultérieur de l'utilisateur. Une demande de modification relance l'étape 2.

## 4. Après le feu vert
- Crée les commits tels que validés, puis `git log --oneline main..HEAD` et `git status`.
- `git push -u origin <branche>`.
- Donne le lien d'ouverture de la PR, tel qu'affiché par le push (`https://github.com/<org>/<dépôt>/pull/new/<branche>`).
- Ne fusionne pas, ne supprime pas la branche.
