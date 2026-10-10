import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';

/**
 * Lot L1, RG-TKT-07 (cahier des charges mis a jour le 2026-10-10), a l'ecran :
 * enregistrement automatique au fil de la saisie (et a la sortie du champ, et a la
 * fermeture du panneau), note validable vide, lecture seule apres la cloture et pour
 * le superviseur, jamais affichee au client, aides presentes.
 */

const getNote = vi.fn();
const saveNote = vi.fn();

vi.mock('../../../services/ticket.service', () => ({
  ticketService: {
    getNote: (...a: unknown[]) => getNote(...a),
    saveNote: (...a: unknown[]) => saveNote(...a),
  },
}));

import { DELAI_ENREGISTREMENT_MS, NoteTicketPanel } from '../NoteTicketPanel';

async function attendreChargement() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('NoteTicketPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    window.localStorage.clear();
    getNote.mockReset().mockResolvedValue({ ticketId: 't1', contenu: 'Rappeler le greffe', modifiePar: 'e1', modifieLe: '2026-10-10T09:00:00Z' });
    saveNote.mockReset().mockImplementation(async (_t: string, contenu: string) =>
      ({ ticketId: 't1', contenu, modifiePar: 'e1', modifieLe: '2026-10-10T09:05:00Z' }));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('enregistre automatiquement apres une pause de saisie, sans bouton', async () => {
    render(<NoteTicketPanel ticketId="t1" statut="GENERATION_DOCUMENTS" role="EMPLOYE" />);
    await attendreChargement();
    const champ = screen.getByRole('textbox', { name: 'Note du ticket' });
    expect(champ).toHaveValue('Rappeler le greffe');

    fireEvent.change(champ, { target: { value: 'Rappeler le greffe lundi' } });
    expect(saveNote).not.toHaveBeenCalled();
    await act(async () => {
      vi.advanceTimersByTime(DELAI_ENREGISTREMENT_MS);
    });
    expect(saveNote).toHaveBeenCalledTimes(1);
    expect(saveNote).toHaveBeenCalledWith('t1', 'Rappeler le greffe lundi');
    expect(screen.queryByRole('button', { name: /enregistrer/i })).toBeNull();
  });

  it('une note videe s enregistre aussi (validable meme vide)', async () => {
    render(<NoteTicketPanel ticketId="t1" statut="CREATION_TICKET" role="EMPLOYE" />);
    await attendreChargement();
    fireEvent.change(screen.getByRole('textbox', { name: 'Note du ticket' }), { target: { value: '' } });
    await act(async () => {
      vi.advanceTimersByTime(DELAI_ENREGISTREMENT_MS);
    });
    expect(saveNote).toHaveBeenCalledWith('t1', '');
  });

  it('rien n est perdu en quittant : sortie du champ et fermeture du panneau envoient la saisie', async () => {
    const { unmount } = render(<NoteTicketPanel ticketId="t1" statut="GENERATION_DOCUMENTS" role="EMPLOYE" />);
    await attendreChargement();
    const champ = screen.getByRole('textbox', { name: 'Note du ticket' });
    fireEvent.change(champ, { target: { value: 'A' } });
    fireEvent.blur(champ);
    expect(saveNote).toHaveBeenLastCalledWith('t1', 'A');

    fireEvent.change(champ, { target: { value: 'AB' } });
    unmount();
    expect(saveNote).toHaveBeenLastCalledWith('t1', 'AB');
    expect(saveNote).toHaveBeenCalledTimes(2);
  });

  it('apres la cloture : lecture seule, aucun enregistrement', async () => {
    render(<NoteTicketPanel ticketId="t1" statut="CLOTURE_DOSSIER" role="EMPLOYE" />);
    await attendreChargement();
    const champ = screen.getByRole('textbox', { name: 'Note du ticket' });
    expect(champ).toHaveAttribute('readonly');
    expect(champ).toHaveValue('Rappeler le greffe');
    expect(screen.getByText(/Ticket clos : la note se consulte en lecture seule/)).toBeInTheDocument();
    fireEvent.blur(champ);
    expect(saveNote).not.toHaveBeenCalled();
  });

  it('le superviseur lit sans ecrire', async () => {
    render(<NoteTicketPanel ticketId="t1" statut="GENERATION_DOCUMENTS" role="SUPERVISEUR" />);
    await attendreChargement();
    expect(screen.getByRole('textbox', { name: 'Note du ticket' })).toHaveAttribute('readonly');
    expect(screen.getByText(/rédigée par l’employé responsable/)).toBeInTheDocument();
  });

  it('jamais affichee au client, et jamais demandee pour lui', async () => {
    const { container } = render(<NoteTicketPanel ticketId="t1" statut="GENERATION_DOCUMENTS" role="CLIENT" />);
    await attendreChargement();
    expect(container).toBeEmptyDOMElement();
    expect(getNote).not.toHaveBeenCalled();
  });

  it('echec d enregistrement : le texte reste et repart a la saisie suivante', async () => {
    saveNote.mockRejectedValueOnce(new Error('Reseau indisponible'));
    render(<NoteTicketPanel ticketId="t1" statut="GENERATION_DOCUMENTS" role="EMPLOYE" />);
    await attendreChargement();
    const champ = screen.getByRole('textbox', { name: 'Note du ticket' });
    fireEvent.change(champ, { target: { value: 'X' } });
    await act(async () => {
      vi.advanceTimersByTime(DELAI_ENREGISTREMENT_MS);
    });
    expect(screen.getByText(/Échec de l’enregistrement/)).toBeInTheDocument();
    expect(champ).toHaveValue('X');
    fireEvent.change(champ, { target: { value: 'XY' } });
    await act(async () => {
      vi.advanceTimersByTime(DELAI_ENREGISTREMENT_MS);
    });
    expect(saveNote).toHaveBeenLastCalledWith('t1', 'XY');
  });

  it('porte ses aides : infobulle et texte d aide masquable puis reaffichable', async () => {
    render(<NoteTicketPanel ticketId="t1" statut="GENERATION_DOCUMENTS" role="EMPLOYE" />);
    await attendreChargement();
    fireEvent.focus(screen.getByRole('button', { name: 'À quoi sert la note du ticket ?' }));
    expect(screen.getByRole('tooltip')).toHaveTextContent('jamais visible par le client');
    expect(screen.getByRole('note', { name: 'Comment fonctionne la note ?' }))
      .toHaveTextContent('enregistrée automatiquement');
    fireEvent.click(screen.getByRole('button', { name: 'Masquer l’aide' }));
    expect(screen.queryByRole('note', { name: 'Comment fonctionne la note ?' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Afficher l’aide/ }));
    expect(screen.getByRole('note', { name: 'Comment fonctionne la note ?' })).toBeInTheDocument();
  });
});
