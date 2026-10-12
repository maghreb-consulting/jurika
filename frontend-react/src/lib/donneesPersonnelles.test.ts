import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  detecterDonneesPersonnelles,
  LIBELLES_DONNEES_PERSONNELLES,
  messageReformulation,
} from './donneesPersonnelles';

/**
 * Lot L5 E8 (RG-COM-04) : meme jeu de cas que le serveur
 * (ai-service FiltreDonneesPersonnellesTest), a la racine du depot.
 */
// vitest s'execute depuis frontend-react/ (environnement jsdom : import.meta.url n'est pas un fichier).
const chemin = resolve(process.cwd(), '..', 'tests', 'chatbot', 'cas-donnees-personnelles.json');
const cas = JSON.parse(readFileSync(chemin, 'utf-8')) as {
  bloques: Array<{ question: string; nature: string }>;
  autorises: string[];
};

describe('filtre des donnees personnelles du chatbot', () => {
  it.each(cas.bloques)('bloque : $question ($nature)', ({ question, nature }) => {
    expect(detecterDonneesPersonnelles(question)).toContain(nature);
  });

  it.each(cas.autorises)('laisse passer : %s', (question) => {
    expect(detecterDonneesPersonnelles(question)).toEqual([]);
  });

  it('le jeu de cas couvre chaque nature', () => {
    const natures = new Set(cas.bloques.map((c) => c.nature));
    for (const n of Object.keys(LIBELLES_DONNEES_PERSONNELLES)) {
      expect(natures.has(n)).toBe(true);
    }
    expect(cas.autorises.length).toBeGreaterThanOrEqual(10);
  });

  it('le message nomme la nature sans reprendre la valeur', () => {
    const question = 'Appelez le 0612345678';
    const message = messageReformulation(detecterDonneesPersonnelles(question));
    expect(message).toContain('num\u00e9ro de t\u00e9l\u00e9phone');
    expect(message).not.toContain('0612345678');
  });
});
