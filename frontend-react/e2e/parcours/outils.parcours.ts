import { test, expect } from '@playwright/test';
import { deflateSync } from 'node:zlib';
import { API_URL } from './outils/env';
import { Sentinelle, masquer } from './outils/sentinelle';
import { analyserDocument, analyserTexte, ecrireZip, rapportDocument, texteDocx, textePdf } from './outils/documents';
import { codeTotp } from './outils/totp';

/**
 * Lot L6b (E13) : tests des outils des parcours, SANS pile (page.setContent, reponses simulees
 * par page.route). Chaque controle a son temoin positif (le defaut est signale et nomme) et son
 * temoin negatif (le cas sain ne l'est pas) : un controle qui ne voit rien ne prouve rien.
 */

const CORS = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Content-Type': 'application/json' };

test.describe('sentinelle', () => {
  test('un 400 non tolere est consigne : ecran, requete, corps envoye (masque) et reponse', async ({ page }) => {
    const s = new Sentinelle(page);
    await page.route(`${API_URL}/**`, (r) => r.request().method() === 'OPTIONS'
      ? r.fulfill({ status: 204, headers: CORS })
      : r.fulfill({ status: 400, headers: CORS, body: JSON.stringify({ code: 'VALIDATION_ERROR', message: 'Donnees invalides' }) }));
    await page.setContent('<p>ecran</p>');
    await page.evaluate((u) => fetch(`${u}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'a@b.ma', password: 'secret-ne-doit-pas-sortir' }) }).catch(() => null), API_URL);
    const r = await s.rapport();
    expect(r).toContain('HTTP 400 POST /auth/login');
    expect(r).toContain('"email":"a@b.ma"');
    expect(r).toContain('VALIDATION_ERROR');
    expect(r).not.toContain('secret-ne-doit-pas-sortir');
  });

  test('un 403 tolere nommement n est pas consigne ; un 404 voisin l est', async ({ page }) => {
    const s = new Sentinelle(page).tolerer({ methode: 'GET', chemin: /^\/chat\/conversations$/, statut: 403, motif: 'temoin' });
    await page.route(`${API_URL}/**`, (r) => r.fulfill({ status: r.request().url().includes('chat') ? 403 : 404, headers: CORS, body: '{}' }));
    await page.setContent('<p>ecran</p>');
    await page.evaluate(async (u) => { await fetch(`${u}/chat/conversations`); await fetch(`${u}/inconnu`); }, API_URL);
    const r = await s.rapport();
    expect(r).not.toContain('/chat/conversations');
    expect(r).toContain('HTTP 404 GET /inconnu');
  });

  test('console.error et exception de page sont consignees ; l echo "Failed to load resource" ne l est pas', async ({ page }) => {
    const s = new Sentinelle(page);
    await page.setContent('<p>ecran</p>');
    await page.evaluate(() => {
      console.error('Failed to load resource: the server responded with a status of 403 ()');
      console.error('Erreur de rendu du tableau');
      setTimeout(() => { throw new TypeError('crypto.randomUUID is not a function'); }, 0);
    });
    await page.waitForTimeout(100);
    const r = (await s.rapport()) ?? '';
    expect(r).toContain('console.error : Erreur de rendu du tableau');
    expect(r).toContain('TypeError: crypto.randomUUID is not a function');
    expect(r).not.toContain('Failed to load resource');
  });

  test('page saine : aucun incident', async ({ page }) => {
    const s = new Sentinelle(page);
    await page.route(`${API_URL}/**`, (r) => r.fulfill({ status: 200, headers: CORS, body: '{}' }));
    await page.setContent('<p>ecran</p>');
    await page.evaluate((u) => fetch(`${u}/auth/me`), API_URL);
    expect(await s.rapport()).toBeNull();
  });

  test('masquage des champs sensibles', () => {
    expect(masquer('{"refreshToken":"abc.def","code":"123456","nom":"X"}')).toBe('{"refreshToken":"<masque>","code":"<masque>","nom":"X"}');
    expect(masquer('Authorization: Bearer eyJ.abc')).toBe('Authorization: Bearer <masque>');
  });
});

function docx(paragraphes: string[], entete?: string): Buffer {
  const p = (t: string) => `<w:p><w:r><w:t xml:space="preserve">${t.replace(/&/g, '&amp;').replace(/</g, '&lt;')}</w:t></w:r></w:p>`;
  const corps = `<?xml version="1.0"?><w:document xmlns:w="w"><w:body>${paragraphes.map(p).join('')}</w:body></w:document>`;
  const f: Record<string, string> = { 'word/document.xml': corps };
  if (entete) f['word/header1.xml'] = `<w:hdr xmlns:w="w">${p(entete)}</w:hdr>`;
  return ecrireZip(f);
}

const SAIN = [
  'PROCES-VERBAL DE LA DECISION DE L\'ASSOCIE UNIQUE',
  'M. Ahmed ALAOUI, ne le 12/05/1980 a Casablanca, demeurant 12 rue Demo, Casablanca.',
  'Capital social : 100 000 dirhams.',
  'Ont comparu :',
  'M. Ahmed ALAOUI, associe unique.',
];

test.describe('controle des documents', () => {
  test('document sain : aucun defaut, texte lisible', () => {
    const a = analyserDocument(docx(SAIN));
    expect(a.genre).toBe('docx');
    expect(a.defauts).toEqual([]);
    expect(rapportDocument('pv.docx', a)).toBeNull();
  });

  const cas: [string, string, string][] = [
    ['VALEUR_MANQUANTE', 'Gerant : \u2039 VALEUR MANQUANTE : GERANT_NOM \u203a', 'VALEUR MANQUANTE'],
    ['A_COMPLETER', 'Date d\'effet : [\u00e0 compl\u00e9ter]', '[\u00e0 compl\u00e9ter]'],
    ['A_COMPLETER', 'Ville : [non renseign\u00e9 : ville du RC]', '[non renseign'],
    ['MARQUEUR', 'La societe ${DENOMINATION} est constituee.', '${DENOMINATION}'],
    ['MARQUEUR', 'Capital de $CAPITAL_SOCIAL dirhams.', '$CAPITAL_SOCIAL'],
    ['MARQUEUR', 'Siege {{adresse}}.', '{{adresse}}'],
    ['MARQUEUR', '\u25b6 ASSOCIES', '\u25b6'],
    ['BLANC_POINTILLE', 'Fait a ............, le 12/05/2026', '........'],
    ['BLANC_POINTILLE', 'Numero RC : \u2026\u2026\u2026', '\u2026\u2026'],
    ['BLANC_POINTILLE', 'Signature : ________', '____'],
    ['BLANC_PHRASE', 'M. Ahmed ALAOUI, n\u00e9 le , \u00e0 Casablanca.', 'n\u00e9 le ,'],
    ['BLANC_PHRASE', 'n\u00e9 le 12/05/1980 \u00e0 , demeurant \u00e0 Rabat.', '\u00e0 ,'],
    ['CHAMP_VIDE', 'Nom : , Prenom : Ahmed', 'Nom : ,'],
  ];
  for (const [genre, ligne, attendu] of cas) {
    test(`${genre} est signale : "${ligne}"`, () => {
      const a = analyserDocument(docx([...SAIN, ligne]));
      const d = a.defauts.filter((x) => x.genre === genre);
      expect(d.length, JSON.stringify(a.defauts)).toBeGreaterThan(0);
      expect(d[0].ligne).toBe(SAIN.length + 1);
      expect(d[0].extrait).toContain(attendu);
      expect(rapportDocument('acte.docx', a)).toContain(genre);
    });
  }

  test('CHAMP_VIDE : deux libelles consecutifs sans valeur ; un libelle suivi d une liste ne l est pas', () => {
    const vide = analyserTexte(['Denomination :', 'Forme juridique :', 'SARL']);
    expect(vide.defauts.map((d) => d.genre)).toEqual(['CHAMP_VIDE']);
    expect(vide.defauts[0].extrait).toBe('Denomination : | Forme juridique :');
    expect(analyserTexte(['Ont comparu :', 'M. Ahmed ALAOUI']).defauts).toEqual([]);
  });

  test('le marqueur A OBTENIR d une variable externe est PERMIS, et rendu a part', () => {
    const a = analyserDocument(docx([...SAIN, 'Immatriculee au RC sous le numero \u2039 \u00c0 OBTENIR : RC_NUMERO \u203a.']));
    expect(a.defauts).toEqual([]);
    expect(a.aObtenir).toEqual(['RC_NUMERO']);
  });

  test('les en-tetes sont lus', () => {
    const lignes = texteDocx(docx(SAIN, 'Ref ${REFERENCE}'));
    expect(lignes).toContain('Ref ${REFERENCE}');
    expect(analyserDocument(docx(SAIN, 'Ref ${REFERENCE}')).defauts[0].genre).toBe('MARQUEUR');
  });

  test('une exception nommee du gabarit neutralise seulement son texte', () => {
    const ligne = 'Signature du gerant : ____________';
    const a = analyserDocument(docx([...SAIN, ligne]), [{ texte: /Signature du gerant : _+/, motif: 'ligne de signature manuscrite du gabarit' }]);
    expect(a.defauts).toEqual([]);
    const b = analyserDocument(docx([...SAIN, ligne, 'Visa : ____']), [{ texte: /Signature du gerant : _+/, motif: 'temoin' }]);
    expect(b.defauts.map((d) => d.genre)).toEqual(['BLANC_POINTILLE']);
  });

  function pdf(contenu: string, compresse = true): Buffer {
    const flux = compresse ? deflateSync(Buffer.from(contenu, 'latin1')) : Buffer.from(contenu, 'latin1');
    return Buffer.concat([
      Buffer.from(`%PDF-1.4\n1 0 obj\n<< /Length ${flux.length}${compresse ? ' /Filter /FlateDecode' : ''} >>\nstream\n`, 'latin1'),
      flux,
      Buffer.from('\nendstream\nendobj\n%%EOF\n', 'latin1'),
    ]);
  }

  test('PDF : texte des operateurs Tj / TJ extrait, defaut signale', () => {
    const a = analyserDocument(pdf('BT /F1 12 Tf 72 700 Td (Contrat d\'abonnement du cabinet Demo) Tj ET\nBT [(Ville : [non renseign) -20 (\\351 : ville]) ] TJ ET'));
    expect(a.genre).toBe('pdf');
    expect(a.lisible).toBe(true);
    expect(a.defauts.map((d) => d.genre)).toEqual(['A_COMPLETER']);
  });

  test('PDF illisible (police composite) : le rapport le dit, jamais "sain"', () => {
    const a = analyserDocument(pdf('BT /F1 12 Tf <0012003400560078> Tj ET'));
    expect(a.lisible).toBe(false);
    expect(rapportDocument('acte.pdf', a)).toContain('illisible');
  });
});

test.describe('controle des documents : PDF a police composite', () => {
  // Police composite (PDFBox PDType0Font, LibreOffice) : le contenu ne porte que des numeros de
  // glyphes sur 2 octets ; la table ToUnicode du fichier les traduit.
  const CMAP = '/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n'
    + '2 beginbfchar\n<0001> <0056>\n<0002> <0020>\n endbfchar\n'
    + '1 beginbfrange\n<0010> <0029> <0041>\nendbfrange\nendcmap end end';
  // V(0001) espace(0002) puis A..Z = 0010..0029
  const code = (texte: string) => [...texte].map((c) => (c === 'V' ? '0001' : c === ' ' ? '0002'
    : (0x10 + c.charCodeAt(0) - 65).toString(16).padStart(4, '0'))).join('');
  // Deux polices (F1, F2) aux tables differentes, comme les sous-ensembles Inter regular et gras
  // d'une fiche PDFBox : chaque police doit etre lue avec SA table (une fusion melangerait).
  const CMAP_GRAS = CMAP.replace('<0010> <0029> <0041>', '<0010> <0029> <0061>');
  function pdfComposite(texte: string, gras = ''): Buffer {
    const obj = (n: number, dico: string, flux?: string) => {
      if (flux === undefined) return Buffer.from(`${n} 0 obj\n${dico}\nendobj\n`, 'latin1');
      const f = deflateSync(Buffer.from(flux, 'latin1'));
      return Buffer.concat([Buffer.from(`${n} 0 obj\n<< /Length ${f.length} /Filter /FlateDecode >>\nstream\n`, 'latin1'), f,
        Buffer.from('\nendstream\nendobj\n', 'latin1')]);
    };
    return Buffer.concat([
      Buffer.from('%PDF-1.7\n', 'latin1'),
      obj(1, '', CMAP),
      obj(2, '<< /Type /Font /Subtype /Type0 /ToUnicode 1 0 R >>'),
      obj(3, '', CMAP_GRAS),
      obj(4, '<< /Type /Font /Subtype /Type0 /ToUnicode 3 0 R >>'),
      obj(5, '<< /Type /Page /Resources << /Font << /F1 2 0 R /F2 4 0 R >> >> /Contents 6 0 R >>'),
      obj(6, '', `BT /F1 12 Tf <${code(texte)}> Tj ET${gras ? `\nBT /F2 14 Tf <${code(gras)}> Tj ET` : ''}`),
      Buffer.from('%%EOF\n', 'latin1'),
    ]);
  }

  test('deux polices composites : chacune lue avec sa table', () => {
    const a = analyserDocument(pdfComposite('PROCES VERBAL DE LA DECISION', 'IDENTIFIANTS DE LA SOCIETE'));
    expect(a.lisible).toBe(true);
    expect(textePdf(pdfComposite('PROCES VERBAL', 'TITRE'))).toEqual(['PROCES VERBAL', 'titre']);
  });

  test('chaine litterale sous police composite (PDFBox) : codes de glyphes decodes par la table', () => {
    const litt = [...code('CNSS')].reduce((acc, _c, i, t) => (i % 2 === 0 ? acc + String.fromCharCode(parseInt(t[i] + t[i + 1], 16)) : acc), '');
    const echappe = litt.replace(/[\\()]/g, (c) => `\\${c}`);
    const b = Buffer.concat([pdfComposite('PROCES VERBAL DE LA DECISION DE L ASSOCIE'),
      Buffer.from(`7 0 obj\n<< /Length 0 >>\nstream\nBT /F1 7 Tf (${echappe}) Tj ET\nendstream\nendobj\n`, 'latin1')]);
    expect(textePdf(b)).toContain('CNSS');
  });

  test('ligne brouillee (glyphes lus comme des octets) : document declare illisible', () => {
    const a = analyserTexte(['PROCES VERBAL DE LA DECISION DE L ASSOCIE UNIQUE', ' = B : ) = ] ) B = Q W X']);
    expect(a.lisible).toBe(false);
  });

  test('le texte est decode par la table ToUnicode ; le defaut est signale', () => {
    const a = analyserDocument(pdfComposite('VALEUR MANQUANTE POUR LE GERANT DE LA SOCIETE'));
    expect(a.lisible).toBe(true);
    expect(a.defauts.map((d) => d.genre)).toEqual(['VALEUR_MANQUANTE']);
  });

  test('document composite sain : lisible, aucun defaut', () => {
    const a = analyserDocument(pdfComposite('PROCES VERBAL DE LA DECISION DE L ASSOCIE UNIQUE'));
    expect(a.lisible).toBe(true);
    expect(a.defauts).toEqual([]);
  });
});

test('code TOTP : vecteur de la RFC 6238 (SHA-1, T = 59 s)', () => {
  // Secret ASCII "12345678901234567890" en base 32 ; code a 8 chiffres 94287082, donc 287082 a 6.
  expect(codeTotp('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', 59_000)).toBe('287082');
});
