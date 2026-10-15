import type { EtatEssai } from '../services/essai.service';

/** Lot L7 (E4) : textes de l'essai gratuit (bandeau, ecrans du super-admin). */

const DATE = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Africa/Casablanca' });

export function formaterDate(iso: string | null): string {
  return iso ? DATE.format(new Date(iso)) : '';
}

/** Texte du bandeau selon l'etat de l'essai (exporte pour les tests). */
export function messageEssai(e: EtatEssai): { titre: string; detail: string; urgent: boolean } | null {
  if (!e.enEssai) return null;
  if (e.lectureSeule) {
    return {
      titre: 'Essai gratuit terminé : votre cabinet est en lecture seule.',
      detail: e.suppressionLe
        ? `Sans souscription d’un forfait, toutes ses données seront supprimées définitivement le ${formaterDate(e.suppressionLe)}. Souscrivez pour reprendre votre travail : rien n’est perdu.`
        : 'Souscrivez un forfait pour reprendre votre travail : rien n’est perdu.',
      urgent: true,
    };
  }
  const jours = e.joursRestants <= 1 ? 'moins d’un jour' : `${e.joursRestants} jours`;
  const fin = formaterDate(e.fin);
  if (e.rappel === 'J-1') {
    return {
      titre: `Dernier jour d’essai gratuit : il se termine le ${fin}.`,
      detail: 'Souscrivez dès maintenant pour conserver tout votre travail ; sans souscription, le cabinet passera en lecture seule pendant 30 jours.',
      urgent: true,
    };
  }
  if (e.rappel === 'J-3') {
    return {
      titre: `Plus que ${jours} d’essai gratuit (fin le ${fin}).`,
      detail: 'Pensez à souscrire un forfait : votre travail est conservé et les actes régénérés ne porteront plus la mention d’évaluation.',
      urgent: false,
    };
  }
  return {
    titre: `Essai gratuit : ${jours} restants (fin le ${fin}).`,
    detail: "Pendant l’essai : 3 dossiers, 1 superviseur et 1 employé, 500 Mo, 20 questions au chatbot ; aucun téléchargement, les actes portent la mention « VERSION D'ÉVALUATION — SANS VALEUR JURIDIQUE ».",
    urgent: false,
  };
}

