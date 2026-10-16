import { test, expect } from './fixtures';
import { KARIM, SALMA, suffixeUnique, etape1, etape2, etape3, etape4, etape5, nouveauTicketCreation, tolererPremiereOuverture } from './outils/creation';

/**
 * Lot L6b (E13) : parcours de CREATION d'une SARL, etapes 1 a 5, dans le navigateur, sur la pile
 * reelle, avec la sentinelle (aucun 4xx/5xx, aucune erreur de console).
 *
 * Il garde deux defauts trouves au lot :
 *  - etape 5 en HTTP : crypto.randomUUID absent hors contexte securise (commit 077514a) ;
 *  - etape 5 avec deux gerants aux modes de remuneration differents : l'etape partait avec
 *    gerance.remunerationMode vide, le serveur repondait 400 "Mode de remuneration de la gerance
 *    requis." (corrige au lot : phrase enumeree).
 * Les etapes 1 a 5 ne generent aucun acte : le corpus (reel ou fictif) n'intervient pas, le
 * parcours tourne aussi en CI. Le parcours complet (jusqu'a la cloture) est dans
 * creation-complete.parcours.ts (@corpus-reel).
 *
 * L6b (E14) : gestes repris de outils/creation.ts (certificat de propriete exige a l'etape 2,
 * CIN lue par l'assistant puis verifiee a l'etape 5 ; premiere ouverture du parcours toleree).
 */
test('parcours creation SARL : etapes 1 a 5, deux gerants aux remunerations differentes', async ({ page, entrer, aller, sentinelle, capturer }) => {
  test.setTimeout(400_000);
  tolererPremiereOuverture(sentinelle);
  await entrer('EMPLOYE');
  const denomination = `Atlas Etapes ${suffixeUnique()} SARL`;
  await nouveauTicketCreation(page, aller, { titre: `Constitution ${denomination}`, raison: denomination.toUpperCase(), forme: 'SARL' });
  await capturer('creation-etape-1');
  await etape1(page, denomination);
  await etape2(page);
  await etape3(page);
  await etape4(page);
  await capturer('creation-etape-5');
  expect(await etape5(page, [KARIM, SALMA])).toMatch(/Associ/);
  await capturer('creation-etape-6');
});
