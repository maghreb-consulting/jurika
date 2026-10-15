import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ProchaineActionCarte } from '../ProchaineActionCarte';
import { prochainesActions } from '../prochaineAction';
import { aideStatutTicket } from '../../../pages/tickets/textesAideStatut';
import type { Ticket } from '../../../types/ticket';
import type { EcheanceItem } from '../../../types/dashboard';

// Lot L6b (E9, point 8) : carte " Prochaine action " de l'employe. Ce qu'il faut faire vient
// de l'aide du statut du ticket (aucune regle nouvelle) ; l'echeance la plus proche trie.

function ticket(p: Partial<Ticket>): Ticket {
  return {
    id: 't', workspaceId: 'w1', reference: 'TK-1', titre: 'Ticket', type: 'CREATION', statut: 'CREATION_TICKET',
    priorite: 'NORMALE', dossierId: null, assigneId: 'u1', creeParId: 'u1', description: null, deadline: null,
    annulationMotif: null, clotureAt: null, annuleAt: null, createdAt: '2026-10-01T08:00:00Z', ...p,
  };
}

const AUJOURDHUI = new Date(2026, 9, 15); // 15 octobre 2026

const tickets: Ticket[] = [
  ticket({ id: 'a', reference: 'TK-A', titre: 'Sans échéance', statut: 'GENERATION_DOCUMENTS' }),
  ticket({ id: 'b', reference: 'TK-B', titre: 'Démarche proche', statut: 'DEROULEMENT_DEMARCHE', dossierId: 'd1' }),
  ticket({ id: 'c', reference: 'TK-C', titre: 'Date limite lointaine', deadline: '2026-11-30' }),
  ticket({ id: 'd', reference: 'TK-D', titre: 'Clos', statut: 'CLOTURE_DOSSIER', deadline: '2026-10-01' }),
  ticket({ id: 'e', reference: 'TK-E', titre: 'Annulé', statut: 'ANNULE' }),
  ticket({ id: 'f', reference: 'TK-F', titre: 'Autre responsable', assigneId: 'u2', deadline: '2026-10-14' }),
];
const echeances: EcheanceItem[] = [
  { id: 'e2', dossierId: 'd1', libelle: 'Dépôt au greffe', dateEcheance: '2026-10-25', severite: 'APPROCHE' },
  { id: 'e1', dossierId: 'd1', libelle: 'Enregistrement des statuts', dateEcheance: '2026-10-17', severite: 'CRITIQUE' },
];

describe('prochainesActions', () => {
  it('ecarte les tickets clos et annules, trie par echeance la plus proche, sans echeance a la fin', () => {
    const r = prochainesActions(tickets, echeances, 'u1');
    expect(r.map((a) => a.ticket.id)).toEqual(['f', 'b', 'c', 'a']);
  });

  it('prend la plus proche des demarches du dossier et le texte d aide du statut', () => {
    const b = prochainesActions(tickets, echeances, 'u1').find((a) => a.ticket.id === 'b')!;
    expect(b.echeance).toEqual({ date: '2026-10-17', source: 'DEMARCHE', libelle: 'Enregistrement des statuts', severite: 'CRITIQUE' });
    expect(b.aide.faire).toBe(aideStatutTicket('DEROULEMENT_DEMARCHE', 'RESPONSABLE').faire);
  });

  it('un ticket assigne a un autre employe est en consultation', () => {
    const f = prochainesActions(tickets, echeances, 'u1').find((a) => a.ticket.id === 'f')!;
    expect(f.profil).toBe('LECTURE');
    expect(f.aide.faire).toBe(aideStatutTicket('CREATION_TICKET', 'LECTURE').faire);
  });

  it('les echeances indisponibles laissent les dates limites des tickets', () => {
    const r = prochainesActions(tickets, null, 'u1');
    expect(r.map((a) => a.ticket.id)).toEqual(['f', 'c', 'a', 'b']);
  });
});

describe('ProchaineActionCarte', () => {
  const rendre = (p: Partial<Parameters<typeof ProchaineActionCarte>[0]>) =>
    render(
      <MemoryRouter>
        <ProchaineActionCarte tickets={tickets} echeances={echeances} userId="u1" loading={false} aujourdHui={AUJOURDHUI} {...p} />
      </MemoryRouter>,
    );

  it('liste les actions dans l ordre, avec le lien vers le ticket et l echeance', () => {
    rendre({});
    const liste = screen.getByRole('list', { name: /Prochaines actions/ });
    const lignes = within(liste).getAllByRole('listitem');
    expect(lignes).toHaveLength(4);
    expect(within(lignes[0]).getByRole('link')).toHaveAttribute('href', '/tickets/f');
    expect(within(lignes[0]).getByText('Dépassée de 1 jour')).toBeInTheDocument();
    expect(within(lignes[1]).getByRole('link')).toHaveAttribute('href', '/tickets/b');
    expect(within(lignes[1]).getByText('Démarche : Enregistrement des statuts')).toBeInTheDocument();
    expect(within(lignes[1]).getByText('Dans 2 jours')).toBeInTheDocument();
    expect(within(lignes[1]).getByText(aideStatutTicket('DEROULEMENT_DEMARCHE', 'RESPONSABLE').faire)).toBeInTheDocument();
    expect(within(lignes[3]).getByText('Aucune échéance connue')).toBeInTheDocument();
    expect(screen.queryByText(/TK-D/)).not.toBeInTheDocument();
  });

  it('etat vide qui explique quoi faire', () => {
    rendre({ tickets: [] });
    expect(screen.getByText('Aucune action en attente')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ouvrir les tickets' })).toHaveAttribute('href', '/tickets');
  });

  it('tickets indisponibles : dit, jamais « aucune action »', () => {
    rendre({ tickets: null });
    expect(screen.getByText('Vos tickets n’ont pas pu être chargés')).toBeInTheDocument();
    expect(screen.queryByText('Aucune action en attente')).not.toBeInTheDocument();
  });

  it('echeances indisponibles : le dit', () => {
    rendre({ echeances: null });
    expect(screen.getByText(/échéances des démarches n’ont pas pu être chargées/)).toBeInTheDocument();
  });
});
