import { test, expect, type Page } from '@playwright/test';

/**
 * Lot L6b (E11, point 8) : JURI ne recouvre JAMAIS un bouton ni un contenu.
 *
 * Pour plusieurs ecrans de chaque role, en 1366 et 1920 px, en clair et en sombre : la zone
 * de JURI (robot, et bulle "Besoin d'aide ?" quand elle est visible) ne doit couper aucun
 * element interactif (lien, bouton, champ...) ni aucun element qui porte du texte ou une
 * image, au repos, au survol et au focus clavier, en haut et en bas de la page.
 * Le test echoue si JURI est absent (un controle qui ne verifie rien ne vaut rien).
 *
 * API simulee (page.route) : aucune pile n'est necessaire. Lancer contre `vite preview` :
 *   E2E_BASE_URL=http://127.0.0.1:4795 npx playwright test e2e/juri-chevauchement.spec.ts --project=chromium
 */

type Role = 'EMPLOYE' | 'SUPERVISEUR' | 'CLIENT' | 'SUPER_ADMIN';

const ECRANS: { role: Role; chemin: string }[] = [
  { role: 'EMPLOYE', chemin: '/dashboard' },
  { role: 'EMPLOYE', chemin: '/tickets' },
  { role: 'EMPLOYE', chemin: '/data-rooms' },
  { role: 'EMPLOYE', chemin: '/profile' },
  { role: 'SUPERVISEUR', chemin: '/dashboard' },
  { role: 'SUPERVISEUR', chemin: '/app/team' },
  { role: 'CLIENT', chemin: '/dashboard' },
  { role: 'SUPER_ADMIN', chemin: '/admin/workspaces' },
];

const TAILLES = [
  { width: 1366, height: 768 },
  { width: 1920, height: 1080 },
];

function jwt(role: Role): string {
  const b = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  return `${b({ alg: 'none' })}.${b({ role, email: `${role.toLowerCase()}@cabinet.ma`, uid: `u-${role}`, wsid: 'ws-1', iat: now, exp: now + 3600 })}.x`;
}

const TICKETS = Array.from({ length: 8 }, (_, i) => ({
  id: `t-${i}`,
  workspaceId: 'ws-1',
  reference: `TCK-2026-00${10 + i}`,
  titre: `Ticket de d\u00e9monstration ${i + 1}`,
  type: 'CREATION_SARL',
  statut: ['CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE', 'CLOTURE_DOSSIER'][i % 4],
  priorite: 'NORMALE',
  dossierId: null,
  assigneId: 'u-EMPLOYE',
  creeParId: 'u-EMPLOYE',
  description: null,
  deadline: null,
  annulationMotif: null,
  clotureAt: null,
  annuleAt: null,
  createdAt: '2026-10-01T09:00:00Z',
}));

const DOSSIERS = Array.from({ length: 6 }, (_, i) => ({
  id: `d-${i}`,
  raisonSociale: `SOCIETE EXEMPLE ${i + 1}`,
  reference: `2026-${i + 1} \u2014 SOCIETE EXEMPLE ${i + 1}`,
  formeJuridique: 'SARL',
  ice: null,
  ville: 'Casablanca',
  statut: 'ACTIF',
  accessStatus: 'ACTIVE',
}));

async function preparer(page: Page, role: Role, theme: 'CLAIR' | 'SOMBRE') {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const p = url.pathname.replace(/^.*\/api\/v1/, '');
    let body: unknown = [];
    if (p === '/users/me/preferences') body = { theme, juriMasque: false };
    else if (p === '/auth/me/aides') body = { cles: ['visite-employe', 'visite-superviseur', 'visite-client', 'visite-super-admin'] };
    else if (p === '/tickets') body = { items: TICKETS, total: TICKETS.length, limit: 50, offset: 0 };
    else if (p === '/dataroom/dossiers') body = DOSSIERS;
    else if (p.includes('count')) body = { count: 2 };
    else if (p.includes('unread')) body = { total: 0 };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.addInitScript(
    ([t, r]) => {
      localStorage.setItem('jurika_access_token', t);
      localStorage.setItem('jurika_refresh_token', 'r');
      localStorage.setItem(
        'jurika-auth',
        JSON.stringify({
          state: { user: { userId: `u-${r}`, workspaceId: 'ws-1', workspaceCode: 'JUR-DEMO1', email: `${r.toLowerCase()}@cabinet.ma`, role: r }, isAuthenticated: true },
          version: 0,
        }),
      );
    },
    [jwt(role), role] as const,
  );
}

/** Elements coupes par la zone de JURI (vide : aucun chevauchement). */
async function chevauchements(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const juri = document.querySelector('[data-testid="juri"]');
    if (!juri) return ['JURI absent'];
    const zones: DOMRect[] = [];
    const robot = juri.querySelector('[data-juri-bouton]');
    if (robot) zones.push(robot.getBoundingClientRect());
    const bulle = juri.querySelector('[data-testid="juri-bulle"]') as HTMLElement | null;
    if (bulle && getComputedStyle(bulle).visibility !== 'hidden' && Number(getComputedStyle(bulle).opacity) > 0) {
      zones.push(bulle.getBoundingClientRect());
    }
    const vw = window.innerWidth;
    // Partie visible a l'ecran seulement (le robot deborde a droite au repos).
    const visibles = zones.map((z) => ({ left: Math.max(0, z.left), right: Math.min(vw, z.right), top: z.top, bottom: z.bottom }));
    const interactif = 'a[href], button, input, select, textarea, summary, [role="button"], [role="link"], [role="tab"], [role="menuitem"], [tabindex]:not([tabindex="-1"])';
    const fautes: string[] = [];
    for (const el of Array.from(document.body.querySelectorAll<HTMLElement>('*'))) {
      if (juri.contains(el) || el.contains(juri)) continue;
      const style = getComputedStyle(el);
      if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) === 0) continue;
      if (el.closest('.sr-only') || el.classList.contains('sr-only')) continue;
      const texte = Array.from(el.childNodes).some((n) => n.nodeType === Node.TEXT_NODE && (n.textContent ?? '').trim() !== '');
      const contenu = texte || el.matches(interactif) || el.tagName === 'IMG' || el.tagName === 'svg';
      if (!contenu) continue;
      // Partie reellement visible : coupee par les ancetres qui rognent leur contenu
      // (tableau ou colonnes a defilement interne).
      const b0 = el.getBoundingClientRect();
      const r = { left: b0.left, right: b0.right, top: b0.top, bottom: b0.bottom, width: 0, height: 0 };
      for (let a = el.parentElement; a && a !== document.body; a = a.parentElement) {
        const s = getComputedStyle(a);
        if (s.overflowX !== 'visible' || s.overflowY !== 'visible') {
          const c = a.getBoundingClientRect();
          if (s.overflowX !== 'visible') {
            r.left = Math.max(r.left, c.left);
            r.right = Math.min(r.right, c.right);
          }
          if (s.overflowY !== 'visible') {
            r.top = Math.max(r.top, c.top);
            r.bottom = Math.min(r.bottom, c.bottom);
          }
        }
      }
      r.width = r.right - r.left;
      r.height = r.bottom - r.top;
      if (r.width < 1 || r.height < 1) continue;
      for (const z of visibles) {
        const l = Math.max(r.left, z.left);
        const d = Math.min(r.right, z.right);
        const h = Math.max(r.top, z.top);
        const b = Math.min(r.bottom, z.bottom);
        if (d - l > 0.5 && b - h > 0.5) {
          fautes.push(`${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''} "${(el.textContent ?? '').trim().slice(0, 40)}"`);
          break;
        }
      }
    }
    return fautes;
  });
}

for (const taille of TAILLES) {
  for (const theme of ['CLAIR', 'SOMBRE'] as const) {
    for (const ecran of ECRANS) {
      test(`JURI ne recouvre rien : ${ecran.role} ${ecran.chemin} ${taille.width} ${theme.toLowerCase()}`, async ({ page }) => {
        await page.setViewportSize(taille);
        await preparer(page, ecran.role, theme);
        await page.goto(ecran.chemin);
        const juri = page.getByTestId('juri');
        await expect(juri).toBeVisible();
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(300);

        const bouton = page.locator('[data-juri-bouton]');
        for (const position of ['haut', 'bas'] as const) {
          await page.evaluate((p) => window.scrollTo(0, p === 'haut' ? 0 : document.documentElement.scrollHeight), position);
          await page.mouse.move(10, taille.height / 2);
          await page.waitForTimeout(250);
          expect(await chevauchements(page), `repos, ${position}`).toEqual([]);

          await bouton.hover();
          await expect(bouton).toHaveAttribute('data-deploye', 'oui');
          await page.waitForTimeout(250);
          expect(await chevauchements(page), `survol, ${position}`).toEqual([]);
          await page.mouse.move(10, taille.height / 2);

          await bouton.focus();
          await page.waitForTimeout(250);
          expect(await chevauchements(page), `focus, ${position}`).toEqual([]);
          await bouton.blur();
        }
        // Pas de defilement horizontal provoque par JURI.
        const debord = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(debord).toBeLessThanOrEqual(0);
      });
    }
  }
}

// Garde du controle lui-meme (motif 9) : sans la bande reservee, il DOIT voir le chevauchement.
test('garde : sans la bande reservee a JURI, le controle detecte le chevauchement', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 });
  await preparer(page, 'EMPLOYE', 'CLAIR');
  await page.goto('/tickets');
  await expect(page.getByTestId('juri')).toBeVisible();
  await page.waitForLoadState('networkidle');
  await page.addStyleTag({
    content: '[data-reserve-juri]{padding-right:0 !important} #contenu{max-width:none !important;padding-right:0 !important}',
  });
  await page.locator('[data-juri-bouton]').hover();
  await page.waitForTimeout(250);
  expect((await chevauchements(page)).length).toBeGreaterThan(0);
});
