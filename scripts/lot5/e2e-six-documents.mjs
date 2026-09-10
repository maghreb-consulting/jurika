#!/usr/bin/env node
/**
 * ⚠ HORS SERVICE DEPUIS LE LOT A (2026-09-10) — CORPUS RETIRÉ.
 *
 * Les six documents visés ici (STATUTS_SARL_DIRECTEUR, ACTE_NOMINATION_GERANT_DIRECTEUR,
 * ANNONCE_LEGALE_DIRECTEUR et les trois imprimés du 4 septembre) ont été remplacés
 * par les 23 gabarits livrés le 9 septembre. Le workflow CREATION_SARL n'a plus de
 * mapper : l'endpoint appelé plus bas renverra une erreur. Le script est conservé
 * comme patron — c'est lui qui avait révélé 43 défauts sur une suite entièrement
 * verte — et doit être refait au lot B, sur les nouveaux codes.
 *
 * Lot 5 — LES SIX DOCUMENTS, DE BOUT EN BOUT, DANS L'APPLICATION QUI TOURNE.
 *
 * Le contrôle de complétude et la correction des sentinelles changent ce qui SORT
 * du moteur. Les tests le disent ; ce script le montre : on ouvre un vrai ticket,
 * on remplit les étapes 1 à 6 par l'API de l'application, puis on demande les six
 * documents au même endpoint que l'étape 7 du front, avec le même payload.
 *
 * Ce qui est vérifié sur les fichiers RÉELLEMENT produits :
 *   - les six documents sortent ;
 *   - aucun marqueur du moteur ne survit ($VAR, ▼▲, ◇◆, ◈, ↳) ;
 *   - les cases à cocher portent une croix ;
 *   - le contrôle de complétude REFUSE le modèle 2 sans lieu de naissance,
 *     et ACCEPTE le formulaire de TP sans direction régionale.
 *
 * Les .docx sont écrits dans .tmp/lot5-sortie/ pour être ouverts.
 *
 * Usage : node scripts/lot5/e2e-six-documents.mjs
 */
import { mkdir, writeFile, rm } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..', '..');
const SORTIE = join(ROOT, '.tmp', 'lot5-sortie');
const BASE = process.env.BASE || 'http://localhost:8080';

const WS = process.env.WS || 'JUR-SSKB3';
const EMAIL = process.env.EMAIL || 'employe-a-jursskb3@jurika.test';
const PWD = process.env.PWD_JURIKA || 'DemoPwd2026!';

const C = { ok: '\x1b[32m', ko: '\x1b[31m', dim: '\x1b[90m', gras: '\x1b[1m', z: '\x1b[0m' };
let echecs = 0;

function dire(statut, texte, detail = '') {
  const c = statut === 'OK' ? C.ok : statut === 'INFO' ? C.dim : C.ko;
  if (statut === 'KO') echecs++;
  console.log(`  ${c}[${statut}]${C.z} ${texte}${detail ? '  ' + C.dim + detail + C.z : ''}`);
}
function titre(t) { console.log(`\n${C.gras}${t}${C.z}`); }

async function http(method, path, { body = null, token = null, brut = false } = {}) {
  const headers = { Accept: brut ? '*/*' : 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload = null;
  if (body) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const res = await fetch(`${BASE}${path}`, { method, headers, body: payload });
  if (brut) {
    const buf = Buffer.from(await res.arrayBuffer());
    return { ok: res.ok, status: res.status, buf, headers: res.headers };
  }
  let parsed = null;
  try { parsed = await res.json(); } catch { /* corps vide */ }
  return { ok: res.ok, status: res.status, body: parsed, headers: res.headers };
}

// ---------------------------------------------------------------------
//  Le dossier : une SARL complète, telle que les étapes 1 à 6 la produisent
// ---------------------------------------------------------------------
const STAMP = Date.now();
const DENOM = `PARACOSME LOT5 ${STAMP}`;

const GERANT = {
  id: 'g1', typePersonne: 'PHYSIQUE', civilite: 'M', nom: 'BENANI', prenom: 'Yassine',
  cinNumero: 'BK123456', nationalite: 'Marocaine',
  adresse: '5 avenue Yacoub El Mansour, Casablanca',
  dateNaissance: '1980-01-20', lieuNaissance: 'Casablanca',
  fonction: 'GERANT', isStatutaire: false, isAssociate: true, cinUploaded: true,
  dureeMandatType: 'determinee', dureeAnnees: 3,
  remunerationMode: 'gratuit', remunerationMontant: 0,
};

/** Miroir de `dureeMandatLabel()` (étape 5), partagé par le front et ce script. */
function dureeMandatLabel(d) {
  return d.dureeMandatType === 'determinee' && Number(d.dureeAnnees) > 0
    ? `${d.dureeAnnees} année(s)`
    : "illimitée (jusqu'à révocation)";
}

const GERANCE = {
  dureeMandat: '3 année(s)', dureeGerance: '3 année(s)', remunerationMode: 'gratuit',
  gerantModeDesignation: 'non statutaire', limitationPouvoirs: '',
  modeSignature: 'séparée', signaturePlafond: 0, signatureMandataire: false,
  modeSignatureAdmin: 'identique',
};

const ASSOCIES = [
  { id: 'a1', typePersonne: 'PHYSIQUE', civilite: 'M', nom: 'BENANI', prenom: 'Yassine',
    cin: 'BK123456', adresse: '5 avenue Yacoub El Mansour, Casablanca',
    dateNaissance: '1980-01-20', lieuNaissance: 'Casablanca', nationalite: 'Marocaine',
    ifFiscal: '12345678', nombreParts: 700, montantApport: 70000,
    typeApport: 'NUMERAIRE', pourcentageDetention: 70, cinUploaded: true },
  { id: 'a2', typePersonne: 'PHYSIQUE', civilite: 'Mme', nom: 'IDRISSI', prenom: 'Salma',
    cin: 'BE654321', adresse: '12 rue des Écoles, Casablanca',
    dateNaissance: '1985-04-12', lieuNaissance: 'Rabat', nationalite: 'Marocaine',
    nombreParts: 300, montantApport: 30000,
    typeApport: 'NUMERAIRE', pourcentageDetention: 30, cinUploaded: true },
];

/** Les saisies propres aux formulaires (panneau « Compléments » de l'étape 7). */
const COMPLEMENTS = {
  directionRegionale: 'Direction régionale de Casablanca-Settat',
  subdivision: 'Subdivision Anfa',
  dateDebutActivite: '2026-03-01',
  telephone: '+212522334455',
  fax: '+212522334456',
  email: 'contact@paracosme.ma',
  regimeResultat: 'Résultat net réel',
  tvaAssujettissement: 'De plein droit',
  tvaFaitGenerateur: 'Encaissement',
  tvaPeriodicite: 'Trimestrielle',
  activiteNature: 'Permanente',
  associePrincipalVille: 'Casablanca',
  associePrincipalTel: '+212661223344',
  associePrincipalEmail: 'y.benani@paracosme.ma',
  enseigne: 'PARACOSME CONSEIL',
  piecesProduites: 'Statuts enregistrés ; certificat négatif ; contrat de bail enregistré ; CIN du gérant',
};

/** Miroir du `buildPayload` de Step7Generation.tsx. */
function payloadEtape7(complements = COMPLEMENTS, gerants = [GERANT]) {
  return {
    societe: {
      denomination: DENOM,
      formeJuridique: 'SARL',
      adresseSiege: '101 boulevard Zerktouni, Casablanca',
      capitalChiffres: 100000,
      // Miroir EXACT de `formatObjetSocial()` du front : dès deux activités, une
      // liste à tirets. Sans cela le script produirait un objet social collé
      // (« le conseil en ingénierie la formation professionnelle ») que
      // l'application, elle, ne produit pas — la vérification porterait alors sur
      // une sortie qui n'existe nulle part.
      objetSocial: '- le conseil en ingénierie\n- la formation professionnelle',
      activiteSociete: "le conseil en ingénierie\nla formation professionnelle",
      activites: ['le conseil en ingénierie', 'la formation professionnelle'],
      nombreParts: 1000, valeurPart: 100, dureeAnnees: 99,
      iceNumero: '001234567000045', ifNumero: '40123456',
      sigle: 'PRCM',
      certificatNegatifNumero: `CN-2026-${STAMP % 100000}`,
      certificatNegatifDate: '2026-01-15',
      rcVille: 'Casablanca', villeGreffe: 'Casablanca',
      ville: 'Casablanca',
      tribunalCompetent: 'Casablanca', villeSignature: 'Casablanca',
      dateConstitution: '2026-02-13', dateSignature: '2026-02-13',
      modeLiberation: 'intégrale', modeSignature: 'séparée',
      nationalite: 'marocaine',
      // 2026-09-08 — repli de la durée du mandat (miroir de buildPayload).
      dureeGerance: GERANCE.dureeGerance,
    },
    formulaires: complements,
    gerants: gerants.map((d) => ({
      civilite: d.civilite, prenom: d.prenom, nom: d.nom, cin: d.cinNumero,
      nationalite: 'marocaine', adresse: d.adresse,
      dateNaissance: d.dateNaissance, lieuNaissance: d.lieuNaissance,
      isStatutaire: d.isStatutaire, typePersonne: 'PHYSIQUE',
      // Le mandat PROPRE au dirigeant : c'est lui que le moteur scope par gérant.
      dureeMandat: d.dureeMandatType ? dureeMandatLabel(d) : GERANCE.dureeMandat,
    })),
    associes: ASSOCIES.map((a) => ({
      civilite: a.civilite, prenom: a.prenom, nom: a.nom, cin: a.cin,
      nationalite: 'marocaine', adresse: a.adresse,
      dateNaissance: a.dateNaissance, lieuNaissance: a.lieuNaissance,
      ifFiscal: a.ifFiscal, nombreParts: a.nombreParts,
      montantApport: a.montantApport, typeApport: 'NUMERAIRE', typePersonne: 'PHYSIQUE',
    })),
    depot: { dateSignature: '2026-02-13', capitalLibere: 100000 },
  };
}

// ---------------------------------------------------------------------
//  Lecture du .docx produit
// ---------------------------------------------------------------------
async function texteDuDocx(buf) {
  const { default: JSZip } = await import('jszip');
  const zip = await JSZip.loadAsync(buf);
  const xml = await zip.file('word/document.xml').async('string');
  return xml
    .replace(/<w:(tab|br)[^>]*\/>/g, ' ')
    .replace(/<\/w:p>/g, '\n')
    .replace(/<[^>]+>/g, '');
}

const MARQUEURS = [
  { nom: '$VARIABLE', re: /\$[A-Z][A-Z0-9_]{2,}/ },
  { nom: '▼ ou ▲ (boucle)', re: /[▼▲]/ },
  { nom: '◇ ou ◆ (condition)', re: /[◇◆]/ },
  { nom: '◈ (case à cocher)', re: /◈/ },
  { nom: '↳ (annotation)', re: /↳/ },
  { nom: 'VALEUR MANQUANTE', re: /VALEUR MANQUANTE/ },
];

async function main() {
  console.log(`${C.gras}Lot 5 — les six documents, dans l'application (${BASE})${C.z}`);
  await rm(SORTIE, { recursive: true, force: true });
  await mkdir(SORTIE, { recursive: true });

  // ---- 1. Connexion réelle -------------------------------------------
  titre('1. Connexion');
  const auth = await http('POST', '/api/v1/auth/login',
    { body: { workspaceCode: WS, email: EMAIL, password: PWD } });
  if (!auth.ok || !auth.body?.accessToken) {
    dire('KO', 'connexion', `${auth.status} ${JSON.stringify(auth.body)}`);
    return;
  }
  const token = auth.body.accessToken;
  dire('OK', `connecté ${EMAIL} sur ${WS}`);

  // ---- 2. Ticket + workflow ------------------------------------------
  titre('2. Ticket CRÉATION et workflow');
  const tk = await http('POST', '/api/v1/tickets', {
    token,
    body: {
      titre: DENOM, type: 'CREATION', priorite: 'NORMALE',
      description: 'Lot 5 — vérification des six documents',
      companyInfo: { raisonSociale: DENOM, formeJuridique: 'SARL' },
    },
  });
  if (tk.status !== 201) { dire('KO', 'création du ticket', `${tk.status} ${JSON.stringify(tk.body)}`); return; }
  const ticketId = tk.body.id;
  dire('OK', `ticket ${tk.body.reference ?? ticketId}`);

  const start = await http('POST', `/api/v1/workflows/${ticketId}/start`,
    { token, body: { type: 'CREATION' } });
  if (start.status !== 201) { dire('KO', 'démarrage du workflow', `${start.status}`); return; }
  dire('OK', 'workflow CRÉATION démarré');

  // ---- 3. Étapes 1 à 6 ------------------------------------------------
  titre('3. Étapes 1 à 6');
  const etapes = [
    [1, { denomination: DENOM, ice: '001234567000045', ifFiscal: '40123456',
          cnNumero: `CN-2026-${STAMP % 100000}`, cnDate: '2026-01-15',
          activiteCn: 'Conseil', beneficiaire: 'Yassine BENANI',
          formeJuridique: 'SARL', sigle: 'PRCM' }],
    [2, { adresse: '101 boulevard Zerktouni, Casablanca', province: 'Casablanca',
          commune: 'Casablanca', codePostal: '20000', villeGreffe: 'Casablanca',
          justificatifType: 'BAIL' }],
    [3, { apportNumeraire: 100000, apportNumeraireLibere: 100000,
          apportNature: 0, apportIndustrie: 0, nombreParts: 1000,
          valeurNominale: 100, dureeAnnees: 99, depotFondsBloque: 'non' }],
    [4, { description: "le conseil en ingénierie\nla formation professionnelle",
          dateDebutExercice: '2026-01-01' }],
    [5, { dirigeants: [GERANT], gerance: GERANCE }],
    [6, { associes: ASSOCIES, formeJuridique: 'SARL' }],
  ];
  for (const [n, payload] of etapes) {
    const r = await http('POST', `/api/v1/workflows/${ticketId}/execute-step`,
      { token, body: { step: n, payload } });
    dire(r.status === 200 ? 'OK' : 'KO', `étape ${n}`,
      r.status === 200 ? '' : `${r.status} ${JSON.stringify(r.body)}`);
    if (r.status !== 200) return;
  }

  // ---- 4. Ce que l'étape 7 propose ------------------------------------
  titre('4. Documents proposés par l’étape 7');
  const tpl = await http('GET', '/api/v1/ai/workflows/CREATION_SARL/templates', { token });
  const codes = (tpl.body ?? []).map((t) => t.code);
  const attendus = ['STATUTS_SARL_DIRECTEUR', 'ANNONCE_LEGALE_DIRECTEUR',
    'ACTE_NOMINATION_GERANT_DIRECTEUR', 'DEMANDE_TAXE_PROFESSIONNELLE',
    'DECLARATION_EXISTENCE', 'DECLARATION_IMMATRICULATION_RC'];
  for (const c of attendus) {
    dire(codes.includes(c) ? 'OK' : 'KO', `manifeste expose ${c}`);
  }

  // ---- 5. Génération des six documents --------------------------------
  titre('5. Génération des six documents');
  const payload = payloadEtape7();
  const produits = [];
  for (const code of attendus) {
    const r = await http('POST', `/api/v1/ai/workflows/CREATION_SARL/documents/${code}`,
      { token, body: payload, brut: true });
    if (!r.ok) {
      dire('KO', `génération ${code}`, `${r.status} ${r.buf.toString('utf8').slice(0, 300)}`);
      continue;
    }
    const fichier = join(SORTIE, `${code}.docx`);
    await writeFile(fichier, r.buf);
    const manquantes = r.headers.get('x-missing-variables') || '';
    produits.push({ code, fichier, buf: r.buf, manquantes });
    dire('OK', `${code}`, `${(r.buf.length / 1024).toFixed(1)} Ko` +
      (manquantes ? ` — non renseignées : ${manquantes}` : ' — aucune variable vide'));
  }

  // ---- 6. Ce que contiennent vraiment les fichiers ---------------------
  titre('6. Contrôle sur les fichiers produits');
  for (const p of produits) {
    const texte = await texteDuDocx(p.buf);
    const residus = MARQUEURS.filter((m) => m.re.test(texte)).map((m) => m.nom);
    dire(residus.length === 0 ? 'OK' : 'KO', `${p.code} — aucun marqueur résiduel`,
      residus.length ? `trouvés : ${residus.join(', ')}` : '');
  }

  const tp = produits.find((p) => p.code === 'DEMANDE_TAXE_PROFESSIONNELLE');
  if (tp) {
    const t = await texteDuDocx(tp.buf);
    dire(/☒/.test(t) ? 'OK' : 'KO', 'demande TP — une case porte une croix (☒)');
    dire(t.includes(DENOM) ? 'OK' : 'KO', 'demande TP — dénomination imprimée');
    dire(t.includes('Direction régionale de Casablanca-Settat') ? 'OK' : 'KO',
      'demande TP — direction régionale saisie à l’étape 7');
  }
  const acte = produits.find((p) => p.code === 'ACTE_NOMINATION_GERANT_DIRECTEUR');
  if (acte) {
    const t = await texteDuDocx(acte.buf);
    const ligne = (t.split('\n').find((l) => l.includes('pour une durée de')) || '').trim();
    dire(ligne.includes('3 année(s)') ? 'OK' : 'KO',
      'acte — la durée de mandat SAISIE à l’étape 5 est imprimée', ligne);
    dire(!ligne.includes('99 années') ? 'OK' : 'KO',
      'acte — la durée de la SOCIÉTÉ ne tient pas lieu de mandat');
  }

  const de = produits.find((p) => p.code === 'DECLARATION_EXISTENCE');
  if (de) {
    const t = await texteDuDocx(de.buf);
    const croix = (t.match(/☒/g) || []).length;
    dire(croix === 5 ? 'OK' : 'KO', 'déclaration d’existence — les 5 blocs de cases cochés',
      `croix trouvées : ${croix}`);
    dire(t.includes('Yassine BENANI') ? 'OK' : 'KO',
      'déclaration d’existence — associé principal déduit du nombre de parts');
  }
  const rc = produits.find((p) => p.code === 'DECLARATION_IMMATRICULATION_RC');
  if (rc) {
    const t = await texteDuDocx(rc.buf);
    dire(t.includes('20/01/1980 à Casablanca') ? 'OK' : 'KO',
      'modèle 2 — « né le … à … » complet (le trou de l’audit)');
    dire(t.includes('TRIBUNAL DE COMMERCE') ? 'OK' : 'KO',
      'modèle 2 — tribunal déduit de la ville');
    dire(!t.includes('triple exemplaire, de façon très lisible') ? 'OK' : 'KO',
      'modèle 2 — le NOTA (annotation ↳) n’est pas imprimé');
  }

  // ---- 7. Le contrôle de complétude, en vrai --------------------------
  titre('7. Le contrôle de complétude');
  const sansLieu = payloadEtape7(COMPLEMENTS,
    [{ ...GERANT, lieuNaissance: undefined }]);
  const refus = await http('POST',
    '/api/v1/ai/workflows/CREATION_SARL/documents/DECLARATION_IMMATRICULATION_RC',
    { token, body: sansLieu, brut: true });
  const msg = refus.buf.toString('utf8');
  dire(refus.status === 422 ? 'OK' : 'KO',
    'sans lieu de naissance — génération REFUSÉE (422)', `reçu ${refus.status}`);
  dire(msg.includes('GERANT_LIEU_NAISSANCE') ? 'OK' : 'KO',
    'le refus NOMME la variable fautive');
  dire(msg.includes('Date et lieu de naissance') ? 'OK' : 'KO',
    'le refus cite la LIGNE du document');
  if (refus.status === 422) console.log(`      ${C.dim}${msg.slice(0, 400)}${C.z}`);

  const sansDirection = { ...COMPLEMENTS };
  delete sansDirection.directionRegionale;
  delete sansDirection.subdivision;
  const accepte = await http('POST',
    '/api/v1/ai/workflows/CREATION_SARL/documents/DEMANDE_TAXE_PROFESSIONNELLE',
    { token, body: payloadEtape7(sansDirection), brut: true });
  dire(accepte.status === 200 ? 'OK' : 'KO',
    'case administrative vide — génération ACCEPTÉE', `reçu ${accepte.status}`);
  if (accepte.status === 200) {
    dire('INFO', 'la case vide est tout de même remontée',
      accepte.headers.get('x-missing-variables') || '(aucune)');
  }

  // ---- 8. La propagation « gérance statutaire ? » ----------------------
  titre('8. Propagation de la gérance sur les démarches 9, 15 et 18');
  const vue = await http('GET', `/api/v1/tickets/${ticketId}/demarches`, { token });
  const etat = (o) => {
    for (const ph of vue.body?.phases ?? []) {
      for (const l of ph.demarches) if (l.ordre === o) return l.etat;
    }
    return '?';
  };
  if (vue.ok) {
    dire(etat(9) === 'A_FAIRE' ? 'OK' : 'KO',
      'gérance NON statutaire — démarche 9 applicable', `état ${etat(9)}`);
    const prop = await http('POST', `/api/v1/tickets/${ticketId}/demarches/condition-gerance`,
      { token, body: { statutaire: true } });
    if (prop.ok) {
      const e = (o) => {
        for (const ph of prop.body.phases) for (const l of ph.demarches) if (l.ordre === o) return l;
        return null;
      };
      for (const o of [9, 15, 18]) {
        const l = e(o);
        dire(l?.etat === 'NON_APPLICABLE' ? 'OK' : 'KO',
          `démarche ${o} écartée par la réponse`, `état ${l?.etat}`);
      }
      dire('INFO', 'motif porté par les trois', (e(9)?.motif || '').slice(0, 120));
    } else {
      dire('KO', 'appel condition-gerance', `${prop.status} ${JSON.stringify(prop.body)}`);
    }
  } else {
    dire('KO', 'vue des démarches', `${vue.status}`);
  }

  console.log(`\n${C.gras}Fichiers écrits dans${C.z} ${SORTIE}`);
  console.log(echecs === 0
    ? `${C.ok}Tout est vert.${C.z}`
    : `${C.ko}${echecs} contrôle(s) en échec.${C.z}`);
  process.exit(echecs === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(2); });
