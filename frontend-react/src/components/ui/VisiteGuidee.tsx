import { useEffect, useId, useRef, useState, useSyncExternalStore, type CSSProperties, type KeyboardEvent } from 'react';
import { X } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { useAidesStore } from '../../store/aidesStore';
import { cleVisite, etapesVisite, type EtapeVisite } from './visiteEtapes';

/**
 * Lot L6 (E6, CDC 15.4) : premiere visite guidee, une par role.
 *
 * Elle s'ouvre a la premiere connexion d'un role, des que les aides masquees
 * du serveur sont chargees (jamais avant : pas d'ouverture intempestive, ni en
 * test sans serveur). Fermer ou terminer la visite la masque, comme une aide
 * (cle "visite-<role>", memorisee cote serveur) ; "Revoir la visite guidee"
 * la reaffiche.
 *
 * Accessibilite : boite de dialogue modale (role="dialog", aria-modal), focus
 * place sur la boite a chaque etape et rendu a l'element d'origine a la
 * fermeture, Echap ferme, fleches gauche et droite naviguent, la tabulation
 * reste dans la boite. Les cibles sont des attributs `data-visite` de la mise
 * en page commune ; une cible absente ou invisible donne une etape centree.
 */

/** Rafraichit le placement au redimensionnement, au defilement et apres le montage. */
let version = 0;
function abonner(rappel: () => void): () => void {
  const notifier = () => {
    version++;
    rappel();
  };
  window.addEventListener('resize', notifier);
  window.addEventListener('scroll', notifier, true);
  const image = window.requestAnimationFrame(notifier);
  return () => {
    window.removeEventListener('resize', notifier);
    window.removeEventListener('scroll', notifier, true);
    window.cancelAnimationFrame(image);
  };
}
const lireVersion = () => version;

interface Zone {
  top: number;
  left: number;
  width: number;
  height: number;
}

/** Zone visible de la cible : l'element marque, ou son premier descendant visible (bulle fixe). */
function zoneCible(cible: string | undefined): Zone | null {
  if (!cible || typeof document === 'undefined') return null;
  const el = document.querySelector<HTMLElement>(`[data-visite="${cible}"]`);
  if (!el) return null;
  const candidats: Element[] = [el, ...Array.from(el.querySelectorAll('*'))];
  for (const c of candidats) {
    const r = c.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) return { top: r.top, left: r.left, width: r.width, height: r.height };
  }
  return null;
}

const LARGEUR = 340;
const MARGE = 12;

function placement(zone: Zone | null): CSSProperties {
  if (!zone) {
    return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)', width: LARGEUR };
  }
  const largeurEcran = window.innerWidth;
  const hauteurEcran = window.innerHeight;
  const left = Math.min(Math.max(MARGE, zone.left + zone.width / 2 - LARGEUR / 2), Math.max(MARGE, largeurEcran - LARGEUR - MARGE));
  const dessous = zone.top + zone.height + MARGE;
  if (dessous + 200 <= hauteurEcran) {
    return { top: dessous, left, width: LARGEUR };
  }
  return { bottom: Math.max(MARGE, hauteurEcran - zone.top + MARGE), left, width: LARGEUR };
}

export function VisiteGuidee() {
  const role = useAuthStore((s) => s.user?.role);
  const chargee = useAidesStore((s) => s.chargee);
  const cle = role ? cleVisite(role) : null;
  const masquee = useAidesStore((s) => (cle ? s.masquees.has(cle) : true));
  const ouverte = Boolean(role && cle && chargee && !masquee);
  if (!ouverte || !role || !cle) return null;
  // Cle de remontage : chaque ouverture repart de la premiere etape.
  return <Visite key={cle} cle={cle} etapes={etapesVisite(role)} />;
}

function Visite({ cle, etapes }: { cle: string; etapes: EtapeVisite[] }) {
  const masquer = useAidesStore((s) => s.masquer);
  const [index, setIndex] = useState(0);
  const titreId = useId();
  const texteId = useId();
  const boite = useRef<HTMLDivElement>(null);
  useSyncExternalStore(abonner, lireVersion, lireVersion);

  // Declare en premier : retient l'element focalise avant que la boite prenne le focus.
  useEffect(() => {
    const precedent = document.activeElement;
    return () => {
      if (precedent instanceof HTMLElement && precedent.isConnected) precedent.focus();
    };
  }, []);

  useEffect(() => {
    boite.current?.focus();
  }, [index]);

  if (etapes.length === 0) return null;
  const etape = etapes[Math.min(index, etapes.length - 1)];
  const derniere = index >= etapes.length - 1;
  const zone = zoneCible(etape.cible);

  const fermer = () => masquer(cle);
  const suivante = () => (derniere ? fermer() : setIndex((i) => i + 1));
  const precedente = () => setIndex((i) => Math.max(0, i - 1));

  function auClavier(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Escape') {
      e.preventDefault();
      fermer();
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      suivante();
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      precedente();
    } else if (e.key === 'Tab' && boite.current) {
      const focalisables = Array.from(
        boite.current.querySelectorAll<HTMLElement>('button:not([disabled])'),
      );
      if (focalisables.length === 0) return;
      const premier = focalisables[0];
      const dernier = focalisables[focalisables.length - 1];
      const actif = document.activeElement;
      if (e.shiftKey && (actif === premier || actif === boite.current)) {
        e.preventDefault();
        dernier.focus();
      } else if (!e.shiftKey && actif === dernier) {
        e.preventDefault();
        premier.focus();
      }
    }
  }

  return (
    <div className="fixed inset-0 z-[60]" data-testid="visite-guidee">
      {/* Voile : bloque l'ecran pendant la visite ; un clic ne ferme pas (Echap ou "Fermer"). */}
      <div className={`absolute inset-0 ${zone ? '' : 'bg-scrim/45'}`} aria-hidden="true" />
      {zone && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute rounded-lg ring-2 ring-accent ring-offset-2 ring-offset-transparent"
          style={{ top: zone.top - 4, left: zone.left - 4, width: zone.width + 8, height: zone.height + 8, boxShadow: '0 0 0 9999px color-mix(in srgb, var(--jeton-scrim) 45%, transparent)' }}
        />
      )}
      <div
        ref={boite}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titreId}
        aria-describedby={texteId}
        tabIndex={-1}
        onKeyDown={auClavier}
        className="absolute max-w-[calc(100vw-24px)] rounded-xl border border-border-hi bg-bg-raised p-5 text-fg shadow-card-lifted focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        style={placement(zone)}
      >
        <button
          type="button"
          onClick={fermer}
          aria-label="Fermer la visite guidée"
          title="Fermer la visite guidée (Échap)"
          className="absolute right-3 top-3 rounded p-1 text-fg-subtle hover:text-fg focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
        <p className="text-[11px] font-medium uppercase tracking-wide text-accent">
          Visite guidée · étape {index + 1} sur {etapes.length}
        </p>
        <h2 id={titreId} className="mt-1 pr-6 font-heading text-lg font-semibold text-fg">
          {etape.titre}
        </h2>
        <p id={texteId} className="mt-2 text-sm leading-relaxed text-fg-muted">
          {etape.texte}
        </p>
        <div className="mt-4 flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={fermer}
            className="text-xs text-fg-subtle underline hover:text-fg focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          >
            Passer la visite
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={precedente}
              disabled={index === 0}
              className="rounded-md border border-border-hi px-3 py-1.5 text-sm text-fg-muted transition hover:bg-bg-overlay disabled:opacity-40 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            >
              Précédent
            </button>
            <button
              type="button"
              onClick={suivante}
              className="rounded-md bg-accent px-3 py-1.5 text-sm font-semibold text-bg transition hover:bg-accent-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            >
              {derniere ? 'Terminer' : 'Suivant'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
