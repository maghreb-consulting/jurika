import { useEffect, useState } from 'react';
import { dataroomService } from '../../../services/dataroom.service';
import { authService } from '../../../services/auth.service';
import { Button } from '../../../components/ui/Button';
import type { WorkspaceUser } from '../../../types/auth';
import { ReaffectationForm } from './ReaffectationForm';
import { extractError } from '../../../lib/api';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import type { ReaffectationVue } from '../../../types/dataroom';
import { LIBELLES_NATURE, dateFr } from './responsables';

/**
 * Lot L1 (RG-DOS-02, RG-DOS-03, RG-TKT-08) : historique des responsables d'un dossier,
 * du plus recent au plus ancien : transferts acceptes, reaffectations d'office par le
 * superviseur, designations faites lors de la mise a niveau des donnees (rattrapages).
 * Le superviseur y reaffecte d'office le dossier (RG-DOS-03) : employe actif, motif
 * obligatoire ; le changement apparait aussitot dans l'historique.
 */

const nom = (n: string | null | undefined) => n ?? 'compte inconnu';

export function HistoriqueResponsablesPanel({
  dossierId,
  raisonSociale = 'ce dossier',
  peutReaffecter = false,
}: {
  dossierId: string;
  raisonSociale?: string;
  /** Superviseur seulement (RG-DOS-03) ; le serveur le verifie aussi. */
  peutReaffecter?: boolean;
}) {
  const [lignes, setLignes] = useState<ReaffectationVue[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [employes, setEmployes] = useState<WorkspaceUser[]>([]);
  const [formulaire, setFormulaire] = useState(false);
  const [enCours, setEnCours] = useState(false);
  const [succes, setSucces] = useState<string | null>(null);

  useEffect(() => {
    let actif = true;
    dataroomService
      .historiqueResponsables(dossierId)
      .then((l) => actif && setLignes([...l].reverse()))
      .catch((err) => actif && setErreur(extractError(err).message));
    return () => {
      actif = false;
    };
  }, [dossierId, version]);

  useEffect(() => {
    if (!peutReaffecter) return undefined;
    let actif = true;
    authService
      .listWorkspaceUsers()
      .then((u) => actif && setEmployes(u.filter((x) => x.role === 'EMPLOYE' && x.status === 'ACTIVE')))
      .catch(() => actif && setEmployes([]));
    return () => {
      actif = false;
    };
  }, [peutReaffecter]);

  async function reaffecter(employeId: string, motif: string) {
    setEnCours(true);
    setErreur(null);
    setSucces(null);
    try {
      await dataroomService.reaffecter(dossierId, employeId, motif);
      const e = employes.find((x) => x.userId === employeId);
      setSucces(`Dossier réaffecté à ${e ? `${e.firstName} ${e.lastName}` : 'l’employé choisi'}.`);
      setFormulaire(false);
      setVersion((v) => v + 1);
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(false);
    }
  }

  // Toutes les lignes portent le responsable actuel du dossier.
  const responsableActuel = lignes && lignes.length > 0 ? lignes[0] : null;

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
          {peutReaffecter &&
            ' Pour réaffecter ce dossier, cliquez sur « Réaffecter le dossier », choisissez un employé actif et indiquez le motif : les deux employés sont prévenus et le changement s’ajoute à cet historique.'}
        </p>
      </TexteAide>
      {responsableActuel && (
        <p className="text-sm text-fg">
          Responsable actuel : <strong>{nom(responsableActuel.responsableActuelNom)}</strong>
        </p>
      )}
      {peutReaffecter && !formulaire && (
        <Button size="sm" variant="secondary" onClick={() => { setSucces(null); setFormulaire(true); }}>
          Réaffecter le dossier
        </Button>
      )}
      {peutReaffecter && formulaire && (
        <ReaffectationForm
          raisonSociale={raisonSociale}
          responsableActuelId={responsableActuel?.responsableActuelId ?? null}
          employes={employes}
          enCours={enCours}
          onValider={(employeId, motif) => void reaffecter(employeId, motif)}
          onAnnuler={() => setFormulaire(false)}
        />
      )}
      {succes && <p role="status" className="text-sm text-success">{succes}</p>}
      {erreur && <p role="alert" className="text-sm text-danger">{erreur}</p>}
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
