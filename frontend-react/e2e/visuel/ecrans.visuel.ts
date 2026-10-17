import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { analyser, type Defaut } from './detecteurs';
import { ECRANS, type Ecran } from './ecrans';
import { HAUTEUR, LARGEURS, MAINTENANT, THEMES, type Theme } from './constantes';
import { PROFILS, poserSession } from './simulation/session';
import { installerSimulation } from './simulation/routeur';
import { ROUTES } from './simulation/donnees';

/**
 * Lot L6b (E4) : verification visuelle de chaque ecran principal, a 1366 et 1920 px, en
 * theme clair et sombre : chevauchement, debordement, contraste, defilement a la molette,
 * capture pleine page (captures du rapport). Donnees simulees, aucun appel reseau reel.
 *
 * Mode non bloquant (VISUEL_NON_BLOQUANT=1) : tous les defauts sont consignes au rapport
 * (e2e/visuel/synthese.ts), le test ne tombe pas : rapport et captures complets.
 */

const SORTIE = process.env.VISUEL_SORTIE ?? path.resolve('test-results/captures');
const RAPPORT = process.env.VISUEL_RAPPORT ?? path.resolve('test-results/visuel-rapport');
const NON_BLOQUANT = process.env.VISUEL_NON_BLOQUANT === '1';

/**
 * Couches prevues (exclues du chevauchement) : region des confirmations (toasts), non
 * cliquable ; L6c : couche sombre de la demonstration "theme clair et sombre" de la vitrine
 * (decorative, aria-hidden et inert), posee volontairement sur la couche claire et decoupee
 * a moitie (les deux themes cote a cote quand les animations sont reduites).
 */
const COUCHES = ['.fixed.pointer-events-none', '.v-demo-couche-sombre'];

/** Indicateurs de chargement en cours : l'ecran n'est pas encore stable. */
const CHARGEMENT = '.animate-pulse, .animate-spin, [aria-busy="true"]';

export type Categorie =
  | 'requete-non-simulee'
  | 'erreur-page'
  | 'navigation'
  | 'instable'
  | 'action'
  | 'molette'
  | Defaut['type'];

export interface Constat {
  categorie: Categorie;
  message: string;
}

export interface Fragment {
  ecran: string;
  groupe: string;
  chemin: string;
  profil: string;
  largeur: number;
  theme: Theme;
  capture: string | null;
  absent: boolean;
  constats: Constat[];
  examines?: Record<string, number>;
}

/**
 * Attend un ecran stable : plus d'indicateur de chargement visible, DOM inchange sur 3 releves.
 * Rend null si stable, sinon la raison (indicateur de chargement nomme, ou contenu qui change).
 */
async function attendreStabilite(page: Page, chargementMontre = false, limiteMs = 15_000): Promise<string | null> {
  const debut = Date.now();
  let precedent = '';
  let stables = 0;
  let raison = '';
  await page.evaluate(() => document.fonts.ready.then(() => undefined));
  while (Date.now() - debut < limiteMs) {
    const etat = await page.evaluate((sel) => {
      const enCours = Array.from(document.querySelectorAll(sel)).find((e) => {
        const r = e.getBoundingClientRect();
        // Un point qui pulse (pastille d'etat, moins de 12 px) est une decoration, pas un chargement.
        return r.width >= 12 && r.height >= 8 && getComputedStyle(e).visibility !== 'hidden';
      });
      const nom = enCours
        ? `${enCours.tagName.toLowerCase()}.${(enCours.getAttribute('class') ?? '').split(/\s+/).slice(0, 4).join('.')} dans "${(enCours.parentElement?.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 60)}"`
        : '';
      return { cle: `${!!enCours}|${document.body.querySelectorAll('*').length}|${document.body.innerText.length}`, nom };
    }, chargementMontre ? '[data-jamais-present]' : CHARGEMENT);
    if (etat.cle === precedent && etat.cle.startsWith('false')) {
      stables++;
      if (stables >= 3) return null;
    } else {
      stables = 0;
    }
    raison = etat.nom ? `indicateur de chargement toujours visible : ${etat.nom}` : `contenu qui change encore (${etat.cle})`;
    precedent = etat.cle;
    await page.waitForTimeout(200);
  }
  return raison;
}

/** Defilement a la molette : la page (ou, a defaut, le grand conteneur qui defile) bouge. */
async function verifierMolette(page: Page, largeur: number): Promise<Constat | null> {
  const mesure = await page.evaluate(() => {
    const doc = document.documentElement;
    const pageLongue = doc.scrollHeight > window.innerHeight + 40;
    let conteneur: { x: number; y: number; nom: string } | null = null;
    if (!pageLongue) {
      let meilleur = 0;
      for (const el of Array.from(document.querySelectorAll('*'))) {
        const s = getComputedStyle(el);
        if (!/(auto|scroll)/.test(s.overflowY) || el.scrollHeight <= el.clientHeight + 40) continue;
        const r = el.getBoundingClientRect();
        const aire = r.width * r.height;
        if (aire > meilleur && aire > window.innerWidth * window.innerHeight * 0.25) {
          meilleur = aire;
          conteneur = {
            x: r.left + r.width / 2,
            y: r.top + Math.min(r.height / 2, 200),
            nom: el.tagName.toLowerCase() + (el.getAttribute('data-testid') ? `[data-testid="${el.getAttribute('data-testid')}"]` : ''),
          };
        }
      }
    }
    // Point de la molette sur la page : au milieu de la hauteur, sur le premier endroit du
    // contenu qu'aucune couche ouverte (panneau, menu, fenetre, element fixe) ne recouvre.
    let pointPage: { x: number; y: number } | null = null;
    if (pageLongue) {
      const y = Math.round(window.innerHeight / 2);
      for (let x = Math.round(window.innerWidth * 0.3); x < window.innerWidth - 20; x += 40) {
        let couvert = false;
        for (let e = document.elementFromPoint(x, y); e; e = e.parentElement) {
          const st = getComputedStyle(e);
          if (st.position === 'fixed' || (st.position === 'absolute' && (parseInt(st.zIndex, 10) || 0) >= 20) || e.matches('dialog[open], :popover-open, [role="dialog"], [role="menu"]')) {
            couvert = true;
            break;
          }
        }
        if (!couvert) {
          pointPage = { x, y };
          break;
        }
      }
    }
    return { pageLongue, conteneur, pointPage };
  });
  if (!mesure.pageLongue && !mesure.conteneur) return null;
  const x = mesure.conteneur?.x ?? mesure.pointPage?.x ?? largeur / 2;
  const y = mesure.conteneur?.y ?? mesure.pointPage?.y ?? HAUTEUR / 2;
  const avant = await page.evaluate(
    ({ x, y }) => {
      const el = document.elementFromPoint(x, y);
      let c: Element | null = el;
      while (c && !(c.scrollHeight > c.clientHeight + 1 && /(auto|scroll)/.test(getComputedStyle(c).overflowY))) c = c.parentElement;
      return { page: window.scrollY, interne: c ? c.scrollTop : 0 };
    },
    { x, y },
  );
  await page.mouse.move(x, y);
  await page.mouse.wheel(0, 500);
  // Releve jusqu'a 2 s : un defilement doux (scroll-behavior: smooth) ou une machine chargee
  // repoussent le deplacement ; seule l'absence de tout deplacement est un defaut.
  let apres = avant;
  for (let i = 0; i < 10; i++) {
    await page.waitForTimeout(200);
    apres = await page.evaluate(
      ({ x, y }) => {
        const el = document.elementFromPoint(x, y);
        let c: Element | null = el;
        while (c && !(c.scrollHeight > c.clientHeight + 1 && /(auto|scroll)/.test(getComputedStyle(c).overflowY))) c = c.parentElement;
        return { page: window.scrollY, interne: c ? c.scrollTop : 0 };
      },
      { x, y },
    );
    if (apres.page > avant.page || apres.interne > avant.interne) break;
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  const bouge = mesure.pageLongue ? apres.page > avant.page : apres.interne > avant.interne || apres.page > avant.page;
  if (bouge) return null;
  return {
    categorie: 'molette',
    message: mesure.pageLongue
      ? `la molette ne fait pas defiler la page (scrollY reste ${avant.page}, page de ${await page.evaluate(() => document.documentElement.scrollHeight)} px)`
      : `la molette ne fait pas defiler le conteneur ${mesure.conteneur?.nom}`,
  };
}

function ecrireFragment(f: Fragment) {
  const dossier = path.join(RAPPORT, 'fragments');
  fs.mkdirSync(dossier, { recursive: true });
  fs.writeFileSync(path.join(dossier, `${f.ecran}-${f.profil}-${f.largeur}-${f.theme}.json`), JSON.stringify(f, null, 2));
}

function nomEcran(e: Ecran) {
  return `${e.id}-${e.profil ?? 'public'}`;
}

// Garde : deux ecrans ne partagent pas un nom de capture.
const noms = ECRANS.map(nomEcran);
const doublons = noms.filter((n, i) => noms.indexOf(n) !== i);
if (doublons.length) throw new Error(`Noms de captures en double : ${doublons.join(', ')}`);

for (const ecran of ECRANS) {
  for (const largeur of LARGEURS) {
    for (const theme of THEMES) {
      const titre = `${nomEcran(ecran)} ${largeur} ${theme} (${ecran.chemin})`;
      test(titre, async ({ page }) => {
        const profil = ecran.profil ? PROFILS[ecran.profil] : null;
        const constats: Constat[] = [];
        const erreurs: string[] = [];
        page.on('pageerror', (e) => erreurs.push(e.message));
        await page.setViewportSize({ width: largeur, height: HAUTEUR });
        await page.emulateMedia({ colorScheme: theme === 'sombre' ? 'dark' : 'light', reducedMotion: 'reduce' });
        await page.clock.setFixedTime(MAINTENANT);
        await poserSession(page, profil, theme);
        const journal = await installerSimulation(page, ROUTES, { profil, theme });

        await page.goto(ecran.chemin);
        const instable = await attendreStabilite(page, ecran.chargementMontre);
        if (instable) constats.push({ categorie: 'instable', message: `l'ecran ne se stabilise pas en 15 s : ${instable}` });

        const attendu = ecran.cheminAttendu ?? ecran.chemin.split('?')[0];
        const actuel = new URL(page.url()).pathname;
        const surPlace = typeof attendu === 'string' ? actuel === attendu : attendu.test(actuel);
        const absent = !surPlace && !!ecran.siPresent;
        if (!surPlace && !absent) constats.push({ categorie: 'navigation', message: `redirige vers ${actuel} au lieu de ${String(attendu)}` });

        const themeApplique = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
        const themeVoulu = theme === 'sombre' ? 'dark' : 'light';
        if (themeApplique !== themeVoulu) constats.push({ categorie: 'navigation', message: `theme applique ${themeApplique} au lieu de ${themeVoulu}` });

        if (ecran.action && !absent) {
          try {
            await ecran.action(page);
            const apres = await attendreStabilite(page, ecran.chargementMontre);
            if (apres) constats.push({ categorie: 'instable', message: `la vue ouverte par l'action ne se stabilise pas : ${apres}` });
          } catch (e) {
            constats.push({ categorie: 'action', message: (e as Error).message });
          }
        }

        let capture: string | null = null;
        let examines: Record<string, number> | undefined;
        if (!absent) {
          const r = await analyser(page, { couches: COUCHES, tolerance: 1 });
          examines = r.examines;
          for (const d of r.defauts) constats.push({ categorie: d.type, message: d.message });
          fs.mkdirSync(SORTIE, { recursive: true });
          capture = path.join(SORTIE, `${nomEcran(ecran)}-${largeur}-${theme}.png`);
          await page.screenshot({ path: capture, fullPage: true, animations: 'disabled', caret: 'hide' });
          const m = await verifierMolette(page, largeur);
          if (m) constats.push(m);
        }

        for (const n of journal.nonSimulees) constats.push({ categorie: 'requete-non-simulee', message: n });
        for (const e of erreurs) constats.push({ categorie: 'erreur-page', message: e });

        ecrireFragment({
          ecran: ecran.id,
          groupe: ecran.groupe,
          chemin: ecran.chemin,
          profil: ecran.profil ?? 'public',
          largeur,
          theme,
          capture,
          absent,
          constats,
          examines,
        });
        await test.info().attach('constats', { body: JSON.stringify(constats, null, 2), contentType: 'application/json' });
        if (capture) await test.info().attach('capture', { path: capture, contentType: 'image/png' });

        if (absent) test.info().annotations.push({ type: 'absent', description: `${ecran.chemin} n'existe pas encore` });
        if (!NON_BLOQUANT) {
          expect(constats.map((c) => `[${c.categorie}] ${c.message}`), `defauts de ${titre}`).toEqual([]);
        }
      });
    }
  }
}
