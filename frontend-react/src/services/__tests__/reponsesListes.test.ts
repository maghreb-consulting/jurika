import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L6b : une reponse qui n'est pas une liste (erreur du serveur rendue en 200, corps vide,
 * objet d'erreur) ne doit jamais atteindre un `.map` au rendu : le parcours de modification
 * plantait en entier (ErrorBoundary). Elle devient une erreur nommee, traitee par l'appelant.
 */
const get = vi.fn();
vi.mock('../../lib/api', async (orig) => ({
  ...(await orig<typeof import('../../lib/api')>()),
  api: { get: (...a: unknown[]) => get(...a) },
}));

const { listTemplatesForWorkflow } = await import('../workflowDocumentService');
const { workflowService } = await import('../workflow.service');

describe('reponses attendues en liste', () => {
  beforeEach(() => get.mockReset());

  it('modeles d un parcours : une reponse qui n est pas une liste est une erreur nommee', async () => {
    get.mockResolvedValue({ data: { code: 'NON_IMPLEMENTE' } });
    await expect(listTemplatesForWorkflow('SEANCE_AG')).rejects.toThrow(/Les modèles du parcours SEANCE_AG n’ont pas pu être lus/);
    get.mockResolvedValue({ data: [{ code: 'PV_AG' }] });
    await expect(listTemplatesForWorkflow('SEANCE_AG')).resolves.toEqual([{ code: 'PV_AG' }]);
  });

  it('donnees attendues : une reponse qui n est pas une liste est une erreur nommee', async () => {
    get.mockResolvedValue({ data: '' });
    await expect(workflowService.donneesAttendues('t-1')).rejects.toThrow(/Les données attendues du ticket n’ont pas pu être lues/);
    get.mockResolvedValue({ data: [] });
    await expect(workflowService.donneesAttendues('t-1')).resolves.toEqual([]);
  });
});
