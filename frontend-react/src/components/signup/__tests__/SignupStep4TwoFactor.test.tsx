/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { SignupStep4TwoFactor } from '../SignupStep4TwoFactor';

describe('<SignupStep4TwoFactor>', () => {
  beforeEach(() => {
    try { localStorage.removeItem('jurika.signup.preferred2fa'); } catch { /* noop */ }
  });

  it('renders both options, with SMS shown as out-of-service and disabled', () => {
    render(
      <BrowserRouter>
        <SignupStep4TwoFactor initial="TOTP" onBack={vi.fn()} onNext={vi.fn()} />
      </BrowserRouter>
    );
    const sms = screen.getByTestId('signup-2fa-option-sms');
    const totp = screen.getByTestId('signup-2fa-option-totp');
    expect(sms).toBeInTheDocument();
    expect(totp).toBeInTheDocument();
    // SMS_2FA_ENABLED=false par defaut : SMS « Hors service », non selectionnable.
    expect(sms).toHaveTextContent(/Hors service/i);
    expect(sms).toHaveAttribute('aria-disabled', 'true');
    expect(sms.querySelector('input[type="radio"]')).toBeDisabled();
  });

  it('ignores clicks on the disabled SMS card and submits TOTP', () => {
    const onNext = vi.fn();
    render(
      <BrowserRouter>
        <SignupStep4TwoFactor initial="TOTP" onBack={vi.fn()} onNext={onNext} />
      </BrowserRouter>
    );
    // Cliquer sur SMS (hors service) ne change rien : TOTP reste selectionne.
    fireEvent.click(screen.getByTestId('signup-2fa-option-sms'));
    fireEvent.click(screen.getByTestId('signup-step4-next'));
    expect(onNext).toHaveBeenCalledWith('TOTP');
    expect(localStorage.getItem('jurika.signup.preferred2fa')).toBe('TOTP');
  });

  it('forces TOTP even if a resumed draft preferred SMS', () => {
    const onNext = vi.fn();
    render(
      <BrowserRouter>
        <SignupStep4TwoFactor initial="SMS" onBack={vi.fn()} onNext={onNext} />
      </BrowserRouter>
    );
    fireEvent.click(screen.getByTestId('signup-step4-next'));
    expect(onNext).toHaveBeenCalledWith('TOTP');
  });
});
