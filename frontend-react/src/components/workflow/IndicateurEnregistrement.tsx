import { AlertTriangle, Check, HardDrive, Loader2 } from 'lucide-react';
import { useEtatEnregistrement, type EtatEnregistrement } from '../../pages/workflow/enregistrementAuto';
import { InfoBulle } from '../ui/Aide';

/**
 * Lot L7 (E2) : indicateur discret de l'enregistrement automatique des saisies
 * d'un parcours. Rien hors d'un parcours charge.
 */
export function IndicateurEnregistrement() {
  const etat = useEtatEnregistrement();
  if (!etat) return null;
  const alerte = etat.type === 'erreur' || etat.type === 'session-expiree' || etat.type === 'hors-ligne';
  return (
    <div className="flex items-center gap-1 text-xs" data-testid="indicateur-enregistrement">
      <p
        role={alerte ? 'alert' : 'status'}
        aria-live="polite"
        className={
          alerte
            ? 'inline-flex items-center gap-1 font-medium text-danger'
            : 'inline-flex items-center gap-1 text-fg-subtle'
        }
      >
        {icone(etat)}
        {messageEnregistrement(etat)}
      </p>
      <InfoBulle
        libelle="Comment votre saisie est-elle conservée ?"
        texte="Chaque champ, chaque choix et chaque case de l’étape (y compris l’état des pièces de l’étape 8) est enregistré automatiquement en brouillon : peu après la frappe, quand vous changez d’étape, quittez le parcours ou fermez la page. Les fichiers déjà déposés dans la Data Room ne sont pas renvoyés. Tant que le serveur n’a pas confirmé, votre saisie est aussi gardée dans ce navigateur : si votre session expire ou si la connexion est coupée, reconnectez-vous ; elle sera envoyée automatiquement, avant tout réaffichage. L’étape n’est validée qu’avec le bouton de validation."
      />
    </div>
  );
}

function icone(etat: EtatEnregistrement) {
  if (etat.type === 'en-cours') return <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />;
  if (etat.type === 'erreur') return <AlertTriangle className="h-3 w-3" aria-hidden="true" />;
  if (etat.type === 'session-expiree' || etat.type === 'hors-ligne') {
    return <HardDrive className="h-3 w-3" aria-hidden="true" />;
  }
  if (etat.type === 'enregistre') return <Check className="h-3 w-3" aria-hidden="true" />;
  return null;
}

function heure(d: Date): string {
  return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
}

function messageEnregistrement(etat: EtatEnregistrement): string {
  if (etat.type === 'en-cours') return 'Enregistrement…';
  if (etat.type === 'enregistre') return `Enregistré à ${heure(etat.a)}`;
  if (etat.type === 'session-expiree') {
    return 'Session expirée : votre saisie est gardée dans ce navigateur, reconnectez-vous. Elle sera envoyée automatiquement dès votre reconnexion.';
  }
  if (etat.type === 'hors-ligne') {
    return 'Hors ligne : votre saisie est gardée dans ce navigateur. Elle sera envoyée automatiquement dès le retour de la connexion.';
  }
  if (etat.type === 'erreur') {
    return etat.gardeLocalement
      ? `Échec de l’enregistrement automatique : ${etat.message} Votre saisie reste à l’écran et elle est gardée dans ce navigateur ; elle sera renvoyée à la prochaine modification ou au changement d’étape.`
      : `Échec de l’enregistrement automatique : ${etat.message} Votre saisie reste à l’écran, mais elle n’est pas gardée dans ce navigateur : ne fermez pas la page avant qu’elle soit enregistrée.`;
  }
  return 'Enregistrement automatique de la saisie';
}
