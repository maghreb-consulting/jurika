import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { Step3Capital } from '../Step3Capital';
import { Step5Dirigeants } from '../Step5Dirigeants';
import { Step6Associes } from '../Step6Associes';
import type { DirtyGetter } from '../../useWorkflow';

/**
 * Lot L7 (E2) : aucune valeur par defaut silencieuse dans la creation. Le capital en
 * numeraire (100 000), la part liberee (25 000), le nombre de parts (1 000) et la
 * nationalite du representant ("Marocaine") partaient dans les actes sans geste de
 * l'employe. Ils sont vides, ou proposes et a confirmer ; un brouillon garde ses valeurs.
 */
function capturer() {
  const ref: { getter: DirtyGetter | null } = { getter: null };
  const registerDirty = vi.fn((_step: number, g: DirtyGetter) => {
    ref.getter = g;
    return () => undefined;
  });
  return { ref, registerDirty };
}

describe('Étape 3 de la création (L7) — capital sans valeur d’office', () => {
  it('montant, part libérée et nombre de parts sont vides et rien n’est transmis', () => {
    const { ref, registerDirty } = capturer();
    render(<Step3Capital existing={{}} saving={false} onSubmit={vi.fn()} registerDirty={registerDirty} />);
    expect(screen.getByRole('spinbutton', { name: 'Montant total (MAD)' })).toHaveValue(null);
    expect(screen.getByRole('spinbutton', { name: 'Dont libéré (MAD)' })).toHaveValue(null);
    expect(screen.getByRole('spinbutton', { name: 'Nombre total de parts sociales' })).toHaveValue(null);
    const charge = ref.getter?.() ?? {};
    expect(charge.apportNumeraire).toBeNull();
    expect(charge.apportNumeraireLibere).toBeNull();
    expect(charge.nombreParts).toBeNull();
    expect(screen.getByRole('button', { name: /Valider et continuer/i })).toBeDisabled();
    expect(screen.getByText('Saisissez la part libérée de l’apport en numéraire.')).toBeInTheDocument();
  });

  it('transmet ce que l’employé saisit', () => {
    const { ref, registerDirty } = capturer();
    render(<Step3Capital existing={{}} saving={false} onSubmit={vi.fn()} registerDirty={registerDirty} />);
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Montant total (MAD)' }), { target: { value: '50000' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Dont libéré (MAD)' }), { target: { value: '12500' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Nombre total de parts sociales' }), { target: { value: '500' } });
    const charge = ref.getter?.() ?? {};
    expect(charge).toMatchObject({ apportNumeraire: 50000, apportNumeraireLibere: 12500, nombreParts: 500 });
  });

  it('un brouillon déjà enregistré garde ses valeurs', () => {
    const { ref, registerDirty } = capturer();
    render(
      <Step3Capital
        existing={{ capital: { apportNumeraire: 100000, apportNumeraireLibere: 25000, nombreParts: 1000, hasNumeraire: true } }}
        saving={false}
        onSubmit={vi.fn()}
        registerDirty={registerDirty}
      />,
    );
    expect(screen.getByRole('spinbutton', { name: 'Montant total (MAD)' })).toHaveValue(100000);
    expect(ref.getter?.()).toMatchObject({ apportNumeraire: 100000, apportNumeraireLibere: 25000, nombreParts: 1000 });
  });
});

describe('Étape 5 de la création (L7) — nationalité du représentant proposée, à confirmer', () => {
  const morale = { id: 'd1', typePersonne: 'MORALE', denomination: 'HOLDING ALPHA', repNom: 'ALAOUI' };

  it('n’envoie aucune nationalité tant que l’employé ne l’a pas confirmée', () => {
    const { ref, registerDirty } = capturer();
    render(
      <Step5Dirigeants
        existing={{ dirigeants: [morale] }}
        formeJuridique="SARL"
        saving={false}
        onSubmit={vi.fn()}
        registerDirty={registerDirty}
      />,
    );
    const champ = document.getElementById('dirigeant-rep-nationalite-d1') as HTMLInputElement;
    expect(champ).toHaveValue('marocaine');
    expect(screen.getAllByText('Valeur proposée, à confirmer.').length).toBeGreaterThan(0);
    const dirigeants = (ref.getter?.()?.dirigeants ?? []) as Array<Record<string, unknown>>;
    expect(dirigeants[0].repNationalite).toBe('');
  });

  it('transmet la nationalité confirmée, et garde celle d’un brouillon', () => {
    const { ref, registerDirty } = capturer();
    const vue = render(
      <Step5Dirigeants
        existing={{ dirigeants: [morale] }}
        formeJuridique="SARL"
        saving={false}
        onSubmit={vi.fn()}
        registerDirty={registerDirty}
      />,
    );
    // Le bouton "Confirmer" de la valeur proposee (meme bloc que le libelle du champ).
    const bloc = document.querySelector('label[for="dirigeant-rep-nationalite-d1"]')?.parentElement as HTMLElement;
    fireEvent.click(within(bloc).getByRole('button', { name: 'Confirmer' }));
    let dirigeants = (ref.getter?.()?.dirigeants ?? []) as Array<Record<string, unknown>>;
    expect(dirigeants[0].repNationalite).toBe('marocaine');
    vue.unmount();

    const second = capturer();
    render(
      <Step5Dirigeants
        existing={{ dirigeants: [{ ...morale, repNationalite: 'Marocaine' }] }}
        formeJuridique="SARL"
        saving={false}
        onSubmit={vi.fn()}
        registerDirty={second.registerDirty}
      />,
    );
    dirigeants = (second.ref.getter?.()?.dirigeants ?? []) as Array<Record<string, unknown>>;
    expect(dirigeants[0].repNationalite).toBe('Marocaine');
  });
});

describe('Étape 6 de la création (L7) — même règle pour le représentant d’un associé personne morale', () => {
  it('nationalité proposée, non transmise avant confirmation', () => {
    const { ref, registerDirty } = capturer();
    render(
      <Step6Associes
        existing={{ associes: [{ id: 'a1', typePersonne: 'MORALE', denomination: 'HOLDING BETA' }] }}
        formeJuridique="SARL"
        saving={false}
        onSubmit={vi.fn()}
        registerDirty={registerDirty}
      />,
    );
    const champ = document.getElementById('associe-rep-nationalite-a1') as HTMLInputElement;
    expect(champ).toHaveValue('marocaine');
    const associes = (ref.getter?.()?.associes ?? []) as Array<Record<string, unknown>>;
    expect(associes[0].repNationalite).toBe('');
  });
});
