import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../../lib/api', () => ({ api: { get } }));

import { LandingPage } from '../LandingPage';
import { oublierMentionsLegales } from '../mentionsLegales';
import { FORFAITS, formatPrice } from '../config/pricing';
import { NOMS_CAPTURES } from '../captures';
import { FONCTIONNALITES } from '../contenu';

/**
 * Lot L7 (E3), repris au lot L6b (E10) : la vitrine est alignee sur le cahier des charges
 * (paragraphes 1, 4, 13, 16, P1) et le contrat d'abonnement V1, et ne publie aucune
 * affirmation non etablie. Le test rend la page ENTIERE et lit le texte affiche.
 * Les mentions legales viennent de l'API (simulee : aucun appel reseau).
 */

const MENTIONS = {
  raisonSociale: 'Editeur Exemple',
  formeJuridique: 'SARL',
  capitalSocial: null,
  siegeSocial: '1 rue Exemple',
  ville: 'Casablanca',
  rcNumero: null,
  rcTribunal: 'Tribunal de commerce de Casablanca',
  ice: null,
  identifiantFiscal: null,
  taxeProfessionnelle: null,
  representantNom: null,
  representantQualite: null,
};

beforeEach(() => {
  oublierMentionsLegales();
  get.mockReset();
  // Par defaut, la lecture reste en attente (aucune mise a jour apres le test) ; les tests
  // du pied de page la resolvent.
  get.mockReturnValue(new Promise(() => undefined));
});

afterEach(() => {
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

describe('Vitrine : sections', () => {
  it('presente les sections demandees, dans cet ordre', () => {
    rendre();
    const ids = Array.from(document.querySelectorAll('main section[id]')).map((s) => s.id);
    expect(ids).toEqual(['haut', 'solution', 'fonctionnalites', 'parcours', 'securite', 'tarifs', 'essai', 'faq', 'contact']);
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('liste les parcours couverts du cahier des charges (16.1)', () => {
    rendre();
    const parcours = texte(screen.getByTestId('parcours'));
    for (const p of [
      'Création de SARL et de SARL à associé unique',
      'Modifications et vie sociale',
      'Comptes, résultat et distributions',
      'assemblée générale ordinaire',
      'Succursale d’une société mère marocaine',
      'Ouverture, vie et fermeture',
      'Succursale au Maroc d’une société mère étrangère',
      'Dissolution et liquidation',
      'Incidents d’assemblée',
      'Import de dossier',
      'SARL et SARL à associé unique',
    ]) {
      expect(parcours, p).toContain(p);
    }
    expect(within(screen.getByTestId('parcours')).getAllByRole('listitem')).toHaveLength(8);
  });

  it("presente la securite par des faits du contrat V1, avec la declaration CNDP en cours", () => {
    rendre();
    const securite = texte(screen.getByTestId('securite'));
    for (const fait of [
      'Double authentification',
      'Cloisonnement des cabinets',
      'Journalisation des actions',
      'Sauvegardes quotidiennes',
      'testées par restauration',
      'Amazon Web Services, à Paris',
      'sous réserve de l’autorisation de transfert de la CNDP',
      'Déclaration CNDP en cours',
      'jamais à entraîner un modèle d’intelligence artificielle',
    ]) {
      expect(securite, fait).toContain(fait);
    }
  });

  it('annonce les fonctions reservees a un forfait a partir de la table du paragraphe 4.5', () => {
    rendre();
    const section = screen.getByRole('region', { name: 'Ce que fait la plateforme' });
    const carte = (titre: string) => texte(within(section).getByRole('heading', { name: titre }).closest('li') as HTMLElement);
    expect(carte('Alertes de délais légaux')).toContain('À partir du forfait Professionnel');
    expect(carte('Messagerie interne')).toContain('À partir du forfait Professionnel');
    expect(carte('Édition intégrée et tableaux de bord')).toContain('À partir du forfait Max');
    expect(carte('Data Room par dossier')).not.toContain('À partir du forfait');
  });
});

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

  it("presente l'essai gratuit (4.2, piece D) et le socle commun (4.4), sans mention conforme CNDP", () => {
    rendre();
    const essai = texte(screen.getByTestId('pricing-essai'));
    expect(essai).toContain('Essai gratuit de 7 jours');
    expect(essai).toContain('3 dossiers, 1 superviseur et 1 employé, 500 Mo de stockage, 20 questions au chatbot');
    expect(essai).toContain('Fonctions du forfait Professionnel');
    expect(essai).toContain('Aucun téléchargement');
    expect(essai).toContain('ne doit pas être utilisé pour traiter de vrais dossiers de clients');
    expect(essai).toContain('lecture seule pendant 30 jours');
    const socle = texte(screen.getByTestId('pricing-socle'));
    expect(socle).toContain('Sauvegardes quotidiennes, testées par restauration');
    expect(socle).toContain('Export complet des données du cabinet');
    expect(socle).toContain('déclaration auprès de la CNDP en cours');
  });

  it("menent a l'inscription et a l'essai", () => {
    rendre();
    expect(screen.getByTestId('pricing-cta-essentiel')).toHaveAttribute('href', '/signup');
    expect(screen.getByTestId('hero-essai-cta')).toHaveAttribute('href', '/essai');
    expect(screen.getByTestId('essai-cta')).toHaveAttribute('href', '/essai');
    expect(screen.getByTestId('navbar-login-cta')).toHaveAttribute('href', '/login');
  });
});

describe('Vitrine : affirmations', () => {
  it("annonce l'hebergement AWS prevu a Paris, sans objectif de disponibilite chiffre", () => {
    rendre();
    const page = texte(document.body);
    expect(page).toContain('Amazon Web Services, à Paris');
    expect(page).toMatch(/hébergement (est )?prévu chez Amazon Web Services/i);
    expect(page).toContain('Aucune intelligence artificielle ne rédige un acte');
    // L6b E10 (V2) : l'objectif de disponibilite du contrat V1 n'est pas un engagement de
    // service ; le plus sur est de ne pas l'afficher (demande du 2026-10-15).
    expect(page).not.toMatch(/99[,.]\d/);
    expect(page).not.toMatch(/disponibilité/i);
  });

  it('ne publie aucune affirmation contraire au cahier des charges', () => {
    rendre();
    const page = texte(document.body);
    const interdits = [
      'OVH', 'Hetzner', 'hébergement Maroc', 'hébergées au Maroc', '99,9', 'conforme CNDP', 'conformité CNDP',
      '499', '1 199', 'Business', 'Entreprise sur devis', 'Utilisateurs illimités', 'chatbot RAG',
      'Messagerie entre le client', 'chat client', 'temps réel', 'DPO', 'jurika.ai', '+212',
      'Statuts générés', 'Génération IA', 'IA génération', 'Copilote', 'Spring AI', 'RLS', 'multi-tenant',
      'Interopérable', '82%', 'SLA', 'Sans carte bancaire', '99,5', '99,9 %', 'garantie de disponibilité',
      // L6b E10 (V2) : compteurs de l'ancienne vitrine (corpus du cabinet, pas une promesse).
      '222', 'modèles d’actes', 'matrices de démarches',
      // L6b E10 : authentification unique absente du code et du contrat V1 (lot Securite).
      'authentification unique', "annuaire du cabinet",
    ];
    for (const mot of interdits) {
      const motif = new RegExp(`(^|[^\\p{L}\\d])${mot.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}($|[^\\p{L}\\d])`, 'iu');
      expect(page, mot).not.toMatch(motif);
    }
  });

  it('ne publie aucune conformite, certification ni promesse inverifiable', () => {
    rendre();
    const page = texte(document.body);
    for (const motif of [
      /conform/i, /certifié/i, /certification/i, /agréé/i, /homologu/i, /\bISO\b/, /garanti(e|s)? à 100/i, /100 ?%/,
      /\bn° ?1\b/i, /\bleader\b/i, /le meilleur/i, /la meilleure/i, /révolution/i, /en quelques clics/i,
      /gain de temps/i, /\bfois plus\b/i, /illimité/i, /zéro erreur/i, /sans aucune erreur/i,
    ]) {
      expect(page, String(motif)).not.toMatch(motif);
    }
  });

  it('ne publie aucun temoignage ni logo de client', () => {
    rendre();
    const page = texte(document.body);
    for (const motif of [/témoign/i, /nous font confiance/i, /ils nous/i, /nos clients/i, /avis (de nos|des) clients/i, /recommand/i, /satisfait/i]) {
      expect(page, String(motif)).not.toMatch(motif);
    }
    expect(document.querySelector('blockquote, q, cite')).toBeNull();
    // Seules images : le logo Moucharabieh de JURIKA et les captures de la plateforme
    // (public/vitrine, liste fermee de captures.ts) ; aucune autre image (logo de client...).
    const images = Array.from(document.querySelectorAll('img')).map((i) => i.getAttribute('src') ?? '');
    expect(images.length).toBeGreaterThan(0);
    const captures = new Set(NOMS_CAPTURES.map((n) => `/vitrine/${n}-800.webp`));
    for (const src of images) expect(/moucharabieh/.test(src) || captures.has(src), src).toBe(true);
  });

  it('ne publie aucun chiffre ni statistique hors prix, quotas et textes du cahier des charges', () => {
    rendre();
    const page = texte(document.body);
    // Prix (4.3) ; quotas (4.3, 4.2) ; delais du contrat V1 (essai 7 et 30 jours, violation de
    // donnees 72 heures) ; references des lois (5-96, 09-08) ;
    // annee du droit d'auteur.
    const prix = FORFAITS.flatMap((f) =>
      f.prixAnnuelHt === null || f.prixMensuelHt === null ? [] : [f.prixAnnuelHt, f.prixMensuelHt, f.prixMensuelHt * 12],
    ).map((n) => formatPrice(n).replace(/\u00a0/g, ' '));
    const autorises = new Set([
      ...prix, '1', '2', '3', '4', '5', '7', '10', '20', '25', '30', '50', '100', '500', '72', '96', '09', '08',
      String(new Date().getFullYear()),
    ]);
    const nombres = page.match(/\d+(?:[ ,]\d+)*/g) ?? [];
    const inconnus = nombres.filter((n) => !autorises.has(n));
    expect(inconnus).toEqual([]);
  });

  it("n'utilise aucune couleur en dur, aucune police tierce ni bibliotheque d'animation", () => {
    const dossier = resolve(__dirname, '..');
    const sources = (d: string): string[] =>
      readdirSync(d, { withFileTypes: true }).flatMap((e) =>
        e.isDirectory() ? (e.name === '__tests__' ? [] : sources(join(d, e.name))) : /\.(tsx?|css)$/.test(e.name) ? [join(d, e.name)] : [],
      );
    const fichiers = sources(dossier);
    expect(fichiers.length).toBeGreaterThan(5);
    for (const f of fichiers) {
      const code = readFileSync(f, 'utf-8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
      expect(code, f).not.toMatch(/#[0-9a-f]{3,8}\b|\brgba?\(|\bhsla?\(/i);
      expect(code, f).not.toMatch(/\b(?:bg|text|border|ring|from|to|via|fill|stroke)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b|\b(?:bg|text|border)-(?:white|black)\b/);
      expect(code, f).not.toMatch(/fonts\.googleapis|Playfair|from 'gsap|from 'lenis|framer-motion/);
    }
    const html = readFileSync(resolve(__dirname, '../../../../index.html'), 'utf-8');
    expect(html).not.toMatch(/fonts\.googleapis|Playfair/);
  });
});

describe('Vitrine : captures de la plateforme et mouvement', () => {
  it('montre chaque capture prevue, optimisee : srcset 800w et 1600w, dimensions, chargement differe, alt', () => {
    rendre();
    const imgs = Array.from(document.querySelectorAll<HTMLImageElement>('img[src^="/vitrine/"]'));
    const vues = new Set(imgs.map((i) => (i.getAttribute('src') ?? '').replace(/^\/vitrine\/|-800\.webp$/g, '')));
    // Le tableau de bord de l'accroche suit le theme (clair en jsdom) ; la version sombre est
    // montree a cote de la claire dans la section du suivi.
    expect([...vues].sort()).toEqual([...NOMS_CAPTURES].sort());
    for (const img of imgs) {
      const src = img.getAttribute('src') ?? '';
      const base = src.replace(/-800\.webp$/, '');
      expect(img.getAttribute('srcset'), src).toBe(`${base}-800.webp 800w, ${base}-1600.webp 1600w`);
      expect(img.getAttribute('sizes'), src).toBeTruthy();
      expect(Number(img.getAttribute('width')), src).toBeGreaterThan(0);
      expect(Number(img.getAttribute('height')), src).toBeGreaterThan(0);
      expect(img.getAttribute('decoding'), src).toBe('async');
      expect((img.getAttribute('alt') ?? '').length, src).toBeGreaterThan(20);
    }
    const accroche = imgs.filter((i) => screen.getByTestId('hero').contains(i));
    expect(accroche).toHaveLength(1);
    expect(accroche[0]).toHaveAttribute('loading', 'eager');
    expect(accroche[0]).toHaveAttribute('fetchpriority', 'high');
    for (const img of imgs.filter((i) => !accroche.includes(i))) expect(img, img.src).toHaveAttribute('loading', 'lazy');
  });

  it('presente chaque fonctionnalite livree une seule fois', () => {
    rendre();
    const section = screen.getByRole('region', { name: 'Ce que fait la plateforme' });
    for (const f of FONCTIONNALITES) {
      expect(within(section).getAllByRole('heading', { name: f.titre }), f.titre).toHaveLength(1);
    }
  });

  it("n'anime rien quand l'ordinateur demande de reduire les animations", () => {
    const origine = window.matchMedia;
    window.matchMedia = ((q: string) => ({
      matches: q.includes('reduce'),
      media: q,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    })) as typeof window.matchMedia;
    const io = vi.fn(function () {
      return { observe: vi.fn(), unobserve: vi.fn(), disconnect: vi.fn() };
    });
    vi.stubGlobal('IntersectionObserver', io);
    try {
      rendre();
      expect(document.querySelector('[data-apparition]')).toBeNull();
      expect(io).not.toHaveBeenCalled();
    } finally {
      window.matchMedia = origine;
      vi.unstubAllGlobals();
    }
  });

  it("anime les apparitions au defilement, sans masquer ce qui est deja a l'ecran", () => {
    const observes: Element[] = [];
    vi.stubGlobal(
      'IntersectionObserver',
      vi.fn(function () {
        return { observe: (e: Element) => observes.push(e), unobserve: vi.fn(), disconnect: vi.fn() };
      }),
    );
    try {
      rendre();
      expect(document.querySelector('[data-apparition]')).not.toBeNull();
      expect(observes.length).toBeGreaterThan(5);
      // jsdom : toutes les boites sont a 0 x 0, donc "a l'ecran" : rien n'est masque.
      expect(document.querySelectorAll('[data-apparait]:not([data-visible])')).toHaveLength(0);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("n'anime que l'opacite et la transformation, et coupe tout mouvement si l'animation est reduite (vitrine.css)", () => {
    // L6b E10 (V3, demande du 2026-10-15) : la vitrine est animee (keyframes, durees de plus
    // de 300 ms permises) ; le principe reste : seules l'opacite et la transformation
    // s'animent (aucun decalage de la mise en page) et "reduire les animations" arrete tout.
    // Le detail des gardes est dans LandingMouvement.test.tsx.
    const css = readFileSync(resolve(__dirname, '../vitrine.css'), 'utf-8').replace(/\/\*[\s\S]*?\*\//g, '');
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)/);
    for (const m of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      for (const t of m[2].matchAll(/transition(?:-property)?\s*:\s*([^;]+);/g)) {
        for (const prop of t[1].split(',').map((x) => x.trim().split(/\s+/)[0])) {
          // Seule la regle .v-bouton reprend les transitions de couleur des boutons de la bibliotheque.
          const permises = ['opacity', 'transform', 'translate', 'scale', 'rotate', 'none'];
          if (m[1].includes('.v-bouton')) permises.push('color', 'background-color', 'border-color');
          expect(permises, `${m[1].trim()} : ${prop}`).toContain(prop);
        }
      }
    }
  });
});

describe('Vitrine : pied de page', () => {
  it('donne les conditions generales, la confidentialite a venir sans lien et les mentions legales', async () => {
    get.mockResolvedValue({ data: MENTIONS });
    rendre();
    expect(screen.getByTestId('pied-cgu')).toHaveAttribute('href', '/cgu');
    const confidentialite = screen.getByTestId('pied-confidentialite');
    expect(texte(confidentialite)).toBe('Politique de confidentialité (à venir)');
    expect(confidentialite.querySelector('a')).toBeNull();
    expect(document.querySelector('a[href="/confidentialite"]')).toBeNull();
    expect(screen.getByTestId('pied-mentions-lien')).toHaveAttribute('href', '/mentions-legales');
    const rappel = await screen.findByTestId('pied-mentions');
    expect(get).toHaveBeenCalledWith('/public/editeur/mentions-legales');
    const t = texte(rappel);
    expect(t).toContain('Editeur Exemple, SARL');
    expect(t).toContain('1 rue Exemple, Casablanca');
    expect(t).toContain('non renseigné (Tribunal de commerce de Casablanca)');
    expect(t).toMatch(/ICE ?non renseigné/);
  });

  it("dit clairement l'echec du chargement des mentions legales, puis reessaie", async () => {
    get.mockReset();
    get.mockRejectedValueOnce(new Error('reseau')).mockResolvedValueOnce({ data: MENTIONS });
    rendre();
    const erreur = await screen.findByTestId('pied-mentions-erreur');
    expect(texte(erreur)).toContain('Les mentions légales de l’éditeur n’ont pas pu être chargées');
    await userEvent.click(within(erreur).getByRole('button', { name: 'Réessayer' }));
    await waitFor(() => expect(screen.getByTestId('pied-mentions')).toBeInTheDocument());
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("refuse une reponse illisible au lieu d'afficher des mentions supposees", async () => {
    get.mockReset();
    get.mockResolvedValue({ data: { raisonSociale: 42 } });
    rendre();
    expect(await screen.findByTestId('pied-mentions-erreur')).toBeInTheDocument();
    expect(screen.queryByTestId('pied-mentions')).toBeNull();
  });
});

describe('Vitrine : adresse de contact configurable (VITE_CONTACT_EMAIL)', () => {
  it("sans valeur, n'affiche aucune adresse et oriente le forfait Sur Devis vers JURIKA", async () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', '');
    get.mockResolvedValue({ data: MENTIONS });
    rendre();
    await screen.findByTestId('pied-mentions');
    expect(texte(document.body)).not.toContain('@');
    expect(document.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(screen.getByTestId('pricing-sur-devis-sans-contact')).toBeInTheDocument();
    expect(screen.getByTestId('contact-sans-adresse')).toBeInTheDocument();
  });

  it('avec une valeur, affiche cette adresse dans le pied de page, le contact et pour le devis', () => {
    vi.stubEnv('VITE_CONTACT_EMAIL', 'contact@exemple.test');
    rendre();
    expect(screen.getByTestId('footer-contact')).toHaveAttribute('href', 'mailto:contact@exemple.test');
    expect(screen.getByTestId('contact-adresse')).toHaveAttribute('href', 'mailto:contact@exemple.test');
    const devis = within(screen.getByTestId('pricing-card-sur-devis')).getByTestId('pricing-cta-sur-devis');
    expect(devis.getAttribute('href')).toMatch(/^mailto:contact@exemple\.test\?subject=/);
  });
});
