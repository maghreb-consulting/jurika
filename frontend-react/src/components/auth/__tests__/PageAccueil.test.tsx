import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { PageAccueil, TitreAccueil } from '../PageAccueil';

/**
 * Lot L6b (E8) : mise en page d'accueil des ecrans hors coquille (connexion, inscription,
 * essai, mot de passe oublie...). Logo cliquable vers la vitrine, pied de page legal,
 * titre de l'onglet, un seul h1 porte par le contenu.
 */
function rendre(ui: React.ReactNode) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

describe('PageAccueil', () => {
  it('pose le logo (retour a l’accueil), le titre de l’onglet et les liens legaux', () => {
    rendre(
      <PageAccueil titre="Connexion" enTete={<span>Déjà un compte ?</span>}>
        <TitreAccueil titre="Accédez à votre espace">Saisissez le code.</TitreAccueil>
      </PageAccueil>,
    );
    expect(screen.getByRole('link', { name: /retour à l’accueil/ })).toHaveAttribute('href', '/');
    expect(screen.getByRole('img', { name: 'JURIKA' })).toBeInTheDocument();
    expect(document.title).toBe('Connexion - JURIKA');
    expect(screen.getByRole('heading', { level: 1, name: 'Accédez à votre espace' })).toBeInTheDocument();
    expect(screen.getByText('Déjà un compte ?')).toBeInTheDocument();
    const legal = screen.getByRole('navigation', { name: 'Informations légales' });
    expect(legal.querySelector('a[href="/cgu"]')).not.toBeNull();
    expect(legal.querySelector('a[href="/confidentialite"]')).not.toBeNull();
  });

  it('ecran de session (premiere connexion) : le logo ne renvoie pas vers la vitrine', () => {
    rendre(
      <PageAccueil titre="Double authentification" retourAccueil={false}>
        <p>contenu</p>
      </PageAccueil>,
    );
    expect(screen.queryByRole('link', { name: /retour à l’accueil/ })).toBeNull();
    expect(screen.getByRole('img', { name: 'JURIKA' })).toBeInTheDocument();
  });

  it('TitreAccueil : l’infobulle d’aide porte son libelle accessible', () => {
    rendre(<TitreAccueil titre="Mot de passe oublié" infoBulle="Indiquez le code du cabinet." libelleInfoBulle="Aide sur la réinitialisation" />);
    expect(screen.getByRole('button', { name: 'Aide sur la réinitialisation' })).toBeInTheDocument();
  });
});
