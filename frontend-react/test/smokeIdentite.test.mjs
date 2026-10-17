import { describe, it, expect } from 'vitest';
import { identiteDeFumee } from '../../scripts/smoke-identite.mjs';

/**
 * Recette R1 (point 2) : le nom "Smoke" lu par la personne venait du test de fumee du Z440, qui
 * inscrivait un cabinet "Smoke Test" et envoyait son courriel de bienvenue ("Bonjour Smoke", meme
 * sujet que le vrai courriel, code d'un cabinet supprime a la fin du test) a la VRAIE boite
 * SMOKE_CONTACT_EMAIL (sous-adresse +smoke-...). Aucune donnee de test ne doit atteindre une vraie
 * boite : toutes les adresses du test sont dans le domaine reserve @jurika.test.
 */
describe('identite du test de fumee', () => {
  it('n’adresse jamais un courriel a une vraie boite, meme SMOKE_CONTACT_EMAIL defini', () => {
    const id = identiteDeFumee(1760000000, { SMOKE_CONTACT_EMAIL: 'personne.reelle@gmail.com' });
    expect(id.email).toMatch(/@jurika\.test$/);
    expect(id.contactEmail).toMatch(/@jurika\.test$/);
    expect(JSON.stringify(id)).not.toContain('gmail.com');
  });

  it('garde le nom qui sert de garde-fou a la suppression du cabinet de test', () => {
    expect(identiteDeFumee(1760000000).workspaceName).toBe('SmokeTest Cabinet 1760000000');
  });
});
