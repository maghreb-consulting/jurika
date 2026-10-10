import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { dataroomService } from '../../services/dataroom.service';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Button } from '../../components/ui/Button';
import type { ReaffectationVue } from '../../types/dataroom';
import type { WorkspaceUser } from '../../types/auth';
import { dateFr } from '../dataroom/components/responsables';
import { ReaffectationForm } from '../dataroom/components/ReaffectationForm';

/**
 * Lot L1 (decision D1) : la mise a niveau des donnees (migration V28) a designe un
 * employe responsable pour chaque dossier qui n'en avait pas. Le superviseur verifie
 * chacun de ces choix : il le confirme, ou reaffecte le dossier (reaffectation d'office
 * RG-DOS-03, tracee et notifiee), ce qui vaut verification.
 */

const nom = (n: string | null | undefined) => n ?? 'compte inconnu';

export function DossiersAVerifierPage() {
  const [lignes, setLignes] = useState<ReaffectationVue[] | null>(null);
  const [employes, setEmployes] = useState<WorkspaceUser[]>([]);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState<string | null>(null);
  const [reaffectation, setReaffectation] = useState<ReaffectationVue | null>(null);

  const charger = useCallback(async () => {
    try {
      setLignes(await dataroomService.listRattrapages());
    } catch (err) {
      setErreur(extractError(err).message);
    }
  }, []);

  useEffect(() => {
    let actif = true;
    dataroomService
      .listRattrapages()
      .then((l) => actif && setLignes(l))
      .catch((err) => actif && setErreur(extractError(err).message));
    authService
      .listWorkspaceUsers()
      .then((u) => actif && setEmployes(u.filter((x) => x.role === 'EMPLOYE' && x.status === 'ACTIVE')))
      .catch(() => actif && setEmployes([]));
    return () => {
      actif = false;
    };
  }, []);

  async function confirmer(l: ReaffectationVue) {
    setEnCours(l.id);
    setErreur(null);
    try {
      await dataroomService.verifierRattrapage(l.id);
      await charger();
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(null);
    }
  }

  async function reaffecter(employe: string, motif: string) {
    if (!reaffectation) return;
    const ligne = reaffectation;
    setEnCours(ligne.id);
    setErreur(null);
    try {
      await dataroomService.reaffecter(ligne.dossierId, employe, motif);
      await dataroomService.verifierRattrapage(ligne.id);
      setReaffectation(null);
      await charger();
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(null);
    }
  }

  const aVerifier = lignes?.filter((l) => !l.verifieLe).length ?? 0;

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <header className="space-y-1">
        <h1 className="flex items-center gap-2 text-2xl font-bold text-fg">
          Dossiers à vérifier
          <InfoBulle
            libelle="Pourquoi vérifier ces dossiers ?"
            texte="Ces dossiers n’avaient pas d’employé responsable. Lors de la mise à niveau des données, la plateforme en a désigné un d’office ; c’est à vous de confirmer ce choix ou de le corriger."
          />
        </h1>
        <p className="text-sm text-fg-subtle">
          {lignes === null ? 'Chargement…' : aVerifier === 0 ? 'Tous les dossiers ont été vérifiés.' : `${aVerifier} dossier(s) à vérifier.`}
        </p>
      </header>

      <TexteAide cle="dossiers-a-verifier" titre="Comment vérifier un dossier ?">
        <p>
          Pour chaque dossier, la plateforme indique l’employé désigné et la règle appliquée (unique
          employé assigné aux tickets du dossier, sinon créateur du premier ticket, sinon employé
          actif le plus ancien). Si le choix vous convient, cliquez sur « Confirmer ». Sinon,
          « Réaffecter » confie le dossier et tous ses tickets à un autre employé : il est prévenu,
          et le changement est tracé.
        </p>
      </TexteAide>

      {erreur && <p className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">{erreur}</p>}

      {lignes && lignes.length === 0 && (
        <p className="text-sm text-fg-subtle">Aucun dossier n’a reçu de responsable désigné d’office.</p>
      )}

      {lignes && lignes.length > 0 && (
        <table className="w-full text-sm">
          <caption className="sr-only">Dossiers dont le responsable a été désigné d’office</caption>
          <thead>
            <tr className="text-left text-xs text-fg-subtle">
              <th className="py-2 pr-3 font-medium">Dossier</th>
              <th className="py-2 pr-3 font-medium">Employé désigné</th>
              <th className="py-2 pr-3 font-medium">Responsable actuel</th>
              <th className="py-2 pr-3 font-medium">Vérification</th>
              <th className="py-2 font-medium"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {lignes.map((l) => (
              <tr key={l.id} className="border-t border-border align-top">
                <td className="py-2 pr-3">
                  <Link to={`/data-rooms?dossier=${l.dossierId}`} className="font-medium text-accent underline">
                    {l.raisonSociale}
                  </Link>
                  <p className="text-xs text-fg-subtle">Désigné le {dateFr(l.createdAt)}</p>
                </td>
                <td className="py-2 pr-3 text-fg">{nom(l.nouveauResponsableNom)}</td>
                <td className="py-2 pr-3 text-fg">{nom(l.responsableActuelNom)}</td>
                <td className="py-2 pr-3 text-fg-muted">
                  {l.verifieLe ? `Vérifié par ${nom(l.verifieParNom)} le ${dateFr(l.verifieLe)}` : 'À vérifier'}
                </td>
                <td className="py-2">
                  {!l.verifieLe && (
                    <div className="flex flex-wrap gap-2">
                      <Button size="sm" onClick={() => void confirmer(l)} loading={enCours === l.id}>
                        Confirmer
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => setReaffectation(l)}
                      >
                        Réaffecter
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {reaffectation && (
        <ReaffectationForm
          key={reaffectation.id}
          raisonSociale={reaffectation.raisonSociale}
          responsableActuelId={reaffectation.responsableActuelId}
          employes={employes}
          enCours={enCours === reaffectation.id}
          onValider={(employe, motif) => void reaffecter(employe, motif)}
          onAnnuler={() => setReaffectation(null)}
        />
      )}
    </div>
  );
}
