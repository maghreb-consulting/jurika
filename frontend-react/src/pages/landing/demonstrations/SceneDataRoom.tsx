import {
  Activity,
  ArrowLeft,
  ArrowRightLeft,
  ChevronDown,
  ChevronRight,
  Copy,
  Download,
  Eye,
  FileDown,
  FileText,
  FileUp,
  Filter,
  History,
  IdCard,
  Info,
  MessageSquare,
  Printer,
  Search,
  Send,
  TicketPlus,
  Trash2,
  Upload,
  UserCheck,
  X,
} from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Case } from '../../../components/ui/Choix';
import { Onglets } from '../../../components/ui/Onglets';
import { Select } from '../../../components/ui/Select';
import { ZoneTexte } from '../../../components/ui/TextField';
import { nomFichierDocument, referenceDocument } from '../../../lib/referencesLisibles';
import { DEMANDE_STATUT_LABELS, DOCUMENT_TYPE_LABELS } from '../../../types/dataroom';
import { STATUT_LABELS_COURTS } from '../../../types/ticket';
import { cn } from '../../../lib/utils';
import { Coquille, Curseur, EncadreAide, PageEnTeteDemo } from './Briques';
import { ZoneFrappe } from './Briques';
import { DEMANDE_DEMO, DOCUMENTS_DATAROOM_DEMO, DOSSIER_TICKET_DEMO, SOCIETE_DEMO } from './donnees';
import type { ProprietesScene } from './ScenesTicket';

/**
 * Lot L6c E5 (retour de l'utilisateur du 2026-10-17) : demonstration de la Data Room d'un
 * dossier, cote employe, ecran reel a 1280 x 800 (DataroomPage, AccesClientPanel,
 * DossierJuridiqueTab, UploadDocumentDialog, PdfPreviewModal, DemandesTab), donnees fictives.
 *
 * Gestes montres, dans l'ordre : l'acces du client se regle (case "Deposer des documents") ;
 * le dossier juridique, classe par operation (dossier du ticket de creation, groupes "Actes
 * generes" et "Pieces client") ; "Ajouter un document" en "Nouvelle version de..." avec son
 * motif ; la nouvelle version (version 2, "Anciennes versions") ; l'apercu ; la demande du
 * client recue (cloche, onglet "Demandes du client", "Non traitee").
 *
 * Etapes : 0 : haut de la page ; 1 : curseur vers "Deposer des documents" ; 2 : clic, case
 * cochee ; 3 : defilement jusqu'au dossier juridique, curseur vers "Ajouter un document" ;
 * 4 : clic, fenetre "Ajouter un document", motif saisi ; 5 : clic sur "Remplacer comme nouvelle
 * version" (chargement) ; 6 : statuts en version 2 ; 7 : curseur sur "Apercu", clic ; 8 : apercu ;
 * 9 : une demande du client arrive (cloche), defilement jusqu'aux onglets, curseur vers
 * "Demandes du client" ; 10 : clic, la demande "Non traitee" (etat final) ; 11 : fondu.
 */

const TITRE_LISTE = 'Mes Data Rooms';
const DEFILEMENT = [0, 0, 0, 740, 740, 740, 740, 740, 740, 560, 560, 560];
const POSITIONS: [number, number][] = [
  [700, 520],
  [585, 718],
  [585, 718],
  [1045, 412],
  [1045, 412],
  [810, 608],
  [810, 608],
  [945, 552],
  [945, 552],
  [697, 345],
  [697, 345],
  [700, 520],
];

/** Permissions du client (AccesClientPanel, PERMISSIONS), dans l'ordre de l'ecran. */
const PERMISSIONS_DEMO = [
  { libelle: 'Consulter les documents', coche: true },
  { libelle: 'Télécharger', coche: true },
  { libelle: 'Imprimer', coche: true },
  { libelle: 'Déposer des documents', coche: false, cle: 'depot' },
  { libelle: 'Envoyer des demandes', coche: true },
];

const ONGLETS = [
  { id: 'juridique', libelle: 'Dossier juridique', icone: FileText },
  { id: 'depots', libelle: 'Dépôts du client', icone: Upload },
  { id: 'demandes', libelle: 'Demandes du client', icone: MessageSquare },
  { id: 'requetes', libelle: 'Requêtes du cabinet', icone: Send },
];

const AIDE_ONGLETS: Record<string, string> = {
  juridique:
    'Les documents juridiques de la société, rangés par ticket (une opération, un dossier). Ajoutez un document déposé ; consultez les versions remplacées ; téléchargez la fiche client ou la fiche dossier.',
  demandes:
    'Les demandes que le client adresse au cabinet. Prenez-les en charge, répondez, ou ouvrez un ticket quand une demande appelle une opération juridique.',
};

const rien = () => undefined;
const MOTIF = 'Correction de l’objet social demandée par la gérante';

function LigneDocument({ type, titre, date, version, surligne }: { type: keyof typeof DOCUMENT_TYPE_LABELS; titre: string; date: string; version: number; surligne?: boolean }) {
  return (
    <li className={cn('v-demo-vue flex flex-col gap-1 px-5 py-3', surligne && 'bg-accent-soft')}>
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <div className="flex min-w-0 items-center gap-3">
          <span className="h-4 w-4 shrink-0 rounded-sm border border-border-input" />
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-overlay">
            <FileText className="h-4 w-4 text-fg-muted" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-fg">{titre}</p>
            <p className="text-xs text-fg-muted">
              {referenceDocument(DOCUMENT_TYPE_LABELS[type], SOCIETE_DEMO.denomination, version)} · {date}
            </p>
          </div>
        </div>
        <div className="ml-auto flex shrink-0 items-center gap-1">
          <Button type="button" size="sm" variant="ghost" icon={<Eye />} tabIndex={-1}>
            Aperçu
          </Button>
          <Button type="button" size="sm" variant="secondary" icon={<Download />} tabIndex={-1}>
            Télécharger
          </Button>
          <Button type="button" size="sm" variant="ghost" iconOnly icon={<Trash2 className="text-danger" />} aria-label="Supprimer le document" tabIndex={-1} />
        </div>
      </div>
      <span className="inline-flex h-7 w-fit items-center gap-1 rounded-md px-2 text-xs font-medium text-fg-muted">
        <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
        <History className="h-3.5 w-3.5" aria-hidden="true" />
        Anciennes versions
      </span>
    </li>
  );
}

function OngletJuridique({ version }: { version: number }) {
  const groupes = ['Actes générés', 'Pièces client'];
  return (
    <div className="space-y-4">
      <Card padding={false} className="flex items-center gap-2 p-4">
        <span className="flex h-9 flex-1 items-center gap-2 rounded-lg border border-border-input bg-bg-raised px-3 text-sm text-fg-subtle">
          <Search className="h-4 w-4" aria-hidden="true" />
          Rechercher dans les documents juridiques (titre, nom de fichier)…
        </span>
        <Button type="button" variant="secondary" size="sm" icon={<Filter />} tabIndex={-1}>
          Filtres
        </Button>
      </Card>
      <Card
        padding={false}
        titleAs="h3"
        title={
          <span className="flex items-center gap-2">
            <FileText className="h-4 w-4 text-fg-muted" aria-hidden="true" />
            Documents en vigueur
            <Badge variant="neutral">{DOCUMENTS_DATAROOM_DEMO.length}</Badge>
            <Info className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
          </span>
        }
        actions={
          <div className="flex flex-wrap items-center justify-end gap-2">
            <Button type="button" size="sm" variant="secondary" icon={<IdCard />} tabIndex={-1}>
              Identifiants
            </Button>
            <Button type="button" size="sm" variant="secondary" icon={<FileDown />} tabIndex={-1}>
              Fiche client
            </Button>
            <Button type="button" size="sm" variant="secondary" icon={<FileDown />} tabIndex={-1}>
              Fiche dossier
            </Button>
            <Button type="button" size="sm" variant="secondary" icon={<Upload />} tabIndex={-1}>
              Ajouter plusieurs documents
            </Button>
            <Button type="button" size="sm" icon={<Upload />} tabIndex={-1}>
              Ajouter un document
            </Button>
          </div>
        }
      >
        <section className="border-b border-border last:border-b-0">
          <div className="flex w-full items-center gap-2 bg-bg-overlay px-5 py-2.5 text-left">
            <ChevronDown className="h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
            <span className="min-w-0 truncate text-sm font-semibold text-fg">{DOSSIER_TICKET_DEMO}</span>
            <Badge variant="info">{STATUT_LABELS_COURTS.DEROULEMENT_DEMARCHE}</Badge>
            <Badge variant="neutral" className="ml-auto">
              {DOCUMENTS_DATAROOM_DEMO.length}
            </Badge>
          </div>
          {groupes.map((g) => {
            const docs = DOCUMENTS_DATAROOM_DEMO.filter((d) => d.groupe === g);
            return (
              <div key={g} className="border-t border-border">
                <h4 className="flex items-center gap-2 px-5 pb-1 pt-3 text-xs font-semibold text-fg-muted">
                  {g}
                  <span className="tabular-nums text-fg-subtle">({docs.length})</span>
                </h4>
                <ul className="divide-y divide-border">
                  {docs.map((d) => {
                    const statuts = d.type === 'STATUTS';
                    return (
                      <LigneDocument
                        key={d.titre}
                        type={d.type}
                        titre={d.titre}
                        date={statuts && version > 1 ? '07/04/2026' : d.date}
                        version={statuts ? version : 1}
                        surligne={statuts && version > 1}
                      />
                    );
                  })}
                </ul>
              </div>
            );
          })}
        </section>
      </Card>
    </div>
  );
}

function OngletDemandes() {
  return (
    <div className="space-y-4">
      <Card padding={false} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
        <div className="flex flex-wrap items-center gap-2 text-sm text-fg-muted">
          <Badge variant="info">1 non traitée</Badge>
          <Badge variant="warning">0 en cours</Badge>
          <Badge variant="success">2 traitées</Badge>
          <Info className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
        </div>
      </Card>
      <Card padding={false}>
        <ul className="divide-y divide-border">
          <li className="px-5 py-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="text-sm font-medium text-fg">{DEMANDE_DEMO.sujet}</p>
                  <Badge variant="info">{DEMANDE_STATUT_LABELS.NON_TRAITEE}</Badge>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-sm text-fg-muted">{DEMANDE_DEMO.description}</p>
                <p className="mt-1 text-xs text-fg-muted">Soumise le {DEMANDE_DEMO.soumise}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Button type="button" size="sm" variant="secondary" icon={<TicketPlus />} tabIndex={-1}>
                  Créer un ticket
                </Button>
                <Button type="button" size="sm" variant="secondary" tabIndex={-1}>
                  Modifier
                </Button>
              </div>
            </div>
          </li>
        </ul>
      </Card>
    </div>
  );
}

export function SceneDataRoom({ etape, joue, statique }: ProprietesScene) {
  const depotPermis = statique || (etape >= 2 && etape <= 10);
  const fenetre = !statique && (etape === 4 || etape === 5);
  const version = statique || (etape >= 6 && etape <= 10) ? 2 : 1;
  const apercu = !statique && etape === 8;
  const demandes = statique || etape === 10;
  const cloche = statique || (etape >= 9 && etape <= 10) ? 2 : 1;
  const defilement = statique ? 560 : (DEFILEMENT[etape] ?? 0);
  const [cx, cy] = POSITIONS[etape] ?? [700, 520];
  const onglet = demandes ? 'demandes' : 'juridique';
  return (
    <div className={cn('v-demo-plan', etape === 11 && 'v-demo-sortie')}>
      <Coquille actif={TITRE_LISTE} defilement={defilement} cloche={cloche}>
        <PageEnTeteDemo
          fil={['Accueil', TITRE_LISTE, SOCIETE_DEMO.denomination]}
          titre={SOCIETE_DEMO.denomination}
          etat={
            <Badge variant="success" dot>
              Accès du client actif
            </Badge>
          }
          description={
            <div className="space-y-0.5">
              <p>SARL · {SOCIETE_DEMO.ville}</p>
              <p className="flex items-center gap-1 text-xs">
                Dossier DOS-2026-0007
                <Info className="h-3.5 w-3.5" aria-hidden="true" />
              </p>
            </div>
          }
          actions={
            <>
              <Button type="button" variant="ghost" icon={<ArrowLeft />} tabIndex={-1}>
                Retour aux Data Rooms
              </Button>
              <Button type="button" variant="secondary" icon={<Activity />} tabIndex={-1}>
                Activité du client
              </Button>
              <Button type="button" variant="secondary" icon={<ArrowRightLeft />} tabIndex={-1}>
                Transférer à un collègue
              </Button>
            </>
          }
        />
        <EncadreAide titre="Ajouter, supprimer, conserver" className="mb-6">
          <p>
            Les documents de la société sont conservés : une Data Room ne se supprime pas. L’employé responsable du dossier ajoute des documents ;
            il ne peut en supprimer que si le superviseur lui en a accordé le droit, et toute suppression est tracée. Un document supprimé reste en
            archive.
          </p>
        </EncadreAide>
        <div className="space-y-6">
          <div className="grid gap-4" style={{ gridTemplateColumns: 'minmax(0,1fr) minmax(0,20rem)' }}>
            {/* Acces du client (AccesClientPanel) */}
            <section className="@container space-y-4 rounded-xl border border-border bg-bg-raised p-5 shadow-card">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="flex items-center gap-1 text-base font-semibold text-heading">
                  Accès du client
                  <Info className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
                </h2>
                <div className="flex items-center gap-2">
                  <Badge variant="success" dot>
                    Accès actif
                  </Badge>
                  <Button type="button" size="sm" variant="secondary" tabIndex={-1}>
                    Suspendre l’accès
                  </Button>
                </div>
              </div>
              <EncadreAide titre="Régler l’accès du client">
                <p>
                  Cochez ce que le client peut faire : la modification s’applique tout de suite. Pour couper l’accès un temps, utilisez « Suspendre
                  l’accès » : le client ne voit plus rien jusqu’à la réactivation, et ses réglages sont conservés. Seuls l’employé responsable du
                  dossier et le superviseur modifient ces réglages.
                </p>
              </EncadreAide>
              <fieldset className="grid grid-cols-2 gap-x-5 gap-y-2">
                {PERMISSIONS_DEMO.map((p) => (
                  <Case key={p.libelle} label={p.libelle} checked={p.cle === 'depot' ? depotPermis : p.coche} onChange={rien} tabIndex={-1} />
                ))}
              </fieldset>
              <div className="space-y-1.5">
                <p className="flex items-center gap-1 text-sm font-medium text-fg">
                  Lien d’accès du client
                  <Info className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  <code className="max-w-full break-all rounded-md border border-border bg-bg-overlay px-2 py-1 font-mono text-xs text-fg">
                    https://cabinet-exemple.jurika.ma/data-rooms
                  </code>
                  <Button type="button" size="sm" variant="secondary" icon={<Copy />} tabIndex={-1}>
                    Copier le lien
                  </Button>
                </div>
              </div>
            </section>
            {/* Client rattache */}
            <Card title="Client rattaché" titleAs="h2">
              <div className="space-y-3">
                <div className="flex items-start gap-2">
                  <UserCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="font-medium text-fg">Yasmine Exemple</p>
                    <p className="truncate text-sm text-fg-muted">y.exemple@exemple.ma</p>
                  </div>
                </div>
                <p className="text-xs text-fg-muted">
                  Client avec accès : <strong className="text-fg">6</strong> · dernière consultation le 06/04/2026
                </p>
              </div>
            </Card>
          </div>

          <div>
            <Onglets
              libelle="Sections de la Data Room"
              actif={onglet}
              onChange={rien}
              onglets={ONGLETS.map((o) => {
                const Icone = o.icone;
                return {
                  id: o.id,
                  libelle: (
                    <>
                      <Icone className="h-4 w-4" aria-hidden="true" />
                      {o.libelle}
                    </>
                  ),
                };
              })}
            />
            <p className="mt-3 text-sm text-fg-muted">{AIDE_ONGLETS[onglet]}</p>
            <div className="mt-4">{demandes ? <OngletDemandes /> : <OngletJuridique version={version} />}</div>
          </div>
        </div>
      </Coquille>

      {/* Fenetre "Ajouter un document" (UploadDocumentDialog : Modal taille lg) */}
      <div className={cn('v-demo-voile absolute inset-0 bg-scrim/50', !fenetre && !apercu && 'v-demo-cachee')} />
      <div className={cn('v-demo-feuille absolute left-1/2 top-[90px] z-40 -ml-[336px] flex w-[672px] flex-col overflow-hidden rounded-xl border border-border bg-bg-raised text-fg shadow-card-lifted', !fenetre && 'v-demo-feuille-basse')}>
        <div className="flex items-start justify-between gap-4 border-b border-border px-6 py-4">
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold text-heading">Ajouter un document</h2>
            <div className="mt-1 text-sm text-fg-muted">Un nouveau document, ou une nouvelle version d’un document en vigueur (l’ancienne reste consultable).</div>
          </div>
          <span className="-mr-2 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-fg-subtle">
            <X className="h-4 w-4" aria-hidden="true" />
          </span>
        </div>
        <div className="space-y-4 px-6 py-5">
          <Select
            label="Destination"
            value="statuts"
            onChange={rien}
            hint="Choisissez « Nouvelle version de… » pour remplacer un document : un motif est alors demandé."
            options={[{ value: 'statuts', label: `${DOCUMENT_TYPE_LABELS.STATUTS} — Statuts de la SARL (version 1)` }]}
            tabIndex={-1}
          />
          {etape === 4 && joue ? (
            <ZoneFrappe label="Motif du remplacement" texte={MOTIF} joue={joue} rows={2} hint="Au moins 5 caractères : le motif est conservé avec l’ancienne version." required />
          ) : (
            <ZoneTexte
              label="Motif du remplacement"
              value={etape >= 5 ? MOTIF : ''}
              rows={2}
              hint="Au moins 5 caractères : le motif est conservé avec l’ancienne version."
              readOnly
              required
              tabIndex={-1}
            />
          )}
          <div className="space-y-1.5">
            <p className="text-sm font-medium text-fg">Fichier</p>
            <p className="flex items-center gap-3 text-sm text-fg-muted">
              <span className="inline-flex h-9 items-center rounded-lg border border-border-input bg-bg-raised px-3 text-sm font-medium text-fg">Parcourir…</span>
              statuts-oasis-exemple-v2.docx
            </p>
            <p className="text-xs text-fg-muted">statuts-oasis-exemple-v2.docx — 48 Ko</p>
          </div>
          <div className="flex items-center justify-end gap-2 border-t border-border pt-4">
            <Button type="button" variant="secondary" size="sm" tabIndex={-1}>
              Annuler
            </Button>
            <Button type="button" size="sm" icon={<FileUp />} loading={etape === 5} tabIndex={-1}>
              Remplacer comme nouvelle version
            </Button>
          </div>
        </div>
      </div>

      {/* Apercu (PdfPreviewModal) */}
      <div className={cn('v-demo-feuille absolute inset-[24px] z-40 flex flex-col overflow-hidden rounded-xl bg-bg-raised shadow-2xl', !apercu && 'v-demo-feuille-basse')}>
        <div className="flex items-center justify-between border-b border-border px-5 py-3">
          <div className="flex min-w-0 items-center gap-2">
            <FileText className="h-5 w-5 shrink-0 text-accent" aria-hidden="true" />
            <h3 className="truncate text-sm font-semibold text-fg">{nomFichierDocument(referenceDocument(DOCUMENT_TYPE_LABELS.STATUTS, SOCIETE_DEMO.denomination, 2), 'statuts-oasis-exemple-v2.docx')}</h3>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" tabIndex={-1}>
              <Download className="mr-1 h-4 w-4" />
              Télécharger
            </Button>
            <Button variant="secondary" size="sm" tabIndex={-1}>
              <Printer className="mr-1 h-4 w-4" />
              Imprimer
            </Button>
            <span className="rounded-full p-1.5 text-fg-subtle">
              <X className="h-5 w-5" aria-hidden="true" />
            </span>
          </div>
        </div>
        <div className="flex flex-1 justify-center overflow-hidden bg-bg-overlay pt-6">
          <div className="w-[600px] bg-bg-raised px-16 py-12 text-[13px] leading-relaxed text-fg shadow-card">
            <p className="text-center font-bold text-heading">{SOCIETE_DEMO.nom} — SARL</p>
            <p className="text-center uppercase">Société à responsabilité limitée</p>
            <p className="text-center uppercase">Au capital de {SOCIETE_DEMO.capital} dirhams</p>
            <p className="text-center uppercase">Siège social : {SOCIETE_DEMO.siege}</p>
            <p className="mt-5 text-center text-base font-bold tracking-[0.2em] text-heading">STATUTS</p>
            <p className="mt-5 font-semibold text-heading">ARTICLE 1 — FORME</p>
            <p className="text-justify">
              Il est formé entre les soussignés, propriétaires des parts ci-après créées et de celles qui pourraient l’être ultérieurement, une
              société à responsabilité limitée qui sera régie par les lois et règlements en vigueur […] ainsi que par les présents statuts.
            </p>
            <p className="mt-3 font-semibold text-heading">ARTICLE 2 — DÉNOMINATION SOCIALE</p>
            <p>La dénomination de la société est : « {SOCIETE_DEMO.nom} » SARL.</p>
            <p className="mt-3 font-semibold text-heading">ARTICLE 3 — OBJET SOCIAL</p>
            <p className="text-justify">
              La société a pour objet, tant au Maroc qu’à l’étranger, directement ou indirectement, pour son compte ou pour le compte de tiers :{' '}
              {SOCIETE_DEMO.objet}.
            </p>
            <p className="mt-3 font-semibold text-heading">ARTICLE 4 — SIÈGE SOCIAL</p>
            <p>Le siège social est fixé à : {SOCIETE_DEMO.siege}.</p>
          </div>
        </div>
      </div>
      <Curseur x={cx} y={cy} clic={[2, 4, 5, 7, 10].includes(etape) ? etape : 0} visible={!statique && etape >= 1 && etape <= 10 && etape !== 8} />
    </div>
  );
}
