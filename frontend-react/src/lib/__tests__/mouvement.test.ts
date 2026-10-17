import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { avecTransitionDeVue, mouvementReduit, useMouvementReduit } from '../mouvement';

/** Lot L6b (E3) : "reduire les animations" respecte (CSS et JS), transitions de vue. */
function reglerMouvementReduit(reduit: boolean) {
  window.matchMedia = ((q: string) => ({
    matches: q.includes('prefers-reduced-motion') ? reduit : false,
    media: q,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })) as unknown as typeof window.matchMedia;
}

afterEach(() => {
  delete (window as { matchMedia?: unknown }).matchMedia;
  delete (document as { startViewTransition?: unknown }).startViewTransition;
});

describe('Mouvement reduit', () => {
  it('sans matchMedia : animations permises (rien n est suppose)', () => {
    expect(mouvementReduit()).toBe(false);
  });

  it('lit le reglage de l ordinateur (fonction et crochet)', () => {
    reglerMouvementReduit(true);
    expect(mouvementReduit()).toBe(true);
    expect(renderHook(() => useMouvementReduit()).result.current).toBe(true);
  });

  it('transition de vue quand le navigateur la connait ; directe si les animations sont reduites', () => {
    const svt = vi.fn((f: () => void) => f());
    (document as { startViewTransition?: unknown }).startViewTransition = svt;
    const maj = vi.fn();
    reglerMouvementReduit(false);
    avecTransitionDeVue(maj);
    expect(svt).toHaveBeenCalledTimes(1);
    expect(maj).toHaveBeenCalledTimes(1);
    reglerMouvementReduit(true);
    avecTransitionDeVue(maj);
    expect(svt).toHaveBeenCalledTimes(1);
    expect(maj).toHaveBeenCalledTimes(2);
  });

  it('sans View Transitions : mise a jour directe', () => {
    const maj = vi.fn();
    avecTransitionDeVue(maj);
    expect(maj).toHaveBeenCalledTimes(1);
  });

  it('la feuille de style coupe animations et transitions sous prefers-reduced-motion, durees au plus 200 ms', () => {
    const css = readFileSync(resolve(__dirname, '../../index.css'), 'utf-8');
    const bloc = /@media \(prefers-reduced-motion: reduce\) \{([\s\S]*?)\n\}/.exec(css)?.[1] ?? '';
    expect(bloc).toMatch(/animation-duration:\s*0\.01ms !important/);
    expect(bloc).toMatch(/transition-duration:\s*0\.01ms !important/);
    expect(bloc).toMatch(/::view-transition/);
    for (const m of css.matchAll(/--duree-[a-z]+:\s*(\d+)ms/g)) {
      expect(Number(m[1])).toBeLessThanOrEqual(200);
    }
  });
});
