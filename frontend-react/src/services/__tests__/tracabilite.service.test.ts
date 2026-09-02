import { describe, it, expect, vi, beforeEach } from 'vitest';

const get = vi.fn();
vi.mock('../../lib/api', () => ({
  api: { get: (...args: unknown[]) => get(...args) },
}));

import { tracabiliteService } from '../tracabilite.service';

describe('tracabiliteService (E2)', () => {
  beforeEach(() => {
    get.mockReset();
    get.mockResolvedValue({ data: { items: [], total: 0, limit: 50, offset: 0 } });
  });

  it('search n\'envoie jamais le workspace et applique les filtres fournis', async () => {
    await tracabiliteService.search({ action: 'TICKET_ASSIGNED', entityType: 'ticket', limit: 20, offset: 40 });
    expect(get).toHaveBeenCalledWith('/tracabilite', {
      params: { action: 'TICKET_ASSIGNED', entityType: 'ticket', limit: 20, offset: 40 },
    });
    const [, opts] = get.mock.calls[0];
    expect(opts.params).not.toHaveProperty('workspaceId');
  });

  it('search applique des defauts de pagination', async () => {
    await tracabiliteService.search({});
    expect(get).toHaveBeenCalledWith('/tracabilite', { params: { limit: 50, offset: 0 } });
  });

  it('entityHistory cible /tracabilite/entite avec entityType + entityId', async () => {
    await tracabiliteService.entityHistory('dossier', 'd1');
    expect(get).toHaveBeenCalledWith('/tracabilite/entite', {
      params: { entityType: 'dossier', entityId: 'd1', limit: 50 },
    });
  });
});
