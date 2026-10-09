---
description: Lot en mode autonome complet -- demarrage, decoupage, execution, verification et push de la branche du lot, sans arret ni question ; rapport final
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
   ne doit montrer que des fichiers ajoutes).
4. **Tests rouges.**
   - Cause dans ton test ou ton harnais : corrige-le, continue, note-le au journal.
   - Defaut hors perimetre : inscris-le au backlog de `CLAUDE.md`, continue.
   - Defaut du lot encore rouge apres **3 tentatives** : note-le **non resolu** dans le RAPPORT
     (erreur exacte, pistes), annule les changements de l'etape (`git restore` sur ses seuls
     fichiers), laisse sa case non cochee avec la mention `NON RESOLU`, passe a l'etape suivante.
5. **Docker.** Aucune commande Docker sur la pile du Z440 (projet `jurika-local`, ses conteneurs,
   volumes, images) pendant le lot. Les tests Testcontainers de `mvn ... -Pit` sont permis.
   Tout ce qui doit etre fait sur le Z440 va dans le **plan Docker** du RAPPORT (voir plus bas).
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

## 7. Rapport final
Ecris le RAPPORT avec, dans cet ordre :
1. **Perimetre et decoupage** (lien vers le perimetre et le journal, liste des etapes et statut).
2. **Ce qui a ete fait** (par etape : changement, fichiers, tests, commit).
3. **Decisions a revoir** (question, option retenue, alternative ecartee, raison ; les `A_DECIDER`).
4. **Points non resolus** (etape, erreur exacte, tentatives, pistes).
5. **Resultats de `mvn -o clean verify -Pit`** (Reactor Summary brut, totaux par module, ignores
   et raisons).
6. **Points a controler a l'ecran** (par role : superviseur, employe, client ; comptes de
   demonstration si utiles, sans aucun mot de passe reel).
7. **Plan Docker a executer apres la fusion** (pour `/lot-activer`), en commandes numerotees, une
   par ligne, chacune avec son controle attendu :
   - sauvegarde d'abord : `pg_dump -Fc` de **toutes** les bases non modeles et
     `pg_dumpall --globals-only`, chaque archive verifiee par `pg_restore --list` (et la taille) ;
     volume MinIO en archive tar si le lot touche aux documents ;
   - `-p jurika-local` sur **chaque** commande `docker compose` ;
   - jamais `down -v`, jamais `prune` ; suppressions nom par nom seulement ;
   - jamais de secret en argument de commande (fichier ou entree standard) ;
   - reconstruction, recreation des services touches, attente des controles de sante, test de
     fumee, verifications ;
   - retour arriere : restauration des sauvegardes (les migrations ne s'annulent pas).
8. **Lien de la PR**.

Affiche ensuite le chemin du RAPPORT et sa section **Decisions a revoir**, puis arrete-toi.
