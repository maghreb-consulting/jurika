import { CheckCircle2, Plus } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Select } from '../../../components/ui/Select';
import { FriseDemarches } from '../../../components/tickets/FriseDemarches';
import { FriseEtapes } from '../../../components/workflow/FriseEtapes';
import { ETAPES_CREATION } from '../../workflow/aidesParcours';
import { STATUT_LABELS } from '../../../types/ticket';
import { cn } from '../../../lib/utils';
import { ChampFixe, ChampFrappe, Coquille, Curseur, EncadreAide } from './Briques';
import { LIBELLE_COCHEE_DEMO, SOCIETE_DEMO, TICKETS_DEMO, vueDemarchesDemo } from './donnees';

/**
 * Lot L6c (E1) : scenes "creation d'un ticket" (accroche) et "frise des demarches".
 * Etapes : voir scenes.ts (durees) ; `etape` est l'etape en cours, `joue` dit si la scene
 * avance (saisie lettre a lettre).
 */

export interface ProprietesScene {
  etape: number;
  joue: boolean;
  statique: boolean;
}

const ETAPES_FRISE = ETAPES_CREATION.map((e) => ({ numero: e.number, libelle: e.label }));
const AIDE_DENOMINATION = ETAPES_CREATION[0];

const OPTIONS_TYPE = [{ value: 'CREATION', label: 'Création' }];
const OPTIONS_FORME = [{ value: 'SARL', label: 'SARL' }];

/* ------------------------------------------------- Creation d'un ticket ---- */
/* 0 : liste, curseur au repos ; 1 : curseur vers "Nouveau ticket" ; 2 : clic, tiroir ;
 * 3 : saisie du titre ; 4 : clic dans "Raison sociale" ; 5 : saisie ; 6 : clic sur
 * "Creer et ouvrir le parcours" (chargement) ; 7 : parcours, etape 1 et son aide (etat
 * final) ; 8 : fondu de sortie. */

const POSITIONS_CREATION: [number, number][] = [
  [380, 330],
  [716, 44],
  [716, 44],
  [600, 148],
  [600, 322],
  [600, 322],
  [716, 474],
  [716, 474],
  [380, 330],
];

export function SceneCreation({ etape, joue, statique }: ProprietesScene) {
  const tiroir = etape >= 2 && etape <= 6;
  const parcours = etape >= 7;
  const [cx, cy] = POSITIONS_CREATION[etape] ?? [380, 330];
  return (
    <div className={cn('v-demo-plan', etape === 8 && 'v-demo-sortie')}>
      <Coquille actif="Tickets">
        {/* Liste des tickets */}
        <div className={cn('v-demo-vue absolute inset-0 p-6', parcours && 'v-demo-cachee')}>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-semibold text-heading">Tickets</h2>
              <p className="mt-1 text-sm text-fg-muted">Les opérations juridiques de vos dossiers, du début à la fin.</p>
            </div>
            <Button type="button" size="sm" icon={<Plus className="h-4 w-4" />} tabIndex={-1}>
              Nouveau ticket
            </Button>
          </div>
          <Card className="mt-5" padding={false}>
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

        {/* Parcours de creation, etape 1 */}
        <div className={cn('v-demo-vue absolute inset-0 space-y-4 p-6', !parcours && 'v-demo-cachee')}>
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs text-fg-subtle">Tickets / {SOCIETE_DEMO.titreTicket}</p>
              <h2 className="text-lg font-semibold text-heading">Création : {SOCIETE_DEMO.denomination}</h2>
            </div>
            <Badge variant="default" dot>
              {STATUT_LABELS.CREATION_TICKET}
            </Badge>
          </div>
          <FriseEtapes etapes={ETAPES_FRISE} plusAvancee={1} />
          <EncadreAide titre={`Aide de l’étape « ${AIDE_DENOMINATION.label} »`}>
            <ul className="space-y-1">
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
            <ChampFixe label="Dénomination" value={SOCIETE_DEMO.denomination} required />
            <ChampFixe label="Certificat négatif (facultatif)" placeholder="Numéro du certificat" value="" />
          </div>
        </div>

        {/* Tiroir "Nouveau ticket" */}
        <div className={cn('v-demo-tiroir absolute inset-y-0 right-0 flex w-[360px] flex-col border-l border-border bg-bg-raised shadow-card-lifted', !tiroir && 'v-demo-tiroir-ferme')}>
          <div className="border-b border-border px-5 py-3">
            <p className="text-base font-semibold text-heading">Nouveau ticket</p>
            <p className="mt-0.5 text-xs text-fg-muted">Le parcours correspondant démarre aussitôt.</p>
          </div>
          <div className="flex-1 space-y-3 px-5 py-4">
            {etape === 3 ? (
              <ChampFrappe label="Titre" texte={SOCIETE_DEMO.titreTicket} joue={joue} required />
            ) : (
              <ChampFixe label="Titre" value={etape >= 4 ? SOCIETE_DEMO.titreTicket : ''} placeholder="Ex : Constitution Atlas Trading" required />
            )}
            <Select label="Type d’opération" value="CREATION" onChange={() => undefined} options={OPTIONS_TYPE} tabIndex={-1} />
            <fieldset className="space-y-3 rounded-lg border border-border bg-bg-overlay p-3">
              <legend className="px-1 text-sm font-semibold text-heading">Société à créer</legend>
              {etape === 5 ? (
                <ChampFrappe label="Raison sociale" texte={SOCIETE_DEMO.denomination} joue={joue} required />
              ) : (
                <ChampFixe label="Raison sociale" value={etape >= 6 ? SOCIETE_DEMO.denomination : ''} placeholder="Ex : ATLAS TRADING SARL" required />
              )}
              <Select label="Forme juridique" value="SARL" onChange={() => undefined} options={OPTIONS_FORME} tabIndex={-1} />
            </fieldset>
          </div>
          <div className="flex justify-end gap-2 border-t border-border px-5 py-3">
            <Button type="button" variant="secondary" size="sm" tabIndex={-1}>
              Annuler
            </Button>
            <Button type="button" size="sm" loading={etape === 6} tabIndex={-1}>
              Créer et ouvrir le parcours
            </Button>
          </div>
        </div>
      </Coquille>
      <Curseur x={cx} y={cy} clic={[2, 4, 6].includes(etape) ? etape : 0} visible={!statique && etape >= 1 && etape <= 6} />
    </div>
  );
}

/* ---------------------------------------------------- Frise des demarches ---- */
/* 0 : curseur au repos ; 1 : curseur vers "Cocher la demarche" ; 2 : clic (chargement) ;
 * 3 : demarche accomplie, frise a jour (etat final) ; 4 : fondu de sortie. */

export function SceneFrise({ etape, statique }: ProprietesScene) {
  const cochee = etape >= 3;
  const vue = vueDemarchesDemo(cochee);
  return (
    <div className={cn('v-demo-plan h-full w-full overflow-hidden bg-bg p-5 text-fg', etape === 4 && 'v-demo-sortie')}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <p className="text-xs text-fg-subtle">
            {SOCIETE_DEMO.reference} · {SOCIETE_DEMO.denomination}
          </p>
          <h2 className="text-lg font-semibold text-heading">{SOCIETE_DEMO.titreTicket}</h2>
        </div>
        <Badge variant="info" dot>
          {STATUT_LABELS.DEROULEMENT_DEMARCHE}
        </Badge>
      </div>
      <div className="mb-3 flex items-center justify-between gap-3 rounded-lg border border-border bg-bg-raised px-4 py-2.5 shadow-card">
        <div className="min-w-0">
          <p className="text-[11px] font-medium uppercase tracking-wide text-fg-muted">Prochaine démarche</p>
          <p className="truncate text-sm font-medium text-fg">{cochee ? vue.avancement.prochainLibelle : LIBELLE_COCHEE_DEMO}</p>
        </div>
        {cochee ? (
          <Badge variant="success">
            <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
            Démarche cochée
          </Badge>
        ) : (
          <Button type="button" size="sm" loading={etape === 2} tabIndex={-1}>
            Cocher la démarche
          </Button>
        )}
      </div>
      <div className="v-demo-frise">
        <FriseDemarches vue={vue} />
      </div>
      <Curseur x={etape === 0 ? 420 : 712} y={etape === 0 ? 330 : 96} clic={etape === 2 ? 1 : 0} visible={!statique && etape >= 1 && etape <= 2} />
    </div>
  );
}
