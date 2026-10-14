import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

vi.mock('../../../services/billing.service', () => ({
  billingService: { getPricing: vi.fn().mockRejectedValue(new Error('hors ligne')) },
}));

import { FormulaireCmi } from '../FormulaireCmi';
import { PlanPicker } from '../PlanPicker';
import { ChoixModePaiement } from '../ChoixModePaiement';

/** Lot L7 (E5) : composants de la facturation (RG-PAY-04, grille du CDC 4.3). */
describe('FormulaireCmi', () => {
  beforeEach(() => {
    HTMLFormElement.prototype.submit = vi.fn();
  });

  it('soumet en POST vers la page du CMI avec chaque champ signe en champ cache', () => {
    render(<FormulaireCmi cmi={{ url: 'https://cmi.test/fim/est3Dgate', champs: { clientid: '600', amount: '450.00', oid: 'JUR1', HASH: 'abc==' } }} />);
    const form = screen.getByTestId('formulaire-cmi') as HTMLFormElement;
    expect(form.method.toLowerCase()).toBe('post');
    expect(form.action).toBe('https://cmi.test/fim/est3Dgate');
    const caches = Array.from(form.querySelectorAll('input[type="hidden"]')) as HTMLInputElement[];
    expect(caches.map((i) => [i.name, i.value])).toEqual([
      ['clientid', '600'], ['amount', '450.00'], ['oid', 'JUR1'], ['HASH', 'abc=='],
    ]);
    expect(HTMLFormElement.prototype.submit).toHaveBeenCalledTimes(1);
  });

  it('sans soumission automatique, attend le clic', () => {
    render(<FormulaireCmi autoSoumettre={false} cmi={{ url: 'https://cmi.test', champs: {} }} />);
    expect(HTMLFormElement.prototype.submit).not.toHaveBeenCalled();
  });
});

describe('PlanPicker', () => {
  it('affiche la grille du cahier des charges, prix hors taxes', () => {
    const onChoisir = vi.fn();
    render(<PlanPicker period="yearly" onPeriodChange={vi.fn()} onChoisir={onChoisir} />);
    expect(screen.getByTestId('plan-essentiel')).toHaveTextContent('3 750 DH HT / an');
    expect(screen.getByTestId('plan-professionnel')).toHaveTextContent('7 500 DH HT / an');
    expect(screen.getByTestId('plan-max')).toHaveTextContent('15 000 DH HT / an');
    expect(screen.getByTestId('plan-sur_devis')).toHaveTextContent('Selon devis');
    expect(screen.getByTestId('plan-essentiel')).toHaveTextContent('Jusqu’à 25 dossiers par mois d’abonnement');
    expect(screen.getByTestId('plan-essentiel')).toHaveTextContent('1 superviseur + 1 accès employé');
    expect(screen.getByTestId('plan-max')).toHaveTextContent('1 superviseur + 4 accès employés');
    expect(screen.getByTestId('plan-professionnel')).toHaveTextContent('Messagerie interne');
    fireEvent.click(screen.getByTestId('plan-choisir-professionnel'));
    expect(onChoisir).toHaveBeenCalledWith(expect.objectContaining({ code: 'professionnel' }));
  });

  it('en paiement mensuel : 375 DH HT par mois, soit 4 500 DH HT sur un an', () => {
    render(<PlanPicker period="monthly" onPeriodChange={vi.fn()} onChoisir={vi.fn()} />);
    expect(screen.getByTestId('plan-essentiel')).toHaveTextContent('375 DH HT / mois');
    expect(screen.getByTestId('plan-essentiel')).toHaveTextContent('soit 4 500 DH HT sur un an');
  });
});

describe('ChoixModePaiement', () => {
  it('desactive la carte avec son motif quand le CMI n’est pas configure', () => {
    render(<ChoixModePaiement valeur={null} onChange={vi.fn()} carteDisponible={false}
      carteMotif="Paiement par carte non encore disponible." />);
    const radios = screen.getAllByRole('radio') as HTMLInputElement[];
    expect(radios[0]).toBeDisabled();
    expect(radios.slice(1).every((r) => !r.disabled)).toBe(true);
    expect(screen.getByText('Paiement par carte non encore disponible.')).toBeInTheDocument();
  });
});
