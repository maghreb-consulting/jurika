/**
 * Lot L6b (E11) : lecture des aides de l'ecran en cours, pour le panneau de JURI.
 *
 * Les composants d'aide (components/ui/Aide.tsx) portent des attributs `data-aide` :
 * - `data-aide="texte"` : encadre TexteAide affiche (`data-aide-cle`, `data-aide-titre`) ;
 * - `data-aide="texte-masque"` : encadre masque par l'utilisateur (meme cle et titre) ;
 * - `data-aide="infobulle"` : InfoBulle (`data-aide-libelle`, `data-aide-texte`).
 *
 * Seule la zone de contenu de l'ecran (`#contenu`) est lue : les aides des fenetres et du
 * chatbot n'appartiennent pas a l'ecran. Aucune aide n'est inventee : un ecran sans aide
 * rend une liste vide, que le panneau annonce comme telle.
 */
export interface AideTexte {
  type: 'texte';
  cle: string;
  titre: string;
  texte: string;
  masquee: boolean;
}

export interface AideBulle {
  type: 'infobulle';
  libelle: string;
  texte: string;
}

export type AideEcran = AideTexte | AideBulle;

function nettoyer(t: string | null | undefined): string {
  return (t ?? '').replace(/\s+/g, ' ').trim();
}

/** Texte d'un encadre, sans son titre ni son bouton "Masquer l'aide". */
function corpsTexteAide(el: Element): string {
  const copie = el.cloneNode(true) as Element;
  copie.querySelectorAll('button').forEach((b) => b.remove());
  const titre = copie.firstElementChild;
  if (titre && titre.tagName === 'P') titre.remove();
  return nettoyer(copie.textContent);
}

export function lireAidesEcran(racine: ParentNode | null = typeof document === 'undefined' ? null : document.getElementById('contenu')): AideEcran[] {
  if (!racine) return [];
  const aides: AideEcran[] = [];
  const vues = new Set<string>();
  racine.querySelectorAll<HTMLElement>('[data-aide]').forEach((el) => {
    const genre = el.dataset.aide;
    if (genre === 'texte' || genre === 'texte-masque') {
      const cle = el.dataset.aideCle ?? '';
      if (!cle || vues.has(`t:${cle}`)) return;
      vues.add(`t:${cle}`);
      aides.push({
        type: 'texte',
        cle,
        titre: nettoyer(el.dataset.aideTitre),
        texte: genre === 'texte' ? corpsTexteAide(el) : '',
        masquee: genre === 'texte-masque',
      });
    } else if (genre === 'infobulle') {
      const texte = nettoyer(el.dataset.aideTexte);
      if (!texte || vues.has(`b:${texte}`)) return;
      vues.add(`b:${texte}`);
      aides.push({ type: 'infobulle', libelle: nettoyer(el.dataset.aideLibelle), texte });
    }
  });
  return aides;
}

/** Titre de l'ecran en cours (titre de la page), ou null. */
export function titreEcran(racine: ParentNode | null = typeof document === 'undefined' ? null : document.getElementById('contenu')): string | null {
  const h1 = racine?.querySelector('h1');
  const t = nettoyer(h1?.textContent);
  return t || null;
}

function echapper(v: string): string {
  return typeof CSS !== 'undefined' && typeof CSS.escape === 'function' ? CSS.escape(v) : v.replace(/["\\]/g, '\\$&');
}

/** Amene un encadre d'aide a l'ecran et lui donne le focus (lecteurs d'ecran). */
export function montrerAide(cle: string): boolean {
  if (typeof document === 'undefined') return false;
  const el = document.querySelector<HTMLElement>(`#contenu [data-aide="texte"][data-aide-cle="${echapper(cle)}"]`);
  if (!el) return false;
  if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '-1');
  el.scrollIntoView?.({ block: 'center' });
  el.focus({ preventScroll: true });
  return true;
}
