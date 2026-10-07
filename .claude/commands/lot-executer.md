---
description: Exécute un lot étape par étape — journal à cases à cocher, un commit local par étape verte, points d'arrêt obligatoires (ne pousse jamais)
argument-hint: <lot>  (ex. L-2)
---

Exécute le lot **$ARGUMENTS** à partir de son périmètre validé. Cette commande **ne pousse jamais et ne fusionne jamais** : pas de `git push`, pas de `git merge`, pas de `gh pr merge`.

Si `$ARGUMENTS` est vide, demande l'identifiant du lot et arrête-toi. Dans la suite, `<lot>` vaut `$ARGUMENTS`.

## 1. Préconditions
Vérifie, dans cet ordre :
- `~/docs/rapports/<lot>_perimetre.md` existe ;
- la branche courante (`git branch --show-current`) est celle de sa ligne `Branche :`, et ce n'est jamais `main` ;
- l'arbre de travail est propre (`git status --short` vide).

Si l'une manque, arrête-toi et dis laquelle, avec la sortie exacte de la commande qui le montre. Ne crée pas la branche, ne nettoie pas l'arbre.

## 2. Pas encore de journal : découper et s'arrêter
Si `~/docs/rapports/<lot>_journal.md` n'existe pas :
- Lis le périmètre, `CLAUDE.md` (dont le backlog / dette connue) et, si le lot le demande, `~/docs/ANALYSE_ECART.md`. Le cahier des charges `~/docs/CAHIER_DES_CHARGES_JURIKA.md` fait foi.
- Découpe le périmètre en étapes courtes : **une étape = un changement testable**.
- Écris le journal :

```markdown
# Lot $ARGUMENTS — journal
Branche : <branche>
Périmètre : ~/docs/rapports/<lot>_perimetre.md

## Étapes
- [ ] E1 — <intitulé>
  - Objectif : <ce que l'étape change, avec sa source (périmètre §…, RG-…)>
  - Fichiers visés : <chemins>
  - Test qui la valide : <classe#méthode, existant ou à écrire ; module>
- [ ] E2 — …

## ⏸ En attente
(vide)

## Déroulement
```

- Signale dans le journal toute étape qui touchera un point d'arrêt (§4) : elle s'arrêtera au moment venu.

**Arrête-toi ici**, sans écrire de code : affiche le chemin du journal et la liste des étapes, et attends la validation.

## 3. Journal existant : exécuter
Si la section « ⏸ En attente » contient une question sans réponse de l'utilisateur, rappelle-la et arrête-toi.

Sinon, reprends à la **première étape non cochée**. Pour chaque étape :
1. Code le changement, strictement dans les fichiers et l'objectif de l'étape.
2. Lance, depuis `backend-java/`, `mvn -B -o verify -pl <modules touchés> -am`, sortie complète dans un fichier du scratchpad ; lis le Reactor Summary, jamais le seul code de retour d'un pipeline. Si l'étape touche le frontend, lance aussi sa suite de tests.
3. Tests rouges : corrige et relance. Après **2 tentatives** encore rouges, c'est un point d'arrêt (§4).
4. Tests verts : coche la case de l'étape.
5. Ajoute au journal, sous « Déroulement » :

```markdown
### E<n> — <intitulé> — <AAAA-MM-JJ HH:MM>
- Fait : <ce qui a été fait>
- Fichiers modifiés : <chemins>
- Tests : <commande> — <Reactor Summary : modules SUCCESS/FAILURE> ; <exécutés / échecs / erreurs / ignorés>
- Décisions : <choix faits et pourquoi ; « aucune » sinon>
- Commit : <hash court> <message>
```

6. Commit local au format du dépôt : `type(portée): résumé` en français, corps qui dit pourquoi et ce qui a été vérifié, ligne d'attribution `Co-Authored-By` en vigueur dans la session, identité git du dépôt sans la modifier. Indexe **fichier par fichier** (`git add <chemin>`), jamais `git add -A` ni `git add .`. Vérifie avec `git status --short` qu'il ne reste rien d'inattendu, puis reporte le hash dans le journal.

Puis passe à l'étape suivante.

## 4. Points d'arrêt obligatoires
Dans chacun de ces cas, **avant d'agir**, écris la question dans le journal sous « ⏸ En attente » (étape concernée, contexte, options, ce que tu recommandes), affiche-la et arrête-toi :
- nouvelle migration Flyway ;
- suppression de fichier ou de données ;
- règle métier absente du cahier des charges ;
- tests encore rouges après 2 tentatives (joins l'erreur exacte) ;
- toute commande Docker ;
- toute modification hors du périmètre.

L'étape en cours reste non cochée et sans commit. Laisse l'arbre de travail propre : si un changement partiel ne peut pas rester, annule-le (`git restore` sur les seuls fichiers de l'étape) et note-le au journal.

## 5. Arrêt demandé
Si l'utilisateur demande d'arrêter : termine l'étape en cours si ses tests passent (case cochée, journal, commit), sinon annule-la proprement (`git restore` sur ses fichiers, case non cochée). Mets le journal à jour avec l'état exact, puis arrête-toi.

## 6. Fin du lot
Quand toutes les cases sont cochées, applique les étapes de `/lot-verifier` pour ce lot. Ses commandes Docker en lecture seule (`docker compose ps`, `docker ps`) sont les seules exceptions au point d'arrêt « commande Docker ».

Ajoute au journal la conclusion du rapport de vérification, affiche `git log --oneline main..HEAD` et arrête-toi. Ne pousse jamais, ne fusionne jamais : la suite passe par `/lot-cloturer`.
