/**
 * Lot L4d (E11d) : saisies de l'etape 1 de la dissolution lues par le proces-verbal du
 * corpus (PV_DISSOLUTION_ANTICIPEE_*) : precisions propres au motif et mandat du liquidateur
 * (duree, comptes rendus, pouvoirs). Rien n'est propose par defaut : le cabinet n'a pas
 * d'usage decide pour ces donnees.
 */
import { TextField } from '../ui/TextField';
import { TexteAide } from '../ui/Aide';
import type { DetailPvDissolution } from '../../lib/liquidationPv';

interface Props {
  valeurs: DetailPvDissolution;
  onChange: (patch: DetailPvDissolution) => void;
  /** Message d'erreur affiche sous un champ (validation de la page hote). */
  erreur?: (cle: string) => string | undefined;
}

function Zone({ id, label, value, onChange, hint, error }: {
  id: string; label: string; value: string; onChange: (v: string) => void; hint?: string; error?: string;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-fg-muted">{label}</label>
      <textarea id={id} data-testid={id} rows={3} value={value} onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? true : undefined}
        className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm" />
      {hint && <p className="mt-1 text-[11px] text-fg-subtle">{hint}</p>}
      {error && <p className="mt-1 text-xs text-danger" role="alert">{error}</p>}
    </div>
  );
}

/** Precisions demandees par le rapport de la gerance selon la nature de la dissolution. */
export function PrecisionsMotifFields({ nature, valeurs: v, onChange, erreur }: Props & { nature: string }) {
  if (nature === 'realisation_objet' || nature === 'extinction_objet') {
    return (
      <Zone id="dissolution-objet-evenement" label="Événement qui a réalisé ou éteint l'objet social *"
        value={v.objetEvenement ?? ''} onChange={(x) => onChange({ objetEvenement: x })}
        error={erreur?.('objetEvenement')}
        hint="Complète la phrase du procès-verbal : « l’objet social est entièrement réalisé, …, et que la société est ainsi privée de son objet ». Ex. : le chantier pour lequel la société a été constituée ayant été livré." />
    );
  }
  if (nature === 'situation_nette_art86') {
    return (
      <div className="grid gap-3 md:grid-cols-2" data-testid="dissolution-art86">
        <TextField label="Situation nette (dirhams) *" type="number" value={v.situationNetteMontant ?? ''}
          data-testid="dissolution-situation-nette" onChange={(e) => onChange({ situationNetteMontant: e.target.value })}
          error={erreur?.('situationNetteMontant')} />
        <TextField label="Comptes approuvés le *" type="date" value={v.approbationComptesDate ?? ''}
          data-testid="dissolution-approbation-comptes" onChange={(e) => onChange({ approbationComptesDate: e.target.value })}
          error={erreur?.('approbationComptesDate')}
          hint="La décision doit intervenir dans les trois mois de cette approbation (article 86 de la loi 5-96)." />
        <div className="md:col-span-2">
          <Zone id="dissolution-art86-motifs" label="Pourquoi la reconstitution des capitaux propres est-elle écartée ? *"
            value={v.art86MotifsEcartement ?? ''} onChange={(x) => onChange({ art86MotifsEcartement: x })}
            error={erreur?.('art86MotifsEcartement')} />
        </div>
      </div>
    );
  }
  if (nature === 'cause_statutaire') {
    return (
      <Zone id="dissolution-clause-texte" label="Circonstances de la survenance de la cause *"
        value={v.clauseTexte ?? ''} onChange={(x) => onChange({ clauseTexte: x })}
        error={erreur?.('clauseTexte')} />
    );
  }
  return null;
}

/** Mandat du liquidateur : duree, comptes rendus aux associes, pouvoirs particuliers. */
export function MandatLiquidateurFields({ valeurs: v, onChange, erreur }: Props) {
  return (
    <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4" data-testid="dissolution-mandat-liquidateur">
      <h4 className="text-sm font-semibold text-fg">Mandat du liquidateur</h4>
      <TexteAide cle="dissolution-mandat-liquidateur" titre="Ce que le procès-verbal fixe pour le liquidateur">
        <p>
          Le procès-verbal fixe la durée du mandat du liquidateur et la périodicité de ses comptes
          rendus aux associés : indiquez-les tels que décidés. Les pouvoirs types sont déjà rédigés ;
          ajoutez seulement des pouvoirs particuliers ou une limitation s’il y en a. Ces données sont
          reprises à la clôture de la liquidation.
        </p>
      </TexteAide>
      <div className="grid gap-3 md:grid-cols-2">
        <TextField label="Durée du mandat *" value={v.dureeMandat ?? ''} data-testid="dissolution-duree-mandat"
          onChange={(e) => onChange({ dureeMandat: e.target.value })} placeholder="Ex. : trois ans"
          error={erreur?.('dureeMandat')} hint="Imprimée : « Son mandat est conféré pour une durée de … »." />
        <TextField label="Périodicité des comptes rendus *" value={v.redditionPeriodicite ?? ''}
          data-testid="dissolution-reddition" onChange={(e) => onChange({ redditionPeriodicite: e.target.value })}
          placeholder="Ex. : chaque semestre" error={erreur?.('redditionPeriodicite')}
          hint="Imprimée : « il établira et adressera aux associés, …, un compte rendu écrit »." />
        <Zone id="dissolution-pouvoirs-complementaires" label="Pouvoirs particuliers (facultatif, un par ligne)"
          value={v.pouvoirsComplementaires ?? ''} onChange={(x) => onChange({ pouvoirsComplementaires: x })} />
        <Zone id="dissolution-pouvoirs-limitation" label="Limitation des pouvoirs (facultatif)"
          value={v.pouvoirsLimitation ?? ''} onChange={(x) => onChange({ pouvoirsLimitation: x })}
          hint="Complète : « le liquidateur ne pourra, sans une décision préalable des associés : … »." />
      </div>
    </section>
  );
}
