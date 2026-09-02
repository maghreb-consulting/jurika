import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { useLenisScroll } from './useLenisScroll';

// ---------------------------------------------------------------------------
// Mock de l'instance Lenis : on compte les constructions et les destroy() pour
// prouver (1) que Lenis n'est PAS instancié sur les routes denylistées et
// (2) qu'il est bien DÉTRUIT en quittant une route scrollable (aucun transform
// résiduel — c'est destroy() qui retire les styles posés sur <html>).
// ---------------------------------------------------------------------------
const lenisStats = { constructs: 0, destroys: 0 };

vi.mock('lenis', () => ({
  default: class LenisMock {
    constructor() {
      lenisStats.constructs += 1;
    }
    raf() {
      /* no-op */
    }
    destroy() {
      lenisStats.destroys += 1;
    }
  },
}));

function Harness() {
  useLenisScroll();
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate('/chat')}>
      go-chat
    </button>
  );
}

beforeEach(() => {
  lenisStats.constructs = 0;
  lenisStats.destroys = 0;
  // jsdom n'implémente pas matchMedia : on simule "pas de reduced-motion".
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }) as unknown as typeof window.matchMedia;
  // rAF stub : ne jamais ré-invoquer le callback (la boucle raf de Lenis se
  // re-planifie elle-même -> récursion infinie si on appelle cb). On se
  // contente de rendre un id ; on teste construct/destroy, pas le rendu.
  vi.stubGlobal('requestAnimationFrame', vi.fn().mockReturnValue(1));
  vi.stubGlobal('cancelAnimationFrame', vi.fn());
});

describe('useLenisScroll', () => {
  it("n'instancie PAS Lenis sur la landing '/'", () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Harness />
      </MemoryRouter>,
    );
    expect(lenisStats.constructs).toBe(0);
  });

  it("n'instancie PAS Lenis sur /chat (layout 100vh)", () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <Harness />
      </MemoryRouter>,
    );
    expect(lenisStats.constructs).toBe(0);
  });

  it("n'instancie PAS Lenis sur /chatbot (layout 100vh)", () => {
    render(
      <MemoryRouter initialEntries={['/chatbot']}>
        <Harness />
      </MemoryRouter>,
    );
    expect(lenisStats.constructs).toBe(0);
  });

  it('instancie Lenis sur une route scrollable (ex. /dashboard)', () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Harness />
      </MemoryRouter>,
    );
    expect(lenisStats.constructs).toBe(1);
    expect(lenisStats.destroys).toBe(0);
  });

  it('DÉTRUIT Lenis en naviguant vers /chat (pas de transform résiduel)', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Harness />
      </MemoryRouter>,
    );
    expect(lenisStats.constructs).toBe(1);
    expect(lenisStats.destroys).toBe(0);

    await user.click(document.querySelector('button')!);

    // La nav vers /chat (denylisté) re-évalue l'effet : cleanup -> destroy(),
    // puis early-return -> aucune nouvelle instance.
    expect(lenisStats.destroys).toBe(1);
    expect(lenisStats.constructs).toBe(1);
  });
});
