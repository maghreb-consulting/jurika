import type { TicketStatut } from '../../types/ticket';

/**
 * Lot L6 (E9, CDC 15.4) : aide de chaque statut du ticket -- ce qu'il faut faire, ce qui
 * vient ensuite -- selon le profil de la personne qui regarde. Ce qui manque n'est pas ecrit
 * ici : ce sont les obstacles nommes par le serveur (/tickets/{id}/passage).
 *
 * Profils :
 * - RESPONSABLE : l'employe responsable du dossier, seul a faire avancer le ticket ;
 * - SUPERVISEUR : suit le ticket en consultation ;
 * - LECTURE : un autre employe (consultation seule).
 */
export type ProfilTicket = 'RESPONSABLE' | 'SUPERVISEUR' | 'LECTURE';

export interface AideStatut {
  titre: string;
  faire: string;
  ensuite: string;
}

const RESERVE = 'Seul l’employé responsable du dossier fait avancer ce ticket ; vous le consultez.';

export function profilTicket(role: string | undefined, enCharge: boolean): ProfilTicket {
  if (role === 'EMPLOYE' && enCharge) return 'RESPONSABLE';
  if (role === 'SUPERVISEUR' || role === 'SUPER_ADMIN') return 'SUPERVISEUR';
  return 'LECTURE';
}

export function aideStatutTicket(statut: TicketStatut, profil: ProfilTicket): AideStatut {
  const responsable = profil === 'RESPONSABLE';
  switch (statut) {
    case 'CREATION_TICKET':
      return {
        titre: 'Étape 1 sur 4 : création du ticket et collecte des informations',
        faire: responsable
          ? 'Rassemblez les informations et les pièces du client (identité des associés, siège, capital…), puis faites passer le ticket à la génération des documents.'
          : `L’employé responsable rassemble les informations et les pièces du client. ${RESERVE}`,
        ensuite:
          'Génération des documents : les données du parcours sont saisies et les actes sont produits à partir des modèles du cabinet.',
      };
    case 'GENERATION_DOCUMENTS':
      return {
        titre: 'Étape 2 sur 4 : génération des documents',
        faire: responsable
          ? 'Ouvrez le parcours pour saisir les données et générer les actes, puis relisez-les. Une donnée obligatoire manquante empêche la génération : elle vous est nommée.'
          : `L’employé responsable saisit les données et génère les actes ; les données saisies et les documents produits s’affichent au fur et à mesure. ${RESERVE}`,
        ensuite:
          'Déroulement de la démarche : dépôts et formalités auprès des administrations, cochés au fil de l’eau.',
      };
    case 'DEROULEMENT_DEMARCHE':
      return {
        titre: 'Étape 3 sur 4 : déroulement de la démarche',
        faire: responsable
          ? 'Accomplissez les démarches auprès des administrations et cochez-les au fur et à mesure, en déposant les justificatifs reçus. Saisissez dès leur arrivée les données attendues d’un organisme (numéro RC, ICE, IF…).'
          : `L’employé responsable accomplit les démarches et les coche au fur et à mesure. Le récapitulatif montre ce qui est fait et ce qui manque. ${RESERVE}`,
        ensuite:
          'Clôture du dossier : le récapitulatif dit ce que le dossier contient et ce qui lui manque ; une fois clos, le ticket devient l’archive de l’opération.',
      };
    case 'CLOTURE_DOSSIER':
      return {
        titre: 'Étape 4 sur 4 : dossier clos',
        faire:
          'Le ticket est l’archive de l’opération : tout se consulte (données, documents, démarches, débours), plus rien ne se modifie.',
        ensuite: 'Rien pour ce ticket. Une nouvelle opération sur la société fait l’objet d’un nouveau ticket.',
      };
    case 'ANNULE':
      return {
        titre: 'Ticket annulé',
        faire: responsable
          ? 'Le ticket est annulé ; son motif est rappelé dans le détail du ticket. Préparez, si besoin, la note d’annulation et les lettres de retrait des dépôts faits, depuis le détail du ticket dans la liste des tickets.'
          : `Le ticket est annulé ; son motif est rappelé dans le détail du ticket. ${RESERVE}`,
        ensuite: responsable
          ? 'Si l’opération reprend, utilisez « Reprendre » dans le détail du ticket : il retrouve le statut qu’il avait avant l’annulation, avec le motif de reprise.'
          : 'Si l’opération reprend, l’employé responsable reprend le ticket : il retrouve le statut qu’il avait avant l’annulation.',
      };
    default:
      return { titre: 'Statut du ticket', faire: '', ensuite: '' };
  }
}
