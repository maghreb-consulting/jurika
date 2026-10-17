import { test as base, expect, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { BASE_URL, DOSSIER_CAPTURES } from './outils/env';
import { connecter, creerCabinet, supprimerCabinet, type Cabinet, type Session } from './outils/comptes';
import { Sentinelle, TEMPS_REEL_ABSENT } from './outils/sentinelle';

/**
 * Lot L6b (E13) : fixtures des parcours de bout en bout (pile reelle).
 *
 *  - `comptes` (portee : processus de test) : cabinets crees a la demande par la route de seed,
 *    un par role principal (EMPLOYE, SUPERVISEUR, SUPER_ADMIN) ; le CLIENT est le client du
 *    dossier du cabinet EMPLOYE. Tous sont supprimes a la fin. Les sessions sont gardees en
 *    memoire et relues dans le navigateur apres chaque test (rotation du jeton de
 *    rafraichissement).
 *  - `sentinelle` (automatique) : la page de chaque test est surveillee ; le test ECHOUE a la
 *    fin s'il y a eu une reponse 4xx/5xx non toleree, une erreur de console ou une requete en
 *    echec. Le message nomme l'ecran, la requete, le corps envoye et la reponse.
 *  - `entrer(role)` : session injectee (localStorage jurika_access_token / jurika_refresh_token,
 *    comme le front apres connexion) avant le premier chargement.
 *  - `aller(chemin)` : navigation depuis l'origine du front (CORS), attente du reseau, visite
 *    guidee fermee par son bouton si elle s'ouvre.
 *  - `capturer(nom)` : capture de fin de parcours dans test-results/parcours/.
 */
export type Role = 'EMPLOYE' | 'SUPERVISEUR' | 'SUPER_ADMIN' | 'CLIENT';

export class Comptes {
  private readonly cabinets = new Map<string, Promise<Cabinet>>();
  private readonly sessions = new Map<Role, Session>();

  cabinet(role: Role): Promise<Cabinet> {
    const cle = role === 'CLIENT' ? 'EMPLOYE' : role;
    let c = this.cabinets.get(cle);
    if (!c) {
      c = creerCabinet(cle as Exclude<Role, 'CLIENT'>);
      this.cabinets.set(cle, c);
    }
    return c;
  }

  async session(role: Role): Promise<Session> {
    const s = this.sessions.get(role);
    if (s) return s;
    const c = await this.cabinet(role);
    const n = await connecter(c, role === 'CLIENT' ? 'client' : 'principal');
    this.sessions.set(role, n);
    return n;
  }

  /** Jetons relus dans le navigateur (le front a pu les faire tourner). */
  remplacer(role: Role, accessToken: string | null, refreshToken: string | null): void {
    const s = this.sessions.get(role);
    if (s && accessToken && refreshToken) this.sessions.set(role, { ...s, accessToken, refreshToken });
  }

  oublier(role: Role): void { this.sessions.delete(role); }

  async toutSupprimer(): Promise<string[]> {
    const echecs: string[] = [];
    for (const p of this.cabinets.values()) {
      const c = await p.catch(() => null);
      if (!c) continue;
      try { await supprimerCabinet(c); } catch (e) { echecs.push(`${c.workspaceCode} : ${(e as Error).message}`); }
    }
    this.cabinets.clear();
    return echecs;
  }
}

interface FixturesTest {
  sentinelle: Sentinelle;
  entrer: (role: Role) => Promise<Session>;
  aller: (chemin: string) => Promise<void>;
  capturer: (nom: string) => Promise<void>;
}

export const test = base.extend<FixturesTest, { comptes: Comptes }>({
  comptes: [async ({ playwright }, utiliser) => {
    void playwright; // fixture de processus : Playwright exige un premier argument deconstruit
    const comptes = new Comptes();
    await utiliser(comptes);
    const echecs = await comptes.toutSupprimer();
    if (echecs.length) console.warn(`Cabinets de test non supprimes :\n${echecs.join('\n')}`);
  }, { scope: 'worker' }],

  sentinelle: [async ({ page }, utiliser, info) => {
    const s = new Sentinelle(page);
    if (process.env.E2E_SANS_TEMPS_REEL === '1') s.tolererConsole(TEMPS_REEL_ABSENT);
    await utiliser(s);
    const r = await s.rapport();
    if (r) {
      await info.attach('incidents', { body: r, contentType: 'text/plain' });
      throw new Error(`Sentinelle : incidents pendant "${info.title}" :\n${r}`);
    }
  }, { auto: true }],

  entrer: async ({ page, comptes }, utiliser) => {
    let roleCourant: Role | null = null;
    await utiliser(async (role) => {
      const s = await comptes.session(role);
      roleCourant = role;
      await page.context().addInitScript(([a, r]) => {
        if (!window.localStorage.getItem('jurika_access_token')) {
          window.localStorage.setItem('jurika_access_token', a);
          window.localStorage.setItem('jurika_refresh_token', r);
        }
      }, [s.accessToken, s.refreshToken]);
      return s;
    });
    if (roleCourant && page.url().startsWith(BASE_URL)) {
      const [a, r] = await page.evaluate(() => [
        window.localStorage.getItem('jurika_access_token'),
        window.localStorage.getItem('jurika_refresh_token'),
      ]).catch(() => [null, null]);
      if (a && r) comptes.remplacer(roleCourant, a, r);
      else comptes.oublier(roleCourant);
    }
  },

  aller: async ({ page }, utiliser) => {
    await utiliser(async (chemin) => {
      await page.goto(BASE_URL + chemin, { waitUntil: 'domcontentloaded' });
      await calme(page);
      await fermerVisite(page);
    });
  },

  capturer: async ({ page }, utiliser, info) => {
    await utiliser(async (nom) => {
      fs.mkdirSync(DOSSIER_CAPTURES, { recursive: true });
      const fichier = path.join(DOSSIER_CAPTURES, `${nom.replace(/[^a-zA-Z0-9_-]+/g, '-')}.png`);
      await page.screenshot({ path: fichier, fullPage: true });
      await info.attach(nom, { path: fichier, contentType: 'image/png' });
    });
  },
});

/** Attend que le reseau se calme (les ecrans chargent en plusieurs vagues), borne a 15 s. */
export async function calme(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
}

/** Ferme la visite guidee si elle est ouverte (premiere connexion de chaque role). */
export async function fermerVisite(page: Page): Promise<void> {
  const passer = page.getByRole('button', { name: 'Passer la visite' });
  if (await passer.isVisible().catch(() => false)) {
    await passer.click();
    await expect(page.getByTestId('visite-guidee')).toBeHidden();
    await calme(page);
  }
}

export { expect };
