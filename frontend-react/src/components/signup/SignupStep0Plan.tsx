import { useEffect, useState } from 'react';
import { PlanPicker } from '../billing/PlanPicker';
import { TexteAide } from '../ui/Aide';
import { demanderDevis } from '../../lib/demandeDevis';
import type { BillingPeriod, PlanCode } from '../../types/billing';
import { SIGNUP_PERIOD_STORAGE_KEY, lireRythmeInscription } from '../../lib/forfaits';

export type { PlanCode };

interface Props {
  initial?: PlanCode;
  onNext: (plan: PlanCode) => void;
}

/**
 * Premiere etape de l'inscription payante : choix du forfait et du rythme (lot L7 : grille du
 * CDC 4.3). Sur Devis ne se souscrit pas en ligne : le choisir conduit a la demande de devis
 * (RG-SOU-01, contact JURIKA).
 */
export function SignupStep0Plan({ onNext }: Props) {
  const [period, setPeriod] = useState<BillingPeriod>(() => lireRythmeInscription() ?? 'yearly');
  const [noteDevis, setNoteDevis] = useState<string | null>(null);

  useEffect(() => {
    try { sessionStorage.setItem(SIGNUP_PERIOD_STORAGE_KEY, period); }
    catch { /* stockage indisponible : le rythme sera redemande au paiement */ }
  }, [period]);

  return (
    <div className="mx-auto max-w-6xl px-4 py-8" data-testid="signup-step-plan">
      <div className="mb-6 text-center">
        <h2 className="font-heading text-3xl font-semibold text-fg">Choisissez votre forfait</h2>
        <p className="mt-2 text-sm text-fg-subtle">
          Prix hors taxes. Le forfait est souscrit pour un an, réglé en une fois ou chaque mois.
        </p>
      </div>
      <div className="mx-auto mb-6 max-w-3xl">
        <TexteAide cle="inscription-forfait" titre="Comment choisir ?">
          <p>
            Comparez le nombre de dossiers ouverts par mois, les accès employés et le stockage. Le forfait
            Sur Devis (plus de 100 dossiers par mois) s’établit avec JURIKA : choisissez-le pour demander un devis.
            Ensuite : vos informations, la sécurité du compte, puis le paiement.
          </p>
        </TexteAide>
      </div>
      {noteDevis && <p role="status" className="rounded-lg border border-border bg-bg-overlay p-3 text-sm text-fg">{noteDevis}</p>}
      <PlanPicker
        period={period}
        onPeriodChange={setPeriod}
        testIdPrefix="signup-plan"
        libelleAction={(t) => (t.quoteOnly ? 'Demander un devis' : `Choisir ${t.label}`)}
        onChoisir={(t) => {
          if (t.quoteOnly) {
            setNoteDevis(demanderDevis());
            return;
          }
          onNext(t.code);
        }}
      />
      <p className="mt-8 text-center text-xs text-fg-subtle">
        Vous pourrez changer de forfait depuis votre espace de facturation.
      </p>
    </div>
  );
}
