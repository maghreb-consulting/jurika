# CLAUDE.md — regles de travail sur le depot JURIKA (cabinet, V2)

Ce fichier est lu au demarrage de chaque session. Il condense les regles de methode du
projet ; le document d'amorcage de reference est `~/docs/CAHIER_DES_CHARGES_JURIKA.md` (puis
`~/docs/ANNEXE_TECHNIQUE_JURIKA.md`), a lire au besoin. Tout est en ASCII pur (voir regle 6).

## Ce qu'est JURIKA (rappel bref)

SaaS B2B multi-tenant de gestion juridique des entreprises au Maroc, edite par Maghreb
Consulting. Le coeur metier est **deterministe** : a donnees identiques, acte identique,
octet pour octet. **Aucun LLM ne produit d'acte.** L'IA n'intervient qu'en peripherie
(chatbot RAG a reponses sourcees, extraction de pieces d'identite Donut/docTR).

Pile : 10 microservices Spring Boot 3.4 / Java 21 (Maven multi-modules + module commun) ;
2 services Python (kie = Donut, ocr = docTR) ; 1 service Node temps reel ; front React 19 /
TS / Vite / Tailwind ; PostgreSQL 16 (JSONB + Row Level Security + filtrage applicatif
explicite, toujours les deux), Redis, RabbitMQ (conserve), MinIO (S3), Collabora CODE.
Moteur documentaire maison DocxTemplateEngine (Apache POI/XWPF), horodatage ZIP fige.

## Methode de travail (lecons payees — a respecter)

1. **Commits et push** : commits locaux sur une branche de lot, un par etape validee par les
   tests. Push autorise uniquement sur `lot/*` et `outillage/*` (`git push -u origin <branche>`,
   regle `allow` de `.claude/settings.json`), apres un verify complet vert. Jamais de push sur
   `main`, jamais de fusion par Claude, jamais de reecriture d'historique. Une branche par lot.
   Rapports factuels (ce qui est vert, ce qui est rouge, ce qui est saute -- sans arrondir).
2. **Cloture d'un lot** : `mvn -o clean verify -Pit` (le `clean` est obligatoire — un vert
   incremental ne prouve rien sur une suppression) + suite frontend + **lecture des documents
   produits**. Chaque defaut grave a ete trouve en ouvrant un fichier, jamais par un compteur.
3. **Ne jamais conclure** sur : une commande interrompue (sortie vide != propre) ; un motif de
   recherche trop etroit ; un nom jamais ecrit (les conditions en francais deviennent des
   drapeaux internes) ; le code de retour d'un pipeline (`mvn ... | tail` ment — lire le
   Reactor Summary).
4. **Un test doit echouer d'abord** quand il garde un defaut (caracterisation) et attendre la
   donnee, jamais un element statique. La presence d'une cle ne prouve pas sa justesse : le
   dictionnaire tranche, jamais la ressemblance des noms.
5. **Verifier a l'ecran / dans le fichier**, pas seulement en test. Signaler tout ecart plutot
   que le contourner. Ne jamais rien inventer (delai, cout, variable) : laisser vide et signaler.
6. **ASCII pur** pour identifiants, fichiers, scripts, commits (l'encodage Windows a deja casse
   des scripts). Francais soigne et accentue uniquement pour les contenus utilisateurs.
   La regle s'applique aux fichiers du depot (noms et contenu : code, scripts, configuration,
   CI) et aux messages de commit : ni guillemets francais, ni signe paragraphe, ni symbole
   degre. Seuls les contenus destines aux utilisateurs (gabarits, textes affiches,
   documentation d'exploitation en francais) font exception.
7. **Migrations Flyway appliquees : intouchables** (meme un commentaire) — `validate-on-migrate`
   est desactive, un ecart serait avale en silence. La regle s'apprecie par rapport a la base
   courante et aux installations futures, pas par rapport a l'ancienne base du portable (archive).
   Une migration en echec (annulee par Flyway, absente de `flyway_history_*`) n'est pas appliquee.
   **Une migration appliquee au Z440 ne se modifie plus, meme avant la fusion** (l'activation de
   fin de lot l'applique depuis la branche du lot) : toute correction passe par une nouvelle
   migration.
8. **Deux piles ne tournent jamais en meme temps** (memes noms de conteneurs, memes ports).
   **Jamais `docker compose down -v`** : les volumes sont la base.
9. **Motif recurrent a chercher partout : la configuration ou le controle silencieusement
   inoperant** — test qui surveille du code mort, healthcheck qui ne s'execute pas, valeur de
   repli qui masque une donnee absente, cle presente mais fausse, baseline Flyway qui saute une
   migration fondatrice.
10. **L'execution qui marche ne remplace pas la suite qui passe.** Une pile 18/18 saine, un
    smoke-test vert et une extraction reussie ne valent pas `mvn -o clean verify -Pit` complet.
    Le 2026-09-25, un correctif pousse sans avoir lance le verify a casse la CI (tests unitaires
    de contenu des migrations qui affirmaient l'etat d'avant). Verify complet et vert AVANT tout
    push, sans exception.

## Mode autonome des lots (`/lot-complet`, depuis le 2026-10-09)

- `/lot-complet <lot>` enchaine demarrage, decoupage, execution, verification et push de la
  branche du lot **sans arret ni question**. Il reprend a la premiere etape non cochee du
  journal s'il est interrompu. Resultat : `~/docs/rapports/<lot>_rapport_final.md` (perimetre et
  decoupage, fait, **Decisions a revoir**, non resolus, verify, points a l'ecran, plan Docker,
  lien de PR).
- Le perimetre reprend d'office les entrees du backlog ci-dessous qui concernent le lot.
- Face a un choix : option la plus sure, la plus conforme au cahier des charges, la plus
  petite en cas de doute ; inscrite dans **Decisions a revoir** avec l'alternative ecartee.
- Decision du directeur (envoi des courriels, CMI, CNDP, duree de conservation, clauses
  particulieres des statuts...) : jamais inventee ; emplacement marque `A_DECIDER`, inscrit
  dans **Decisions a revoir**.
- Nouvelles migrations permises sur la branche du lot ; une migration presente dans `main`
  ne se modifie jamais.
- Test rouge du fait du test ou du harnais : corrige. Defaut hors perimetre : backlog. Defaut du
  lot rouge apres 3 tentatives : **non resolu** au rapport, etape mise de cote, lot poursuivi.
- **Docker autonome (depuis le 2026-10-09 : la plateforme n'est pas en production).** Permis
  sans demande : `docker ps`, `logs`, `inspect`, `exec`, et `docker compose -p jurika-local` avec
  `build`, `up -d`, `stop`, `start`, `restart`, `ps`. Toujours refuses : `down -v`, toute
  commande contenant `prune`, `docker volume rm`, `docker rm -v`. Toute autre commande Docker
  demande l'accord de l'utilisateur.
- **Activation en fin de lot** : apres le verify vert et le push, `/lot-complet` active lui-meme
  la branche du lot sur le Z440 : sauvegarde controlee d'abord (`pg_dump -Fc` de toutes les
  bases, `pg_dumpall --globals-only`, verifies par `pg_restore --list` ; volume MinIO par
  `scripts/sauvegarde-volume-par-exec.py`), reconstruction et recreation, etat sain, test de
  fumee ; resultat dans le rapport final. Echec : retour arriere du code (depuis `main`), jamais
  de restauration de donnees automatique. `/lot-activer <lot>` reste disponible (pas a pas).
- Les commandes `/lot-demarrer`, `/lot-executer`, `/lot-verifier` et `/lot-cloturer` (mode
  pas a pas, avec points d'arret) restent disponibles.

## Regle des variables d'un acte (2026-10-09 ; a reprendre dans le perimetre de L3)

- Chaque variable du dictionnaire est classee **interne** ou **externe**. Externe : donnee
  attendue d'un organisme (numero RC, ICE, IF, date d'immatriculation, ou toute donnee produite
  par une administration ou un tiers). Interne : tout le reste (donnees du client, du dossier,
  des decisions).
- Une variable **interne** manquante **bloque** la generation, et la donnee manquante est
  **nommee** a l'utilisateur.
- Seule une variable **externe** peut manquer : l'acte sort avec un **marqueur visible**, la
  plateforme **reclame** la donnee, puis l'acte se **regenere** quand elle arrive.
- Les 17 gabarits du corpus dont le texte a change au lot L2 (decision D3,
  `~/docs/rapports/L2_comparaison_temoin.md`) suivent cette regle : leurs marqueurs "VALEUR
  MANQUANTE" actuels sont a reclasser (interne : blocage nomme ; externe : marqueur et relance).

## Corpus documentaires (source de verite metier)

- Corpus de reference courant : `~/corpus/CORPUS_2026-10-03` (lire son `00_LISEZ_MOI.md`).
  222 modeles, 12 matrices avec un onglet « Demarches », dictionnaire unique
  `00_COMMUN/DICTIONNAIRE_UNIQUE_VARIABLES.xlsx`.
- Le corpus est une **donnee versionnee** : chaque nouvelle version est un **nouveau dossier
  date**, jamais une modification en place. Les anciennes livraisons de `~/corpus` (ex.
  `LIVRAISON_2026-09-09`, `modifications_v2`) sont des **archives** — ne pas les utiliser
  comme reference.
- Gabarits du cabinet **intouchables** : tout ecart se **rapporte**, jamais ne se corrige en
  silence. Les `.docx` (`GABARITS_WORD/`) sont la source d'execution, les `.md` (`MODELES_MD/`)
  des references.
- La validation du directeur couvre le **contenu juridique**, pas la mecanique de balisage :
  les incoherences mecaniques sont attendues et se rapportent.

## Architecture des donnees (piege connu)

Plusieurs services Spring **partagent la meme base `jurika_db`** (schema `public`), chacun
avec sa propre table d'historique `flyway_history_<service>`. `billing` est seul sur
`jurika_billing`. Consequence : `auth-service` est le **proprietaire du schema racine**
(`workspaces`, `users`, `subscriptions`, `audit_log`) ; les services qui referencent ces
tables doivent migrer **apres** auth. `baseline-on-migrate` ne pose un baseline que sur un
schema non vide — sur base partagee, cela peut faire **sauter la V1 fondatrice** d'un service
si un autre a deja peuple `public`. Toute table reellement partagee (ex. `audit_log`) se cree
en `IF NOT EXISTS` / `DROP POLICY IF EXISTS`.

## Commandes utiles

- Demarrer la pile locale (serveur) : `./scripts/start-local.sh` (build + up + attente des
  healthchecks). `--no-build`, `--logs`, `--reset` disponibles. ATTENTION : `--reset` fait
  `down -v` (volumes effaces) : ne jamais l'utiliser sur le Z440 (regle 8).
- **Sur le Z440, toute commande docker compose porte -p jurika-local.** Sans lui, compose prend
  le projet `infrastructure` (nom du dossier des fichiers) : il cree des volumes vides et des
  images `infrastructure-*` a cote de la pile en place (incident du 2026-10-08, lot L0).
- Controle des ports publies (lot L0) : `python3 scripts/controle-ports-publies.py`.
- Seed de demonstration : `node scripts/seed-demo.mjs`.
- Smoke test : `./scripts/smoke-test.sh`.
- Verification d'un lot backend : `mvn -o clean verify -Pit` (depuis `backend-java/`).

## Backlog / dette connue

- **Bump des actions CI** : `setup-java@v5`, et les versions d'actions ciblant Node 24 (les
  actions actuelles s'appuient sur des runtimes en fin de vie).
- **Nettoyage du lint frontend herite** : `frontend-react` porte des avertissements de lint
  preexistants (bruit dans les annotations CI), a resorber independamment.
- **`out-of-order: true` : une cle qui ouvre plus que sa serrure.** Active sur CINQ services
  (ai, dashboard, workflow, dataroom, ticket) depuis l'import initial — jamais restreint a
  ticket. Elle affaiblit durablement la garantie d'ordre : une migration de version inferieure
  « oubliee » s'appliquera tard, sans bruit. La migration `V23_1` (ticket) en depend UNIQUEMENT
  pour se poser sur une base deja passee a V26 (le Z440) ; une base vierge applique V23_1 dans
  l'ordre, sans ce drapeau. Condition pour retirer cette dependance cote ticket : quand toutes
  les bases vivantes portent V23_1 dans `flyway_history_ticket`. Revoir (voire desactiver) le
  drapeau sur les 4 autres services est un chantier distinct, a instruire.
- **Inventaire de derive — base Z440.** `flyway_history_ticket` du serveur porte l'empreinte de
  l'ANCIEN V24 (variante videe, commit f8e09e0) et d'un V26 relache transitoire, tous deux
  divergents du V24/V26 restaures au depot (4887fdb). `validate-on-migrate: false` le masque :
  c'est le motif recurrent — un ecart reel que rien ne signale. La DONNEE, elle, est correcte
  (V26 a bien cable taxe pro / CNSS). A resorber en rejouant ticket sur une base vierge, ou a
  accepter en connaissance de cause tant que le Z440 reste une base de developpement.
- **Staging : ai-service ne demarrera plus sans corpus (lot L2).** `jurika.corpus.root` est
  obligatoire sans repli ; `infrastructure/deployment/docker-compose.staging.yml` (deploiement
  manuel, secrets non configures) ne monte aucun corpus et ne definit pas `JURIKA_CORPUS_ROOT`.
  A traiter avant tout deploiement staging : montage en lecture seule d'un dossier date.
- FicheClientPdfTest et DeboursPdfGeneratorTest avalent les erreurs d'écriture (catch (Exception ignore)) : à remplacer par un échec ou un journal explicite.
- **4 tests ai-service ignores faute de LibreOffice sur le serveur Z440** :
  `DocxToPdfConverterTest.real_conversion_produces_valid_pdf_when_libreoffice_installed`
  (`@EnabledIf("hasLibreOffice")`) et les 3 tests de `HtmlToPdfConverterTest` (assumption
  « LibreOffice absent »). La conversion PDF n'est donc jamais verifiee par `verify -Pit` ici.
- **Gabarits rendus par deux mappers differents** : `RAPPORT_GESTION` (AnnualReportMapper et
  ApprobationComptesMapper), `PV_DISSOLUTION_LIQUIDATION_SARL` et
  `PV_DISSOLUTION_LIQUIDATION_SARL_AU` (DissolutionMapper et LiquidationMapper). Doublon a
  resorber au lot qui unifie les parcours.

### Lot L2 (chargeur du corpus, 2026-10-09) -- dette relevee

- **Parcours a basculer sur les modeles du corpus (L4).** 12 des 15 gabarits du classpath
  absents du corpus ont un equivalent dans `CORPUS_2026-10-03` (STATUTS deterministes,
  CONVOCATION_AG, FEUILLE_PRESENCE_AG, RAPPORT_GESTION, RAPPORT_LIQUIDATION_DIRECTEUR,
  PV_DISSOLUTION_LIQUIDATION, PV_MODIFICATION, ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE) :
  les parcours basculeront sur ces equivalents en L4 (correspondances parfois reparties sur
  plusieurs modeles, choix selon la donnee du parcours). Restent :
  PV_DEFAUT_QUORUM_SARL_AU et PV_IRREGULARITE_CONVOCATION_SARL_AU (sans objet) ;
  PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU (A_DECIDER directeur : manque, ou doublon de
  PV_CREATION_SUCCURSALE_ETRANGERE_SARL qui vise une societe etrangere sans forme).
  Tableau : `~/docs/rapports/L2_correspondance_gabarits.md`.
- **D8 : afficher un message clair a l'utilisateur au lieu d'une erreur 500 quand un modele est
  refuse** (gabarit du corpus modifie depuis le chargement ou non rendable : `CorpusException`).

### Lot L0 (securite serveur, 2026-10-08) -- dette relevee

- **PRIORITE HAUTE : le consommateur RabbitMQ de l'audit (`AuditEventConsumer`) avale toute
  exception** : une trace refusee est perdue sans bruit. Il faut la rejeter vers une file
  d'erreurs (dead-letter) ou la rejouer.
- **Bascule `jurika_app` restante** : supervision, dashboard, ai et billing se connectent encore
  en proprietaire (superutilisateur sur le Z440), donc hors RLS.
- **Seed e2e expose** : la passerelle route `/api/v1/test/**` (route `test-seed`) vers le seed
  de dataroom, actif sur le Z440 (`JURIKA_TEST_SEED_ENABLED=true`) : creation de workspaces et
  mots de passe en clair par un appel anonyme. A fermer (route hors profil de test, ou seed
  desactive sur le Z440).
- **`/internal/**` sans authentification entre services** : bloque a la passerelle et ports
  lies a 127.0.0.1 (L0), mais tout conteneur du reseau Docker peut les appeler.
- **Transitions de ticket par UPDATE direct** depuis workflow
  (`WorkflowUseCases#autoTransitionTicket`) : au lot des parcours.
- **Dependance circulaire des migrations ticket V21 / dataroom** : ticket V21 reference
  `dataroom_documents`, que dataroom V5 cree en referencant `tickets`. Les tests passent par
  l'ordre auth, ticket jusqu'a V20, dataroom, fin de ticket. A demeler au lot des parcours.
- **`JwtAuthFilter`** : si la suite de la chaine leve une exception dans le `try`, le
  `catch (Exception)` relance `chain.doFilter` une seconde fois (double execution).
- **`AuditLogJpaRepository` (auth)** inutilise depuis E10b : code mort a retirer (test d'abord).
- **Annulation d'un ticket** : action de remplacement pour la Data Room (RG-TKT-04), au lot des
  parcours (la suppression automatique a ete retiree en E16a).
- **Objets MinIO orphelins** quand l'ecriture en base echoue, et suppression de l'ancien objet
  avant validation (dataroom).
- **Front : boutons d'action encore visibles au superviseur** alors que le serveur les refuse
  (E5, E5b, E6) ; 46 fichiers du front citent `SUPERVISEUR`.
- **Chatbot (lot L5)** : corpus RAG range par workspace (`CorpusRetriever`, `WHERE workspace_id
  = ?`). Les sources du super-admin (seul autorise depuis E24) restent dans son workspace et
  n'atteignent pas les cabinets : corpus commun a concevoir (migration probable).
- **Export ZIP du Dossier Juridique** : echec complet (`duplicate entry`) quand deux documents
  d'un meme type portent le meme nom de fichier (`exportSelectionAsZip`).
- **Erreurs avalees (motif 9)** : `DossierIdentityQueryService#identity` (workflow) rend une map
  vide sur toute exception ; `WorkflowUseCases#executeStep` avale l'echec de
  `loadDossierFactsByTicket` en DEBUG.
- **Dockerfile de dashboard** : ignore une erreur Maven pendant le telechargement des
  dependances (`dependency:go-offline ... || true`, vu en E26 sur une collision du cache
  partage entre constructions paralleles).
- **Exemple d'environnement serveur** : `.env.local.server.example` n'active pas le profil
  compose `mailhog` (`COMPOSE_PROFILES`) alors que `start-local.sh` attend `jurika-mailhog` :
  une installation faite depuis l'exemple n'atteint jamais 18/18.
- **Scripts e2e ad hoc** de `scripts/` qui visent `http://localhost:<port interne>` (8025, 8085,
  8089, 8090) : ports desormais lies a 127.0.0.1 et Node peut resoudre `localhost` en `::1` ;
  a passer en `127.0.0.1` (fait pour `smoke-test.mjs`).
- **Octet NUL litteral** dans `DataroomJuridiqueService.java` (separateur de cle) : `file` classe
  le source en donnees binaires.
- **Le seed de demonstration ne rattache ni client ni responsable aux dossiers**
  (`scripts/seed-demo.mjs`, cabinet JUR-DEMO2) : client et employes n'y voient aucun dossier.
  Complete a la main sur le Z440 le 2026-10-08 pour la verification du lot L0 (client2,
  rattachements de 4 dossiers).
- **minio tourne en root** (`user: "0:0"`) pour rester compatible avec le volume existant
  (ecrit en root par l'ancienne image `minio/minio`) ; a durcir en changeant le proprietaire
  du volume (image Chainguard prevue pour l'uid 65532).
- **Cles JWT et uid des images** : les images Java tournent en uid 1000 et lisent
  `infrastructure/secrets/jwt/*.pem` (cle privee en 600). Sur le Z440 cela marche parce que
  l'utilisateur de l'hote est aussi l'uid 1000 ; `start-local.sh` ne garantit pas ce contrat
  (installation par un autre uid : auth ne demarre pas, vu en CI le 2026-10-08).
- **`start-local.sh` attend `jurika-mailhog` "healthy"** alors que mailhog n'a aucun controle
  de sante (ni compose ni image) : l'attente ne peut pas aboutir quand mailhog tourne
  (corrige dans `base-vierge.yml` le 2026-10-08 : "running" suffit sans controle de sante).
- **Le test de fumee affiche un prefixe de la cle Stripe** (`scripts/smoke-test.mjs`,
  controle "Stripe env vars" : `sk_test_51...`) : ne plus afficher aucune partie d'une cle.
- **Liste des conteneurs attendus dupliquee** entre `start-local.sh` et
  `.github/workflows/base-vierge.yml` : a factoriser.
- **Messages de commit du lot L0 non ASCII** (24 commits, guillemets francais, signe
  paragraphe, degre) : historique conserve tel quel par decision du 2026-10-08.

## Documents de reference

- `~/docs/CAHIER_DES_CHARGES_JURIKA.md` — cahier des charges final, qui **fait foi** (regles de
  gestion completes).
- `~/docs/ANNEXE_TECHNIQUE_JURIKA.md` — annexe technique.
- Les anciens documents de `~/docs/archives/` sont des **archives** — ne pas les utiliser comme
  reference.
