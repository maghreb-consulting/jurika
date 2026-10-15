import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { Ecrans, prechargerEcran } from '../pages';
import { menuDuRole } from '../../components/layout/navigation';
import type { Role } from '../../types/auth';

/** Lot L6b (E3) : ecrans charges a la demande et prechargement depuis le menu. */
const LAZY = Symbol.for('react.lazy');

describe('Ecrans charges a la demande', () => {
  it('chaque ecran est un composant React.lazy', () => {
    for (const [nom, e] of Object.entries(Ecrans)) {
      expect((e.Composant as unknown as { $$typeof: symbol }).$$typeof, nom).toBe(LAZY);
    }
  });

  it('App.tsx n importe plus aucun ecran directement (tout passe par routes/pages)', () => {
    const app = readFileSync(resolve(__dirname, '../../App.tsx'), 'utf-8');
    const imports = [...app.matchAll(/^import .* from '\.\/pages\/([^']+)';$/gm)].map((m) => m[1]);
    expect(imports).toEqual(['workflow/useRenvoiBrouillons']);
  });

  it('chaque entree du menu, pour chaque role, a un chargeur de prechargement', () => {
    const chemins = new Set(
      (['SUPER_ADMIN', 'SUPERVISEUR', 'EMPLOYE', 'CLIENT'] as Role[]).flatMap((r) =>
        menuDuRole(r).flatMap((g) => g.entrees.map((e) => e.to)),
      ),
    );
    // Premier appel : le chargement part ; second : deja demande, rien n'est relance.
    for (const c of chemins) {
      expect(prechargerEcran(c), c).toBe(true);
      expect(prechargerEcran(c), c).toBe(false);
    }
    expect(prechargerEcran('/adresse-inconnue')).toBe(false);
  }, 60_000);
});
