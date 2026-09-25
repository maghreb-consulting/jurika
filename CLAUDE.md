# CLAUDE.md — regles de travail sur le depot JURIKA (cabinet, V2)

Ce fichier est lu au demarrage de chaque session. Il condense les regles de methode
du document d'amorcage `docs/refonte-v2/CONTEXTE_PROJET_JURIKA.md` (a lire en entier au
besoin). Tout est en ASCII pur (voir regle 6).

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

1. **Pas de commit sans feu vert** de l'utilisateur. Une branche par lot. Rapports factuels
   (ce qui est vert, ce qui est rouge, ce qui est saute — sans arrondir).
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
7. **Migrations Flyway appliquees : intouchables** (meme un commentaire) — `validate-on-migrate`
   est desactive, un ecart serait avale en silence. La regle s'apprecie par rapport a la base
   courante et aux installations futures, pas par rapport a l'ancienne base du portable (archive).
   Une migration en echec (annulee par Flyway, absente de `flyway_history_*`) n'est pas appliquee.
8. **Deux piles ne tournent jamais en meme temps** (memes noms de conteneurs, memes ports).
   **Jamais `docker compose down -v`** : les volumes sont la base.
9. **Motif recurrent a chercher partout : la configuration ou le controle silencieusement
   inoperant** — test qui surveille du code mort, healthcheck qui ne s'execute pas, valeur de
   repli qui masque une donnee absente, cle presente mais fausse, baseline Flyway qui saute une
   migration fondatrice.

## Corpus documentaires (source de verite metier)

- Gabarits du cabinet **intouchables** : tout ecart se **rapporte**, jamais ne se corrige en
  silence. Les `.docx` sont la source d'execution, les `.md` des references.
- Chaque livraison est datee et accompagnee d'une note ; tout changement de convention
  s'annonce. La livraison du 3 septembre est ecartee (decision direction).
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
  healthchecks). `--no-build`, `--logs`, `--reset` disponibles.
- Seed de demonstration : `node scripts/seed-demo.mjs`.
- Smoke test : `./scripts/smoke-test.sh`.
- Verification d'un lot backend : `mvn -o clean verify -Pit` (depuis `backend-java/`).

## Backlog / dette connue

- **Aucune protection CI contre la regression « une base vierge demarre ».** Les tests
  d'integration par service utilisent chacun leur base Testcontainers isolee : ni le schema
  partage `jurika_db`, ni l'ordre de demarrage inter-services ne sont couverts. Le premier
  demarrage sur base vierge (auth cree le schema racine, les autres migrent apres, buckets et
  seeds crees) n'est verifie par rien aujourd'hui. A prevoir : un controle CI de premier
  demarrage (up complet sur volumes neufs + attente 18/18 sain), independant de ce lot.

## Documents de reference V2

- `docs/refonte-v2/CONTEXTE_PROJET_JURIKA.md` — contexte complet + methode (source de ce fichier).
- `CAHIER_DES_CHARGES_JURIKA_V2.md` — regles de gestion completes.
- `GUIDE_REFONTE_JURIKA_V2.md` — ordre des lots.
