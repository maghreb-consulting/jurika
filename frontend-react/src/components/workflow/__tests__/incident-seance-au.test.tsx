import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

/**
 * Lot L4d (E11) : les PV d'incident de séance SARL AU sont retirés (sans objet : un associé
 * unique ne tient ni assemblée à quorum ni convocation). L'écran le dit au lieu d'une liste vide.
 */
vi.mock('../../../services/workflowDocumentService', () => ({
  listTemplatesForWorkflow: vi.fn(async () => [
    { code: 'PV_DEFAUT_QUORUM_SARL', documentKind: 'PV', file: null, origin: 'corpus', deprecated: false },
    { code: 'PV_IRREGULARITE_CONVOCATION_SARL', documentKind: 'PV', file: null, origin: 'corpus', deprecated: false },
  ]),
  generateDocument: vi.fn(),
  emplacementClausesLibres: vi.fn(async () => ({ possible: false })),
  GenerationRefuseeError: class extends Error {},
}));

import { IncidentSeancePanel } from '../IncidentSeancePanel';

/**
 * Lot L6b (E15, parcours " incident d'assemblee ") : le PV de defaut de quorum rappelle
 * " l'ordre du jour suivant : " ; le panneau partait d'un ordre du jour VIDE, si bien que le
 * PV imprimait les deux-points suivis de rien. L'ordre du jour de l'assemblee lui est
 * transmis (modifiable : l'incident peut porter sur une partie seulement).
 */
describe('Incident de séance : ordre du jour de l’assemblée', () => {
  it('le panneau reprend l’ordre du jour de l’assemblée', () => {
    render(
      <IncidentSeancePanel formeJuridique="SARL" societe={{ denomination: 'ATLAS NORD SARL' }}
        initialOrdreDuJour={['Nomination du ou des gérants', 'Pouvoirs pour les formalités']} />,
    );
    fireEvent.click(screen.getByTestId('incident-seance-toggle'));
    expect(screen.getByDisplayValue('Nomination du ou des gérants')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Pouvoirs pour les formalités')).toBeInTheDocument();
  });
});

describe('Incident de séance en SARL à associé unique', () => {
  it('explique qu’aucun PV d’incident ne s’applique', async () => {
    render(<IncidentSeancePanel formeJuridique="SARL_AU" societe={{ denomination: 'PARACOSME' }} />);
    fireEvent.click(screen.getByTestId('incident-seance-toggle'));
    expect(await screen.findByTestId('incident-seance-aucun')).toHaveTextContent(
      'il n’y a ni quorum ni convocation',
    );
  });
});
