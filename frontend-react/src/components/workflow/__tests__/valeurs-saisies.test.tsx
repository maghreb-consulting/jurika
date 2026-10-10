import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, renderHook, act } from '@testing-library/react';
import { ValeurProposee } from '../ValeurProposee';
import { useSeanceForm, serializeAssocie, emptyAssocie, emptyResolution } from '../SeanceForm';

/**
 * Lot L3 (D6) : aucune valeur par defaut silencieuse. Vote, quorum et presence sont
 * saisis ; les valeurs d'usage sont proposees, affichees, et transmises seulement une
 * fois confirmees par l'employe.
 */
describe('Valeur proposée (L3, D6)', () => {
  it('affiche la proposition, non transmise tant qu’elle n’est pas confirmée', () => {
    const onChange = vi.fn();
    render(<ValeurProposee id="nb" label="Nombre d’originaux" proposition="6" valeur={undefined} onChange={onChange} />);
    expect(screen.getByRole('textbox')).toHaveValue('6');
    expect(screen.getByRole('status')).toHaveTextContent('Valeur proposée, à confirmer.');
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }));
    expect(onChange).toHaveBeenCalledWith('6');
  });

  it('une valeur confirmée n’offre plus le bouton et le dit', () => {
    render(<ValeurProposee id="h" label="Heure de l’acte" proposition="10 heures" valeur="11 heures" onChange={() => {}} />);
    expect(screen.queryByRole('button', { name: 'Confirmer' })).toBeNull();
    expect(screen.getByRole('textbox')).toHaveValue('11 heures');
    expect(screen.getByRole('status')).toHaveTextContent('Valeur confirmée.');
  });
});

describe('Séance : vote, quorum et présence saisis (L3, D6)', () => {
  const societe = { denomination: 'PARACOSME', villeGreffe: 'Casablanca' };

  it('rien n’est supposé : présence, pièce, résultat, quorum et vote vides', () => {
    expect(emptyAssocie().presence).toBe('');
    expect(emptyAssocie().pieceType).toBe('');
    expect(emptyResolution().resultat).toBe('');
    expect(serializeAssocie({ ...emptyAssocie(), nom: 'ALAOUI' }).pieceType).toBe('');
    const { result } = renderHook(() => useSeanceForm({ formeJuridique: 'SARL', societe }));
    const p = result.current.buildPayload() as { quorumAtteint: string; vote: Record<string, string> };
    expect(p.quorumAtteint).toBe('');
    expect(p.vote).toEqual({ resultat: '', majorite: '', voixPour: '', voixContre: '', abstentions: '' });
  });

  it('le vote et le quorum saisis partent dans la charge utile', () => {
    const { result } = renderHook(() => useSeanceForm({ formeJuridique: 'SARL', societe }));
    act(() => {
      result.current.setQuorumAtteint('oui');
      result.current.setVote({ resultat: 'adoptée', majorite: 'à l’unanimité', voixPour: '1000', voixContre: '0', abstentions: '0' });
    });
    const p = result.current.buildPayload() as { quorumAtteint: string; vote: Record<string, string> };
    expect(p.quorumAtteint).toBe('oui');
    expect(p.vote.majorite).toBe('à l’unanimité');
    expect(p.vote.voixPour).toBe('1000');
  });
});
