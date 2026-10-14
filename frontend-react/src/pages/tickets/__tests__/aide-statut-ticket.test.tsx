import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AideStatutTicket } from '../AideStatutTicket';
import { aideStatutTicket, profilTicket } from '../textesAideStatut';
import type { TicketStatut } from '../../../types/ticket';

/**
 * Lot L6 (E9, CDC 15.4) : a chaque statut du ticket, l'aide dit ce qu'il faut faire, ce qui
 * manque (obstacles nommes par le serveur) et ce qui vient ensuite, selon le profil.
 */
describe('Aide du statut du ticket', () => {
  beforeEach(() => window.localStorage.clear());

  it('profil : seul l employe en charge est responsable', () => {
    expect(profilTicket('EMPLOYE', true)).toBe('RESPONSABLE');
    expect(profilTicket('EMPLOYE', false)).toBe('LECTURE');
    expect(profilTicket('SUPERVISEUR', false)).toBe('SUPERVISEUR');
    expect(profilTicket(undefined, false)).toBe('LECTURE');
  });

  it('chaque statut a un texte, a faire et ensuite, pour chaque profil', () => {
    const statuts: TicketStatut[] = ['CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE', 'CLOTURE_DOSSIER', 'ANNULE'];
    for (const s of statuts) {
      for (const p of ['RESPONSABLE', 'SUPERVISEUR', 'LECTURE'] as const) {
        const a = aideStatutTicket(s, p);
        expect(a.faire.length).toBeGreaterThan(20);
        expect(a.ensuite.length).toBeGreaterThan(10);
      }
    }
    expect(aideStatutTicket('GENERATION_DOCUMENTS', 'SUPERVISEUR').faire).toContain('Seul l’employé responsable');
    expect(aideStatutTicket('GENERATION_DOCUMENTS', 'RESPONSABLE').faire).toContain('Ouvrez le parcours');
  });

  it('liste les obstacles nommes par le serveur', () => {
    render(<AideStatutTicket statut="DEROULEMENT_DEMARCHE" profil="RESPONSABLE"
      obstacles={['Démarche obligatoire non cochée : dépôt au greffe', 'Donnée attendue : numéro RC']} />);
    expect(screen.getByText('Étape 3 sur 4 : déroulement de la démarche')).toBeInTheDocument();
    expect(screen.getByText('Démarche obligatoire non cochée : dépôt au greffe')).toBeInTheDocument();
    expect(screen.getByText('Donnée attendue : numéro RC')).toBeInTheDocument();
  });

  it('renvoie au passage de statut quand les obstacles y sont deja listes', () => {
    render(<AideStatutTicket statut="CREATION_TICKET" profil="RESPONSABLE" obstacles={['a', 'b']} renvoi />);
    expect(screen.getByText(/2 points à régler/)).toBeInTheDocument();
    expect(screen.queryByTestId('aide-obstacles')).toBeNull();
  });

  it('n affirme rien tant que le serveur n a pas repondu, et rien ne manque a un ticket clos', () => {
    const { rerender } = render(<AideStatutTicket statut="GENERATION_DOCUMENTS" profil="LECTURE" obstacles={null} />);
    expect(screen.queryByText(/Ce qui manque/)).toBeNull();
    rerender(<AideStatutTicket statut="CLOTURE_DOSSIER" profil="RESPONSABLE" obstacles={[]} />);
    expect(screen.queryByText(/Ce qui manque/)).toBeNull();
    expect(screen.getByText(/archive de l’opération/)).toBeInTheDocument();
  });

  it('rien ne manque : le dit', () => {
    render(<AideStatutTicket statut="GENERATION_DOCUMENTS" profil="RESPONSABLE" obstacles={[]} />);
    expect(screen.getByText(/rien n’empêche le passage/)).toBeInTheDocument();
  });
});
