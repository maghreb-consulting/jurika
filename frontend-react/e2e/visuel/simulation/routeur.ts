import type { Page, Route } from '@playwright/test';
import { API_SIMULEE } from '../constantes';
import type { Profil } from './session';
import type { Theme } from '../constantes';

/**
 * Lot L6b (E4) : simulation de TOUTES les reponses de l'API (page.route) et du temps reel
 * (page.routeWebSocket). Aucune requete ne sort du navigateur :
 *   - l'API (domaine .invalid inscrit au build) est servie par la table de routes ;
 *   - le service temps reel (meme origine, /temps-reel/...) aussi ;
 *   - les fichiers du front (meme origine) passent vers vite preview ;
 *   - toute autre requete est REFUSEE et NOMMEE (motif 9 : rien de silencieux) ; le test
 *     de l'ecran echoue en la citant.
 */
export interface Requete {
  methode: string;
  /** Chemin sous /api/v1 (API) ou sous /temps-reel/api/v1 (prefixe "rt:"). */
  chemin: string;
  query: URLSearchParams;
  corps: unknown;
}

export interface Contexte {
  profil: Profil | null;
  theme: Theme;
}

export interface ReponseSimulee {
  statut?: number;
  corps?: unknown;
  /** Corps brut (texte, binaire) avec son type. */
  brut?: { type: string; contenu: string | Buffer };
}

export type Gestionnaire = (req: Requete, ctx: Contexte, params: string[]) => unknown;

export interface RouteSimulee {
  methode: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | '*';
  motif: RegExp;
  reponse: Gestionnaire;
}

/** Enveloppe une reponse non 200 (statut, corps). */
export function reponse(statut: number, corps?: unknown): ReponseSimulee & { __simulee: true } {
  return { __simulee: true, statut, corps };
}

export function brut(type: string, contenu: string | Buffer): ReponseSimulee & { __simulee: true } {
  return { __simulee: true, statut: 200, brut: { type, contenu } };
}

function estSimulee(v: unknown): v is ReponseSimulee {
  return !!v && typeof v === 'object' && (v as { __simulee?: boolean }).__simulee === true;
}

export interface Journal {
  /** Requetes non simulees (methode, adresse) : chacune est un echec de l'ecran. */
  nonSimulees: string[];
  /** Requetes servies (pour le diagnostic). */
  servies: string[];
}

export async function installerSimulation(page: Page, routes: RouteSimulee[], ctx: Contexte): Promise<Journal> {
  const journal: Journal = { nonSimulees: [], servies: [] };
  const origineApi = new URL(API_SIMULEE).origin;
  const prefixeApi = new URL(API_SIMULEE).pathname;

  async function servir(route: Route, chemin: string) {
    const r = route.request();
    const url = new URL(r.url());
    let corps: unknown = undefined;
    const donnees = r.postData();
    if (donnees) {
      try {
        corps = JSON.parse(donnees);
      } catch {
        corps = donnees;
      }
    }
    const req: Requete = { methode: r.method(), chemin, query: url.searchParams, corps };
    const origine = r.headers()['origin'] ?? '*';
    if (req.methode === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: entetesCors(origine) });
      return;
    }
    for (const rs of routes) {
      if (rs.methode !== '*' && rs.methode !== req.methode) continue;
      const m = rs.motif.exec(chemin);
      if (!m) continue;
      const v = rs.reponse(req, ctx, m.slice(1));
      journal.servies.push(`${req.methode} ${chemin}`);
      if (estSimulee(v)) {
        if (v.brut) {
          await route.fulfill({ status: v.statut ?? 200, headers: { ...entetesCors(origine), 'content-type': v.brut.type }, body: v.brut.contenu });
        } else {
          await route.fulfill({
            status: v.statut ?? 200,
            headers: { ...entetesCors(origine), 'content-type': 'application/json' },
            body: v.corps === undefined ? '' : JSON.stringify(v.corps),
          });
        }
      } else {
        await route.fulfill({
          status: 200,
          headers: { ...entetesCors(origine), 'content-type': 'application/json' },
          body: v === undefined ? '' : JSON.stringify(v),
        });
      }
      return;
    }
    journal.nonSimulees.push(`${req.methode} ${chemin}${url.search}`);
    await route.fulfill({
      status: 501,
      headers: { ...entetesCors(origine), 'content-type': 'application/json' },
      body: JSON.stringify({ code: 'NON_SIMULE', message: `Requete non simulee : ${req.methode} ${chemin}` }),
    });
  }

  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (url.origin === origineApi && url.pathname.startsWith(prefixeApi)) {
      await servir(route, url.pathname.slice(prefixeApi.length) || '/');
      return;
    }
    const origineFront = new URL(page.url() === 'about:blank' ? route.request().url() : page.url()).origin;
    if (url.origin === origineFront || url.hostname === '127.0.0.1') {
      if (url.pathname.startsWith('/temps-reel/api/v1')) {
        await servir(route, 'rt:' + url.pathname.slice('/temps-reel/api/v1'.length));
        return;
      }
      if (url.pathname.startsWith('/temps-reel/')) {
        // Transport socket.io en "polling" : le WebSocket simule ci-dessous suffit.
        await route.fulfill({ status: 404, body: '' });
        return;
      }
      await route.continue();
      return;
    }
    if (url.protocol === 'data:' || url.protocol === 'blob:') {
      await route.continue();
      return;
    }
    journal.nonSimulees.push(`${route.request().method()} ${url.href}`);
    await route.abort('blockedbyclient');
  });

  // Temps reel : poignee de main engine.io / socket.io simulee (connexion etablie, aucun evenement).
  await page.routeWebSocket(/.*/, (ws) => {
    const url = new URL(ws.url());
    if (!url.pathname.startsWith('/temps-reel/') && !url.pathname.startsWith('/socket.io')) {
      journal.nonSimulees.push(`WS ${ws.url()}`);
      ws.close();
      return;
    }
    ws.send(JSON.stringify({ sid: 'simule', upgrades: [], pingInterval: 25000, pingTimeout: 60000, maxPayload: 1000000 }).replace(/^/, '0'));
    ws.onMessage((m) => {
      const t = String(m);
      if (t.startsWith('40')) ws.send('40{"sid":"simule-socket"}');
      else if (t === '2') ws.send('3');
    });
  });

  return journal;
}

function entetesCors(origine: string): Record<string, string> {
  return {
    'access-control-allow-origin': origine,
    'access-control-allow-credentials': 'true',
    'access-control-allow-headers': '*',
    'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'access-control-expose-headers': '*',
  };
}
