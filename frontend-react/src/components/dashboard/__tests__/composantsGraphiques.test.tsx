import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { BarresHorizontales } from '../charts/BarresHorizontales';
import { ColonnesChart } from '../charts/ColonnesChart';
import { KpiCard } from '../KpiCard';
import { dureeCloture } from '../valeurs';
import { etatDelai } from '../delais';

// Recharts mesure le conteneur (ResizeObserver absent de jsdom) : trace neutralise, le tableau
// equivalent et la legende restent rendus.
vi.mock('recharts', async (orig) => {
  const reel = await orig<typeof import('recharts')>();
  return { ...reel, ResponsiveContainer: () => null };
});

/** Lot L6b (E17) : composants communs des quatre tableaux de bord. */
describe('BarresHorizontales', () => {
  it('ecrit chaque libelle, sa valeur, sa part et le total', () => {
    render(
      <MemoryRouter>
        <BarresHorizontales
          legende="Tickets par étape"
          part
          total="Tickets ouverts"
          barres={[
            { cle: 'a', libelle: 'Génération des documents', valeur: 3 },
            { cle: 'b', libelle: 'Démarches', valeur: 1, lien: '/tickets' },
          ]}
        />
      </MemoryRouter>,
    );
    const liste = screen.getByRole('list', { name: 'Tickets par étape' });
    const [l1, l2] = within(liste).getAllByRole('listitem');
    expect(l1).toHaveTextContent('Génération des documents');
    expect(l1).toHaveTextContent('3');
    expect(l1).toHaveTextContent('(75 %)');
    expect(within(l2).getByRole('link', { name: 'Démarches' })).toHaveAttribute('href', '/tickets');
    expect(screen.getByText('Tickets ouverts').parentElement).toHaveTextContent('4');
  });
});

describe('ColonnesChart', () => {
  it('legende avec le total de chaque serie et tableau equivalent en entiers', () => {
    render(
      <ColonnesChart
        donnees={[
          { mois: 'sept. 26', crees: 3, clotures: 1 },
          { mois: 'oct. 26', crees: 2, clotures: 0 },
        ]}
        cleX="mois"
        legende="Créés et clôturés par mois"
        series={[
          { cle: 'crees', nom: 'Créés', ton: 'info' },
          { cle: 'clotures', nom: 'Clôturés', ton: 'succes' },
        ]}
      />,
    );
    expect(screen.getByText('Créés sur la période :').parentElement ?? screen.getByText(/Créés sur la période/)).toHaveTextContent('5');
    const table = screen.getByRole('table', { name: 'Créés et clôturés par mois' });
    const lignes = within(table).getAllByRole('row');
    expect(lignes[1]).toHaveTextContent('sept. 26');
    expect(lignes[1]).toHaveTextContent('31');
    expect(table.textContent).not.toMatch(/\d[.,]\d/);
  });
});

describe('KpiCard', () => {
  it('avec `to`, toute la carte est un lien et l aide explique le calcul', () => {
    render(
      <MemoryRouter>
        <KpiCard label="Tickets en cours" value={4} to="/tickets" aide="Tickets ouverts." />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Tickets en cours' })).toHaveAttribute('href', '/tickets');
    expect(screen.getByRole('button', { name: 'Aide : Tickets en cours' })).toBeInTheDocument();
  });

  it('valeur absente : "--", jamais zero', () => {
    render(<KpiCard label="Dossiers en retard" value={null} />);
    expect(screen.getByText('--')).toBeInTheDocument();
  });
});

describe('lecture des durees et des delais', () => {
  it('duree de cloture en heures sous 48 h, en jours au-dela', () => {
    expect(dureeCloture(12.4)).toBe('12 h');
    expect(dureeCloture(196)).toBe('8,2 jours');
    expect(dureeCloture(24 * 15)).toBe('15 jours');
  });

  it('delai : en retard, aujourd hui, bientot, plus tard', () => {
    expect(etatDelai(-4)).toEqual({ texte: 'En retard de 4 jours', variante: 'danger' });
    expect(etatDelai(0).texte).toBe('Aujourd’hui');
    expect(etatDelai(1)).toEqual({ texte: 'Dans 1 jour', variante: 'warning' });
    expect(etatDelai(30).variante).toBe('neutral');
  });
});
