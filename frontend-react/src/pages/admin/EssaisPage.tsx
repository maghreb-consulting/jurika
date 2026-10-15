import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Hourglass, RefreshCw } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Case } from '../../components/ui/Choix';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { PromptDialog } from '../../components/ui/PromptDialog';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { TexteAide } from '../../components/ui/Aide';
import { useToast } from '../../components/ui/useToast';
import { extractError } from '../../lib/api';
import { formaterDate } from '../../lib/essai';
import { essaiService, type EssaiAdmin } from '../../services/essai.service';

type Action =
  | { type: 'prolonger'; essai: EssaiAdmin }
  | { type: 'fin'; essai: EssaiAdmin }
  | { type: 'suspendre'; essai: EssaiAdmin }
  | { type: 'lever'; essai: EssaiAdmin };

function statutEssai(e: EssaiAdmin): { libelle: string; variant: 'success' | 'warning' | 'danger' | 'info' | 'neutral' } {
  if (e.supprimeLe) return { libelle: 'Supprimé', variant: 'neutral' };
  if (e.statut === 'SUSPENDED') return { libelle: 'Suspendu', variant: 'danger' };
  if (e.statutEssai === 'CONVERTED') return { libelle: 'Abonné', variant: 'success' };
  if (e.statutEssai === 'TRIAL_EXPIRED') return { libelle: 'Lecture seule', variant: 'warning' };
  if (e.statutEssai === 'TRIAL_ACTIVE') return { libelle: 'En cours', variant: 'info' };
  return { libelle: e.statutEssai ?? '--', variant: 'neutral' };
}

/**
 * Lot L7 (E4) : essais gratuits, ecran du super-admin.
 * RG-ESS-03 : inscriptions suspectes signalees (meme reseau, meme appareil), jamais bloquees ;
 * le super-admin peut suspendre l'essai. RG-ESS-11 : prolonger (une fois) ou mettre fin.
 */
export function EssaisPage() {
  const toast = useToast();
  const [essais, setEssais] = useState<EssaiAdmin[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [seulementSignales, setSeulementSignales] = useState(false);
  const [action, setAction] = useState<Action | null>(null);
  const [enCours, setEnCours] = useState(false);

  const recuperer = useCallback(
    () => essaiService.liste()
      .then((d) => { setEssais(d); setErreur(null); })
      .catch((e) => setErreur(extractError(e).message)),
    [],
  );

  useEffect(() => { void recuperer(); }, [recuperer]);

  const affiches = useMemo(
    () => (essais ?? []).filter((e) => !seulementSignales || (e.signalement && !e.signalementExamineLe)),
    [essais, seulementSignales],
  );
  const aExaminer = (essais ?? []).filter((e) => e.signalement && !e.signalementExamineLe && !e.supprimeLe).length;

  async function executer(travail: () => Promise<unknown>, succes: string) {
    setEnCours(true);
    try {
      await travail();
      toast.success(succes);
      setAction(null);
      await recuperer();
    } catch (e) {
      toast.error(`L’action a échoué : ${extractError(e).message}`);
    } finally {
      setEnCours(false);
    }
  }

  async function examiner(e: EssaiAdmin) {
    await executer(() => essaiService.signalementExamine(e.workspaceId), `Signalement de ${e.code} marqué comme examiné.`);
  }

  const colonnes: ColonneTableau<EssaiAdmin>[] = [
    {
      cle: 'cabinet',
      titre: 'Cabinet',
      rendu: (e) => (
        <>
          <p className="font-medium text-fg">{e.nom ?? 'Cabinet supprimé'}</p>
          <p className="font-mono text-xs text-fg-subtle">{e.code}</p>
          {e.contactEmail && <p className="text-xs text-fg-muted">{e.contactEmail}</p>}
          <p className="text-xs text-fg-subtle">Inscrit le {formaterDate(e.creeLe)}</p>
        </>
      ),
    },
    {
      cle: 'statut',
      titre: 'Statut',
      rendu: (e) => {
        const s = statutEssai(e);
        return (
          <>
            <Badge variant={s.variant}>{s.libelle}</Badge>
            {e.prolongeLe && <p className="mt-1 text-xs text-fg-muted">Prolongé de {e.prolongationJours} jour(s)</p>}
            {e.finMotif && <p className="mt-1 text-xs text-fg-muted">Fin décidée : {e.finMotif}</p>}
          </>
        );
      },
    },
    {
      cle: 'fin',
      titre: 'Fin de l’essai',
      rendu: (e) => (
        <span className="text-xs text-fg-muted">
          {e.supprimeLe
            ? `Données supprimées le ${formaterDate(e.supprimeLe)}`
            : e.statutEssai === 'TRIAL_EXPIRED' && e.expireLe
              ? `Lecture seule depuis le ${formaterDate(e.expireLe)}`
              : formaterDate(e.fin)}
        </span>
      ),
    },
    {
      cle: 'signalement',
      titre: 'Signalement',
      rendu: (e) =>
        e.signalement ? (
          <div className="text-xs">
            <p className="flex items-start gap-1 text-warning">
              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              {e.signalement}
            </p>
            {e.adresseIp && <p className="font-mono text-fg-subtle">IP {e.adresseIp}</p>}
            {e.signalementExamineLe ? (
              <p className="text-fg-subtle">Examiné le {formaterDate(e.signalementExamineLe)}</p>
            ) : (
              !e.supprimeLe && (
                <Button type="button" size="sm" variant="secondary" className="mt-1" onClick={() => { void examiner(e); }}>
                  Marquer examiné
                </Button>
              )
            )}
          </div>
        ) : (
          <span className="text-xs text-fg-subtle">Aucun</span>
        ),
    },
    {
      cle: 'actions',
      titre: 'Actions',
      rendu: (e) => {
        const ouvert = !e.supprimeLe && (e.statutEssai === 'TRIAL_ACTIVE' || e.statutEssai === 'TRIAL_EXPIRED');
        return ouvert ? (
          <div className="flex flex-wrap gap-1">
            {!e.prolongeLe && (
              <Button type="button" size="sm" variant="secondary" onClick={() => setAction({ type: 'prolonger', essai: e })}>Prolonger</Button>
            )}
            {e.statutEssai === 'TRIAL_ACTIVE' && (
              <Button type="button" size="sm" variant="secondary" onClick={() => setAction({ type: 'fin', essai: e })}>Mettre fin</Button>
            )}
            {e.statut === 'SUSPENDED' ? (
              <Button type="button" size="sm" variant="secondary" onClick={() => setAction({ type: 'lever', essai: e })}>Lever la suspension</Button>
            ) : (
              <Button type="button" size="sm" variant="danger" onClick={() => setAction({ type: 'suspendre', essai: e })}>Suspendre</Button>
            )}
          </div>
        ) : (
          <span className="text-xs text-fg-subtle">--</span>
        );
      },
    },
  ];

  return (
    <Page
      titre="Essais gratuits"
      description="Les essais de 7 jours des cabinets, leurs signalements et leur suite."
      infoBulle="Chaque cabinet n’a droit qu’à un essai de 7 jours. Une inscription venue du même réseau ou du même appareil qu’un autre essai est signalée ici ; elle n’est jamais bloquée automatiquement, un réseau partagé n’étant pas une preuve de fraude."
      actions={
        <Button type="button" variant="secondary" onClick={() => { void recuperer(); }} icon={<RefreshCw />}>
          Actualiser
        </Button>
      }
      aide={
      <TexteAide cle="admin.essais" titre="Ce que vous pouvez faire">
        Examinez les inscriptions signalées, puis marquez-les comme examinées ou suspendez l’essai en cas d’abus
        (la suspension se lève ensuite). Vous pouvez prolonger un essai une seule fois, ou y mettre fin : le cabinet
        passe alors en lecture seule pendant 30 jours, puis ses données sont supprimées après avertissement, sauf s’il
        souscrit un forfait.
      </TexteAide>
      }
    >
    <div className="space-y-6">
      {aExaminer > 0 && (
        <Message ton="attention">
          {aExaminer === 1 ? '1 inscription signalée reste à examiner.' : `${aExaminer} inscriptions signalées restent à examiner.`}
        </Message>
      )}

      <Case
        label="Afficher seulement les signalements à examiner"
        checked={seulementSignales}
        onChange={(e) => setSeulementSignales(e.target.checked)}
      />

      {erreur ? (
        <Message ton="erreur" titre="La liste des essais n’a pas pu être chargée">
          La liste des essais n’a pas pu être chargée : {erreur}
        </Message>
      ) : (
        <Tableau
          legende="Essais gratuits des cabinets"
          colonnes={colonnes}
          lignes={affiches}
          cleLigne={(e) => e.workspaceId}
          chargement={essais === null}
          enTeteCollant={false}
          vide={
            <EmptyState
              compact
              icon={<Hourglass />}
              title={seulementSignales ? 'Aucun signalement à examiner.' : 'Aucun essai gratuit pour l’instant'}
              description={seulementSignales ? 'Décochez le filtre pour revoir tous les essais.' : 'Les inscriptions à l’essai apparaîtront ici, avec leurs éventuels signalements.'}
            />
          }
        />
      )}

      <PromptDialog
        open={action?.type === 'prolonger'}
        onOpenChange={(o) => { if (!o) setAction(null); }}
        title="Prolonger l’essai"
        description="Un essai ne se prolonge qu’une fois. Indiquez le nombre de jours ajoutés (de 1 à 30) ; un essai en lecture seule redevient actif."
        label="Nombre de jours"
        defaultValue=""
        loading={enCours}
        validate={(v) => (/^\d+$/.test(v.trim()) && Number(v) >= 1 && Number(v) <= 30 ? null : 'Indiquez un nombre de jours entre 1 et 30.')}
        confirmLabel="Prolonger"
        onConfirm={(v) => {
          if (action?.type === 'prolonger') {
            const e = action.essai;
            void executer(() => essaiService.prolonger(e.workspaceId, Number(v.trim())), `Essai de ${e.code} prolongé de ${v.trim()} jour(s).`);
          }
        }}
      />
      <PromptDialog
        open={action?.type === 'fin'}
        onOpenChange={(o) => { if (!o) setAction(null); }}
        title="Mettre fin à l’essai"
        description="Le cabinet passe aussitôt en lecture seule pendant 30 jours ; il est averti par courriel et dans l’application de la date de suppression de ses données."
        label="Motif"
        multiline
        variant="danger"
        loading={enCours}
        validate={(v) => (v.trim() ? null : 'Indiquez le motif de la fin de l’essai.')}
        confirmLabel="Mettre fin"
        onConfirm={(v) => {
          if (action?.type === 'fin') {
            const e = action.essai;
            void executer(() => essaiService.mettreFin(e.workspaceId, v.trim()), `Essai de ${e.code} terminé.`);
          }
        }}
      />
      <ConfirmDialog
        open={action?.type === 'suspendre' || action?.type === 'lever'}
        onOpenChange={(o) => { if (!o) setAction(null); }}
        title={action?.type === 'lever' ? 'Lever la suspension' : 'Suspendre l’essai'}
        description={action?.type === 'lever'
          ? 'Les utilisateurs du cabinet pourront de nouveau se connecter.'
          : 'Les utilisateurs du cabinet ne pourront plus se connecter tant que la suspension n’est pas levée. Aucune donnée n’est supprimée.'}
        variant={action?.type === 'lever' ? 'primary' : 'danger'}
        confirmLabel={action?.type === 'lever' ? 'Lever la suspension' : 'Suspendre'}
        loading={enCours}
        onConfirm={() => {
          if (action?.type === 'suspendre') {
            const e = action.essai;
            void executer(() => essaiService.suspendre(e.workspaceId), `Essai de ${e.code} suspendu.`);
          } else if (action?.type === 'lever') {
            const e = action.essai;
            void executer(() => essaiService.leverSuspension(e.workspaceId), `Suspension de ${e.code} levée.`);
          }
        }}
      />
    </div>
    </Page>
  );
}
