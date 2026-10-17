import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../../lib/api', () => ({ api: { get } }));

import { LandingPage } from '../LandingPage';
import { oublierMentionsLegales } from '../mentionsLegales';
import { ACCROCHE, PARCOURS } from '../contenu';

/**
 * Lot L6b (E10, V3) : mouvement de la vitrine (demande du 2026-10-15).
 *
 * Accroche animee (embleme qui se dessine, titre mot par mot, capture en profondeur),
 * apparitions en cascade, recit au defilement des fonctionnalites, captures vivantes, motif
 * de zellige des bandeaux marine, bandeau defilant des parcours, retour en haut, transitions
 * entre les pages de la vitrine. Toujours : transform et opacity seulement, rien ne bouge
 * quand "reduire les animations" est actif, tout reste visible.
 */

type Requetes = Record<string, boolean>;

/** matchMedia simule : chaque requete connue repond selon `reponses` (faux sinon). */
function simulerMedias(reponses: Requetes) {
  const origine = window.matchMedia;
  window.matchMedia = ((q: string) => ({
    matches: Object.entries(reponses).some(([cle, v]) => v && q.includes(cle)),
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

/** IntersectionObserver simule : garde les rappels pour declencher une entree a la main. */
function simulerObservateurs() {
  const instances: { rappel: IntersectionObserverCallback; elements: Element[] }[] = [];
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn(function (this: unknown, rappel: IntersectionObserverCallback) {
      const inst = { rappel, elements: [] as Element[] };
      instances.push(inst);
      return {
        observe: (e: Element) => inst.elements.push(e),
        unobserve: vi.fn(),
        disconnect: vi.fn(),
      };
    }),
  );
  return {
    entrer(el: Element) {
      for (const i of instances) {
        if (!i.elements.includes(el)) continue;
        i.rappel([{ target: el, isIntersecting: true } as unknown as IntersectionObserverEntry], {} as IntersectionObserver);
      }
    },
  };
}

beforeEach(() => {
  oublierMentionsLegales();
  get.mockReset();
  get.mockReturnValue(new Promise(() => undefined));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function Emplacement() {
  const l = useLocation();
  return <p data-testid="emplacement">{l.pathname}</p>;
}

function rendre(depart = '/') {
  return render(
    <MemoryRouter initialEntries={[depart]}>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="*" element={<Emplacement />} />
      </Routes>
    </MemoryRouter>,
  );
}

const CSS = readFileSync(resolve(__dirname, '../vitrine.css'), 'utf-8').replace(/\/\*[\s\S]*?\*\//g, '');

/** Corps d'une regle @ (keyframes, media...) : texte entre l'accolade ouvrante et sa fermante. */
function blocsAt(css: string, motif: RegExp): { nom: string; corps: string }[] {
  const blocs: { nom: string; corps: string }[] = [];
  for (const m of css.matchAll(motif)) {
    let i = (m.index ?? 0) + m[0].length;
    let profondeur = 1;
    const debut = i;
    while (i < css.length && profondeur > 0) {
      if (css[i] === '{') profondeur++;
      if (css[i] === '}') profondeur--;
      i++;
    }
    blocs.push({ nom: m[1] ?? '', corps: css.slice(debut, i - 1) });
  }
  return blocs;
}

describe('Vitrine (V3) : accroche animee', () => {
  it("dessine l'embleme Moucharabieh : trace de l'arc, puis treillis d'or, puis l'embleme du depot", () => {
    rendre();
    const embleme = screen.getByTestId('embleme-anime');
    expect(embleme.getAttribute('aria-hidden')).toBe('true');
    const arc = embleme.querySelector('[data-trace="arc"]');
    expect(arc?.getAttribute('d')).toBe('M32.19 84 V61.13 A21 21 0 1 1 67.81 61.13 V84');
    expect(arc?.getAttribute('pathLength')).toBe('1');
    expect(embleme.querySelector('[data-treillis]')).not.toBeNull();
    // Etat final : l'embleme valide du depot (dessin inchange), pose par-dessus le trace.
    const logo = embleme.querySelector('img');
    expect(logo?.getAttribute('src')).toMatch(/moucharabieh\/embleme\.svg/);
    expect(logo?.getAttribute('alt')).toBe('');
  });

  it('fait apparaitre le titre mot par mot, sans changer son texte', () => {
    rendre();
    const h1 = screen.getByRole('heading', { level: 1 });
    const mots = Array.from(h1.querySelectorAll<HTMLElement>('.v-mot'));
    const attendu = `${ACCROCHE.titre} ${ACCROCHE.accent1} et ${ACCROCHE.accent2}`.split(/\s+/);
    expect(mots.map((m) => m.textContent)).toEqual(attendu);
    mots.forEach((m, i) => expect(m.style.getPropertyValue('--i'), m.textContent ?? '').toBe(String(i)));
    expect((h1.textContent ?? '').replace(/\s+/g, ' ').trim()).toBe(attendu.join(' '));
  });

  it('fait entrer la demonstration principale avec une profondeur (transform seul)', () => {
    rendre();
    const hero = screen.getByTestId('hero');
    const profondeur = hero.querySelector('.v-profondeur');
    expect(profondeur).not.toBeNull();
    // L6c : la demonstration de l'accroche (HTML, aucune image a charger) entre en profondeur.
    expect(profondeur?.querySelector('figure[data-demo="creation-ticket"]')).not.toBeNull();
    const regle = CSS.match(/\.v-profondeur\s*\{[^}]*\}/)?.[0] ?? '';
    expect(regle).toMatch(/animation\s*:\s*v-profondeur\b/);
    const images = blocsAt(CSS, /@keyframes\s+(v-profondeur)\s*\{/g)[0]?.corps ?? '';
    // La capture reste opaque : son affichage (LCP) n'attend pas l'animation.
    expect(images).not.toMatch(/opacity/);
    expect(images).toMatch(/perspective\(/);
  });
});

describe('Vitrine (V3) : apparitions en cascade', () => {
  it('donne un rang a chaque element qui apparait, pour la cascade', () => {
    simulerObservateurs();
    rendre();
    const roles = within(screen.getByRole('region', { name: 'Digitaliser et centraliser la gestion juridique des sociétés' }))
      .getAllByRole('listitem')
      .filter((li) => li.hasAttribute('data-apparait'));
    expect(roles.length).toBe(6);
    expect(roles.map((li) => li.style.getPropertyValue('--rang'))).toEqual(['0', '1', '2', '3', '4', '5']);
  });
});

// V4 (demande du 2026-10-16), L6c : une demonstration animee par fonctionnalite
// (demonstrations/scenes.ts), la Data Room garde sa capture.
const ORDRE_RECIT = ['theme-clair-sombre', 'frise-demarches', 'data-room-clair', 'statuts-apercu', 'juri-aide'];

describe('Vitrine (V3) : recit au defilement des fonctionnalites', () => {
  it("presente cinq etapes, dans l'ordre demande, et une capture fixe qui change d'ecran", () => {
    const io = simulerObservateurs();
    rendre();
    const section = screen.getByRole('region', { name: 'Ce que fait la plateforme' });
    const etapes = Array.from(section.querySelectorAll<HTMLElement>('[data-etape]'));
    expect(etapes.map((e) => e.getAttribute('data-capture'))).toEqual(ORDRE_RECIT);
    const ecrans = Array.from(section.querySelectorAll<HTMLElement>('[data-recit-ecran]'));
    expect(ecrans.map((e) => e.getAttribute('data-recit-ecran'))).toEqual(ORDRE_RECIT);
    expect(section.querySelector('.v-recit-fixe')).not.toBeNull();
    const actifs = () => ecrans.filter((e) => e.hasAttribute('data-actif')).map((e) => e.getAttribute('data-recit-ecran'));
    expect(actifs()).toEqual([ORDRE_RECIT[0]]);
    act(() => io.entrer(etapes[3]));
    expect(actifs()).toEqual([ORDRE_RECIT[3]]);
    // Seul l'ecran actif est expose aux lecteurs d'ecran.
    for (const e of ecrans) expect(e.getAttribute('aria-hidden'), e.getAttribute('data-recit-ecran') ?? '').toBe(e.hasAttribute('data-actif') ? null : 'true');
    expect(within(section).getByRole('button', { name: /Acte/ })).toHaveAttribute('aria-current', 'step');
  });

  it("ne joue que la demonstration de l'ecran actif de la pile, et la change avec l'etape lue", () => {
    const io = simulerObservateurs();
    rendre();
    const section = screen.getByRole('region', { name: 'Ce que fait la plateforme' });
    const pile = Array.from(section.querySelectorAll<HTMLElement>('[data-recit-ecran]'));
    // Toutes les boites de la pile entrent a l'ecran (meme place) : seule l'active joue.
    const entrerTout = () => {
      for (const e of pile) {
        const b = e.querySelector('.v-demo-boite');
        if (b) act(() => io.entrer(b));
      }
    };
    entrerTout();
    const jouees = () => pile.filter((e) => e.querySelector('figure[data-lecture="oui"]')).map((e) => e.getAttribute('data-recit-ecran'));
    expect(jouees()).toEqual(['theme-clair-sombre']);
    // Les ecrans caches ne recoivent ni le focus ni la lecture (inert).
    for (const e of pile) expect(e.hasAttribute('inert'), e.getAttribute('data-recit-ecran') ?? '').toBe(!e.hasAttribute('data-actif'));
    act(() => io.entrer(section.querySelector('[data-etape="3"]') as Element));
    entrerTout();
    expect(jouees()).toEqual(['statuts-apercu']);
  });
});

describe('Vitrine (V3) : captures vivantes', () => {
  it('incline la capture et deplace reflet et ombre au survol, avec une souris', async () => {
    const restaurer = simulerMedias({ 'hover: hover': true });
    try {
      rendre();
      const vivante = screen.getByTestId('hero').querySelector<HTMLElement>('.v-vivante');
      expect(vivante).not.toBeNull();
      expect(vivante?.querySelector('.v-reflet')).not.toBeNull();
      expect(vivante?.parentElement?.querySelector('.v-ombre')).not.toBeNull();
      vi.spyOn(vivante as HTMLElement, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 200, height: 100, right: 200, bottom: 100, x: 0, y: 0, toJSON: () => ({}) });
      fireEvent.pointerMove(vivante as HTMLElement, { clientX: 200, clientY: 0 });
      await waitFor(() => {
        expect(vivante?.style.getPropertyValue('--ry')).toBe('6deg');
        expect(vivante?.style.getPropertyValue('--rx')).toBe('4deg');
        expect(vivante).toHaveAttribute('data-survol');
      });
    } finally {
      restaurer();
    }
  });

  it("n'incline rien quand les animations sont reduites", () => {
    const restaurer = simulerMedias({ 'hover: hover': true, reduce: true });
    try {
      rendre();
      const vivante = screen.getByTestId('hero').querySelector<HTMLElement>('.v-vivante') as HTMLElement;
      fireEvent.pointerMove(vivante, { clientX: 10, clientY: 10 });
      expect(vivante.style.getPropertyValue('--ry')).toBe('');
      expect(vivante).not.toHaveAttribute('data-survol');
    } finally {
      restaurer();
    }
  });
});

describe('Vitrine (V3) : decor et bandeau des parcours', () => {
  it('pose un motif de zellige discret dans chaque bandeau marine de la page', () => {
    rendre();
    const bandeaux = Array.from(document.querySelectorAll('main .zone-marine'));
    expect(bandeaux.length).toBeGreaterThanOrEqual(2);
    for (const b of bandeaux) {
      const motif = b.querySelector('[data-motif-zellige]');
      expect(motif, b.className).not.toBeNull();
      expect(motif?.getAttribute('aria-hidden')).toBe('true');
      expect(motif?.querySelector('pattern')).not.toBeNull();
    }
  });

  it('fait defiler les parcours reellement couverts (contenu.ts), copie de boucle cachee aux lecteurs', () => {
    rendre();
    const bandeau = screen.getByTestId('bandeau-parcours');
    expect(bandeau).toHaveAttribute('data-defile');
    const listes = bandeau.querySelectorAll('ul');
    expect(listes).toHaveLength(2);
    const titres = PARCOURS.map((p) => p.titre);
    expect(Array.from(listes[0].querySelectorAll('li')).map((li) => li.textContent)).toEqual(titres);
    expect(listes[0].getAttribute('aria-hidden')).toBeNull();
    expect(listes[1].getAttribute('aria-hidden')).toBe('true');
    expect(Array.from(listes[1].querySelectorAll('li')).map((li) => li.textContent)).toEqual(titres);
  });

  it("montre le bandeau immobile, en une seule liste, quand les animations sont reduites", () => {
    const restaurer = simulerMedias({ reduce: true });
    try {
      rendre();
      const bandeau = screen.getByTestId('bandeau-parcours');
      expect(bandeau).not.toHaveAttribute('data-defile');
      expect(bandeau.querySelectorAll('ul')).toHaveLength(1);
    } finally {
      restaurer();
    }
  });
});

describe('Vitrine (V3) : interactions', () => {
  it('propose un retour en haut, inactif en haut de page, anime apres un defilement', async () => {
    const scrollTo = vi.fn();
    vi.stubGlobal('scrollTo', scrollTo);
    rendre();
    const bouton = screen.getByTestId('retour-haut');
    expect(bouton).toHaveAttribute('inert');
    Object.defineProperty(window, 'scrollY', { value: window.innerHeight * 2, configurable: true });
    act(() => {
      window.dispatchEvent(new Event('scroll'));
    });
    await waitFor(() => expect(bouton).not.toHaveAttribute('inert'));
    await userEvent.click(within(bouton).getByRole('button', { name: 'Revenir en haut de la page' }));
    expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true });
  });

  it('anime la navigation entre les pages de la vitrine (View Transitions)', async () => {
    const transitions: Array<() => unknown> = [];
    const doc = document as Document & { startViewTransition?: (f: () => unknown) => unknown };
    doc.startViewTransition = (f: () => unknown) => {
      transitions.push(f);
      return { finished: Promise.resolve() };
    };
    try {
      rendre();
      await userEvent.click(screen.getByRole('link', { name: 'Lire le contrat d’abonnement' }));
      expect(transitions).toHaveLength(1);
      await act(async () => {
        await transitions[0]();
      });
      expect(screen.getByTestId('emplacement')).toHaveTextContent('/cgu');
    } finally {
      delete doc.startViewTransition;
    }
  });

  it("navigue sans transition quand les animations sont reduites", async () => {
    const restaurer = simulerMedias({ reduce: true });
    const appel = vi.fn();
    const doc = document as Document & { startViewTransition?: (f: () => unknown) => unknown };
    doc.startViewTransition = appel;
    try {
      rendre();
      await userEvent.click(screen.getByRole('link', { name: 'Lire le contrat d’abonnement' }));
      expect(appel).not.toHaveBeenCalled();
      expect(screen.getByTestId('emplacement')).toHaveTextContent('/cgu');
    } finally {
      delete doc.startViewTransition;
      restaurer();
    }
  });

  it('donne aux boutons un survol et une pression, et ouvre les questions en douceur (vitrine.css)', () => {
    rendre();
    expect(screen.getByTestId('hero-essai-cta').className).toMatch(/\bv-bouton\b/);
    expect(CSS).toMatch(/\.v-bouton:hover\s*\{[^}]*translate:/);
    expect(CSS).toMatch(/\.v-bouton:active\s*\{[^}]*scale:/);
    expect(CSS).toMatch(/details\[open\]\s*>\s*\.v-reponse\s*\{[^}]*animation\s*:\s*v-ouverture/);
    expect(document.querySelectorAll('#faq details > .v-reponse').length).toBeGreaterThan(5);
    expect(CSS).toMatch(/\.v-carte-tarif:hover\s*\{[^}]*transform\s*:\s*translateY\(-/);
  });
});

describe('Vitrine (V3) : exigences du mouvement (vitrine.css)', () => {
  const PROPRIETES_MOUVEMENT = ['opacity', 'transform', 'translate', 'scale', 'rotate'];

  it("n'anime que transform et opacity (trace de l'arc seule exception : stroke-dashoffset)", () => {
    const keyframes = blocsAt(CSS, /@keyframes\s+([\w-]+)\s*\{/g);
    expect(keyframes.length).toBeGreaterThan(5);
    for (const k of keyframes) {
      const proprietes = [...k.corps.matchAll(/([a-z-]+)\s*:/g)].map((m) => m[1]);
      expect(proprietes.length, k.nom).toBeGreaterThan(0);
      const permises = k.nom === 'v-trace' ? [...PROPRIETES_MOUVEMENT, 'stroke-dashoffset'] : PROPRIETES_MOUVEMENT;
      for (const p of proprietes) expect(permises, `${k.nom} : ${p}`).toContain(p);
    }
    // Transitions : transform et opacity ; les boutons de la bibliotheque gardent leurs
    // transitions de couleur (seule regle qui les reprend : .v-bouton).
    const COULEURS_BOUTON = ['color', 'background-color', 'border-color'];
    for (const m of CSS.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      const selecteur = m[1].trim();
      for (const t of m[2].matchAll(/transition(?:-property)?\s*:\s*([^;]+);/g)) {
        for (const prop of t[1].split(',').map((x) => x.trim().split(/\s+/)[0])) {
          const permises = selecteur.includes('.v-bouton') ? [...PROPRIETES_MOUVEMENT, ...COULEURS_BOUTON, 'none'] : [...PROPRIETES_MOUVEMENT, 'none'];
          expect(permises, `${selecteur} : ${prop}`).toContain(prop);
        }
      }
    }
  });

  it('remplace tout mouvement par des fondus simples quand "reduire les animations" est actif (opacite seule)', () => {
    // V4 (demande du 2026-10-16) : sous "reduire les animations", aucune transformation ; les
    // animations restent, en fondus simples. Garde : dans ce bloc, toute animation designe
    // une image cle qui ne touche que l'opacite, toute transition ne porte que sur l'opacite.
    const bloc = blocsAt(CSS, /@media \(prefers-reduced-motion: reduce\)\s*\{/g).map((b) => b.corps).join('\n');
    expect(bloc).toMatch(/\.vitrine \*,\s*\.vitrine \*::before,\s*\.vitrine \*::after\s*\{[^}]*animation:\s*none !important;[^}]*transition:\s*none !important;/);
    const imagesCles = new Map(blocsAt(CSS, /@keyframes\s+([\w-]+)\s*\{/g).map((k) => [k.nom, k.corps]));
    const animations = [...bloc.matchAll(/animation(?:-name)?\s*:\s*([^;]+);/g)].map((m) => m[1].trim()).filter((v) => !/^none\b/.test(v));
    expect(animations.length).toBeGreaterThanOrEqual(2);
    for (const a of animations) {
      const nom = a.split(/\s+/)[0];
      const corps = imagesCles.get(nom);
      expect(corps, `${a} : image cle connue`).toBeDefined();
      const proprietes = [...(corps ?? '').matchAll(/([a-z-]+)\s*:/g)].map((m) => m[1]);
      expect(new Set(proprietes), nom).toEqual(new Set(['opacity']));
    }
    for (const t of bloc.matchAll(/transition(?:-property)?\s*:\s*([^;]+);/g)) {
      for (const prop of t[1].split(',').map((x) => x.trim().split(/\s+/)[0])) expect(['opacity', 'none'], t[1]).toContain(prop);
    }
    // Aucune transformation laissee active : toute valeur de transform, translate ou scale est neutre.
    for (const m of bloc.matchAll(/\b(transform|translate|scale)\s*:\s*([^;]+);/g)) {
      expect(m[2].trim(), `${m[1]} : ${m[2]}`).toMatch(/^(none( !important)?|rotate\(180deg\))$/);
    }
    // Le contenu qui apparait au defilement est visible d'emblee ; l'accroche entre en fondu ;
    // les captures ne s'inclinent pas.
    expect(bloc).toMatch(/\[data-apparait\][^{]*\{[^}]*opacity:\s*1/);
    expect(bloc).toMatch(/\.v-entree[^{]*\{[^}]*animation:\s*v-fondu/);
    expect(bloc).toMatch(/\.v-vivante[^{]*\{[^}]*transform:\s*none/);
  });

  it('ne decale jamais la mise en page : aucune propriete de boite animee, parallaxe sous @supports', () => {
    expect(CSS).not.toMatch(/transition[^;]*\b(width|height|top|left|right|bottom|margin|padding|max-height)\b/);
    for (const k of blocsAt(CSS, /@keyframes\s+([\w-]+)\s*\{/g)) {
      expect(k.corps, k.nom).not.toMatch(/\b(width|height|top|left|margin|padding)\s*:/);
    }
    // Parallaxe des fonds : animations liees au defilement (CSS), seulement si le navigateur
    // les connait ; ailleurs, les fonds restent fixes (aucun script de defilement).
    const supports = blocsAt(CSS, /@supports \(animation-timeline: (?:scroll|view)\(\)\)\s*\{/g).map((b) => b.corps);
    expect(supports.join('\n')).toMatch(/animation-timeline:\s*(scroll|view)\(/);
    const horsSupports = supports
      .reduce((css, corps) => css.replace(corps, ''), CSS)
      .replace(/@supports \(animation-timeline: (?:scroll|view)\(\)\)/g, '');
    expect(horsSupports).not.toMatch(/animation-timeline/);
  });
});
