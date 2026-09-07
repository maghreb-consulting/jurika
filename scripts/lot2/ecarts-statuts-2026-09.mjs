/**
 * Lot 2 — RELEVÉ D'ÉCARTS : statuts du 4 septembre vs statuts embarqués
 * (2026-09-07).
 *
 * **Ce script ne remplace rien.** Il compare et rapporte, pour que le cabinet
 * tranche entre substituer les modèles et reporter les seules évolutions.
 *
 * Le constat qui justifie ce relevé : le fichier du 4 septembre est le PLUS
 * COURT et porte MOINS de variables que celui embarqué (7 août). Les variables
 * qui manquent sont celles ajoutées en juin-août pour couvrir la combinatoire
 * (mode de signature, plafond, mandataire, mode de libération, dépôt en compte
 * bloqué) et les accords grammaticaux. Substituer sans examen ferait donc
 * RÉGRESSER la génération.
 *
 * Le relevé distingue trois choses, parce qu'elles n'appellent pas la même
 * décision :
 *   1. ce que le 4 septembre APPORTE (absent de l'embarqué) ;
 *   2. ce qu'il PERD (présent dans l'embarqué, absent chez lui) ;
 *   3. ce qui a seulement été REFORMULÉ (même idée, autres mots).
 *
 * Usage : node scripts/lot2/ecarts-statuts-2026-09.mjs
 */

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const RACINE = path.resolve(process.argv[2] ?? 'C:/dev/JURIKA');
const RAPPORT = path.join(RACINE, 'output/2026-09-07_ecarts_statuts.md');

const PAIRES = [
  {
    titre: 'STATUTS_SARL',
    fourni: 'specs/creation-2026-09/STATUTS_SARL.docx',
    embarque: 'projet/backend-java/ai-service/src/main/resources/templates/docx/STATUTS_SARL_modele_deterministe.docx',
  },
  {
    titre: 'STATUTS_SARL_AU',
    fourni: 'specs/creation-2026-09/STATUTS_SARL_AU.docx',
    embarque: 'projet/backend-java/ai-service/src/main/resources/templates/docx/STATUTS_SARL_AU_modele_deterministe.docx',
  },
];

function lireDocumentXml(fichier) {
  const buf = fs.readFileSync(fichier);
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  const nb = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  for (let i = 0; i < nb; i++) {
    const methode = buf.readUInt16LE(off + 10);
    const tailleComp = buf.readUInt32LE(off + 20);
    const lgNom = buf.readUInt16LE(off + 28);
    const lgExtra = buf.readUInt16LE(off + 30);
    const lgComm = buf.readUInt16LE(off + 32);
    const debutLocal = buf.readUInt32LE(off + 42);
    const nom = buf.toString('utf8', off + 46, off + 46 + lgNom);
    if (nom === 'word/document.xml') {
      const lgNomL = buf.readUInt16LE(debutLocal + 26);
      const lgExtraL = buf.readUInt16LE(debutLocal + 28);
      const d = debutLocal + 30 + lgNomL + lgExtraL;
      const donnees = buf.subarray(d, d + tailleComp);
      return (methode === 0 ? donnees : zlib.inflateRawSync(donnees)).toString('utf8');
    }
    off += 46 + lgNom + lgExtra + lgComm;
  }
  throw new Error('document.xml absent : ' + fichier);
}

const texteDe = (xml) => xml.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();

/**
 * Paragraphes de l'ACTE, préambule exclu.
 *
 * Le fichier du 4 septembre s'ouvre sur un préambule « MODÈLE JURIKA » +
 * « Convention de balisage » ; le modèle embarqué, lui, a déjà été préparé et
 * commence directement par l'en-tête de l'acte. Comparer les deux tels quels
 * ferait passer ce préambule pour un « apport » et noierait les vrais écarts.
 * On coupe donc, des deux côtés, au premier bloc portant $DENOMINATION —
 * l'en-tête de l'acte dans les deux fichiers.
 */
function paragraphes(xml) {
  const tous = [...xml.matchAll(/<w:(p|tbl)\b[\s\S]*?<\/w:\1>/g)]
    .map((m) => texteDe(m[0]))
    .filter((t) => t.length > 0);
  // Le bloc d'en-tete COMMENCE par la variable ; c'est ce qui le distingue de
  // la phrase du preambule qui cite « exemple : $DENOMINATION, $CAPITAL_CHIFFRES ».
  const debut = tous.findIndex((t) => t.startsWith('$DENOMINATION'));
  return debut < 0 ? tous : tous.slice(debut);
}

/** Normalisation pour comparer le SENS et non la ponctuation. */
function normaliser(t) {
  return t
    .toLowerCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/\$[a-z0-9_]+/g, '$')          // les variables ne distinguent pas un paragraphe
    .replace(/[’']/g, "'")
    .replace(/[^a-z0-9$'\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Similarité de Jaccard sur les mots — assez pour reperer une reformulation. */
function similarite(a, b) {
  const A = new Set(a.split(' ').filter((w) => w.length > 3));
  const B = new Set(b.split(' ').filter((w) => w.length > 3));
  if (A.size === 0 || B.size === 0) return 0;
  let inter = 0;
  for (const w of A) if (B.has(w)) inter++;
  return inter / (A.size + B.size - inter);
}

const VAR = /\$[A-Z][A-Z0-9_]*/g;

function comparer(paire) {
  const xf = lireDocumentXml(path.join(RACINE, paire.fourni));
  const xe = lireDocumentXml(path.join(RACINE, paire.embarque));
  const pf = paragraphes(xf);
  const pe = paragraphes(xe);
  const nf = pf.map(normaliser);
  const ne = pe.map(normaliser);
  const setE = new Set(ne);
  const setF = new Set(nf);

  const apportes = [];
  const reformules = [];
  for (let i = 0; i < pf.length; i++) {
    if (setE.has(nf[i])) continue;
    // Cherche le plus proche cote embarque : au-dela de 0.6 on parle de
    // reformulation, en dessous d'un ajout.
    let best = 0; let bestIdx = -1;
    for (let j = 0; j < pe.length; j++) {
      const s = similarite(nf[i], ne[j]);
      if (s > best) { best = s; bestIdx = j; }
    }
    if (best >= 0.6) reformules.push({ fourni: pf[i], embarque: pe[bestIdx], score: best });
    else apportes.push(pf[i]);
  }

  const perdus = [];
  for (let j = 0; j < pe.length; j++) {
    if (setF.has(ne[j])) continue;
    let best = 0;
    for (let i = 0; i < pf.length; i++) best = Math.max(best, similarite(ne[j], nf[i]));
    if (best < 0.6) perdus.push(pe[j]);
  }

  const varsF = new Set((texteDe(xf).match(VAR) ?? []));
  const varsE = new Set((texteDe(xe).match(VAR) ?? []));

  const identiques = nf.filter((t) => setE.has(t)).length;

  return {
    titre: paire.titre,
    identiques,
    nbParaFourni: pf.length,
    nbParaEmbarque: pe.length,
    apportes,
    perdus,
    reformules,
    varsSeulementFourni: [...varsF].filter((v) => !varsE.has(v)).sort(),
    varsSeulementEmbarque: [...varsE].filter((v) => !varsF.has(v)).sort(),
    varsCommunes: [...varsF].filter((v) => varsE.has(v)).length,
  };
}

const res = PAIRES.map(comparer);

const l = [];
l.push('# Statuts du 4 septembre — relevé des écarts');
l.push('');
l.push('> Généré par `scripts/lot2/ecarts-statuts-2026-09.mjs` le 2026-09-07.');
l.push('> **Aucun modèle n’a été remplacé.** Ce document sert à l’arbitrage du cabinet :');
l.push('> substituer les fichiers du 4 septembre, ou n’y reporter que leurs apports.');
l.push('');
l.push('## Pourquoi la question se pose');
l.push('');
l.push('Contrairement à ce qu’on attend d’une version plus récente, le fichier du');
l.push('4 septembre est **plus court** et porte **moins de variables** que celui');
l.push('embarqué (7 août). Les variables manquantes sont précisément celles ajoutées');
l.push('en juin-août pour couvrir la combinatoire (mode de signature, plafond,');
l.push('mandataire, mode de libération, dépôt en compte bloqué) et les accords');
l.push('grammaticaux. **Une substitution pure ferait régresser la génération.**');
l.push('');
for (const r of res) {
  l.push('## ' + r.titre);
  l.push('');
  l.push('| | Fourni 04/09 | Embarqué 07/08 |');
  l.push('|---|---:|---:|');
  l.push('| Paragraphes | ' + r.nbParaFourni + ' | ' + r.nbParaEmbarque + ' |');
  l.push('| Variables | ' + (r.varsCommunes + r.varsSeulementFourni.length) + ' | '
    + (r.varsCommunes + r.varsSeulementEmbarque.length) + ' |');
  l.push('| Paragraphes **identiques** | ' + r.identiques + ' | ' + r.identiques + ' |');
  l.push('');
  l.push('> ' + r.identiques + ' paragraphes seulement sont identiques sur '
    + r.nbParaFourni + ' / ' + r.nbParaEmbarque + '. Ce ne sont pas deux versions du');
  l.push('> même texte : ce sont **deux rédactions distinctes** du même acte. Un report');
  l.push('> ligne à ligne n’est donc pas un ajustement, c’est une reprise de fond.');
  l.push('');
  l.push('### 1. Ce que le 4 septembre APPORTE (' + r.apportes.length + ' passage(s))');
  l.push('');
  if (r.apportes.length === 0) l.push('_Rien : aucun passage nouveau._');
  else for (const t of r.apportes) l.push('- ' + t.slice(0, 300) + (t.length > 300 ? '…' : ''));
  l.push('');
  l.push('### 2. Ce que le 4 septembre PERDRAIT (' + r.perdus.length + ' passage(s))');
  l.push('');
  l.push('Passages présents dans le modèle embarqué et absents du fichier fourni.');
  l.push('C’est le coût d’une substitution pure.');
  l.push('');
  if (r.perdus.length === 0) l.push('_Rien._');
  else for (const t of r.perdus) l.push('- ' + t.slice(0, 300) + (t.length > 300 ? '…' : ''));
  l.push('');
  l.push('### 3. Simples reformulations (' + r.reformules.length + ')');
  l.push('');
  l.push('Même contenu, autre rédaction — sans effet sur la génération.');
  l.push('');
  if (r.reformules.length === 0) l.push('_Aucune._');
  else for (const x of r.reformules.slice(0, 40)) {
    l.push('- **04/09** : ' + x.fourni.slice(0, 180));
    l.push('  **07/08** : ' + x.embarque.slice(0, 180));
  }
  if (r.reformules.length > 40) l.push('- _(… ' + (r.reformules.length - 40) + ' autres)_');
  l.push('');
  l.push('### 4. Variables');
  l.push('');
  l.push('- Seulement dans le fichier **fourni** : '
    + (r.varsSeulementFourni.length ? r.varsSeulementFourni.map((v) => '`' + v + '`').join(', ') : '—'));
  l.push('- Seulement dans le modèle **embarqué** (perdues en cas de substitution) : '
    + (r.varsSeulementEmbarque.length ? r.varsSeulementEmbarque.map((v) => '`' + v + '`').join(', ') : '—'));
  l.push('');
}
l.push('## Décision attendue du cabinet');
l.push('');
l.push('1. Les passages listés en **1** doivent-ils être reportés dans le modèle embarqué ?');
l.push('2. Les passages listés en **2** ont-ils été retirés **volontairement** par le');
l.push('   directeur, ou le fichier du 4 septembre part-il d’une base antérieure aux');
l.push('   enrichissements de juin-août ?');
l.push('');
l.push('Tant que le point 2 n’est pas tranché, aucune substitution n’est faite.');
l.push('');
fs.mkdirSync(path.dirname(RAPPORT), { recursive: true });
fs.writeFileSync(RAPPORT, l.join('\n'), 'utf8');

for (const r of res) {
  console.log(r.titre, '| apportes:', r.apportes.length, '| perdus:', r.perdus.length,
    '| reformules:', r.reformules.length,
    '| vars perdues:', r.varsSeulementEmbarque.length);
}
console.log('Rapport :', RAPPORT);
