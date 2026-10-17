import { CheckCircle2, Clock } from 'lucide-react';
import { FormulaireCmi } from './FormulaireCmi';
import { formatMadCents, LIBELLE_MODE, type PreparationPaiementVue } from '../../types/billing';

/**
 * Lot L7 (E5) : suite d'un paiement prepare. Carte : formulaire du CMI (soumis aussitot).
 * Virement, cheque, especes : instructions de reglement et rappel que JURIKA enregistre la
 * reception (RG-PAY-02) ; le cabinet ne confirme jamais lui-meme son paiement.
 */
export function SuitePaiement({ preparation }: { preparation: PreparationPaiementVue }) {
  const p = preparation.paiement;
  if (preparation.cmi) {
    return <FormulaireCmi cmi={preparation.cmi} />;
  }
  return (
    <div className="space-y-3 rounded-xl border border-border border-l-4 border-l-warning bg-bg-raised p-4 text-sm shadow-card" data-testid="suite-paiement-hors-ligne">
      <p className="flex items-start gap-2 font-medium text-fg">
        <Clock className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" aria-hidden="true" />
        {preparation.message}
      </p>
      <dl className="grid gap-x-4 gap-y-1 sm:grid-cols-[max-content_1fr]">
        <dt className="text-fg-muted">Mode</dt>
        <dd className="text-fg">{LIBELLE_MODE[p.mode]}</dd>
        {p.montants && (
          <>
            <dt className="text-fg-muted">Montant</dt>
            <dd className="text-fg">
              {formatMadCents(p.montants.ttcCentimes)} TTC ({formatMadCents(p.montants.htCentimes)} HT + TVA{' '}
              {formatMadCents(p.montants.tvaCentimes)})
            </dd>
          </>
        )}
        {preparation.instructions.map((i) => (
          <InstructionLigne key={i.libelle} libelle={i.libelle} valeur={i.valeur} />
        ))}
      </dl>
      <p className="flex items-start gap-2 text-xs text-fg-muted">
        <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" />
        Ensuite : JURIKA constate la réception et l’enregistre ; votre abonnement est alors activé ou
        prolongé et la facture est émise. Vous la retrouverez dans Facturation.
      </p>
    </div>
  );
}

function InstructionLigne({ libelle, valeur }: { libelle: string; valeur: string }) {
  return (
    <>
      <dt className="text-fg-muted">{libelle}</dt>
      <dd className="whitespace-pre-line font-medium text-fg">{valeur}</dd>
    </>
  );
}
