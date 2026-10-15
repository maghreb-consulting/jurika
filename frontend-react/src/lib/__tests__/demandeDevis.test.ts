import { afterEach, describe, expect, it, vi } from 'vitest';
import { demanderDevis, messageEchecDevis } from '../demandeDevis';

/** Lot L7 : plus d'adresse codee en dur ; l'adresse de contact vient de VITE_CONTACT_EMAIL. */
describe('demande de devis', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('sans adresse configuree : aucun mailto, un message sans adresse', () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', '');
    const ouvrir = vi.fn();
    const msg = demanderDevis(ouvrir);
    expect(ouvrir).not.toHaveBeenCalled();
    expect(msg).toMatch(/Sur Devis/);
    expect(msg).not.toMatch(/@/);
    expect(messageEchecDevis()).not.toMatch(/@/);
  });

  it('avec adresse configuree : la messagerie s’ouvre vers elle', () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', 'contact@exemple.test');
    const ouvrir = vi.fn();
    expect(demanderDevis(ouvrir)).toBeNull();
    expect(ouvrir).toHaveBeenCalledWith(expect.stringMatching(/^mailto:contact@exemple\.test\?subject=/));
    expect(messageEchecDevis()).toContain('contact@exemple.test');
  });
});
