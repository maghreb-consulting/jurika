import { api } from '../lib/api';
import type {
  ClientDashboardDto,
  EmployeDashboardDto,
  SuperAdminDashboardDto,
  SuperviseurDashboardDto,
} from '../types/dashboard';

/** Sprint 10 -- 4 endpoints dashboards + invalidate + export CSV. */
export const dashboardService = {
  async getSuperAdmin(): Promise<SuperAdminDashboardDto> {
    const { data } = await api.get<SuperAdminDashboardDto>('/dashboards/super-admin');
    return data;
  },

  async getSuperviseur(): Promise<SuperviseurDashboardDto> {
    const { data } = await api.get<SuperviseurDashboardDto>('/dashboards/superviseur');
    return data;
  },

  async getEmploye(): Promise<EmployeDashboardDto> {
    const { data } = await api.get<EmployeDashboardDto>('/dashboards/employe');
    return data;
  },

  async getClient(): Promise<ClientDashboardDto> {
    const { data } = await api.get<ClientDashboardDto>('/dashboards/client');
    return data;
  },

  async invalidate(workspaceId?: string): Promise<number> {
    const { data } = await api.post<number>(
      '/dashboards/invalidate',
      null,
      { params: workspaceId ? { workspaceId } : undefined },
    );
    return data;
  },

  async exportCsv(metric: 'evolution-tickets-30j' | 'signups-30j'): Promise<Blob> {
    const response = await api.get('/dashboards/export/csv', {
      params: { metric },
      responseType: 'blob',
    });
    return response.data as Blob;
  },
};
