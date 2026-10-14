import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Step1Denomination } from '../Step1Denomination';
import { Step5Dirigeants } from '../Step5Dirigeants';
import { Step9Synthese } from '../Step9Synthese';

/**
 * Lot L6 (E8) : etapes de la creation.
 *
 * - Etape 1 : la forme verrouillee a l'ouverture du ticket l'emporte au rendu (avant :
 *   un effet recopiait la forme dans l'etat) ; ce qui manque est nomme a cote du bouton.
 * - Etape 9 : la liste des documents est celle des documents VALIDES a l'etape 7
 *   (avant : des noms de fichiers ecrits en dur, et un bouton « Telecharger » inerte).
 * - Etape 5 : RG-C15 en SARL AU, le dirigeant que l'on vient de cocher « Egalement
 *   associe » est celui qui reste coche (avant : un effet gardait le dernier de la liste).
 */
describe('Étape 1 (L6) — forme verrouillée et motifs du blocage', () => {
  const complet = {
    denomination: 'PARACOSME',
    ice: '000 000 000 000 001',
    cnNumero: 'CN-2026-001',
    cnDate: '2026-10-01',
    activiteCn: 'conseil',
    beneficiaire: 'M. BENANI',
    formeJuridique: 'SARL',
  };

  it('envoie la forme choisie à l’ouverture du ticket, même si le brouillon en porte une autre', () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <Step1Denomination
        existing={complet}
        lockedFormeJuridique="SARL_AU"
        saving={false}
        onSubmit={onSubmit}
      />,
    );
    expect(screen.getByText('SARL AU (associé unique)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Valider et continuer/i }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ formeJuridique: 'SARL_AU' }));
  });

  it('nomme ce qui manque au lieu de griser le bouton sans explication', () => {
    render(<Step1Denomination existing={{}} saving={false} onSubmit={vi.fn()} />);
    const blocage = screen.getByTestId('step1-blocage');
    expect(blocage).toHaveTextContent('Saisissez l’ICE.');
    expect(blocage).toHaveTextContent('Saisissez le numéro du certificat négatif.');
    expect(screen.getByRole('button', { name: /Valider et continuer/i })).toBeDisabled();
  });
});

describe('Étape 5 (L6) — un seul dirigeant associé en SARL AU (RG-C15)', () => {
  it('garde coché le dirigeant que l’on vient de cocher', () => {
    const existing = {
      dirigeants: [
        { id: 'd1', nom: 'ALAOUI', prenom: 'Ali', typePersonne: 'PHYSIQUE', isAssociate: false },
        { id: 'd2', nom: 'BENANI', prenom: 'Sara', typePersonne: 'PHYSIQUE', isAssociate: false },
      ],
    };
    render(
      <Step5Dirigeants existing={existing} formeJuridique="SARL_AU" saving={false} onSubmit={vi.fn()} />,
    );
    const cases = () => screen.getAllByRole('switch', { name: /Également associé/i });
    fireEvent.click(cases()[1]);
    expect(cases()[1]).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(cases()[0]);
    expect(cases()[0]).toHaveAttribute('aria-checked', 'true');
    expect(cases()[1]).toHaveAttribute('aria-checked', 'false');
  });

  it('une liste rechargée avec deux associés n’en garde qu’un (le dernier, comme avant)', () => {
    const existing = {
      dirigeants: [
        { id: 'd1', nom: 'ALAOUI', prenom: 'Ali', typePersonne: 'PHYSIQUE', isAssociate: true },
        { id: 'd2', nom: 'BENANI', prenom: 'Sara', typePersonne: 'PHYSIQUE', isAssociate: true },
      ],
    };
    render(
      <Step5Dirigeants existing={existing} formeJuridique="SARL_AU" saving={false} onSubmit={vi.fn()} />,
    );
    const cases = screen.getAllByRole('switch', { name: /Également associé/i });
    expect(cases[0]).toHaveAttribute('aria-checked', 'false');
    expect(cases[1]).toHaveAttribute('aria-checked', 'true');
  });
});

describe('Étape 9 (L6) — documents validés à l’étape 7', () => {
  it('liste les seuls documents validés, sous leur libellé', () => {
    const data = {
      step1: { denomination: { denomination: 'PARACOSME', formeJuridique: 'SARL' } },
      step7: {
        documents: {
          STATUTS_SARL: { generated: true, validated: true },
          ANNONCE_LEGALE_CONSTITUTION: { generated: true, validated: false },
        },
      },
    };
    render(<Step9Synthese data={data} saving={false} onSubmit={vi.fn()} />);
    expect(screen.getByText('Statuts de la société')).toBeInTheDocument();
    expect(screen.queryByText(/Statuts_PARACOSME/)).toBeNull();
    expect(screen.queryByRole('button', { name: /Télécharger/i })).toBeNull();
  });

  it('dit quoi faire quand aucun document n’est validé', () => {
    render(<Step9Synthese data={{ step1: {} }} saving={false} onSubmit={vi.fn()} />);
    expect(screen.getByText(/Aucun document validé pour l’instant/)).toBeInTheDocument();
  });
});
