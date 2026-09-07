import { useEffect, useState } from 'react';
import { dataroomService } from '../../services/dataroom.service';
import { DOCUMENT_TYPE_LABELS } from '../../types/dataroom';

/**
 * Lot 2 (2026-09-07) — LES TYPES DE DOCUMENT VIENNENT DU BACKEND.
 *
 * La liste du menu « Uploader document » vivait en dur dans le frontend : 16
 * types, alors que la base en accepte 44 depuis le lot 1. Un employé qui
 * déposait à la main un certificat négatif, un contrat de domiciliation, un
 * titre de propriété, une attestation d'enregistrement, un pouvoir ou un
 * rapport de commissaire aux apports le rangeait donc sous « AUTRE » : le
 * document devenait introuvable par type, et n'était plus reconnu comme le
 * justificatif attendu par la démarche correspondante.
 *
 * Le catalogue est désormais servi par l'API, aligné sur la contrainte de la
 * table et sur le rangement en groupes. Repli sur les libellés statiques si
 * l'appel échoue : mieux vaut un menu court qu'un menu vide.
 */
export interface TypeDocumentOption {
  code: string;
  libelle: string;
  groupe: string | null;
}

const REPLI: TypeDocumentOption[] = Object.entries(DOCUMENT_TYPE_LABELS).map(
  ([code, libelle]) => ({ code, libelle, groupe: null }),
);

export function useDocumentTypes(): {
  types: TypeDocumentOption[];
  chargement: boolean;
} {
  const [types, setTypes] = useState<TypeDocumentOption[]>(REPLI);
  const [chargement, setChargement] = useState(true);

  useEffect(() => {
    let annule = false;
    void dataroomService
      .listDocumentTypes()
      .then((liste) => {
        if (annule || liste.length === 0) return;
        setTypes(liste);
      })
      .catch(() => {
        // Repli déjà en place.
      })
      .finally(() => {
        if (!annule) setChargement(false);
      });
    return () => {
      annule = true;
    };
  }, []);

  return { types, chargement };
}
