import { Banknote, Coins, CreditCard, ScrollText } from 'lucide-react';
import { InfoBulle } from '../ui/Aide';
import { LIBELLE_MODE, type PaymentMethod } from '../../types/billing';

const MODES: { value: PaymentMethod; description: string; icon: typeof CreditCard }[] = [
  { value: 'CARD', description: 'Sur la page sécurisée du CMI ; acquis à la confirmation du CMI.', icon: CreditCard },
  { value: 'BANK_TRANSFER', description: 'Pris en compte quand JURIKA a constaté la réception du virement.', icon: Banknote },
  { value: 'CHEQUE', description: 'Pris en compte à la réception du chèque par JURIKA.', icon: ScrollText },
  { value: 'CASH', description: 'Règlement au siège de l’éditeur, contre reçu.', icon: Coins },
];

/**
 * Lot L7 (E5), RG-PAY-01 : quatre modes de paiement. La carte est desactivee, avec son motif, tant
 * que la liaison avec le CMI n'est pas configuree.
 */
export function ChoixModePaiement({ valeur, onChange, carteDisponible, carteMotif }: {
  valeur: PaymentMethod | null;
  onChange: (m: PaymentMethod) => void;
  carteDisponible: boolean;
  carteMotif?: string | null;
}) {
  return (
    <fieldset className="space-y-2" data-testid="choix-mode-paiement">
      <legend className="mb-1 flex items-center gap-1 text-sm font-medium text-fg">
        Mode de paiement
        <InfoBulle libelle="Aide : mode de paiement" texte="Carte : paiement immédiat sur la page du CMI. Virement, chèque ou espèces : JURIKA enregistre la réception, puis active ou prolonge votre abonnement." />
      </legend>
      {MODES.map((m) => {
        const desactive = m.value === 'CARD' && !carteDisponible;
        const Icone = m.icon;
        return (
          <label key={m.value}
            className={[
              'flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm transition-colors duration-150 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
              valeur === m.value ? 'border-accent bg-accent-soft' : 'border-border-input bg-bg-raised hover:border-fg-subtle',
              desactive ? 'cursor-not-allowed opacity-60' : '',
            ].join(' ')}>
            <input type="radio" name="mode-paiement" value={m.value} checked={valeur === m.value}
              disabled={desactive} onChange={() => onChange(m.value)} className="mt-1 accent-[var(--color-accent)]" />
            <Icone className="mt-0.5 h-4 w-4 flex-shrink-0 text-fg-muted" aria-hidden="true" />
            <span>
              <span className="font-medium text-fg">{LIBELLE_MODE[m.value]}</span>
              <span className="block text-xs text-fg-muted">
                {desactive ? (carteMotif || 'Paiement par carte indisponible.') : m.description}
              </span>
            </span>
          </label>
        );
      })}
    </fieldset>
  );
}
