import { useEffect, useState } from 'react';
import { Check, Minus } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { InfoBulle } from '../ui/Aide';
import { FONCTIONS_COMMUNES, GRILLE_CDC, lignesQuotas } from '../../lib/forfaits';
import { formatMad, type BillingPeriod, type PricingTier } from '../../types/billing';

export interface PlanPickerProps {
  period: BillingPeriod;
  onPeriodChange: (p: BillingPeriod) => void;
  /** Forfait actuel du cabinet (marque "Votre forfait"). */
  forfaitActuel?: string | null;
  /** Choix d'un forfait (Sur Devis compris : a l'hote de proposer la demande de devis). */
  onChoisir: (tier: PricingTier) => void;
  /** Libelle du bouton d'un forfait. */
  libelleAction?: (tier: PricingTier) => string;
  /** Prefixe des identifiants de test (plan-essentiel, plan-period-yearly...). */
  testIdPrefix?: string;
  desactive?: boolean;
}

/**
 * Lot L7 (E5) : grille des forfaits du cahier des charges (4.3 et 4.5). Prix HORS TAXES ; le
 * forfait est souscrit pour un an, regle en une fois (annuel) ou en douze mensualites (mensuel).
 * La grille est lue sur /public/pricing (catalogue du serveur) ; la grille de secours du CDC sert
 * pendant le premier affichage.
 */
export function PlanPicker({ period, onPeriodChange, forfaitActuel, onChoisir, libelleAction,
  testIdPrefix = 'plan', desactive = false }: PlanPickerProps) {
  const [tiers, setTiers] = useState<PricingTier[]>(GRILLE_CDC);

  useEffect(() => {
    let annule = false;
    billingService.getPricing()
      .then((res) => { if (!annule && res?.tiers?.length) setTiers(res.tiers); })
      .catch(() => { /* grille de secours du CDC */ });
    return () => { annule = true; };
  }, []);

  const annuel = period === 'yearly';

  return (
    <div data-testid={`${testIdPrefix}-picker`}>
      <div className="mb-6 flex flex-wrap items-center justify-center gap-2">
        <div role="group" aria-label="Rythme de paiement"
          className="inline-flex items-center rounded-full border border-border bg-bg-raised p-1">
          {(['monthly', 'yearly'] as BillingPeriod[]).map((p) => (
            <button key={p} type="button" onClick={() => onPeriodChange(p)} aria-pressed={period === p}
              data-testid={`${testIdPrefix}-period-${p}`}
              className={['rounded-full px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent',
                period === p ? 'bg-accent text-bg' : 'text-fg-subtle hover:text-fg'].join(' ')}>
              {p === 'monthly' ? 'Paiement mensuel' : 'Paiement annuel'}
            </button>
          ))}
        </div>
        <InfoBulle texte="Le forfait est souscrit pour un an. Paiement annuel : un seul règlement par an. Paiement mensuel : douze mensualités égales au dixième du prix annuel ; le paiement annuel revient donc à deux mois offerts. Prix hors taxes." />
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {tiers.map((t) => {
          const actuel = forfaitActuel === t.code;
          const prix = annuel ? t.yearly?.priceMad : t.monthly?.priceMad;
          return (
            <div key={t.code} data-testid={`${testIdPrefix}-${t.code}`}
              className={['flex flex-col rounded-2xl border-2 bg-bg-raised p-5',
                actuel ? 'border-accent' : 'border-border'].join(' ')}>
              <div className="flex items-baseline justify-between gap-2">
                <h3 className="font-heading text-xl font-semibold text-fg">{t.label}</h3>
                {actuel && <span className="rounded-full bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">Votre forfait</span>}
              </div>
              <div className="my-3 min-h-[4rem]">
                {t.quoteOnly || prix === undefined ? (
                  <>
                    <p className="text-lg font-semibold text-fg">Selon devis</p>
                    <p className="text-xs text-fg-subtle">Calculé selon l’usage réel ; devis établi par JURIKA.</p>
                  </>
                ) : (
                  <>
                    <p className="text-2xl font-bold text-fg">
                      {formatMad(prix)} <span className="text-sm font-normal text-fg-subtle">HT / {annuel ? 'an' : 'mois'}</span>
                    </p>
                    <p className="text-xs text-fg-subtle">
                      {annuel
                        ? `ou ${formatMad(t.monthly?.priceMad ?? 0)} HT par mois en paiement mensuel`
                        : `soit ${formatMad(t.totalAnnuelEnMensuelMad ?? 0)} HT sur un an ; ${formatMad(t.yearly?.priceMad ?? 0)} HT en paiement annuel`}
                    </p>
                  </>
                )}
              </div>
              <ul className="mb-3 space-y-1 text-sm text-fg">
                {lignesQuotas(t).map((l) => (
                  <li key={l} className="flex items-start gap-2"><Check className="mt-0.5 h-4 w-4 flex-shrink-0 text-success" aria-hidden="true" />{l}</li>
                ))}
              </ul>
              <ul className="mb-4 flex-1 space-y-1 text-xs">
                {FONCTIONS_COMMUNES.map((f) => (
                  <li key={f} className="flex items-start gap-2 text-fg-muted"><Check className="mt-0.5 h-3.5 w-3.5 flex-shrink-0 text-success" aria-hidden="true" />{f}</li>
                ))}
                {t.fonctions.map((f) => (
                  <li key={f.code} className={['flex items-start gap-2', f.incluse ? 'text-fg-muted' : 'text-fg-subtle line-through'].join(' ')}>
                    {f.incluse
                      ? <Check className="mt-0.5 h-3.5 w-3.5 flex-shrink-0 text-success" aria-hidden="true" />
                      : <Minus className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" />}
                    <span>{f.libelle}{f.incluse ? '' : ' (non incluse)'}</span>
                  </li>
                ))}
              </ul>
              <button type="button" disabled={desactive} onClick={() => onChoisir(t)}
                data-testid={`${testIdPrefix}-choisir-${t.code}`}
                className="mt-auto rounded-lg border border-accent px-4 py-2 text-sm font-semibold text-accent hover:bg-accent hover:text-bg disabled:cursor-not-allowed disabled:opacity-60">
                {libelleAction ? libelleAction(t) : t.quoteOnly ? 'Demander un devis' : `Choisir ${t.label}`}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
