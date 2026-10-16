import fs from 'node:fs';
import path from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { identifiantUnique } from '../identifiant';

/**
 * Lot L6b, defaut bloquant reproduit sur le Z440 (2026-10-16) : servi en HTTP
 * (http://192.168.1.251, contexte non securise), le navigateur n'expose pas
 * crypto.randomUUID ; l'etape 5 de la creation (dirigeants) plantait juste apres la saisie
 * de l'activite ("crypto.randomUUID is not a function", ErrorBoundary).
 */
describe('identifiantUnique', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('rend un UUID v4 meme sans crypto.randomUUID (contexte non securise)', () => {
    const vrai = globalThis.crypto;
    vi.stubGlobal('crypto', { getRandomValues: (a: Uint8Array) => vrai.getRandomValues(a) });
    const id = identifiantUnique();
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(identifiantUnique()).not.toBe(id);
  });

  it('aucun ecran n appelle crypto.randomUUID directement', () => {
    const racine = path.resolve(__dirname, '../..');
    const fautifs: string[] = [];
    const parcourir = (d: string) => {
      for (const n of fs.readdirSync(d, { withFileTypes: true })) {
        const p = path.join(d, n.name);
        if (n.isDirectory()) { if (n.name !== '__tests__' && n.name !== 'node_modules') parcourir(p); continue; }
        if (!/\.(ts|tsx)$/.test(n.name) || p.endsWith(path.join('lib', 'identifiant.ts'))) continue;
        if (/crypto\.randomUUID\s*\(/.test(fs.readFileSync(p, 'utf8'))) fautifs.push(path.relative(racine, p));
      }
    };
    parcourir(racine);
    expect(fautifs, 'utiliser identifiantUnique() (lib/identifiant.ts)').toEqual([]);
  });
});
