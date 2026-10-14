import { describe, it, expect, vi, afterEach } from 'vitest';
import { createHash } from 'node:crypto';
import type { ReactElement } from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CguPage } from '../CguPage';
import { ConfidentialitePage } from '../ConfidentialitePage';
import { adresseContact } from '../contact';
import conditions from '../contrat-abonnement-conditions.md?raw';

/**
 * Lot L7 (E3) : pages legales publiques.
 *  - CGU : texte du contrat d'abonnement valide, repris tel quel (pieces B, C et D) ;
 *  - confidentialite : sans jargon technique ni affirmation non etablie ; CNDP "en cours" ;
 *  - adresse de contact configurable au build (VITE_CONTACT_EMAIL), sans valeur de repli.
 */

// Empreinte des lignes 60 a 358 de ~/docs/CONTRAT_ABONNEMENT_JURIKA_PROJET.md (version du
// 12 octobre 2026, fichier d'empreinte a36817c7...) : toute retouche du texte la change.
const EMPREINTE_CONTRAT = '50d03efbc94a60141ddc4566b51a45f962c50fcc104af487fa88fb8e40880d7f';

afterEach(() => {
  vi.unstubAllEnvs();
});

function page(el: ReactElement) {
  return render(<MemoryRouter>{el}</MemoryRouter>);
}

/** Texte sans mise en forme : espaces normalisees. */
function aplati(s: string): string {
  return s.replace(/[\u00a0\u202f]/g, ' ').replace(/\s+/g, '');
}

describe('Conditions generales (CguPage)', () => {
  it('reprend le texte du contrat valide sans retouche', () => {
    expect(createHash('sha256').update(conditions, 'utf8').digest('hex')).toBe(EMPREINTE_CONTRAT);
  });

  it("affiche tout le texte, mot pour mot, sans en perdre ni en ajouter", () => {
    page(<CguPage />);
    const article = document.querySelector('article');
    expect(article).not.toBeNull();
    // Le source, debarrasse de sa seule syntaxe (titres, gras, tableaux, numeros de liste).
    const source = conditions
      .split('\n')
      .filter((l) => !/^\|[-|]+\|$/.test(l.trim()))
      .map((l) => l.replace(/^#+\s/, '').replace(/^\d+\.\s/, '').replace(/\*\*/g, '').replace(/\|/g, ''))
      .join('\n');
    const rendu = (article as HTMLElement).textContent ?? '';
    const debut = rendu.indexOf("B. Conditions générales d'abonnement");
    expect(debut).toBeGreaterThan(-1);
    expect(aplati(rendu.slice(debut))).toBe(aplati(source));
  });

  it("presente les 18 articles des conditions generales, l'accord de traitement et l'essai", () => {
    page(<CguPage />);
    expect(screen.getByRole('heading', { level: 2, name: "B. Conditions générales d'abonnement" })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'C. Accord de traitement des données personnelles' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: "D. Conditions d'utilisation de l'essai gratuit" })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: 'Article 18 — Droit applicable et litiges' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 3, name: /^Article \d+ — / })).toHaveLength(18 + 10);
  });

  it('signale chaque mention encore a completer, sans la remplacer', () => {
    page(<CguPage />);
    const attendues = conditions.match(/\[[^\]]+\]/g) ?? [];
    expect(attendues.length).toBeGreaterThan(0);
    const marquees = Array.from(document.querySelectorAll('mark[data-a-completer]')).map((m) => m.textContent);
    expect(marquees).toEqual(attendues);
    expect(marquees).toContain('[durée déclarée à la CNDP]');
  });

  it("ne reprend plus l'ancien texte (Stripe, Business, 99,9 %)", () => {
    page(<CguPage />);
    const t = document.body.textContent ?? '';
    for (const mot of ['Stripe', 'Business', 'Entreprise)', '99,9', 'legal@jurika.ma', 'PLAN_LIMIT']) {
      expect(t, mot).not.toContain(mot);
    }
  });
});

describe('Politique de confidentialite', () => {
  it('annonce la declaration CNDP en cours et AWS a Paris', () => {
    page(<ConfidentialitePage />);
    expect(screen.getByTestId('cndp-statut').textContent).toMatch(/en cours/);
    const t = document.body.textContent ?? '';
    expect(t).toContain('Amazon Web Services');
    expect(t).toContain('Union européenne — Paris');
    expect(t).toContain('au moins dix ans');
  });

  it("laisse la duree de conservation des archives a decider, sans valeur inventee", () => {
    page(<ConfidentialitePage />);
    const marquees = Array.from(document.querySelectorAll('mark[data-a-completer]')).map((m) => m.textContent);
    expect(marquees).toContain('[durée déclarée à la CNDP — déclaration en cours]');
    expect(marquees).toContain('[72 heures]');
  });

  it('ne contient ni jargon technique ni affirmation non etablie', () => {
    page(<ConfidentialitePage />);
    const t = document.body.textContent ?? '';
    const interdits = [
      'DPO', 'dpo@', 'BCrypt', 'JWT', 'AES', 'TLS', 'Row Level', 'RLS', 'OpenTelemetry', 'Prometheus',
      'Stripe', 'Twilio', 'Sentry', 'OpenAI', 'OVH', 'territoire marocain', 'RGPD', 'Pentest', 'cookie',
      '13 mois', '12 mois', '30 jours en lecture seule pour export',
    ];
    for (const mot of interdits) expect(t, mot).not.toContain(mot);
  });
});

describe('Adresse de contact (VITE_CONTACT_EMAIL)', () => {
  it("sans valeur, aucune adresse n'est affichee", () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', '');
    expect(adresseContact()).toBeNull();
    page(<ConfidentialitePage />);
    expect(document.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(document.body.textContent ?? '').not.toMatch(/@/);
  });

  it("une valeur qui n'est pas une adresse ne s'affiche pas", () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', '  a-remplir  ');
    expect(adresseContact()).toBeNull();
  });

  it("avec une valeur, l'adresse est affichee sur les pages legales", () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', ' contact@exemple.test ');
    expect(adresseContact()).toBe('contact@exemple.test');
    page(<ConfidentialitePage />);
    expect(screen.getByTestId('confidentialite-contact')).toHaveAttribute('href', 'mailto:contact@exemple.test');
    expect(screen.getByTestId('legal-contact')).toHaveAttribute('href', 'mailto:contact@exemple.test');
  });
});
