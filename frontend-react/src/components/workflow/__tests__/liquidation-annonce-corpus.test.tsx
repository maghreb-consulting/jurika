import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/**
 * Lot L4b (E7, D5) : l'avis de clôture du corpus reçoit ses données saisies (acte de
 * clôture, date d'effet de la dissolution confirmée, compte définitif, quitus, siège).
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

import { LiquidationAnnoncePanel, type AvisCloture } from '../LiquidationAnnoncePanel';
import { useState } from 'react';

function Hote() {
  const [avis, setAvis] = useState<AvisCloture>({});
  return (
    <LiquidationAnnoncePanel
      ticketId="t1" dossierId="d1" formeJuridique="SARL" denomination="PARACOSME" dateCloture="2026-06-20"
      liquidateur={{ civilite: 'M.', prenom: 'Ahmed', nom: 'Alaoui' }} totalActif="300000" totalPassif="150000"
      avis={avis} onAvisChange={setAvis} dateDissolution="2026-05-15"
      depotLegalNumero="" depotLegalDate="" onDepotLegalNumeroChange={vi.fn()} onDepotLegalDateChange={vi.fn()}
    />
  );
}

describe('Avis de clôture de liquidation (modèle du corpus)', () => {
  it('rien n’est choisi d’office ; les données saisies partent avec l’avis', async () => {
    render(<Hote />);
    expect(screen.getByRole('combobox', { name: /Acte constatant la clôture/ })).toHaveValue('');
    expect(screen.getByRole('combobox', { name: /Quitus au liquidateur/ })).toHaveValue('');
    fireEvent.change(screen.getByRole('combobox', { name: /Acte constatant la clôture/ }), { target: { value: 'Décision des associés' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }));
    fireEvent.change(screen.getByLabelText(/Compte définitif arrêté au/), { target: { value: '2026-06-10' } });
    fireEvent.change(screen.getByRole('combobox', { name: /Quitus au liquidateur/ }), { target: { value: 'sous_reserve' } });
    fireEvent.change(screen.getByLabelText(/Siège de la liquidation/), { target: { value: '20 boulevard Zerktouni' } });
    fireEvent.click(screen.getByTestId('generate-btn-ANNONCE_LEGALE_LIQUIDATION_SARL'));
    await waitFor(() => expect(generateDocument).toHaveBeenCalled());
    const payload = (generateDocument.mock.calls[0] as unknown[])[2] as Record<string, Record<string, unknown>>;
    expect(payload.cloture).toMatchObject({
      acteNature: 'Décision des associés', dissolutionDateEffet: '2026-05-15', compteDefinitifDate: '2026-06-10',
      quitus: 'sous_reserve', siegeLiquidation: '20 boulevard Zerktouni',
    });
  });

  it('L6b (E19) : ni sens ni montant de résultat tiré de actif - passif, à l’écran comme dans la charge utile', async () => {
    generateDocument.mockClear();
    render(<Hote />);
    // 300 000 - 150 000 s'affichait "Resultat publie : boni de liquidation de 150 000 DH".
    expect(screen.getByTestId('annonce-resultat')).not.toHaveTextContent(/150\s000/);
    expect(screen.getByTestId('annonce-resultat')).toHaveTextContent(/ne publie pas le résultat/);
    fireEvent.change(screen.getByRole('combobox', { name: /Acte constatant la clôture/ }), { target: { value: 'Décision des associés' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }));
    fireEvent.change(screen.getByLabelText(/Compte définitif arrêté au/), { target: { value: '2026-06-10' } });
    fireEvent.change(screen.getByRole('combobox', { name: /Quitus au liquidateur/ }), { target: { value: 'accorde' } });
    fireEvent.change(screen.getByLabelText(/Siège de la liquidation/), { target: { value: '20 boulevard Zerktouni' } });
    fireEvent.click(screen.getByTestId('generate-btn-ANNONCE_LEGALE_LIQUIDATION_SARL'));
    await waitFor(() => expect(generateDocument).toHaveBeenCalled());
    const payload = (generateDocument.mock.calls[0] as unknown[])[2] as Record<string, Record<string, unknown>>;
    expect(payload.cloture).not.toHaveProperty('resultatType');
    expect(payload.cloture).not.toHaveProperty('resultatMontant');
  });
});
