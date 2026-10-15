import { describe, expect, it } from 'vitest';
import type { Role } from '../../types/auth';
import {
  DELAI_SEQUENCE_MS,
  RACCOURCIS,
  creerLecteurRaccourcis,
  estChampDeSaisie,
  raccourcisDuRole,
  type ToucheLue,
} from '../raccourcis';

/**
 * Lot L6b (E11) : raccourcis clavier. Jamais pendant la saisie ni sous une fenetre modale ;
 * sequences "g puis x" bornees dans le temps ; offerts selon le role ; la liste affichee et
 * le lecteur ne font qu'un.
 */
function touche(key: string, extra: Partial<ToucheLue> = {}): ToucheLue {
  return { key, ctrlKey: false, metaKey: false, altKey: false, target: document.body, ...extra };
}

function lecteur(role: Role, modale = false) {
  let t = 1000;
  const lire = creerLecteurRaccourcis({ role: () => role, maintenant: () => t, modaleOuverte: () => modale });
  return {
    lire,
    avancer: (ms: number) => {
      t += ms;
    },
  };
}

describe('Raccourcis clavier', () => {
  it('chaque raccourci annonce a un role est reconnu par le lecteur (source unique)', () => {
    for (const role of ['EMPLOYE', 'SUPERVISEUR', 'CLIENT', 'SUPER_ADMIN'] as Role[]) {
      for (const r of raccourcisDuRole(role)) {
        const { lire } = lecteur(role);
        let action = null;
        if (r.combinaison) {
          action = lire(touche(r.touches[1].toLowerCase(), { ctrlKey: true }));
        } else {
          for (const k of r.touches) action = lire(touche(k));
        }
        expect(action, `${role} ${r.touches.join(' ')}`).toBe(r.action);
      }
    }
  });

  it('Ctrl+K et Cmd+K ouvrent la palette ; Alt ou Ctrl avec une autre lettre ne font rien', () => {
    const { lire } = lecteur('EMPLOYE');
    expect(lire(touche('k', { ctrlKey: true }))).toBe('palette');
    expect(lire(touche('k', { metaKey: true }))).toBe('palette');
    expect(lire(touche('K', { ctrlKey: true }))).toBe('palette');
    expect(lire(touche('k', { ctrlKey: true, altKey: true }))).toBeNull();
    expect(lire(touche('n', { ctrlKey: true }))).toBeNull();
    expect(lire(touche('n', { altKey: true }))).toBeNull();
  });

  it('g puis t mene aux tickets dans le delai ; au-dela, plus rien', () => {
    const a = lecteur('EMPLOYE');
    expect(a.lire(touche('g'))).toBeNull();
    a.avancer(DELAI_SEQUENCE_MS - 1);
    expect(a.lire(touche('t'))).toBe('aller-tickets');

    const b = lecteur('EMPLOYE');
    b.lire(touche('g'));
    b.avancer(DELAI_SEQUENCE_MS + 1);
    expect(b.lire(touche('t'))).toBeNull();
  });

  it('n cree un ticket (cabinet) ; ? ouvre la liste ; une majuscule ou une repetition ne declenchent rien', () => {
    const { lire } = lecteur('SUPERVISEUR');
    expect(lire(touche('n'))).toBe('nouveau-ticket');
    expect(lire(touche('N'))).toBeNull();
    expect(lire(touche('n', { repeat: true }))).toBeNull();
    expect(lire(touche('?'))).toBe('raccourcis');
  });

  it('le client n a ni ticket ni calendrier ; le super-admin ni ticket ni dossier', () => {
    const client = lecteur('CLIENT');
    expect(client.lire(touche('n'))).toBeNull();
    client.lire(touche('g'));
    expect(client.lire(touche('t'))).toBeNull();
    client.lire(touche('g'));
    expect(client.lire(touche('d'))).toBe('aller-dossiers');

    const admin = lecteur('SUPER_ADMIN');
    admin.lire(touche('g'));
    expect(admin.lire(touche('d'))).toBeNull();
    admin.lire(touche('g'));
    expect(admin.lire(touche('a'))).toBe('aller-accueil');
    expect(raccourcisDuRole('SUPER_ADMIN').map((r) => r.action)).toEqual(['palette', 'raccourcis', 'aller-accueil']);
  });

  it('jamais pendant la saisie dans un champ, meme Ctrl+K', () => {
    const { lire } = lecteur('EMPLOYE');
    const champs: HTMLElement[] = [
      document.createElement('input'),
      document.createElement('textarea'),
      document.createElement('select'),
    ];
    const editable = document.createElement('div');
    editable.setAttribute('contenteditable', 'true');
    const dansCombobox = document.createElement('span');
    const combobox = document.createElement('div');
    combobox.setAttribute('role', 'combobox');
    combobox.appendChild(dansCombobox);
    champs.push(editable, dansCombobox);
    for (const c of champs) {
      expect(estChampDeSaisie(c), c.outerHTML).toBe(true);
      expect(lire(touche('n', { target: c }))).toBeNull();
      expect(lire(touche('?', { target: c }))).toBeNull();
      expect(lire(touche('k', { target: c, ctrlKey: true }))).toBeNull();
    }
    // Une case a cocher ou un bouton ne recoivent pas de saisie.
    const caseACocher = document.createElement('input');
    caseACocher.type = 'checkbox';
    expect(estChampDeSaisie(caseACocher)).toBe(false);
    expect(lire(touche('n', { target: caseACocher }))).toBe('nouveau-ticket');
  });

  it('un g tape dans un champ n amorce pas de sequence', () => {
    const { lire } = lecteur('EMPLOYE');
    const champ = document.createElement('input');
    lire(touche('g'));
    lire(touche('x', { target: champ }));
    expect(lire(touche('t'))).toBeNull();
  });

  it('rien quand une fenetre modale est ouverte', () => {
    const { lire } = lecteur('EMPLOYE', true);
    expect(lire(touche('k', { ctrlKey: true }))).toBeNull();
    expect(lire(touche('n'))).toBeNull();
    expect(lire(touche('?'))).toBeNull();
  });

  it('les descriptions sont redigees (aucune vide) et les touches uniques par role', () => {
    for (const r of RACCOURCIS) expect(r.description.length).toBeGreaterThan(10);
    for (const role of ['EMPLOYE', 'SUPERVISEUR', 'CLIENT', 'SUPER_ADMIN'] as Role[]) {
      const cles = raccourcisDuRole(role).map((r) => r.touches.join(' '));
      expect(new Set(cles).size).toBe(cles.length);
    }
  });
});
