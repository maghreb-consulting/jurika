import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DashboardRouter } from '../DashboardRouter';
import type { Role } from '../../../types/auth';

// Sprint 14 bis / D4 — DashboardRouter
// 2 tests : rend SupervisorDashboard pour SUPERVISEUR + EmployeeDashboard pour EMPLOYE,
// rend null si pas de user.

const userMock: { current: { role: Role; email: string } | null } = { current: null };

vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => userMock.current,
}));

// Stub des dashboards pour eviter de tester leur dependance reseau
vi.mock('../SupervisorDashboard', () => ({
  SupervisorDashboard: () => <div data-testid="supervisor-dashboard">SUP</div>,
}));
vi.mock('../EmployeeDashboard', () => ({
  EmployeeDashboard: () => <div data-testid="employee-dashboard">EMP</div>,
}));
vi.mock('../SuperAdminDashboard', () => ({
  SuperAdminDashboard: () => <div data-testid="superadmin-dashboard">SA</div>,
}));
vi.mock('../ClientDashboard', () => ({
  ClientDashboard: () => <div data-testid="client-dashboard">CL</div>,
}));

describe('DashboardRouter', () => {
  it('rend SupervisorDashboard pour role SUPERVISEUR', () => {
    userMock.current = { role: 'SUPERVISEUR', email: 'sup@jurika.ma' };
    render(<DashboardRouter />);
    expect(screen.getByTestId('supervisor-dashboard')).toBeInTheDocument();
  });

  it('rend EmployeeDashboard pour role EMPLOYE', () => {
    userMock.current = { role: 'EMPLOYE', email: 'emp@jurika.ma' };
    render(<DashboardRouter />);
    expect(screen.getByTestId('employee-dashboard')).toBeInTheDocument();
  });
});
