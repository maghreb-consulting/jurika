/**
 * Lot 2 — PRÉPARATION DES TROIS FORMULAIRES ADMINISTRATIFS (2026-09-07)
 *
 * Ce script NE FAIT PAS l'intégration : il produit, à côté des fichiers fournis
 * par le cabinet, une version prête à intégrer, et un relevé de ce qu'il a
 * trouvé. La décision d'intégrer reste au cabinet.
 *
 * CE QU'IL RETIRE, ET POURQUOI
 * Chacun des trois `.docx` du 4 septembre s'ouvre sur un préambule JURIKA qui
 * documente la convention de balisage. Ce préambule contient les chaînes
 * `$EN_MAJUSCULES` et `$VARIABLE` — non pas comme variables, mais comme
 * EXEMPLES dans une phrase explicative :
 *
 *   « Variables — notées $EN_MAJUSCULES et affichées en bleu … »
 *   « ◈ CASE À COCHER (choix unique) pilotée par $VARIABLE »
 *
 * Le moteur (DocxTemplateEngine) ne fait pas la différence : il les prendrait
 * pour deux variables inconnues, et le document sortirait avec un marqueur non
 * résolu au milieu d'un formulaire officiel. Le préambule décrit AUSSI les
 * marqueurs ▼▲ ◇◆ ◈ dans des paragraphes d'exemple, que le moteur tenterait
 * d'interpréter comme de vraies boucles et de vraies conditions.
 *
 * Le préambule s'arrête au premier bloc du formulaire officiel — « ROYAUME DU
 * MAROC » dans les trois fichiers. Le script coupe là, ni avant ni après, et
 * refuse de travailler s'il ne trouve pas cette frontière : mieux vaut ne rien
 * produire qu'amputer un formulaire.
 *
 * Usage :
 *   node scripts/lot2/preparer-formulaires-dgi.mjs
 * Sorties :
 *   specs/creation-2026-09/prepares/<NOM>.docx   (préambule retiré)
 *   output/2026-09-07_formulaires_dgi_prepares.md (relevé)
 */

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const RACINE = path.resolve(process.argv[2] ?? 'C:/dev/JURIKA');
const SRC = path.join(RACINE, 'specs/creation-2026-09');
const DEST = path.join(SRC, 'prepares');
const RAPPORT = path.join(RACINE, 'output/2026-09-07_formulaires_dgi_prepares.md');

const FICHIERS = [
  'DECLARATION_EXISTENCE',
  'DEMANDE_TAXE_PROFESSIONNELLE',
  'DECLARATION_IMMATRICULATION_RC',
];

/** Premier bloc du formulaire officiel : la frontière de coupe. */
const DEBUT_FORMULAIRE = 'ROYAUME DU MAROC';

// ─────────────────────────────────────────────────────────────────────
//  Lecture / écriture ZIP minimale (pas de dépendance externe)
// ─────────────────────────────────────────────────────────────────────

function lireZip(fichier) {
  const buf = fs.readFileSync(fichier);
  // End of central directory
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('ZIP illisible : ' + fichier);
  const nb = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entrees = [];
  for (let i = 0; i < nb; i++) {
    if (buf.readUInt32LE(off) !== 0x02014b50) throw new Error('Entree centrale invalide');
    const methode = buf.readUInt16LE(off + 10);
    const crc = buf.readUInt32LE(off + 16);
    const tailleComp = buf.readUInt32LE(off + 20);
    const tailleBrute = buf.readUInt32LE(off + 24);
    const lgNom = buf.readUInt16LE(off + 28);
    const lgExtra = buf.readUInt16LE(off + 30);
    const lgComm = buf.readUInt16LE(off + 32);
    const debutLocal = buf.readUInt32LE(off + 42);
    const nom = buf.toString('utf8', off + 46, off + 46 + lgNom);
    // En-tête local
    const lgNomL = buf.readUInt16LE(debutLocal + 26);
    const lgExtraL = buf.readUInt16LE(debutLocal + 28);
    const debutDonnees = debutLocal + 30 + lgNomL + lgExtraL;
    const donnees = buf.subarray(debutDonnees, debutDonnees + tailleComp);
    const contenu = methode === 0 ? donnees : zlib.inflateRawSync(donnees);
    entrees.push({ nom, contenu, methode, crc, tailleBrute });
    off += 46 + lgNom + lgExtra + lgComm;
  }
  return entrees;
}

function crc32(buf) {
  let c;
  const table = crc32.table || (crc32.table = (() => {
    const t = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      t[n] = c;
    }
    return t;
  })());
  let crc = -1;
  for (let i = 0; i < buf.length; i++) crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xff];
  return (crc ^ -1) >>> 0;
}

function ecrireZip(fichier, entrees) {
  const locaux = [];
  const centrales = [];
  let offset = 0;
  for (const e of entrees) {
    const nom = Buffer.from(e.nom, 'utf8');
    const comp = zlib.deflateRawSync(e.contenu, { level: 9 });
    const crc = crc32(e.contenu);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);        // version
    local.writeUInt16LE(0, 6);         // flags
    local.writeUInt16LE(8, 8);         // deflate
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(comp.length, 18);
    local.writeUInt32LE(e.contenu.length, 22);
    local.writeUInt16LE(nom.length, 26);
    locaux.push(local, nom, comp);

    const centrale = Buffer.alloc(46);
    centrale.writeUInt32LE(0x02014b50, 0);
    centrale.writeUInt16LE(20, 4);
    centrale.writeUInt16LE(20, 6);
    centrale.writeUInt16LE(0, 8);
    centrale.writeUInt16LE(8, 10);
    centrale.writeUInt32LE(crc, 16);
    centrale.writeUInt32LE(comp.length, 20);
    centrale.writeUInt32LE(e.contenu.length, 24);
    centrale.writeUInt16LE(nom.length, 28);
    centrale.writeUInt32LE(offset, 42);
    centrales.push(centrale, nom);

    offset += 30 + nom.length + comp.length;
  }
  const debutCentral = offset;
  const tailleCentral = centrales.reduce((n, b) => n + b.length, 0);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(entrees.length, 8);
  eocd.writeUInt16LE(entrees.length, 10);
  eocd.writeUInt32LE(tailleCentral, 12);
  eocd.writeUInt32LE(debutCentral, 16);
  fs.writeFileSync(fichier, Buffer.concat([...locaux, ...centrales, eocd]));
}

// ─────────────────────────────────────────────────────────────────────
//  Découpe du préambule
// ─────────────────────────────────────────────────────────────────────

const texteDe = (xml) => xml.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();

function blocsDuCorps(xml) {
  const m = /<w:body>([\s\S]*)<\/w:body>/.exec(xml);
  if (!m) throw new Error('corps introuvable');
  const blocs = [];
  const re = /<w:(p|tbl)\b[\s\S]*?<\/w:\1>/g;
  let x;
  while ((x = re.exec(m[1])) !== null) {
    blocs.push({ xml: x[0], debut: x.index, fin: x.index + x[0].length, texte: texteDe(x[0]) });
  }
  return { corps: m[1], debutCorps: m.index + '<w:body>'.length, blocs };
}

const VAR = /\$[A-Z][A-Z0-9_]*/g;

function preparer(code) {
  const src = path.join(SRC, code + '.docx');
  const entrees = lireZip(src);
  const doc = entrees.find((e) => e.nom === 'word/document.xml');
  if (!doc) throw new Error(code + ' : word/document.xml absent');
  const xml = doc.contenu.toString('utf8');
  const { blocs } = blocsDuCorps(xml);

  const iDebut = blocs.findIndex((b) => b.texte.startsWith(DEBUT_FORMULAIRE));
  if (iDebut < 0) {
    throw new Error(code + ' : frontiere « ' + DEBUT_FORMULAIRE + ' » introuvable — '
      + 'aucun fichier produit (on ne coupe pas au jugé).');
  }
  const preambule = blocs.slice(0, iDebut);
  const corpsFormulaire = blocs.slice(iDebut);

  // Variables du préambule (les fausses) et du formulaire (les vraies).
  const varsPreambule = [...new Set(preambule.map((b) => b.texte).join(' ').match(VAR) ?? [])];
  const varsFormulaire = [...new Set(corpsFormulaire.map((b) => b.texte).join(' ').match(VAR) ?? [])].sort();

  // Reconstruction : on retire les blocs du préambule dans l'ordre inverse
  // pour ne pas décaler les positions restantes.
  let nouveau = xml;
  for (let i = preambule.length - 1; i >= 0; i--) {
    const b = preambule[i];
    const pos = nouveau.indexOf(b.xml);
    if (pos < 0) throw new Error(code + ' : bloc de preambule introuvable a la reecriture');
    nouveau = nouveau.slice(0, pos) + nouveau.slice(pos + b.xml.length);
  }

  fs.mkdirSync(DEST, { recursive: true });
  const cible = path.join(DEST, code + '.docx');
  ecrireZip(cible, entrees.map((e) => (e.nom === 'word/document.xml'
    ? { ...e, contenu: Buffer.from(nouveau, 'utf8') }
    : e)));

  // Relecture de contrôle : le fichier produit doit être lisible et ne plus
  // porter les fausses variables.
  const relu = lireZip(cible).find((e) => e.nom === 'word/document.xml').contenu.toString('utf8');
  const varsRelues = [...new Set(texteDe(relu).match(VAR) ?? [])].sort();
  const fausses = varsRelues.filter((v) => v === '$VARIABLE' || v === '$EN_MAJUSCULES');

  return {
    code,
    blocsRetires: preambule.length,
    texteRetire: preambule.map((b) => b.texte).filter(Boolean),
    varsPreambule,
    varsFormulaire,
    varsApres: varsRelues,
    fausesRestantes: fausses,
    marqueurs: {
      boucles: (texteDe(relu).match(/▼|▲/g) ?? []).length,
      conditions: (texteDe(relu).match(/◇|◆/g) ?? []).length,
      cases: (texteDe(relu).match(/◈/g) ?? []).length,
    },
  };
}

// ─────────────────────────────────────────────────────────────────────
//  Dictionnaire des variables (référence du cabinet)
// ─────────────────────────────────────────────────────────────────────

function variablesDuDictionnaire() {
  const p = path.join(SRC, 'DICTIONNAIRE_VARIABLES.docx');
  const xml = lireZip(p).find((e) => e.nom === 'word/document.xml').contenu.toString('utf8');
  // Le dictionnaire est un tableau : on prend le texte de chaque cellule pour
  // éviter la concaténation d'une variable avec le mot suivant.
  const cellules = [...xml.matchAll(/<w:tc\b[\s\S]*?<\/w:tc>/g)].map((m) => texteDe(m[0]));
  const paras = [...xml.matchAll(/<w:p\b[\s\S]*?<\/w:p>/g)].map((m) => texteDe(m[0]));
  const set = new Set();
  for (const t of [...cellules, ...paras]) {
    for (const v of t.match(VAR) ?? []) set.add(v);
  }
  return set;
}

// ─────────────────────────────────────────────────────────────────────

const dico = variablesDuDictionnaire();
const resultats = FICHIERS.map(preparer);

const l = [];
l.push('# Formulaires DGI / greffe — préparation à l’intégration');
l.push('');
l.push('> Généré par `scripts/lot2/preparer-formulaires-dgi.mjs` le 2026-09-07.');
l.push('> **Rien n’est intégré** : les fichiers prêts sont déposés dans');
l.push('> `specs/creation-2026-09/prepares/`. L’intégration reste une décision du cabinet.');
l.push('');
l.push('## Ce qui a été retiré');
l.push('');
l.push('Le préambule « MODÈLE JURIKA » + « Convention de balisage » de chaque fichier :');
l.push('il documente la syntaxe du moteur en s’en servant comme exemple, donc il');
l.push('contient de fausses variables (`$VARIABLE`, `$EN_MAJUSCULES`) et de faux');
l.push('marqueurs (`▼ DÉBUT BOUCLE`, `◇ SI`, `◈ CASE À COCHER`) que le moteur aurait');
l.push('interprétés. La coupe s’arrête au premier bloc du formulaire officiel');
l.push('(« ROYAUME DU MAROC ») — le script refuse de produire un fichier s’il ne');
l.push('trouve pas cette frontière.');
l.push('');
for (const r of resultats) {
  l.push('### ' + r.code);
  l.push('');
  l.push('- Blocs retirés : **' + r.blocsRetires + '**');
  l.push('- Fausses variables présentes dans le préambule retiré : '
    + (r.varsPreambule.length ? r.varsPreambule.map((v) => '`' + v + '`').join(', ') : '—'));
  l.push('- Fausses variables encore présentes après préparation : '
    + (r.fausesRestantes.length ? '**' + r.fausesRestantes.join(', ') + '**' : '**aucune**'));
  l.push('- Variables réelles du formulaire : **' + r.varsFormulaire.length + '**');
  l.push('- Marqueurs du moteur conservés : ' + r.marqueurs.boucles + ' de boucle (▼▲), '
    + r.marqueurs.conditions + ' de condition (◇◆), ' + r.marqueurs.cases + ' de case (◈)');
  const inconnues = r.varsFormulaire.filter((v) => !dico.has(v));
  l.push('- **Variables absentes du dictionnaire fourni** : '
    + (inconnues.length ? inconnues.map((v) => '`' + v + '`').join(', ') : 'aucune'));
  l.push('');
}
l.push('## Variables inconnues — à trancher par le cabinet');
l.push('');
l.push('Aucune n’a été inventée ni renommée. Elles sont listées telles qu’elles');
l.push('figurent dans les formulaires ; il revient au cabinet de dire si elles');
l.push('doivent rejoindre le dictionnaire, ou être renommées vers une variable');
l.push('existante.');
l.push('');
const toutesInconnues = [...new Set(resultats.flatMap((r) => r.varsFormulaire.filter((v) => !dico.has(v))))].sort();
if (toutesInconnues.length === 0) {
  l.push('_Aucune : toutes les variables des trois formulaires figurent au dictionnaire._');
} else {
  l.push('| Variable | Formulaire(s) |');
  l.push('|---|---|');
  for (const v of toutesInconnues) {
    const ou = resultats.filter((r) => r.varsFormulaire.includes(v)).map((r) => r.code).join(', ');
    l.push('| `' + v + '` | ' + ou + ' |');
  }
}
l.push('');
fs.mkdirSync(path.dirname(RAPPORT), { recursive: true });
fs.writeFileSync(RAPPORT, l.join('\n'), 'utf8');

for (const r of resultats) {
  console.log(r.code, '— blocs retires:', r.blocsRetires,
    '| vars formulaire:', r.varsFormulaire.length,
    '| fausses restantes:', r.fausesRestantes.length,
    '| inconnues:', r.varsFormulaire.filter((v) => !dico.has(v)).length);
}
console.log('Rapport :', RAPPORT);
console.log('Fichiers prepares :', DEST);
