import { Check, ChevronRight, Plus } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Select } from '../../../components/ui/Select';
import { FriseDemarches } from '../../../components/tickets/FriseDemarches';
import { FriseEtapes } from '../../../components/workflow/FriseEtapes';
import { ETAPES_CREATION } from '../../workflow/aidesParcours';
import { FORME_JURIDIQUE_LABELS, STATUT_LABELS, TICKET_TYPE_LABELS } from '../../../types/ticket';
import { cn } from '../../../lib/utils';
import { ChampFixe, ChampFrappe, Coquille, Curseur, EncadreAide } from './Briques';
import { LIBELLE_COCHEE_DEMO, ORDRE_COCHEE_DEMO, SOCIETE_DEMO, TICKETS_DEMO, TITRE_TICKETS_DEMO, vueDemarchesDemo } from './donnees';

/**
 * Lot L6c (E1) : scenes "creation d'un ticket" (accroche) et "frise des demarches".
 * Etapes : voir scenes.ts (durees) ; `etape` est l'etape en cours, `joue` dit si la scene
 * avance (saisie lettre a lettre).
 *
 * L6c E3 : libelles alignes sur l'application (page "Mes tickets", tiroir "Nouveau ticket"
 * de NewTicketDrawer, en-tete du parcours, champs de l'etape 1, bouton "Cocher" des
 * demarches) ; coquille au menu reel de l'employe.
 */

export interface ProprietesScene {
  etape: number;
  joue: boolean;
  statique: boolean;
}

const ETAPES_FRISE = ETAPES_CREATION.map((e) => ({ numero: e.number, libelle: e.label }));
const AIDE_DENOMINATION = ETAPES_CREATION[0];

const OPTIONS_TYPE = [{ value: 'CREATION', label: TICKET_TYPE_LABELS.CREATION }];
const OPTIONS_FORME = [{ value: 'SARL', label: FORME_JURIDIQUE_LABELS.SARL }];

/** Fil d'Ariane, comme le composant FilAriane (Accueil > Tickets > reference...). */
export function FilDemo({ elements }: { elements: string[] }) {
  return (
    <p className="flex items-center gap-1 text-[11px] text-fg-muted">
      {elements.map((e, i) => (
        <span key={e} className="flex items-center gap-1">
          {i > 0 && <ChevronRight className="h-3 w-3" aria-hidden="true" />}
          <span className={cn(i === elements.length - 1 && 'text-fg')}>{e}</span>
        </span>
      ))}
    </p>
  );
}

/* ------------------------------------------------- Creation d'un ticket ---- */
/* 0 : liste, curseur au repos ; 1 : curseur vers "Nouveau ticket" ; 2 : clic, tiroir ;
 * 3 : saisie du titre ; 4 : clic dans "Raison sociale" ; 5 : saisie ; 6 : clic sur
 * "Creer et ouvrir le parcours" (chargement) ; 7 : parcours, etape 1 et son aide (etat
 * final) ; 8 : fondu de sortie. */

const POSITIONS_CREATION: [number, number][] = [
  [380, 330],
  [722, 84],
  [722, 84],
  [640, 140],
  [640, 330],
  [640, 330],
  [700, 478],
  [700, 478],
  [380, 330],
];

export function SceneCreation({ etape, joue, statique }: ProprietesScene) {
  const tiroir = etape >= 2 && etape <= 6;
  const parcours = etape >= 7;
  const [cx, cy] = POSITIONS_CREATION[etape] ?? [380, 330];
  return (
    <div className={cn('v-demo-plan', etape === 8 && 'v-demo-sortie')}>
      <Coquille actif={TITRE_TICKETS_DEMO}>
        {/* Page "Mes tickets" */}
        <div className={cn('v-demo-vue absolute inset-0 p-5', parcours && 'v-demo-cachee')}>
          <FilDemo elements={['Accueil', TITRE_TICKETS_DEMO]} />
          <div className="mt-1 flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-semibold text-heading">{TITRE_TICKETS_DEMO}</h2>
              <p className="mt-1 max-w-[380px] text-xs text-fg-muted">
                Les opérations juridiques des dossiers dont vous êtes responsable, rangées par statut.
              </p>
            </div>
            <Button type="button" size="sm" icon={<Plus className="h-4 w-4" />} tabIndex={-1}>
              Nouveau ticket
            </Button>
          </div>
          <Card className="mt-4" padding={false}>
            <ul className="divide-y divide-border">
              {TICKETS_DEMO.map((t) => (
                <li key={t.titre} className="flex items-center justify-between gap-3 px-4 py-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-fg">{t.titre}</p>
                    <p className="text-xs text-fg-muted">{t.type}</p>
                  </div>
                  <Badge variant={t.ton} dot>
                    {t.statut}
                  </Badge>
                </li>
              ))}
            </ul>
          </Card>
        </div>

        {/* Parcours de creation, etape 1 (en-tete du parcours : titre du ticket, fil, etat) */}
        <div className={cn('v-demo-vue absolute inset-0 space-y-3 p-5', !parcours && 'v-demo-cachee')}>
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <FilDemo elements={['Accueil', 'Tickets', SOCIETE_DEMO.reference, 'Parcours']} />
              <h2 className="mt-1 truncate text-lg font-semibold text-heading">{SOCIETE_DEMO.titreTicket}</h2>
            </div>
            <Badge variant="default" dot>
              {STATUT_LABELS.CREATION_TICKET}
            </Badge>
          </div>
          <FriseEtapes etapes={ETAPES_FRISE} plusAvancee={1} />
          <EncadreAide titre={`Aide de l’étape « ${AIDE_DENOMINATION.label} »`}>
            <ul className="space-y-1 text-xs">
              <li>
                <span className="font-medium text-fg">À faire : </span>
                {AIDE_DENOMINATION.aide.faire}
              </li>
              <li>
                <span className="font-medium text-fg">Ensuite : </span>
                {AIDE_DENOMINATION.aide.ensuite}
              </li>
            </ul>
          </EncadreAide>
          <div className="grid grid-cols-2 gap-4">
            <ChampFixe label="Dénomination (raison sociale)" value={SOCIETE_DEMO.nom} required />
            <ChampFixe label="Numéro du certificat négatif" value="" />
          </div>
        </div>

      </Coquille>
      <div className={cn('v-demo-voile absolute inset-0 bg-scrim/30', !tiroir && 'v-demo-cachee')} />
      {/* Tiroir "Nouveau ticket" (NewTicketDrawer), sur toute la hauteur comme le Drawer */}
      <div className={cn('v-demo-tiroir absolute inset-y-0 right-0 flex w-[340px] flex-col border-l border-border bg-bg-raised shadow-card-lifted', !tiroir && 'v-demo-tiroir-ferme')}>
        <div className="border-b border-border px-5 py-2.5">
          <p className="text-base font-semibold text-heading">Nouveau ticket</p>
          <p className="mt-0.5 text-[11px] leading-snug text-fg-muted">Ouvrez le ticket d’une opération juridique : le parcours correspondant démarre aussitôt.</p>
        </div>
        <div className="flex-1 space-y-2.5 px-5 py-3">
          {etape === 3 ? (
            <ChampFrappe label="Titre" texte={SOCIETE_DEMO.titreTicket} joue={joue} required />
          ) : (
            <ChampFixe label="Titre" value={etape >= 4 ? SOCIETE_DEMO.titreTicket : ''} placeholder="Ex : Constitution Atlas Trading" required />
          )}
          <Select label="Type d’opération" value="CREATION" onChange={() => undefined} options={OPTIONS_TYPE} tabIndex={-1} />
          <fieldset className="space-y-2.5 rounded-lg border border-border bg-bg-overlay p-3">
            <legend className="px-1 text-sm font-semibold text-heading">Société à créer</legend>
            {etape === 5 ? (
              <ChampFrappe label="Raison sociale" texte={SOCIETE_DEMO.denomination} joue={joue} required />
            ) : (
              <ChampFixe label="Raison sociale" value={etape >= 6 ? SOCIETE_DEMO.denomination : ''} placeholder="Ex : ATLAS TRADING SARL" required />
            )}
            <Select label="Forme juridique" value="SARL" onChange={() => undefined} options={OPTIONS_FORME} tabIndex={-1} />
          </fieldset>
        </div>
        <div className="flex justify-end gap-2 border-t border-border px-5 py-2.5">
          <Button type="button" variant="secondary" size="sm" tabIndex={-1}>
            Annuler
          </Button>
          <Button type="button" size="sm" loading={etape === 6} tabIndex={-1}>
            Créer et ouvrir le parcours
          </Button>
        </div>
      </div>
      <Curseur x={cx} y={cy} clic={[2, 4, 6].includes(etape) ? etape : 0} visible={!statique && etape >= 1 && etape <= 6} />
    </div>
  );
}

/* ---------------------------------------------------- Frise des demarches ---- */
/* 0 : curseur au repos ; 1 : curseur vers "Cocher" ; 2 : clic (chargement) ;
 * 3 : demarche accomplie, frise a jour (etat final) ; 4 : fondu de sortie. */

export function SceneFrise({ etape, statique }: ProprietesScene) {
  const cochee = etape >= 3;
  const vue = vueDemarchesDemo(cochee);
  return (
    <div className={cn('v-demo-plan', etape === 4 && 'v-demo-sortie')}>
      <Coquille actif={TITRE_TICKETS_DEMO}>
        <div className="absolute inset-0 overflow-hidden p-4">
          <div className="mb-2 flex items-start justify-between gap-3">
            <div className="min-w-0">
              <FilDemo elements={['Accueil', 'Tickets', SOCIETE_DEMO.reference]} />
              <h2 className="mt-0.5 truncate text-base font-semibold text-heading">{SOCIETE_DEMO.titreTicket}</h2>
            </div>
            <Badge variant="info" dot>
              {STATUT_LABELS.DEROULEMENT_DEMARCHE}
            </Badge>
          </div>
          {/* Ligne de la demarche, comme dans le panneau des demarches du ticket */}
          <div className="mb-2 flex items-center justify-between gap-3 rounded-lg border border-border bg-bg-raised px-3 py-2 shadow-card">
            <p className="min-w-0 truncate text-xs text-fg">
              <span className="font-semibold tabular-nums">{ORDRE_COCHEE_DEMO}.</span> {LIBELLE_COCHEE_DEMO}
            </p>
            {cochee ? (
              <Badge variant="success">Accomplie</Badge>
            ) : (
              <Button type="button" size="sm" variant="secondary" icon={<Check className="h-4 w-4" />} loading={etape === 2} tabIndex={-1}>
                Cocher
              </Button>
            )}
          </div>
          <div className="v-demo-frise">
            <FriseDemarches vue={vue} />
          </div>
        </div>
      </Coquille>
      <Curseur x={etape === 0 ? 420 : 714} y={etape === 0 ? 330 : 134} clic={etape === 2 ? 1 : 0} visible={!statique && etape >= 1 && etape <= 2} />
    </div>
  );
}
