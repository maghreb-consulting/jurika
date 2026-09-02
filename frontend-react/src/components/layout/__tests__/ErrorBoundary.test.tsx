/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ErrorBoundary } from '../ErrorBoundary';

function Bomb({ explode }: { explode: boolean }): React.ReactElement {
  if (explode) throw new Error('boom');
  return <div>Contenu OK</div>;
}

describe('<ErrorBoundary>', () => {
  // React logge l'erreur catchée sur console.error — on le tait pour garder
  // la sortie de test lisible.
  let spy: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    spy = vi.spyOn(console, 'error').mockImplementation(() => {});
  });
  afterEach(() => {
    spy.mockRestore();
  });

  it('affiche le fallback quand un enfant lève une erreur au rendu', () => {
    render(
      <ErrorBoundary>
        <Bomb explode />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Une erreur est survenue')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /recharger la page/i })).toBeInTheDocument();
  });

  it('laisse la navigation (élément frère hors boundary) cliquable après un crash', () => {
    const onNavigate = vi.fn();
    render(
      <div>
        <button onClick={onNavigate}>Aller au dashboard</button>
        <ErrorBoundary>
          <Bomb explode />
        </ErrorBoundary>
      </div>,
    );
    // Le fallback est rendu...
    expect(screen.getByText('Une erreur est survenue')).toBeInTheDocument();
    // ...mais le lien de navigation hors-boundary reste présent et cliquable.
    fireEvent.click(screen.getByText('Aller au dashboard'));
    expect(onNavigate).toHaveBeenCalledTimes(1);
  });

  it('rend les enfants normalement quand il n y a pas d erreur', () => {
    render(
      <ErrorBoundary>
        <Bomb explode={false} />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Contenu OK')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
