import type { Page, TestInfo } from '@playwright/test';
import { test, expect } from './fixtures';
import {
  ASSOCIES, calme, champ, champSiPresent, choisirSociete, cloturerTicket, confirmerPropositions, ficheDossier,
  genererDocument, lireApi, ouvrirTicket, remplirSeance, reprendreSociete, societeFictive,
  tolererDemarrageParcours, validerDocument, validerEtape, verifierRedactionSeance,
} from './outils/actes';

/**
 * Lot L6b (E15) : parcours d'ACTES de bout en bout, sur la pile reelle, comme un utilisateur
 * (sous-agent P2) : une reprise de dossier (import), une modification (nomination d'un
 * gerant), une approbation des comptes, une succursale, une dissolution puis la liquidation,
 * un incident d'assemblee, l'annulation et la reprise d'un ticket.
 *
 * Prerequis : chaque parcours sur une societe existante la fait d'abord entrer par le
 * parcours de REPRISE D'UN DOSSIER (import), moyen que l'application prevoit (societe propre
 * au test : denomination et ICE uniques).
 *
 * Chaque document produit est relu (outils/documents.ts : "VALEUR MANQUANTE", marqueurs,
 * blancs ; "A OBTENIR" permis) et sa redaction controlee sur les defauts trouves au lot
 * (civilite abregee, convocation reprise, ordre du jour de l'incident).
 *
 * @corpus-reel : les actes viennent des modeles du cabinet (corpus reel du Z440). La reprise
 * seule ne genere aucun acte : elle tourne aussi en CI.
 *
 * Relancer : npm run e2e:parcours -- --grep "parcours actes"
 */

const CONVOCATION = { date: '2026-10-20', heure: '10:00', affichee: '20/10/2026' };

/** Liquidation jouee : actif realise (une realisation), sens et montant attendus du resultat. */
interface CasLiquidation { actif: string; inventaire: string; resultat: 'boni' | 'mali'; montant: string }

async function versLesTickets(page: Page, aller: (c: string) => Promise<void>) {
  await aller('/tickets');
  await expect(page.getByRole('button', { name: 'Nouveau ticket' })).toBeVisible();
}

/** Convocation de l'etape 1 (date et heure), la date de l'assemblee suit (regle des 16 jours). */
async function convoquer(page: Page, date: string = CONVOCATION.date) {
  await page.locator('main').getByLabel('Date de convocation', { exact: true }).first().fill(date);
  await page.locator('main').getByLabel('Heure de convocation', { exact: true }).first().fill(CONVOCATION.heure);
}

test.describe('parcours actes', () => {
  test.describe.configure({ timeout: 900_000 });

  test('parcours actes : reprise d\'un dossier (import), fiche du dossier complete', async ({ page, entrer, aller, sentinelle, capturer }) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    await versLesTickets(page, aller);
    const s = societeFictive('REPRISE PARCOURS');
    const { dossierId } = await reprendreSociete(page, s);
    // La reprise fait entrer la societe telle qu'elle a ete saisie (siege, capital, greffe).
    const fiche = await ficheDossier(page, dossierId);
    expect(fiche.DENOMINATION).toBe(s.raisonSociale);
    expect(fiche.ICE).toBe(s.ice);
    expect(fiche.CAPITAL, 'capital repris').toMatch(/100\s?000/);
    expect(fiche.SIEGE, 'siege repris').toContain(s.adresse);
    expect(fiche.RC, 'ville du greffe reprise').toContain(s.ville);
    const parties = await lireApi<{ associes: unknown[]; gerants: unknown[]; nombreParts: number }>(page, `/workflows/dossiers/${dossierId}/parties`);
    expect(parties.associes).toHaveLength(2);
    expect(parties.gerants).toHaveLength(1);
    expect(parties.nombreParts).toBe(1000);
    await capturer('actes-reprise-dossier');
  });

  test('parcours actes @corpus-reel : modification, nomination d\'une cogerante, jusqu\'a la cloture', async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    await versLesTickets(page, aller);
    const s = societeFictive('NOMINATION PARCOURS');
    const { dossierId } = await reprendreSociete(page, s);
    await versLesTickets(page, aller);
    const ticketId = await ouvrirTicket(page, { titre: `Nomination d'une cogerante ${s.raisonSociale}`, type: 'MODIFICATION' });

    // Etape 1 : societe, decision, assemblee ordinaire, convocation.
    const main = page.locator('main');
    await main.locator('#step1-societe-select').selectOption(dossierId);
    await calme(page);
    await main.getByLabel(/^Nomination du ou des g\u00e9rant/).check();
    await main.getByLabel('Type d\u2019assembl\u00e9e *').selectOption('ordinaire');
    await convoquer(page);
    await validerEtape(page);

    // Etape 2 : la cogerante (la seconde associee).
    const a = ASSOCIES[1];
    await main.getByLabel('G\u00e9rant sortant ?').selectOption('non');
    await main.getByRole('button', { name: 'Ajouter', exact: true }).click();
    await (await champ(page, 'Dur\u00e9e du mandat')).fill('trois ann\u00e9es');
    await (await champ(page, 'Civilit\u00e9', 0, true)).fill('Mme');
    await (await champ(page, 'Pr\u00e9nom', 0, true)).fill(a.prenom);
    await (await champ(page, 'Nom', 0, true)).fill(a.nom);
    await (await champ(page, 'Nationalit\u00e9', 0, true)).fill('marocaine');
    await (await champ(page, 'N\u00e9(e) le')).fill(a.naissance);
    await (await champ(page, 'Adresse', 0, true)).fill(a.adresse);
    await (await champ(page, 'Pi\u00e8ce', 0, true)).fill('CIN');
    await (await champ(page, 'N\u00b0 pi\u00e8ce')).fill(a.cin);
    await validerEtape(page);

    // Etape 3 : seance du PV, puis PV, annonce legale et statuts refondus.
    await remplirSeance(page);
    const pv = await genererDocument(page, info, 'PV de modification', 'pv-modification');
    verifierRedactionSeance(pv.texte, CONVOCATION.affichee);
    expect(pv.texte).toContain(`${a.prenom} ${a.nom}`);
    await validerDocument(page, 'PV de modification');
    await genererDocument(page, info, 'Annonce l\u00e9gale (modification)', 'annonce-modification');
    await validerDocument(page, 'Annonce l\u00e9gale (modification)');
    const statuts = await genererDocument(page, info, 'Statuts refondus', 'statuts-refondus');
    expect(statuts.texte, 'statuts signes au lieu de signature du PV').toMatch(/Fait \u00e0 Casablanca/);
    await validerDocument(page, 'Statuts refondus');
    await validerEtape(page);

    // Etape 4 : pieces jointes (facultatives), etape 5 : synthese et finalisation.
    await validerEtape(page);
    await finaliser(page, ticketId);
    await capturer('actes-modification-nomination');
    await cloturerTicket(page, ticketId, /Nomination du ou des g\u00e9rant/);
    await capturer('actes-modification-cloture');
  });

  test('parcours actes @corpus-reel : approbation des comptes, jusqu\'a la cloture', async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    await versLesTickets(page, aller);
    const s = societeFictive('COMPTES PARCOURS');
    await reprendreSociete(page, s);
    await versLesTickets(page, aller);
    const ticketId = await ouvrirTicket(page, { titre: `Approbation des comptes 2025 ${s.raisonSociale}`, type: 'DIVERS', sousType: 'PV_AGO' });

    await choisirSociete(page, s.raisonSociale);
    await page.locator('main').getByLabel('Exercice clos *').selectOption('2025');
    await convoquer(page);
    await validerEtape(page);

    // Etape 2 : seance (convocation reprise), resultat et affectation, quitus.
    await remplirSeance(page);
    await (await champ(page, 'Type de r\u00e9sultat')).selectOption('b\u00e9n\u00e9fice');
    await (await champ(page, 'R\u00e9sultat net (MAD)')).fill('50000');
    await (await champ(page, 'Commissaire aux comptes pr\u00e9sent')).selectOption('non');
    await (await champ(page, 'Montant (MAD)')).fill('2500');
    await page.locator('main').getByLabel('Poste d\u2019affectation 2').fill('Report \u00e0 nouveau');
    await page.locator('main').getByLabel('Montant du poste 2 (MAD)').fill('47500');
    await (await champ(page, 'Dividende distribu\u00e9')).selectOption('non');
    await (await champ(page, 'Quitus donn\u00e9')).selectOption('oui');
    await (await champ(page, 'Conventions r\u00e9glement\u00e9es intervenues')).selectOption('non');
    await validerEtape(page);

    const pv = await genererDocument(page, info, 'Approbation des comptes (AGO', 'pv-approbation-comptes');
    verifierRedactionSeance(pv.texte, CONVOCATION.affichee);
    expect(pv.texte, 'resultat de l\'exercice').toMatch(/50\s000/);
    await validerDocument(page, 'Approbation des comptes (AGO');
    await validerEtape(page);
    await validerEtape(page);
    await finaliser(page, ticketId);
    await capturer('actes-approbation-comptes');
    await cloturerTicket(page, ticketId, /comptes/i);
  });

  test('parcours actes @corpus-reel : succursale marocaine, jusqu\'a la cloture', async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    await versLesTickets(page, aller);
    const s = societeFictive('SUCCURSALE PARCOURS');
    await reprendreSociete(page, s);
    await versLesTickets(page, aller);
    const ticketId = await ouvrirTicket(page, { titre: `Ouverture d'une succursale a Rabat ${s.raisonSociale}`, type: 'DIVERS', sousType: 'SUCCURSALE_MA' });

    await choisirSociete(page, s.raisonSociale);
    await convoquer(page);
    await validerEtape(page);

    await (await champ(page, 'Enseigne')).fill(`${s.raisonSociale.replace(/ SARL$/, '')} RABAT`);
    await (await champ(page, 'Activit\u00e9')).fill('le conseil en organisation et gestion des entreprises');
    await (await champ(page, 'Adresse', 0)).fill('3 avenue Mohammed V');
    await (await champ(page, 'Ville', 0, true)).fill('Rabat');
    await (await champ(page, 'Ville du greffe de la succursale')).fill('Rabat');
    await (await champ(page, 'Date d\u2019ouverture')).fill('2026-12-01');
    await page.locator('main').getByLabel('Un responsable de la succursale est d\u00e9sign\u00e9').check();
    await (await champ(page, 'Choisir une partie prenante')).selectOption({ index: 1 });
    await (await champ(page, 'Pouvoirs conf\u00e9r\u00e9s')).fill('les pouvoirs les plus \u00e9tendus pour la gestion courante de la succursale');
    await validerEtape(page);

    await remplirSeance(page, { majorite: '\u00e0 la majorit\u00e9 des trois quarts des parts sociales' });
    const mandataire = await champSiPresent(page, 'Mandataire charg\u00e9 des formalit\u00e9s');
    if (mandataire && !(await mandataire.inputValue())) await mandataire.fill('M. Mehdi Alaoui');
    const pv = await genererDocument(page, info, 'PV', 'pv-creation-succursale');
    verifierRedactionSeance(pv.texte, CONVOCATION.affichee);
    await validerDocument(page, 'PV');
    // Rechargement entre deux documents : la saisie de l'etape 2 doit survivre (defaut trouve au lot).
    await page.reload();
    await calme(page);
    const annonce = await genererDocument(page, info, 'Annonce', 'annonce-ouverture-succursale');
    expect(annonce.texte, 'annonce : enseigne de la succursale reprise apres rechargement').toContain('RABAT');
    await validerDocument(page, 'Annonce');
    await validerEtape(page);
    await validerEtape(page);
    await finaliser(page, ticketId);
    await capturer('actes-succursale');
    await cloturerTicket(page, ticketId, /succursale/i);
  });

  /**
   * Dissolution puis liquidation jusqu'a la cloture du ticket. L6b (E19) : le resultat de la
   * liquidation a une seule source, le rapport final ; il est relu a l'ecran (rapport final et
   * synthese), au PV de cloture et au rapport ; l'avis de cloture ne le publie pas.
   */
  async function dissoudreEtLiquider(page: Page, info: TestInfo, aller: (c: string) => Promise<void>,
    capturer: (nom: string) => Promise<void>, nom: string, cas: CasLiquidation) {
    await versLesTickets(page, aller);
    const s = societeFictive(nom);
    await reprendreSociete(page, s);
    await versLesTickets(page, aller);
    const ticketId = await ouvrirTicket(page, { titre: `Dissolution anticipee ${s.raisonSociale}`, type: 'DISSOLUTION' });

    await choisirSociete(page, s.raisonSociale);
    // Dissolution passee (la liquidation refuse une date de dissolution future) : convocation
    // le 01/09/2026, assemblee le 17/09/2026.
    await convoquer(page, '2026-09-01');
    const main = page.locator('main');
    await main.locator('#dissolution-nature').selectOption('volonte_associes');
    const dateAge = await main.getByLabel('Date de l\u2019AGE *').inputValue();
    await main.locator('#dissolution-date-effet').fill(dateAge);
    await main.locator('#dissolution-motif').fill('Cessation de l\u2019activit\u00e9 d\u00e9cid\u00e9e par les associ\u00e9s.');
    // Liquidateur : le gerant, repris du dossier (nationalite et naissance comprises).
    await (await champ(page, 'D\u00e9signation')).selectOption({ index: 1 });
    await expect(await champ(page, 'Nationalit\u00e9', 0, true), 'nationalite du liquidateur reprise du dossier').toHaveValue('marocaine');
    const ancienGerant = await champSiPresent(page, 'A-t-il \u00e9t\u00e9 g\u00e9rant');
    if (ancienGerant) await ancienGerant.selectOption('oui');
    await (await champ(page, 'R\u00e9mun\u00e9ration du liquidateur')).selectOption('non');
    await (await champ(page, 'Dur\u00e9e du mandat')).fill('deux ans');
    await (await champ(page, 'P\u00e9riodicit\u00e9 des comptes rendus')).fill('tous les six mois');
    await main.locator('#dissolution-siege-identique').selectOption('oui');
    await confirmerPropositions(page);
    await validerEtape(page);

    await remplirSeance(page, { majorite: '\u00e0 la majorit\u00e9 des trois quarts des parts sociales' });
    await (await champ(page, 'Folio du registre')).fill('12');
    await (await champ(page, 'Journal d\u2019annonces l\u00e9gales')).fill('Journal fictif des annonces');
    await (await champ(page, 'Un commissaire aux comptes est-il en fonction')).selectOption('non');
    await (await champ(page, 'Quitus donn\u00e9 \u00e0 la g\u00e9rance')).selectOption('oui');
    await (await champ(page, 'Dix salari\u00e9s ou plus')).selectOption('non');
    const pv = await genererDocument(page, info, 'PV', 'pv-dissolution');
    verifierRedactionSeance(pv.texte, '01/09/2026');
    expect(pv.texte).toContain('liquidateur');
    await validerDocument(page, 'PV');
    await genererDocument(page, info, 'Annonce', 'annonce-dissolution');
    await validerDocument(page, 'Annonce');
    await validerEtape(page);
    await validerEtape(page);
    await finaliser(page, ticketId);
    await capturer('actes-dissolution');
    await cloturerTicket(page, ticketId, /dissolution/i);

    // Liquidation : la societe dissoute, liquidateur lu en base.
    await versLesTickets(page, aller);
    const liquidation = await ouvrirTicket(page, { titre: `Cloture de la liquidation ${s.raisonSociale}`, type: 'LIQUIDATION' });
    await choisirSociete(page, s.raisonSociale);
    const liq = page.locator('main');
    await expect(liq.getByLabel('Nom *').first(), 'liquidateur nomme a la dissolution, lu en base').toHaveValue(ASSOCIES[0].nom);
    await expect(liq.getByLabel('Nationalit\u00e9', { exact: true }).first(), 'nationalite du liquidateur reprise').toHaveValue('marocaine');
    await (await champ(page, 'R\u00e9mun\u00e9ration du liquidateur')).selectOption('non');
    // La cloture se tient 15 jours au moins apres la dissolution (17/09/2026) : convocation le
    // 20/09/2026, assemblee de cloture fixee 16 jours plus tard (06/10/2026).
    await liq.getByLabel('Date de convocation', { exact: true }).first().fill('2026-09-20');
    await liq.getByLabel('Heure de convocation', { exact: true }).first().fill('10:00');
    await expect(liq.getByLabel('Date de l\u2019AGE de cl\u00f4ture *')).toHaveValue('2026-10-06');
    await (await champ(page, 'Total actif (MAD)')).fill(cas.actif);
    await (await champ(page, 'Total passif (MAD)')).fill('20000');
    await confirmerPropositions(page);
    await validerEtape(page);
    // Etape 2 : seance de cloture, compte definitif, PV de cloture et annonce.
    await remplirSeance(page);
    const texte = async (lib: string, v: string) => { const c = await champSiPresent(page, lib); if (c && !(await c.inputValue())) await c.fill(v); };
    const choix = async (lib: string, v: string | number) => {
      const c = await champSiPresent(page, lib);
      if (!c || (await c.inputValue())) return;
      // Valeur cherchee parmi les options (valeur ou libelle commencant par le texte), sinon l'option n.
      const idx = typeof v === 'number' ? v : await c.evaluate((el, t) => [...(el as HTMLSelectElement).options]
        .findIndex((o) => o.value && (o.value.toLowerCase().startsWith(t) || o.text.toLowerCase().startsWith(t))), v.toLowerCase());
      await c.selectOption({ index: idx > 0 ? idx : 1 }, { timeout: 15_000 });
    };
    await texte('Folio du registre', '13');
    await choix('Un commissaire aux comptes est-il en fonction', 'non');
    await texte('Inscription de la dissolution au RC', '2026-09-25');
    await texte('Journal d\u2019annonces l\u00e9gales de la nomination', 'Journal fictif des annonces');
    await texte('Date de publication', '2026-09-25');
    await choix('Assembl\u00e9e convoqu\u00e9e par', 1);
    await texte('R\u00e9sum\u00e9 des op\u00e9rations de liquidation', 'R\u00e9alisation de l\u2019actif et apurement du passif.');
    await texte('Frais et honoraires de liquidation', '5000');
    await choix('Les comptes bancaires sont-ils cl\u00f4tur\u00e9s', 'oui');
    await choix('Le compte d\u00e9finitif est-il approuv\u00e9', 'oui');
    await choix('Retenue \u00e0 la source sur le boni', 'non');
    await texte('Motif de l\u2019absence de retenue', 'motif fictif du parcours de test');
    await texte('Modalit\u00e9s de paiement', 'par virement bancaire');
    await texte('Paiement au plus tard le', '2026-10-31');
    await texte('D\u00e9positaire', 'M. Mehdi Alaoui');
    await texte('Qualit\u00e9 du d\u00e9positaire', 'ancien liquidateur');
    await texte('Adresse de conservation', '8 rue des Essais, Casablanca');
    await texte('Dur\u00e9e de conservation', 'dix ans');
    await choix('Subsiste-t-il des passifs \u00e9ventuels', 'non');
    await choix('Acte constatant la cl\u00f4ture', 1);
    await texte('Compte d\u00e9finitif arr\u00eat\u00e9 au', '2026-10-06');
    await choix('Quitus au liquidateur', 1);
    await texte('Si\u00e8ge de la liquidation', 'au si\u00e8ge social');
    await confirmerPropositions(page);

    // Rapport final du liquidateur : montants qui s'equilibrent avec l'etape 1 (actif, passif
    // 20 000) et les frais du PV (5 000, fonctions gratuites), pour un capital de 100 000.
    // L6b (E19) : il est la seule source du resultat ; le PV de cloture le reprend, il se
    // genere donc apres lui.
    const rapport = page.locator('main');
    const rt = (k: string, v: string) => rapport.getByTestId(`rapport-${k}`).fill(v);
    const rs = (k: string, v: string) => rapport.getByTestId(`rapport-${k}`).selectOption(v);
    await rt('periodeDebut', '2026-09-17');
    await rt('periodeFin', '2026-10-06');
    await rt('dissolutionOrgane', 'assembl\u00e9e g\u00e9n\u00e9rale extraordinaire des associ\u00e9s');
    await rt('dissolutionDateDecision', '2026-09-17');
    await rt('dissolutionMotifLibelle', 'cessation de l\u2019activit\u00e9 d\u00e9cid\u00e9e par les associ\u00e9s');
    await rapport.getByLabel('Num\u00e9ro de l\u2019inscription modificative au registre du commerce').fill('RC-MOD-2026-1234');
    await rs('prolongee', 'non');
    const ligne = async (n: number, valeurs: [string, string][]) => {
      await rapport.getByRole('button', { name: 'Ajouter une ligne' }).nth(n).click();
      for (const [lib, v] of valeurs) await rapport.getByLabel(lib, { exact: true }).last().fill(v);
    };
    await ligne(0, [['Bien ou droit r\u00e9alis\u00e9', 'Mobilier et mat\u00e9riel de bureau'], ['Mode de r\u00e9alisation', 'cession de gr\u00e9 \u00e0 gr\u00e9'],
      ['Acqu\u00e9reur ou d\u00e9biteur', 'Soci\u00e9t\u00e9 fictive des achats'], ['Prix ou montant encaiss\u00e9 (DH)', cas.actif],
      ['Date de r\u00e9alisation', '2026-09-30'], ['Valeur \u00e0 l\u2019inventaire d\u2019ouverture (DH)', cas.inventaire], ['Observations', 'n\u00e9ant']]);
    await ligne(1, [['Cr\u00e9ancier', 'Fournisseur fictif'], ['Nature de la dette', 'factures de fournitures'],
      ['Montant admis (DH)', '20000'], ['Montant r\u00e9gl\u00e9 (DH)', '20000'], ['Date du r\u00e8glement', '2026-10-01'],
      ['Mode de r\u00e8glement', 'virement bancaire'], ['Observations', 'n\u00e9ant']]);
    await rs('creancesIrrecouvrablesExistent', 'non');
    await rs('passifsContestesExistent', 'non');
    await rs('cessionsRestreintesExistent', 'non');
    await ligne(2, [['Nature du frais ou de l\u2019honoraire', 'frais de publicit\u00e9 et de greffe'], ['B\u00e9n\u00e9ficiaire', 'Greffe et journal fictifs'],
      ['Montant (DH)', '5000'], ['\u00c9tat', 'r\u00e9gl\u00e9']]);
    // "Date" est aussi le libelle de la date de seance, plus bas : la date du frais suit son montant.
    await rapport.getByLabel('Montant (DH)', { exact: true }).last()
      .locator('xpath=following::input[@type="date"][1]').fill('2026-10-02');
    await rt('fraisRembourses', '0');
    await rs('personnelExiste', 'non');
    await rs('contratsExistent', 'non');
    await ligne(3, [['Diligence', 'd\u00e9claration de cessation d\u2019activit\u00e9'], ['Organisme destinataire', 'Direction g\u00e9n\u00e9rale des imp\u00f4ts'],
      ['Fondement', 'code g\u00e9n\u00e9ral des imp\u00f4ts'], ['Date limite', '2026-10-17'], ['Date d\u2019accomplissement', '2026-10-05'],
      ['\u00c9tat', 'accomplie'], ['Observations', 'n\u00e9ant']]);
    await rs('tvaAssujettie', 'non');
    await rt('attestationsEtat', 'attestations de r\u00e9gularit\u00e9 fiscale et sociale obtenues');
    await rt('dateSignature', '2026-10-06');
    // Boni : le modele imprime des observations sur la retenue a la source (champ affiche pour un boni).
    if (cas.resultat === 'boni') await rt('retenueObservations', 'aucune retenue op\u00e9r\u00e9e (donn\u00e9e fictive du parcours de test)');
    const montant = cas.montant.replace(' ', '\\s');
    // A l'ecran : le calcul du rapport final (miroir de la regle du serveur).
    await expect(rapport.getByTestId('rapport-resultat'), `rapport final a l'ecran : ${cas.resultat} de ${cas.montant}`)
      .toContainText(new RegExp(`${cas.resultat} de ${montant}(,00)? DH`));
    const rapportFinal = await genererDocument(page, info, 'Rapport final du liquidateur \u2014 mod\u00e8le', `rapport-final-liquidateur-${cas.resultat}`);
    expect(rapportFinal.texte, `rapport final : ${cas.resultat} de ${cas.montant} DH`)
      .toMatch(new RegExp(`${cas.resultat} de liquidation d[\u2019']un montant de ${montant} dirhams`));
    expect(rapportFinal.texte, 'rapport final : un seul sens').not.toMatch(new RegExp(`${cas.resultat === 'boni' ? 'mali' : 'boni'} de liquidation d[\u2019']un montant`));
    await validerDocument(page, 'Rapport final du liquidateur \u2014 mod\u00e8le');
    const pvCloture = await genererDocument(page, info, 'PV Dissolution / Liquidation', `pv-cloture-liquidation-${cas.resultat}`);
    verifierRedactionSeance(pvCloture.texte);
    expect(pvCloture.texte, `PV de cloture : ${cas.resultat} de ${cas.montant} DH`)
      .toMatch(new RegExp(`constituant un ${cas.resultat} de liquidation : ${montant} dirhams`));
    expect(pvCloture.texte, 'PV de cloture : un seul sens').not.toMatch(new RegExp(`constituant un ${cas.resultat === 'boni' ? 'mali' : 'boni'} de liquidation`));
    await validerDocument(page, 'PV Dissolution / Liquidation');
    const annonce = await genererDocument(page, info, 'Annonce l\u00e9gale de cl\u00f4ture', `annonce-cloture-liquidation-${cas.resultat}`);
    // Le modele du cabinet ne publie pas le resultat de la liquidation (non imprime).
    expect(annonce.texte, 'avis de cloture : resultat non imprime').not.toMatch(/\b(boni|mali)\b/i);
    await validerDocument(page, 'Annonce l\u00e9gale de cl\u00f4ture');
    await validerEtape(page);
    await validerEtape(page);
    await finaliser(page, liquidation);
    // L6b : la synthese affiche le resultat du rapport et du PV, plus le solde actif - passif
    // presente comme un boni (100 000 pour un mali de 5 000).
    const sens = cas.resultat === 'boni' ? 'Boni' : 'Mali';
    await expect(page.getByTestId('synthese-resultat'), 'resultat de la liquidation a la synthese')
      .toHaveText(new RegExp(`^${sens} de ${montant}(,00)? MAD$`));
    await capturer(`actes-liquidation-${cas.resultat}`);
    await cloturerTicket(page, liquidation, /liquidation/i);
  }

  test('parcours actes @corpus-reel : dissolution puis liquidation, jusqu\'a la cloture', async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    // Actif 120 000, passif 20 000, frais 5 000 : tresorerie 95 000 pour un capital de 100 000,
    // mali de 5 000 (actif - passif donnait "boni 100 000").
    await dissoudreEtLiquider(page, info, aller, capturer, 'DISSOLUTION PARCOURS',
      { actif: '120000', inventaire: '110000', resultat: 'mali', montant: '5 000' });
  });

  test('parcours actes @corpus-reel : dissolution puis liquidation avec boni, jusqu\'a la cloture', async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    // Actif 150 000, passif 20 000, frais 5 000 : tresorerie 125 000 pour un capital de
    // 100 000, boni de 25 000 (actif - passif aurait donne 130 000).
    await dissoudreEtLiquider(page, info, aller, capturer, 'LIQUIDATION SOLDE POSITIF',
      { actif: '150000', inventaire: '140000', resultat: 'boni', montant: '25 000' });
  });

  test('parcours actes @corpus-reel : incident d\'assemblee (defaut de quorum)', async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    await versLesTickets(page, aller);
    const s = societeFictive('INCIDENT PARCOURS');
    const { dossierId } = await reprendreSociete(page, s);
    await versLesTickets(page, aller);
    await ouvrirTicket(page, { titre: `Assemblee sans quorum ${s.raisonSociale}`, type: 'MODIFICATION' });
    const main = page.locator('main');
    await main.locator('#step1-societe-select').selectOption(dossierId);
    await calme(page);
    await main.getByLabel(/^Nomination du ou des g\u00e9rant/).check();
    await main.getByLabel('Type d\u2019assembl\u00e9e *').selectOption('ordinaire');
    await convoquer(page);
    await validerEtape(page);
    await main.getByLabel('G\u00e9rant sortant ?').selectOption('non');
    await main.getByRole('button', { name: 'Ajouter', exact: true }).click();
    await (await champ(page, 'Dur\u00e9e du mandat')).fill('trois ann\u00e9es');
    await (await champ(page, 'Pr\u00e9nom', 0, true)).fill(ASSOCIES[1].prenom);
    await (await champ(page, 'Nom', 0, true)).fill(ASSOCIES[1].nom);
    await validerEtape(page);

    // Incident de seance : la seconde associee absente, quorum non atteint.
    await page.getByTestId('incident-seance-toggle').click();
    const incident = page.locator('section').filter({ has: page.getByTestId('incident-seance-toggle') }).last();
    const f = (lab: string) => incident.locator('input,select').and(incident.getByLabel(lab)).first();
    await f('Heure de l\u2019assembl\u00e9e').fill('10:00');
    await f('Heure de cl\u00f4ture').fill('10:30');
    await f('Date (2e assembl\u00e9e)').fill('2026-11-20');
    await f('Heure (2e assembl\u00e9e)').fill('10:00');
    await f('Lieu (2e assembl\u00e9e)').fill('au si\u00e8ge social');
    await f('Quorum atteint *').selectOption('non');
    await f('R\u00e9sultat du vote *').selectOption('rejet\u00e9e');
    await f('Majorit\u00e9 *').fill('faute de quorum');
    await f('Voix pour *').fill('0');
    await f('Voix contre *').fill('0');
    await f('Abstentions *').fill('0');
    await incident.locator('select').and(incident.getByLabel('Pr\u00e9sence *')).nth(0).selectOption('pr\u00e9sent');
    await incident.locator('select').and(incident.getByLabel('Pr\u00e9sence *')).nth(1).selectOption('absent');
    await confirmerPropositions(page, incident);
    const pv = await genererDocument(page, info, 'D\u00e9faut de quorum', 'pv-defaut-quorum', undefined, incident);
    verifierRedactionSeance(pv.texte, CONVOCATION.affichee);
    // L'ordre du jour rappele n'est pas vide (defaut trouve au lot : deux-points suivis de rien).
    const lignes = pv.texte.split('\n');
    const i = lignes.findIndex((l) => /ordre du jour suivant :\s*$/.test(l));
    expect(i, 'rappel de l\'ordre du jour').toBeGreaterThanOrEqual(0);
    expect(lignes[i + 1] ?? '', 'premier point de l\'ordre du jour').toMatch(/Nomination/);
    expect(pv.texte).toContain('20/11/2026');
    await capturer('actes-incident-assemblee');
  });

  test('parcours actes @corpus-reel : annulation puis reprise d\'un ticket', async ({ page, entrer, aller, sentinelle, capturer }, info) => {
    tolererDemarrageParcours(sentinelle);
    await entrer('EMPLOYE');
    await versLesTickets(page, aller);
    const s = societeFictive('ANNULATION PARCOURS');
    const { dossierId } = await reprendreSociete(page, s);
    await versLesTickets(page, aller);
    const ticketId = await ouvrirTicket(page, { titre: `Nomination ajournee ${s.raisonSociale}`, type: 'MODIFICATION' });
    const main = page.locator('main');
    await main.locator('#step1-societe-select').selectOption(dossierId);
    await calme(page);
    await main.getByLabel(/^Nomination du ou des g\u00e9rant/).check();
    await main.getByLabel('Type d\u2019assembl\u00e9e *').selectOption('ordinaire');
    await convoquer(page);
    await validerEtape(page);
    await main.getByLabel('G\u00e9rant sortant ?').selectOption('non');
    await main.getByRole('button', { name: 'Ajouter', exact: true }).click();
    await (await champ(page, 'Dur\u00e9e du mandat')).fill('trois ann\u00e9es');
    await (await champ(page, 'Pr\u00e9nom', 0, true)).fill(ASSOCIES[1].prenom);
    await (await champ(page, 'Nom', 0, true)).fill(ASSOCIES[1].nom);
    await validerEtape(page);
    const avant = await lireApi<{ currentStep: number; data: Record<string, unknown> }>(page, `/workflows/${ticketId}`);

    // Annulation depuis le parcours : confirmation, motif, confirmation definitive.
    await main.getByRole('button', { name: 'Annuler le ticket' }).click();
    let d = page.getByRole('dialog');
    await d.getByRole('button', { name: 'Continuer' }).click();
    d = page.getByRole('dialog');
    await d.getByLabel(/Motif/).fill('Le client suspend l\u2019op\u00e9ration dans l\u2019attente de son financement.');
    await d.getByRole('button', { name: 'Suivant' }).click();
    const annulation = page.waitForResponse((r) => r.url().endsWith(`/tickets/${ticketId}/transition`) && r.request().method() === 'POST');
    await page.getByRole('dialog').getByRole('button', { name: 'Annuler le ticket' }).click();
    expect((await annulation).status()).toBeLessThan(400);
    await calme(page);
    const annule = await lireApi<{ statut: string; annulationMotif: string }>(page, `/tickets/${ticketId}`);
    expect(annule.statut).toBe('ANNULE');
    expect(annule.annulationMotif).toContain('financement');

    // Documents d'annulation depuis le detail du ticket.
    await aller(`/tickets?ticket=${ticketId}`);
    const detail = page.getByRole('dialog').last();
    await detail.getByLabel('Origine de l\u2019annulation').selectOption('D\u00e9cision du client');
    await detail.getByLabel('Op\u00e9ration abandonn\u00e9e').fill('Nomination d\u2019une cog\u00e9rante');
    await detail.getByLabel('\u00c9tat de la d\u00e9cision sociale').selectOption('aucune d\u00e9cision adopt\u00e9e');
    await detail.getByLabel('Des formalit\u00e9s ont-elles d\u00e9j\u00e0 produit effet ?').selectOption('non');
    await detail.getByLabel('Lieu de signature').fill('Casablanca');
    await detail.getByLabel('Date de signature').fill('2026-10-16');
    await detail.getByLabel('Client signataire du bordereau').fill('M. Mehdi Alaoui');
    await detail.getByLabel('Qualit\u00e9 du client signataire').fill('g\u00e9rant');
    // Une piece restituee au client : sans elle, le bordereau est refuse (pieces nommees).
    await detail.getByRole('button', { name: /Ajouter une pi\u00e8ce/ }).click();
    await detail.getByLabel('Pi\u00e8ce', { exact: true }).last().fill('Statuts certifi\u00e9s conformes');
    await detail.getByLabel('Forme', { exact: true }).last().fill('original');
    await detail.getByLabel('Nombre d\u2019exemplaires', { exact: true }).last().fill('1');
    const saisies = page.waitForResponse((r) => r.url().endsWith(`/workflows/${ticketId}/annulation/saisies`));
    await detail.getByRole('button', { name: 'Enregistrer les saisies' }).click();
    expect((await saisies).status()).toBeLessThan(400);
    const note = await genererDocument(page, info, 'Note d\u2019annulation', 'note-annulation', undefined, detail);
    expect(note.texte).toContain('financement');
    await genererDocument(page, info, 'Bordereau de restitution', 'bordereau-restitution', undefined, detail);

    // Reprise : le ticket retrouve son statut et le parcours son etape et ses saisies.
    await detail.getByRole('button', { name: 'Reprendre', exact: true }).click();
    let r = page.getByRole('dialog').last();
    await r.getByRole('button', { name: 'Continuer' }).click();
    r = page.getByRole('dialog').last();
    await r.getByLabel(/Motif/).fill('Le financement est obtenu : le client relance l\u2019op\u00e9ration.');
    await r.getByRole('button', { name: 'Suivant' }).click();
    const reprise = page.waitForResponse((x) => x.url().endsWith(`/tickets/${ticketId}/transition`) && x.request().method() === 'POST');
    await page.getByRole('dialog').last().getByRole('button', { name: /^Reprendre/ }).click();
    expect((await reprise).status()).toBeLessThan(400);
    await calme(page);
    const repris = await lireApi<{ statut: string; repriseMotif: string }>(page, `/tickets/${ticketId}`);
    expect(repris.statut).not.toBe('ANNULE');
    expect(repris.repriseMotif).toContain('financement');
    const apres = await lireApi<{ currentStep: number; data: Record<string, unknown> }>(page, `/workflows/${ticketId}`);
    expect(apres.currentStep).toBe(avant.currentStep);
    expect(JSON.stringify(apres.data.step2)).toBe(JSON.stringify(avant.data.step2));
    await aller(`/workflows/${ticketId}`);
    await expect(page.locator('[aria-current="step"]').first()).toHaveAttribute('aria-label', /^\u00c9tape 3/);
    await capturer('actes-annulation-reprise');
  });
});

/** Derniere etape d'un parcours : finalisation, puis parcours termine au serveur. */
async function finaliser(page: Page, ticketId: string): Promise<void> {
  const b = page.locator('main').getByRole('button', { name: /^(Finaliser|Terminer le parcours|Valider l\u2019\u00e9tape)$/ }).last();
  const rep = page.waitForResponse((r) => r.url().includes('/execute-step') && r.request().method() === 'POST', { timeout: 90_000 });
  await b.click();
  expect((await rep).status()).toBeLessThan(400);
  await calme(page);
  const w = await lireApi<{ statut: string }>(page, `/workflows/${ticketId}`);
  expect(w.statut, 'parcours termine').toBe('TERMINE');
}
