import { api } from '../lib/api';
import type { DossierTransfer } from '../types/transfer';

/**
 * Transfert de dossier (V9). Appelle ticket-service VIA LA GATEWAY
 * (/api/v1/...), contrairement au chat qui tape le realtime-service en direct.
 */
export const transferService = {
  /** VOIE A — le responsable cree une demande EN_ATTENTE vers un collegue. */
  async requestTransfer(dossierId: string, toUserId: string, motif?: string): Promise<DossierTransfer> {
    const { data } = await api.post<DossierTransfer>(
      `/dossiers/${dossierId}/transfer-requests`,
      { toUserId, motif: motif || null },
    );
    return data;
  },

  async accept(id: string): Promise<DossierTransfer> {
    const { data } = await api.post<DossierTransfer>(`/dossier-transfer-requests/${id}/accept`);
    return data;
  },

  async reject(id: string): Promise<DossierTransfer> {
    const { data } = await api.post<DossierTransfer>(`/dossier-transfer-requests/${id}/reject`);
    return data;
  },

  async cancel(id: string): Promise<DossierTransfer> {
    const { data } = await api.post<DossierTransfer>(`/dossier-transfer-requests/${id}/cancel`);
    return data;
  },

  async listInbox(): Promise<DossierTransfer[]> {
    const { data } = await api.get<DossierTransfer[]>('/dossier-transfer-requests', {
      params: { box: 'inbox' },
    });
    return data;
  },

  async listOutbox(): Promise<DossierTransfer[]> {
    const { data } = await api.get<DossierTransfer[]>('/dossier-transfer-requests', {
      params: { box: 'outbox' },
    });
    return data;
  },
};
