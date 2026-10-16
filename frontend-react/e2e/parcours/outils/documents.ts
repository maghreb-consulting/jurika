import { inflateRawSync, inflateSync } from 'node:zlib';

/**
 * Lot L6b (E13) : controle des documents produits (.docx et .pdf).
 *
 * Lecture :
 *  - .docx : archive ZIP lue par un lecteur minimal (node:zlib, aucune dependance) ; texte de
 *    word/document.xml, des en-tetes (word/header*.xml) et des pieds (word/footer*.xml), un
 *    paragraphe <w:p> par ligne (cellules de tableau comprises), <w:tab/> en tabulation ;
 *  - .pdf : pdfjs-dist n'est pas installe (hors ligne) ; extraction SIMPLE : flux FlateDecode
 *    decompresses, chaines des operateurs Tj / TJ / ' / " (litterales, et hexadecimales decodees
 *    par les tables ToUnicode du fichier, ou sur un octet sans table). Glyphe sans
 *    correspondance, tables contradictoires entre polices, ou trop peu de lettres : le controle
 *    le DIT (`lisible: false`) au lieu de conclure a un document sain.
 *
 * Defauts detectes (`analyserTexte`) :
 *  1. VALEUR_MANQUANTE : "VALEUR MANQUANTE" (marqueur rouge du moteur : variable non resolue) ;
 *  2. A_COMPLETER : "[a completer]" et "[non renseigne" (repli du moteur / du contrat : donnee
 *     declaree mais absente) ;
 *  3. MARQUEUR : marqueur non remplace : ${...}, {{...}}, $NOM_EN_MAJUSCULES (3 caracteres au
 *     moins), sentinelles du moteur (U+E000, U+E001), marqueurs de bloc ou de condition du
 *    gabarit (U+25B6, U+25C0, U+25BC, U+25B2, U+25C7, U+25C6, U+25C8), annotation (U+21B3), "NOTES D'EMPLOI" ;
 *  4. BLANC_POINTILLE : blanc de remplissage, suite d'au moins 4 points, de 2 points de
 *     suspension (U+2026) ou plus, ou d'au moins 4 soulignes ;
 *  5. BLANC_PHRASE : champ vide au milieu d'une phrase : un mot de liaison suivi directement
 *     d'une ponctuation ou d'un autre mot de liaison ("ne le , a", "demeurant a ,", "le  a") ;
 *  6. CHAMP_VIDE : libelle sans valeur entre deux libelles : deux paragraphes consecutifs qui ne
 *     sont chacun qu'un libelle termine par ":" (le premier est vide), ou " : " suivi d'une
 *     ponctuation (",", ";", ".") dans la meme ligne ;
 *  7. DATE_ISO (L6b E14) : date imprimee au format de l'ecran (aaaa-mm-jj) au lieu de jj/mm/aaaa
 *     (constate sur la pile : bail, declaration d'existence, declaration d'immatriculation).
 * PERMIS (regle des variables d'un acte, CLAUDE.md) : le marqueur "<< A OBTENIR : NOM >>" d'une
 * variable EXTERNE (donnee attendue d'un organisme) ; il est rendu a part (`aObtenir`).
 * Un test peut nommer des exceptions (`permis`) : texte du gabarit lui-meme (ligne de signature
 * pointillee d'un imprime, par exemple), chacune justifiee.
 */
export type GenreDefaut = 'VALEUR_MANQUANTE' | 'A_COMPLETER' | 'MARQUEUR' | 'BLANC_POINTILLE' | 'BLANC_PHRASE' | 'CHAMP_VIDE' | 'DATE_ISO';

export interface Defaut { genre: GenreDefaut; ligne: number; extrait: string }
export interface Permis { texte: RegExp; motif: string }
export interface Analyse { defauts: Defaut[]; aObtenir: string[]; lisible: boolean; lignes: number }

/* ------------------------------ lecture ZIP ------------------------------ */

export function lireZip(buf: Buffer): Map<string, Buffer> {
  const fichiers = new Map<string, Buffer>();
  let fin = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65_557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { fin = i; break; }
  }
  if (fin < 0) throw new Error('archive ZIP illisible : fin du repertoire central introuvable');
  const total = buf.readUInt16LE(fin + 10);
  let p = buf.readUInt32LE(fin + 16);
  for (let n = 0; n < total; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('archive ZIP illisible : repertoire central corrompu');
    const methode = buf.readUInt16LE(p + 10);
    const taille = buf.readUInt32LE(p + 20);
    const lNom = buf.readUInt16LE(p + 28);
    const lExtra = buf.readUInt16LE(p + 30);
    const lCom = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const nom = buf.toString('utf8', p + 46, p + 46 + lNom);
    const debut = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const brut = buf.subarray(debut, debut + taille);
    fichiers.set(nom, methode === 0 ? Buffer.from(brut) : inflateRawSync(brut));
    p += 46 + lNom + lExtra + lCom;
  }
  return fichiers;
}

function decoderXml(t: string): string {
  return t.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&amp;/g, '&');
}

/** Paragraphes d'une partie WordprocessingML. */
export function paragraphesXml(xml: string): string[] {
  const out: string[] = [];
  for (const m of xml.matchAll(/<w:p[ >][\s\S]*?<\/w:p>|<w:p\/>/g)) {
    let t = '';
    for (const e of m[0].matchAll(/<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>|<w:tab\/>|<w:br\/>/g)) {
      if (e[0] === '<w:tab/>') t += '\t';
      else if (e[0] === '<w:br/>') t += '\n';
      else t += decoderXml(e[1]);
    }
    out.push(t);
  }
  return out;
}

export function texteDocx(buf: Buffer): string[] {
  const z = lireZip(buf);
  const doc = z.get('word/document.xml');
  if (!doc) throw new Error('.docx illisible : word/document.xml absent');
  const parties = [...z.keys()].filter((k) => /^word\/(header|footer)\d*\.xml$/.test(k)).sort();
  return [doc, ...parties.map((k) => z.get(k) as Buffer)].flatMap((b) => paragraphesXml(b.toString('utf8')));
}

/* ------------------------------ lecture PDF ------------------------------ */

function chaineLitterale(s: string): string {
  return s.replace(/\\([nrtbf()\\]|[0-7]{1,3})/g, (_, c: string) => {
    const t: Record<string, string> = { n: '\n', r: '\r', t: '\t', b: '\b', f: '\f', '(': '(', ')': ')', '\\': '\\' };
    return t[c] ?? String.fromCharCode(parseInt(c, 8));
  });
}

/** UTF-16BE (hexadecimal) vers texte. */
function utf16(hex: string): string {
  let t = '';
  for (let i = 0; i + 3 < hex.length; i += 4) t += String.fromCharCode(parseInt(hex.slice(i, i + 4), 16));
  return t;
}

interface TableUnicode { table: Map<string, string>; largeur: number; conflits: number }

/** Table ToUnicode (beginbfchar / beginbfrange) d'un ou de plusieurs flux CMap. */
function tableUnicode(flux: string[]): TableUnicode {
  const table = new Map<string, string>();
  let largeur = 2;
  let conflits = 0;
  const poser = (code: string, valeur: string) => {
    const k = code.toUpperCase();
    largeur = Math.max(largeur, k.length);
    const avant = table.get(k);
    if (avant !== undefined && avant !== valeur) conflits++;
    table.set(k, valeur);
  };
  for (const f of flux) {
    for (const b of f.matchAll(/beginbfchar([\s\S]*?)endbfchar/g)) {
      for (const m of b[1].matchAll(/<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>/g)) poser(m[1], utf16(m[2]));
    }
    for (const b of f.matchAll(/beginbfrange([\s\S]*?)endbfrange/g)) {
      for (const m of b[1].matchAll(/<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>\s*(?:<([0-9a-fA-F]+)>|\[([^\]]*)\])/g)) {
        const n = m[1].length;
        const de = parseInt(m[1], 16);
        const jusque = parseInt(m[2], 16);
        const liste = m[4] ? [...m[4].matchAll(/<([0-9a-fA-F]+)>/g)].map((x) => x[1]) : null;
        for (let c = de; c <= jusque && c - de < 65_536; c++) {
          const code = c.toString(16).padStart(n, '0');
          if (liste) { if (liste[c - de]) poser(code, utf16(liste[c - de])); continue; }
          const base = m[3] as string;
          const fin = (parseInt(base.slice(-4), 16) + (c - de)).toString(16).padStart(4, '0');
          poser(code, utf16(base.slice(0, -4) + fin));
        }
      }
    }
  }
  return { table, largeur, conflits };
}

interface ObjetPdf { dico: string; flux?: string }

/** Objets du fichier : objets en clair et objets des flux d'objets (/Type /ObjStm). */
function objetsPdf(buf: Buffer): Map<number, ObjetPdf> {
  const brut = buf.toString('latin1');
  const objets = new Map<number, ObjetPdf>();
  for (const m of brut.matchAll(/(\d+)\s+\d+\s+obj\b/g)) {
    const debut = (m.index ?? 0) + m[0].length;
    const fin = brut.indexOf('endobj', debut);
    if (fin < 0) continue;
    const corps = brut.slice(debut, fin);
    const st = corps.search(/stream\r?\n/);
    if (st < 0) { objets.set(Number(m[1]), { dico: corps }); continue; }
    const dico = corps.slice(0, st);
    const ouverture = corps.slice(st).match(/^stream\r?\n/)?.[0].length ?? 7;
    const finFlux = corps.lastIndexOf('endstream');
    const octets = buf.subarray(debut + st + ouverture, debut + (finFlux > 0 ? finFlux : corps.length));
    let flux: string | undefined;
    if (/FlateDecode/.test(dico)) {
      try { flux = inflateSync(octets).toString('latin1'); } catch { flux = undefined; }
    } else if (!/\/Filter/.test(dico)) {
      flux = octets.toString('latin1');
    }
    objets.set(Number(m[1]), { dico, flux });
  }
  for (const o of [...objets.values()]) {
    if (!/\/Type\s*\/ObjStm/.test(o.dico) || !o.flux) continue;
    const premier = Number(o.dico.match(/\/First\s+(\d+)/)?.[1] ?? -1);
    const nombre = Number(o.dico.match(/\/N\s+(\d+)/)?.[1] ?? 0);
    if (premier < 0) continue;
    const entete = o.flux.slice(0, premier).trim().split(/\s+/).map(Number);
    for (let i = 0; i < nombre; i++) {
      const num = entete[2 * i];
      const dec = entete[2 * i + 1];
      const suiv = i + 1 < nombre ? entete[2 * i + 3] : o.flux.length - premier;
      if (!objets.has(num)) objets.set(num, { dico: o.flux.slice(premier + dec, premier + suiv) });
    }
  }
  return objets;
}

export interface TextePdf { lignes: string[]; conflits: number }

/**
 * Texte d'un PDF. Chaque police composite est lue avec SA table ToUnicode (ressources /Font de
 * la page -> objet police -> /ToUnicode) ; une police sans table est lue octet par octet.
 */
export function textePdfDetaille(buf: Buffer): TextePdf {
  const objets = objetsPdf(buf);
  const tables = new Map<string, TableUnicode>();
  let conflits = 0;
  // Ressources /Font : dictionnaire en ligne (/Font << /F1 10 0 R >>) ou objet a part (/Font 9 0 R).
  const ressources: string[] = [];
  for (const o of objets.values()) {
    for (const r of o.dico.matchAll(/\/Font\s*<<([\s\S]*?)>>/g)) ressources.push(r[1]);
    for (const r of o.dico.matchAll(/\/Font\s+(\d+)\s+\d+\s+R/g)) {
      const d = objets.get(Number(r[1]))?.dico;
      if (d) ressources.push(d);
    }
  }
  for (const r of ressources) {
    {
      for (const f of r.matchAll(/\/([^\s/<>[\]()]+)\s+(\d+)\s+\d+\s+R/g)) {
        const police = objets.get(Number(f[2]));
        const ref = police?.dico.match(/\/ToUnicode\s+(\d+)\s+\d+\s+R/);
        const cmap = ref ? objets.get(Number(ref[1]))?.flux : undefined;
        if (!cmap) continue;
        const t = tableUnicode([cmap]);
        const deja = tables.get(f[1]);
        if (deja && deja.table.size !== t.table.size) conflits++;
        tables.set(f[1], t);
      }
    }
  }
  let courante: TableUnicode | undefined;
  const hexa = (h: string): string => {
    let t = '';
    if (courante && courante.table.size > 0) {
      for (let i = 0; i < h.length; i += courante.largeur) {
        t += courante.table.get(h.slice(i, i + courante.largeur).toUpperCase()) ?? '\ufffd';
      }
      return t;
    }
    for (let i = 0; i + 1 < h.length; i += 2) t += String.fromCharCode(parseInt(h.slice(i, i + 2), 16));
    return t;
  };
  const lignes: string[] = [];
  for (const o of objets.values()) {
    if (!o.flux || !/\bBT\b/.test(o.flux) || /beginbfchar|beginbfrange/.test(o.flux)) continue;
    let t = '';
    let dansTexte = false;
    const jeton = /\/([^\s/<>[\]()]+)\s+[-\d.]+\s+Tf|\bBT\b|\bET\b|\((?:\\.|[^\\)])*\)|<([0-9a-fA-F\s]+)>|T\*|\bT[dD]\b|'|"/g;
    for (const op of o.flux.matchAll(jeton)) {
      const x = op[0];
      if (op[1]) { courante = tables.get(op[1]); continue; }
      if (x === 'BT') { dansTexte = true; t = ''; continue; }
      if (x === 'ET') { dansTexte = false; lignes.push(t.replace(/\s+/g, ' ').trim()); continue; }
      if (!dansTexte) continue;
      if (x.startsWith('(')) {
        // Chaine litterale : sous une police composite, ce sont aussi des codes de glyphes.
        const octets = chaineLitterale(x.slice(1, -1));
        t += courante && courante.table.size > 0
          ? hexa([...octets].map((c) => c.charCodeAt(0).toString(16).padStart(2, '0')).join(''))
          : octets;
      } else if (op[2]) t += hexa(op[2].replace(/\s+/g, ''));
      else t += ' ';
    }
  }
  return { lignes: lignes.filter((l) => l !== ''), conflits };
}

export function textePdf(buf: Buffer): string[] {
  return textePdfDetaille(buf).lignes;
}

/* ------------------------------ analyse ------------------------------ */

const A_OBTENIR = /\u2039\s*\u00c0 OBTENIR\s*:\s*([^\u203a]+?)\s*\u203a/g;
const LIAISON = '(?:le|la|les|du|de|des|au|aux|\u00e0|en|n\u00e9|n\u00e9e|demeurant|domicili\u00e9|domicili\u00e9e|sis|sise|titulaire|par|pour|sur|n\u00b0)';
const REGLES: { genre: GenreDefaut; motif: RegExp }[] = [
  { genre: 'VALEUR_MANQUANTE', motif: /VALEUR MANQUANTE/i },
  { genre: 'A_COMPLETER', motif: /\[\s*\u00e0 compl\u00e9ter\s*\]|\[non renseign/i },
  { genre: 'MARQUEUR', motif: /\$\{[^}]*\}|\{\{[^}]*\}\}|\$[A-Z][A-Z0-9_]{2,}|[\ue000\ue001\u25b6\u25c0\u25bc\u25b2\u25c7\u25c6\u25c8\u21b3]|NOTES D.EMPLOI/ },
  { genre: 'BLANC_POINTILLE', motif: /\.{4,}|\u2026{2,}|_{4,}/ },
  // Mot de liaison suivi d'une ponctuation ("ne le , a"), ou de deux espaces puis d'un autre mot de
  // liaison ("le  a" : la valeur entre les deux a disparu). "de la", "a la" restent permis.
  { genre: 'BLANC_PHRASE', motif: new RegExp(`(?:^|[\\s(])${LIAISON}(?:\\s+[,;.)]|\\s{2,}${LIAISON}(?=[\\s,;.]))`, 'i') },
  { genre: 'CHAMP_VIDE', motif: /\s:\s*[,;.](?!\d)/ },
  { genre: 'DATE_ISO', motif: /\b(?:19|20)\d{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])\b/ },
];
const LIBELLE_SEUL = /^[^:\n]{1,60}:\s*$/;

export function analyserTexte(lignes: string[], permis: Permis[] = []): Analyse {
  const defauts: Defaut[] = [];
  const aObtenir: string[] = [];
  const nettes = lignes.map((l) => {
    let t = l;
    for (const m of l.matchAll(A_OBTENIR)) aObtenir.push(m[1].trim());
    t = t.replace(A_OBTENIR, 'A_OBTENIR');
    for (const p of permis) t = t.replace(new RegExp(p.texte.source, p.texte.flags.includes('g') ? p.texte.flags : `${p.texte.flags}g`), ' ');
    return t;
  });
  nettes.forEach((t, i) => {
    for (const r of REGLES) {
      const m = t.match(r.motif);
      if (m) defauts.push({ genre: r.genre, ligne: i + 1, extrait: extrait(lignes[i], m.index ?? 0) });
    }
    const suivante = nettes[i + 1];
    if (LIBELLE_SEUL.test(t.trim()) && suivante !== undefined && LIBELLE_SEUL.test(suivante.trim())) {
      defauts.push({ genre: 'CHAMP_VIDE', ligne: i + 1, extrait: `${lignes[i].trim()} | ${lignes[i + 1].trim()}` });
    }
  });
  // Lisible : assez de lettres, dont la plupart forment des mots (3 lettres ou plus). Un texte
  // mal decode (glyphes lus comme des octets) donne des lettres isolees : "A \u00be U ...".
  // Une empreinte hexadecimale (32 chiffres ou plus, SHA-256 du contrat par exemple) est un texte
  // voulu, ni mot ni glyphe mal lu : elle est retiree de ces calculs (ses lettres a-f isolees
  // feraient passer sa ligne pour brouillee, une fois sur deux selon le hasard de l'empreinte).
  const sansEmpreinte = lignes.map((l) => l.replace(/\b[0-9a-fA-F]{32,}\b/g, ' '));
  const tout = sansEmpreinte.join(' ');
  const signes = tout.replace(/\s+/g, '').length;
  const enMots = (tout.match(/[A-Za-z\u00c0-\u024f]{3,}/g) ?? []).join('').length;
  // Et aucune ligne brouillee : des lettres, mais presque aucune en mots.
  const brouillee = sansEmpreinte.some((l) => {
    const lettres = l.match(/[A-Za-z\u00c0-\u024f]/g)?.length ?? 0;
    const mots = (l.match(/[A-Za-z\u00c0-\u024f]{3,}/g) ?? []).join('').length;
    return lettres >= 4 && mots / lettres < 0.3;
  });
  const lisible = enMots >= 20 && signes > 0 && enMots / signes >= 0.5 && !brouillee;
  return { defauts, aObtenir, lisible, lignes: lignes.length };
}

function extrait(l: string, i: number): string {
  const a = Math.max(0, i - 40);
  return `${a > 0 ? '...' : ''}${l.slice(a, i + 60).replace(/\s+/g, ' ').trim()}${i + 60 < l.length ? '...' : ''}`;
}

/** Type d'un document d'apres ses premiers octets. */
export function genreFichier(buf: Buffer): 'docx' | 'pdf' | 'autre' {
  if (buf.subarray(0, 5).toString('latin1') === '%PDF-') return 'pdf';
  if (buf.readUInt32LE(0) === 0x04034b50) return 'docx';
  return 'autre';
}

export function analyserDocument(buf: Buffer, permis: Permis[] = []): Analyse & { genre: 'docx' | 'pdf' } {
  const g = genreFichier(buf);
  if (g === 'autre') throw new Error('document ni .docx ni .pdf (premiers octets inattendus)');
  if (g === 'docx') return { ...analyserTexte(texteDocx(buf), permis), genre: g };
  const t = textePdfDetaille(buf);
  const a = analyserTexte(t.lignes, permis);
  // Glyphes sans correspondance (U+FFFD) ou tables contradictoires : lecture douteuse, dite.
  const douteux = t.conflits > 0 || (t.lignes.join('').match(/\ufffd/g)?.length ?? 0) > 0;
  return { ...a, lisible: a.lisible && !douteux, genre: g };
}

/** Message d'echec nommant chaque defaut, ou null si le document est sain. */
export function rapportDocument(nom: string, a: Analyse & { genre: string }): string | null {
  if (!a.lisible) {
    return `${nom} (${a.genre}) : texte illisible par l'extraction simple (police composite ?) : `
      + 'controler le .docx correspondant ; aucun document n\'est declare sain sans avoir ete lu.';
  }
  if (a.defauts.length === 0) return null;
  return `${nom} (${a.genre}) : ${a.defauts.length} defaut(s)\n`
    + a.defauts.map((d) => `  - ${d.genre} ligne ${d.ligne} : "${d.extrait}"`).join('\n');
}

/* ------------------------------ ecriture ZIP (temoins) ------------------------------ */

/** Archive ZIP minimale, sans compression (temoins des tests du detecteur). */
export function ecrireZip(fichiers: Record<string, string>): Buffer {
  const locaux: Buffer[] = [];
  const centraux: Buffer[] = [];
  let decalage = 0;
  for (const [nom, contenu] of Object.entries(fichiers)) {
    const n = Buffer.from(nom, 'utf8');
    const d = Buffer.from(contenu, 'utf8');
    const l = Buffer.alloc(30);
    l.writeUInt32LE(0x04034b50, 0); l.writeUInt16LE(20, 4); l.writeUInt32LE(d.length, 18); l.writeUInt32LE(d.length, 22);
    l.writeUInt16LE(n.length, 26);
    const c = Buffer.alloc(46);
    c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt32LE(d.length, 20);
    c.writeUInt32LE(d.length, 24); c.writeUInt16LE(n.length, 28); c.writeUInt32LE(decalage, 42);
    locaux.push(l, n, d);
    centraux.push(c, n);
    decalage += 30 + n.length + d.length;
  }
  const central = Buffer.concat(centraux);
  const fin = Buffer.alloc(22);
  fin.writeUInt32LE(0x06054b50, 0);
  fin.writeUInt16LE(Object.keys(fichiers).length, 8); fin.writeUInt16LE(Object.keys(fichiers).length, 10);
  fin.writeUInt32LE(central.length, 12); fin.writeUInt32LE(decalage, 16);
  return Buffer.concat([...locaux, central, fin]);
}
