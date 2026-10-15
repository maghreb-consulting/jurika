import { useCallback, useEffect, useState } from 'react';
import { Check, FolderCheck, UserCog } from 'lucide-react';
import { dataroomService } from '../../services/dataroom.service';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { Page } from '../../components/layout/Page';
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

  const colonnes: ColonneTableau<ReaffectationVue>[] = [
    {
      cle: 'dossier',
      titre: 'Dossier',
      rendu: (l) => (
        <div className="min-w-0">
          <Lien to={`/data-rooms?dossier=${l.dossierId}`}>{l.raisonSociale}</Lien>
          <p className="text-xs text-fg-muted">Désigné le {dateFr(l.createdAt)}</p>
        </div>
      ),
    },
    { cle: 'designe', titre: 'Employé désigné', rendu: (l) => nom(l.nouveauResponsableNom) },
    { cle: 'actuel', titre: 'Responsable actuel', rendu: (l) => nom(l.responsableActuelNom), masquerSous: 'md' },
    {
      cle: 'verification',
      titre: 'Vérification',
      rendu: (l) =>
        l.verifieLe ? (
          <span className="inline-flex items-start gap-1.5 text-sm text-success">
            <Check className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{`Vérifié par ${nom(l.verifieParNom)} le ${dateFr(l.verifieLe)}`}</span>
          </span>
        ) : (
          <Badge variant="warning">À vérifier</Badge>
        ),
    },
    {
      cle: 'actions',
      titre: <span className="sr-only">Actions</span>,
      alignement: 'droite',
      rendu: (l) =>
        !l.verifieLe && (
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" size="sm" variant="secondary" icon={<Check />} onClick={() => void confirmer(l)} loading={enCours === l.id}>
              Confirmer
            </Button>
            <Button type="button" size="sm" variant="ghost" icon={<UserCog />} onClick={() => setReaffectation(l)}>
              Réaffecter
            </Button>
          </div>
        ),
    },
  ];

  return (
    <Page
      titre="Dossiers à vérifier"
      etat={
        <InfoBulle
          libelle="Pourquoi vérifier ces dossiers ?"
          texte="Ces dossiers n’avaient pas d’employé responsable. Lors de la mise à niveau des données, la plateforme en a désigné un d’office ; c’est à vous de confirmer ce choix ou de le corriger."
        />
      }
      description={lignes === null ? 'Chargement…' : aVerifier === 0 ? 'Tous les dossiers ont été vérifiés.' : `${aVerifier} dossier(s) à vérifier.`}
      aide={
        <TexteAide cle="dossiers-a-verifier" titre="Comment vérifier un dossier ?">
          <p>
            Pour chaque dossier, la plateforme indique l’employé désigné et la règle appliquée (unique
            employé assigné aux tickets du dossier, sinon créateur du premier ticket, sinon employé
            actif le plus ancien). Si le choix vous convient, cliquez sur « Confirmer ». Sinon,
            « Réaffecter » confie le dossier et tous ses tickets à un autre employé : il est prévenu,
            et le changement est tracé.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4">
        {erreur && (
          <Message ton="erreur" onFermer={() => setErreur(null)}>
            {erreur}
          </Message>
        )}

        {(lignes === null || lignes.length > 0) && (
          <Tableau
            colonnes={colonnes}
            lignes={lignes ?? []}
            cleLigne={(l) => l.id}
            legende="Dossiers dont le responsable a été désigné d’office"
            chargement={lignes === null && !erreur}
            lignesChargement={3}
            enTeteCollant={false}
          />
        )}

        {lignes && lignes.length === 0 && (
          <Card padding={false}>
            <EmptyState
              icon={<FolderCheck />}
              title="Rien à vérifier"
              description="Aucun dossier n’a reçu de responsable désigné d’office."
            />
          </Card>
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
    </Page>
  );
}
