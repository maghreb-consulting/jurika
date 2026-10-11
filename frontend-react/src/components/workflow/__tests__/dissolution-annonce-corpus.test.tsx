import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/**
 * Lot L4b (E7, D5) : l'avis de dissolution du corpus reçoit les données saisies à l'étape 1
 * (date d'effet confirmée, motif du corpus, siège de la liquidation, entrée en fonctions et
 * pièce du liquidateur). Avant : la date d'effet était la date de l'assemblée, supposée.
 */
const generateDocument = vi.fn(async () => ({ blob: new Blob(['x']), filename: 'avis.docx', donneesAObtenir: [] }));
vi.mock('../../../services/workflowDocumentService', () => ({
  generateDocument: (...a: unknown[]) => generateDocument(...(a as [])),
  listTemplatesForWorkflow: vi.fn(async () => []),
  emplacementClausesLibres: vi.fn(async () => ({ possible: false })),
  GenerationRefuseeError: class extends Error {},
}));
vi.mock('../../../services/workflow.service', () => ({
  workflowService: new Proxy({}, { get: () => vi.fn(async () => []) }),
}));
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: new Proxy({}, { get: () => vi.fn(async () => ({})) }),
}));

import { DissolutionAnnoncePanel } from '../DissolutionAnnoncePanel';

describe('Avis de dissolution (modèle du corpus)', () => {
  it('envoie la date d’effet confirmée, le motif du corpus et le siège choisi', async () => {
    render(
      <DissolutionAnnoncePanel
        ticketId="t1"
        dossierId="d1"
        formeJuridique="SARL"
        denomination="PARACOSME"
        dateAGE="2026-05-15"
        motif="Cessation d'activité décidée par les associés."
        nature="volonte_associes"
        dissolutionDetail={{ dateEffet: '2026-06-01', siegeIdentique: 'non', mandatDateEffet: '2026-06-01' }}
        liquidateur={{ civilite: 'M.', prenom: 'Ahmed', nom: 'Alaoui', adresse: '5 rue d’Alger', pieceType: 'CIN', cin: 'BE123456' }}
        siegeLiquidation="20 boulevard Zerktouni, Casablanca"
        depotLegalNumero=""
        depotLegalDate=""
        onDepotLegalNumeroChange={vi.fn()}
        onDepotLegalDateChange={vi.fn()}
        onReady={vi.fn()}
      />,
    );
    fireEvent.click(await screen.findByTestId('generate-btn-ANNONCE_LEGALE_DISSOLUTION_SARL'));
    await waitFor(() => expect(generateDocument).toHaveBeenCalled());
    const payload = (generateDocument.mock.calls[0] as unknown[])[2] as Record<string, Record<string, unknown>>;
    expect(payload.dissolution).toMatchObject({
      nature: 'volonte_associes', date: '2026-05-15', dateEffet: '2026-06-01', siegeLiquidationIdentique: 'non',
    });
    expect(payload.liquidateur).toMatchObject({ pieceType: 'CIN', pieceNumero: 'BE123456', mandatDateEffet: '2026-06-01' });
    expect(payload.siegeLiquidation).toBe('20 boulevard Zerktouni, Casablanca');
  });
});
