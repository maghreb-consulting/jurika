---
description: Démarre un lot — lit le plan, crée la branche, écrit le périmètre et s'arrête pour validation
argument-hint: <lot>  (ex. L-2)
---

Démarre le lot **$ARGUMENTS**. Tu t'arrêtes à la fin pour validation : aucune modification de code dans cette commande.

Si `$ARGUMENTS` est vide, demande l'identifiant du lot et arrête-toi.

## 1. Lire les sources
Lis intégralement :
- `CLAUDE.md` (racine du dépôt), dont le backlog / dette connue ;
- `~/docs/PLAN_DEVELOPPEMENT_JURIKA.md` ;
- `~/docs/ANALYSE_ECART.md`.

Si un de ces fichiers est absent ou ne mentionne pas le lot $ARGUMENTS, dis-le tel quel et arrête-toi : ne reconstitue pas le périmètre de mémoire. Le cahier des charges `~/docs/CAHIER_DES_CHARGES_JURIKA.md` fait foi en cas de contradiction ; `~/docs/archives/` n'est jamais une référence.

## 2. Créer la branche
- Vérifie que l'arbre de travail est propre (`git status`). S'il ne l'est pas, liste les fichiers et arrête-toi.
- Pars de `main` à jour : `git checkout main`, `git pull`.
- Crée la branche `lot/<slug>`, où `<slug>` est un nom court en kebab-case tiré de l'intitulé du lot dans le plan. Si le plan impose un nom de branche, utilise celui-là.

## 3. Écrire le périmètre
Crée `~/docs/rapports/` s'il n'existe pas, puis écris `~/docs/rapports/<lot>_perimetre.md`, où `<lot>` vaut `$ARGUMENTS` :

```markdown
# Lot $ARGUMENTS — périmètre
Date : <AAAA-MM-JJ>
Branche : lot/<slug>
Sources : CLAUDE.md, PLAN_DEVELOPPEMENT_JURIKA.md (§…), ANALYSE_ECART.md (§…)

## Objectif
<une ou deux phrases, reprises du plan>

## Dans le périmètre
- <livrable / écart traité> — source : <fichier §section>

## Hors périmètre
- <ce qui est explicitement exclu ou reporté>

## Fichiers et modules probablement touchés
- <chemin> — <pourquoi>

## Critères de vérification
- `mvn -o clean verify -Pit` vert (depuis `backend-java/`)
- <critères propres au lot, tirés du plan>

## Points à trancher avant de coder
- <ambiguïtés, contradictions entre sources, entrées de backlog liées>
```

Chaque élément cite sa source. Ce qui est déduit et non écrit dans les sources est signalé comme tel.

## 4. S'arrêter
Affiche le chemin du fichier, la branche créée et la section « Points à trancher ». N'écris aucun code et ne commite rien. Attends la validation du périmètre.
