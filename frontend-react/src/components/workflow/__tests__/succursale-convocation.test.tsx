import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';

vi.mock('../../../services/workflowDocumentService', async (orig) => ({
  ...(await orig<typeof import('../../../services/workflowDocumentService')>()),
  listTemplatesForWorkflow: vi.fn().mockResolvedValue([]),
}));

import { SuccursaleOperationForm } from '../SuccursaleOperationForm';
import { SeanceOperationForm } from '../SeanceOperationForm';

/**
 * Lot L6b (E15, parcours de bout en bout " succursale ") : la date et l'heure de convocation
 * saisies a l'etape 1 (ouverture d'une succursale marocaine, fermeture) arrivent au
 * proces-verbal de l'etape 3 sans etre redemandees. Constate a l'ecran : les deux champs
 * etaient vides a l'etape 3.
 */
describe('Succursale — convocation saisie en amont', () => {
  it('la convocation de l’étape 1 est reprise par le procès-verbal, sans nouveau champ', async () => {
    const onSnapshot = vi.fn();
    await act(async () => {
      render(
        <SuccursaleOperationForm mode="creation-ma" formeJuridique="SARL"
          societe={{ denomination: 'ATLAS NORD SARL' }} defaultDate="2026-11-05"
          defaultConvocation={{ date: '2026-10-20', heure: '10:00' }} onSnapshot={onSnapshot} />,
      );
    });
    expect(screen.queryByLabelText('Date de convocation')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Heure de convocation')).not.toBeInTheDocument();
    expect(onSnapshot).toHaveBeenLastCalledWith(expect.objectContaining({ convDate: '2026-10-20', convHeure: '10:00' }));
  });

  it('dissolution et liquidation : la convocation de l’étape 1 est reprise par le procès-verbal', async () => {
    const onSnapshot = vi.fn();
    await act(async () => {
      render(
        <SeanceOperationForm mode="dissolution" formeJuridique="SARL"
          societe={{ denomination: 'ATLAS NORD SARL' }} defaultDate="2026-11-05"
          defaultConvocation={{ date: '2026-10-20', heure: '10:00' }} onSnapshot={onSnapshot} />,
      );
    });
    expect(screen.queryByLabelText('Date de convocation')).not.toBeInTheDocument();
    expect(onSnapshot).toHaveBeenLastCalledWith(expect.objectContaining({ convDate: '2026-10-20', convHeure: '10:00' }));
  });

  it('sans convocation en amont, le procès-verbal la demande', async () => {
    await act(async () => {
      render(
        <SuccursaleOperationForm mode="creation-ma" formeJuridique="SARL"
          societe={{ denomination: 'ATLAS NORD SARL' }} defaultDate="2026-11-05" />,
      );
    });
    expect(screen.getByLabelText('Date de convocation')).toBeInTheDocument();
  });
});
