import type { Page } from '@playwright/test';

/**
 * Lot L6b (E4) : detecteurs de defauts visuels, executes DANS la page.
 *
 * `analyserDocument` est envoyee telle quelle au navigateur (page.evaluate) : elle ne doit
 * rien referencer hors d'elle-meme (aucune importation, aucune variable du module).
 *
 * 1. CHEVAUCHEMENT. Candidats : les elements visibles INTERACTIFS (lien, bouton, champ,
 *    role interactif) et les elements TEXTE (porteurs d'un noeud texte non vide ; leur boite
 *    est celle des lignes de texte, par Range.getClientRects, pas celle du bloc). Paires
 *    verifiees : interactif/interactif, texte/texte, texte/interactif. Exclusions : relation
 *    parent/enfant (l'un contient l'autre), un element dans une fenetre modale ouverte et
 *    l'autre hors d'elle, les couches prevues (selecteurs `couches`), les elements caches
 *    (display, visibility, opacite nulle, boite de moins de 2 px, hors de la zone visible de
 *    leurs ancetres qui coupent). L'analyse se fait a defilement nul : un en-tete collant ne
 *    doit rien recouvrir dans cette position. Tolerance : `tolerance` px (1 par defaut).
 * 2. DEBORDEMENT. Defilement horizontal de la page ; texte dont les lignes sortent de la
 *    boite de son element (debordement visible) ; texte coupe par un ancetre qui masque
 *    (overflow hidden ou clip) sans ellipsis (text-overflow) ni line-clamp. Un conteneur qui
 *    defile (overflow auto ou scroll) est un defilement prevu, pas un defaut.
 * 3. CONTRASTE (axe-core absent du depot, hors ligne) : couleur calculee du texte (opacite
 *    des ancetres comprise) contre le fond effectif (fonds des ancetres composes jusqu'au
 *    premier fond opaque ; un fond en image ou en degrade rend le texte "non evalue", compte a
 *    part). Seuils WCAG AA : 4,5:1, ou 3:1 pour un grand texte (24 px, ou 18,66 px gras).
 *    Exclus (exemptes par WCAG 1.4.3) : textes des composants desactives.
 */

export type TypeDefaut =
  | 'chevauchement'
  | 'debordement-page'
  | 'debordement-texte'
  | 'texte-coupe'
  | 'contraste';

export interface Defaut {
  type: TypeDefaut;
  /** Phrase lisible qui nomme le ou les elements. */
  message: string;
  elements: string[];
}

export interface OptionsAnalyse {
  tolerance?: number;
  /** Selecteurs des couches prevues (exclues du chevauchement), ex. region des toasts. */
  couches?: string[];
  /** Detecteurs a executer (tous par defaut). */
  detecteurs?: Array<'chevauchement' | 'debordement' | 'contraste'>;
}

export interface ResultatAnalyse {
  defauts: Defaut[];
  /** Nombre d'elements candidats examines par detecteur (preuve que l'analyse a porte). */
  examines: { chevauchement: number; debordement: number; contraste: number; contrasteNonEvalue: number };
}

export function analyserDocument(options: OptionsAnalyse): ResultatAnalyse {
  const tolerance = options.tolerance ?? 1;
  const couches = options.couches ?? [];
  const actifs = options.detecteurs ?? ['chevauchement', 'debordement', 'contraste'];
  const defauts: Defaut[] = [];
  const examines = { chevauchement: 0, debordement: 0, contraste: 0, contrasteNonEvalue: 0 };

  interface Boite {
    l: number;
    t: number;
    r: number;
    b: number;
  }

  const SELECTEUR_INTERACTIF = [
    'a[href]',
    'button',
    'input:not([type="hidden"])',
    'select',
    'textarea',
    'summary',
    '[role="button"]',
    '[role="link"]',
    '[role="tab"]',
    '[role="checkbox"]',
    '[role="radio"]',
    '[role="switch"]',
    '[role="menuitem"]',
    '[role="option"]',
    '[contenteditable="true"]',
  ].join(',');

  // ---------- Outils ----------

  function texteDirect(el: Element): string {
    let t = '';
    for (const n of Array.from(el.childNodes)) {
      if (n.nodeType === Node.TEXT_NODE) t += n.textContent ?? '';
    }
    return t.replace(/\s+/g, ' ').trim();
  }

  function nomLisible(el: Element): string {
    const tag = el.tagName.toLowerCase();
    let sel = tag;
    const id = el.getAttribute('id');
    const testid = el.getAttribute('data-testid');
    if (testid) sel += `[data-testid="${testid}"]`;
    else if (id && !/^(?:radix-|:r)/.test(id) && !/[:\s]/.test(id)) sel += `#${id}`;
    else {
      const role = el.getAttribute('role');
      if (role) sel += `[role="${role}"]`;
      const cls = (el.getAttribute('class') ?? '').split(/\s+/).filter((c) => c && !c.includes(':') && !c.includes('['));
      if (cls.length) sel += '.' + cls.slice(0, 2).join('.');
    }
    // Ancetre repere le plus proche (landmark ou testid) pour situer l'element.
    let situe = '';
    let p = el.parentElement;
    while (p && p !== document.body) {
      const ptest = p.getAttribute('data-testid');
      const ptag = p.tagName.toLowerCase();
      if (ptest) {
        situe = `[data-testid="${ptest}"] `;
        break;
      }
      if (['header', 'nav', 'main', 'aside', 'footer', 'dialog', 'form', 'table'].includes(ptag)) {
        const lab = p.getAttribute('aria-label');
        situe = `${ptag}${lab ? `[aria-label="${lab.slice(0, 30)}"]` : ''} `;
        break;
      }
      p = p.parentElement;
    }
    const libelle =
      el.getAttribute('aria-label') ??
      ((el as HTMLInputElement).placeholder || '') ??
      '';
    const texte = (libelle || (el.textContent ?? '')).replace(/\s+/g, ' ').trim().slice(0, 50);
    return `${situe}${sel}${texte ? ` "${texte}"` : ''}`;
  }

  const cacheStyle = new Map<Element, CSSStyleDeclaration>();
  function style(el: Element): CSSStyleDeclaration {
    let s = cacheStyle.get(el);
    if (!s) {
      s = getComputedStyle(el);
      cacheStyle.set(el, s);
    }
    return s;
  }

  function cache(el: Element): boolean {
    for (let e: Element | null = el; e; e = e.parentElement) {
      const s = style(e);
      if (s.display === 'none') return true;
      if (s.opacity === '0') return true;
      if (e.hasAttribute('hidden') && e.tagName !== 'DIALOG') return true;
      if (e.tagName === 'DIALOG' && !(e as HTMLDialogElement).open) return true;
      if (e.tagName === 'DETAILS' && e !== el && !(e as HTMLDetailsElement).open) {
        // Contenu d'un <details> ferme (hors <summary>) : invisible.
        const summary = e.querySelector(':scope > summary');
        if (!summary || !summary.contains(el)) return true;
      }
    }
    return style(el).visibility === 'hidden' || style(el).visibility === 'collapse';
  }

  /** Masque d'un element (clip / clip-path des lecteurs d'ecran, sr-only). */
  function masqueLecteurEcran(el: Element): boolean {
    for (let e: Element | null = el; e; e = e.parentElement) {
      const s = style(e);
      if (s.clip && s.clip !== 'auto' && /rect\(\s*0(px)?[ ,]+0(px)?[ ,]+0(px)?[ ,]+0(px)?\s*\)/.test(s.clip)) return true;
      if (s.clipPath && /inset\(\s*50%/.test(s.clipPath)) return true;
    }
    return false;
  }

  /** Zone visible : intersection avec les ancetres qui coupent (overflow != visible) et la page. */
  function zoneCoupe(el: Element, seulementMasquants: boolean): Boite | null {
    let z: Boite = { l: -Infinity, t: -Infinity, r: Infinity, b: Infinity };
    for (let e = el.parentElement; e && e !== document.documentElement; e = e.parentElement) {
      const s = style(e);
      const ox = s.overflowX;
      const oy = s.overflowY;
      const coupeX = seulementMasquants ? ox === 'hidden' || ox === 'clip' : ox !== 'visible';
      const coupeY = seulementMasquants ? oy === 'hidden' || oy === 'clip' : oy !== 'visible';
      if (coupeX || coupeY) {
        const r = e.getBoundingClientRect();
        z = {
          l: coupeX ? Math.max(z.l, r.left) : z.l,
          r: coupeX ? Math.min(z.r, r.right) : z.r,
          t: coupeY ? Math.max(z.t, r.top) : z.t,
          b: coupeY ? Math.min(z.b, r.bottom) : z.b,
        };
      }
      if (s.position === 'fixed') break;
    }
    return z.l < z.r && z.t < z.b ? z : null;
  }

  function inter(a: Boite, b: Boite): Boite | null {
    const l = Math.max(a.l, b.l);
    const r = Math.min(a.r, b.r);
    const t = Math.max(a.t, b.t);
    const bo = Math.min(a.b, b.b);
    return r > l && bo > t ? { l, t, r, b: bo } : null;
  }

  function boiteElement(el: Element): Boite {
    const r = el.getBoundingClientRect();
    return { l: r.left, t: r.top, r: r.right, b: r.bottom };
  }

  /** Boites des lignes des noeuds texte directs de l'element. */
  function boitesTexte(el: Element): Boite[] {
    const res: Boite[] = [];
    for (const n of Array.from(el.childNodes)) {
      if (n.nodeType !== Node.TEXT_NODE || !(n.textContent ?? '').trim()) continue;
      const range = document.createRange();
      range.selectNodeContents(n);
      for (const r of Array.from(range.getClientRects())) {
        if (r.width > 0.5 && r.height > 0.5) res.push({ l: r.left, t: r.top, r: r.right, b: r.bottom });
      }
    }
    return res;
  }

  /**
   * Lignes du texte ramenees a la hauteur de la ligne (line-height) : la zone que Range
   * renvoie est celle de la police (ascendante + descendante), plus haute que la ligne quand
   * l'interligne est serre ; deux lignes voisines s'y recouvrent sans que rien ne se voie.
   */
  function lignesTexte(el: Element): Boite[] {
    const s = style(el);
    const taille = parseFloat(s.fontSize) || 16;
    const lh = s.lineHeight === 'normal' ? taille * 1.2 : parseFloat(s.lineHeight) || taille * 1.2;
    return boitesTexte(el).map((b) => {
      const h = b.b - b.t;
      if (h <= lh) return b;
      const d = (h - lh) / 2;
      return { l: b.l, r: b.r, t: b.t + d, b: b.b - d };
    });
  }

  function union(bs: Boite[]): Boite | null {
    if (!bs.length) return null;
    return {
      l: Math.min(...bs.map((b) => b.l)),
      t: Math.min(...bs.map((b) => b.t)),
      r: Math.max(...bs.map((b) => b.r)),
      b: Math.max(...bs.map((b) => b.b)),
    };
  }

  function dansCouche(el: Element): boolean {
    return couches.some((sel) => {
      try {
        return !!el.closest(sel);
      } catch {
        return false;
      }
    });
  }

  /**
   * Couche ouverte au-dessus du contenu dont l'element fait partie : fenetre (dialog ouvert,
   * role dialog), popover ouvert, menu, liste d'options, infobulle, ou element en position
   * ABSOLUE avec un z-index d'au moins 20 (menu deroulant, panneau des notifications).
   * Une position FIXE n'en fait pas une couche : une bulle fixe (assistant) ne doit rien
   * recouvrir et reste verifiee (sauf selecteur declare dans `couches`).
   */
  function coucheDe(el: Element): Element | null {
    const ouverte = el.closest(
      'dialog[open], [role="dialog"], [role="alertdialog"], [role="menu"], [role="listbox"], [role="tooltip"]',
    );
    let positionnee: Element | null = null;
    for (let e: Element | null = el; e; e = e.parentElement) {
      if (e.matches(':popover-open')) {
        positionnee = e;
        break;
      }
      const st = style(e);
      if (st.position === 'absolute' && (parseInt(st.zIndex, 10) || 0) >= 20) {
        positionnee = e;
        break;
      }
    }
    if (!ouverte) return positionnee;
    if (!positionnee) return ouverte;
    return ouverte.contains(positionnee) ? positionnee : ouverte;
  }

  const tous = Array.from(document.body.querySelectorAll('*')).filter(
    (el) => !['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'svg', 'SVG'].includes(el.tagName) && !el.closest('svg'),
  );
  const visibles = tous.filter((el) => !cache(el) && !masqueLecteurEcran(el));

  // ---------- 1. Chevauchement ----------
  if (actifs.includes('chevauchement')) {
    interface Candidat {
      el: Element;
      genre: 'interactif' | 'texte';
      boite: Boite;
      /** Boites a comparer : la boite de l'interactif, ou chaque ligne du texte. */
      parties: Boite[];
      modale: Element | null;
    }
    const candidats: Candidat[] = [];
    const interactifs = new Set(visibles.filter((el) => el.matches(SELECTEUR_INTERACTIF)));
    for (const el of visibles) {
      if (dansCouche(el)) continue;
      const zone = zoneCoupe(el, false);
      if (!zone) continue;
      if (interactifs.has(el)) {
        const b = boiteElement(el);
        if (b.r - b.l < 2 || b.b - b.t < 2) continue;
        const v = inter(b, zone);
        if (v) candidats.push({ el, genre: 'interactif', boite: v, parties: [v], modale: coucheDe(el) });
        continue;
      }
      if (texteDirect(el)) {
        // Un element qui coupe son propre texte (truncate, line-clamp) n'en montre que sa boite.
        const st = style(el);
        const zoneSoi = st.overflowX !== 'visible' || st.overflowY !== 'visible' ? inter(zone, boiteElement(el)) : zone;
        if (!zoneSoi) continue;
        const parties = lignesTexte(el)
          .map((l) => inter(l, zoneSoi))
          .filter((l): l is Boite => !!l && l.r - l.l >= 2 && l.b - l.t >= 2);
        const v = union(parties);
        if (v) candidats.push({ el, genre: 'texte', boite: v, parties, modale: coucheDe(el) });
      }
    }
    examines.chevauchement = candidats.length;
    candidats.sort((a, b) => a.boite.l - b.boite.l);
    const vus = new Set<string>();
    for (let i = 0; i < candidats.length; i++) {
      const a = candidats[i];
      for (let j = i + 1; j < candidats.length; j++) {
        const b = candidats[j];
        if (b.boite.l >= a.boite.r - tolerance) break;
        if (a.modale !== b.modale) continue;
        if (a.el.contains(b.el) || b.el.contains(a.el)) continue;
        // Un texte dans un interactif (libelle d'un bouton) est un enfant : deja exclu ci-dessus.
        // Recouvrement le plus large entre une partie de l'un et une partie de l'autre (un
        // segment en ligne qui passe a la ligne n'occupe pas tout le rectangle de ses lignes).
        let ix = 0;
        let iy = 0;
        for (const pa of a.parties) {
          for (const pb of b.parties) {
            const x = Math.min(pa.r, pb.r) - Math.max(pa.l, pb.l);
            const y = Math.min(pa.b, pb.b) - Math.max(pa.t, pb.t);
            if (x > tolerance && y > tolerance && x * y > ix * iy) {
              ix = x;
              iy = y;
            }
          }
        }
        if (ix <= tolerance || iy <= tolerance) continue;
        // Un <label> et son champ : le libelle cliquable est voulu au-dessus du champ (case stylee).
        const lab = (x: Element, y: Element) =>
          x.tagName === 'LABEL' && ((x as HTMLLabelElement).control === y || x.contains(y));
        const labelDe = (x: Element, y: Element) => {
          const l = x.closest('label');
          return !!l && (l.contains(y) || (l as HTMLLabelElement).control === y);
        };
        if (lab(a.el, b.el) || lab(b.el, a.el) || labelDe(a.el, b.el) || labelDe(b.el, a.el)) continue;
        // Un bouton pose DANS un champ (afficher le mot de passe, effacer) : ornement voulu.
        const dedans = (x: Candidat, y: Candidat) =>
          x.el.matches('input, textarea, select') &&
          y.boite.l >= x.boite.l - tolerance &&
          y.boite.r <= x.boite.r + tolerance &&
          y.boite.t >= x.boite.t - tolerance &&
          y.boite.b <= x.boite.b + tolerance;
        if (dedans(a, b) || dedans(b, a)) continue;
        const na = nomLisible(a.el);
        const nb = nomLisible(b.el);
        const cle = [na, nb].sort().join('|');
        if (vus.has(cle)) continue;
        vus.add(cle);
        defauts.push({
          type: 'chevauchement',
          message: `${a.genre} ${na} recouvre ${b.genre} ${nb} (${Math.round(ix)} x ${Math.round(iy)} px)`,
          elements: [na, nb],
        });
      }
    }
  }

  // ---------- 2. Debordement ----------
  if (actifs.includes('debordement')) {
    const racine = document.documentElement;
    const largeurVue = racine.clientWidth;
    if (racine.scrollWidth > largeurVue + tolerance) {
      // Nomme les elements qui depassent l'ecran sans etre coupes par un ancetre (feuilles d'abord).
      const depasse = (el: Element) => {
        if (el.getBoundingClientRect().right <= largeurVue + tolerance) return false;
        const z = zoneCoupe(el, false);
        return !!z && z.r > largeurVue + tolerance;
      };
      const fautifsLarges = visibles
        .filter(depasse)
        .filter((el) => !Array.from(el.children).some(depasse))
        .slice(0, 5)
        .map(nomLisible);
      defauts.push({
        type: 'debordement-page',
        message: `la page defile horizontalement (${racine.scrollWidth} px pour ${largeurVue} px) ; depassent : ${fautifsLarges.join(' ; ') || 'non identifie'}`,
        elements: fautifsLarges,
      });
    }
    for (const el of visibles) {
      if (!texteDirect(el)) continue;
      if (el.matches('input, textarea, select')) continue;
      const lignes = boitesTexte(el);
      if (!lignes.length) continue;
      examines.debordement++;
      const s = style(el);
      const u = union(lignes);
      if (!u) continue;
      // (a) Texte sorti de la boite de son propre element (overflow visible).
      const b = boiteElement(el);
      const inline = s.display === 'inline' || s.display === 'contents';
      if (!inline && s.overflowX === 'visible' && (u.r > b.r + tolerance || u.l < b.l - tolerance)) {
        const nom = nomLisible(el);
        defauts.push({
          type: 'debordement-texte',
          message: `le texte de ${nom} deborde de sa boite de ${Math.round(Math.max(u.r - b.r, b.l - u.l))} px`,
          elements: [nom],
        });
        continue;
      }
      // (b) Texte coupe par un ancetre (ou l'element) qui masque, sans ellipsis ni line-clamp.
      let zone: Boite | null = null;
      {
        let z: Boite = { l: -Infinity, t: -Infinity, r: Infinity, b: Infinity };
        let coupeur: Element | null = null;
        for (let e: Element | null = el; e && e !== document.documentElement; e = e.parentElement) {
          const se = style(e);
          const mx = se.overflowX === 'hidden' || se.overflowX === 'clip';
          const my = se.overflowY === 'hidden' || se.overflowY === 'clip';
          // Un ancetre qui defile : ce qui est au-dela est atteignable, pas coupe.
          if (se.overflowX === 'auto' || se.overflowX === 'scroll' || se.overflowY === 'auto' || se.overflowY === 'scroll') break;
          if (mx || my) {
            const r = e.getBoundingClientRect();
            z = {
              l: mx ? Math.max(z.l, r.left) : z.l,
              r: mx ? Math.min(z.r, r.right) : z.r,
              t: my ? Math.max(z.t, r.top) : z.t,
              b: my ? Math.min(z.b, r.bottom) : z.b,
            };
            coupeur = coupeur ?? e;
          }
          if (se.position === 'fixed') break;
        }
        if (coupeur) zone = z;
        if (zone && coupeur) {
          const dedans = inter(u, zone);
          const coupeH = u.r > zone.r + tolerance || u.l < zone.l - tolerance;
          const coupeV = u.b > zone.b + tolerance + 1 || u.t < zone.t - tolerance - 1;
          if (dedans && (coupeH || coupeV)) {
            const ellipsis = [el, coupeur].some((x) => style(x).textOverflow === 'ellipsis');
            const clamp = [el, coupeur].some((x) => {
              const v = style(x).getPropertyValue('-webkit-line-clamp');
              return !!v && v !== 'none';
            });
            const voulu = (coupeH && !coupeV && ellipsis) || (coupeV && clamp);
            if (!voulu) {
              const nom = nomLisible(el);
              defauts.push({
                type: 'texte-coupe',
                message: `le texte de ${nom} est coupe (${coupeH ? 'en largeur' : 'en hauteur'}) par ${nomLisible(coupeur)} sans ellipsis`,
                elements: [nom, nomLisible(coupeur)],
              });
            }
          }
        }
      }
    }
  }

  // ---------- 3. Contraste ----------
  if (actifs.includes('contraste')) {
    const toile = document.createElement('canvas');
    toile.width = 1;
    toile.height = 1;
    const ctx = toile.getContext('2d', { willReadFrequently: true });
    const cacheCouleur = new Map<string, [number, number, number, number]>();
    function rgba(c: string): [number, number, number, number] {
      const k = cacheCouleur.get(c);
      if (k) return k;
      let res: [number, number, number, number] = [0, 0, 0, 0];
      if (c && c !== 'transparent' && ctx) {
        ctx.clearRect(0, 0, 1, 1);
        ctx.fillStyle = '#000';
        ctx.fillStyle = c;
        ctx.fillRect(0, 0, 1, 1);
        const d = ctx.getImageData(0, 0, 1, 1).data;
        res = [d[0], d[1], d[2], d[3] / 255];
      }
      cacheCouleur.set(c, res);
      return res;
    }
    function sur(dessus: [number, number, number, number], dessous: [number, number, number]): [number, number, number] {
      const a = dessus[3];
      return [0, 1, 2].map((i) => dessus[i] * a + dessous[i] * (1 - a)) as [number, number, number];
    }
    function lum(c: [number, number, number]): number {
      const v = c.map((x) => {
        const s = x / 255;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
      });
      return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }
    function ratio(a: [number, number, number], b: [number, number, number]): number {
      const la = lum(a);
      const lb = lum(b);
      return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }
    /** Fond effectif : couches des ancetres composees jusqu'au premier fond opaque. */
    function fond(el: Element): [number, number, number] | null {
      const pile: Array<[number, number, number, number]> = [];
      for (let e: Element | null = el; e; e = e.parentElement) {
        const s = style(e);
        if (s.backgroundImage && s.backgroundImage !== 'none') return null;
        const c = rgba(s.backgroundColor);
        const op = Number(s.opacity);
        if (c[3] > 0) pile.push([c[0], c[1], c[2], c[3] * (Number.isFinite(op) ? op : 1)]);
        if (c[3] >= 1 && op >= 1) break;
      }
      let base: [number, number, number] = [255, 255, 255];
      const racine = rgba(style(document.documentElement).backgroundColor);
      if (racine[3] > 0) base = sur(racine, base);
      for (let i = pile.length - 1; i >= 0; i--) base = sur(pile[i], base);
      return base;
    }
    for (const el of visibles) {
      if (!texteDirect(el)) continue;
      if (!boitesTexte(el).length && !el.matches('input, textarea, select')) continue;
      if (el.closest('[disabled], [aria-disabled="true"], fieldset:disabled')) continue;
      if ((el as HTMLButtonElement).disabled) continue;
      // Libelle d'un champ desactive (case, bouton radio) : composant inactif, exempte aussi.
      const libelle = el.closest('label') as HTMLLabelElement | null;
      if (libelle?.control && (libelle.control as HTMLInputElement).disabled) continue;
      const s = style(el);
      const coul = rgba(s.color);
      if (coul[3] === 0) continue;
      let opacite = 1;
      for (let e: Element | null = el; e; e = e.parentElement) opacite *= Number(style(e).opacity) || 1;
      const f = fond(el);
      if (!f) {
        examines.contrasteNonEvalue++;
        continue;
      }
      examines.contraste++;
      const texte = sur([coul[0], coul[1], coul[2], coul[3] * opacite], f);
      const r = ratio(texte, f);
      const taille = parseFloat(s.fontSize);
      const gras = Number(s.fontWeight) >= 700;
      const grand = taille >= 24 || (gras && taille >= 18.66);
      const seuil = grand ? 3 : 4.5;
      if (r + 0.005 < seuil) {
        const nom = nomLisible(el);
        defauts.push({
          type: 'contraste',
          message: `contraste ${r.toFixed(2)}:1 < ${seuil}:1 pour ${nom} (texte rgb(${texte.map(Math.round).join(',')}) sur rgb(${f.map(Math.round).join(',')}), ${taille}px${gras ? ' gras' : ''})`,
          elements: [nom],
        });
      }
    }
  }

  return { defauts, examines };
}

/** Execute l'analyse dans la page (defilement remis a zero d'abord). */
export async function analyser(page: Page, options: OptionsAnalyse = {}): Promise<ResultatAnalyse> {
  await page.evaluate(() => window.scrollTo(0, 0));
  return page.evaluate(analyserDocument, options);
}
