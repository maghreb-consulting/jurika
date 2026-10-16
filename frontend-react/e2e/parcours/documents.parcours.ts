import { test, expect } from './fixtures';
import { API_URL } from './outils/env';
import { analyserDocument, rapportDocument } from './outils/documents';

/**
 * Lot L6b (E13) : documents produits, telecharges par l'API avec le jeton de la session, lus et
 * controles (outils/documents.ts : "VALEUR MANQUANTE", marqueur non remplace, blanc de
 * remplissage, champ vide ; "A OBTENIR" permis pour une variable externe).
 *
 * Fiches client et dossier (PDF de PDFBox, sans corpus) : tournent partout, CI comprise.
 * Un test d'acte genere depuis le corpus REEL du cabinet portera l'etiquette @corpus-reel
 * (exclu de la CI par --grep-invert @corpus-reel, lance sur le Z440) : non ecrit a ce lot
 * (parcours de generation, etapes 6 et 7, a construire sur la pile redeployee).
 */
for (const fiche of ['fiche-client', 'fiche-dossier'] as const) {
  test(`documents ${fiche} PDF : aucun blanc, aucun marqueur`, async ({ page, entrer, aller, comptes }, info) => {
    const s = await entrer('EMPLOYE');
    const cabinet = await comptes.cabinet('EMPLOYE');
    await aller('/data-rooms');
    const r = await page.request.post(`${API_URL}/dataroom/dossiers/${cabinet.dossierId}/juridique/${fiche}/pdf`, {
      headers: { Authorization: `Bearer ${s.accessToken}` },
    });
    expect(r.status(), `POST ${fiche}/pdf`).toBe(200);
    const corps = await r.body();
    await info.attach(`${fiche}.pdf`, { body: corps, contentType: 'application/pdf' });
    const a = analyserDocument(corps);
    expect(a.genre).toBe('pdf');
    expect(rapportDocument(`${fiche}.pdf`, a)).toBeNull();
  });
}

