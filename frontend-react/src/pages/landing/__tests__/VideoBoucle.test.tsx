import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { VideoBoucle } from '../composants/VideoBoucle';
import { VIDEOS } from '../videos';

/**
 * Lot L6b (E10, V4, demande du 2026-10-16) : video en boucle de la vitrine. Lecture
 * automatique muette et en boucle quand elle est a l'ecran, pause hors ecran, rien de
 * telecharge avant ; "reduire les animations" : image fixe et bouton "Lire la video" ;
 * pause toujours possible (WCAG 2.2.2).
 */

type Rappel = (entrees: { isIntersecting: boolean }[]) => void;
let rappels: Rappel[] = [];
let play: ReturnType<typeof vi.spyOn>;
let pause: ReturnType<typeof vi.spyOn>;

function medias(reduit: boolean) {
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

function ecran(visible: boolean) {
  act(() => {
    for (const r of rappels) r([{ isIntersecting: visible }]);
  });
}

beforeEach(() => {
  rappels = [];
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn(function (this: unknown, r: Rappel) {
      rappels.push(r);
      return { observe: vi.fn(), unobserve: vi.fn(), disconnect: vi.fn() };
    }),
  );
  play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(function (this: HTMLMediaElement) {
    this.dispatchEvent(new Event('playing'));
    return Promise.resolve();
  });
  pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(function (this: HTMLMediaElement) {
    this.dispatchEvent(new Event('pause'));
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('VideoBoucle', () => {
  it("ne charge rien hors de l'ecran, puis lit la video muette et en boucle des qu'elle est visible", () => {
    const { container } = render(<VideoBoucle nom="frise-demarches" />);
    // Avant : l'image fixe seule, place reservee, aucun element video (rien n'est telecharge).
    const img = container.querySelector('img') as HTMLImageElement;
    expect(img).toHaveAttribute('src', '/vitrine/videos/frise-demarches.webp');
    expect(img).toHaveAttribute('width', '1280');
    expect(img).toHaveAttribute('height', '800');
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(container.querySelector('video')).toBeNull();

    ecran(true);
    const v = container.querySelector('video') as HTMLVideoElement;
    expect(v).not.toBeNull();
    expect(v.muted).toBe(true);
    expect(v.loop).toBe(true);
    expect(v).toHaveAttribute('playsinline');
    expect(v).toHaveAttribute('preload', 'none');
    expect(v).toHaveAttribute('poster', '/vitrine/videos/frise-demarches.webp');
    expect(v).toHaveAttribute('aria-label', VIDEOS['frise-demarches'].alt);
    expect(Array.from(v.querySelectorAll('source')).map((s) => [s.getAttribute('src'), s.getAttribute('type')])).toEqual([
      ['/vitrine/videos/frise-demarches.mp4', 'video/mp4'],
      ['/vitrine/videos/frise-demarches.webm', 'video/webm'],
    ]);
    expect(v.querySelector('track[kind="audio"]')).toBeNull();
    expect(play).toHaveBeenCalledTimes(1);
    expect(v).toHaveAttribute('data-actif');
    expect(screen.getByRole('button', { name: /^Mettre la vidéo en pause/ })).toBeInTheDocument();

    // Hors de l'ecran : pause.
    ecran(false);
    expect(pause).toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /^Lire la vidéo/ })).toBeInTheDocument();
  });

  it('respecte la pause demandee par le visiteur : la video ne repart pas seule', () => {
    render(<VideoBoucle nom="juri-aide" />);
    ecran(true);
    fireEvent.click(screen.getByRole('button', { name: /^Mettre la vidéo en pause/ }));
    expect(pause).toHaveBeenCalled();
    play.mockClear();
    ecran(false);
    ecran(true);
    expect(play).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /^Lire la vidéo/ }));
    expect(play).toHaveBeenCalledTimes(1);
  });

  it("ne lit pas la video d'un ecran inactif (pile du recit)", () => {
    const { container } = render(<VideoBoucle nom="statuts-apercu" actif={false} />);
    ecran(true);
    expect(container.querySelector('video')).toBeNull();
    expect(play).not.toHaveBeenCalled();
  });

  it('"reduire les animations" : image fixe et bouton "Lire la video" ; lecture a la demande seulement', () => {
    const restaurer = medias(true);
    try {
      const { container } = render(<VideoBoucle nom="theme-clair-sombre" />);
      expect(rappels).toHaveLength(0);
      expect(container.querySelector('video')).toBeNull();
      expect(container.querySelector('img')).toHaveAttribute('alt', VIDEOS['theme-clair-sombre'].alt);
      const bouton = screen.getByRole('button', { name: `Lire la vidéo : ${VIDEOS['theme-clair-sombre'].legende}` });
      expect(play).not.toHaveBeenCalled();
      fireEvent.click(bouton);
      expect(container.querySelector('video')).not.toBeNull();
      expect(play).toHaveBeenCalledTimes(1);
      // Le grand bouton disparait : le focus passe au bouton de pause de la barre du cadre.
      const pauseBarre = screen.getByRole('button', { name: /^Mettre la vidéo en pause/ });
      expect(pauseBarre).toHaveFocus();
      expect(screen.getAllByRole('button')).toHaveLength(1);
    } finally {
      restaurer();
    }
  });

  it("accroche : image fixe prioritaire, lecture seulement apres le chargement de la page", () => {
    const etat = vi.spyOn(document, 'readyState', 'get').mockReturnValue('loading');
    const { container } = render(<VideoBoucle nom="creation-ticket" apresChargement prioritaire />);
    const img = container.querySelector('img');
    expect(img).toHaveAttribute('loading', 'eager');
    expect(img).toHaveAttribute('fetchpriority', 'high');
    ecran(true);
    expect(container.querySelector('video')).toBeNull();
    etat.mockReturnValue('complete');
    act(() => {
      window.dispatchEvent(new Event('load'));
    });
    expect(container.querySelector('video')).not.toBeNull();
    expect(play).toHaveBeenCalledTimes(1);
  });

  it('donne un texte equivalent et une legende visible', () => {
    render(<VideoBoucle nom="statuts-apercu" />);
    expect(screen.getByRole('figure')).toHaveTextContent(VIDEOS['statuts-apercu'].legende);
    expect(screen.getByRole('img', { name: VIDEOS['statuts-apercu'].alt })).toBeInTheDocument();
  });
});
