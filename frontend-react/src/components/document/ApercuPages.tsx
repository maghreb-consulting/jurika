import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileText, Loader2, X } from 'lucide-react';
import { TexteAide } from '../ui/Aide';
import { extractError } from '../../lib/api';
import { useAuthStore } from '../../store/authStore';
import type { ApercuPages } from '../../types/apercu';

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : apercu d'un document en AFFICHAGE seulement.
 * Les pages sont des images produites par le serveur ; pendant l'essai, la mention
 * « VERSION D'ÉVALUATION — SANS VALEUR JURIDIQUE » y est incrustee. Aucun fichier n'est recu,
 * aucun bouton de telechargement, d'enregistrement ou d'impression n'est propose.
 */

/** Aide de l'essai : pourquoi l'apercu seulement, et comment souscrire. */
export function AideApercuEssai() {
  const role = useAuthStore((s) => s.user?.role);
  return (
    <TexteAide cle="apercu-essai" titre="Essai gratuit : documents en aperçu seulement">
      <p>
        Pendant l’essai gratuit, les documents se consultent dans la plateforme, en aperçu : aucun
        téléchargement, enregistrement ni impression n’est permis, et chaque page porte la mention
        « VERSION D’ÉVALUATION — SANS VALEUR JURIDIQUE ».
      </p>
      <p className="mt-1">
        {role === 'SUPERVISEUR' ? (
          <>
            Pour télécharger les actes sans mention,{' '}
            <Link to="/app/billing" className="font-medium text-accent hover:underline">
              souscrivez un forfait
            </Link>{' '}
            : tout le travail du cabinet est conservé et les actes régénérés ne portent plus la mention.
          </>
        ) : (
          'Pour télécharger les actes sans mention, le superviseur du cabinet souscrit un forfait : tout le travail est conservé et les actes régénérés ne portent plus la mention.'
        )}
      </p>
    </TexteAide>
  );
}

/** Les pages de l'apercu, l'une sous l'autre. */
export function ApercuPagesView({ apercu, titre }: { apercu: ApercuPages; titre: string }) {
  return (
    <div className="space-y-4" data-testid="apercu-pages">
      {apercu.pages.map((p) => (
        <img
          key={p.numero}
          src={p.image}
          width={p.largeur}
          height={p.hauteur}
          alt={`${titre} — page ${p.numero} sur ${apercu.nombrePages}`}
          draggable={false}
          onContextMenu={(e) => e.preventDefault()}
          className="mx-auto block h-auto w-full max-w-3xl select-none rounded border border-border bg-white shadow-sm"
        />
      ))}
      {apercu.tronque && (
        <p className="text-center text-xs text-fg-subtle">
          Seules les {apercu.pages.length} premières pages sur {apercu.nombrePages} sont affichées dans l’aperçu.
        </p>
      )}
    </div>
  );
}

/** Charge et affiche un apercu en pages (aide de l'essai quand la mention est incrustee). */
export function ApercuPagesChargees({
  charger,
  cle,
  titre,
}: {
  charger: () => Promise<ApercuPages>;
  /** Change quand le document change : l'apercu est recharge. */
  cle: string;
  titre: string;
}) {
  const [resultat, setResultat] = useState<{ cle: string; apercu: ApercuPages | null; erreur: string | null } | null>(null);
  const chargerRef = useRef(charger);
  useEffect(() => {
    chargerRef.current = charger;
  });
  useEffect(() => {
    let annule = false;
    chargerRef.current()
      .then((a) => { if (!annule) setResultat({ cle, apercu: a, erreur: null }); })
      .catch((e) => { if (!annule) setResultat({ cle, apercu: null, erreur: extractError(e).message }); });
    return () => {
      annule = true;
    };
  }, [cle]);
  const courant = resultat && resultat.cle === cle ? resultat : null;
  if (!courant) {
    return (
      <div className="flex flex-col items-center gap-2 py-10 text-fg-subtle">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
        <p className="text-sm">Préparation de l’aperçu…</p>
      </div>
    );
  }
  if (courant.erreur || !courant.apercu) {
    return (
      <div role="alert" className="mx-auto max-w-md rounded-lg border border-danger/40 bg-danger/10 px-4 py-3 text-center text-sm text-danger">
        <p className="font-semibold">Aperçu indisponible</p>
        <p className="mt-1 text-xs">{courant.erreur}</p>
      </div>
    );
  }
  return (
    <div className="space-y-3">
      {courant.apercu.filigrane && <AideApercuEssai />}
      <ApercuPagesView apercu={courant.apercu} titre={titre} />
    </div>
  );
}

/** Fenetre d'apercu en pages : affichage seulement, sans telechargement ni impression. */
export function ApercuPagesModal({
  open,
  cle,
  titre,
  charger,
  onClose,
}: {
  open: boolean;
  cle: string | null;
  titre: string;
  charger: () => Promise<ApercuPages>;
  onClose: () => void;
}) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open || !cle) return null;
  return (
    <div
      className="fixed inset-0 z-50 flex flex-col bg-fg/60 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label={`Aperçu de ${titre}`}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="m-4 flex flex-1 flex-col overflow-hidden rounded-xl bg-bg-raised shadow-2xl md:m-6">
        <header className="flex items-center justify-between border-b border-border px-5 py-3">
          <div className="flex min-w-0 items-center gap-2">
            <FileText className="h-5 w-5 shrink-0 text-accent" />
            <h3 className="truncate text-sm font-semibold text-fg">{titre}</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg-muted"
            aria-label="Fermer l’aperçu"
          >
            <X className="h-5 w-5" />
          </button>
        </header>
        <div className="flex-1 overflow-auto bg-bg-overlay p-4">
          <ApercuPagesChargees cle={cle} titre={titre} charger={charger} />
        </div>
      </div>
    </div>
  );
}
