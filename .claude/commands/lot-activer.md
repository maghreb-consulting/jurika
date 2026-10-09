---
description: Execute sur le Z440 le plan Docker du rapport final d'un lot, une commande a la fois, chacune soumise a l'accord de l'utilisateur
argument-hint: <lot>  (ex. L2)
---

Active le lot **$ARGUMENTS** sur le Z440 en executant le **plan Docker** de
`~/docs/rapports/<lot>_rapport_final.md`. Cette commande n'est PAS autonome : chaque
commande Docker est soumise a l'accord de l'utilisateur, une a la fois.

Si `$ARGUMENTS` est vide, demande l'identifiant du lot et arrete-toi.
Dans la suite, `<lot>` vaut `$ARGUMENTS`.

## 1. Preconditions
- Le RAPPORT existe et contient la section **Plan Docker a executer apres la fusion**. Sinon,
  dis-le et arrete-toi.
- La branche du lot est fusionnee dans `origin/main` (`git fetch` puis
  `git merge-base --is-ancestor origin/<branche du lot> origin/main`). Sinon, dis-le et
  arrete-toi : on n'active jamais un lot non fusionne.
- `git checkout main && git pull --ff-only` ; arbre de travail propre.
- Etat initial en lecture seule : `docker ps` et
  `docker compose -p jurika-local ... ps -a`. Une seule pile tourne (regle 8 de `CLAUDE.md`).

## 2. Execution, une commande a la fois
Pour chaque commande du plan, dans l'ordre :
1. Montre la commande exacte, ce qu'elle fait et le controle attendu.
2. Lance-la seule (l'accord de l'utilisateur est demande par l'outil) ; jamais plusieurs
   commandes Docker chainees dans un meme appel.
3. Controle le resultat (code de retour, sortie, controle de sante...) et note-le dans
   `~/docs/rapports/<lot>_activation.md` (horodatage, commande, resultat).
4. Ecart avec le controle attendu : arrete-toi, rapporte l'ecart exact et propose la suite
   (dont le retour arriere du plan). Ne continue pas sans accord.

Regles permanentes :
- `-p jurika-local` sur **chaque** commande `docker compose` ;
- jamais `down -v`, jamais `prune` ; suppressions nom par nom, apres preuve (vide, non reference) ;
- jamais de secret en argument de commande ni affiche : fichiers ou entree standard ;
- la sauvegarde (`pg_dump -Fc` de toutes les bases non modeles, `pg_dumpall --globals-only`,
  verifies par `pg_restore --list`) precede toute modification.

## 3. Fin
- Test de fumee (`./scripts/smoke-test.sh`) et verifications du plan.
- Complete `~/docs/rapports/<lot>_activation.md` : commandes, resultats, ecarts, sauvegardes
  (chemins, tailles), etat final de la pile.
- Rappelle les **points a controler a l'ecran** du RAPPORT, puis arrete-toi.
