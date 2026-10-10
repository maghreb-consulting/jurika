import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { InfoBulle, TexteAide } from '../Aide';

/** Aide integree (CDC 15.4) : une aide deja vue peut etre masquee et reaffichee. */
describe('Aide', () => {
  beforeEach(() => window.localStorage.clear());

  it('une aide masquee le reste au retour sur l ecran, puis se reaffiche', () => {
    const { unmount } = render(<TexteAide cle="essai" titre="Titre">Texte</TexteAide>);
    fireEvent.click(screen.getByRole('button', { name: 'Masquer l’aide' }));
    unmount();
    render(<TexteAide cle="essai" titre="Titre">Texte</TexteAide>);
    expect(screen.queryByRole('note')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Afficher l’aide/ }));
    expect(screen.getByRole('note', { name: 'Titre' })).toHaveTextContent('Texte');
  });

  it('sans stockage du navigateur, l aide s affiche quand meme', () => {
    const lecture = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloque');
    });
    render(<TexteAide cle="essai2" titre="Titre">Texte</TexteAide>);
    expect(screen.getByRole('note', { name: 'Titre' })).toBeInTheDocument();
    lecture.mockRestore();
  });

  it('l infobulle s ouvre au clavier et se relie au bouton', () => {
    render(<InfoBulle libelle="Aide sur le champ" texte="Explication" />);
    const bouton = screen.getByRole('button', { name: 'Aide sur le champ' });
    fireEvent.focus(bouton);
    const bulle = screen.getByRole('tooltip');
    expect(bulle).toHaveTextContent('Explication');
    expect(bouton).toHaveAttribute('aria-describedby', bulle.id);
    fireEvent.blur(bouton);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });
});
