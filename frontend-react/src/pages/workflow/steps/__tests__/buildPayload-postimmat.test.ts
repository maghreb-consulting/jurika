import { describe, it, expect } from 'vitest';
import { buildPayloadCreationSarl } from '../../CreationSarlWorkflowPage';

/**
 * Phase 3 (B.3) — Chemin post-immatriculation : les valeurs saisies en Step9
 * (RC + date de dépôt légal) doivent alimenter `societe.rcNumero` /
 * `societe.dateDepotLegal`, consommées par ANNONCE_LEGALE_DIRECTEUR pour
 * remplacer les placeholders « à compléter après immatriculation ».
 */
describe('buildPayloadCreationSarl — post-immatriculation (B.3)', () => {
  it('propage rcNumero + dateDepotLegal depuis step9.postImmat vers societe', () => {
    const stepData = {
      step1: { denomination: { denomination: 'ACME', formeJuridique: 'SARL' } },
      step3: { capital: { capitalSocialMad: 100000, nombreParts: 1000, valeurNominale: 100 } },
      step9: { postImmat: { rcNumero: 'RC-12345', dateDepotLegal: '2026-03-01' } },
    } as Record<string, Record<string, unknown>>;

    const payload = buildPayloadCreationSarl(stepData);
    const societe = payload.societe as Record<string, unknown>;

    expect(societe.rcNumero).toBe('RC-12345');
    expect(societe.dateDepotLegal).toBe('2026-03-01');
  });

  it('rcNumero absent => non renseigné (le mapper rend le placeholder)', () => {
    const stepData = {
      step1: { denomination: { denomination: 'ACME' } },
    } as Record<string, Record<string, unknown>>;

    const payload = buildPayloadCreationSarl(stepData);
    const societe = payload.societe as Record<string, unknown>;

    expect(societe.rcNumero ?? null).toBeNull();
  });
});
