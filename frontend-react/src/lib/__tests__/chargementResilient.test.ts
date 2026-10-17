import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { chargerAvecReprise } from '../chargementResilient';

/**
 * Lot L6b (exploration de bout en bout sur le Z440, 2026-10-16) : un ecran charge a la demande dont
 * le fichier ne se charge pas (reseau qui change : ERR_NETWORK_CHANGED ; ou fichiers renommes par
 * un redeploiement) laissait une page blanche derriere l'ErrorBoundary, sans nouvel essai.
 */
const erreurModule = () => new TypeError('Failed to fetch dynamically imported module: http://x/assets/Page-ab12.js');

describe('chargerAvecReprise', () => {
  let recharger: ReturnType<typeof vi.fn>;
  beforeEach(() => { sessionStorage.clear(); recharger = vi.fn(); vi.useFakeTimers(); });
  afterEach(() => vi.useRealTimers());

  it('reessaie et reussit au second essai', async () => {
    const charger = vi.fn().mockRejectedValueOnce(erreurModule()).mockResolvedValueOnce({ Page: 'ok' });
    const p = chargerAvecReprise(charger, { recharger });
    await vi.runAllTimersAsync();
    await expect(p).resolves.toEqual({ Page: 'ok' });
    expect(charger).toHaveBeenCalledTimes(2);
    expect(recharger).not.toHaveBeenCalled();
  });

  it('apres les essais, recharge la page une seule fois (fichiers renommes par un redeploiement)', async () => {
    const charger = vi.fn().mockRejectedValue(erreurModule());
    void chargerAvecReprise(charger, { recharger });
    await vi.runAllTimersAsync();
    expect(recharger).toHaveBeenCalledTimes(1);
    // Second echec juste apres le rechargement : pas de boucle, l'erreur remonte a l'ErrorBoundary.
    const p2 = chargerAvecReprise(charger, { recharger });
    const attendu = expect(p2).rejects.toThrow(/dynamically imported module/);
    await vi.runAllTimersAsync();
    await attendu;
    expect(recharger).toHaveBeenCalledTimes(1);
  });

  it('une erreur qui n est pas un chargement de module remonte sans reessai', async () => {
    const charger = vi.fn().mockRejectedValue(new Error('erreur du code de l ecran'));
    const p = chargerAvecReprise(charger, { recharger });
    const attendu = expect(p).rejects.toThrow('erreur du code');
    await vi.runAllTimersAsync();
    await attendu;
    expect(charger).toHaveBeenCalledTimes(1);
  });
});
