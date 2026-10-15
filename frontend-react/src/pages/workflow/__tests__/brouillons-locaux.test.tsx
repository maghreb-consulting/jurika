import { act, fireEvent, render, screen } from '@testing-library/react';
import { useCallback, useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L7 (E12) -- decision de l'utilisateur du 2026-10-14 : "Si la session a expire,
 * garde un brouillon local et renvoie-le a la reconnexion. Aucune saisie ne doit se
 * perdre." Et l'etape 8 de la creation est enregistree automatiquement.
 */

const save = vi.fn();
const get = vi.fn();
vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    get: (...args: unknown[]) => get(...args),
    start: vi.fn(),
    save: (...args: unknown[]) => save(...args),
  },
}));
vi.mock('../../../services/ticket.service', () => ({
  ticketService: { get: vi.fn(async () => ({ id: 't-1', type: 'CREATION_SARL', titre: 'ALPHA' })) },
}));
const logoutServeur = vi.fn(async () => undefined);
vi.mock('../../../services/auth.service', () => ({
  authService: { logout: (...args: unknown[]) => logoutServeur(...(args as [])) },
}));
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig<typeof import('react-router-dom')>()),
  useParams: () => ({ ticketId: 't-1' }),
}));

const { WorkflowDataProvider, useWorkflow } = await import('../useWorkflow');
const { useStepAutosave } = await import('../useStepAutosave');
const { IndicateurEnregistrement } = await import('../../../components/workflow/IndicateurEnregistrement');
const { LECTURE_SAISIE_MS } = await import('../enregistrementAuto');
const { canonique, ecrireBrouillon, lireBrouillons, EVENEMENT_SESSION_OUVERTE } = await import('../brouillonsLocaux');
const { useRenvoiBrouillons } = await import('../useRenvoiBrouillons');
const { useDeconnexion } = await import('../../../components/layout/useDeconnexion');
const { Step8PiecesJointes } = await import('../steps/Step8PiecesJointes');
const { useAuthStore } = await import('../../../store/authStore');
const { tokenStorage } = await import('../../../lib/api');

const A = { userId: 'u-a', workspaceId: 'w-1' };
const B = { userId: 'u-b', workspaceId: 'w-1' };
const JETON = 'jeton-acces-secret';

function connecter(u: { userId: string; workspaceId: string }) {
  tokenStorage.setTokens({
    accessToken: JETON, refreshToken: 'rafraichissement-secret',
    accessExpiresAt: '', refreshExpiresAt: '', userId: u.userId, workspaceId: u.workspaceId,
  });
  useAuthStore.setState({
    user: { ...u, email: 'employe@cabinet.ma', role: 'EMPLOYE' },
    isAuthenticated: true,
    isHydrating: false,
  });
}

function progression(data: Record<string, unknown>) {
  return { id: 'wf-1', ticketId: 't-1', currentStep: 1, totalSteps: 9, statut: 'EN_COURS', data };
}

/** Erreur axios reduite a ce que lisent `natureEchec` et `extractError`. */
function erreurHttp(status?: number, data: unknown = {}) {
  return { isAxiosError: true, message: 'echec', response: status ? { status, data } : undefined };
}

function Etape() {
  const { stepData, registerDirty } = useWorkflow();
  const [nom, setNom] = useState(String(stepData.step1?.nom ?? ''));
  useStepAutosave(1, () => ({ nom }), registerDirty);
  return (
    <div>
      <IndicateurEnregistrement />
      <label htmlFor="nom">Nom</label>
      <input id="nom" value={nom} onChange={(e) => setNom(e.target.value)} />
    </div>
  );
}

function Parcours({ ouvert, children }: { ouvert: boolean; children?: React.ReactNode }) {
  return ouvert ? (
    <WorkflowDataProvider fallback={<p>Chargement</p>}>{children ?? <Etape />}</WorkflowDataProvider>
  ) : (
    <p>Liste des tickets</p>
  );
}

async function laisserFiler(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

async function taper(valeur: string) {
  fireEvent.change(screen.getByLabelText('Nom'), { target: { value: valeur } });
  await laisserFiler(LECTURE_SAISIE_MS * 2);
}

function brouillonsBruts(): string[] {
  const out: string[] = [];
  for (let i = 0; i < localStorage.length; i += 1) {
    const k = localStorage.key(i);
    if (k?.startsWith('jurika_brouillon:')) out.push(`${k}=${localStorage.getItem(k)}`);
  }
  return out;
}

describe('brouillon local des saisies (session expiree, hors ligne, fermeture)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    save.mockReset();
    get.mockReset();
    logoutServeur.mockClear();
    get.mockImplementation(async () => progression({ step1: { nom: 'ALPHA' } }));
    save.mockImplementation(async () => progression({}));
    connecter(A);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('401 pendant la frappe : brouillon local ecrit, message clair, renvoi a la reconnexion puis effacement', async () => {
    save.mockRejectedValue(erreurHttp(401));
    render(<Parcours ouvert />);
    await laisserFiler(0);
    await taper('ALPHA SARL');

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Session expirée : votre saisie est gardée dans ce navigateur, reconnectez-vous dans les 7 jours.',
    );
    const gardes = lireBrouillons(A, 't-1');
    expect(gardes).toHaveLength(1);
    expect(gardes[0]).toMatchObject({ cle: 'step1', step: 1, contenu: { nom: 'ALPHA SARL' }, base: canonique({ nom: 'ALPHA' }) });
    expect(gardes[0].version).toBeGreaterThan(0);
    expect(Date.parse(gardes[0].ecritA)).not.toBeNaN();
    // Aucun jeton dans le brouillon.
    expect(brouillonsBruts().join('\n')).not.toMatch(/secret/);

    // Reconnexion (nouveau jeton, meme utilisateur) : la saisie repart et le brouillon est efface.
    save.mockReset();
    save.mockImplementation(async () => progression({ step1: { nom: 'ALPHA SARL' } }));
    await act(async () => {
      useAuthStore.getState().setSession(
        { accessToken: 'nouveau', refreshToken: 'r2', accessExpiresAt: '', refreshExpiresAt: '', ...A },
        'EMPLOYE', 'employe@cabinet.ma',
      );
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'ALPHA SARL' } });
    expect(lireBrouillons(A, 't-1')).toHaveLength(0);
    expect(screen.getByTestId('indicateur-enregistrement')).toHaveTextContent(/Enregistré à/);
  });

  it('apres expiration et rechargement, le brouillon est renvoye AVANT que les champs ne montent', async () => {
    save.mockRejectedValue(erreurHttp(401));
    const vue = render(<Parcours ouvert />);
    await laisserFiler(0);
    await taper('ALPHA HOLDING');
    vue.rerender(<Parcours ouvert={false} />);
    await laisserFiler(0);
    expect(lireBrouillons(A, 't-1')).toHaveLength(1);

    // Nouvelle session ; le serveur a toujours l'ancienne version.
    save.mockReset();
    save.mockImplementation(async () => progression({ step1: { nom: 'ALPHA HOLDING' } }));
    vue.rerender(<Parcours ouvert />);
    await laisserFiler(0);
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'ALPHA HOLDING' } });
    expect(screen.getByLabelText('Nom')).toHaveValue('ALPHA HOLDING');
    expect(lireBrouillons(A, 't-1')).toHaveLength(0);
  });

  it('hors ligne : brouillon garde, message clair, renvoi au retour du reseau', async () => {
    save.mockRejectedValue(erreurHttp(undefined));
    render(<Parcours ouvert />);
    await laisserFiler(0);
    await taper('BETA');
    expect(screen.getByRole('alert')).toHaveTextContent('Hors ligne : votre saisie est gardée dans ce navigateur.');
    expect(lireBrouillons(A, 't-1')[0]?.contenu).toEqual({ nom: 'BETA' });

    save.mockReset();
    save.mockImplementation(async () => progression({ step1: { nom: 'BETA' } }));
    await act(async () => {
      window.dispatchEvent(new Event('online'));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'BETA' } });
    expect(lireBrouillons(A, 't-1')).toHaveLength(0);
  });

  it('fermeture de l’onglet : la saisie est ecrite localement avant l’envoi fiable, effacee s’il aboutit', async () => {
    const envoi = vi.fn(async () => { throw new TypeError('Failed to fetch'); });
    vi.stubGlobal('fetch', envoi);
    render(<Parcours ouvert />);
    await laisserFiler(0);
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'GAMMA' } });
    await act(async () => {
      window.dispatchEvent(new Event('pagehide'));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(envoi).toHaveBeenCalledTimes(1);
    expect(lireBrouillons(A, 't-1')[0]?.contenu).toEqual({ nom: 'GAMMA' });

    const reussi = vi.fn(async () => ({ ok: true, status: 200, json: async () => progression({ step1: { nom: 'GAMMA SARL' } }) }));
    vi.stubGlobal('fetch', reussi);
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'GAMMA SARL' } });
    await act(async () => {
      window.dispatchEvent(new Event('pagehide'));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(reussi).toHaveBeenCalledTimes(1);
    expect(lireBrouillons(A, 't-1')).toHaveLength(0);
  });

  it('un brouillon d’un autre utilisateur n’est jamais envoye ni affiche', async () => {
    ecrireBrouillon(B, 't-1', 'step1', 1, { nom: 'SAISIE DE B' }, canonique({ nom: 'ALPHA' }));
    render(<Parcours ouvert />);
    await laisserFiler(0);
    expect(save).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Nom')).toHaveValue('ALPHA');
    expect(screen.queryByText(/Une saisie gardée dans ce navigateur/)).not.toBeInTheDocument();
    expect(lireBrouillons(A)).toHaveLength(0);
    expect(lireBrouillons(B, 't-1')).toHaveLength(1);
  });

  it('conflit : rien n’est ecrase ; l’employe garde sa saisie locale', async () => {
    // Saisie faite sur la version ALPHA ; le serveur porte depuis ALPHA HOLDING.
    ecrireBrouillon(A, 't-1', 'step1', 1, { nom: 'ALPHA ET FILS' }, canonique({ nom: 'ALPHA' }));
    get.mockImplementation(async () => progression({ step1: { nom: 'ALPHA HOLDING' } }));
    render(<Parcours ouvert />);
    await laisserFiler(0);
    expect(save).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Nom')).toHaveValue('ALPHA HOLDING');
    expect(screen.getByRole('alert', { name: 'Saisie gardée dans ce navigateur' })).toHaveTextContent(
      'L’étape a été modifiée depuis. Quelle version voulez-vous garder ?',
    );

    save.mockImplementation(async () => progression({ step1: { nom: 'ALPHA ET FILS' } }));
    fireEvent.click(screen.getByRole('button', { name: 'Garder ma saisie de ce navigateur' }));
    await laisserFiler(0);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'ALPHA ET FILS' } });
    expect(screen.getByLabelText('Nom')).toHaveValue('ALPHA ET FILS');
    expect(screen.queryByRole('button', { name: 'Garder ma saisie de ce navigateur' })).not.toBeInTheDocument();
    expect(lireBrouillons(A, 't-1')).toHaveLength(0);
  });

  it('conflit : garder la version enregistree efface la saisie locale sans rien envoyer', async () => {
    ecrireBrouillon(A, 't-1', 'step1', 1, { nom: 'ALPHA ET FILS' }, canonique({ nom: 'ALPHA' }));
    get.mockImplementation(async () => progression({ step1: { nom: 'ALPHA HOLDING' } }));
    render(<Parcours ouvert />);
    await laisserFiler(0);
    fireEvent.click(screen.getByRole('button', { name: 'Garder la version enregistrée' }));
    await laisserFiler(0);
    expect(save).not.toHaveBeenCalled();
    expect(lireBrouillons(A, 't-1')).toHaveLength(0);
    expect(screen.getByLabelText('Nom')).toHaveValue('ALPHA HOLDING');
  });

  it('reconnexion hors du parcours : les brouillons de l’utilisateur partent, pas ceux d’un autre', async () => {
    ecrireBrouillon(A, 't-1', 'step1', 1, { nom: 'DELTA' }, canonique({ nom: 'ALPHA' }));
    ecrireBrouillon(B, 't-1', 'step1', 1, { nom: 'SAISIE DE B' }, canonique({ nom: 'ALPHA' }));
    function Application() {
      useRenvoiBrouillons();
      return <p>Tableau de bord</p>;
    }
    useAuthStore.setState({ user: null, isAuthenticated: false, isHydrating: false });
    render(<Application />);
    await laisserFiler(0);
    expect(save).not.toHaveBeenCalled();
    await act(async () => {
      useAuthStore.getState().setSession(
        { accessToken: 'nouveau', refreshToken: 'r2', accessExpiresAt: '', refreshExpiresAt: '', ...A },
        'EMPLOYE', 'employe@cabinet.ma',
      );
      window.dispatchEvent(new Event(EVENEMENT_SESSION_OUVERTE));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'DELTA' } });
    expect(lireBrouillons(A)).toHaveLength(0);
    expect(lireBrouillons(B)).toHaveLength(1);
  });

  it('un brouillon de plus de 7 jours est efface sans etre envoye, meme celui d’un autre utilisateur', async () => {
    const maintenant = new Date('2026-10-20T10:00:00Z');
    vi.setSystemTime(new Date(maintenant.getTime() - 8 * 24 * 3600 * 1000));
    ecrireBrouillon(A, 't-ancien', 'step1', 1, { nom: 'PERIME' }, canonique({ nom: 'ALPHA' }));
    ecrireBrouillon(B, 't-1', 'step1', 1, { nom: 'PERIME DE B' }, canonique({ nom: 'ALPHA' }));
    vi.setSystemTime(new Date(maintenant.getTime() - 6 * 24 * 3600 * 1000));
    ecrireBrouillon(A, 't-1', 'step1', 1, { nom: 'RECENT' }, canonique({ nom: 'ALPHA' }));
    vi.setSystemTime(maintenant);

    expect(lireBrouillons(A).map((b) => b.ticketId)).toEqual(['t-1']);
    expect(brouillonsBruts().some((k) => k.includes('PERIME'))).toBe(false);

    function Application() {
      useRenvoiBrouillons();
      return <p>Tableau de bord</p>;
    }
    render(<Application />);
    await laisserFiler(0);
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'RECENT' } });
    expect(brouillonsBruts()).toEqual([]);
  });
});

describe('deconnexion volontaire et brouillons locaux', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    save.mockReset();
    get.mockReset();
    logoutServeur.mockClear();
    get.mockImplementation(async () => progression({ step1: { nom: 'ALPHA' } }));
    connecter(A);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  function BoutonDeconnexion() {
    const { deconnecter, dialogue } = useDeconnexion();
    return (
      <>
        <button type="button" onClick={() => void deconnecter()}>Déconnexion</button>
        {dialogue}
      </>
    );
  }

  it('saisie envoyee avant la deconnexion : aucun avertissement, brouillon efface', async () => {
    ecrireBrouillon(A, 't-1', 'step1', 1, { nom: 'EPSILON' }, canonique({ nom: 'ALPHA' }));
    save.mockImplementation(async () => progression({ step1: { nom: 'EPSILON' } }));
    render(<MemoryRouter><BoutonDeconnexion /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Déconnexion' }));
    await laisserFiler(0);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'EPSILON' } });
    expect(screen.queryByText('Une saisie n’a pas pu être envoyée')).not.toBeInTheDocument();
    expect(logoutServeur).toHaveBeenCalled();
    expect(brouillonsBruts()).toHaveLength(0);
  });

  it('saisie non envoyee : avertissement ; rester connecte la garde, confirmer l’efface', async () => {
    ecrireBrouillon(A, 't-1', 'step1', 1, { nom: 'ZETA' }, canonique({ nom: 'ALPHA' }));
    ecrireBrouillon(B, 't-1', 'step1', 1, { nom: 'SAISIE DE B' }, canonique({ nom: 'ALPHA' }));
    save.mockRejectedValue(erreurHttp(undefined));
    render(<MemoryRouter><BoutonDeconnexion /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Déconnexion' }));
    await laisserFiler(0);
    expect(screen.getByText('Une saisie n’a pas pu être envoyée')).toBeInTheDocument();
    expect(logoutServeur).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Rester connecté' }));
    await laisserFiler(0);
    expect(lireBrouillons(A)).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: 'Déconnexion' }));
    await laisserFiler(0);
    fireEvent.click(screen.getByRole('button', { name: 'Me déconnecter et l’effacer' }));
    await laisserFiler(0);
    expect(logoutServeur).toHaveBeenCalled();
    expect(lireBrouillons(A)).toHaveLength(0);
    // Le brouillon d'un autre utilisateur n'est pas touche.
    expect(lireBrouillons(B)).toHaveLength(1);
  });
});

describe('etape 8 de la creation : enregistrement automatique et relecture', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    save.mockReset();
    get.mockReset();
    connecter(A);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  const REGISTRE = { CN: { code: 'CN', label: 'Certificat négatif', filename: 'cn-signe.pdf' } };
  const AMONT = {
    step5: { dirigeants: [{ id: 'd1234567-x', nom: 'ALAMI', prenom: 'Sara', cinNumero: 'AB123' }] },
  };

  /** Derniere lecture de la saisie de l'etape 8, inscrite aupres du moteur. */
  let lectureEtape8: (() => Record<string, unknown> | null) | null = null;

  function Etape8() {
    const { stepData, progress, registerDirty } = useWorkflow();
    const espion = useCallback<typeof registerDirty>((step, getter) => {
      lectureEtape8 = getter;
      return registerDirty(step, getter);
    }, [registerDirty]);
    return (
      <Step8PiecesJointes
        existing={stepData.step8}
        data={stepData}
        registeredPieces={progress?.data?.pieces as Record<string, { code: string; label: string; filename?: string }>}
        saving={false}
        onSubmit={async () => undefined}
        onPieceUploaded={async () => undefined}
        registerDirty={espion}
      />
    );
  }

  it('toute saisie de l’etape part sans clic et se relit a l’identique apres rechargement', async () => {
    get.mockImplementation(async () => ({ ...progression({ ...AMONT, pieces: REGISTRE }), currentStep: 8 }));
    // Le serveur fusionne la saisie recue dans `data` (WorkflowUseCases.save).
    let serveur: Record<string, unknown> = { ...AMONT, pieces: REGISTRE };
    save.mockImplementation(async (_t: string, _s: number, data: Record<string, unknown>) => {
      serveur = { ...serveur, ...data };
      return { ...progression(serveur), currentStep: 8 };
    });
    const vue = render(<Parcours ouvert><Etape8 /></Parcours>);
    await laisserFiler(0);

    // Piece retiree, piece complementaire ajoutee puis importee, libelle en cours de saisie.
    fireEvent.click(screen.getByRole('button', { name: 'Retirer : Certificat négatif' }));
    fireEvent.change(screen.getByLabelText('Libellé d’une pièce complémentaire'), { target: { value: 'Attestation bancaire' } });
    fireEvent.click(screen.getByRole('button', { name: /Ajouter la pièce/ }));
    const bloc = screen.getByText('Attestation bancaire').closest('.border-2') as HTMLElement;
    const fichier = new File(['%PDF'], 'attestation.pdf', { type: 'application/pdf' });
    fireEvent.change(bloc.querySelector('input[type="file"]') as HTMLInputElement, { target: { files: [fichier] } });
    fireEvent.change(screen.getByLabelText('Libellé d’une pièce complémentaire'), { target: { value: 'Quittance' } });
    await laisserFiler(LECTURE_SAISIE_MS * 2);

    expect(save).toHaveBeenCalled();
    const [tid, etape, envoye] = save.mock.calls.at(-1) as [string, number, Record<string, Record<string, unknown>>];
    expect(tid).toBe('t-1');
    expect(etape).toBe(8);
    const saisie = envoye.step8;
    // Forme que le serveur range et que l'etape relit ; aucun fichier, seulement son nom.
    expect(saisie.pieces).toEqual({ uploaded: saisie.piecesUploaded });
    expect(saisie.piecesUploaded).toEqual([expect.stringMatching(/^EXTRA_/)]);
    expect(saisie.piecesRetirees).toEqual(['CN']);
    expect(saisie.libellePieceComplementaire).toBe('Quittance');
    expect(JSON.stringify(saisie)).not.toContain('%PDF');
    const avant = lectureEtape8?.();

    // Rechargement : le serveur rend ce qu'il a recu (save fusionne `step8`).
    vue.rerender(<Parcours ouvert={false} />);
    await laisserFiler(0);
    save.mockClear();
    get.mockImplementation(async () => ({ ...progression({ ...AMONT, pieces: REGISTRE, step8: JSON.parse(JSON.stringify(saisie)) }), currentStep: 8 }));
    vue.rerender(<Parcours ouvert><Etape8 /></Parcours>);
    await laisserFiler(LECTURE_SAISIE_MS * 3);

    expect(lectureEtape8?.()).toEqual(avant);
    expect(save).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Libellé d’une pièce complémentaire')).toHaveValue('Quittance');
    expect(screen.getByText('attestation.pdf')).toBeInTheDocument();
    expect(screen.queryByText('cn-signe.pdf')).not.toBeInTheDocument();
  });
});
