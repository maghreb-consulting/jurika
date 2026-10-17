import { useEffect, useRef } from 'react';
import { Button, Spinner } from '../ui/Button';
import type { CmiFormulaireVue } from '../../types/billing';

/**
 * Lot L7 (E5), RG-PAY-04 : formulaire POST auto-soumis vers la page de paiement hebergee du CMI.
 * La carte est saisie chez le CMI : JURIKA ne la voit ni ne la stocke. Le paiement n'est acquis
 * qu'au retour serveur signe du CMI, jamais au retour du navigateur.
 *
 * `autoSoumettre` (vrai par defaut) soumet le formulaire des l'affichage ; un bouton reste
 * disponible si le navigateur bloque la soumission.
 */
export function FormulaireCmi({ cmi, autoSoumettre = true }: { cmi: CmiFormulaireVue; autoSoumettre?: boolean }) {
  const ref = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (autoSoumettre) ref.current?.submit();
  }, [autoSoumettre]);

  return (
    <form ref={ref} method="POST" action={cmi.url} data-testid="formulaire-cmi"
      className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg shadow-card">
      {Object.entries(cmi.champs).map(([nom, valeur]) => (
        <input key={nom} type="hidden" name={nom} value={valeur} />
      ))}
      <p className="flex items-center gap-2">
        <Spinner className="text-fg-muted" />
        Redirection vers la page de paiement sécurisée du CMI…
      </p>
      <p className="mt-2 text-xs text-fg-muted">
        Votre carte est saisie sur la page du CMI, jamais sur JURIKA. Le paiement est acquis à la
        confirmation du CMI ; vous reviendrez ensuite sur JURIKA pour en suivre l’état.
      </p>
      <Button type="submit" className="mt-3">
        Continuer vers la page du CMI
      </Button>
    </form>
  );
}
