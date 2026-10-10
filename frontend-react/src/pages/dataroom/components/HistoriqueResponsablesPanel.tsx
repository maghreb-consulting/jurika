import { useEffect, useState } from 'react';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import type { ReaffectationVue } from '../../../types/dataroom';
import { LIBELLES_NATURE, dateFr } from './responsables';

/**
 * Lot L1 (RG-DOS-02, RG-DOS-03, RG-TKT-08) : historique des responsables d'un dossier,
 * du plus recent au plus ancien : transferts acceptes, reaffectations d'office par le
 * superviseur, designations faites lors de la mise a niveau des donnees (rattrapages).
 */

const nom = (n: string | null | undefined) => n ?? 'compte inconnu';

export function HistoriqueResponsablesPanel({ dossierId }: { dossierId: string }) {
  const [lignes, setLignes] = useState<ReaffectationVue[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    let actif = true;
    dataroomService
      .historiqueResponsables(dossierId)
      .then((l) => actif && setLignes([...l].reverse()))
      .catch((err) => actif && setErreur(extractError(err).message));
    return () => {
      actif = false;
    };
  }, [dossierId]);

  return (
    <section aria-labelledby="historique-responsables" className="space-y-2">
      <h2 id="historique-responsables" className="flex items-center gap-1 text-sm font-semibold text-fg">
        Historique des responsables
        <InfoBulle
          libelle="Que montre l’historique des responsables ?"
          texte="Chaque changement d’employé responsable de ce dossier, avec la date, l’auteur et le motif. Les tickets du dossier suivent toujours son responsable."
        />
      </h2>
      <TexteAide cle="historique-responsables" titre="Changer de responsable">
        <p>
          Un dossier change de responsable de deux façons : par un transfert que l’employé propose
          et que son collègue accepte, ou par une réaffectation décidée par le superviseur en cas
          d’absence ou de départ. Les tickets du dossier suivent toujours.
        </p>
      </TexteAide>
      {erreur && <p className="text-sm text-danger">{erreur}</p>}
      {lignes && lignes.length === 0 && (
        <p className="text-sm text-fg-subtle">
          Aucun changement de responsable : le dossier est suivi par l’employé qui l’a créé.
        </p>
      )}
      {lignes && lignes.length > 0 && (
        <ol className="divide-y divide-border rounded-md border border-border">
          {lignes.map((l) => (
            <li key={l.id} className="space-y-0.5 px-3 py-2 text-sm">
              <p className="font-medium text-fg">
                {LIBELLES_NATURE[l.nature]} — {dateFr(l.createdAt)}
              </p>
              <p className="text-fg-muted">
                {l.ancienResponsableId ? `De ${nom(l.ancienResponsableNom)} à ` : 'Confié à '}
                {nom(l.nouveauResponsableNom)}
                {l.auteurNom ? ` · par ${l.auteurNom}` : ''}
              </p>
              {l.motif && <p className="text-xs text-fg-subtle">Motif : {l.motif}</p>}
              {l.nature === 'RATTRAPAGE' && (
                <p className="text-xs text-fg-subtle">
                  {l.verifieLe
                    ? `Vérifié par ${nom(l.verifieParNom)} le ${dateFr(l.verifieLe)}.`
                    : 'À vérifier par le superviseur.'}
                </p>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
