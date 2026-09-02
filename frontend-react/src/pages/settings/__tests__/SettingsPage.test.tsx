/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Role } from '../../../types/auth';

/**
 * Lot M (2026-06-30) — Non-regression : l'onglet Facturation de /settings est
 * masque au CLIENT (experience reduite). Les autres roles le conservent.
 */

const userRef: { current: { role: Role; email: string } | null } = { current: null };

vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => userRef.current,
}));

// Stubs des pages/onglets enfants (appels reseau au mount) — hors-scope.
vi.mock('../../account/ProfilePage', () => ({ ProfilePage: () => <div>profil</div> }));
vi.mock('../../account/ProfileSecurityPage', () => ({ ProfileSecurityPage: () => <div>securite</div> }));
vi.mock('../../team/TeamPage', () => ({ TeamPage: () => <div>equipe</div> }));
vi.mock('../../billing/BillingPage', () => ({ BillingPage: () => <div>facturation</div> }));
vi.mock('../NotificationPreferencesTab', () => ({ NotificationPreferencesTab: () => <div>notifs</div> }));
vi.mock('../PreferencesTab', () => ({ PreferencesTab: () => <div>preferences</div> }));

import { SettingsPage } from '../SettingsPage';

function renderAt(role: Role, path = '/settings/profile') {
  userRef.current = { role, email: `${role.toLowerCase()}@acme.ma` };
  // SettingsPage est monte sous /settings/* (comme App.tsx) pour que ses routes
  // imbriquees relatives ("profile", "billing", ...) se resolvent.
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/settings/*" element={<SettingsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Lot M — onglet Facturation /settings', () => {
  it('CLIENT : onglet Facturation absent', () => {
    renderAt('CLIENT');
    expect(screen.queryByTestId('settings-tab-billing')).not.toBeInTheDocument();
    // Garde Profil / Securite / Notifications / Preferences.
    expect(screen.getByTestId('settings-tab-profile')).toBeInTheDocument();
  });

  it('CLIENT : deep-link /settings/billing retombe sur Profil (route gardee)', () => {
    renderAt('CLIENT', '/settings/billing');
    expect(screen.queryByText('facturation')).not.toBeInTheDocument();
    expect(screen.getByText('profil')).toBeInTheDocument();
  });

  it('EMPLOYE : onglet Facturation present (non-regression)', () => {
    renderAt('EMPLOYE');
    expect(screen.getByTestId('settings-tab-billing')).toBeInTheDocument();
  });

  it('SUPERVISEUR : onglet Facturation present (non-regression)', () => {
    renderAt('SUPERVISEUR');
    expect(screen.getByTestId('settings-tab-billing')).toBeInTheDocument();
  });
});
