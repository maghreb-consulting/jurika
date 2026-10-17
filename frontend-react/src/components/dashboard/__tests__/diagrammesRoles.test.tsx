import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FriseDelais } from '../charts/FriseDelais';
import { BarresEmpilees } from '../charts/BarresEmpilees';
import { JaugeProgression } from '../charts/JaugeProgression';
import { Sparkline } from '../charts/Sparkline';
import { ColonnesChart } from '../charts/ColonnesChart';
import { RythmeEmploye } from '../GraphiquesEmploye';
import { comptesParMois, moisRecents, ticketsParType } from '../series';
import type { Ticket } from '../../../types/ticket';

// Recharts mesure le conteneur (ResizeObserver absent de jsdom) : trace neutralise, legende et
// tableau equivalent restent rendus.
vi.mock('recharts', async (orig) => {
  const reel = await orig<typeof import('recharts')>();
  return { ...reel, ResponsiveContainer: () => null };
});

/** Lot L6b (E18) : diagrammes propres a chaque role (demande du 2026-10-17). */
const AUJOURDHUI = new Date(2026, 9, 15); // jeudi 15 octobre 2026

describe('FriseDelais', () => {
  const points = [
    { date: '2026-10-16', libelle: 'Dépôt des comptes', detail: 'Rif Logistique' },
    { date: '2026-10-16', libelle: 'Date limite du ticket', detail: 'TK-1' },
    { date: '2026-11-10', libelle: 'Publication au BO', detail: 'Argane' },
    { date: '2026-12-31', libelle: 'Hors fenetre' },
  ];

  it('une case par jour sur 30 jours, alignee sur le lundi, delais comptes par jour', () => {
    const { container } = render(<FriseDelais points={points} enRetard={1} libelleRetard="tickets en retard" legende="Mes délais" aujourdHui={AUJOURDHUI} />);
    // Jeudi : trois cases vides (lundi a mercredi) avant le 15 octobre.
    const cases = container.querySelectorAll('.grid.grid-cols-7.gap-1:not(.mb-1) > *');
    expect(cases.length % 7).toBe(0);
    expect([...cases].slice(0, 3).every((c) => c.tagName === 'SPAN')).toBe(true);
    expect(container.querySelectorAll('.grid.grid-cols-7.gap-1:not(.mb-1) > button')).toHaveLength(30);
    // Le delai du 31 decembre est hors fenetre : 3 delais, pas 4.
    expect(screen.getByText(/Mes délais : 3 délais dans les 30 prochains jours, 1 tickets en retard/)).toBeInTheDocument();
  });

  it('le premier jour charge est detaille, un autre jour se choisit', () => {
    render(<FriseDelais points={points} legende="Mes délais" aujourdHui={AUJOURDHUI} />);
    expect(screen.getByText('Dépôt des comptes')).toBeInTheDocument();
    fireEvent.click(screen.getByTitle('10 novembre : 1 délai'));
    expect(screen.getByText('Publication au BO')).toBeInTheDocument();
    expect(screen.queryByText('Dépôt des comptes')).not.toBeInTheDocument();
  });

  it('etat vide explicite', () => {
    render(<FriseDelais points={[]} legende="Mes délais" aujourdHui={AUJOURDHUI} />);
    expect(screen.getByText('Aucun délai dans les 30 prochains jours.')).toBeInTheDocument();
  });
});

describe('BarresEmpilees', () => {
  it('total par ligne, total par serie en legende, tableau equivalent', () => {
    render(
      <BarresEmpilees
        legende="Charge"
        series={[
          { cle: 'a', nom: 'Création', ton: 'neutre' },
          { cle: 'b', nom: 'Démarches', ton: 'accent' },
        ]}
        lignes={[
          { cle: 'n', libelle: 'Nadia Idrissi', valeurs: { a: 1, b: 2 } },
          { cle: 'o', libelle: 'Omar Berrada', valeurs: { b: 1 } },
        ]}
      />,
    );
    const table = screen.getByRole('table', { name: 'Charge' });
    const lignes = within(table).getAllByRole('row');
    expect(lignes[1]).toHaveTextContent('Nadia Idrissi123');
    expect(lignes[2]).toHaveTextContent('Omar Berrada011');
    expect(screen.getByText('Démarches', { selector: 'figcaption span' })).toHaveTextContent('Démarches3');
  });
});

describe('JaugeProgression et Sparkline', () => {
  it('la jauge dit l etape aux lecteurs d ecran et l ecrit au centre', () => {
    render(<JaugeProgression valeur={2} total={4} libelle="Étape 2 sur 4 : préparation des actes" />);
    expect(screen.getByRole('img', { name: 'Étape 2 sur 4 : préparation des actes' })).toHaveTextContent('2/4');
  });

  it('la courbe de tendance ne dessine rien sous deux points', () => {
    const { container, rerender } = render(<Sparkline valeurs={[3]} libelle="Tendance" />);
    expect(container.querySelector('svg')).toBeNull();
    rerender(<Sparkline valeurs={[1, 1, 2, 4]} libelle="Tendance" />);
    expect(screen.getByRole('img', { name: 'Tendance' })).toBeInTheDocument();
  });
});

describe('ColonnesChart avec ligne', () => {
  it('la serie en ligne figure au tableau equivalent et sa derniere valeur en legende', () => {
    render(
      <ColonnesChart
        donnees={[
          { mois: 'sept. 26', nouveaux: 1, total: 4 },
          { mois: 'oct. 26', nouveaux: 2, total: 6 },
        ]}
        cleX="mois"
        legende="Croissance"
        series={[{ cle: 'nouveaux', nom: 'Nouveaux', ton: 'info' }]}
        ligne={{ cle: 'total', nom: 'Au total', ton: 'accent' }}
      />,
    );
    const lignes = within(screen.getByRole('table', { name: 'Croissance' })).getAllByRole('row');
    expect(lignes[0]).toHaveTextContent('PériodeNouveauxAu total');
    expect(lignes[2]).toHaveTextContent('oct. 2626');
    expect(screen.getByText(/Au total \(axe de droite\)/).parentElement ?? screen.getByText(/Au total \(axe de droite\)/)).toHaveTextContent('6');
  });
});

describe('series', () => {
  it('six mois, comptes par mois, dates absentes ignorees', () => {
    expect(moisRecents(3, AUJOURDHUI)).toEqual(['2026-08', '2026-09', '2026-10']);
    const l = comptesParMois({ a: ['2026-10-02T10:00:00Z', '2026-08-20T10:00:00Z', null, '2025-01-01T00:00:00Z'] }, 3, AUJOURDHUI);
    expect(l.map((x) => x.a)).toEqual([1, 0, 1]);
  });

  it('types d operation en francais, du plus frequent au moins frequent', () => {
    const t = (type: string) => ({ type }) as Ticket;
    expect(ticketsParType([t('MODIFICATION'), t('PV_AGO'), t('MODIFICATION')])).toEqual([
      { key: 'MODIFICATION', label: 'Modification', count: 2 },
      { key: 'PV_AGO', label: 'PV AGO', count: 1 },
    ]);
  });
});

describe('RythmeEmploye', () => {
  it('dit quand la liste chargee est partielle', () => {
    const tickets = [{ id: 't', type: 'CREATION', statut: 'CLOTURE_DOSSIER', createdAt: '2026-10-01T10:00:00Z', clotureAt: '2026-10-10T10:00:00Z' }] as Ticket[];
    render(<RythmeEmploye tickets={tickets} totalServeur={250} aujourdHui={AUJOURDHUI} />);
    expect(screen.getByText(/calculé sur vos 1 tickets les plus récents \(250 au total\)/)).toBeInTheDocument();
    const lignes = within(screen.getByRole('table', { name: 'Mes tickets reçus et clôturés par mois' })).getAllByRole('row');
    expect(lignes[6]).toHaveTextContent('oct. 2611');
  });
});
