import { Check, Lock } from 'lucide-react';
import { cn } from '../../lib/utils';
import { etatEtape, TEXTE_ETAT, type EtatEtape } from './etatsEtapes';

/**
 * Lot L6b (E5) : frise des etapes d'un parcours, commune a tous les parcours.
 *
 * Une etape est, au choix :
 * - validee (numero < etape la plus avancee) : pastille verte cochee, cliquable ;
 * - affichee (celle de l'ecran) : pastille or (etat actif), aria-current="step" ;
 * - atteinte (etape la plus avancee, quand l'ecran montre une etape anterieure) : cliquable ;
 * - verrouillee (au-dela) : cadenas, non cliquable (aucun saut d'etape non validee) ;
 * - bloquee (etape affichee en defaut) : pastille rouge.
 *
 * L'etat se lit aussi sans la couleur : icone (coche, cadenas) et texte de l'etat dans le
 * nom accessible de chaque pastille ("Etape 3 : Capital, validee").
 */
export interface EtapeFrise {
  numero: number;
  libelle: string;
}

interface Props {
  etapes: EtapeFrise[];
  /** Etape la plus avancee atteinte (cote serveur). */
  plusAvancee: number;
  /** Etape affichee a l'ecran (defaut : la plus avancee). */
  affichee?: number;
  bloquee?: boolean;
  /** Si fourni, les etapes atteintes (<= plus avancee) deviennent cliquables. */
  onNavigate?: (numero: number) => void;
  className?: string;
}

const PASTILLE: Record<EtatEtape, string> = {
  validee: 'border-success bg-success text-success-fg',
  affichee: 'border-accent bg-accent text-accent-fg shadow-glow-gold',
  atteinte: 'border-accent bg-accent-soft text-accent',
  verrouillee: 'border-border bg-bg-overlay text-fg-subtle',
  bloquee: 'border-danger bg-danger text-danger-fg',
};

const LIBELLE: Record<EtatEtape, string> = {
  validee: 'text-fg-muted',
  affichee: 'font-semibold text-heading',
  atteinte: 'text-fg',
  verrouillee: 'text-fg-subtle',
  bloquee: 'font-semibold text-danger',
};

export function FriseEtapes({ etapes, plusAvancee, affichee, bloquee, onNavigate, className }: Props) {
  const vue = affichee ?? plusAvancee;
  const total = etapes.length;
  const validees = Math.max(0, Math.min(plusAvancee - 1, total));
  const courante = etapes.find((e) => e.numero === vue);
  const position = courante ? etapes.indexOf(courante) + 1 : vue;

  return (
    <nav
      aria-label="Progression du parcours"
      className={cn('@container rounded-xl border border-border bg-bg-raised p-4 shadow-card', className)}
    >
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <p className="text-sm text-fg-muted">
          <span className="font-semibold text-heading">
            Étape {position} sur {total}
          </span>
          {courante && <span> : {courante.libelle}</span>}
        </p>
        <p className="text-xs text-fg-subtle" data-testid="frise-compteur">
          {validees}/{total} étapes validées
        </p>
      </div>
      <ol className="flex items-start">
        {etapes.map((e, idx) => {
          const etat = etatEtape(e.numero, plusAvancee, vue, bloquee);
          const cliquable = !!onNavigate && e.numero <= plusAvancee && e.numero !== vue;
          const nom = `Étape ${e.numero} : ${e.libelle}, ${TEXTE_ETAT[etat]}`;
          const titre =
            etat === 'verrouillee'
              ? `Étape ${e.numero} verrouillée : validez d’abord les étapes précédentes`
              : cliquable
                ? `Revenir à l’étape ${e.numero}`
                : undefined;
          const derniere = idx === etapes.length - 1;
          return (
            <li key={e.numero} className="relative flex min-w-0 flex-1 flex-col items-center" data-etat={etat}>
              {!derniere && (
                <span
                  aria-hidden="true"
                  className={cn(
                    'absolute left-1/2 top-4 h-0.5 w-full',
                    e.numero < plusAvancee ? 'bg-success' : 'bg-border',
                  )}
                />
              )}
              <button
                type="button"
                onClick={cliquable ? () => onNavigate?.(e.numero) : undefined}
                disabled={!cliquable}
                title={titre}
                aria-label={nom}
                aria-current={etat === 'affichee' || etat === 'bloquee' ? 'step' : undefined}
                className={cn(
                  'relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 text-xs font-semibold',
                  'transition-[box-shadow,background-color] duration-150 motion-reduce:transition-none',
                  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
                  PASTILLE[etat],
                  cliquable ? 'cursor-pointer hover:shadow-glow-gold' : 'cursor-default',
                  etat === 'verrouillee' && 'cursor-not-allowed',
                )}
              >
                {etat === 'validee' ? (
                  <Check className="h-4 w-4" aria-hidden="true" />
                ) : etat === 'verrouillee' || etat === 'bloquee' ? (
                  <Lock className="h-3.5 w-3.5" aria-hidden="true" />
                ) : (
                  e.numero
                )}
              </button>
              <span
                aria-hidden="true"
                className={cn(
                  'mt-1.5 hidden px-1 text-center text-xs leading-tight @3xl:block',
                  LIBELLE[etat],
                )}
              >
                {e.libelle}
              </span>
            </li>
          );
        })}
      </ol>
      <div
        role="progressbar"
        aria-label="Étapes validées"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={validees}
        aria-valuetext={`${validees} étapes validées sur ${total}`}
        className="mt-3 h-1 overflow-hidden rounded-full bg-bg-overlay"
      >
        <div
          className="h-full rounded-full bg-success transition-[width] duration-200 motion-reduce:transition-none"
          style={{ width: `${total > 0 ? Math.round((validees / total) * 100) : 0}%` }}
        />
      </div>
    </nav>
  );
}
