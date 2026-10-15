import { useState, type ReactNode } from 'react';
import { Download, FileText, Plus, Save, Search, Trash2, User } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Lien } from '../../components/ui/Lien';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { Case, GroupeRadio, Interrupteur } from '../../components/ui/Choix';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Message } from '../../components/ui/Message';
import { FilAriane } from '../../components/ui/FilAriane';
import { Onglets } from '../../components/ui/Onglets';
import { Menu, MenuElement, MenuSeparateur } from '../../components/ui/Menu';
import { Tableau, type TriTableau } from '../../components/ui/Tableau';
import { EmptyState } from '../../components/ui/EmptyState';
import { CardSkeleton, SqueletteTexte } from '../../components/ui/LoadingSkeleton';
import { ErrorState } from '../../components/ui/ErrorState';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Modal } from '../../components/ui/Modal';
import { Drawer } from '../../components/ui/Drawer';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/useToast';

/**
 * Lot L6b (E1) : page /design (super-admin) : tous les composants de la bibliotheque, dans
 * leurs etats, en theme clair et en theme sombre cote a cote (zones [data-theme]).
 * Les donnees affichees sont des EXEMPLES de presentation (libelles generiques), aucune
 * donnee de cabinet.
 */

const JETONS = [
  'bg', 'bg-raised', 'bg-overlay', 'border', 'border-input', 'fg', 'fg-muted', 'fg-subtle', 'heading',
  'nav-bg', 'nav-active', 'accent', 'accent-soft', 'focus', 'success', 'success-soft', 'warning',
  'warning-soft', 'danger', 'danger-soft', 'info', 'info-soft',
];

interface LigneExemple {
  id: string;
  dossier: string;
  etape: string;
  etat: 'success' | 'warning' | 'danger' | 'neutral';
  libelleEtat: string;
}

const LIGNES: LigneExemple[] = [
  { id: '1', dossier: 'Dossier exemple A', etape: 'Saisie des informations', etat: 'neutral', libelleEtat: 'Brouillon' },
  { id: '2', dossier: 'Dossier exemple B', etape: 'Dépôt au greffe', etat: 'warning', libelleEtat: 'En attente' },
  { id: '3', dossier: 'Dossier exemple C', etape: 'Clôture', etat: 'success', libelleEtat: 'Terminé' },
  { id: '4', dossier: 'Dossier exemple D', etape: 'Pièces du client', etat: 'danger', libelleEtat: 'Bloqué' },
];

function Section({ titre, children }: { titre: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="border-b border-border pb-1 text-sm font-semibold uppercase tracking-wide text-fg-muted">{titre}</h2>
      {children}
    </section>
  );
}

function Vitrine({ theme }: { theme: 'light' | 'dark' }) {
  const toast = useToast();
  const [onglet, setOnglet] = useState('a');
  const [radio, setRadio] = useState<string | null>('mensuel');
  const [inter, setInter] = useState(true);
  const [tri, setTri] = useState<TriTableau | null>({ cle: 'dossier', sens: 'asc' });
  const [fenetre, setFenetre] = useState(false);
  const [tiroir, setTiroir] = useState(false);
  const [confirmer, setConfirmer] = useState(false);
  const suffixe = theme === 'light' ? 'clair' : 'sombre';

  return (
    <div
      data-theme={theme}
      className="@container min-w-0 space-y-8 rounded-xl border border-border bg-bg p-5 text-fg"
      aria-label={theme === 'light' ? 'Thème clair' : 'Thème sombre'}
      role="region"
    >
      <p className="text-base font-semibold text-heading">{theme === 'light' ? 'Thème clair' : 'Thème sombre'}</p>

      <Section titre="Couleurs (jetons)">
        <ul className="grid grid-cols-2 gap-2 @md:grid-cols-3">
          {JETONS.map((j) => (
            <li key={j} className="flex items-center gap-2 text-xs text-fg-muted">
              <span className="h-6 w-6 shrink-0 rounded-md border border-border" style={{ background: `var(--jeton-${j})` }} />
              <code className="font-mono">{j}</code>
            </li>
          ))}
        </ul>
      </Section>

      <Section titre="Typographie">
        <p className="text-3xl font-semibold text-heading">Titre de niveau 1</p>
        <p className="text-xl font-semibold text-heading">Titre de section</p>
        <p className="text-sm text-fg">Texte courant : l’acte est généré à partir des données du dossier.</p>
        <p className="text-sm text-fg-muted">Texte secondaire : précisions et compléments.</p>
        <p className="text-xs text-fg-subtle">Texte d’appoint : date, aide, mention discrète.</p>
      </Section>

      <Section titre="Boutons">
        <div className="flex flex-wrap items-center gap-2">
          <Button icon={<Plus />}>Principal</Button>
          <Button variant="secondary">Secondaire</Button>
          <Button variant="ghost">Discret</Button>
          <Button variant="danger" icon={<Trash2 />}>
            Supprimer
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm">Petit</Button>
          <Button size="md">Moyen</Button>
          <Button size="lg">Grand</Button>
          <Button loading>Enregistrement…</Button>
          <Button disabled>Désactivé</Button>
          <Button variant="secondary" iconOnly aria-label="Télécharger" icon={<Download />} />
        </div>
      </Section>

      <Section titre="Liens">
        <p className="text-sm">
          Un <Lien to="/design">lien interne</Lien>, un{' '}
          <Lien href="https://www.example.org" externe>
            lien externe
          </Lien>{' '}
          et un <Lien to="/design" variante="discret">lien discret</Lien>.
        </p>
      </Section>

      <Section titre="Champs">
        <div className="grid gap-4 @lg:grid-cols-2">
          <TextField id={`design-nom-${suffixe}`} label="Dénomination" placeholder="Ex. : Société exemple" hint="Telle qu’elle figure au certificat négatif." />
          <TextField id={`design-ice-${suffixe}`} label="ICE" defaultValue="12" error="L’ICE compte 15 chiffres." mono />
          <TextField id={`design-capital-${suffixe}`} label="Capital (dirhams)" type="number" defaultValue={100000} />
          <TextField id={`design-date-${suffixe}`} label="Date de l’assemblée" type="date" />
          <TextField id={`design-off-${suffixe}`} label="Champ désactivé" disabled defaultValue="Non modifiable" />
          <Select
            id={`design-forme-${suffixe}`}
            label="Forme juridique"
            options={[
              { value: '', label: 'Choisir…' },
              { value: 'SARL', label: 'SARL' },
              { value: 'SARLAU', label: 'SARL AU' },
            ]}
          />
        </div>
        <ZoneTexte id={`design-objet-${suffixe}`} label="Objet social" hint="Une activité par ligne." />
      </Section>

      <Section titre="Cases, choix, interrupteur">
        <Case label="J’ai vérifié les pièces" description="Obligatoire avant le dépôt." defaultChecked />
        <Case label="Option indisponible" disabled />
        <GroupeRadio
          legende="Rythme de paiement"
          name={`design-rythme-${suffixe}`}
          horizontal
          value={radio}
          onChange={setRadio}
          options={[
            { value: 'mensuel', label: 'Mensuel' },
            { value: 'annuel', label: 'Annuel' },
          ]}
        />
        <Interrupteur checked={inter} onChange={setInter} label="Alertes de délais" description="Un rappel avant chaque échéance." />
      </Section>

      <Section titre="Badges d’état">
        <div className="flex flex-wrap gap-2">
          <Badge>Actif</Badge>
          <Badge variant="success" dot>
            Terminé
          </Badge>
          <Badge variant="warning" dot>
            En attente
          </Badge>
          <Badge variant="danger" dot>
            Bloqué
          </Badge>
          <Badge variant="info">Information</Badge>
          <Badge variant="neutral">Brouillon</Badge>
        </div>
      </Section>

      <Section titre="Messages">
        <Message ton="info" titre="À savoir">
          Les actes sont produits à partir des données saisies.
        </Message>
        <Message ton="succes">Les pièces ont été déposées.</Message>
        <Message ton="attention" titre="Donnée attendue" action={<Button size="sm" variant="secondary">Réclamer</Button>}>
          Le numéro RC n’est pas encore connu.
        </Message>
        <Message ton="erreur" onFermer={() => undefined}>
          La génération a échoué : la dénomination manque.
        </Message>
      </Section>

      <Section titre="Aides">
        <p className="flex items-center gap-1 text-sm">
          Infobulle <InfoBulle texte="Le texte d’aide s’affiche au survol, au focus ou au clic." />
        </p>
        <TexteAide cle={`design-aide-${suffixe}`} titre="Encadré d’aide">
          <p>Ce qu’il faut faire, ce qui manque, ce qui vient ensuite. Masquable, puis réaffichable.</p>
        </TexteAide>
      </Section>

      <Section titre="Carte, fil d’Ariane, onglets, menu">
        <FilAriane elements={[{ libelle: 'Accueil', to: '/design' }, { libelle: 'Tickets', to: '/design' }, { libelle: 'Dossier exemple' }]} />
        <Card title="Carte avec en-tête" description="Description courte." actions={<Button size="sm" variant="secondary">Modifier</Button>}>
          <p className="text-sm text-fg-muted">Contenu de la carte.</p>
        </Card>
        <Card accent className="p-5">
          <p className="text-sm">Carte mise en avant (filet or).</p>
        </Card>
        <Onglets
          libelle="Exemple d’onglets"
          actif={onglet}
          onChange={setOnglet}
          onglets={[
            { id: 'a', libelle: 'Informations', contenu: <p className="text-sm">Premier onglet.</p> },
            { id: 'b', libelle: 'Documents', badge: <Badge variant="neutral">3</Badge>, contenu: <p className="text-sm">Deuxième onglet.</p> },
            { id: 'c', libelle: 'Historique', contenu: <p className="text-sm">Troisième onglet.</p> },
            { id: 'd', libelle: 'Indisponible', disabled: true },
          ]}
        />
        <Menu libelle="Actions du dossier" bouton={<span className="px-3 py-1.5 text-sm font-medium">Actions</span>} classeBouton="border border-border-input bg-bg-raised">
          <MenuElement icone={<FileText />} onSelect={() => toast.info('Ouverture du dossier')}>
            Ouvrir
          </MenuElement>
          <MenuElement icone={<User />} onSelect={() => toast.info('Changement de responsable')}>
            Changer de responsable
          </MenuElement>
          <MenuSeparateur />
          <MenuElement icone={<Trash2 />} danger onSelect={() => setConfirmer(true)}>
            Supprimer
          </MenuElement>
        </Menu>
      </Section>

      <Section titre="Tableau">
        <Tableau
          legende="Dossiers d’exemple"
          lignes={LIGNES}
          cleLigne={(l) => l.id}
          tri={tri}
          onTri={(cle) => setTri((t) => ({ cle, sens: t?.cle === cle && t.sens === 'asc' ? 'desc' : 'asc' }))}
          hauteurMax="16rem"
          colonnes={[
            { cle: 'dossier', titre: 'Dossier', triable: true, rendu: (l) => <span className="font-medium">{l.dossier}</span> },
            { cle: 'etape', titre: 'Étape', masquerSous: 'md', rendu: (l) => <span className="text-fg-muted">{l.etape}</span> },
            { cle: 'etat', titre: 'État', triable: true, rendu: (l) => <Badge variant={l.etat}>{l.libelleEtat}</Badge> },
          ]}
        />
        <Tableau legende="Chargement" lignes={[] as LigneExemple[]} cleLigne={(l) => l.id} chargement lignesChargement={2} colonnes={[{ cle: 'a', titre: 'Dossier', rendu: () => null }, { cle: 'b', titre: 'État', rendu: () => null }]} />
        <Tableau
          legende="Vide"
          lignes={[] as LigneExemple[]}
          cleLigne={(l) => l.id}
          colonnes={[{ cle: 'a', titre: 'Dossier', rendu: () => null }]}
          vide={<EmptyState compact title="Aucun dossier pour le moment" description="Créez un ticket : son dossier apparaîtra ici." />}
        />
      </Section>

      <Section titre="États vides, chargement, erreur">
        <Card>
          <EmptyState
            icon={<Search />}
            title="Aucun résultat"
            description="Aucun ticket ne correspond à ces filtres. Retirez un filtre ou cherchez un autre nom."
            cta={<Button size="sm">Retirer les filtres</Button>}
          />
        </Card>
        <CardSkeleton />
        <SqueletteTexte lignes={3} />
        <ErrorState onRetry={() => toast.info('Nouvel essai')} />
      </Section>

      <Section titre="Fenêtres et confirmations">
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => setFenetre(true)}>
            Ouvrir une fenêtre
          </Button>
          <Button variant="secondary" onClick={() => setTiroir(true)}>
            Ouvrir un tiroir
          </Button>
          <Button variant="secondary" icon={<Save />} onClick={() => toast.enregistre()}>
            Confirmation discrète
          </Button>
          <Button variant="secondary" onClick={() => toast.error('Échec de l’enregistrement : réessayez.')}>
            Toast d’erreur
          </Button>
        </div>
      </Section>

      <Modal
        open={fenetre}
        onOpenChange={setFenetre}
        title="Fenêtre d’exemple"
        description="Une fenêtre modale native (<dialog>)."
        footer={
          <>
            <Button variant="secondary" onClick={() => setFenetre(false)}>
              Annuler
            </Button>
            <Button onClick={() => setFenetre(false)}>Valider</Button>
          </>
        }
      >
        <TextField label="Champ dans la fenêtre" />
      </Modal>
      <Drawer open={tiroir} onClose={() => setTiroir(false)} title="Tiroir d’exemple" subtitle="Panneau latéral" footer={<Button onClick={() => setTiroir(false)}>Fermer</Button>}>
        <SqueletteTexte lignes={30} />
      </Drawer>
      <ConfirmDialog
        open={confirmer}
        onOpenChange={setConfirmer}
        variant="danger"
        title="Supprimer ce dossier ?"
        description="Exemple : aucune donnée n’est supprimée."
        confirmLabel="Supprimer"
        onConfirm={() => setConfirmer(false)}
      />
    </div>
  );
}

export function DesignPage() {
  return (
    <Page
      titre="Système de design"
      description="Tous les composants de la bibliothèque, dans leurs états, en thème clair et en thème sombre."
      infoBulle="Page de référence pour les écrans : un composant absent d’ici ne doit pas être réinventé dans un écran."
    >
      <div className="grid gap-6 @5xl/page:grid-cols-2">
        <Vitrine theme="light" />
        <Vitrine theme="dark" />
      </div>
    </Page>
  );
}
