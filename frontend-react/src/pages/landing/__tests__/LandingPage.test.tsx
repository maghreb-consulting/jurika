import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LandingPage } from '../LandingPage';

/**
 * Lot L7 (E3) : la vitrine est alignee sur le cahier des charges (paragraphe 4, 15.3, P1)
 * et ne publie aucune affirmation non etablie. Le test rend la page ENTIERE (toutes les
 * sections montees par MarketingApp) et lit le texte affiche.
 */
vi.mock('lenis', () => ({
  default: class {
    raf() { /* rien */ }
    on() { /* rien */ }
    destroy() { /* rien */ }
  },
}));
vi.mock('gsap', () => ({
  default: { registerPlugin: () => undefined, ticker: { add: () => undefined, remove: () => undefined, lagSmoothing: () => undefined } },
}));
vi.mock('gsap/ScrollTrigger', () => ({ ScrollTrigger: { update: () => undefined, refresh: () => undefined, getAll: () => [] } }));

beforeEach(() => {
  // Mouvement reduit : les textes animes sont rendus dans leur etat final, sans minuterie.
  vi.stubGlobal('matchMedia', (q: string) => ({
    matches: q.includes('reduce'),
    media: q,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
  }));
  vi.stubGlobal('IntersectionObserver', class {
    observe() { /* rien */ }
    unobserve() { /* rien */ }
    disconnect() { /* rien */ }
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

function rendre() {
  return render(
    <MemoryRouter>
      <LandingPage />
    </MemoryRouter>,
  );
}

/** Texte affiche, espaces insecables ramenees a des espaces simples. */
function texte(el: HTMLElement): string {
  return (el.textContent ?? '').replace(/[\u00a0\u202f]/g, ' ').replace(/\s+/g, ' ');
}

describe('Vitrine : grille tarifaire du cahier des charges (4.3, 4.5)', () => {
  it('affiche les quatre forfaits avec leurs prix HT annuels et mensuels', () => {
    rendre();
    const attendu: Record<string, string[]> = {
      essentiel: ['Essentiel', '3 750 DH HT / an', '375 DH HT / mois', '4 500 DH HT sur un an', "Jusqu'à 25 dossiers juridiques par mois", '1 superviseur et 1 accès employé', '5 Go'],
      professionnel: ['Professionnel', '7 500 DH HT / an', '750 DH HT / mois', '9 000 DH HT sur un an', "Jusqu'à 50 dossiers juridiques par mois", '1 superviseur et 2 accès employés', '10 Go'],
      max: ['Max', '15 000 DH HT / an', '1 500 DH HT / mois', '18 000 DH HT sur un an', "Jusqu'à 100 dossiers juridiques par mois", '1 superviseur et 4 accès employés', '25 Go'],
      'sur-devis': ['Sur Devis', 'Sur devis', 'Plus de 100 dossiers juridiques par mois', 'Utilisateurs selon devis', 'Stockage selon devis'],
    };
    for (const [id, morceaux] of Object.entries(attendu)) {
      const carte = texte(screen.getByTestId(`pricing-card-${id}`));
      for (const m of morceaux) expect(carte, `${id} : ${m}`).toContain(m);
    }
    expect(screen.getByTestId('pricing-grid').querySelectorAll('article')).toHaveLength(4);
  });

  it('reserve chaque fonction aux forfaits du paragraphe 4.5', () => {
    rendre();
    const carte = (id: string) => texte(screen.getByTestId(`pricing-card-${id}`));
    for (const id of ['essentiel', 'professionnel', 'max', 'sur-devis']) {
      expect(carte(id)).toContain('Chatbot juridique aux réponses sourcées');
      expect(carte(id)).toContain('Data Room et accès client');
    }
    expect(carte('essentiel')).not.toContain('Alertes de délais légaux');
    expect(carte('essentiel')).not.toContain('Messagerie interne');
    expect(carte('professionnel')).toContain('Alertes de délais légaux');
    expect(carte('professionnel')).toContain('Messagerie interne');
    expect(carte('professionnel')).not.toContain('Tableaux de bord de productivité');
    expect(carte('max')).toContain('Tableaux de bord de productivité');
    expect(carte('max')).toContain('Édition des actes intégrée à la plateforme');
    expect(carte('max')).not.toContain('Engagement de service contractuel');
    expect(carte('sur-devis')).toContain('Engagement de service contractuel et support prioritaire');
  });

  it("presente l'essai gratuit (4.2) et le socle commun (4.4), sans mention conforme CNDP", () => {
    rendre();
    const essai = texte(screen.getByTestId('pricing-essai'));
    expect(essai).toContain('Essai gratuit de 7 jours');
    expect(essai).toContain('3 dossiers, 1 superviseur et 1 employé, 500 Mo de stockage, 20 questions au chatbot');
    expect(essai).toContain('Fonctions du forfait Professionnel');
    expect(essai).toContain('Aucun téléchargement');
    const socle = texte(screen.getByTestId('pricing-socle'));
    expect(socle).toContain('Sauvegardes quotidiennes, testées par restauration');
    expect(socle).toContain('Export complet des données du cabinet');
    expect(socle).toContain('déclaration auprès de la CNDP en cours');
  });
});

describe('Vitrine : affirmations', () => {
  it("annonce l'hebergement AWS a Paris et l'objectif de 99,5 % en heures ouvrees", () => {
    rendre();
    const page = texte(document.body);
    expect(page).toContain('Amazon Web Services, à Paris');
    expect(page).toContain('99,5 % de disponibilité en heures ouvrées');
    expect(page).toContain('Aucune intelligence artificielle ne rédige un acte');
  });

  it("ne publie aucune affirmation contraire au cahier des charges", () => {
    rendre();
    const page = texte(document.body);
    const interdits = [
      'OVH', 'Hetzner', 'hébergement Maroc', 'hébergées au Maroc', '99,9', 'conforme CNDP', 'conformité CNDP',
      '499', '1 199', 'Business', 'Entreprise sur devis', 'Utilisateurs illimités', 'chatbot RAG',
      'Messagerie entre le client', 'chat client', 'temps réel', 'DPO', 'jurika.ai', '+212',
      'Statuts générés', 'Génération IA', 'IA génération', 'Copilote', 'Spring AI', 'RLS', 'multi-tenant',
      'Interopérable', '82%', 'SLA', 'Sans carte bancaire',
    ];
    for (const mot of interdits) {
      const motif = new RegExp(`(^|[^\\p{L}\\d])${mot.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}($|[^\\p{L}\\d])`, 'u');
      expect(page, mot).not.toMatch(motif);
    }
  });
});

describe('Vitrine : adresse de contact configurable (VITE_CONTACT_EMAIL)', () => {
  it("sans valeur, n'affiche aucune adresse et oriente le forfait Sur Devis vers JURIKA", () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', '');
    rendre();
    expect(texte(document.body)).not.toContain('@');
    expect(document.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(screen.getByTestId('pricing-sur-devis-sans-contact')).toBeInTheDocument();
  });

  it('avec une valeur, affiche cette adresse dans le pied de page et pour le devis', () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', 'contact@exemple.test');
    rendre();
    expect(screen.getByTestId('footer-contact')).toHaveAttribute('href', 'mailto:contact@exemple.test');
    const devis = within(screen.getByTestId('pricing-card-sur-devis')).getByTestId('pricing-cta-sur-devis');
    expect(devis.getAttribute('href')).toMatch(/^mailto:contact@exemple\.test\?subject=/);
  });
});
