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

// Empreinte des lignes 57 a 356 (pieces B, C et D, jusqu'a la fin) de
// ~/docs/CONTRAT_ABONNEMENT_JURIKA_V1.md (version 1 du 14 octobre 2026, validee par la direction,
// fichier d'empreinte ea69585b...) : toute retouche du texte la change.
const EMPREINTE_CONTRAT = 'ef6051777aa31d71aa7a89621cb14ff2e603f78d17944555e0abb16dd7d343e4';

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

  it('version 1 validee : plus aucune mention entre crochets ni mention de projet', () => {
    page(<CguPage />);
    expect(conditions).not.toMatch(/\[|\]/);
    expect(document.querySelectorAll('mark').length).toBe(0);
    const t = document.body.textContent ?? '';
    expect(t).not.toMatch(/projet|à compléter|surlign/i);
    expect(screen.getByTestId('cgu-version')).toHaveTextContent('Version 1 du contrat d’abonnement');
    expect(t).toContain('la durée déclarée à la Commission nationale de contrôle');
    expect(t).toContain('tribunal de commerce de Casablanca');
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
    expect(marquees).toEqual(['[durée déclarée à la CNDP — déclaration en cours]']);
    // Sous-traitants et delai de notification : ceux de la version 1 du contrat.
    const t = document.body.textContent ?? '';
    expect(t).toContain('au plus tard 72 heures après');
    expect(t).toContain('Brevo, puis Amazon Web Services (SES)');
    expect(t).toContain('Fournisseur du modèle de langage désigné sur la plateforme');
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
