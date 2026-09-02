import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TableView } from '../TableView';
import { responsableLabel, type Ticket } from '../../../types/ticket';

/**
 * Vue Tableau des tickets (SUPERVISEUR) — colonne « Responsable ».
 *
 * Couvre : (1) un ticket assigne affiche le « Prenom Nom » resolu backend ;
 * (2) un ticket non assigne affiche « Non assigne » ; (3) aucun UUID brut n'est
 * rendu (RG : jamais d'identifiant technique dans l'UI).
 */

function ticket(overrides: Partial<Ticket>): Ticket {
  return {
    id: 't-1',
    workspaceId: 'ws-1',
    reference: 'T-2026-00001',
    titre: 'Creation SARL',
    type: 'CREATION',
    statut: 'EN_COURS',
    priorite: 'NORMALE',
    dossierId: 'dos-1',
    assigneId: null,
    creeParId: 'u-0',
    description: null,
    deadline: null,
    annulationMotif: null,
    clotureAt: null,
    annuleAt: null,
    createdAt: '2026-07-18T10:00:00Z',
    ...overrides,
  };
}

describe('responsableLabel', () => {
  it('compose « Prenom Nom » quand resolu', () => {
    expect(responsableLabel({ assignePrenom: 'Karim', assigneNom: 'Bennani' })).toBe('Karim Bennani');
  });

  it('renvoie « Non assigne » quand les deux champs sont vides', () => {
    expect(responsableLabel({ assignePrenom: null, assigneNom: null })).toBe('Non assigne');
    expect(responsableLabel({})).toBe('Non assigne');
  });
});

describe('TableView — colonne Responsable', () => {
  it('affiche le nom du responsable pour un ticket assigne', () => {
    render(
      <TableView
        tickets={[
          ticket({
            id: 't-a',
            assigneId: 'emp-1',
            assignePrenom: 'Karim',
            assigneNom: 'Bennani',
          }),
        ]}
        onSelect={() => {}}
      />,
    );
    expect(screen.getByText('Responsable')).toBeInTheDocument(); // en-tete
    expect(screen.getByText('Karim Bennani')).toBeInTheDocument();
    // Jamais l'UUID d'assignation.
    expect(screen.queryByText(/emp-1/)).not.toBeInTheDocument();
  });

  it('affiche « Non assigne » pour un ticket sans responsable', () => {
    render(<TableView tickets={[ticket({ id: 't-b', assigneId: null })]} onSelect={() => {}} />);
    const row = screen.getByText('Creation SARL').closest('tr') as HTMLElement;
    expect(within(row).getByText('Non assigne')).toBeInTheDocument();
  });
});
