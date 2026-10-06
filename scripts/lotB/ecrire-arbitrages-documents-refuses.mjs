/**
 * Ecrit, pour le cabinet, la liste des documents que la plateforme REFUSE de
 * produire — document, variable qui bloque, phrase ou elle apparait — et la
 * formule en DEMANDE D'ARBITRAGE.
 *
 *   node scripts/lotB/ecrire-arbitrages-documents-refuses.mjs
 *
 * Les phrases sont reprises VERBATIM du rapport du parcours temoin
 * (`backend-java/ai-service/target/output/lotB/temoin/refus-SARL.md`, ecrit par
 * ParcoursCreationTemoinTest), lui-meme ecrit a partir du texte
 * reellement extrait des `.docx` produits. Aucune n'est retapee : une citation
 * approximative dans un document d'arbitrage ferait trancher sur autre chose que
 * ce que le gabarit ecrit.
 *
 * La classification de chaque variable (sans source / derivable / champ deja
 * ouvert) est lue dans `creation-champs.json`, la meme source que le parcours.
 */
import fs from 'node:fs';
import path from 'node:path';

const RACINE = path.resolve(process.argv[2] ?? '.');
const CATALOGUE = path.join(RACINE,
  'backend-java/ai-service/src/main/resources/templates/v2/creation-champs.json');
const REFUS = path.join(RACINE, 'backend-java/ai-service/target/output/lotB/temoin/refus-SARL.md');
const SORTIE = path.join(RACINE, 'output/lotB/arbitrages-documents-refuses.md');

const cat = JSON.parse(fs.readFileSync(CATALOGUE, 'utf8'));
const SANS = new Map(cat.sans_source.map((x) => [x.variable, x]));
const DER = new Set(cat.derivables.map((x) => x.variable));
const CHAMPS = new Map(cat.champs.map((x) => [x.variable, x]));
const DOCS = new Map(cat.documents.map((d) => [d.code, d]));

/** Les trois reponses possibles, telles qu'elles seront posees au cabinet. */
const A_SAISIR = 'à saisir';
const DERIVABLE = 'dérivable';
const VIDE = 'rubrique vide';

/**
 * Ce que chaque document attend du cabinet. Redige a la main : le classement
 * mecanique dit d'ou vient le blocage, il ne dit pas ce qu'il faut en faire.
 */
const COMMENTAIRE = {
  CONTRAT_BAIL: {
    groupe: 'A',
    titre: 'Contrat de bail commercial',
    quoi: "Le bail est un contrat entre la société et **un tiers** : le bailleur. "
      + "Huit de ses mentions — identité, forme, capital, siège, registre du commerce, "
      + "représentant — décrivent ce tiers, et rien dans le dossier ne les porte. Quatre "
      + "autres sont des clauses que vous négociez : destination des lieux, modalités de "
      + "paiement du loyer, tribunal compétent, charge des frais d'enregistrement.",
    proposition: "Ouvrir un bloc « Bailleur » au parcours, renseigné au moment où le bail "
      + "est négocié. C'est une dizaine de champs, saisis une fois, et qui servent aussi "
      + "à la légalisation et au dépôt.",
  },
  CONTRAT_DOMICILIATION: {
    groupe: 'A',
    titre: 'Contrat de domiciliation',
    quoi: "Même situation que le bail, avec un tiers différent : le domiciliataire. Neuf "
      + "mentions le décrivent — l'ICE en plus, la loi 89-17 l'exigeant. Sept autres sont "
      + "les conditions du contrat : durée, date d'effet, redevance, périodicité, "
      + "modalités, tribunal, frais.",
    proposition: "Si le cabinet travaille avec **un nombre restreint de domiciliataires**, "
      + "ces neuf mentions se saisissent une fois pour toutes et se choisissent ensuite "
      + "dans une liste. Dites-nous si c'est le cas : cela change la forme de l'écran, "
      + "pas la décision.",
  },
  LETTRE_RETRAIT_DEPOT: {
    groupe: 'A',
    quoi: "La lettre demande au greffe la restitution des pièces d'un dossier abandonné. "
      + "Elle cite **la référence et la date du dépôt** — deux données que l'administration "
      + "a délivrées au moment du dépôt, et que le dossier ne conserve pas aujourd'hui.",
    proposition: "Le parcours fait déjà cocher « dépôt » et « retrait » sur onze lignes, "
      + "avec un récépissé téléversé. Si vous voulez que cette lettre se produise seule, il "
      + "faut **saisir le numéro du récépissé au moment du cochage** — un champ sur la ligne "
      + "de dépôt, pas un écran de plus.",
  },
  RAPPORT_COMMISSAIRE_APPORTS: {
    groupe: 'A',
    quoi: "Le rapport est l'œuvre **du commissaire aux apports**, pas du cabinet. Trois "
      + "mentions en dépendent : la date de l'ordonnance qui le désigne, le lieu et la date "
      + "de son rapport.",
    proposition: "Notre lecture : ce document n'a pas à être produit par la plateforme. Le "
      + "commissaire remet son rapport, le cabinet le téléverse comme justificatif. Le "
      + "gabarit resterait disponible comme **modèle de travail**, sans génération "
      + "automatique. Confirmez-nous ce retrait, ou dites quelles données vous relevez.",
  },
  ETAT_ACTES_SOCIETE_EN_FORMATION: {
    groupe: 'B',
    quoi: "Un seul blocage, et c'est **une somme** : le total des engagements repris. La "
      + "liste des actes est déjà saisie, ligne par ligne, avec son montant.",
    proposition: "Aucune décision attendue de vous : c'est un calcul, à faire côté "
      + "plateforme. Il reste à écrire.",
  },
  BORDEREAU_REMISE_DOSSIER: {
    groupe: 'B',
    quoi: "Le bordereau énumère les pièces remises. La désignation de chaque pièce est "
      + "saisie ; elle n'était pas republiée dans la boucle au moment du témoin.",
    proposition: "Aucune décision attendue de vous : correction technique.",
  },
  NOTE_ANNULATION_DOSSIER: {
    groupe: 'B',
    quoi: "Deux mentions : la date à laquelle le dossier est clos sans suite, et le statut "
      + "atteint au moment de l'abandon. La plateforme connaît les deux — c'est la date du "
      + "geste d'annulation et l'état du parcours.",
    proposition: "Aucune décision attendue de vous : c'est une dérivation, à écrire.",
  },
  FICHE_RENSEIGNEMENTS_CREATION: {
    groupe: 'C',
    quoi: "Les trois dénominations proposées à l'OMPIC, la date de début d'activité "
      + "envisagée et le numéro du titre de propriété. **Toutes ont un champ** au parcours.",
    proposition: null,
  },
  POUVOIR_FORMALITES_CREATION: {
    groupe: 'C',
    quoi: "Les six mentions décrivent le **mandataire** — le cabinet lui-même, le plus "
      + "souvent. Elles ont un champ.",
    proposition: "Une remarque tout de même : ce sont vos propres coordonnées, identiques "
      + "d'un dossier à l'autre. Si vous le souhaitez, elles peuvent être **préremplies "
      + "depuis la fiche du cabinet** plutôt que ressaisies. Dites-le-nous.",
  },
  DECLARATION_BENEFICIAIRES_EFFECTIFS: {
    groupe: 'C',
    quoi: "Civilité, pièce d'identité et pourcentage de droits de vote de chaque "
      + "bénéficiaire, plus l'identité du déclarant. Tous ont un champ, dans la boucle "
      + "« Bénéficiaires effectifs ».",
    proposition: null,
  },
  DEMANDE_DEBLOCAGE_CAPITAL: {
    groupe: 'C',
    quoi: "L'agence bancaire et trois dates : souscription, immatriculation, attestation "
      + "de blocage. Toutes ont un champ.",
    proposition: "La date d'immatriculation, elle, **sera connue du dossier** dès que le "
      + "numéro RC y est porté. Nous pouvons la reprendre automatiquement — dites-nous si "
      + "vous préférez la voir affichée plutôt que saisie.",
  },
  DECLARATION_CNDP: {
    groupe: 'C',
    quoi: "Le nom et la qualité du représentant légal, et le point de contact pour "
      + "l'exercice des droits. Tous ont un champ.",
    proposition: null,
  },
  DEMANDE_ADHESION_SIMPL: {
    groupe: 'C',
    quoi: "Le type et le numéro de la pièce d'identité du contact déclaré. Deux champs.",
    proposition: null,
  },
};

/**
 * La ligne du parcours est LUE au catalogue, jamais recopiee ici : une ligne
 * fausse dans un document d'arbitrage ferait chercher au cabinet une etape qui
 * n'existe pas.
 */
const ligneDe = (code) => (DOCS.get(code) ?? {}).ligneGeneration ?? null;

// --------------------------------------------------------------------------
//  Lecture des refus, verbatim
// --------------------------------------------------------------------------
const blocs = fs.readFileSync(REFUS, 'utf8').split(/^## /m).slice(1);
const refus = [];
for (const b of blocs) {
  const code = b.split('\n')[0].trim();
  const corps = b.slice(b.indexOf('phrase.') + 7).trim();
  const variables = [];
  for (const p of corps.split(' ; ')) {
    const m = p.match(/\$([A-Z0-9_]+)\s+—\s+«\s*([\s\S]*?)\s*»/);
    if (!m) throw new Error('Refus illisible : ' + p.slice(0, 80));
    const v = m[1];
    variables.push({
      variable: v,
      phrase: m[2].replace(/\s+/g, ' ').trim(),
      categorie: SANS.has(v) ? 'SANS_SOURCE' : DER.has(v) ? 'DERIVABLE'
        : CHAMPS.has(v) ? 'CHAMP_OUVERT' : 'INCONNUE',
      label: (CHAMPS.get(v) ?? {}).label ?? null,
    });
  }
  refus.push({ code, variables });
}

const reponseProposee = (c) => (c === 'SANS_SOURCE' ? A_SAISIR
  : c === 'DERIVABLE' ? DERIVABLE : A_SAISIR);

const GROUPES = {
  A: {
    titre: 'A — Ce qui appelle vraiment votre décision : une donnée que personne ne détient',
    intro: "Ces quatre documents citent une donnée qui n'est **ni dans le dossier, ni "
      + "productible par un écran** : elle appartient à un tiers — un bailleur, un "
      + "domiciliataire, un commissaire aux apports — ou à une administration. Tant que la "
      + "question n'est pas tranchée, ils ne se génèrent pas.\n\n"
      + "Pour chacun, trois réponses sont possibles :\n\n"
      + "- **donnée à saisir** — nous ouvrons le champ ; dites qui la relève et à quel moment ;\n"
      + "- **donnée dérivable** — elle existe ailleurs dans le dossier sous une autre forme, "
      + "et nous la calculons ;\n"
      + "- **rubrique laissée vide** — la mention ne doit pas figurer ; le contrôle cessera "
      + "de bloquer sur elle.",
  },
  B: {
    titre: 'B — Ce qui ne vous demande rien : un calcul qui reste à écrire',
    intro: "Ces trois documents butent sur une valeur que la plateforme **peut** produire à "
      + "partir de ce qui est déjà saisi. Aucune décision n'est attendue de vous ; ils "
      + "figurent ici pour que la liste soit complète et que rien ne reste sans explication.",
  },
  C: {
    titre: 'C — Ce qui n’est pas un blocage : un champ existe, il n’était pas rempli',
    intro: "Ces six documents ont été refusés au parcours témoin parce que le dossier d'essai "
      + "**ne renseignait volontairement qu'une vingtaine des champs du parcours** — c'était "
      + "l'objet du témoin : montrer ce qu'un dossier incomplet produit. Chacune des "
      + "variables citées ci-dessous a bien un champ à l'écran, et se résout dès qu'il est "
      + "rempli.\n\n"
      + "Nous le disons comme nous l'avons établi : c'est une conséquence du catalogue des "
      + "champs, **et non une génération réussie que nous aurions constatée** — le dossier "
      + "témoin vise précisément le cas inverse.\n\n"
      + "Ils sont listés pour une seule raison : si l'une de ces mentions ne vous paraît "
      + "**pas devoir être demandée**, dites-le, et la rubrique sera laissée vide plutôt que "
      + "d'attendre une saisie qui ne viendra jamais.",
  },
};

const parGroupe = { A: [], B: [], C: [] };
for (const r of refus) {
  const c = COMMENTAIRE[r.code];
  if (!c) throw new Error('Document sans commentaire : ' + r.code);
  parGroupe[c.groupe].push(r);
}

// --------------------------------------------------------------------------
//  Redaction
// --------------------------------------------------------------------------
const L = [];
const total = refus.reduce((n, r) => n + r.variables.length, 0);

L.push('# Documents non produits — demande d’arbitrage');
L.push('');
L.push('> Maghreb Consulting — parcours CRÉATION du 9 septembre.');
L.push('> Établi le ' + new Date().toISOString().slice(0, 10)
  + ' à partir du parcours témoin, SARL puis SARL AU.');
L.push('> Document **distinct du rapport de lot** : il n’attend pas une lecture, mais des réponses.');
L.push('');
L.push('## Ce que vous lisez ici');
L.push('');
L.push('Le corpus du 9 septembre compte vingt-trois modèles. Un parcours complet a été mené '
  + 'de bout en bout, deux fois — SARL puis SARL AU — et **les documents produits ont été '
  + 'ouverts et lus**. Sur les vingt-deux modèles que chaque forme met en jeu, **neuf sont '
  + 'sortis, treize ont été refusés**.');
L.push('');
L.push('Un refus n’est pas une panne. La plateforme refuse de produire un acte qui sortirait '
  + '**avec un blanc au milieu d’une phrase** : « Les frais d’enregistrement du présent bail '
  + 'et les droits de timbre sont à la charge de . » Un tel acte est signable, et faux. Le '
  + 'contrôle distingue ce blanc-là de la case vide d’un formulaire administratif, qui, elle, '
  + 'reste recevable.');
L.push('');
L.push('Les treize refus portent sur **' + total + ' variables**. Elles ne sont pas de même '
  + 'nature, et les mélanger vous ferait arbitrer sur des questions qui n’en sont pas :');
L.push('');
const mentions = (g) => parGroupe[g].reduce((n, r) => n + r.variables.length, 0);
const sansSourceDe = (g) => parGroupe[g].reduce(
  (n, r) => n + r.variables.filter((v) => v.categorie === 'SANS_SOURCE').length, 0);

L.push('| | Documents | Mentions bloquantes | dont sans source | Ce qu’il faut en faire |');
L.push('|---|---:|---:|---:|---|');
L.push('| **A** — au moins une donnée d’un tiers | ' + parGroupe.A.length + ' | '
  + mentions('A') + ' | **' + sansSourceDe('A') + '** | **votre décision** |');
L.push('| **B** — uniquement des valeurs dérivables | ' + parGroupe.B.length + ' | '
  + mentions('B') + ' | — | travail plateforme |');
L.push('| **C** — uniquement des champs déjà ouverts | ' + parGroupe.C.length + ' | '
  + mentions('C') + ' | — | rien, sauf avis contraire |');
L.push('| | **' + refus.length + '** | **' + total + '** | **' + sansSourceDe('A') + '** | |');
L.push('');
L.push('**Autrement dit : quatre documents attendent une décision de votre part**, et non treize.');
L.push('');

for (const g of ['A', 'B', 'C']) {
  L.push('---');
  L.push('');
  L.push('## ' + GROUPES[g].titre);
  L.push('');
  L.push(GROUPES[g].intro);
  L.push('');
  for (const r of parGroupe[g]) {
    const d = DOCS.get(r.code) ?? {};
    const ligne = ligneDe(r.code);
    L.push('### ' + (COMMENTAIRE[r.code].titre ?? d.libelle ?? r.code)
      + (ligne ? ' — ligne ' + ligne + ' du parcours'
        : ' — aucune ligne du parcours ne le produit'));
    L.push('');
    L.push('`' + r.code + '` — **' + r.variables.length + ' mention'
      + (r.variables.length > 1 ? 's bloquantes' : ' bloquante') + '**');
    L.push('');
    L.push(COMMENTAIRE[r.code].quoi);
    L.push('');
    L.push('| Variable | La phrase où elle apparaît, telle qu’elle sortirait | Réponse |');
    L.push('|---|---|---|');
    for (const v of r.variables) {
      const reponse = v.categorie === 'SANS_SOURCE'
        ? '☐ ' + A_SAISIR + ' ☐ ' + DERIVABLE + ' ☐ ' + VIDE
        : v.categorie === 'DERIVABLE' ? '_dérivable — rien à décider_'
          : '_champ ouvert : « ' + (v.label ?? v.variable) + ' »_';
      L.push('| `$' + v.variable + '` | « ' + v.phrase.replace(/\|/g, '\\|') + ' » | '
        + reponse + ' |');
    }
    L.push('');
    if (COMMENTAIRE[r.code].proposition) {
      L.push('**Ce que nous proposons.** ' + COMMENTAIRE[r.code].proposition);
      L.push('');
    }
  }
}

L.push('---');
L.push('');
L.push('## Comment répondre');
L.push('');
L.push('Une croix dans la colonne de droite des tableaux du groupe **A** suffit. Une réponse '
  + 'par ligne, ou une réponse par bloc si elle vaut pour tout le bloc — « tout le bailleur '
  + 'est à saisir » est une réponse complète.');
L.push('');
L.push('Trois précisions utiles, si vous choisissez **à saisir** :');
L.push('');
L.push('1. **qui** relève la donnée — le client, l’employé, ou une pièce téléversée ;');
L.push('2. **quand** — à l’ouverture du dossier, ou au moment où le document se produit ;');
L.push('3. si elle **se répète d’un dossier à l’autre** — auquel cas elle se saisit une fois '
  + 'et se choisit ensuite dans une liste.');
L.push('');
L.push('Si vous choisissez **rubrique laissée vide**, la mention disparaîtra de la phrase '
  + 'rendue — pas seulement du contrôle. Nous vous soumettrons la phrase réécrite avant de '
  + 'toucher au gabarit : **le contenu de vos modèles ne sera pas modifié sans votre accord.**');
L.push('');
L.push('Les cinquante-cinq variables sans source du corpus complet — dont les vingt-deux '
  + 'ci-dessus — sont listées dans `output/lotB/variables-sans-source.md`. Le détail des '
  + 'refus, document par document, est dans le relevé témoin des refus, produit par les '
  + 'tests (formes SARL et SARL AU) : les deux listes sont identiques.');
L.push('');

fs.writeFileSync(SORTIE, L.join('\n'), 'utf8');
console.log('Ecrit : ' + SORTIE);
console.log(refus.length + ' documents, ' + total + ' variables — A:' + parGroupe.A.length
  + ' B:' + parGroupe.B.length + ' C:' + parGroupe.C.length);
