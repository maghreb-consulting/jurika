import type { Locator, Page } from '@playwright/test';
import { test, expect, calme, type Role } from './fixtures';

/**
 * Lot L6b (E13) : EXPLORATION de tous les ecrans, par role, sur la pile reelle.
 *
 * Pour chaque role (super-admin, superviseur, employe, client) : chaque route de src/App.tsx
 * ouverte au role, ses onglets (role tab, tous cliques), ses fenetres et tiroirs principaux
 * SANS effet (ouverts puis fermes par Echap), et l'en-tete (menu du compte, notifications,
 * JURI). La sentinelle (fixtures.ts) fait echouer l'ecran a la moindre reponse 4xx/5xx non
 * toleree, erreur de console ou requete en echec, en nommant la requete.
 *
 * Un bouton attendu et absent est un echec (soft) nomme : l'exploration continue l'ecran.
 * Les actions a effet (suppression, suspension, acceptation de contrat, paiement...) ne sont
 * jamais declenchees ici ; les formulaires sont soumis dans formulaires.parcours.ts.
 *
 * Relancer seule : npm run e2e:parcours -- --grep exploration
 */
interface Ecran {
  chemin: string | ((ids: { workspaceId: string }) => string);
  /** Fenetres et tiroirs a ouvrir (nom accessible exact du bouton, ou expression). */
  fenetres?: (string | RegExp)[];
  /** Avant les fenetres : action propre a l'ecran (ouvrir une Data Room...). */
  avant?: (page: Page) => Promise<void>;
  /** Chemin attendu apres chargement (redirection), si different. */
  attendu?: RegExp;
}

const OUVRIR_DATA_ROOM = async (page: Page) => {
  const carte = page.getByRole('button', { name: /^Ouvrir la Data Room de / }).first();
  await expect.soft(carte, 'carte de la Data Room du dossier du seed').toBeVisible();
  if (await carte.isVisible().catch(() => false)) {
    await carte.click();
    await calme(page);
  }
};

/**
 * Bouton "Identifiants" du dossier juridique : toujours present (employe, superviseur), suivi
 * pour l'employe d'une pastille du nombre d'identifiants a completer ("Identifiants 2") quand
 * il en manque (cas du dossier du seed, sans RC ni IF ; espace eventuel selon le calcul du nom).
 */
const IDENTIFIANTS = /^Identifiants\s*\d*$/;

const PARAMETRES_COMMUNS = ['/settings/profile', '/settings/security', '/settings/notifications', '/settings/preferences'];

const ECRANS: Record<Role, Ecran[]> = {
  EMPLOYE: [
    { chemin: '/dashboard' },
    { chemin: '/tickets', fenetres: ['Nouveau ticket'] },
    { chemin: '/data-rooms' },
    {
      chemin: '/data-rooms',
      avant: OUVRIR_DATA_ROOM,
      fenetres: ['Activit\u00e9 du client', 'Transf\u00e9rer \u00e0 un coll\u00e8gue', IDENTIFIANTS, 'Fiche client', 'Fiche dossier',
        'Ajouter un document', 'Ajouter plusieurs documents', 'Filtres'],
    },
    { chemin: '/calendar' },
    { chemin: '/chat' },
    { chemin: '/delais-greffe' },
    { chemin: '/profile' },
    ...PARAMETRES_COMMUNS.map((chemin) => ({ chemin })),
    { chemin: '/settings/billing' },
    { chemin: '/account/security' },
    { chemin: '/app/billing' },
    { chemin: '/app/billing/change-plan' },
    { chemin: '/app/archive' },
    // Routes d'un autre role : renvoi vers /forbidden, sans 4xx.
    { chemin: '/app/team', attendu: /\/forbidden$/ },
    { chemin: '/admin/workspaces', attendu: /\/forbidden$/ },
  ],
  SUPERVISEUR: [
    { chemin: '/dashboard' },
    { chemin: '/tickets' },
    { chemin: '/dossiers-a-verifier' },
    { chemin: '/calendar' },
    { chemin: '/data-rooms' },
    { chemin: '/data-rooms', avant: OUVRIR_DATA_ROOM, fenetres: ['Activit\u00e9 du client', IDENTIFIANTS, 'Fiche client', 'Fiche dossier', 'Filtres'] },
    { chemin: '/app/team', fenetres: ['Inviter un membre'] },
    { chemin: '/tracabilite', fenetres: [] },
    { chemin: '/chat' },
    { chemin: '/delais-greffe' },
    { chemin: '/app/contrat' },
    { chemin: '/profile' },
    ...PARAMETRES_COMMUNS.map((chemin) => ({ chemin })),
    { chemin: '/settings/cabinet' },
    { chemin: '/settings/team' },
    { chemin: '/settings/billing' },
    { chemin: '/account/security' },
    { chemin: '/app/billing' },
    { chemin: '/app/billing/change-plan' },
    { chemin: '/app/archive' },
    { chemin: '/admin/workspaces', attendu: /\/forbidden$/ },
  ],
  CLIENT: [
    { chemin: '/dashboard' },
    { chemin: '/data-rooms' },
    { chemin: '/mes-demandes', fenetres: ['Nouvelle demande'] },
    { chemin: '/mes-requetes' },
    { chemin: '/profile' },
    ...PARAMETRES_COMMUNS.map((chemin) => ({ chemin })),
    { chemin: '/tickets', attendu: /\/forbidden$/ },
    { chemin: '/app/billing', attendu: /\/forbidden$/ },
  ],
  SUPER_ADMIN: [
    { chemin: '/dashboard' },
    { chemin: '/admin/workspaces' },
    { chemin: ({ workspaceId }) => `/admin/workspaces/${workspaceId}` },
    { chemin: '/admin/abonnements' },
    { chemin: '/admin/paiements' },
    { chemin: '/admin/essais' },
    { chemin: '/admin/contrats' },
    { chemin: '/admin/users' },
    { chemin: '/admin/audit' },
    { chemin: '/admin/referentiel-demarches' },
    { chemin: '/admin/chatbot-sources' },
    { chemin: '/admin/editeur' },
    { chemin: '/design', fenetres: ['Ouvrir une fen\u00eatre', 'Ouvrir un tiroir'] },
    { chemin: '/profile' },
    ...PARAMETRES_COMMUNS.map((chemin) => ({ chemin })),
    { chemin: '/settings/platform' },
    { chemin: '/account/security' },
    { chemin: '/app/billing', attendu: /\/forbidden$/ },
    { chemin: '/tickets', attendu: /\/forbidden$/ },
  ],
};

/**
 * Clique chaque onglet actif de la zone principale, ou de la fenetre ouverte : derriere une fenetre
 * modale, les onglets de la page sont inertes (le clic attendait sans fin : defaut du test, vu le
 * 2026-10-16 sur /design et la Data Room).
 */
async function parcourirOnglets(page: Page, dans?: Locator): Promise<number> {
  const onglets = (dans ?? page.locator('main')).locator('[role="tab"]:not([disabled]):not([aria-disabled="true"])');
  const n = await onglets.count();
  // Onglets selectionnes a l'arrivee : re-selectionnes a la fin. Sans cela, les fenetres de
  // l'ecran etaient cherchees sur le DERNIER onglet clique (detail de la Data Room : "Requetes
  // du cabinet"), ou les boutons du dossier juridique (Identifiants, Fiche client...) n'existent
  // pas : faux "bouton introuvable" (defaut du test, vu le 2026-10-16).
  const initiaux: number[] = [];
  for (let i = 0; i < n; i++) {
    if ((await onglets.nth(i).getAttribute('aria-selected')) === 'true') initiaux.push(i);
  }
  for (let i = 0; i < n; i++) {
    const o = onglets.nth(i);
    if (!(await o.isVisible().catch(() => false))) continue;
    // L6c : les demonstrations de la vitrine reproduisent de vrais onglets dans une scene inerte
    // (role="img", inert) ; personne ne peut les atteindre, le test non plus (defaut du test, vu
    // le 2026-10-17 : clic attendu 180 s sur un onglet de la demonstration de la Data Room).
    if (await o.evaluate((e) => !!e.closest('[inert], [aria-hidden="true"]')).catch(() => true)) continue;
    await o.click();
    await calme(page);
  }
  for (const i of initiaux) {
    const o = onglets.nth(i);
    if (!(await o.isVisible().catch(() => false))) continue;
    if (await o.evaluate((e) => !!e.closest('[inert], [aria-hidden="true"]')).catch(() => true)) continue;
    await o.click();
    await calme(page);
  }
  return n;
}

/** Ouvre une fenetre par son bouton, verifie qu'elle s'affiche, la ferme par Echap. */
async function ouvrirFermer(page: Page, nom: string | RegExp): Promise<void> {
  const bouton = page.getByRole('button', { name: nom, exact: typeof nom === 'string' }).first();
  const visible = await bouton.isVisible().catch(() => false);
  expect.soft(visible, `bouton introuvable : ${String(nom)}`).toBe(true);
  if (!visible) return;
  await bouton.click();
  const fenetre = page.getByRole('dialog').last();
  await expect.soft(fenetre, `fenetre ouverte par ${String(nom)}`).toBeVisible();
  await calme(page);
  await parcourirOnglets(page, fenetre);
  await page.keyboard.press('Escape');
  await expect.soft(page.getByRole('dialog'), `fenetre fermee (Echap) : ${String(nom)}`).toHaveCount(0);
}

/** En-tete : menu du compte, notifications (hors client), JURI (hors client). */
async function parcourirEnTete(page: Page, role: Role): Promise<void> {
  const compte = page.getByRole('button', { name: /^Menu du compte/ });
  await expect.soft(compte, 'menu du compte').toBeVisible();
  if (await compte.isVisible().catch(() => false)) {
    await compte.click();
    await expect.soft(page.getByRole('menu')).toBeVisible();
    await page.keyboard.press('Escape');
  }
  if (role === 'CLIENT') return;
  const cloche = page.getByRole('button', { name: /^Notifications/ });
  if (await cloche.isVisible().catch(() => false)) {
    await cloche.click();
    await calme(page);
    const onglets = page.getByRole('tab', { name: /^(Toutes|Non lues)/ });
    for (let i = 0; i < (await onglets.count()); i++) await onglets.nth(i).click();
    await page.keyboard.press('Escape');
  } else {
    expect.soft(false, 'bouton des notifications introuvable').toBe(true);
  }
  await ouvrirFermer(page, /^Besoin d.aide \? Ouvrir JURI/);
}

for (const role of Object.keys(ECRANS) as Role[]) {
  test.describe(`exploration ${role}`, () => {
    ECRANS[role].forEach((e, n) => {
      const nom = typeof e.chemin === 'string' ? e.chemin : '/admin/workspaces/:id';
      test(`exploration ${role} ${nom}${e.avant ? ' (detail)' : ''}`, async ({ page, entrer, aller, capturer, comptes }) => {
        await entrer(role);
        const cabinet = await comptes.cabinet(role);
        const chemin = typeof e.chemin === 'string' ? e.chemin : e.chemin({ workspaceId: cabinet.workspaceId });
        await aller(chemin);
        if (e.attendu) {
          await expect(page).toHaveURL(e.attendu);
        } else {
          await expect(page, 'pas de renvoi vers la connexion ni vers /forbidden').not.toHaveURL(/\/(login|forbidden)(\?|$)/);
          await expect(page.locator('h1, h2').first()).toBeVisible();
        }
        if (e.avant) await e.avant(page);
        await parcourirOnglets(page);
        for (const f of e.fenetres ?? []) await ouvrirFermer(page, f);
        if (n === 0) await parcourirEnTete(page, role);
        await capturer(`exploration-${role}-${nom}${e.avant ? '-detail' : ''}`);
      });
    });
  });
}

/** Pages publiques (sans session) : elles tournent sur toute pile, sans seed. */
const PUBLIQUES = ['/', '/login', '/signup', '/essai', '/reset-password', '/auth/recover-with-code', '/forbidden', '/cgu',
  '/confidentialite', '/mentions-legales'];

test.describe('exploration PUBLIC', () => {
  for (const chemin of PUBLIQUES) {
    test(`exploration PUBLIC ${chemin}`, async ({ page, aller, capturer }) => {
      await aller(chemin);
      await expect(page.locator('h1, h2').first()).toBeVisible();
      await parcourirOnglets(page);
      await capturer(`exploration-PUBLIC-${chemin === '/' ? 'accueil' : chemin}`);
    });
  }
});
