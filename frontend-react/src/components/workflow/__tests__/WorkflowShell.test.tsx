import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuthStore } from '../../../store/authStore';
import type { Role } from '../../../types/auth';
import type { Ticket } from '../../../types/ticket';
import { WorkflowShell } from '../WorkflowShell';

/**
 * Lot L6b (E5) : coquille des parcours. En-tete standard (titre h1, fil d'Ariane vers le
 * ticket), frise des etapes ; le superviseur ne voit aucun bouton que le serveur lui refuse
 * (enregistrement, validation, annulation : 403) et les champs sont en lecture seule.
 */
const TICKET = {
  id: 't-1', workspaceId: 'ws-1', reference: 'TCK-2026-0042', titre: 'Dissolution de la société Atlas',
  type: 'DISSOLUTION', statut: 'EN_COURS', priorite: 'NORMALE', dossierId: 'd-1', assigneId: null,
  creeParId: 'u-1', description: null, deadline: null, annulationMotif: null, clotureAt: null, annuleAt: null,
  createdAt: '2026-10-01T10:00:00Z',
} as unknown as Ticket;

const ETAPES = [
  { number: 1, label: 'Décision' },
  { number: 2, label: 'Documents' },
];

function connecter(role: Role) {
  useAuthStore.setState({
    user: { role, email: 'x@cabinet.ma', userId: 'u-1', workspaceId: 'ws-1', workspaceCode: 'JUR-TEST1' } as never,
    isAuthenticated: true,
  } as never);
}

function rendre() {
  return render(
    <MemoryRouter>
      <WorkflowShell
        ticket={TICKET}
        steps={ETAPES}
        currentStep={1}
        saving={false}
        onPrev={vi.fn()}
        onSaveDraft={vi.fn()}
        onValidate={vi.fn()}
        onCancel={vi.fn()}
      >
        <input aria-label="Date de la décision" />
      </WorkflowShell>
    </MemoryRouter>,
  );
}

afterEach(() => useAuthStore.setState({ user: null, isAuthenticated: false } as never));

describe('WorkflowShell', () => {
  it('pose le titre du ticket, le fil vers le ticket et la frise', () => {
    connecter('EMPLOYE');
    rendre();
    expect(screen.getByRole('heading', { level: 1, name: 'Dissolution de la société Atlas' })).toBeInTheDocument();
    const fil = screen.getByRole('navigation', { name: /fil d.ariane/i });
    expect(within(fil).getByRole('link', { name: 'TCK-2026-0042' })).toHaveAttribute('href', '/tickets/t-1');
    expect(screen.getByRole('navigation', { name: 'Progression du parcours' })).toBeInTheDocument();
  });

  it('montre a l employe les actions de l etape et l annulation', () => {
    connecter('EMPLOYE');
    rendre();
    expect(screen.getByRole('button', { name: 'Valider et continuer' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sauvegarder le brouillon/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Annuler le ticket/ })).toBeInTheDocument();
    expect(screen.getByLabelText('Date de la décision')).toBeEnabled();
  });

  it('ne montre au superviseur aucune action refusee par le serveur et fige la saisie', () => {
    connecter('SUPERVISEUR');
    rendre();
    expect(screen.queryByRole('button', { name: 'Valider et continuer' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Sauvegarder le brouillon/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Annuler le ticket/ })).toBeNull();
    expect(screen.getByLabelText('Date de la décision')).toBeDisabled();
    expect(screen.getByText(/En tant que superviseur, vous suivez ce parcours/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Quitter/ })).toBeInTheDocument();
  });
});
