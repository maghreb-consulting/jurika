import { describe, it, expect, vi, beforeEach } from 'vitest';

const post = vi.fn();
const get = vi.fn();
vi.mock('../../lib/api', () => ({
  api: {
    post: (...args: unknown[]) => post(...args),
    get: (...args: unknown[]) => get(...args),
  },
}));

import { transferService } from '../transfer.service';

describe('transferService (V9)', () => {
  beforeEach(() => {
    post.mockReset();
    get.mockReset();
    post.mockResolvedValue({ data: { id: 'r1' } });
    get.mockResolvedValue({ data: [] });
  });

  it('requestTransfer poste vers /dossiers/{id}/transfer-requests', async () => {
    await transferService.requestTransfer('d1', 'u2', 'charge');
    expect(post).toHaveBeenCalledWith('/dossiers/d1/transfer-requests', {
      toUserId: 'u2',
      motif: 'charge',
    });
  });

  it('accept / reject / cancel ciblent le bon endpoint', async () => {
    await transferService.accept('r1');
    expect(post).toHaveBeenCalledWith('/dossier-transfer-requests/r1/accept');
    await transferService.reject('r1');
    expect(post).toHaveBeenCalledWith('/dossier-transfer-requests/r1/reject');
    await transferService.cancel('r1');
    expect(post).toHaveBeenCalledWith('/dossier-transfer-requests/r1/cancel');
  });

  it('listInbox / listOutbox passent le bon parametre box', async () => {
    await transferService.listInbox();
    expect(get).toHaveBeenCalledWith('/dossier-transfer-requests', { params: { box: 'inbox' } });
    await transferService.listOutbox();
    expect(get).toHaveBeenCalledWith('/dossier-transfer-requests', { params: { box: 'outbox' } });
  });
});
