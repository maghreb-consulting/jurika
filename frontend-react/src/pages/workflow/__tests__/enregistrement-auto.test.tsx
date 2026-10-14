import { act, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L7 (E2) -- AUCUNE SAISIE PERDUE. La saisie d'une etape part sans clic :
 * peu apres la frappe, au changement d'etape, au demontage (bouton "Quitter"),
 * a la fermeture de la page (envoi fiable) ; un echec est affiche en clair.
 */

const save = vi.fn();
vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    get: vi.fn(async () => ({
      id: 'wf-1',
      ticketId: 't-1',
      currentStep: 2,
      totalSteps: 3,
      statut: 'EN_COURS',
      data: { step1: { nom: 'ALPHA' } },
    })),
    start: vi.fn(),
    save: (...args: unknown[]) => save(...args),
  },
}));
vi.mock('../../../services/ticket.service', () => ({
  ticketService: { get: vi.fn(async () => ({ id: 't-1', type: 'CREATION_SARL', titre: 'ALPHA' })) },
}));
vi.mock('react-router-dom', () => ({ useParams: () => ({ ticketId: 't-1' }) }));

const { WorkflowDataProvider, useWorkflow } = await import('../useWorkflow');
const { useStepAutosave } = await import('../useStepAutosave');
const { IndicateurEnregistrement } = await import('../../../components/workflow/IndicateurEnregistrement');
const { ATTENTE_MAX_MS, LECTURE_SAISIE_MS } = await import('../enregistrementAuto');

/** Etape de test : un champ, enregistre sous `viewStep` comme les pages a etapes. */
function Etape() {
  const { stepData, viewStep, registerDirty, goToStep } = useWorkflow();
  const [nom, setNom] = useState(String(stepData[`step${viewStep}`]?.nom ?? ''));
  useStepAutosave(viewStep, () => ({ nom }), registerDirty);
  return (
    <div>
      <IndicateurEnregistrement />
      <p>Étape affichée : {viewStep}</p>
      <label htmlFor="nom">Nom</label>
      <input id="nom" value={nom} onChange={(e) => setNom(e.target.value)} />
      <button type="button" onClick={() => void goToStep(viewStep === 1 ? 2 : 1)}>
        Changer d’étape
      </button>
    </div>
  );
}

function Parcours({ ouvert }: { ouvert: boolean }) {
  return ouvert ? (
    <WorkflowDataProvider fallback={<p>Chargement</p>}>
      <Etape />
    </WorkflowDataProvider>
  ) : (
    <p>Liste des tickets</p>
  );
}

async function laisserFiler(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

async function ouvrir() {
  const vue = render(<Parcours ouvert />);
  await laisserFiler(0);
  expect(screen.getByText('Étape affichée : 2')).toBeInTheDocument();
  // Revenir a l'etape 1, deja enregistree (ALPHA) : rien a envoyer.
  fireEvent.click(screen.getByRole('button', { name: 'Changer d’étape' }));
  await laisserFiler(0);
  expect(screen.getByText('Étape affichée : 1')).toBeInTheDocument();
  return vue;
}

function progression() {
  return { id: 'wf-1', ticketId: 't-1', currentStep: 2, totalSteps: 3, statut: 'EN_COURS', data: {} };
}

describe('enregistrement automatique des saisies du parcours', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    save.mockReset();
    save.mockImplementation(async () => progression());
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('une etape ouverte puis quittee sans modification n’envoie rien', async () => {
    const vue = await ouvrir();
    await laisserFiler(ATTENTE_MAX_MS * 2);
    vue.rerender(<Parcours ouvert={false} />);
    await laisserFiler(0);
    expect(save).not.toHaveBeenCalled();
  });

  it('la saisie part peu apres la frappe, sans clic, et l’heure s’affiche', async () => {
    await ouvrir();
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'ALPHA SARL' } });
    await laisserFiler(LECTURE_SAISIE_MS * 2);
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'ALPHA SARL' } });
    expect(screen.getByTestId('indicateur-enregistrement')).toHaveTextContent(/Enregistré à \d{2}:\d{2}/);
  });

  it('quitter le parcours (demontage) sans clic envoie la saisie en attente', async () => {
    const vue = await ouvrir();
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'ALPHA ET FILS' } });
    vue.rerender(<Parcours ouvert={false} />);
    await laisserFiler(0);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'ALPHA ET FILS' } });
  });

  it('changer d’etape juste apres la frappe ne perd rien', async () => {
    await ouvrir();
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'BETA' } });
    fireEvent.click(screen.getByRole('button', { name: 'Changer d’étape' }));
    await laisserFiler(0);
    expect(screen.getByText('Étape affichée : 2')).toBeInTheDocument();
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('t-1', 1, { step1: { nom: 'BETA' } });
  });

  it('un echec d’enregistrement est affiche en clair', async () => {
    save.mockRejectedValue(new Error('coupure'));
    await ouvrir();
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'GAMMA' } });
    await laisserFiler(LECTURE_SAISIE_MS * 2);
    const indicateur = screen.getByTestId('indicateur-enregistrement');
    expect(indicateur).toHaveTextContent('Échec de l’enregistrement automatique');
    expect(screen.getByRole('alert')).toHaveTextContent('Votre saisie reste à l’écran');
    // Pas de renvoi en boucle de la meme saisie : elle repart a la modification suivante.
    await laisserFiler(ATTENTE_MAX_MS * 2);
    expect(save).toHaveBeenCalledTimes(1);
    save.mockImplementation(async () => progression());
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'GAMMA SARL' } });
    await laisserFiler(LECTURE_SAISIE_MS * 2);
    expect(save).toHaveBeenLastCalledWith('t-1', 1, { step1: { nom: 'GAMMA SARL' } });
    expect(indicateur).toHaveTextContent(/Enregistré à/);
  });

  it('la fermeture de la page envoie la saisie par un envoi fiable (keepalive)', async () => {
    const envoi = vi.fn(async () => ({ ok: true, status: 200, json: async () => progression() }));
    vi.stubGlobal('fetch', envoi);
    await ouvrir();
    fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'DELTA' } });
    await act(async () => {
      window.dispatchEvent(new Event('pagehide'));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(envoi).toHaveBeenCalledTimes(1);
    const [url, init] = envoi.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toMatch(/\/workflows\/t-1\/save$/);
    expect(init.keepalive).toBe(true);
    expect(JSON.parse(String(init.body))).toEqual({ currentStep: 1, data: { step1: { nom: 'DELTA' } } });
    expect(save).not.toHaveBeenCalled();
  });
});
