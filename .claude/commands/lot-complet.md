---
description: Lot en mode autonome complet -- demarrage, decoupage, execution, verification, push de la branche du lot et activation sur le Z440, sans arret ni question ; rapport final
argument-hint: <lot>  (ex. L2)
---

Execute le lot **$ARGUMENTS** de bout en bout, en **mode autonome complet** : tu ne t'arretes
jamais et tu ne poses aucune question a l'utilisateur pendant le lot. Tu produis a la fin
`~/docs/rapports/<lot>_rapport_final.md` (ci-dessous : le RAPPORT).

Si `$ARGUMENTS` est vide : c'est la seule question permise ; demande l'identifiant du lot.
Dans la suite, `<lot>` vaut `$ARGUMENTS`.

Les commandes `/lot-demarrer`, `/lot-executer` et `/lot-verifier` decrivent les etapes ; tu en
appliques le contenu, mais **chacun de leurs points d'arret est remplace par les regles du mode
autonome ci-dessous**. Les regles de `CLAUDE.md` restent en vigueur (ASCII, migrations
appliquees intouchables, test rouge d'abord, motif 9, verify complet avant push...).

## Regles du mode autonome (prevalent sur les points d'arret des autres commandes)

1. **Aucune question, aucun arret.** Face a un choix : retiens l'option la plus sure, la plus
   conforme au cahier des charges (`~/docs/CAHIER_DES_CHARGES_JURIKA.md`, qui fait foi) et, en
   cas de doute, la plus petite. Inscris-la dans le RAPPORT, section **Decisions a revoir** :
   la question, l'option retenue, l'alternative ecartee et pourquoi. Puis continue.
2. **Decision du directeur.** Si un sujet attend une decision du directeur (solution d'envoi
   des courriels, CMI, CNDP, duree de conservation, clauses particulieres des statuts, ou tout
   choix juridique ou commercial equivalent) : ne l'invente pas. Construis ce qui n'en depend
   pas, laisse un emplacement marque `A_DECIDER` (commentaire, propriete ou constante nommee
   ainsi, jamais une valeur inventee), et inscris-le dans **Decisions a revoir**.
3. **Migrations.** Les nouvelles migrations Flyway sont autorisees sur la branche du lot. Ne
   modifie **jamais** une migration deja presente dans `main` (`git diff main -- '*/db/migration/*'`
   ne doit montrer que des fichiers ajoutes), ni une migration deja **appliquee au Z440**, meme
   avant la fusion (l'activation de fin de lot l'applique : apres elle, toute correction passe
   par une nouvelle migration).
4. **Tests rouges.**
   - Cause dans ton test ou ton harnais : corrige-le, continue, note-le au journal.
   - Defaut hors perimetre : inscris-le au backlog de `CLAUDE.md`, continue.
   - Defaut du lot encore rouge apres **3 tentatives** : note-le **non resolu** dans le RAPPORT
     (erreur exacte, pistes), annule les changements de l'etape (`git restore` sur ses seuls
     fichiers), laisse sa case non cochee avec la mention `NON RESOLU`, passe a l'etape suivante.
5. **Docker (autonome depuis le 2026-10-09 : la plateforme n'est pas en production).**
   - Permis sans demande (`.claude/settings.json`) : `docker ps`, `logs`, `inspect`, `exec`, et
     `docker compose -p jurika-local ...` avec `build`, `up -d`, `stop`, `start`, `restart`, `ps`.
   - Toujours refuse : `down -v` / `--volumes`, toute commande contenant `prune`,
     `docker volume rm`, `docker rm -v`.
   - Toute autre commande Docker (`run`, `rm`, `stop` hors compose, `down`, `config`...) sort du
     mode autonome : ne la lance pas ; trouve une voie permise ou inscris le besoin au RAPPORT.
   - `-p jurika-local` sur **chaque** commande `docker compose` ; jamais de secret en argument.
   - Pendant les etapes 1 a 6, seules les commandes en lecture (`ps`, `logs`, `inspect`) sont
     utiles ; la pile n'est modifiee qu'a l'etape 7 (activation). Testcontainers reste permis.
6. **Git.** Commits locaux sur la branche du lot ; push autorise uniquement sur `lot/*` et
   `outillage/*` (`git push -u origin <branche>`). Jamais de push sur `main`, jamais de fusion,
   jamais de reecriture d'historique.
7. **Secrets.** Jamais de secret en argument de commande ni affiche : fichiers ou entree
   standard seulement.

## 1. Reprise
- Si `~/docs/rapports/<lot>_journal.md` existe : reprends a la **premiere etape non cochee**
  (une etape marquee `NON RESOLU` compte comme traitee). Verifie d'abord que la branche courante
  est celle du journal (`Branche :`) et que l'arbre de travail est propre ; sinon, si des
  changements non commites appartiennent a l'etape en cours, termine-la ou annule-la
  (`git restore` sur ses fichiers) et note-le au journal.
- Si le RAPPORT existe deja sans journal, ou si tout est coche : va directement a l'etape 5.

## 2. Demarrage (contenu de /lot-demarrer, sans arret)
- Lis `CLAUDE.md`, `~/docs/PLAN_DEVELOPPEMENT_JURIKA.md`, `~/docs/ANALYSE_ECART.md` et le cahier
  des charges. Lot absent des sources : c'est un point non resolu bloquant ; ecris le RAPPORT
  avec ce constat et arrete-toi (seul cas d'arret).
- `git checkout main && git pull --ff-only`, puis branche `lot/<slug>` (kebab-case ASCII ; le
  slug ne contient jamais `main` : `.claude/settings.json` demanderait alors le push).
- Ecris `~/docs/rapports/<lot>_perimetre.md`. Le perimetre **reprend d'office les entrees
  du backlog de `CLAUDE.md` qui concernent le lot** (citees mot pour mot, avec leur source).
  Les points a trancher sont tranches selon la regle 1 et copies dans **Decisions a revoir**.

## 3. Decoupage (sans validation)
- Ecris `~/docs/rapports/<lot>_journal.md` au format de `/lot-executer` (etapes courtes, une
  etape = un changement testable, fichiers vises, test qui la valide).

## 4. Execution (contenu de /lot-executer, points d'arret remplaces par les regles ci-dessus)
- Pour chaque etape : code, `mvn -B -o verify -pl <modules> -am` (avec `-Pit` si l'etape a des
  IT), lecture du Reactor Summary, case cochee, entree au journal, commit local au format du
  depot, en ASCII, avec la ligne `Co-Authored-By` de la session, indexation fichier par fichier.
- Entrees du backlog traitees : retire-les de `CLAUDE.md` dans le commit de l'etape ; nouveaux
  defauts hors perimetre : ajoute-les au backlog.

## 5. Verification (contenu de /lot-verifier)
- `mvn -B -o clean verify -Pit` complet depuis `backend-java/`, en arriere-plan, sortie dans le
  scratchpad ; suite frontend si le lot touche le front. Reactor Summary brut ; resultats par
  module ; chaque echec et chaque test ignore avec sa raison ; `git status`. Rapport
  `~/docs/rapports/<lot>_verification.md`.
- Rouge : applique la regle 4 (3 tentatives), puis relance le verify complet. Le push n'a lieu
  qu'avec un verify complet **vert** ; sinon, pas de push, et le RAPPORT le dit.

## 6. Push
- `git push -u origin <branche du lot>` (jamais `main`). Note le lien d'ouverture de la PR affiche
  par le push.

## 7. Activation sur le Z440 (executee par toi, apres un push sur verify vert)
Le code active est celui de la branche du lot (non fusionnee). Journal de chaque commande
(horodatage, commande, resultat) dans `~/docs/rapports/<lot>_activation.md`.
1. **Etat initial** : `docker ps` et `docker compose -p jurika-local <fichiers> ps -a` ; une seule
   pile ; nombre de conteneurs sains note.
2. **Preconditions du lot** (variables d'environnement nouvelles, corpus, etc.) : ajoutees a
   `.env.local` apres copie de ce fichier dans le dossier de sauvegarde (mode 600) ; aucune
   valeur secrete affichee.
3. **Reconstruction** de toutes les images (`build --parallel`), pile en marche.
4. **Sauvegarde controlee d'abord** (dossier `~/backups/<lot>-<date>`, mode 700) :
   - arret des consommateurs (`stop` des services applicatifs ; postgres, redis, rabbitmq et
     minio restent en marche) ;
   - `pg_dump -Fc` de **toutes** les bases non modeles (liste lue dans `pg_database`) et
     `pg_dumpall --globals-only`, par `docker exec jurika-postgres` ; chaque archive controlee :
     taille non nulle, `pg_restore --list` code 0 et nombre d'entrees, au moins 2 `CREATE ROLE` ;
   - volume MinIO : `python3 scripts/sauvegarde-volume-par-exec.py jurika-minio /data <archive>`
     (controle integre : nombre de fichiers et octets) ;
   - **un controle en echec arrete l'activation** : redemarrer les consommateurs (`start`),
     noter l'echec au RAPPORT, ne rien recreer.
5. **Recreation** de tous les conteneurs : `up -d --force-recreate` ; attente de l'etat sain de
   tous les conteneurs (10 min au plus).
6. **Controles du lot** (journal, montages, migrations appliquees dans `flyway_history_*`...)
   puis **test de fumee** `./scripts/smoke-test.sh`.
7. **Echec apres recreation** (conteneur non sain, test de fumee rouge, controle du lot faux) :
   reconstruire et recreer depuis `main` (retour arriere du code), verifier l'etat sain, et
   noter l'echec au RAPPORT. La restauration des donnees n'est jamais automatique : elle est
   proposee dans le RAPPORT (commandes pretes), a decider par l'utilisateur.

## 8. Rapport final
Ecris le RAPPORT avec, dans cet ordre :
1. **Perimetre et decoupage** (lien vers le perimetre et le journal, liste des etapes et statut).
2. **Ce qui a ete fait** (par etape : changement, fichiers, tests, commit).
3. **Decisions a revoir** (question, option retenue, alternative ecartee, raison ; les `A_DECIDER`).
4. **Points non resolus** (etape, erreur exacte, tentatives, pistes).
5. **Resultats de `mvn -o clean verify -Pit`** (Reactor Summary brut, totaux par module, ignores
   et raisons).
6. **Points a controler a l'ecran** (par role : superviseur, employe, client ; comptes de
   demonstration si utiles, sans aucun mot de passe reel).
7. **Activation sur le Z440** (etape 7 executee) : commandes lancees et resultats, sauvegardes
   (chemins, tailles, controles), etat final de la pile, test de fumee, controles du lot, ecarts ;
   commandes de retour arriere pretes (restauration des sauvegardes : les migrations ne
   s'annulent pas).
8. **Lien de la PR**.

Affiche ensuite le chemin du RAPPORT et sa section **Decisions a revoir**, puis arrete-toi.
