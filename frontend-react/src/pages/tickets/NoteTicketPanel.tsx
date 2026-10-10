import { useCallback, useEffect, useRef, useState } from 'react';
import { ticketService } from '../../services/ticket.service';
import { extractError } from '../../lib/api';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import type { TicketStatut } from '../../types/ticket';

/**
 * Lot L1 (RG-TKT-07) : note de ticket.
 *
 * - ouverte des la creation du ticket, validable meme vide ;
 * - ENREGISTREMENT AUTOMATIQUE au fil de la saisie (apres une courte pause, a la
 *   sortie du champ et a la fermeture du panneau) : rien n'est perdu en quittant ;
 * - redigee par l'employe responsable ; le superviseur la lit ; lecture seule apres
 *   la cloture ; jamais affichee au client (le serveur la lui refuse aussi).
 */

export const DELAI_ENREGISTREMENT_MS = 1000;

type Etat =
  | { type: 'repos' }
  | { type: 'en-cours' }
  | { type: 'enregistre'; a: Date }
  | { type: 'erreur'; message: string };

interface Props {
  ticketId: string;
  statut: TicketStatut;
  role: string | undefined;
}

export function NoteTicketPanel({ ticketId, statut, role }: Props) {
  const lectureSeule = role !== 'EMPLOYE' || statut === 'CLOTURE_DOSSIER';
  const [contenu, setContenu] = useState('');
  const [charge, setCharge] = useState(false);
  const [etat, setEtat] = useState<Etat>({ type: 'repos' });
  const enAttente = useRef<string | null>(null);
  const minuteur = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (role === 'CLIENT') return undefined; // jamais de note pour le client
    let actif = true;
    ticketService
      .getNote(ticketId)
      .then((n) => {
        if (!actif) return;
        setContenu(n.contenu ?? '');
        if (n.modifieLe) setEtat({ type: 'enregistre', a: new Date(n.modifieLe) });
      })
      .catch((err) => actif && setEtat({ type: 'erreur', message: extractError(err).message }))
      .finally(() => actif && setCharge(true));
    return () => {
      actif = false;
    };
  }, [ticketId, role]);

  const enregistrer = useCallback(async () => {
    if (minuteur.current) {
      clearTimeout(minuteur.current);
      minuteur.current = null;
    }
    const valeur = enAttente.current;
    if (valeur === null) return;
    enAttente.current = null;
    setEtat({ type: 'en-cours' });
    try {
      const n = await ticketService.saveNote(ticketId, valeur);
      setEtat({ type: 'enregistre', a: n.modifieLe ? new Date(n.modifieLe) : new Date() });
    } catch (err) {
      // La saisie reste a l'ecran et sera renvoyee a la prochaine modification.
      enAttente.current = enAttente.current ?? valeur;
      setEtat({ type: 'erreur', message: extractError(err).message });
    }
  }, [ticketId]);

  // Fermeture du panneau : ce qui n'est pas encore parti est envoye.
  useEffect(() => () => void enregistrer(), [enregistrer]);

  function saisir(valeur: string) {
    setContenu(valeur);
    enAttente.current = valeur;
    if (minuteur.current) clearTimeout(minuteur.current);
    minuteur.current = setTimeout(() => void enregistrer(), DELAI_ENREGISTREMENT_MS);
  }

  if (role === 'CLIENT') return null;

  return (
    <section aria-labelledby="note-ticket-titre" className="space-y-2">
      <h3 id="note-ticket-titre" className="flex items-center gap-1 text-sm font-semibold text-fg">
        Note du ticket
        <InfoBulle
          libelle="À quoi sert la note du ticket ?"
          texte="Un bloc-notes interne au cabinet, attaché à ce ticket : remplacez-y vos notes sur papier. Il n’est jamais visible par le client."
        />
      </h3>
      <TexteAide cle="note-ticket" titre="Comment fonctionne la note ?">
        <p>
          La note est enregistrée automatiquement pendant que vous écrivez : rien n’est perdu en
          quittant la page. Elle peut rester vide. Si le dossier est transféré ou réaffecté, la note
          suit le ticket chez le nouveau responsable. Après la clôture, elle reste consultable en
          lecture seule.
        </p>
      </TexteAide>
      <label htmlFor={`note-${ticketId}`} className="sr-only">
        Note du ticket
      </label>
      <textarea
        id={`note-${ticketId}`}
        value={contenu}
        readOnly={lectureSeule}
        disabled={!charge}
        onChange={(e) => saisir(e.target.value)}
        onBlur={() => void enregistrer()}
        rows={5}
        maxLength={20000}
        placeholder={lectureSeule ? 'Aucune note.' : 'Vos notes sur ce ticket : démarches à suivre, points à vérifier, rappels…'}
        className="w-full rounded-md border border-border bg-bg-raised p-2 text-sm text-fg read-only:bg-bg-overlay"
      />
      <p className="text-xs text-fg-subtle" aria-live="polite">
        {messageEtat(etat, lectureSeule, statut, role)}
      </p>
    </section>
  );
}

function heure(d: Date): string {
  return d.toLocaleString('fr-MA', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function messageEtat(etat: Etat, lectureSeule: boolean, statut: TicketStatut, role: string | undefined): string {
  if (etat.type === 'erreur') {
    return `Échec de l’enregistrement : ${etat.message} Votre texte reste à l’écran ; il sera renvoyé à la prochaine modification.`;
  }
  if (lectureSeule) {
    const raison = statut === 'CLOTURE_DOSSIER'
      ? 'Ticket clos : la note se consulte en lecture seule.'
      : role === 'SUPERVISEUR'
        ? 'Lecture seule : la note est rédigée par l’employé responsable du ticket.'
        : 'Lecture seule.';
    return etat.type === 'enregistre' ? `${raison} Dernière modification le ${heure(etat.a)}.` : raison;
  }
  if (etat.type === 'en-cours') return 'Enregistrement…';
  if (etat.type === 'enregistre') return `Enregistré le ${heure(etat.a)}.`;
  return 'Enregistrement automatique pendant la saisie.';
}
