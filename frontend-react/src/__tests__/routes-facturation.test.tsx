import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import App from '../App';

// Aucun appel reseau reel : toute requete axios echoue aussitot.
vi.mock('axios', async (orig) => {
  const vrai = await orig<typeof import('axios')>();
  const inst = vrai.default.create({ adapter: () => Promise.reject(new Error('reseau coupe (test)')) });
  return { ...vrai, default: { ...vrai.default, create: () => inst, post: inst.post } };
});

function jeton(role: string): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b64({ alg: 'none' })}.${b64({ role, email: 'a@jurika.test', uid: 'u1', wsid: 'w1', exp: 4102444800 })}.x`;
}

/**
 * Lot L6b (E13), exploration de bout en bout : le super-admin atteignait /app/billing (et ses
 * sous-pages, /app/archive) ; ces ecrans lisent l'abonnement DU CABINET (GET /billing/abonnement,
 * /paiements, /factures), que le serveur refuse au super-admin (403) : l'ecran affichait
 * "L'abonnement n'a pas pu etre charge". Aucun menu ne l'y mene ; la route est fermee au role.
 */
describe('routes de la facturation du cabinet', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it.each(['/app/billing', '/app/billing/change-plan', '/app/billing/payment', '/app/archive'])(
    'super-admin sur %s : renvoye vers /forbidden, aucun ecran du cabinet',
    async (chemin) => {
      window.localStorage.setItem('jurika_access_token', jeton('SUPER_ADMIN'));
      window.history.pushState({}, '', chemin);
      render(<App />);
      await waitFor(() => expect(window.location.pathname).toBe('/forbidden'));
    },
  );
});
