import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) a l'ecran :
 *  - pendant l'essai, aucun fichier n'est demande au serveur : l'apercu est un affichage en pages
 *    (images produites par le serveur), sans bouton de telechargement ni d'impression ;
 *  - la generation passe par l'apercu et la validation par le depot fait par le serveur ;
 *  - hors essai, rien ne change ;
 *  - etat de l'essai illisible pour un compte du cabinet : traite comme un essai.
 */

const get = vi.fn();
const post = vi.fn();
vi.mock('../../../lib/api', () => ({
  api: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) },
  extractError: (e: unknown) => ({ message: e instanceof Error ? e.message : 'Erreur' }),
  tokenStorage: { setTokens: vi.fn(), clear: vi.fn(), getAccess: vi.fn(), getRefresh: vi.fn() },
}));

import { useAuthStore } from '../../../store/authStore';
import { useEssaiStore, useEnEssai } from '../../../store/essaiStore';
import { PdfPreviewModal } from '../../ui/PdfPreviewModal';
import { useDocumentBlocks, freshDocState, type DocState } from '../../workflow/documentBlocks';
import { WorkflowDocumentBlock } from '../../workflow/WorkflowDocumentBlock';
import type { ApercuPages } from '../../../types/apercu';

const PAGES: ApercuPages = {
  filigrane: true,
  mention: 'VERSION D’ÉVALUATION — SANS VALEUR JURIDIQUE',
  nombrePages: 2,
  tronque: false,
  pages: [
    { numero: 1, largeur: 909, hauteur: 1286, image: 'data:image/png;base64,AAAA' },
    { numero: 2, largeur: 909, hauteur: 1286, image: 'data:image/png;base64,BBBB' },
  ],
};

function connecter(role: 'SUPERVISEUR' | 'EMPLOYE' | 'CLIENT' | 'SUPER_ADMIN') {
  useAuthStore.setState({
    user: { userId: 'u-1', workspaceId: 'ws-1', email: 'a@b.ma', role } as never,
    isAuthenticated: true,
    isHydrating: false,
  });
}

function etatEssai(enEssai: boolean) {
  get.mockImplementation(async (url: string) => {
    if (url === '/workspace/essai') return { data: { enEssai } };
    if (url.endsWith('/apercu')) return { data: PAGES };
    throw new Error(`appel inattendu ${url}`);
  });
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  useEssaiStore.getState().reinitialiser();
});

afterEach(() => {
  useAuthStore.setState({ user: null, isAuthenticated: false });
});

describe('Etat de l essai', () => {
  it('etat illisible pour un compte du cabinet : traite comme un essai', async () => {
    connecter('EMPLOYE');
    get.mockRejectedValue(new Error('auth injoignable'));
    const { result } = renderHook(() => useEnEssai());
    await waitFor(() => expect(result.current).toBe(true));
  });

  it('super-admin : jamais en essai, aucun appel', async () => {
    connecter('SUPER_ADMIN');
    const { result } = renderHook(() => useEnEssai());
    await waitFor(() => expect(result.current).toBe(false));
    expect(get).not.toHaveBeenCalled();
  });
});

describe('Apercu de la Data Room', () => {
  it('essai : pages filigranees, aucun fichier demande, ni telechargement ni impression', async () => {
    connecter('EMPLOYE');
    etatEssai(true);
    render(
      <MemoryRouter>
        <PdfPreviewModal open documentId="doc-1" filename="Statuts.docx" canDownload canPrint onClose={() => {}} onDownload={() => {}} />
      </MemoryRouter>,
    );
    expect(await screen.findByAltText('Statuts.docx — page 1 sur 2')).toHaveAttribute('src', 'data:image/png;base64,AAAA');
    expect(screen.getByRole('note', { name: 'Essai gratuit : documents en aperçu seulement' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Télécharger/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Imprimer/ })).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/dataroom/documents/doc-1/apercu');
    // Aucun appel en Blob (fichier).
    expect(get.mock.calls.some((c) => (c[1] as { responseType?: string } | undefined)?.responseType === 'blob')).toBe(false);
  });

  it('hors essai : apercu du fichier inchange', async () => {
    connecter('EMPLOYE');
    get.mockImplementation(async (url: string) => {
      if (url === '/workspace/essai') return { data: { enEssai: false } };
      return { data: new Blob(['%PDF']), headers: { 'content-type': 'application/pdf' } };
    });
    const createObjectURL = vi.fn(() => 'blob:x');
    Object.assign(window.URL, { createObjectURL, revokeObjectURL: vi.fn() });
    render(
      <MemoryRouter>
        <PdfPreviewModal open documentId="doc-1" filename="Statuts.pdf" canDownload onClose={() => {}} onDownload={() => {}} />
      </MemoryRouter>,
    );
    expect(await screen.findByTitle('Aperçu PDF : Statuts.pdf')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Télécharger/ })).toBeInTheDocument();
  });
});

describe('Generation des actes', () => {
  const tpl = { code: 'PV_X', documentKind: 'Procès-verbal', file: 'x', origin: 'directeur', deprecated: false, placeholderStyle: 'x' };

  it('essai : apercu en pages puis depot par le serveur, jamais de fichier recu', async () => {
    connecter('EMPLOYE');
    etatEssai(true);
    post.mockImplementation(async (url: string) => {
      if (url.endsWith('/apercu')) return { data: { apercu: PAGES, nomFichier: 'PV.docx', donneesAObtenir: [] } };
      if (url.endsWith('/depot')) return { data: { document: { id: 'd-1' }, nomFichier: 'PV.docx', filigrane: true, donneesAObtenir: [] } };
      throw new Error(`appel inattendu ${url}`);
    });
    let docs: Record<string, DocState> = {};
    const setDocs = (f: Record<string, DocState> | ((p: Record<string, DocState>) => Record<string, DocState>)) => {
      docs = typeof f === 'function' ? f(docs) : f;
    };
    const { result } = renderHook(() =>
      useDocumentBlocks({
        workflowCode: 'DISSOLUTION', dossierId: 'dos-1', ticketId: null,
        mapType: () => 'PV_AGE' as never, docs, setDocs: setDocs as never, buildPayload: () => ({ a: 1 }),
      }),
    );
    await act(async () => { await result.current.generateOne(tpl); });
    expect(docs.PV_X.apercu).toEqual(PAGES);
    expect(docs.PV_X.blob).toBeUndefined();
    expect(post.mock.calls[0][0]).toBe('/ai/workflows/DISSOLUTION/documents/PV_X/apercu');
    expect(post.mock.calls.some((c) => (c[2] as { responseType?: string } | undefined)?.responseType === 'blob')).toBe(false);

    const { result: r2 } = renderHook(() =>
      useDocumentBlocks({
        workflowCode: 'DISSOLUTION', dossierId: 'dos-1', ticketId: null,
        mapType: () => 'PV_AGE' as never, docs, setDocs: setDocs as never, buildPayload: () => ({ a: 1 }),
      }),
    );
    await act(async () => { await r2.current.validateOne(tpl); });
    const depot = post.mock.calls.find((c) => String(c[0]).endsWith('/depot'));
    expect(depot?.[1]).toMatchObject({ depot: { dossierId: 'dos-1', documentType: 'PV_AGE', mode: 'NOUVEAU' } });
    expect(docs.PV_X.depositedToDataroom).toBe(true);
  });

  it('essai : le bloc affiche les pages et aucun bouton de telechargement ni d edition', async () => {
    connecter('EMPLOYE');
    etatEssai(true);
    const state: DocState = { ...freshDocState(), generated: true, previewOpen: true, apercu: PAGES, filename: 'PV.docx' };
    render(
      <MemoryRouter>
        <WorkflowDocumentBlock tpl={tpl} state={state} onGenerate={() => {}} onDownload={() => {}}
          onRegenerate={() => {}} onValidate={() => {}} onTogglePreview={() => {}} onEdited={() => {}} />
      </MemoryRouter>,
    );
    expect(await screen.findByAltText('Procès-verbal — page 2 sur 2')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('button', { name: /Télécharger/ })).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /Éditer/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Valider/ })).toBeInTheDocument();
  });

  it('hors essai : boutons inchanges', async () => {
    connecter('EMPLOYE');
    etatEssai(false);
    const state: DocState = { ...freshDocState(), generated: true, blob: new Blob(['x']), filename: 'PV.docx' };
    render(
      <MemoryRouter>
        <WorkflowDocumentBlock tpl={tpl} state={state} onGenerate={() => {}} onDownload={() => {}}
          onRegenerate={() => {}} onValidate={() => {}} onTogglePreview={() => {}} />
      </MemoryRouter>,
    );
    expect(await screen.findByRole('button', { name: /Télécharger \(Word\)/ })).toBeInTheDocument();
  });
});
