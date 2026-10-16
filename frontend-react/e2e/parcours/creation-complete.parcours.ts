import fs from 'node:fs';
import path from 'node:path';
import type { Locator, Page, Response } from '@playwright/test';
import { test, expect } from './fixtures';
import { API_URL } from './outils/env';
import { analyserDocument, rapportDocument, texteDocx, textePdf, type Permis } from './outils/documents';
import { calme, champ, pdfFictif, valider } from './outils/saisie';
import {
  KARIM, NADIA, SALMA, etape1, etape2, etape3, etape4, etape5, etape6, nouveauTicketCreation,
  tolererPremiereOuverture, type Forme,
} from './outils/creation';

/**
 * Lot L6b (E14) : CREATION d'une SARL et d'une SARL AU, du statut 1 a la cloture, dans le
 * navigateur, sur la pile reelle, comme un employe : saisies des etapes 1 a 9 (CIN lues par
 * l'assistant puis verifiees a la main), generation de chaque acte a l'etape 7 (apercu ouvert,
 * document telecharge et lu, depot en Data Room), pieces jointes, synthese ; demarches cochees
 * avec leurs justificatifs, statut apres statut ; identifiants obtenus des organismes saisis
 * dans la fiche de la societe (RC, IF, TP, CNSS), date du depot legal saisie a l'etape 7 et
 * actes regeneres d'office ; cloture ; fiche client et fiche dossier en PDF lues.
 *
 * @corpus-reel : l'etape 7 genere les actes du corpus REEL du cabinet (le corpus fictif de CI
 * n'a aucun modele de creation). Les etapes 1 a 5 sans corpus restent couvertes en CI par
 * creation.parcours.ts.
 *
 * Donnees fictives seulement (personnes, numeros, adresses, images et PDF generes par le test).
 */
const DOSSIER_DOCS = path.join(process.env.E2E_CAPTURES ?? 'test-results/parcours', 'documents');

/** Textes du gabarit lui-meme, permis et justifies (jamais une donnee absente). */
const PERMIS: Permis[] = [
  { texte: /\s\.{10,}\s(?=\d+ parts)/, motif: 'points de conduite du tableau de repartition des parts (gabarit des statuts)' },
  { texte: /(?:attribu\u00e9e?|num\u00e9ro de d\u00e9p\u00f4t|date de d\u00e9p\u00f4t|date de r\u00e9ception|num\u00e9ro d'enregistrement)\s+\.{8,}/i, motif: 'cadre reserve a l\u2019administration de l\u2019imprime officiel (rempli par le service)' },
];

const COMPLEMENTS: Record<string, string> = {
  bailDuree: '3 ans', bailLoyerChiffres: '8000', bailLoyerPeriodicite: 'mois',
  bailLoyerModalites: 'payable d\u2019avance le premier jour de chaque mois', bailDepotGarantie: 'deux mois de loyer',
  bailDestination: 'bureaux administratifs', bailFraisCharge: 'preneur', bailFranchise: 'aucune', bailSousLocation: 'interdite',
  bailTravauxPreneur: 'am\u00e9nagements int\u00e9rieurs avec l\u2019accord \u00e9crit du bailleur', bailTribunal: 'Casablanca',
  bailTitreFoncier: '12345/C', bailTitreFoncierNom: 'D\u00e9monstration',
  bailleurType: 'personne physique', bailleurCivilite: 'M.', bailleurPrenom: 'Omar', bailleurNom: 'Bennani',
  bailleurNationalite: 'marocaine', bailleurAdresse: '3 rue des Essais, Casablanca', bailleurPieceType: 'CIN', bailleurPieceNumero: 'BH778899',
  pouvoirMandant: 'associ\u00e9s', pouvoirMandataireType: 'personne physique', pouvoirMandataireCivilite: 'M.',
  pouvoirMandatairePrenom: 'Mehdi', pouvoirMandataireNom: 'Alami', pouvoirMandataireNationalite: 'marocaine',
  pouvoirMandataireAdresse: '20 avenue des Tests, Casablanca', pouvoirMandatairePieceType: 'CIN', pouvoirMandatairePieceNumero: 'BB445566',
  actesEnFormationMandat: 'oui', actesEnFormationMandatObjet: 'la signature du bail des locaux du si\u00e8ge',
  tpCommune: 'Casablanca', tpObjet: 'Personne morale', directionRegionale: 'Direction r\u00e9gionale des imp\u00f4ts de Casablanca',
  subdivision: 'Casablanca-Anfa', effectifPrevisionnel: '3', telephone: '0522000000', email: 'contact@atlas-parcours.test',
  activiteNature: 'Prestation de services', regimeResultat: 'Imp\u00f4t sur les soci\u00e9t\u00e9s \u2014 r\u00e9gime du r\u00e9sultat net r\u00e9el',
  tvaAssujettissement: 'Assujetti \u00e0 titre obligatoire', tvaFaitGenerateur: 'R\u00e9gime de l\u2019encaissement',
  tvaPeriodicite: 'D\u00e9claration trimestrielle', dateDebutActivite: '2026-11-01', banqueAgence: 'Agence Casablanca Centre',
};

const BOUCLES: Record<string, Record<string, string>[]> = {
  BAIL_LOCAUX: [{ bailLocalAdresse: '12 rue de la D\u00e9monstration, quartier des Affaires', bailLocalDesignation: 'un plateau de bureaux au deuxi\u00e8me \u00e9tage', bailLocalSuperficie: '120' }],
  ACTES_EN_FORMATION: [{ acteFormationDate: '2026-10-05', acteFormationNature: 'contrat de bail', acteFormationCocontractant: 'M. Omar Bennani', acteFormationObjet: 'location des locaux du si\u00e8ge', acteFormationMontant: '8000', acteFormationAuteur: 'M. Karim Benali', acteFormationEcheance: 'mensuelle' }],
};

/** Ce que les identifiants obtenus des organismes donnent a la societe. */
const IDENTIFIANTS = { rc: '654321', if: '45678912', tp: '34567891', cnss: '9876543' };

interface Lu { titre: string; fichier: string; lignes: string[]; aObtenir: string[] }

function sauver(nom: string, contenu: Buffer): string {
  fs.mkdirSync(DOSSIER_DOCS, { recursive: true });
  const f = path.join(DOSSIER_DOCS, nom.replace(/[^a-zA-Z0-9._-]+/g, '_'));
  fs.writeFileSync(f, contenu);
  return f;
}

/** Document lu et controle : aucun defaut (permis du gabarit exceptes), texte lisible. */
function controler(nom: string, contenu: Buffer, info: { attach: (n: string, o: { body: Buffer; contentType: string }) => Promise<void> }): Lu & { promesse: Promise<void> } {
  const fichier = sauver(nom, contenu);
  const a = analyserDocument(contenu, PERMIS);
  expect(rapportDocument(nom, a), `document ${nom}`).toBeNull();
  const lignes = a.genre === 'docx' ? texteDocx(contenu) : textePdf(contenu);
  return {
    titre: nom, fichier, lignes, aObtenir: a.aObtenir,
    promesse: info.attach(nom, { body: contenu, contentType: a.genre === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }),
  };
}

/** Carte d'un document de l'etape 7 par son titre. */
function carte(page: Page, titre: string): Locator {
  return page.locator('main div.rounded-xl').filter({ has: page.getByRole('heading', { level: 4, name: titre, exact: true }) }).last();
}

async function attendre(page: Page, motif: (r: Response) => boolean, action: () => Promise<void>, quoi: string): Promise<Response> {
  const rep = page.waitForResponse(motif, { timeout: 90_000 });
  await action();
  const r = await rep;
  expect(r.status(), quoi).toBeLessThan(400);
  return r;
}

/** Telechargement Word de la carte, lu et controle. */
async function telecharger(page: Page, c: Locator, titre: string, info: Parameters<typeof controler>[2]): Promise<Lu> {
  const dl = page.waitForEvent('download');
  await c.getByRole('button', { name: 'T\u00e9l\u00e9charger (Word)' }).click();
  const d = await dl;
  const tmp = await d.path();
  const lu = controler(d.suggestedFilename() || `${titre}.docx`, fs.readFileSync(tmp), info);
  await lu.promesse;
  return lu;
}

/** Etape 7 : documents retenus, signature, complements ; chaque acte genere, apercu, lu, depose. */
async function etape7(page: Page, info: Parameters<typeof controler>[2]): Promise<Map<string, Lu>> {
  for (const c of await page.locator('main input[type="checkbox"]').all()) {
    if (!(await c.isChecked()) && (await c.isEnabled())) await c.check();
  }
  await (await champ(page, 'Lieu de signature (ville)')).fill('Casablanca');
  await (await champ(page, 'Date de signature')).fill('2026-10-20');
  for (let i = 0; i < 10; i++) {
    const b = page.getByRole('button', { name: 'Confirmer', exact: true }).first();
    if (!(await b.isVisible().catch(() => false))) break;
    await b.click();
  }
  for (const [cle, valeur] of Object.entries(COMPLEMENTS)) {
    const c = page.locator(`[id="complement-${cle}"]`);
    if (!(await c.count())) continue;
    if ((await c.evaluate((e) => e.tagName)) === 'SELECT') {
      const opts = await c.locator('option').allInnerTexts();
      await c.selectOption({ label: opts.find((t) => t.replace(/\u2019/g, '\'') === valeur.replace(/\u2019/g, '\'')) ?? valeur });
    } else await c.fill(valeur);
  }
  for (const [nom, lignes] of Object.entries(BOUCLES)) {
    const bloc = page.getByTestId(`boucle-${nom}`);
    if (!(await bloc.count())) continue;
    for (const [i, ligne] of lignes.entries()) {
      if (!(await page.locator(`[id^="boucle-${nom}-${i}-"]`).count())) await bloc.getByRole('button', { name: 'Ajouter', exact: true }).click();
      for (const [cle, v] of Object.entries(ligne)) {
        const c = page.locator(`[id="boucle-${nom}-${i}-${cle}"]`);
        if (await c.count()) await c.fill(v);
      }
    }
  }
  await calme(page);

  const lus = new Map<string, Lu>();
  const titres = (await page.locator('main h4').allInnerTexts()).map((t) => t.trim());
  for (const titre of titres) {
    const c = carte(page, titre);
    const generer = c.getByRole('button', { name: 'G\u00e9n\u00e9rer', exact: true });
    if (!(await generer.isVisible().catch(() => false))) continue;
    await attendre(page, (r) => /\/ai\/workflows\/CREATION_SARL\/documents\//.test(r.url()) && r.request().method() === 'POST',
      () => generer.click(), `generation de "${titre}"`);
    await expect(c.getByRole('button', { name: 'T\u00e9l\u00e9charger (Word)' }), `"${titre}" genere`).toBeVisible({ timeout: 60_000 });
    await calme(page);
    // Apercu plein ecran : le document s'ouvre et se lit.
    await c.getByRole('button', { name: 'Plein \u00e9cran' }).click();
    const apercu = page.getByRole('dialog');
    await expect(apercu, `apercu de "${titre}"`).toBeVisible();
    await expect.poll(async () => (await apercu.innerText()).replace(/\s+/g, ' ').length, { message: `apercu de "${titre}" non vide` }).toBeGreaterThan(200);
    await apercu.getByRole('button', { name: 'Fermer' }).first().click();
    await expect(apercu).toBeHidden();
    lus.set(titre, await telecharger(page, c, titre, info));
    // Depot en Data Room.
    await attendre(page, (r) => /valider-brouillon|\/juridique\/(documents|upload)/.test(r.url()) && r.request().method() === 'POST',
      () => c.getByRole('button', { name: 'Valider et d\u00e9poser en Data Room' }).click(), `depot de "${titre}"`);
    await expect(c.getByText('Valid\u00e9', { exact: true }), `"${titre}" valide`).toBeVisible();
  }
  expect(lus.size, 'au moins les statuts et l\u2019annonce legale').toBeGreaterThanOrEqual(2);
  return lus;
}

/** Etape 8 : certificat negatif importe, piece complementaire ajoutee et importee. */
async function etape8(page: Page): Promise<void> {
  const zone = (libelle: string) => page.locator('main div.border-2').filter({ has: page.getByText(libelle, { exact: true }) }).last();
  const importer = async (libelle: string, fichier: string) => {
    const choix = page.waitForEvent('filechooser');
    await zone(libelle).locator('label').filter({ hasText: 'Importer le document' }).click();
    await (await choix).setFiles({ name: fichier, mimeType: 'application/pdf', buffer: pdfFictif() });
    await calme(page);
    await expect(zone(libelle).getByText(fichier)).toBeVisible();
  };
  await importer('Certificat n\u00e9gatif', 'certificat-negatif.pdf');
  await page.getByRole('textbox', { name: 'Libell\u00e9 d\u2019une pi\u00e8ce compl\u00e9mentaire' }).fill('Attestation bancaire');
  await page.getByRole('button', { name: 'Ajouter la pi\u00e8ce' }).click();
  await importer('Attestation bancaire', 'attestation-bancaire.pdf');
  await valider(page);
}

/** Etape 9 : synthese, puis \u00ab Finaliser le dossier \u00bb. */
async function etape9(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: /^Synth\u00e8se/ })).toBeVisible();
  await attendre(page, (r) => r.url().includes('/execute-step') && r.request().method() === 'POST',
    () => page.getByRole('button', { name: 'Finaliser le dossier' }).click(), 'finalisation');
  await expect(page.getByText(/parcours termin\u00e9/).first()).toBeVisible();
  await calme(page);
}

/** Identifiants obtenus des organismes, saisis dans la fiche de la societe (Data Room). */
async function saisirIdentifiants(page: Page, aller: (c: string) => Promise<void>, dossierId: string): Promise<void> {
  await aller(`/data-rooms?dossier=${dossierId}`);
  await page.getByTestId('open-identifiants').click();
  const t = page.getByRole('dialog');
  await expect(t.getByRole('heading', { name: 'Identifiants de la soci\u00e9t\u00e9' })).toBeVisible();
  // Le capital et le siege viennent du parcours (etapes 2 et 3) : rien a ressaisir.
  await expect(t.getByLabel('Capital social (MAD)', { exact: true }), 'capital repris de l\u2019etape 3').not.toHaveValue('');
  await expect(t.getByLabel('Adresse du si\u00e8ge', { exact: true }), 'siege repris de l\u2019etape 2').not.toHaveValue('');
  await expect(t.getByLabel('ICE', { exact: true }), 'ICE repris de l\u2019etape 1').not.toHaveValue('');
  await t.getByLabel('Num\u00e9ro RC', { exact: true }).fill(IDENTIFIANTS.rc);
  await t.getByLabel('Identifiant fiscal (IF)', { exact: true }).fill(IDENTIFIANTS.if);
  await t.getByLabel('Taxe professionnelle (patente)', { exact: true }).fill(IDENTIFIANTS.tp);
  await t.getByLabel('Date d\u2019effet de la taxe professionnelle', { exact: true }).fill('2026-11-10');
  await t.getByLabel('CNSS', { exact: true }).fill(IDENTIFIANTS.cnss);
  await attendre(page, (r) => r.url().endsWith(`/dossiers/${dossierId}/identifiants`) && r.request().method() === 'PATCH',
    () => t.getByRole('button', { name: 'Enregistrer' }).click(), 'identifiants de la societe');
  await calme(page);
}

/**
 * Donnee attendue d'un organisme hors fiche (date du depot legal) saisie sur la carte de l'acte,
 * a l'etape 7 du parcours termine ; l'acte se regenere d'office quand tout est arrive.
 */
async function completerEtRegenerer(page: Page, aller: (c: string) => Promise<void>, ticketId: string, titre: string,
  info: Parameters<typeof controler>[2]): Promise<Lu> {
  await aller(`/workflows/${ticketId}`);
  await page.getByRole('button', { name: /^\u00c9tape 7 : G\u00e9n\u00e9ration des actes/ }).click();
  await calme(page);
  const c = carte(page, titre);
  // Le RC vient d'etre saisi dans la fiche : il est "Recu" ; la date du depot legal est reclamee ici.
  await expect(c.getByText('Immatriculation au registre du commerce'), `"${titre}" : RC attendu`).toBeVisible({ timeout: 30_000 });
  const date = c.getByRole('textbox', { name: 'Date du d\u00e9p\u00f4t l\u00e9gal au greffe' });
  await expect(date, `"${titre}" reclame la date du depot legal`).toBeVisible({ timeout: 30_000 });
  await date.fill('2026-11-05');
  const regeneration = page.waitForResponse((r) => /\/ai\/workflows\/CREATION_SARL\/documents\//.test(r.url()) && r.request().method() === 'POST', { timeout: 90_000 });
  await attendre(page, (r) => /donnees-attendues/.test(r.url()) && r.request().method() !== 'GET',
    () => c.getByRole('button', { name: 'Enregistrer' }).click(), `date du depot legal (${titre})`);
  expect((await regeneration).status(), `regeneration de "${titre}"`).toBeLessThan(400);
  await calme(page);
  await expect(c.getByText('Donn\u00e9es attendues d\u2019un organisme'), `"${titre}" n'attend plus rien`).toBeHidden({ timeout: 60_000 });
  return telecharger(page, c, `${titre}-regenere`, info);
}

/** Demarches du statut courant : justificatifs (PDF fictifs) puis \u00ab Cocher \u00bb, une par une. */
async function cocherDemarches(page: Page): Promise<number> {
  // Les demarches du statut courant offrent "Cocher" (panneau recharge apres un passage).
  await expect(page.locator('li[data-testid^="demarche-"]').getByRole('button', { name: 'Cocher', exact: true }).first(),
    'demarches actionnables du statut courant').toBeVisible({ timeout: 30_000 });
  let n = 0;
  for (let garde = 0; garde < 80; garde++) {
    const li = page.locator('li[data-testid^="demarche-"]').filter({ has: page.getByRole('button', { name: 'Cocher', exact: true }) }).first();
    if (!(await li.isVisible().catch(() => false))) break;
    const titre = (await li.locator('p').first().innerText()).trim();
    const groupes = li.locator('div.flex-wrap').filter({ has: page.locator('input[type="file"]') });
    for (let g = 0; g < await groupes.count(); g++) {
      const choix = page.waitForEvent('filechooser');
      await groupes.nth(g).locator('label').first().click();
      await (await choix).setFiles({ name: `justificatif-${titre.slice(0, 20)}-${g}.pdf`, mimeType: 'application/pdf', buffer: pdfFictif() });
    }
    await attendre(page, (r) => /\/demarches\/\d+\/cocher$/.test(r.url()) && r.request().method() === 'POST',
      () => li.getByRole('button', { name: 'Cocher', exact: true }).click(), `cochage de "${titre}"`);
    await calme(page);
    n++;
  }
  return n;
}

/** Passage au statut suivant, sans recharger la page (les obstacles se relisent apres chaque cochage). */
async function passer(page: Page, vers: string): Promise<void> {
  const bouton = page.getByRole('button', { name: `Passer \u00e0 \u00ab ${vers} \u00bb` });
  await expect(page.getByTestId('passage-possible'), `rien ne s'oppose au passage vers ${vers}`).toBeVisible({ timeout: 30_000 });
  await attendre(page, (r) => /\/tickets\/[0-9a-f-]{36}\/transition$/.test(r.url()) && r.request().method() === 'POST',
    () => bouton.click(), `passage vers ${vers}`);
  await calme(page);
}

async function api<T>(page: Page, chemin: string): Promise<T> {
  const jeton = await page.evaluate(() => window.localStorage.getItem('jurika_access_token'));
  const r = await page.request.get(`${API_URL}${chemin}`, { headers: { Authorization: `Bearer ${jeton}` } });
  expect(r.status(), `GET ${chemin}`).toBe(200);
  return (await r.json()) as T;
}

const CAS: { forme: Forme; libelle: string; denomination: string }[] = [
  { forme: 'SARL', libelle: 'SARL a deux associes et deux gerants', denomination: 'Atlas Parcours SARL' },
  { forme: 'SARL_AU', libelle: 'SARL AU, associe unique gerant', denomination: 'Atlas Unique SARL AU' },
];

for (const cas of CAS) {
  test(`parcours creation complete ${cas.libelle} : statut 1 a la cloture, actes et fiches @corpus-reel`, async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    test.setTimeout(1_200_000);
    tolererPremiereOuverture(sentinelle);
    await entrer('EMPLOYE');
    const sarl = cas.forme === 'SARL';
    const nom = sarl ? 'creation-complete-sarl' : 'creation-complete-sarl-au';

    // ---- Statut 1 : ticket et parcours, etapes 1 a 6 -----------------------
    const ticketId = await nouveauTicketCreation(page, aller, { titre: `Constitution ${cas.denomination}`, raison: cas.denomination.toUpperCase(), forme: cas.forme });
    await etape1(page, cas.denomination);
    await etape2(page);
    await etape3(page);
    await etape4(page);
    // SARL : deux gerants aux remunerations differentes (le 400 corrige en bd03546).
    expect(await etape5(page, sarl ? [KARIM, SALMA] : [KARIM])).toMatch(/Associ/);
    expect(await etape6(page, sarl ? [NADIA] : [], sarl ? [60, 40] : [100])).toMatch(/G\u00e9n\u00e9ration/);

    // ---- Etape 7 : chaque acte genere, ouvert, lu, depose --------------------
    const lus = await etape7(page, info);
    const texte = (t: string) => (lus.get(t)?.lignes ?? []).join('\n');
    const statuts = [...lus.keys()].find((t) => /^Statuts/.test(t)) as string;
    expect(texte(statuts)).toContain(cas.denomination);
    expect(texte(statuts)).toContain('Karim');
    if (sarl) {
      expect(texte(statuts)).toContain('El Idrissi');
      expect(texte(statuts)).toContain('Salma');
    }
    const rc = [...lus.keys()].find((t) => /immatriculation au RC/.test(t));
    if (rc) {
      // Section 6 de la declaration : chaque associe une fois (defaut corrige au lot : le premier
      // associe sortait deux fois, le second jamais).
      const t = texte(rc);
      expect(t.match(/Karim Benali, n\u00e9/g)?.length ?? 0, 'Karim Benali une fois en associe').toBe(1);
      if (sarl) expect(t).toContain('Nadia El Idrissi, n\u00e9');
      expect(t).toMatch(/\u2612 Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e/);
    }
    const annonce = [...lus.keys()].find((t) => /^Annonce/.test(t)) as string;
    expect(lus.get(annonce)?.aObtenir, 'l\u2019annonce attend le RC et la date du depot legal').toEqual(expect.arrayContaining(['RC_NUMERO']));
    await capturer(`${nom}-etape-7`);
    await valider(page);

    // ---- Etapes 8 et 9 ------------------------------------------------------
    await etape8(page);
    await etape9(page);

    // ---- Ticket : demarches et statuts -------------------------------------
    const ticket = await api<{ dossierId: string }>(page, `/tickets/${ticketId}`);
    await aller(`/tickets/${ticketId}`);
    expect(await cocherDemarches(page), 'demarches du statut 1').toBeGreaterThan(0);
    await passer(page, 'G\u00e9n\u00e9ration des documents');
    expect(await cocherDemarches(page), 'demarches du statut 2').toBeGreaterThan(0);
    await passer(page, 'D\u00e9roulement de la d\u00e9marche');

    // ---- Donnees attendues d'un organisme : fiche de la societe, puis date du depot legal ----
    await saisirIdentifiants(page, aller, ticket.dossierId);
    const annonceRegeneree = await completerEtRegenerer(page, aller, ticketId, annonce, info);
    expect(annonceRegeneree.aObtenir, 'annonce regeneree complete').toEqual([]);
    expect(annonceRegeneree.lignes.join('\n')).toContain(IDENTIFIANTS.rc);
    await capturer(`${nom}-regeneration`);

    await aller(`/tickets/${ticketId}`);
    expect(await cocherDemarches(page), 'demarches du statut 3').toBeGreaterThan(0);
    await passer(page, 'Cl\u00f4ture de dossier');
    await expect(page.getByText('Ticket clos : lecture seule.')).toBeVisible();

    // ---- Fiche de la societe : fiche client et fiche dossier en PDF ---------
    await aller(`/data-rooms?dossier=${ticket.dossierId}`);
    for (const f of ['client', 'dossier'] as const) {
      await page.getByRole('button', { name: `Fiche ${f}`, exact: true }).click();
      const t = page.getByRole('dialog');
      await expect(t.getByRole('button', { name: `T\u00e9l\u00e9charger la fiche ${f} (PDF)` })).toBeVisible();
      const dl = page.waitForEvent('download');
      await t.getByRole('button', { name: `T\u00e9l\u00e9charger la fiche ${f} (PDF)` }).click();
      const d = await dl;
      const lu = controler(`fiche-${f}-${nom}.pdf`, fs.readFileSync(await d.path()), info);
      await lu.promesse;
      const tout = lu.lignes.join('\n');
      expect(tout, `fiche ${f} : aucune donnee manquante`).not.toMatch(/Donn\u00e9e manquante|Non renseign/);
      // ICE saisi a l'etape 1 : la donnee, quelle que soit la mise en forme (groupes de chiffres ou non).
      expect(tout, `fiche ${f} : ICE de l'etape 1`).toMatch(/001\s?234\s?567\s?000\s?089/);
      for (const v of [IDENTIFIANTS.rc, IDENTIFIANTS.if, IDENTIFIANTS.tp, IDENTIFIANTS.cnss, '10 000', 'D\u00e9monstration']) {
        expect(tout, `fiche ${f} : ${v}`).toContain(v);
      }
      await t.getByRole('button', { name: 'Fermer' }).first().click();
    }
    await capturer(nom);
  });
}
