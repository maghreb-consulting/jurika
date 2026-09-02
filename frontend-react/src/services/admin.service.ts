import { api } from '../lib/api';
import type {
  AdminUsersPage,
  AdminWorkspaceRow,
  WorkspaceDetail,
  WorkspaceStatusAction,
} from '../types/admin';

/**
 * Espace SUPER_ADMIN — gestion cross-workspace de la plateforme.
 * Tous les endpoints sont @PreAuthorize("hasRole('SUPER_ADMIN')") cote back.
 */
export const adminService = {
  // ----- Workspaces -----
  async listWorkspaces(): Promise<AdminWorkspaceRow[]> {
    const { data } = await api.get<AdminWorkspaceRow[]>('/admin/workspaces');
    return data;
  },

  /** Vue détaillée d'un workspace (identité + compteurs + graphes). */
  async getWorkspaceDetail(workspaceId: string): Promise<WorkspaceDetail> {
    const { data } = await api.get<WorkspaceDetail>(`/admin/workspaces/${workspaceId}`);
    return data;
  },

  /** activate / suspend / deactivate un workspace. Renvoie le nouveau statut. */
  async setWorkspaceStatus(
    workspaceId: string,
    action: WorkspaceStatusAction,
  ): Promise<{ workspaceId: string; status: string }> {
    const { data } = await api.post<{ workspaceId: string; status: string }>(
      `/admin/workspaces/${workspaceId}/${action}`,
    );
    return data;
  },

  // ----- Utilisateurs -----
  async listUsers(params: {
    search?: string;
    role?: string;
    workspaceId?: string;
    offset?: number;
    limit?: number;
  } = {}): Promise<AdminUsersPage> {
    const { data } = await api.get<AdminUsersPage>('/admin/users', {
      params: {
        search: params.search || undefined,
        role: params.role || undefined,
        workspaceId: params.workspaceId || undefined,
        offset: params.offset ?? 0,
        limit: params.limit ?? 25,
      },
    });
    return data;
  },

  async suspendUser(userId: string): Promise<void> {
    await api.post(`/admin/users/${userId}/suspend`);
  },

  async reactivateUser(userId: string): Promise<void> {
    await api.post(`/admin/users/${userId}/reactivate`);
  },

  /**
   * Reset MDP = regeneration d'un mot de passe temporaire + email
   * (reutilise l'endpoint resend-welcome existant).
   */
  async resetUserPassword(
    workspaceId: string,
    userId: string,
  ): Promise<{ emailDelivered: boolean; message: string }> {
    const { data } = await api.post<{ emailDelivered: boolean; message: string }>(
      `/admin/workspaces/${workspaceId}/users/${userId}/resend-welcome`,
    );
    return data;
  },

  // ----- Plateforme -----
  /** Liste (dedupliquee) des origines CORS autorisees, pollee par la gateway. */
  async listCorsOrigins(): Promise<string[]> {
    const { data } = await api.get<string[]>('/admin/cors-origins');
    return data;
  },
};
