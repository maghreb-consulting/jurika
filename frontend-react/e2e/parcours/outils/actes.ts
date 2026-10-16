import { expect, type Locator, type Page, type TestInfo } from '@playwright/test';
import { API_URL } from './env';
import { analyserDocument, rapportDocument, texteDocx, type Analyse, type Permis } from './documents';
import type { Sentinelle } from './sentinelle';

/**
 * Lot L6b (E15) : gestes communs des parcours d'actes (reprise de dossier, modification,
 * approbation des comptes, succursale, dissolution et liquidation, incident d'assemblee,
 * annulation et reprise d'un ticket), joues COMME UN UTILISATEUR : clics et saisies au
 * clavier dans la page, fichiers fictifs generes ici (PDF minimal). Les selecteurs viennent
 * des parcours faits a l'ecran sur le Z440 (pilote d'exploration, lot L6b E15).
 */

/** Attend que le reseau se calme (les ecrans chargent en plusieurs vagues), borne a 15 s. */
export async function calme(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
}

/**
 * Premiere ouverture d'un parcours : le front lit le parcours (404 tant qu'il n'est pas
 * demarre) puis le demarre (useWorkflow). Reponse attendue, toleree nommement.
 */
export function tolererDemarrageParcours(s: Sentinelle): void {
  s.tolerer({
    methode: 'GET', chemin: /^\/workflows\/[0-9a-f-]{36}$/, statut: 404,
    motif: 'premiere ouverture du parcours : lecture avant demarrage (useWorkflow, puis POST /start)',
  });
}

/** PDF minimal fictif (aucune donnee reelle). */
export function pdfFictif(): Buffer {
  return Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n'
    + 'trailer << /Root 1 0 R >>\n%%EOF\n', 'latin1');
}

/** Champ de la zone principale par le debut de son libelle (n-ieme occurrence). */
export async function champ(page: Page, debut: string, n = 0, exact = false, zone = 'main'): Promise<Locator> {
  // Les etapes montent apres leurs lectures (squelette de chargement) : on attend le champ.
  const lire = () => page.evaluate(([d, ex, z]) => {
    const ok = (t: string) => (ex ? t.replace(/\s*\*$/, '') === d : t.startsWith(d));
    const racine = document.querySelector(z as string) ?? document.body;
    const parLibelle = Array.from(racine.querySelectorAll('label'))
      .filter((x) => ok((x.textContent ?? '').trim()) && (x as HTMLLabelElement).htmlFor)
      .map((x) => (x as HTMLLabelElement).htmlFor);
    const parAria = Array.from(racine.querySelectorAll('input, textarea, select'))
      .filter((x) => ok(x.getAttribute('aria-label') ?? '') && x.id)
      .map((x) => x.id);
    return [...new Set([...parLibelle, ...parAria])];
  }, [debut, exact, zone] as const);
  await expect.poll(async () => (await lire()).length, { message: `champ introuvable : ${debut} (occurrence ${n + 1})`, timeout: 20_000 })
    .toBeGreaterThan(n);
  const ids = await lire();
  return page.locator(`[id="${ids[n]}"]`);
}

/** Champ facultatif : null s'il n'est pas a l'ecran. */
export async function champSiPresent(page: Page, debut: string, n = 0, exact = false): Promise<Locator | null> {
  const nb = await page.evaluate(([d, ex]) => {
    const ok = (t: string) => (ex ? t.replace(/\s*\*$/, '') === d : t.startsWith(d));
    return Array.from(document.querySelectorAll('main label')).filter((x) => ok((x.textContent ?? '').trim())).length;
  }, [debut, exact] as const);
  return nb > n ? champ(page, debut, n, exact) : null;
}

/** Confirme toutes les valeurs proposees visibles (boutons " Confirmer "). */
export async function confirmerPropositions(page: Page, zone: Locator = page.locator('main')): Promise<void> {
  for (let i = 0; i < 30; i++) {
    const b = zone.getByRole('button', { name: 'Confirmer', exact: true }).first();
    if (!(await b.isVisible().catch(() => false)) || !(await b.isEnabled().catch(() => false))) break;
    await b.click();
  }
}

export async function etapeCourante(page: Page): Promise<string> {
  return (await page.locator('[aria-current="step"]').first().getAttribute('aria-label').catch(() => null)) ?? '-';
}

/** Attend l'affichage du parcours (frise des etapes), apres le squelette de chargement. */
export async function attendreParcours(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/workflows\/[0-9a-f-]{36}$/);
  await expect(page.locator('[aria-current="step"]').first(), 'frise des etapes du parcours').toBeVisible({ timeout: 60_000 });
  await calme(page);
}

/**
 * Valide l'etape affichee (" Valider et continuer " ou " Valider l'etape ") et attend la
 * suivante. La reponse de execute-step doit etre un succes.
 */
export async function validerEtape(page: Page, bouton: RegExp = /^(Valider et continuer|Valider l\u2019\u00e9tape|Continuer)$/): Promise<string> {
  await confirmerPropositions(page);
  const avant = await etapeCourante(page);
  const b = page.locator('main').getByRole('button', { name: bouton }).last();
  await expect(b, `validation possible a l'etape ${avant}`).toBeEnabled();
  const reponse = page.waitForResponse((r) => r.url().includes('/execute-step') && r.request().method() === 'POST', { timeout: 90_000 });
  await b.click();
  const r = await reponse;
  expect(r.status(), `etape ${avant} : POST execute-step (${await r.text().catch(() => '')})`).toBeLessThan(400);
  await expect.poll(() => etapeCourante(page), { message: `passage a l'etape suivante apres ${avant}`, timeout: 60_000 }).not.toBe(avant);
  await calme(page);
  return etapeCourante(page);
}

export interface NouveauTicket {
  titre: string;
  type: 'CREATION' | 'IMPORT' | 'MODIFICATION' | 'DISSOLUTION' | 'LIQUIDATION' | 'DIVERS';
  sousType?: 'SUCCURSALE_MA' | 'SUCCURSALE_ETR' | 'FERMETURE_SUCCURSALE' | 'PV_AGO';
  raisonSociale?: string;
}

/** Ouvre un ticket depuis la liste des tickets et arrive sur son parcours ; rend l'id du ticket. */
export async function ouvrirTicket(page: Page, t: NouveauTicket): Promise<string> {
  // La visite guidee d'un compte neuf peut s'ouvrir apres le chargement : on la quitte.
  const passer = page.getByRole('button', { name: 'Passer la visite' });
  await passer.waitFor({ state: 'visible', timeout: 3_000 }).catch(() => undefined);
  if (await passer.isVisible().catch(() => false)) await passer.click();
  await page.getByRole('button', { name: 'Nouveau ticket' }).click();
  const tiroir = page.getByRole('dialog');
  await tiroir.getByLabel('Titre', { exact: true }).fill(t.titre);
  await tiroir.getByLabel('Type d\u2019op\u00e9ration').selectOption(t.type);
  if (t.sousType) await tiroir.getByLabel('Type', { exact: true }).selectOption(t.sousType);
  if (t.raisonSociale) await tiroir.getByLabel('Raison sociale').fill(t.raisonSociale);
  await tiroir.getByRole('button', { name: /^Cr\u00e9er/ }).click();
  await attendreParcours(page);
  return page.url().split('/').pop() as string;
}

/** Jeton de la session du navigateur (pour lire l'API comme l'ecran le fait). */
export async function jeton(page: Page): Promise<string> {
  const j = await page.evaluate(() => window.localStorage.getItem('jurika_access_token'));
  expect(j, 'session du navigateur').toBeTruthy();
  return j as string;
}

export async function lireApi<T>(page: Page, chemin: string): Promise<T> {
  const r = await page.request.get(API_URL + chemin, { headers: { Authorization: `Bearer ${await jeton(page)}` } });
  expect(r.status(), `GET ${chemin}`).toBe(200);
  return (await r.json()) as T;
}

/** Une ICE fictive unique (15 chiffres) : l'ICE est unique par cabinet. */
export function iceFictive(): string {
  return `0${String(Date.now()).slice(-10)}${String(Math.floor(Math.random() * 10_000)).padStart(4, '0')}`;
}

export interface SocieteFictive {
  raisonSociale: string;
  ice: string;
  rc: string;
  ifiscal: string;
  adresse: string;
  ville: string;
}

/** Societe fictive unique pour un parcours (denomination et ICE propres). */
export function societeFictive(prefixe: string): SocieteFictive {
  const n = String(Date.now()).slice(-6);
  return {
    raisonSociale: `${prefixe} ${n} SARL`,
    ice: iceFictive(),
    rc: n,
    ifiscal: `4${n}1`,
    adresse: '22 boulevard Anfa',
    ville: 'Casablanca',
  };
}

/** Les deux associes fictifs des societes reprises (le premier est gerant). */
export const ASSOCIES = [
  { nom: 'Alaoui', prenom: 'Mehdi', cin: 'BE123456', adresse: '8 rue des Essais, Casablanca', naissance: '1980-03-15', lieu: 'Rabat', parts: '600', genre: 'masculin', civilite: 'M' },
  { nom: 'Bennani', prenom: 'Sara', cin: 'BK765432', adresse: '14 avenue des Tests, Rabat', naissance: '1986-07-21', lieu: 'F\u00e8s', parts: '400', genre: 'f\u00e9minin', civilite: 'Mme' },
] as const;

/**
 * Parcours de REPRISE D'UN DOSSIER (import), etapes 1 a 9, dans le navigateur. Rend l'id du
 * ticket. C'est aussi le moyen que l'application prevoit pour faire entrer une societe
 * existante : les autres parcours de ce lot partent de la societe ainsi reprise.
 */
export async function reprendreSociete(page: Page, s: SocieteFictive): Promise<{ ticketId: string; dossierId: string }> {
  await page.goto(page.url().replace(/(https?:\/\/[^/]+).*/, '$1') + '/tickets', { waitUntil: 'domcontentloaded' });
  await calme(page);
  const ticketId = await ouvrirTicket(page, { titre: `Reprise ${s.raisonSociale}`, type: 'IMPORT', raisonSociale: s.raisonSociale });

  // Etape 1 : identite. La denomination proposee est la raison sociale du ticket (pas son titre).
  // Defaut trouve au lot : le titre du ticket etait propose. Controle souple : l'utilisateur
  // corrige le champ et le parcours continue (le test reste en echec, nommement).
  const denomination = await champ(page, 'D\u00e9nomination (raison sociale)');
  await expect.soft(denomination, 'denomination proposee = raison sociale saisie a l\'ouverture').toHaveValue(s.raisonSociale);
  await denomination.fill(s.raisonSociale);
  await page.locator('#creation-ice').fill(s.ice);
  await (await champ(page, 'Num\u00e9ro du registre du commerce')).fill(s.rc);
  await (await champ(page, 'Identifiant fiscal')).fill(s.ifiscal);
  await validerEtape(page);

  // Etape 2 : siege.
  await (await champ(page, 'Adresse du si\u00e8ge social')).fill(s.adresse);
  await (await champ(page, 'Province ou pr\u00e9fecture')).fill(`Pr\u00e9fecture de ${s.ville}`);
  await (await champ(page, 'Commune')).fill(s.ville);
  await (await champ(page, 'Code postal')).fill('20000');
  await page.locator('#creation-ville-greffe').fill(s.ville);
  await validerEtape(page);

  // Etape 3 : capital (duree proposee confirmee).
  await (await champ(page, 'Montant total (MAD)')).fill('100000');
  await (await champ(page, 'Nombre total de parts sociales')).fill('1000');
  await validerEtape(page);

  // Etape 4 : activite et premier exercice repris.
  const activites = page.locator('#creation-activites');
  await activites.click();
  await activites.pressSequentially('Le conseil en organisation et gestion des entreprises', { delay: 5 });
  await activites.press('Enter');
  await activites.pressSequentially('La formation professionnelle continue', { delay: 5 });
  await page.locator('#import-date-debut-exercice').fill('2026-01-01');
  await validerEtape(page);

  // Etape 5 : gerance (le premier associe).
  const g = ASSOCIES[0];
  await (await champ(page, 'Nom', 0, true)).fill(g.nom);
  await (await champ(page, 'Pr\u00e9nom', 0, true)).fill(g.prenom);
  await (await champ(page, 'N\u00b0 CIN')).fill(g.cin);
  await (await champ(page, 'Date de naissance')).fill(g.naissance);
  await (await champ(page, 'Lieu de naissance')).fill(g.lieu);
  await (await champ(page, 'Pi\u00e8ce valable jusqu')).fill('2032-03-15');
  await (await champ(page, 'Adresse', 0, true)).fill(g.adresse);
  await (await champ(page, 'R\u00e9mun\u00e9ration de')).selectOption('non_remunere');
  await validerEtape(page);

  // Etape 6 : associes.
  await page.locator('main').getByRole('button', { name: 'Ajouter un associ\u00e9' }).click();
  await confirmerPropositions(page);
  for (const [i, a] of ASSOCIES.entries()) {
    await (await champ(page, 'Genre (accord', i)).selectOption(a.genre);
    await (await champ(page, 'Civilit\u00e9', i)).selectOption(a.civilite);
    await (await champ(page, 'Nom', i, true)).fill(a.nom);
    await (await champ(page, 'Pr\u00e9nom', i, true)).fill(a.prenom);
    await (await champ(page, 'N\u00b0 CIN', i)).fill(a.cin);
    await (await champ(page, 'Adresse', i, true)).fill(a.adresse);
    await (await champ(page, 'Date de naissance', i)).fill(a.naissance);
    await (await champ(page, 'Lieu de naissance', i)).fill(a.lieu);
    await (await champ(page, 'Pi\u00e8ce valable jusqu', i)).fill('2033-01-01');
    await (await champ(page, 'Parts de l\u2019apport 1', i)).fill(a.parts);
  }
  await validerEtape(page);

  // Etape 7 : actes juridiques (statuts, PDF fictif), type choisi puis depot.
  await page.locator('#import-files').setInputFiles([{ name: 'statuts-fictifs.pdf', mimeType: 'application/pdf', buffer: pdfFictif() }]);
  const type = page.locator('main').getByLabel('Type du document statuts-fictifs.pdf');
  await type.selectOption('STATUTS');
  await expect(type, 'document depose (type fige)').toBeDisabled({ timeout: 30_000 });
  await validerEtape(page);

  // Etape 8 : pieces comptables et fiscales (facultatives) : un bilan fictif.
  await page.locator('main input[type="file"]').first().setInputFiles([{ name: 'bilan-2025-fictif.pdf', mimeType: 'application/pdf', buffer: pdfFictif() }]);
  await calme(page);
  await validerEtape(page, /^Continuer$/);

  // Etape 9 : certification et finalisation.
  await page.locator('main').getByLabel(/Je certifie/).check();
  const fin = page.waitForResponse((r) => r.url().includes('/execute-step') && r.request().method() === 'POST', { timeout: 90_000 });
  await page.locator('main').getByRole('button', { name: 'Finaliser' }).click();
  const r = await fin;
  expect(r.status(), `finalisation de la reprise (${await r.text().catch(() => '')})`).toBeLessThan(400);
  await expect(page.getByText('Reprise termin\u00e9e')).toBeVisible();

  const t = await lireApi<{ dossierId: string }>(page, `/tickets/${ticketId}`);
  return { ticketId, dossierId: t.dossierId };
}

/** Identifiants de la fiche du dossier (code -> valeur), tels que la Data Room les affiche. */
export async function ficheDossier(page: Page, dossierId: string): Promise<Record<string, string | null>> {
  const f = await lireApi<{ fiche: { identifiants: { code: string; valeur: string | null }[] } }>(
    page, `/dataroom/dossiers/${dossierId}/juridique/fiche-dossier`);
  return Object.fromEntries(f.fiche.identifiants.map((i) => [i.code, i.valeur]));
}

/** Choisit la societe par la recherche (parcours qui la demandent par autocompletion). */
export async function choisirSociete(page: Page, raisonSociale: string): Promise<void> {
  const r = page.locator('main').getByLabel('Recherche de soci\u00e9t\u00e9');
  await r.click();
  await r.pressSequentially(raisonSociale.split(' ').slice(0, 2).join(' '), { delay: 20 });
  await page.getByRole('option').filter({ hasText: raisonSociale }).first().click();
  await calme(page);
}

export interface Seance {
  heure?: string;
  cloture?: string;
  majorite?: string;
  /** Presence par associe, dans l'ordre de la feuille. */
  presences?: ('pr\u00e9sent' | 'repr\u00e9sent\u00e9' | 'absent')[];
  quorum?: 'oui' | 'non';
  vote?: 'adopt\u00e9e' | 'rejet\u00e9e';
  voix?: [string, string, string];
}

/**
 * Remplit le formulaire de seance du PV (heure, quorum, vote, presence). Les champs repris
 * de l'etape 1 (convocation) ne sont PAS ressaisis : le test echoue s'ils sont redemandes.
 */
export async function remplirSeance(page: Page, s: Seance = {}): Promise<void> {
  const h = await champSiPresent(page, 'Heure de l\u2019assembl\u00e9e');
  if (h && !(await h.inputValue())) await h.fill(s.heure ?? '10:00');
  const c = await champSiPresent(page, 'Heure de cl\u00f4ture');
  if (c) await c.fill(s.cloture ?? '11:00');
  expect(await champSiPresent(page, 'Date de convocation'), 'la convocation de l\'etape 1 n\'est pas redemandee').toBeNull();
  await (await champ(page, 'Quorum atteint')).selectOption(s.quorum ?? 'oui');
  await (await champ(page, 'R\u00e9sultat du vote')).selectOption(s.vote ?? 'adopt\u00e9e');
  await (await champ(page, 'Majorit\u00e9', 0, true)).fill(s.majorite ?? '\u00e0 la majorit\u00e9 des associ\u00e9s repr\u00e9sentant plus de la moiti\u00e9 des parts sociales');
  const [pour, contre, abst] = s.voix ?? ['1000', '0', '0'];
  await (await champ(page, 'Voix pour')).fill(pour);
  await (await champ(page, 'Voix contre')).fill(contre);
  await (await champ(page, 'Abstentions')).fill(abst);
  const presences = s.presences ?? ['pr\u00e9sent', 'pr\u00e9sent'];
  for (const [i, p] of presences.entries()) await (await champ(page, 'Pr\u00e9sence', i)).selectOption(p);
  await confirmerPropositions(page);
}

/** Lignes de signature et mentions a remplir a la main, propres aux gabarits du cabinet. */
export const PERMIS_GABARIT: Permis[] = [
  { texte: /Signature(?: l\u00e9galis\u00e9e)?\s*:\s*\.{4,}/, motif: 'ligne de signature manuscrite du gabarit' },
  { texte: /Enregistr\u00e9 \u00e0 [^,]+, le \.{4,}, sous la r\u00e9f\u00e9rence \.{4,}, droits acquitt\u00e9s : \.{4,} dirhams\./, motif: 'mention d\'enregistrement remplie par l\'administration' },
  { texte: /R\u00e9serv\u00e9 \u00e0 l'administration : \.{4,}/, motif: 'cadre reserve a l\'administration' },
  { texte: /\S \.{4,} \d[\d\s]* parts\b/, motif: 'points de conduite du tableau de repartition des parts (statuts du cabinet)' },
];

export interface DocumentProduit { analyse: Analyse & { genre: 'docx' | 'pdf' }; texte: string; octets: Buffer }

/**
 * Genere le document de la zone qui contient `zoneTexte` (bouton " Generer " ou
 * " Regenerer "), recupere le fichier produit (corps de la reponse) et le controle
 * (outils/documents.ts). Le test echoue sur un refus, un blanc ou un marqueur.
 */
export async function genererDocument(page: Page, info: TestInfo, zoneTexte: string, nom: string, permis: Permis[] = PERMIS_GABARIT,
  portee: Locator = page.locator('main')): Promise<DocumentProduit> {
  const boutons = portee.getByRole('button', { name: /^(G\u00e9n\u00e9rer|R\u00e9g\u00e9n\u00e9rer)$/ });
  const idx = await boutons.evaluateAll((els, sec) => {
    const est = (b: Element) => /^(G\u00e9n\u00e9rer|R\u00e9g\u00e9n\u00e9rer)$/.test((b.textContent || '').trim());
    return els.findIndex((e) => {
      let zone: Element = e;
      for (let p = e.parentElement; p; p = p.parentElement) {
        if ([...p.querySelectorAll('button')].filter(est).length > 1) break;
        zone = p;
      }
      return (zone.textContent || '').includes(sec as string);
    });
  }, zoneTexte);
  expect(idx, `bouton Generer de la zone \u00ab ${zoneTexte} \u00bb`).toBeGreaterThanOrEqual(0);
  const reponse = page.waitForResponse((r) => r.request().method() === 'POST' && /\/documents\/[A-Z_]+$/.test(new URL(r.url()).pathname), { timeout: 120_000 });
  await boutons.nth(idx).click();
  const r = await reponse;
  const octets = await r.body();
  expect(r.status(), `generation de ${nom} : ${r.status() >= 400 ? octets.toString('utf8').slice(0, 800) : ''}`).toBe(200);
  await info.attach(`${nom}.docx`, { body: octets, contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });
  const analyse = analyserDocument(octets, permis);
  expect(rapportDocument(nom, analyse), `document ${nom} sans blanc, marqueur ni valeur manquante`).toBeNull();
  const texte = analyse.genre === 'docx' ? texteDocx(octets).join('\n') : '';
  await calme(page);
  return { analyse, texte, octets };
}

/** Valide le document genere de la zone (bouton " Valider " du bloc document). */
export async function validerDocument(page: Page, zoneTexte: string, portee: Locator = page.locator('main')): Promise<void> {
  const boutons = portee.getByRole('button', { name: 'Valider', exact: true });
  const idx = await boutons.evaluateAll((els, sec) => els.findIndex((e) => {
    let zone: Element = e;
    for (let p = e.parentElement; p; p = p.parentElement) {
      if ([...p.querySelectorAll('button')].filter((b) => (b.textContent || '').trim() === 'Valider').length > 1) break;
      zone = p;
    }
    return (zone.textContent || '').includes(sec as string);
  }), zoneTexte);
  expect(idx, `bouton Valider du document \u00ab ${zoneTexte} \u00bb`).toBeGreaterThanOrEqual(0);
  await boutons.nth(idx).click();
  await calme(page);
}

/** Controles de redaction communs (defauts trouves au lot) : civilite abregee, convocation reprise. */
export function verifierRedactionSeance(texte: string, convocation?: string): void {
  expect(texte, 'civilite abregee (\u00ab M. \u00bb, jamais \u00ab M Mehdi \u00bb)').not.toMatch(/\bM Mehdi\b/);
  if (convocation) expect(texte, 'date de convocation saisie a l\'etape 1').toContain(convocation);
}

/**
 * Fiche du ticket : decisions proposees selon la forme du dossier (le ticket est rattache a la
 * societe choisie), puis toutes les demarches cochees statut par statut, jusqu'a la cloture.
 */
const LIBELLES_STATUT: Record<string, string> = {
  CREATION_TICKET: "Cr\u00e9ation du ticket et collecte d'information",
  GENERATION_DOCUMENTS: 'G\u00e9n\u00e9ration des documents',
  DEROULEMENT_DEMARCHE: 'D\u00e9roulement de la d\u00e9marche',
};

export async function cloturerTicket(page: Page, ticketId: string, decision: RegExp): Promise<void> {
  await page.goto(page.url().replace(/(https?:\/\/[^/]+).*/, '$1') + `/tickets/${ticketId}`, { waitUntil: 'domcontentloaded' });
  await calme(page);
  const main = page.locator('main');
  await expect(main, 'la fiche du ticket connait la forme du dossier').not.toContainText('Forme juridique du dossier inconnue');
  const choix = main.getByRole('checkbox', { name: decision }).first();
  if (!(await choix.isChecked())) {
    await choix.check();
    await main.getByRole('button', { name: 'Enregistrer les d\u00e9cisions' }).click();
    await calme(page);
  }
  for (let tour = 0; tour < 6; tour++) {
    const statut = (await lireApi<{ statut: string }>(page, `/tickets/${ticketId}`)).statut;
    if (statut === 'CLOTURE_DOSSIER') break;
    // Les demarches se rechargent apres un passage (squelette) : on attend leur liste.
    await expect(main.getByText('Chargement des d\u00e9marches\u2026')).toBeHidden({ timeout: 30_000 });
    // Le groupe des demarches du statut courant est deplie (il peut rester replie apres un passage).
    const libelle = LIBELLES_STATUT[statut];
    if (libelle) {
      const groupe = main.getByRole('button', { name: new RegExp(`^${libelle}\\s*\\d+\\s*/\\s*\\d+$`) }).first();
      if ((await groupe.getAttribute('aria-expanded', { timeout: 3_000 }).catch(() => null)) === 'false') await groupe.click();
    }
    await main.locator('button:enabled').filter({ hasText: /^Cocher$/ }).first()
      .waitFor({ state: 'visible', timeout: 10_000 }).catch(() => undefined);
    // Seules les demarches actionnables (bouton actif) se cochent ; une demarche d'un autre
    // statut garde un bouton inactif.
    for (let i = 0; i < 80; i++) {
      const b = main.locator('button:enabled').filter({ hasText: /^Cocher$/ }).first();
      if (!(await b.isVisible().catch(() => false))) break;
      await b.click({ timeout: 15_000 });
      await calme(page);
    }
    const passer = main.getByRole('button', { name: /^Passer \u00e0 \u00ab / }).first();
    await expect(passer, `passage de statut possible une fois les demarches cochees (statut ${statut})`).toBeEnabled({ timeout: 30_000 });
    await passer.click({ timeout: 30_000 });
    const dlg = page.getByRole('dialog');
    if (await dlg.isVisible().catch(() => false)) {
      await dlg.getByRole('button', { name: /^(Confirmer|Cl\u00f4turer|Passer)/ }).last().click();
    }
    await expect.poll(async () => (await lireApi<{ statut: string }>(page, `/tickets/${ticketId}`)).statut,
      { message: `passage depuis ${statut}`, timeout: 30_000 }).not.toBe(statut);
    await calme(page);
  }
  const t = await lireApi<{ statut: string }>(page, `/tickets/${ticketId}`);
  expect(t.statut, 'ticket clos').toBe('CLOTURE_DOSSIER');
}
