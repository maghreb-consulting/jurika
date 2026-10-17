import { useEffect, useRef, type KeyboardEvent, type MouseEvent } from 'react';

/**
 * Lot L6b (E1) : pilotage d'un <dialog> natif (modale et tiroir).
 *
 * - Ouverture par `showModal()` : couche superieure, reste de la page inerte, focus place
 *   dans la fenetre, Echap gere par le navigateur (evenement `cancel`).
 * - Navigateur sans `showModal` (ou jsdom) : attribut `open`, Echap gere au clavier.
 * - Clic sur le voile (le <dialog> lui-meme, pas son contenu) : fermeture, seulement si le
 *   clic a aussi COMMENCE sur le voile (une selection de texte qui deborde ne ferme rien).
 * - A la fermeture, le focus revient a l'element qui l'avait.
 *
 * La fenetre n'est montee que lorsqu'elle est ouverte (le crochet s'execute au montage).
 */
export function useDialogueNatif(onClose: () => void, fermerSurVoile = true) {
  const ref = useRef<HTMLDialogElement | null>(null);
  const departSurVoile = useRef(false);
  const fermer = useRef(onClose);
  useEffect(() => {
    fermer.current = onClose;
  });

  useEffect(() => {
    const dlg = ref.current;
    if (!dlg) return;
    const precedent = document.activeElement as HTMLElement | null;
    if (typeof dlg.showModal === 'function') {
      if (!dlg.open) dlg.showModal();
    } else {
      dlg.setAttribute('open', '');
    }
    const surAnnulation = (e: Event) => {
      e.preventDefault();
      fermer.current();
    };
    dlg.addEventListener('cancel', surAnnulation);
    return () => {
      dlg.removeEventListener('cancel', surAnnulation);
      if (typeof dlg.close === 'function' && dlg.open) dlg.close();
      if (precedent && typeof precedent.focus === 'function' && document.contains(precedent)) precedent.focus();
    };
  }, []);

  return {
    ref,
    onKeyDown: (e: KeyboardEvent<HTMLDialogElement>) => {
      if (e.key !== 'Escape') return;
      // Les evenements React remontent l'arbre des composants (portails compris) : une
      // fenetre imbriquee ne ferme pas sa parente.
      e.stopPropagation();
      if (typeof ref.current?.showModal !== 'function') {
        e.preventDefault();
        fermer.current();
      }
    },
    onMouseDown: (e: MouseEvent<HTMLDialogElement>) => {
      departSurVoile.current = e.target === e.currentTarget;
    },
    onClick: (e: MouseEvent<HTMLDialogElement>) => {
      if (fermerSurVoile && e.target === e.currentTarget && departSurVoile.current) fermer.current();
      departSurVoile.current = false;
    },
  };
}
