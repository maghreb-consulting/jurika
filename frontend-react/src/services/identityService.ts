import { api } from '../lib/api';
import type { ExtractIdentityParams, ExtractedIdentity } from '../types/identity';

/**
 * Service d'orchestration extraction d'identité (CIN / CN).
 * <p>
 * Appelle l'endpoint {@code POST /api/v1/dataroom/identity/extract} (gateway →
 * dataroom-service). Le dataroom-service appelle lui-même kie-service en interne.
 * Le frontend ne tape JAMAIS directement sur kie-service.
 */
export const identityService = {
  async extract(params: ExtractIdentityParams): Promise<ExtractedIdentity> {
    const form = new FormData();
    form.append('recto', params.recto);
    if (params.verso && params.type !== 'CN') {
      form.append('verso', params.verso);
    }
    form.append('type', params.type);
    if (params.dossierId) {
      form.append('dossierId', params.dossierId);
    }
    if (params.archive !== undefined) {
      form.append('archive', String(params.archive));
    }
    const { data } = await api.post<ExtractedIdentity>(
      '/dataroom/identity/extract',
      form,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
      },
    );
    return data;
  },
};

export type IdentityService = typeof identityService;
