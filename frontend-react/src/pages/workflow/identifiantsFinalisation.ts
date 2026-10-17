import type { DossierJuridiqueView, UpdateIdentifiantsPayload } from '../../types/dataroom';

/**
 * Lot L6b (E14) : identifiants que la fin d'une creation envoie a la fiche du dossier.
 *
 * La route `PATCH /dossiers/{id}/identifiants` REMPLACE tous les champs de la fiche. N'envoyer
 * que les identifiants connus du parcours effacait l'adresse, la ville, le capital et la date de
 * constitution du dossier (constate sur la pile apres la finalisation d'une creation). La charge
 * part donc de la fiche ACTUELLE et n'y ajoute que ce que le parcours connait et que la fiche n'a
 * pas : une valeur deja connue du dossier n'est jamais remplacee par un vide.
 *
 * L'etape 1 est lue a plat ou imbriquee : l'enregistrement automatique la range a plat
 * (`step1.denomination` est alors la chaine saisie, et l'ICE est `step1.ice`).
 *
 * Renvoie null quand le parcours n'apporte rien de nouveau (aucun appel).
 */
export function identifiantsApresCreation(
  stepData: Record<string, unknown>,
  actuel: DossierJuridiqueView,
): UpdateIdentifiantsPayload | null {
  const carte = (v: unknown): Record<string, unknown> =>
    v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
  const txt = (v: unknown): string | null => {
    const s = v == null ? '' : String(v).trim();
    return s ? s : null;
  };
  const s1 = carte(stepData.step1);
  const den = { ...s1, ...carte(s1.denomination) };
  const s2 = carte(stepData.step2);
  const siege = { ...s2, ...carte(s2.siege) };
  const pi = carte(carte(stepData.step9).postImmat);

  const duParcours: Partial<Record<'ice' | 'rcNumero' | 'rcTribunal' | 'identifiantFiscal' | 'taxeProfessionnelle' | 'cnss', string | null>> = {
    ice: txt(pi.ice ?? den.ice),
    rcNumero: txt(pi.rcNumero ?? den.rcNumero),
    rcTribunal: txt(pi.rcTribunal ?? siege.villeGreffe ?? siege.tribunal),
    identifiantFiscal: txt(pi.identifiantFiscal ?? pi.ifNumero ?? den.ifFiscal),
    taxeProfessionnelle: txt(pi.taxeProfessionnelle ?? pi.patente),
    cnss: txt(pi.cnss),
  };

  const payload: UpdateIdentifiantsPayload = {
    ice: actuel.ice,
    rcNumero: actuel.rcNumero,
    rcTribunal: actuel.rcTribunal,
    identifiantFiscal: actuel.identifiantFiscal,
    taxeProfessionnelle: actuel.taxeProfessionnelle,
    cnss: actuel.cnss,
    adresseSiege: actuel.adresseSiege,
    ville: actuel.ville,
    capitalSocialMad: actuel.capitalSocialMad,
    dateConstitution: actuel.dateConstitution,
  };
  let nouveau = false;
  for (const [cle, valeur] of Object.entries(duParcours) as [keyof typeof duParcours, string | null][]) {
    if (valeur !== null && !txt(payload[cle])) {
      payload[cle] = valeur;
      nouveau = true;
    }
  }
  return nouveau ? payload : null;
}
