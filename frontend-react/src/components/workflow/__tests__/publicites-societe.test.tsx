import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';

const { upload } = vi.hoisted(() => ({ upload: vi.fn(async () => ({ id: 'doc-jal' })) }));
vi.mock('../../../services/dataroom.service', () => ({ dataroomService: { uploadJuridique: upload } }));

import { PublicitesSociete } from '../PublicitesSociete';
import { manquePublicite, publiciteVide, type Publicite } from '../../../lib/publicites';

/** Lot L4d (E8) : publicités de la société saisies à l'import, pièce versée en Data Room. */
function Hote({ initial = [] as Publicite[] }) {
  const [v, setV] = useState<Publicite[]>(initial);
  return <PublicitesSociete value={v} onChange={setV} dossierId="d1" societe="PARACOSME" />;
}

describe('Publicités de la société (import)', () => {
  it('nomme ce qui manque, comme le serveur', () => {
    expect(manquePublicite(publiciteVide())).toMatch(/nature.*support.*date de parution.*numéro de parution/);
    expect(manquePublicite({ nature: 'MODIFICATION', support: 'JAL', dateParution: '2020-03-05', numeroParution: '5712' }))
      .toMatch(/objet de la modification, journal d’annonces légales/);
    expect(manquePublicite({ nature: 'CONSTITUTION', support: 'BO', dateParution: '2020-03-05', numeroParution: '6871' })).toBeNull();
  });

  it('saisit une annonce de constitution et verse sa pièce dans la Data Room', async () => {
    render(<Hote />);
    fireEvent.click(screen.getByRole('button', { name: /Ajouter une publicité/ }));
    fireEvent.change(screen.getByLabelText('Nature de la publicité 1'), { target: { value: 'CONSTITUTION' } });
    fireEvent.change(screen.getByLabelText('Support de la publicité 1'), { target: { value: 'JAL' } });
    fireEvent.change(screen.getByLabelText('Journal 1'), { target: { value: 'L’Économiste' } });
    fireEvent.change(screen.getByLabelText('Date de parution 1'), { target: { value: '2020-03-05' } });
    fireEvent.change(screen.getByLabelText('Numéro de parution 1'), { target: { value: '5712' } });
    const fichier = new File(['x'], 'annonce.pdf', { type: 'application/pdf' });
    fireEvent.change(screen.getByLabelText('Pièce de la publicité 1'), { target: { files: [fichier] } });
    await waitFor(() => expect(upload).toHaveBeenCalled());
    expect(upload).toHaveBeenCalledWith('d1', expect.objectContaining({
      documentType: 'JOURNAL_ANNONCE',
      title: 'Annonce légale — L’Économiste — Constitution — PARACOSME — n° 5712 du 2020-03-05',
    }));
    expect(await screen.findByText(/Pièce versée en Data Room/)).toBeInTheDocument();
  });

  it('refuse de verser la pièce d’une publicité incomplète', async () => {
    upload.mockClear();
    render(<Hote initial={[publiciteVide()]} />);
    fireEvent.change(screen.getByLabelText('Pièce de la publicité 1'), { target: { files: [new File(['x'], 'bo.pdf')] } });
    expect(await screen.findByRole('alert')).toHaveTextContent('Avant de verser la pièce, indiquez nature');
    expect(upload).not.toHaveBeenCalled();
  });
});
