/**
 * Lecteur XLSX minimal, sans dépendance externe autre que jszip (déjà présent
 * à la racine du dépôt). Suffisant pour les classeurs du cabinet : feuilles
 * plates, chaînes partagées, pas de formules à évaluer.
 *
 * Renvoie, par feuille, un tableau de lignes ; chaque ligne est un tableau
 * indexé par colonne (0 = colonne A), les cellules vides valant ''.
 */
import fs from 'node:fs';
import JSZip from 'jszip';

const decode = (s) =>
  s.replace(/&lt;/g, '<')
   .replace(/&gt;/g, '>')
   .replace(/&quot;/g, '"')
   .replace(/&apos;/g, "'")
   .replace(/&#(\d+);/g, (_, d) => String.fromCharCode(Number(d)))
   .replace(/&amp;/g, '&');

const colIndex = (ref) => {
  let n = 0;
  for (const c of ref.replace(/\d+/g, '')) n = n * 26 + (c.charCodeAt(0) - 64);
  return n - 1;
};

export async function readWorkbook(path) {
  const zip = await JSZip.loadAsync(fs.readFileSync(path));

  let shared = [];
  const ss = zip.file('xl/sharedStrings.xml');
  if (ss) {
    const xml = await ss.async('string');
    shared = [...xml.matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) =>
      [...m[1].matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((t) => decode(t[1])).join(''),
    );
  }

  const rels = await zip.file('xl/_rels/workbook.xml.rels').async('string');
  const relMap = {};
  for (const m of rels.matchAll(/<Relationship\b([^>]*)>/g)) {
    const id = (m[1].match(/Id="([^"]*)"/) || [])[1];
    const target = (m[1].match(/Target="([^"]*)"/) || [])[1];
    if (id && target) relMap[id] = target;
  }

  const wb = await zip.file('xl/workbook.xml').async('string');
  const sheets = {};
  for (const m of wb.matchAll(/<sheet\b([^>]*)\/?>/g)) {
    const name = (m[1].match(/name="([^"]*)"/) || [])[1];
    const rid = (m[1].match(/r:id="([^"]*)"/) || [])[1];
    const target = relMap[rid];
    if (!name || !target) continue;

    const path2 = target.startsWith('/') ? target.slice(1) : 'xl/' + target.replace(/^\.\//, '');
    const file = zip.file(path2);
    if (!file) continue;
    const xml = await file.async('string');

    const rows = [];
    for (const rm of xml.matchAll(/<row[^>]*r="(\d+)"[^>]*>([\s\S]*?)<\/row>/g)) {
      const cells = [];
      for (const cm of rm[2].matchAll(/<c ([^>]*?)\/>|<c ([^>]*?)>([\s\S]*?)<\/c>/g)) {
        const attrs = cm[1] || cm[2] || '';
        const body = cm[3] || '';
        const ref = (attrs.match(/r="([A-Z]+\d+)"/) || [])[1];
        if (!ref) continue;
        const type = (attrs.match(/t="([^"]+)"/) || [])[1];
        let value = '';
        if (type === 'inlineStr') {
          value = [...body.matchAll(/<t[^>]*>([\s\S]*?)<\/t>/g)].map((x) => decode(x[1])).join('');
        } else {
          const v = (body.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
          if (v != null) value = type === 's' ? (shared[Number(v)] ?? '') : decode(v);
        }
        cells[colIndex(ref)] = value.trim();
      }
      rows[Number(rm[1]) - 1] = Array.from(cells, (c) => c ?? '');
    }
    sheets[decode(name)] = Array.from(rows, (r) => r ?? []);
  }
  return sheets;
}
