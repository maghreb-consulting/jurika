import { TexteAide } from '../../components/ui/Aide';
import type { TicketStatut } from '../../types/ticket';
import { aideStatutTicket, type ProfilTicket } from './textesAideStatut';

/**
 * Lot L6 (E9, CDC 15.4) : aide du statut courant du ticket -- ce qu'il faut faire, ce qui
 * manque (obstacles nommes par le serveur), ce qui vient ensuite. Une cle par statut : une
 * aide masquee a un statut se reaffiche au statut suivant.
 *
 * `obstacles` : null tant que le serveur n'a pas repondu (rien n'est affirme) ;
 * `renvoi` : les obstacles sont deja listes ailleurs sur l'ecran, l'aide y renvoie.
 */
export function AideStatutTicket({
  statut,
  profil,
  obstacles,
  renvoi = false,
}: {
  statut: TicketStatut;
  profil: ProfilTicket;
  obstacles: string[] | null;
  renvoi?: boolean;
}) {
  const aide = aideStatutTicket(statut, profil);
  const actif = statut !== 'CLOTURE_DOSSIER' && statut !== 'ANNULE';
  return (
    <div data-testid="aide-statut-ticket">
      <TexteAide key={statut} cle={`statut-ticket-${statut}`} titre={aide.titre}>
        <p>
          <strong className="text-fg">À faire : </strong>
          {aide.faire}
        </p>
        {actif && obstacles !== null && (
          <div className="mt-1">
            <strong className="text-fg">Ce qui manque : </strong>
            {obstacles.length === 0 ? (
              'rien n’empêche le passage au statut suivant.'
            ) : renvoi ? (
              obstacles.length === 1
                ? 'un point à régler, nommé dans « Passage de statut » ci-dessous.'
                : `${obstacles.length} points à régler, nommés dans « Passage de statut » ci-dessous.`
            ) : (
              <ul className="ml-5 list-disc" data-testid="aide-obstacles">
                {obstacles.map((o) => <li key={o}>{o}</li>)}
              </ul>
            )}
          </div>
        )}
        <p className="mt-1">
          <strong className="text-fg">Ensuite : </strong>
          {aide.ensuite}
        </p>
      </TexteAide>
    </div>
  );
}
