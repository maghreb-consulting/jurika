import { useContext, useEffect, useState } from 'react';
import { ToastContext } from '../../../components/ui/useToast';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import { Copy } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Badge } from '../../../components/ui/Badge';
import { Case } from '../../../components/ui/Choix';
import { Message } from '../../../components/ui/Message';
import { Skeleton } from '../../../components/ui/LoadingSkeleton';
import type { DataroomSettings, HistoriqueAccesClient, PermissionsClientPatch } from '../../../types/dataroom';
import { dateFr } from './responsables';

/**
 * Lot L1 (RG-CLI-01) : acces du client a la Data Room de son dossier, regle par
 * l'employe responsable ou par le superviseur : permissions (consulter, telecharger,
 * imprimer, deposer, envoyer des demandes), suspension, lien d'acces, et historique
 * de chaque modification (dataroom V35). Les autres voient les reglages sans les changer.
 */

type Cle = keyof PermissionsClientPatch;

const PERMISSIONS: { cle: Cle; libelle: string; aide: string }[] = [
  { cle: 'permConsultation', libelle: 'Consulter les documents', aide: 'Le client voit les documents de sa Data Room.' },
  { cle: 'permDownload', libelle: 'Télécharger', aide: 'Le client enregistre les documents sur son poste.' },
  { cle: 'permPrint', libelle: 'Imprimer', aide: 'Le client imprime les documents depuis la plateforme.' },
  { cle: 'permDepot', libelle: 'Déposer des documents', aide: 'Le client ajoute ses propres pièces dans l’onglet « Dépôts du client ».' },
  { cle: 'permDemandes', libelle: 'Envoyer des demandes', aide: 'Le client adresse des demandes à l’employé responsable du dossier.' },
];

/** Libelles des cles de l'historique (DataroomSettingsService#permissions). */
const LIBELLES_HISTORIQUE: Record<string, string> = {
  consultation: 'Consulter les documents',
  telechargement: 'Télécharger',
  impression: 'Imprimer',
  depot: 'Déposer des documents',
  demandes: 'Envoyer des demandes',
};

const ouiNon = (v: unknown) => (v === true ? 'oui' : v === false ? 'non' : String(v));

function decrireChangement(l: HistoriqueAccesClient): string {
  if (l.nature === 'SUSPENSION') return 'Accès du client suspendu';
  if (l.nature === 'REACTIVATION') return 'Accès du client réactivé';
  const avant = l.avant ?? {};
  const apres = l.apres ?? {};
  const changes = Object.keys(apres)
    .filter((k) => avant[k] !== apres[k])
    .map((k) => `${LIBELLES_HISTORIQUE[k] ?? k} : ${ouiNon(avant[k])} → ${ouiNon(apres[k])}`);
  return `Permissions modifiées — ${changes.join(' ; ')}`;
}

export function AccesClientPanel({
  dossierId,
  settings,
  peutRegler,
  onSettings,
}: {
  dossierId: string;
  settings: DataroomSettings;
  /** Employe responsable du dossier ou superviseur (le serveur le verifie aussi). */
  peutRegler: boolean;
  onSettings: (s: DataroomSettings) => void;
}) {
  // Confirmation discrete (toast "Enregistre") quand la coquille fournit les toasts.
  const toast = useContext(ToastContext);
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [historique, setHistorique] = useState<HistoriqueAccesClient[] | null>(null);
  const [version, setVersion] = useState(0);
  const [copie, setCopie] = useState<string | null>(null);
  const suspendu = settings.accessStatus === 'SUSPENDED';
  const lien = `${window.location.origin}/data-rooms`;

  useEffect(() => {
    if (!peutRegler) return undefined;
    let actif = true;
    dataroomService
      .historiqueAccesClient(dossierId)
      .then((h) => actif && setHistorique(h))
      .catch((err) => actif && setErreur(extractError(err).message));
    return () => {
      actif = false;
    };
  }, [dossierId, peutRegler, version]);

  async function appliquer(action: () => Promise<DataroomSettings>) {
    setEnCours(true);
    setErreur(null);
    try {
      onSettings(await action());
      setVersion((v) => v + 1);
      toast?.enregistre();
    } catch (err) {
      setErreur(extractError(err).message);
    } finally {
      setEnCours(false);
    }
  }

  async function copier() {
    try {
      await navigator.clipboard.writeText(lien);
      setCopie('Lien copié.');
    } catch {
      setCopie('Copie impossible : sélectionnez le lien et copiez-le.');
    }
  }

  return (
    <section
      aria-labelledby="acces-client-titre"
      className="@container space-y-4 rounded-xl border border-border bg-bg-raised p-5 shadow-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="acces-client-titre" className="flex items-center gap-1 text-base font-semibold text-heading">
          Accès du client
          <InfoBulle
            libelle="Que règle l’accès du client ?"
            texte="Ce que le client de ce dossier peut faire dans sa Data Room. Ces réglages valent pour ce dossier seulement ; chaque modification est enregistrée dans l’historique ci-dessous."
          />
        </h2>
        <div className="flex items-center gap-2">
          <Badge variant={suspendu ? 'danger' : 'success'} dot>
            {suspendu ? 'Accès suspendu' : 'Accès actif'}
          </Badge>
          {peutRegler && (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              loading={enCours}
              onClick={() => void appliquer(() => dataroomService.toggleSuspension(dossierId, !suspendu))}
            >
              {suspendu ? 'Réactiver l’accès' : 'Suspendre l’accès'}
            </Button>
          )}
        </div>
      </div>

      {peutRegler ? (
        <TexteAide cle="acces-client" titre="Régler l’accès du client">
          <p>
            Cochez ce que le client peut faire : la modification s’applique tout de suite. Pour couper
            l’accès un temps, utilisez « Suspendre l’accès » : le client ne voit plus rien jusqu’à la
            réactivation, et ses réglages sont conservés. Seuls
            l’employé responsable du dossier et le superviseur modifient ces réglages.
          </p>
        </TexteAide>
      ) : (
        <p className="text-sm text-fg-muted">
          Seuls l’employé responsable du dossier et le superviseur modifient l’accès du client.
        </p>
      )}

      <fieldset disabled={!peutRegler || enCours} className="grid gap-x-5 gap-y-2 @md:grid-cols-2">
        <legend className="sr-only">Permissions du client</legend>
        {PERMISSIONS.map((p) => (
          <Case
            key={p.cle}
            label={p.libelle}
            title={p.aide}
            checked={settings[p.cle]}
            disabled={!peutRegler || enCours}
            onChange={(e) =>
              void appliquer(() => dataroomService.updatePermissions(dossierId, { [p.cle]: e.target.checked }))
            }
          />
        ))}
      </fieldset>

      <div className="space-y-1.5">
        <p className="flex items-center gap-1 text-sm font-medium text-fg">
          Lien d’accès du client
          <InfoBulle
            libelle="À quoi sert le lien d’accès ?"
            texte="L’adresse à communiquer au client. Il s’y connecte avec son propre compte (celui de l’invitation) et retrouve sa Data Room. Sans compte, le lien ne donne accès à rien."
          />
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <code className="max-w-full break-all rounded-md border border-border bg-bg-overlay px-2 py-1 font-mono text-xs text-fg">
            {lien}
          </code>
          <Button type="button" size="sm" variant="secondary" icon={<Copy />} onClick={() => void copier()}>
            Copier le lien
          </Button>
          {copie && (
            <span role="status" className="text-xs text-fg-muted">
              {copie}
            </span>
          )}
        </div>
      </div>

      {erreur && <Message ton="erreur">{erreur}</Message>}

      {peutRegler && (
        <div className="space-y-1.5">
          <h3 className="text-sm font-semibold text-heading">Historique des modifications</h3>
          {historique === null && !erreur && (
            <div className="space-y-1.5" aria-busy="true" aria-label="Chargement de l’historique">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3" />
            </div>
          )}
          {historique && historique.length === 0 && (
            <p className="text-sm text-fg-muted">Aucune modification depuis l’ouverture de la Data Room.</p>
          )}
          {historique && historique.length > 0 && (
            <ol className="divide-y divide-border rounded-lg border border-border">
              {historique.map((l) => (
                <li key={l.id} className="px-3 py-2 text-sm">
                  <p className="text-fg">{decrireChangement(l)}</p>
                  <p className="text-xs text-fg-muted">
                    {dateFr(l.createdAt)} · par {l.acteurNom ?? 'Compte inconnu'}
                  </p>
                </li>
              ))}
            </ol>
          )}
        </div>
      )}
    </section>
  );
}
