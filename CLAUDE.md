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
  la branche du lot sur le Z440 : sauvegarde controlee d'abord, services applicatifs arretes,
  par `scripts/sauvegarde-lot.sh <dossier>` (`pg_dump -Fc` de toutes les bases,
  `pg_dumpall --globals-only`, verifies par `pg_restore --list` ; volume MinIO par
  `scripts/sauvegarde-volume-par-exec.py` ; documents MinIO reels, hors `.minio.sys`, rapproches
  des documents en base par `scripts/controle-sauvegarde-minio.py`), reconstruction et recreation, etat sain, test de
  fumee ; resultat dans le rapport final. Echec : retour arriere du code (depuis `main`), jamais
  de restauration de donnees automatique. `/lot-activer <lot>` reste disponible (pas a pas).
- Les commandes `/lot-demarrer`, `/lot-executer`, `/lot-verifier` et `/lot-cloturer` (mode
  pas a pas, avec points d'arret) restent disponibles.

## Aide integree (CDC 15.4, regle de tous les lots, 2026-10-10)

- Chaque ecran **nouveau ou modifie** recoit ses aides contextuelles (infobulle ou texte d'aide)
  qui disent ce qu'il faut faire, ce qui manque et ce qui vient ensuite, dans un francais
  correct (accents, ponctuation, termes du metier du cabinet). Une aide deja vue peut etre
  masquee puis reaffichee.
- Composants : `frontend-react/src/components/ui/Aide.tsx` (`InfoBulle`, `TexteAide`).
- Le rapport final de chaque lot liste les aides ajoutees (ecran, emplacement, texte).

## Regle des variables d'un acte (2026-10-09 ; appliquee au lot L3)

- Chaque variable du dictionnaire est classee **interne** ou **externe**. Externe : donnee
  attendue d'un organisme (numero RC, ICE, IF, date d'immatriculation, ou toute donnee produite
  par une administration ou un tiers). Interne : tout le reste (donnees du client, du dossier,
  des decisions).
- Une variable **interne** manquante **bloque** la generation, et la donnee manquante est
  **nommee** a l'utilisateur.
- Seule une variable **externe** peut manquer : l'acte sort avec un **marqueur visible**, la
  plateforme **reclame** la donnee, puis l'acte se **regenere** quand elle arrive.
- Mise en oeuvre (lot L3) : liste explicite des variables externes
  `ai-service/.../templates/v2/variables-externes.txt` (regles dans
  `scripts/l3/variables_externes.py`, liste classee : `~/docs/rapports/L3_variables_externes.md`),
  controlee au demarrage contre le dictionnaire. Decision du 2026-10-10 (D2) : seules les donnees
  de la SOCIETE TRAITEE attendues d'un organisme sont externes (RC, ICE, IF, TP, CNSS, dates et
  numeros d'immatriculation, de depot, de recepisse) ; les identifiants des tiers (bailleur,
  domiciliataire, associe personne morale...) sont internes ;
  refus structure (422, donnees nommees) a tous les parcours ; marqueur « A OBTENIR » ;
  reclamation (`donnees_attendues`, workflow V16) et regeneration a l'arrivee. Les 17 gabarits D3
  de L2 suivent la regle (tableau : `~/docs/rapports/L3_rapport_final.md`).

## Corpus documentaires (source de verite metier)

- Corpus de reference courant : `~/corpus/CORPUS_2026-10-11` (lire son `00_LISEZ_MOI.md` et la note
  de livraison du 2026-10-11 : seules les 16 decisions d'AGO de la matrice Modifications SARL
  changent par rapport a `CORPUS_2026-10-03`, preuve par empreintes).
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
- **4 tests ai-service ignores faute de LibreOffice sur le serveur Z440** :
  `DocxToPdfConverterTest.real_conversion_produces_valid_pdf_when_libreoffice_installed`
  (`@EnabledIf("hasLibreOffice")`) et les 3 tests de `HtmlToPdfConverterTest` (assumption
  « LibreOffice absent »). La conversion PDF n'est donc jamais verifiee par `verify -Pit` ici.

### Lot L2 (chargeur du corpus, 2026-10-09) -- dette relevee

- **Ecarts des modeles du cabinet** (L4a, releve complet L4d) : detail par fichier, emplacement, texte
  imprime et correction proposee dans `~/docs/rapports/ECARTS_MODELES_CABINET.md` : `$ASSOCIE_UNIQUE`
  (drapeau "oui" / "non") imprime comme identite dans 19 modeles SARL AU ; `$BAILLEUR_NOM` seul dans
  `FICHE_RENSEIGNEMENTS_CREATION` (vide pour un bailleur societe : fiche refusee, donnee nommee) ;
  mandataire de `LETTRE_RETRAIT_DEPOT` (creation) ; "-- (procedure interne)" n 47-48 du parcours de
  creation ; regle "PV multi-resolutions" (modeles d'AGE cites pour toute assemblee) ; variante
  `PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU` citee par le corpus mais absente (servie par le classpath).
  Gabarits intouchables : A_DECIDER cabinet (correction des modeles, nouvelle version datee du corpus).
- **Bascule des gabarits du classpath sur le corpus (L4d E11, faite ; rapport du liquidateur en
  L5 E4)** : reste au classpath `PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU` (ecart du cabinet,
  A_DECIDER directeur).

### Lot L5b (corrections, 2026-10-13) -- dette relevee

- **Titres des PDF de synthese en Times non embarque** (police standard 14, choisie pour une couche
  texte exacte) : le rendu PDFBox du serveur la remplace par une police aux chasses differentes
  (espacement irregulier sur les apercus PNG). A verifier dans un vrai lecteur et a l'impression ;
  sinon embarquer une serif sans ligatures.
- **Conversations du chatbot : conservation** (decision du 2026-10-13) : gardees tant que le compte
  existe, puis elles suivent le sort des donnees du cabinet. A faire en L7 / L8 : inclure
  `chatbot_conversations` et `chatbot_echanges` (ai, colonne `workspace_id`) dans l'archivage a
  l'arret du forfait et dans la suppression des donnees d'un cabinet sur sa demande.
- **`chatbotService.ask` (front)** n'est plus appele par aucun ecran (garde pour
  `scripts/rag-quality-check.mjs`).
- **Commentaire de dataroom V36** parle encore de Word (migration appliquee, intouchable).
- **`~/.m2` du Z440** : un `mvn install` de jurika-common (SNAPSHOT) y a ete pose pendant le lot ;
  un build sans `-am` pourrait le resoudre au lieu du reacteur. Les verifications du depot passent
  toutes par le reacteur complet.

### Lot L5 (fonctions metier, 2026-10-12) -- dette relevee

- **RLS inoperante tant que la bascule `jurika_app` n'est pas faite** : ai V16 (corpus commun du
  chatbot) et le realtime-service (`jurika_user`) ; seul le filtrage applicatif protege.
- **Realtime** : `/emit` sans authentification ; `HttpNotificationPublisher.publish` n'envoie pas
  le jeton interne.
- **Super-admin non alerte a 80 % du quota** (RG-PLN-02) : RG-COM-08 interdit de notifier hors du
  cabinet, il faut un canal propre au super-admin. `PlanLimitsService.countDossiers` compte tous les
  dossiers, pas ceux du mois (RG-PLN-01) ; l'alerte de quota en herite.
- **Fonctions par forfait non gardees** : alertes de delais et messagerie interne (absentes
  d'Essentiel) ; quota de 20 questions au chatbot (CDC 4) non mis en oeuvre (L7).
- **Trace des identifiants du dossier modifiable** par `jurika_app` (GRANT global de workflow V14,
  joue apres ticket V41) : REVOKE pose en dernier, ou declencheur de refus.
- **CHECK `document_type` (dataroom)** : toute migration qui le redefinit doit reprendre
  `FICHE_CLIENT` et `FICHE_DOSSIER` (dataroom V36).
- **`ZipHorodatage`** : la version de jurika-common rend l'archive inchangee si elle est illisible
  (motif 9) ; la copie de dataroom (`infrastructure/word`) leve une erreur. Unifier sur le
  comportement strict.
- **PV de cloture de liquidation** : date de decision de dissolution prise sur la date de
  dissolution en base (date d'effet) ; impot absent compte 0. `LIQUIDATEUR_CAPITAL_CHIFFRES` non mis
  en forme dans `DissolutionAnnonceVarsBuilder` ("500000").
- **PV_AG_SARL** : `dividendeParPart` et `acompteParPart` saisis alors que calculables. Catalogue :
  `au-ord-distribution` ("dividendes ou reserves") route vers les seuls dividendes ;
  `OfficialDecision.note` jamais affiche (motif 9). Exoneration de retenue : associe unique personne
  morale sans ecran d'identite.
- **Ecrans** : `CabinetTab.tsx` annonce des "PDF generes (fiche client, etat des debours)" ;
  `ProfileSecurityPage` "Changer mon mot de passe" mene a la page de changement obligatoire ;
  `Sidebar.tsx` et `AppShellSidebar.tsx` ne sont plus montes (code mort) ; `useLenisScroll` exclut
  encore `/chatbot` ; `scripts/demo-video/capture-demo.mjs` vise l'ancienne page du chatbot.
- **Auth** : `InviteEmployeUseCase` cherche les doublons sur l'identifiant et non sur
  `contact_email` ; gabarits de courriel existants (password-changed, verify-email...) et leurs
  sujets sans accents ; libelles d'audit `DEBOURS_*` absents de `AuditActionLabels`.
- **Debours** : lignes anterieures avec `piece_jointe_url` affichees sans lien Data Room ;
  dependance `io.minio` inutilisee dans le pom de ticket-service.
- **Fiche** : un auteur d'un autre workspace s'affiche "Compte inconnu".
- **Lint herite** (`react-hooks/set-state-in-effect` et autres) : LiquidationWorkflowPage,
  SeanceOperationForm, modificationResolutions (react-refresh), DossierJuridiqueTab,
  TicketDetailDrawer, ClientDataroomView, ChatPage, NotificationBell, DemandesTab,
  RequetesAuClientSection, AuditLogPage.
- **Tests vitest sensibles a la charge** (delais de 5 s) : step7-documents-conditionnels,
  liquidation-annonce-corpus, LoginPage et d'autres sous charge machine elevee.

### Lot L4d (page de ticket, bascule du corpus, 2026-10-12) -- dette relevee

- **PV de modification non rediges par le modele du corpus** (refus nomme ; aucune donnee ne
  manque, c'est la redaction du modele : A_DECIDER cabinet, nouvelle version datee du corpus) :
  incorporation de reserves et elevation du nominal (PV_AG_SARL et le PV de l'associe unique
  impriment sans condition la souscription de "parts nouvelles") ; article 86 en SARL
  (PV_AG_SARL imprime une note de redaction renvoyant a PV_DISSOLUTION_LIQUIDATION_SARL ; la
  matrice cite `regularisation_capitaux_propres`, le modele `capitaux_propres_art86`) ; acompte
  sur dividendes en SARL AU (aucune branche ; le modele declare qu'aucun acompte n'est organise
  par la reglementation marocaine, quand PV_AG_SARL en redige un). Deux resolutions d'un meme type
  de liste dans un PV : refus (le moteur developpe les boucles imbriquees au niveau global).
  Affectation du resultat et distributions en SARL AU : faites (L5 E5). Detail : rapports finaux
  L4d et L5.
- **Assemblee ordinaire hors approbation des comptes** : la convocation et la feuille AGO du corpus
  sont celles de l'assemblee annuelle ; les parcours succursale, fermeture et modification qui
  tiennent une AGO hors comptes recoivent ces modeles (donnees des comptes demandees). A_DECIDER
  cabinet (modele d'AGO generique) ; cloture de liquidation : `CONVOCATION_AG_CLOTURE_LIQUIDATION_SARL`
  non branche.
- **Associe personne morale sur la feuille de presence** (forme, siege, RC, representant) : sans
  ecran, generation refusee avec les donnees nommees.
- **`SeancePvVarsBuilder`** : `LIEU_SIGNATURE` replie sur la ville du greffe (supposition),
  `CONVOCATION_RANG` "premiere" et `SECRETAIRE_PRESENT` "non" par defaut, "represente" sans accord.
- **`DocumentTypes.ALL`** liste encore `RAPPORT_GESTION` : les endpoints generiques
  `/ai/documents/generate/RAPPORT_GESTION` repondent 404 (aucun ecran ne les appelle).
- **Brouillon** : la page de dissolution ne conserve pas l'instantane de seance du formulaire de PV
  apres rechargement ; l'execution de l'etape 2 du PV AGO remplace `data.step2`.
- **Note interne `NOTE_DEFAUT_DECISION_ASSOCIE_UNIQUE_SARL_AU`** du corpus non branchee (constat de
  defaut de decision de l'associe unique).

### Lot L6 (qualite de la langue, ergonomie) -- a prevoir

- **Passe complete d'ergonomie et d'aide integree** (CDC 15.4) sur tous les ecrans existants,
  en particulier a chaque statut du ticket : ce qu'il faut faire, ce qui manque, ce qui vient
  ensuite.
- **Visite guidee** a la premiere connexion (masquable, reaffichable).
- **Nouveau logo**, avec sa variante ivoire en mode sombre.
- **Super-admin affiche "JURIKA"** (et non un nom de cabinet ou de compte).

### Lot L4b (demarches du corpus, D5, 2026-10-11) -- dette relevee

- **Type du tribunal de la creation** : ville vide => "Tribunal de premiere instance"
  (`CreationFormulairesVarsBuilder`) ; a aligner sur `TribunalDerive` (rien deduit sans ville).
- **Justificatifs du corpus en texte seul** : les lignes du referentiel du corpus n'ont pas de type
  de document attendu ; leur cochage ne depose aucune piece (RG-DEM-04 partiel).
- **Conditions des demarches du corpus** lues sur le magasin du ticket, que seules la creation et
  l'annulation alimentent : hors creation, la plupart des lignes conditionnelles restent "a apprecier"
  jusqu'a la page generique (L4c).

### Lot L4a (controles, annulation, 2026-10-11) -- dette relevee

- **Lettre de retrait : ecart mecanique du corpus** : `LETTRE_RETRAIT_DEPOT` (creation) imprime
  `$POUVOIR_MANDATAIRE_NOM` seul sous la signature, la variante `_SOCIETE_EXISTANTE` civilite,
  prenom et nom. A rapporter au cabinet (gabarits intouchables).
- **Documents d'annulation encore au catalogue de la creation** : `CreationSarlMapper` sert
  toujours `NOTE_ANNULATION_DOSSIER` et `LETTRE_RETRAIT_DEPOT` (parcours `CREATION_SARL`, sans les
  faits du ticket) ; l'ecran de la creation ne les propose pas, le parcours `ANNULATION` les
  produit. A retirer du catalogue quand les temoins de rendu de la creation seront revus.

### Lot L3 (saisie unique, 2026-10-10) -- dette relevee

- **Champ sans ecran** lu par le projecteur : ville du RC d'un associe personne morale
  (`ASSOCIE_RC_VILLE`).
- **Clauses libres** : les PV de dissolution et de cloture du corpus (`PV_DISSOLUTION_ANTICIPEE_*`,
  `PV_CLOTURE_LIQUIDATION_*`) n'ont pas de boucle `RESOLUTIONS` : pas d'emplacement (la generation
  refuse une clause). PV de modification et statuts : faits en L4d (E10, E11c).

### Lot L1 (dossiers et employes, 2026-10-09) -- dette relevee

- **Lien d'acces par jeton jamais lu** (dataroom) : `client_link_token`, `POST .../settings/regenerate-link`,
  `GET .../settings/client-link` et `DataroomSettingsService#findByToken` ne sont appeles par
  aucune route ni aucun ecran (controle silencieusement inoperant, motif 9). En outre
  `GET .../settings` et `GET .../settings/client-link` rendent le jeton a tout employe, sans
  controle du responsable. A retirer, ou a concevoir si un acces par lien est voulu (absent du
  CDC). L'ecran "Acces du client" affiche l'adresse de l'espace client, pas ce jeton (D21).

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
- **Dependance circulaire des migrations ticket V21 / dataroom** : ticket V21 reference
  `dataroom_documents`, que dataroom V5 cree en referencant `tickets`. Les tests passent par
  l'ordre auth, ticket jusqu'a V20, dataroom, fin de ticket. A demeler au lot des parcours.
- **`JwtAuthFilter`** : si la suite de la chaine leve une exception dans le `try`, le
  `catch (Exception)` relance `chain.doFilter` une seconde fois (double execution).
- **`AuditLogJpaRepository` (auth)** inutilise depuis E10b : code mort a retirer (test d'abord).
- **Objets MinIO orphelins** quand l'ecriture en base echoue, et suppression de l'ancien objet
  avant validation (dataroom).
- **Front : boutons d'action encore visibles au superviseur** alors que le serveur les refuse
  (E5, E5b, E6) ; 46 fichiers du front citent `SUPERVISEUR`.
- **Export ZIP du Dossier Juridique** : echec complet (`duplicate entry`) quand deux documents
  d'un meme type portent le meme nom de fichier (`exportSelectionAsZip`).
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
