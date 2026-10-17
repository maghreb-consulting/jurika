import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../../lib/api', () => ({ api: { get } }));

import { LandingPage } from '../LandingPage';
import { oublierMentionsLegales } from '../mentionsLegales';
import { DemonstrationAnimee } from '../demonstrations/DemonstrationAnimee';
import { NOMS_SCENES, SCENES, dureeCycle } from '../demonstrations/scenes';
import { LIBELLES_THEME_DEMO, SOCIETE_DEMO, TITRE_TICKETS_DEMO } from '../demonstrations/donnees';
import { menuDuRole } from '../../../components/layout/navigation';
import { LIBELLES_THEME } from '../../../store/themeStore';
import { titreTickets } from '../../../components/tickets/libellesTickets';
import { DEMANDE_STATUT_LABELS } from '../../../types/dataroom';
import { EmblemeAnime } from '../composants/EmblemeAnime';
import { CYCLE_LOGO_S, DEPART_BOUCLE_LOGO_S } from '../logo';

/**
 * Lot L6c (demande du 2026-10-17) : demonstrations animees en HTML (aucune video ni image
 * animee) et logo anime en boucle.
 *
 * - aucune balise video, aucune image animee (GIF) dans la vitrine ni dans ses fichiers ;
 * - chaque demonstration a son equivalent textuel (role="img", description) ;
 * - "reduire les animations" : aucune demonstration ne joue (etat final fixe) et le logo ne
 *   boucle pas ;
 * - pause hors ecran (IntersectionObserver simule) et onglet cache (visibilitychange) ;
 * - cycle du logo entre 8 et 10 secondes ;
 * - aucune requete reseau, aucun magasin de l'application, donnees fictives et fixes.
 */

const LANDING = resolve(__dirname, '..');
const PUBLIC = resolve(__dirname, '../../../../public');
const CSS = readFileSync(resolve(LANDING, 'vitrine.css'), 'utf-8').replace(/\/\*[\s\S]*?\*\//g, '');

function fichiers(d: string, garder: (nom: string) => boolean): string[] {
  return readdirSync(d, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? fichiers(join(d, e.name), garder) : garder(e.name) ? [join(d, e.name)] : [],
  );
}

/** matchMedia simule : "reduire les animations" selon `reduit`. */
function simulerMouvement(reduit: boolean) {
  const origine = window.matchMedia;
  window.matchMedia = ((q: string) => ({
    matches: reduit && q.includes('reduce'),
    media: q,
    onchange: null,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
  return () => {
    window.matchMedia = origine;
  };
}

/** IntersectionObserver simule : on fait entrer ou sortir un element a la main. */
function simulerObservateurs() {
  const instances: { rappel: IntersectionObserverCallback; elements: Element[] }[] = [];
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn(function (this: unknown, rappel: IntersectionObserverCallback) {
      const inst = { rappel, elements: [] as Element[] };
      instances.push(inst);
      return {
        observe: (el: Element) => inst.elements.push(el),
        unobserve: vi.fn(),
        disconnect: () => {
          inst.elements = [];
        },
      };
    }),
  );
  const signaler = (el: Element, dedans: boolean) => {
    for (const i of instances) {
      if (!i.elements.includes(el)) continue;
      i.rappel([{ target: el, isIntersecting: dedans } as unknown as IntersectionObserverEntry], {} as IntersectionObserver);
    }
  };
  return {
    entrer: (el: Element) => act(() => signaler(el, true)),
    sortir: (el: Element) => act(() => signaler(el, false)),
  };
}

function ongletCache(cache: boolean) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => (cache ? 'hidden' : 'visible') });
  act(() => {
    document.dispatchEvent(new Event('visibilitychange'));
  });
}

let restaurer: () => void = () => undefined;

// L6c E4 : le contenu des scenes est charge a la demande (React.lazy). Il est charge une fois
// ici : ensuite, chaque rendu le trouve pret (rendu synchrone, comme dans le navigateur une
// fois le module recu).
beforeAll(async () => {
  const r = simulerMouvement(true);
  const { unmount } = render(<DemonstrationAnimee nom="frise-demarches" />);
  await waitFor(() => expect(document.querySelector('.v-demo-plan')).not.toBeNull());
  unmount();
  r();
});

beforeEach(() => {
  oublierMentionsLegales();
  get.mockReset();
  get.mockReturnValue(new Promise(() => undefined));
  restaurer = simulerMouvement(false);
});

afterEach(() => {
  restaurer();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  Reflect.deleteProperty(document, 'visibilityState');
});

describe('Vitrine (L6c) : aucune video ni image animee', () => {
  it('ne rend aucune balise video ni image GIF, et chaque demonstration a son equivalent textuel', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    );
    expect(document.querySelectorAll('video, source[type^="video/"]')).toHaveLength(0);
    for (const img of Array.from(document.querySelectorAll('img'))) expect(img.getAttribute('src') ?? '').not.toMatch(/\.gif$/i);
    const figures = Array.from(document.querySelectorAll<HTMLElement>('figure[data-demo]'));
    expect(new Set(figures.map((f) => f.getAttribute('data-demo')))).toEqual(new Set(NOMS_SCENES));
    for (const f of figures) {
      const nom = f.getAttribute('data-demo') as (typeof NOMS_SCENES)[number];
      const image = f.querySelector('[role="img"]');
      expect(image?.getAttribute('aria-label'), nom).toBe(SCENES[nom].description);
      // Le texte equivalent dit que les donnees sont fictives, et ce que montre la scene.
      expect(SCENES[nom].description, nom).toMatch(/^Démonstration animée, données fictives : .{60,}/);
      expect(f.querySelector('.v-cadre-barre')?.textContent, nom).toContain('Données fictives');
    }
  });

  it('ne garde ni video ni GIF dans public, et aucun source de la vitrine ne les reference', () => {
    const medias = fichiers(PUBLIC, (n) => /\.(mp4|webm|gif|mov|ogv)$/i.test(n));
    expect(medias).toEqual([]);
    for (const f of fichiers(LANDING, (n) => /\.(tsx?|css)$/.test(n) && !n.endsWith('.test.tsx') && !n.endsWith('.test.ts'))) {
      const code = readFileSync(f, 'utf-8');
      expect(code, f).not.toMatch(/<video\b|\.mp4\b|\.webm\b|\.gif\b|vitrine\/videos/i);
    }
  });
});

describe('Vitrine (L6c) : demonstrations animees', () => {
  it("chaque scene est une boucle de 6 a 16 secondes, dont l'etat final est une etape de la scene", () => {
    for (const nom of NOMS_SCENES) {
      const s = SCENES[nom];
      expect(dureeCycle(nom), nom).toBeGreaterThanOrEqual(6000);
      expect(dureeCycle(nom), nom).toBeLessThanOrEqual(16000);
      expect(s.etapeFinale, nom).toBeGreaterThanOrEqual(0);
      expect(s.etapeFinale, nom).toBeLessThan(s.durees.length);
    }
  });

  it("joue a l'ecran, se met en pause hors ecran et quand l'onglet est cache", () => {
    vi.useFakeTimers();
    const io = simulerObservateurs();
    render(<DemonstrationAnimee nom="frise-demarches" />);
    const figure = document.querySelector('figure[data-demo="frise-demarches"]') as HTMLElement;
    const boite = figure.querySelector('.v-demo-boite') as HTMLElement;
    const etape = () => figure.getAttribute('data-etape-scene');
    const durees = SCENES['frise-demarches'].durees;

    // Hors ecran au montage : la scene n'est pas construite (cadre seul), rien ne joue,
    // l'horloge n'avance pas la scene.
    expect(figure.getAttribute('data-montee')).toBe('non');
    expect(figure.querySelector('.v-demo-plan')).toBeNull();
    expect(figure.getAttribute('data-lecture')).toBe('non');
    act(() => vi.advanceTimersByTime(dureeCycle('frise-demarches')));
    expect(etape()).toBe('0');
    expect(boite.querySelector('.v-demo-toile')?.hasAttribute('data-pause')).toBe(true);

    // A l'ecran : la scene avance d'etape en etape.
    io.entrer(boite);
    expect(figure.getAttribute('data-montee')).toBe('oui');
    expect(figure.querySelector('.v-demo-plan')).not.toBeNull();
    expect(figure.getAttribute('data-lecture')).toBe('oui');
    expect(boite.querySelector('.v-demo-toile')?.hasAttribute('data-pause')).toBe(false);
    act(() => vi.advanceTimersByTime(durees[0] + 10));
    expect(etape()).toBe('1');

    // Hors ecran : pause, l'etape reste.
    io.sortir(boite);
    expect(figure.getAttribute('data-lecture')).toBe('non');
    act(() => vi.advanceTimersByTime(dureeCycle('frise-demarches') * 2));
    expect(etape()).toBe('1');

    // De retour a l'ecran, puis onglet cache : pause.
    io.entrer(boite);
    act(() => vi.advanceTimersByTime(durees[1] + 10));
    expect(etape()).toBe('2');
    ongletCache(true);
    expect(figure.getAttribute('data-lecture')).toBe('non');
    act(() => vi.advanceTimersByTime(dureeCycle('frise-demarches') * 2));
    expect(etape()).toBe('2');
    ongletCache(false);
    expect(figure.getAttribute('data-lecture')).toBe('oui');

    // Boucle : apres la derniere etape, la scene revient au debut.
    for (const d of durees.slice(2)) act(() => vi.advanceTimersByTime(d + 10));
    expect(etape()).toBe('0');
  });

  it('se met en pause a la demande du visiteur, sans voler le focus, et ne contient rien de focalisable', () => {
    vi.useFakeTimers();
    const io = simulerObservateurs();
    render(<DemonstrationAnimee nom="statuts-apercu" />);
    const figure = document.querySelector('figure[data-demo="statuts-apercu"]') as HTMLElement;
    io.entrer(figure.querySelector('.v-demo-boite') as Element);
    expect(document.activeElement).toBe(document.body);
    const bouton = screen.getByRole('button', { name: /Mettre la démonstration en pause/ });
    fireEvent.click(bouton);
    expect(figure.getAttribute('data-lecture')).toBe('non');
    expect(bouton).toHaveAttribute('aria-pressed', 'true');
    act(() => vi.advanceTimersByTime(dureeCycle('statuts-apercu')));
    expect(figure.getAttribute('data-etape-scene')).toBe('0');
    // Contenu de la scene : inerte et cache aux lecteurs d'ecran ; ses boutons et champs
    // sont hors du parcours du clavier.
    const toile = figure.querySelector('.v-demo-toile') as HTMLElement;
    expect(toile.hasAttribute('inert')).toBe(true);
    expect(toile.getAttribute('aria-hidden')).toBe('true');
    // Les vrais composants (cartes de ticket, onglets) gardent leur tabIndex : c'est `inert` qui
    // les retire du clavier ; aucun ne demande le focus (autofocus).
    expect(toile.querySelector('[autofocus]')).toBeNull();
    for (const el of Array.from(toile.querySelectorAll<HTMLElement>('button, input, select, textarea, a[href], [tabindex]'))) {
      expect(el.closest('[inert]'), el.outerHTML.slice(0, 80)).toBe(toile);
    }
  });

  it("montre l'etat final fixe, sans minuterie ni bouton, quand les animations sont reduites", () => {
    restaurer();
    restaurer = simulerMouvement(true);
    vi.useFakeTimers();
    const io = vi.fn();
    vi.stubGlobal('IntersectionObserver', io);
    for (const nom of NOMS_SCENES) {
      const { unmount } = render(<DemonstrationAnimee nom={nom} />);
      const figure = document.querySelector(`figure[data-demo="${nom}"]`) as HTMLElement;
      expect(figure.getAttribute('data-lecture'), nom).toBe('non');
      expect(figure.getAttribute('data-etape-scene'), nom).toBe(String(SCENES[nom].etapeFinale));
      act(() => vi.advanceTimersByTime(dureeCycle(nom) * 2));
      expect(figure.getAttribute('data-etape-scene'), nom).toBe(String(SCENES[nom].etapeFinale));
      expect(figure.querySelector('.v-cadre-barre button'), nom).toBeNull();
      // Aucun curseur visible dans l'etat final.
      for (const c of Array.from(figure.querySelectorAll<HTMLElement>('[data-curseur]'))) expect(c.style.opacity, nom).toBe('0');
      unmount();
    }
    expect(io).not.toHaveBeenCalled();
  });

  it("montre les deux themes cote a cote quand les animations sont reduites (theme clair et sombre)", () => {
    restaurer();
    restaurer = simulerMouvement(true);
    render(<DemonstrationAnimee nom="theme-clair-sombre" />);
    const plan = document.querySelector('figure[data-demo="theme-clair-sombre"] .v-demo-plan') as HTMLElement;
    expect(plan.hasAttribute('data-partage')).toBe(true);
    expect(plan.querySelector('[data-theme="light"]')).not.toBeNull();
    expect(plan.querySelector('[data-theme="dark"]')).not.toBeNull();
    expect(CSS).toMatch(/\.v-demo-plan\[data-partage\] \.v-demo-couche-sombre\s*\{[^}]*clip-path:\s*inset\(0 0 0 50%\)/);
  });

  it("n'envoie aucune requete et ne lit aucun magasin de l'application", () => {
    const fetchEspion = vi.fn();
    vi.stubGlobal('fetch', fetchEspion);
    simulerObservateurs();
    for (const nom of NOMS_SCENES) render(<DemonstrationAnimee nom={nom} />);
    expect(fetchEspion).not.toHaveBeenCalled();
    expect(get).not.toHaveBeenCalled();
    for (const f of fichiers(resolve(LANDING, 'demonstrations'), (n) => /\.tsx?$/.test(n))) {
      const code = readFileSync(f, 'utf-8');
      expect(code, f).not.toMatch(/from '[./]*(store|services)\/|lib\/api|axios|fetch\(/);
    }
  });

  it('emploie des donnees fictives et fixes, sans donnee du cabinet de demonstration', () => {
    expect(SOCIETE_DEMO.denomination).toMatch(/EXEMPLE/);
    const sources = fichiers(resolve(LANDING, 'demonstrations'), (n) => /\.tsx?$/.test(n)).map((f) => readFileSync(f, 'utf-8')).join('\n');
    for (const interdit of [/JUR-DEMO/i, /demo\.jurika/i, /Atlas Conseil/i, /Khouribga/i, /Casa Digital/i, /Marrakech Tourism/i, /Oujda Mining/i, /Date\.now|new Date\(/]) {
      expect(sources, String(interdit)).not.toMatch(interdit);
    }
  });
});

describe('Vitrine (L6c) : logo anime en boucle', () => {
  function animationsBoucle(): { classe: string; valeur: string }[] {
    return ['v-embleme-reflet', 'v-embleme-halo', 'v-embleme-etoile'].map((classe) => {
      const regle = CSS.match(new RegExp(`(?:^|\\})\\s*\\.${classe}\\s*\\{([^}]*)\\}`))?.[1] ?? '';
      return { classe, valeur: regle.match(/animation\s*:\s*([^;]+);/)?.[1] ?? '' };
    });
  }

  it('boucle sur un cycle de 8 a 10 secondes, apres le dessin du logo', () => {
    expect(CYCLE_LOGO_S).toBeGreaterThanOrEqual(8);
    expect(CYCLE_LOGO_S).toBeLessThanOrEqual(10);
    for (const { classe, valeur } of animationsBoucle()) {
      expect(valeur, classe).toMatch(new RegExp(`^${classe}\\s`));
      const durees = [...valeur.matchAll(/(\d+(?:\.\d+)?)s\b/g)].map((m) => Number(m[1]));
      expect(durees, classe).toEqual([CYCLE_LOGO_S, DEPART_BOUCLE_LOGO_S]);
      expect(valeur, classe).toMatch(/\binfinite\b/);
    }
  });

  it("n'anime que l'opacite et la transformation, et reste le logo exact au repos (couche transparente)", () => {
    for (const nom of ['v-embleme-reflet', 'v-embleme-halo', 'v-embleme-etoile']) {
      const corps = CSS.match(new RegExp(`@keyframes ${nom}\\s*\\{([\\s\\S]*?)\\n\\}`))?.[1] ?? '';
      const proprietes = new Set([...corps.matchAll(/([a-z-]+)\s*:/g)].map((m) => m[1]));
      expect(proprietes.size, nom).toBeGreaterThan(0);
      for (const p of proprietes) expect(['opacity', 'transform'], `${nom} : ${p}`).toContain(p);
    }
    expect(CSS).toMatch(/\.v-embleme-reflet,\s*\.v-embleme-halo,\s*\.v-embleme-etoile\s*\{[^}]*opacity:\s*0;/);
  });

  it("se met en pause hors ecran et quand l'onglet est cache", () => {
    const io = simulerObservateurs();
    render(<EmblemeAnime />);
    const logo = screen.getByTestId('embleme-anime');
    expect(logo.querySelector('[data-boucle-logo]')).not.toBeNull();
    expect(logo.hasAttribute('data-pause')).toBe(true);
    io.entrer(logo);
    expect(logo.hasAttribute('data-pause')).toBe(false);
    io.sortir(logo);
    expect(logo.hasAttribute('data-pause')).toBe(true);
    io.entrer(logo);
    ongletCache(true);
    expect(logo.hasAttribute('data-pause')).toBe(true);
    ongletCache(false);
    expect(logo.hasAttribute('data-pause')).toBe(false);
    expect(CSS).toMatch(/\.v-embleme\[data-pause\] \.v-embleme-boucle \*\s*\{[^}]*animation-play-state:\s*paused/);
  });

  it('reste fixe quand les animations sont reduites : aucune regle ne relance la boucle', () => {
    restaurer();
    restaurer = simulerMouvement(true);
    const io = vi.fn();
    vi.stubGlobal('IntersectionObserver', io);
    render(<EmblemeAnime />);
    expect(screen.getByTestId('embleme-anime').hasAttribute('data-pause')).toBe(true);
    expect(io).not.toHaveBeenCalled();
    // Bloc "reduire les animations" : toute animation est coupee (.vitrine * animation none),
    // et aucune regle n'y rend une animation a la boucle du logo ni aux demonstrations.
    const debut = CSS.indexOf('@media (prefers-reduced-motion: reduce)');
    const bloc = CSS.slice(debut);
    expect(bloc).toMatch(/\.vitrine \*,\s*\.vitrine \*::before,\s*\.vitrine \*::after\s*\{[^}]*animation:\s*none !important;/);
    expect(bloc).not.toMatch(/v-embleme-(reflet|halo|etoile|boucle)|v-curseur|v-demo/);
  });
});

describe('Vitrine (L6c E3) : les demonstrations reprennent les ecrans reels', () => {
  // Entree active du menu, par scene : celle de la page reelle montree.
  const ACTIF: Record<(typeof NOMS_SCENES)[number], string> = {
    'creation-ticket': 'Mes tickets',
    'frise-demarches': 'Mes tickets',
    'statuts-apercu': 'Mes tickets',
    'juri-aide': 'Tableau de bord',
    'theme-clair-sombre': 'Tableau de bord',
    'data-room': 'Mes Data Rooms',
  };

  it("affiche le menu reel de l'employe (groupes, libelles, ordre) avec l'entree active de chaque scene", () => {
    restaurer();
    restaurer = simulerMouvement(true);
    const attendu = menuDuRole('EMPLOYE').map((g) => ({ titre: g.titre, entrees: g.entrees.map((e) => e.libelle) }));
    for (const nom of NOMS_SCENES) {
      const { unmount } = render(<DemonstrationAnimee nom={nom} />);
      const menus = Array.from(document.querySelectorAll<HTMLElement>('[data-menu-demo]'));
      expect(menus.length, nom).toBeGreaterThan(0);
      for (const menu of menus) {
        const groupes = Array.from(menu.querySelectorAll<HTMLElement>('[data-groupe-demo]')).map((g) => ({
          titre: g.getAttribute('data-groupe-demo'),
          entrees: Array.from(g.querySelectorAll('[data-entree-demo]')).map((e) => e.textContent),
        }));
        expect(groupes, nom).toEqual(attendu);
        const actifs = Array.from(menu.querySelectorAll('[data-entree-demo][data-actif]')).map((e) => e.textContent);
        expect(actifs, nom).toEqual([ACTIF[nom]]);
      }
      unmount();
    }
  });

  it("n'affiche aucun ecran ni libelle absent de l'application", () => {
    restaurer();
    restaurer = simulerMouvement(true);
    for (const nom of NOMS_SCENES) {
      const { container, unmount } = render(<DemonstrationAnimee nom={nom} />);
      const t = container.textContent ?? '';
      for (const faux of ['Clients', 'Échéances', 'Actes de la création', 'Modèle du cabinet', 'Cocher la démarche', 'Démarche cochée', 'À générer']) {
        expect(t, `${nom} : ${faux}`).not.toContain(faux);
      }
      unmount();
    }
    // Libelles du choix du theme et titre de la page des tickets : ceux de l'application.
    expect(LIBELLES_THEME_DEMO).toEqual([LIBELLES_THEME.SYSTEME, LIBELLES_THEME.CLAIR, LIBELLES_THEME.SOMBRE]);
    expect(TITRE_TICKETS_DEMO).toBe(titreTickets('EMPLOYE'));
  });
});

describe('Vitrine (L6c E5) : la demonstration de la Data Room reprend les libelles reels', () => {
  const RACINE = resolve(__dirname, '../../../..');
  const source = (chemin: string) => readFileSync(resolve(RACINE, chemin), 'utf-8');
  const SCENE = readFileSync(resolve(LANDING, 'demonstrations/SceneDataRoom.tsx'), 'utf-8');
  // Libelle de l'ecran de demonstration -> fichier de l'application (ou du serveur) qui l'affiche.
  const LIBELLES: [string, string][] = [
    ['Accès du client actif', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Retour aux Data Rooms', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Activité du client', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Ajouter, supprimer, conserver', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Client rattaché', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Dossier juridique', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Dépôts du client', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Demandes du client', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Requêtes du cabinet', 'src/pages/dataroom/DataroomPage.tsx'],
    ['Transférer à un collègue', 'src/components/transfer/TransferDossierButton.tsx'],
    ['Accès du client', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Accès actif', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Suspendre l’accès', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Régler l’accès du client', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Consulter les documents', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Télécharger', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Imprimer', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Déposer des documents', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Envoyer des demandes', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Lien d’accès du client', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Copier le lien', 'src/pages/dataroom/components/AccesClientPanel.tsx'],
    ['Rechercher dans les documents juridiques (titre, nom de fichier)…', 'src/pages/dataroom/components/JuridiqueSearchBar.tsx'],
    ['Documents en vigueur', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Identifiants', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Fiche client', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Fiche dossier', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Ajouter plusieurs documents', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Ajouter un document', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Aperçu', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Supprimer le document', 'src/pages/dataroom/DossierJuridiqueTab.tsx'],
    ['Anciennes versions', 'src/pages/dataroom/components/DocumentVersionsDropdown.tsx'],
    ['Destination', 'src/pages/dataroom/components/UploadDocumentDialog.tsx'],
    ['Choisissez « Nouvelle version de… » pour remplacer un document : un motif est alors demandé.', 'src/pages/dataroom/components/UploadDocumentDialog.tsx'],
    ['Motif du remplacement', 'src/pages/dataroom/components/UploadDocumentDialog.tsx'],
    ['Au moins 5 caractères : le motif est conservé avec l’ancienne version.', 'src/pages/dataroom/components/UploadDocumentDialog.tsx'],
    ['Remplacer comme nouvelle version', 'src/pages/dataroom/components/UploadDocumentDialog.tsx'],
    ['Un nouveau document, ou une nouvelle version d’un document en vigueur (l’ancienne reste consultable).', 'src/pages/dataroom/components/UploadDocumentDialog.tsx'],
    ['Créer un ticket', 'src/pages/dataroom/DemandesTab.tsx'],
    ['Modifier', 'src/pages/dataroom/DemandesTab.tsx'],
    ['Soumise le', 'src/pages/dataroom/DemandesTab.tsx'],
    ['Actes générés', '../backend-java/dataroom-service/src/main/java/ma/jurika/dataroom/application/GroupeDocument.java'],
    ['Pièces client', '../backend-java/dataroom-service/src/main/java/ma/jurika/dataroom/application/GroupeDocument.java'],
  ];

  it.each(LIBELLES)('« %s » est affiche par la scene et par l application (%s)', (libelle, chemin) => {
    expect(SCENE, 'scene').toContain(libelle);
    expect(source(chemin), chemin).toContain(libelle);
  });

  it("montre la demande du client recue, avec son etat reel, dans l'etat final", () => {
    restaurer();
    restaurer = simulerMouvement(true);
    const { container } = render(<DemonstrationAnimee nom="data-room" />);
    const t = container.textContent ?? '';
    expect(t).toContain(DEMANDE_STATUT_LABELS.NON_TRAITEE);
    expect(t).toContain('Demandes du client');
    expect(t).toContain('Déposer des documents');
  });
});
