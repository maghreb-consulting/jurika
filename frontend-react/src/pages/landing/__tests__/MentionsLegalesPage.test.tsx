import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../../lib/api', () => ({ api: { get } }));

import { MentionsLegalesPage } from '../MentionsLegalesPage';
import { CLES_MENTIONS, LIBELLES_MENTIONS, lireMentions, oublierMentionsLegales } from '../mentionsLegales';

/**
 * Lot L6b (E10) : page /mentions-legales. Les douze mentions viennent des Parametres de
 * l'editeur (route publique simulee) ; une mention nulle s'affiche "non renseigné" ; une
 * erreur de chargement est dite, avec "Reessayer".
 */
const COMPLETES = {
  raisonSociale: 'Editeur Exemple',
  formeJuridique: 'SARL',
  capitalSocial: '100 000 DH',
  siegeSocial: '1 rue Exemple',
  ville: 'Casablanca',
  rcNumero: 'RC-EX-1',
  rcTribunal: 'Tribunal de commerce de Casablanca',
  ice: 'ICE-EX-1',
  identifiantFiscal: 'IF-EX-1',
  taxeProfessionnelle: 'TP-EX-1',
  representantNom: 'Representant Exemple',
  representantQualite: 'Gerant',
};

beforeEach(() => {
  oublierMentionsLegales();
  get.mockReset();
});

function rendre() {
  return render(
    <MemoryRouter>
      <MentionsLegalesPage />
    </MemoryRouter>,
  );
}

describe('Mentions legales (/mentions-legales)', () => {
  it('affiche les douze mentions des Parametres de l editeur, chacune a son libelle', async () => {
    get.mockResolvedValue({ data: COMPLETES });
    rendre();
    const liste = await screen.findByTestId('mentions-legales');
    expect(get).toHaveBeenCalledWith('/public/editeur/mentions-legales');
    for (const cle of CLES_MENTIONS) {
      const ligne = within(liste).getByTestId(`mention-${cle}`);
      expect(ligne).toHaveTextContent(LIBELLES_MENTIONS[cle]);
      expect(ligne).toHaveTextContent(COMPLETES[cle]);
    }
    expect(screen.getByRole('heading', { level: 1, name: 'Mentions légales' })).toBeInTheDocument();
    expect(document.title).toBe('Mentions légales — JURIKA');
  });

  it('affiche "non renseigné" pour une mention nulle, sans valeur supposee', async () => {
    get.mockResolvedValue({ data: { ...COMPLETES, ice: null, capitalSocial: null, rcNumero: '   ' } });
    rendre();
    const liste = await screen.findByTestId('mentions-legales');
    expect(within(liste).getByTestId('mention-ice')).toHaveTextContent(/non renseigné$/);
    expect(within(liste).getByTestId('mention-capitalSocial')).toHaveTextContent(/non renseigné$/);
    expect(within(liste).getByTestId('mention-rcNumero')).toHaveTextContent(/non renseigné$/);
    expect(within(liste).getAllByText('non renseigné')).toHaveLength(3);
  });

  it("dit l'erreur de chargement puis reessaie", async () => {
    get.mockRejectedValueOnce(new Error('reseau')).mockResolvedValueOnce({ data: COMPLETES });
    rendre();
    const alerte = await within(screen.getByRole('main')).findByRole('alert');
    expect(alerte).toHaveTextContent('Les mentions légales de l’éditeur n’ont pas pu être chargées');
    expect(screen.queryByTestId('mentions-legales')).toBeNull();
    await userEvent.click(within(alerte).getByRole('button', { name: 'Réessayer' }));
    await waitFor(() => expect(screen.getByTestId('mentions-legales')).toBeInTheDocument());
  });

  it("indique l'hebergeur du contrat V1 et la declaration CNDP en cours, sans lien de confidentialite", async () => {
    get.mockResolvedValue({ data: COMPLETES });
    rendre();
    await screen.findByTestId('mentions-legales');
    const page = document.body.textContent ?? '';
    expect(page).toContain('Amazon Web Services, à Paris (Union européenne)');
    expect(page).toContain('Déclaration CNDP en cours');
    expect(page).toContain('Politique de confidentialité : à venir.');
    expect(document.querySelector('a[href="/confidentialite"]')).toBeNull();
    expect(page).not.toMatch(/conforme/i);
  });
});

describe('lireMentions', () => {
  it('refuse une reponse qui n a pas la forme attendue (motif 9 : aucun repli silencieux)', () => {
    expect(() => lireMentions(null)).toThrow();
    expect(() => lireMentions([])).toThrow();
    expect(() => lireMentions({ ...COMPLETES, ice: 12 })).toThrow(/ice/);
  });

  it('rend nulle une mention absente ou vide', () => {
    const m = lireMentions({ raisonSociale: '  Editeur  ' });
    expect(m.raisonSociale).toBe('Editeur');
    expect(m.ice).toBeNull();
    expect(Object.keys(m)).toEqual([...CLES_MENTIONS]);
  });
});
