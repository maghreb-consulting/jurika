import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';

/**
 * Lot L3 (regle des variables) a l'ecran :
 *  - le refus nomme les donnees internes manquantes (le corps 422 arrive en Blob) ;
 *  - les donnees externes manquantes sont annoncees (en-tete X-Donnees-A-Obtenir) ;
 *  - quand toutes les donnees attendues sont arrivees, l'acte est regenere d'office, une fois.
 */

const post = vi.fn();
vi.mock('../../../lib/api', () => ({ api: { post: (...a: unknown[]) => post(...a), get: vi.fn() } }));

import { generateDocument, GenerationRefuseeError } from '../../../services/workflowDocumentService';
import { RetourGeneration } from '../RetourGeneration';
import { DonneesAttendues } from '../DonneesAttendues';

describe('Refus nomme et donnees a obtenir', () => {
  it('relit le corps 422 (Blob) et nomme les donnees manquantes', async () => {
    const corps = new Blob([JSON.stringify({
      code: 'GENERATION_REFUSEE',
      message: 'Le document ne peut pas être généré : des données manquent.',
      donneesManquantes: [{ variable: 'DATE_SIGNATURE', libelle: 'Date de signature', endroit: 'Fait à Rabat, le .' }],
    })], { type: 'application/json' });
    post.mockRejectedValueOnce(new AxiosError('Request failed with status code 422', 'ERR_BAD_REQUEST', undefined, undefined, {
      status: 422, statusText: 'Unprocessable', headers: {}, config: { headers: new AxiosHeaders() }, data: corps,
    }));
    const err = await generateDocument('DISSOLUTION', 'PV_X', {}).catch((e) => e);
    expect(err).toBeInstanceOf(GenerationRefuseeError);
    expect(err.message).not.toContain('status code');
    render(<RetourGeneration erreur={err.message} refus={err.donneesManquantes} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Date de signature');
    expect(screen.getByRole('alert')).toHaveTextContent('Fait à Rabat, le .');
    expect(screen.getByRole('button', { name: 'Pourquoi le document n’est-il pas généré ?' })).toBeInTheDocument();
  });

  it('annonce les donnees externes a obtenir', async () => {
    const entete = encodeURIComponent(JSON.stringify([{ variable: 'RC_NUMERO', libelle: 'Numéro du registre du commerce' }]));
    post.mockResolvedValueOnce({ data: new Blob(['x']), headers: { 'x-donnees-a-obtenir': entete } });
    const r = await generateDocument('CREATION_SARL', 'ANNONCE_LEGALE_CONSTITUTION', { ticketId: 't-1' });
    expect(r.donneesAObtenir).toEqual([{ variable: 'RC_NUMERO', libelle: 'Numéro du registre du commerce' }]);
    render(<RetourGeneration aObtenir={r.donneesAObtenir} />);
    expect(screen.getByRole('status')).toHaveTextContent('Numéro du registre du commerce');
  });
});

describe('Donnees attendues : regeneration a l arrivee', () => {
  const a = (recue: boolean, variable = 'RC_NUMERO') => ({
    templateCode: 'ANNONCE', workflowCode: 'CREATION_SARL', variable, libelle: variable, reclameeLe: '2026-10-10T10:00:00Z', recue,
  });

  it('ne regenere pas tant qu une donnee manque', () => {
    const onRegenerer = vi.fn();
    render(<DonneesAttendues attendues={[a(true), a(false, 'ICE')]} onRegenerer={onRegenerer} />);
    expect(screen.getByText('En attente')).toBeInTheDocument();
    expect(onRegenerer).not.toHaveBeenCalled();
  });

  it('regenere une seule fois quand tout est arrive', async () => {
    const onRegenerer = vi.fn();
    const { rerender } = render(<DonneesAttendues attendues={[a(true)]} onRegenerer={onRegenerer} />);
    await waitFor(() => expect(onRegenerer).toHaveBeenCalledTimes(1));
    rerender(<DonneesAttendues attendues={[a(true)]} onRegenerer={onRegenerer} />);
    expect(onRegenerer).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('status')).toHaveTextContent('régénéré');
  });
});
