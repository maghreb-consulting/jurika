/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { SignupStep5Recap } from '../SignupStep5Recap';
import type { SignupProfileData } from '../SignupStep1Cabinet';

const profile: SignupProfileData = {
  firstName: 'Jean',
  lastName: 'Dupont',
  workspaceName: 'Cabinet ATLAS Consulting',
  email: 'jean@atlas.ma',
  phone: '+212612345678',
  city: 'Casablanca',
  ice: '123456789012345',
};

describe('<SignupStep5Recap>', () => {
  it('shows recap with titulaire + structure + plan + 2FA preference', () => {
    render(
      <BrowserRouter>
        <SignupStep5Recap profile={profile} twoFactor="TOTP" selectedPlan="business"
          onBack={vi.fn()} onSubmit={vi.fn().mockResolvedValue(undefined)} />
      </BrowserRouter>
    );
    expect(screen.getByText('Cabinet ATLAS Consulting')).toBeInTheDocument();
    expect(screen.getByText('Jean Dupont')).toBeInTheDocument();
    expect(screen.getByText(/Business/i)).toBeInTheDocument();
    expect(screen.getByText(/TOTP/i)).toBeInTheDocument();
    expect(screen.getByTestId('signup-cgu-checkbox')).toBeInTheDocument();
    expect(screen.getByTestId('signup-step5-submit')).toBeInTheDocument();
  });

  it('shows "a completer" when ICE is empty', () => {
    render(
      <BrowserRouter>
        <SignupStep5Recap profile={{ ...profile, ice: '' }} twoFactor="TOTP" selectedPlan="essentiel"
          onBack={vi.fn()} onSubmit={vi.fn().mockResolvedValue(undefined)} />
      </BrowserRouter>
    );
    expect(screen.getByText(/completer plus tard/i)).toBeInTheDocument();
  });

  it('blocks submit when CGU not checked', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <BrowserRouter>
        <SignupStep5Recap profile={profile} twoFactor="SMS" selectedPlan="essentiel"
          onBack={vi.fn()} onSubmit={onSubmit} />
      </BrowserRouter>
    );
    fireEvent.click(screen.getByTestId('signup-step5-submit'));
    await waitFor(() => {
      expect(screen.getByTestId('signup-step5-error')).toHaveTextContent(/CGU/i);
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('calls onSubmit with true when CGU accepted', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <BrowserRouter>
        <SignupStep5Recap profile={profile} twoFactor="SMS" selectedPlan="essentiel"
          onBack={vi.fn()} onSubmit={onSubmit} />
      </BrowserRouter>
    );
    fireEvent.click(screen.getByTestId('signup-cgu-checkbox'));
    fireEvent.click(screen.getByTestId('signup-step5-submit'));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(true));
  });
});
