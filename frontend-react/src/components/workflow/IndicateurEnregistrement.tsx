import { AlertTriangle, Check, Loader2 } from 'lucide-react';
import { useEtatEnregistrement, type EtatEnregistrement } from '../../pages/workflow/enregistrementAuto';
import { InfoBulle } from '../ui/Aide';

/**
 * Lot L7 (E2) : indicateur discret de l'enregistrement automatique des saisies
 * d'un parcours. Rien hors d'un parcours charge.
 */
export function IndicateurEnregistrement() {
  const etat = useEtatEnregistrement();
  if (!etat) return null;
  return (
    <div className="flex items-center gap-1 text-xs" data-testid="indicateur-enregistrement">
      <p
        role={etat.type === 'erreur' ? 'alert' : 'status'}
        aria-live="polite"
        className={
          etat.type === 'erreur'
            ? 'inline-flex items-center gap-1 font-medium text-danger'
            : 'inline-flex items-center gap-1 text-fg-subtle'
        }
      >
        {icone(etat)}
        {messageEnregistrement(etat)}
      </p>
      <InfoBulle
        libelle="Comment votre saisie est-elle conservée ?"
        texte="Chaque champ est enregistré automatiquement en brouillon, peu après la frappe, quand vous changez d’étape, quittez le parcours ou fermez la page. Rien n’est perdu ; l’étape n’est validée qu’avec le bouton de validation."
      />
    </div>
  );
}

function icone(etat: EtatEnregistrement) {
  if (etat.type === 'en-cours') return <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />;
  if (etat.type === 'erreur') return <AlertTriangle className="h-3 w-3" aria-hidden="true" />;
  if (etat.type === 'enregistre') return <Check className="h-3 w-3" aria-hidden="true" />;
  return null;
}

function heure(d: Date): string {
  return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
}

function messageEnregistrement(etat: EtatEnregistrement): string {
  if (etat.type === 'en-cours') return 'Enregistrement…';
  if (etat.type === 'enregistre') return `Enregistré à ${heure(etat.a)}`;
  if (etat.type === 'erreur') {
    return `Échec de l’enregistrement automatique : ${etat.message} Votre saisie reste à l’écran ; elle sera renvoyée à la prochaine modification ou au changement d’étape.`;
  }
  return 'Enregistrement automatique de la saisie';
}
