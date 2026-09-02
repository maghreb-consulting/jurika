/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { SignupStep1Cabinet, type SignupProfileData } from '../SignupStep1Cabinet';

const EMPTY: SignupProfileData = {
  firstName: '', lastName: '', workspaceName: '', email: '', phone: '', city: '', ice: '',
};

function renderWithRouter(ui: React.ReactNode) {
  return render(<BrowserRouter>{ui}</BrowserRouter>);
}

describe('<SignupStep1Cabinet> (formulaire unique)', () => {
  it('renders the unified fields (person + structure)', () => {
    // La roadmap (SignupStepper) est desormais hissee dans l'orchestrateur
    // SignupPage et n'est plus rendue par ce composant d'etape.
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} />);
    expect(screen.getByTestId('signup-step-cabinet')).toBeInTheDocument();
    expect(screen.queryByTestId('signup-stepper')).not.toBeInTheDocument();
    expect(screen.getByTestId('signup-profile-type')).toBeInTheDocument();
    expect(screen.getByTestId('signup-first-name')).toBeInTheDocument();
    expect(screen.getByTestId('signup-last-name')).toBeInTheDocument();
    expect(screen.getByTestId('signup-workspace-name')).toBeInTheDocument();
    expect(screen.getByTestId('signup-admin-email')).toBeInTheDocument();
    expect(screen.getByTestId('signup-admin-phone')).toBeInTheDocument();
    expect(screen.getByTestId('signup-city')).toBeInTheDocument();
    expect(screen.getByTestId('signup-ice')).toBeInTheDocument();
  });

  it('pre-fills the denomination with "Prenom Nom" while untouched', () => {
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} />);
    const ws = screen.getByTestId('signup-workspace-name') as HTMLInputElement;
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    expect(ws.value).toBe('Jean');
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    expect(ws.value).toBe('Jean Dupont');
  });

  it('does NOT overwrite a manually edited denomination', () => {
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} />);
    const ws = screen.getByTestId('signup-workspace-name') as HTMLInputElement;
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    // saisie manuelle -> le champ est "touche"
    fireEvent.change(ws, { target: { value: 'Cabinet Atlas SARL' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    expect(ws.value).toBe('Cabinet Atlas SARL');
  });

  it('does not overwrite an initial (resumed) denomination', () => {
    const initial: SignupProfileData = { ...EMPTY, workspaceName: 'Cabinet Resume' };
    renderWithRouter(<SignupStep1Cabinet initial={initial} onNext={vi.fn()} />);
    const ws = screen.getByTestId('signup-workspace-name') as HTMLInputElement;
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    expect(ws.value).toBe('Cabinet Resume');
  });

  it('strips non-numeric chars from ICE and limits to 15', () => {
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} />);
    const ice = screen.getByTestId('signup-ice') as HTMLInputElement;
    fireEvent.change(ice, { target: { value: 'AB12 34CD5678901234567' } });
    expect(ice.value).toMatch(/^\d+$/);
    expect(ice.value.length).toBeLessThanOrEqual(15);
  });

  it('accepts an EMPTY ICE (optional) and submits', () => {
    const onNext = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType="ENTREPRISE" />,
    );
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'jean@atlas.ma' } });
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '612345678' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Casablanca' } });
    // ICE laisse vide
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(onNext).toHaveBeenCalledTimes(1);
    expect(onNext.mock.calls[0][0].ice).toBe('');
  });

  it('rejects a non-empty ICE that is not 15 digits', () => {
    const onNext = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType="ENTREPRISE" />,
    );
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'jean@atlas.ma' } });
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '612345678' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Casablanca' } });
    fireEvent.change(screen.getByTestId('signup-ice'), { target: { value: '1234567890' } });
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(screen.getByTestId('signup-step1-error')).toHaveTextContent(/ICE/i);
    expect(onNext).not.toHaveBeenCalled();
  });

  it('blocks advancing when no professional type is selected', () => {
    const onNext = vi.fn();
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType={null} />);
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'jean@atlas.ma' } });
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '612345678' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Casablanca' } });
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(screen.getByTestId('signup-step1-error')).toHaveTextContent(/type de profil/i);
    expect(onNext).not.toHaveBeenCalled();
  });

  it('calls onNext with trimmed valid data (with a 15-digit ICE)', () => {
    const onNext = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType="ENTREPRISE" />,
    );
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: '  Jean  ' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: '  Dupont  ' } });
    fireEvent.change(screen.getByTestId('signup-workspace-name'), { target: { value: '  Cabinet Atlas  ' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'contact@atlas.ma' } });
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '612345678' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Casablanca' } });
    fireEvent.change(screen.getByTestId('signup-ice'), { target: { value: '123456789012345' } });
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(onNext).toHaveBeenCalledWith({
      firstName: 'Jean',
      lastName: 'Dupont',
      workspaceName: 'Cabinet Atlas',
      email: 'contact@atlas.ma',
      phone: '+212612345678',
      city: 'Casablanca',
      ice: '123456789012345',
    });
  });

  it('renders the 8 professional-type cards and reports selection', () => {
    const onChange = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} onProfessionalTypeChange={onChange} />,
    );
    expect(screen.getByTestId('signup-profile-type-AVOCAT')).toBeInTheDocument();
    expect(screen.getByTestId('signup-profile-type-NOTAIRE')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('signup-profile-type-AVOCAT'));
    expect(onChange).toHaveBeenCalledWith('AVOCAT');
  });

  it('adapts the entity-name label to the chosen type (Notaire -> Nom de l etude)', () => {
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} professionalType="NOTAIRE" />);
    expect(screen.getByText(/Nom de l'etude/i)).toBeInTheDocument();
  });

  it('marks the entity-name field optional for an individual type (Avocat)', () => {
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} professionalType="AVOCAT" />);
    expect(screen.getByText(/Nom du cabinet \(optionnel\)/i)).toBeInTheDocument();
  });

  it('individual type: submits with "Prenom Nom" fallback when the entity name is left empty', () => {
    const onNext = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType="AVOCAT" />,
    );
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    // L'utilisateur vide explicitement la denomination (exercice en nom propre).
    fireEvent.change(screen.getByTestId('signup-workspace-name'), { target: { value: '' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'jean@barreau.ma' } });
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '612345678' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Rabat' } });
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(onNext).toHaveBeenCalledTimes(1);
    expect(onNext.mock.calls[0][0].workspaceName).toBe('Jean Dupont');
  });

  it('structure type: blocks submission when the raison sociale is left empty', () => {
    const onNext = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType="ENTREPRISE" />,
    );
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    // On vide la denomination auto-remplie : pour une structure c'est bloquant.
    fireEvent.change(screen.getByTestId('signup-workspace-name'), { target: { value: '' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'jean@atlas.ma' } });
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '612345678' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Casablanca' } });
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(screen.getByTestId('signup-step1-error')).toHaveTextContent(/Raison sociale/i);
    expect(onNext).not.toHaveBeenCalled();
  });

  it('phone selector defaults to Morocco (+212) and composes E.164 from the national number', () => {
    const onNext = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType="ENTREPRISE" />,
    );
    // Le bouton indicatif affiche +212 par defaut (Maroc).
    expect(screen.getByTestId('signup-admin-phone-country')).toHaveTextContent('+212');
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'jean@atlas.ma' } });
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '612345678' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Casablanca' } });
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(onNext).toHaveBeenCalledTimes(1);
    expect(onNext.mock.calls[0][0].phone).toBe('+212612345678');
  });

  it('rejects an invalid Moroccan phone number with an in-app message', () => {
    const onNext = vi.fn();
    renderWithRouter(
      <SignupStep1Cabinet initial={EMPTY} onNext={onNext} professionalType="ENTREPRISE" />,
    );
    fireEvent.change(screen.getByTestId('signup-first-name'), { target: { value: 'Jean' } });
    fireEvent.change(screen.getByTestId('signup-last-name'), { target: { value: 'Dupont' } });
    fireEvent.change(screen.getByTestId('signup-admin-email'), { target: { value: 'jean@atlas.ma' } });
    // Trop court pour le Maroc.
    fireEvent.change(screen.getByTestId('signup-admin-phone'), { target: { value: '61' } });
    fireEvent.change(screen.getByTestId('signup-city'), { target: { value: 'Casablanca' } });
    fireEvent.click(screen.getByTestId('signup-step1-next'));
    expect(screen.getByTestId('signup-step1-error')).toHaveTextContent(/telephone invalide/i);
    expect(onNext).not.toHaveBeenCalled();
  });

  it('resumes an existing E.164 phone (parses country + national number)', () => {
    // Un draft repris avec un numero francais doit afficher +33 dans le selecteur.
    const initial: SignupProfileData = { ...EMPTY, phone: '+33612345678' };
    renderWithRouter(<SignupStep1Cabinet initial={initial} onNext={vi.fn()} />);
    expect(screen.getByTestId('signup-admin-phone-country')).toHaveTextContent('+33');
  });

  it('offers Moroccan cities as datalist suggestions while allowing free text', () => {
    renderWithRouter(<SignupStep1Cabinet initial={EMPTY} onNext={vi.fn()} />);
    const city = screen.getByTestId('signup-city') as HTMLInputElement;
    // Le champ est couple a une datalist (suggestions, saisie libre conservee).
    expect(city).toHaveAttribute('list');
    const listId = city.getAttribute('list')!;
    const datalist = document.getElementById(listId);
    expect(datalist).toBeTruthy();
    expect(datalist!.querySelectorAll('option').length).toBeGreaterThan(50);
  });
});
