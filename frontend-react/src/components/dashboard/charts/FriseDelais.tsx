import { useId, useMemo, useState } from 'react';
import { cn } from '../../../lib/utils';

export interface PointDelai {
  /** Date du delai (AAAA-MM-JJ). */
  date: string;
  /** Ce qui tombe ce jour-la (demarche, date limite d'un ticket). */
  libelle: string;
  /** Precision (nom du dossier, reference du ticket). */
  detail?: string;
}

interface Props {
  points: PointDelai[];
  /** Nombre d'elements en retard (date passee), affiche dans une case a part ; null : inconnu. */
  enRetard?: number | null;
  /** Libelle de la case des retards ("tickets en retard", "dossiers en retard"). */
  libelleRetard?: string;
  /** Nom de la frise pour les lecteurs d'ecran. */
  legende: string;
  jours?: number;
  aujourdHui?: Date;
}

const JOURS_SEMAINE = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function intensite(n: number): string {
  if (n <= 0) return 'bg-bg-overlay text-fg-subtle';
  if (n === 1) return 'bg-info/20 text-heading';
  if (n === 2) return 'bg-info/40 text-heading';
  return 'bg-info text-info-fg';
}

/**
 * Lot L6b (E18) : frise des delais des 30 prochains jours, en calendrier (une case par jour,
 * semaines du lundi au dimanche). La teinte dit combien de delais tombent ce jour-la ; les
 * retards ont leur case a part. Une case se choisit (clic ou clavier) pour lire ce qui tombe ce
 * jour-la. Les delais sont ceux que le serveur rend (delais legaux des demarches, dates limites
 * des tickets) : aucune date n'est calculee ici. Liste equivalente pour les lecteurs d'ecran.
 */
export function FriseDelais({ points, enRetard = null, libelleRetard = 'en retard', legende, jours = 30, aujourdHui = new Date() }: Props) {
  const idDetail = useId();
  const debut = useMemo(() => new Date(aujourdHui.getFullYear(), aujourdHui.getMonth(), aujourdHui.getDate()), [aujourdHui]);
  const parJour = useMemo(() => {
    const m = new Map<string, PointDelai[]>();
    for (const p of points) {
      const k = p.date.slice(0, 10);
      m.set(k, [...(m.get(k) ?? []), p]);
    }
    return m;
  }, [points]);

  const cases = useMemo(() => {
    // Premiere ligne alignee sur le lundi de la semaine courante.
    const decalage = (debut.getDay() + 6) % 7;
    const l: Array<{ date: Date | null; index: number }> = [];
    for (let i = 0; i < decalage; i++) l.push({ date: null, index: -1 });
    for (let i = 0; i < jours; i++) l.push({ date: new Date(debut.getFullYear(), debut.getMonth(), debut.getDate() + i), index: i });
    while (l.length % 7 !== 0) l.push({ date: null, index: -1 });
    return l;
  }, [debut, jours]);

  const premierAvecDelai = cases.find((c) => c.date && parJour.has(iso(c.date)))?.date ?? null;
  const [choisi, setChoisi] = useState<string | null>(null);
  const jourChoisi = choisi ?? (premierAvecDelai ? iso(premierAvecDelai) : null);
  const detail = jourChoisi ? (parJour.get(jourChoisi) ?? []) : [];
  const dansFenetre = cases.filter((c) => c.date && parJour.has(iso(c.date)));
  const total = dansFenetre.reduce((n, c) => n + (parJour.get(iso(c.date!))?.length ?? 0), 0);

  return (
    <figure className="m-0">
      <div className="flex flex-col gap-3 sm:flex-row">
        {enRetard !== null && (
          <div
            className={cn(
              'flex shrink-0 flex-row items-center gap-2 self-start rounded-lg border px-3 py-2 sm:mt-5 sm:w-28 sm:flex-col sm:items-start',
              enRetard > 0 ? 'border-danger/40 bg-danger-soft' : 'border-border bg-bg-overlay',
            )}
          >
            <span className={cn('text-2xl font-semibold tabular-nums', enRetard > 0 ? 'text-danger' : 'text-fg-subtle')}>{enRetard}</span>
            <span className="text-xs leading-tight text-fg-muted">{libelleRetard}</span>
          </div>
        )}
        <div className="min-w-0 flex-1" aria-hidden="true">
          <div className="mb-1 grid grid-cols-7 gap-1">
            {JOURS_SEMAINE.map((j, i) => (
              <span key={i} className="text-center text-[0.65rem] font-medium uppercase text-fg-subtle">
                {j}
              </span>
            ))}
          </div>
          <div className="grid grid-cols-7 gap-1">
            {cases.map((c, i) => {
              if (!c.date) return <span key={i} className="h-9 rounded-md" />;
              const k = iso(c.date);
              const n = parJour.get(k)?.length ?? 0;
              const premierDuMois = c.date.getDate() === 1 || c.index === 0;
              return (
                <button
                  key={k}
                  type="button"
                  tabIndex={-1}
                  onClick={() => setChoisi(k)}
                  title={`${c.date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' })} : ${n} délai${n > 1 ? 's' : ''}`}
                  className={cn(
                    'relative flex h-9 flex-col items-center justify-center rounded-md text-[0.7rem] leading-none tabular-nums transition-shadow',
                    intensite(n),
                    c.index === 0 && 'ring-2 ring-accent ring-offset-1 ring-offset-bg-raised',
                    jourChoisi === k && n > 0 && 'outline outline-2 outline-offset-1 outline-heading',
                  )}
                >
                  <span className={cn(premierDuMois && 'font-semibold')}>
                    {premierDuMois ? c.date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' }) : c.date.getDate()}
                  </span>
                  {n > 0 && <span className="mt-0.5 font-semibold">{n}</span>}
                </button>
              );
            })}
          </div>
          <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-[0.7rem] text-fg-subtle">
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-sm ring-2 ring-accent" /> aujourd’hui
            </span>
            <span className="inline-flex items-center gap-1">
              0
              {[0, 1, 2, 3].map((n) => (
                <span key={n} className={cn('h-2.5 w-4 rounded-sm', intensite(n))} />
              ))}
              3 et plus
            </span>
          </div>
        </div>
      </div>
      <div id={idDetail} className="mt-3 min-h-[3.25rem] rounded-lg border border-border bg-bg-overlay/60 px-3 py-2 text-sm" aria-live="polite">
        {jourChoisi && detail.length > 0 ? (
          <>
            <p className="mb-1 text-xs font-semibold text-fg-muted">
              {new Date(`${jourChoisi}T12:00:00`).toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })}
            </p>
            <ul className="space-y-0.5">
              {detail.map((p, i) => (
                <li key={i} className="text-fg">
                  {p.detail && <span className="font-medium">{p.detail} · </span>}
                  <span className="text-fg-muted">{p.libelle}</span>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <p className="text-fg-muted">
            {total === 0 ? `Aucun délai dans les ${jours} prochains jours.` : 'Choisissez un jour coloré pour lire ce qui tombe ce jour-là.'}
          </p>
        )}
      </div>
      <figcaption className="sr-only">
        {legende} : {total} délai{total > 1 ? 's' : ''} dans les {jours} prochains jours
        {enRetard !== null ? `, ${enRetard} ${libelleRetard}` : ''}.
        <ul>
          {dansFenetre.map((c) => {
            const k = iso(c.date!);
            return (
              <li key={k}>
                {c.date!.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' })} :{' '}
                {(parJour.get(k) ?? []).map((p) => (p.detail ? `${p.detail}, ${p.libelle}` : p.libelle)).join(' ; ')}
              </li>
            );
          })}
        </ul>
      </figcaption>
    </figure>
  );
}
