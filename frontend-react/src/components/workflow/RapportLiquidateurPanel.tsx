/**
 * Lot L5 (E4) : saisie des operations de liquidation pour le rapport final du liquidateur
 * (modeles du corpus RAPPORT_LIQUIDATEUR_SARL / _SARL_AU). Chaque tableau du rapport se
 * remplit en lignes ajoutables ; les totaux, le solde de chaque creancier, la tresorerie et le
 * resultat (boni ou mali) sont CALCULES et affiches, avec leur concordance avec les comptes de
 * l'etape 1 et les frais du proces-verbal de cloture. Rien n'est suppose : ce qui manque est
 * nomme ici, puis par la generation.
 */
import type { ReactNode } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Select } from '../ui/Select';
import { TextField } from '../ui/TextField';
import { InfoBulle, TexteAide } from '../ui/Aide';
import {
  COLONNES,
  EXISTENCE,
  calculer,
  formatDh,
  ligneVide,
  manquesRapport,
  type CleTableau,
  type ContexteRapport,
  type Ligne,
  type RapportLiquidateur,
} from '../../lib/rapportLiquidateur';

const OUI_NON = [
  { value: '', label: 'Choisir…' },
  { value: 'oui', label: 'Oui' },
  { value: 'non', label: 'Non' },
];

export interface RapportLiquidateurPanelProps {
  isAU: boolean;
  valeurs: RapportLiquidateur;
  onChange: (patch: RapportLiquidateur) => void;
  contexte: ContexteRapport;
}

function Bloc({ titre, aide, children }: { titre: string; aide?: string; children: ReactNode }) {
  return (
    <div className="space-y-3 rounded-lg border border-border p-3">
      <h5 className="flex items-center gap-1 text-xs font-semibold uppercase tracking-wide text-fg-muted">
        {titre}
        {aide && <InfoBulle libelle={`À propos : ${titre}`} texte={aide} />}
      </h5>
      {children}
    </div>
  );
}

function Tableau({ t, titre, lignes, onChange }: {
  t: CleTableau; titre: string; lignes: Ligne[]; onChange: (l: Ligne[]) => void;
}) {
  const colonnes = COLONNES[t];
  const maj = (i: number, cle: string, v: string) => onChange(lignes.map((l, j) => (j === i ? { ...l, [cle]: v } : l)));
  return (
    <div className="space-y-2" data-testid={`rapport-tableau-${t}`}>
      {lignes.length === 0 && <p className="text-xs italic text-fg-subtle">Aucune ligne.</p>}
      {lignes.map((l, i) => (
        <div key={i} className="rounded-md border border-border-hi p-2" data-testid={`rapport-${t}-ligne-${i}`}>
          <div className="mb-1 flex items-center justify-between">
            <span className="text-xs font-medium text-fg-muted">{titre} — ligne {i + 1}</span>
            <button type="button" aria-label={`Retirer la ligne ${i + 1}`}
              onClick={() => onChange(lignes.filter((_, j) => j !== i))}
              className="rounded p-1 text-fg-subtle hover:text-danger">
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
          <div className="grid gap-2 md:grid-cols-3">
            {colonnes.map((c) => (
              <TextField key={c.cle} label={c.libelle} value={l[c.cle] ?? ''}
                type={c.type === 'date' ? 'date' : 'text'}
                inputMode={c.type === 'montant' ? 'decimal' : undefined}
                hint={c.aide}
                data-testid={`rapport-${t}-${i}-${c.cle}`}
                onChange={(e) => maj(i, c.cle, e.target.value)} />
            ))}
          </div>
        </div>
      ))}
      <button type="button" onClick={() => onChange([...lignes, ligneVide(t)])}
        data-testid={`rapport-${t}-ajouter`}
        className="inline-flex items-center gap-1 rounded-md border border-accent/40 px-2 py-1 text-xs font-medium text-accent hover:bg-accent/10">
        <Plus className="h-3.5 w-3.5" aria-hidden="true" /> Ajouter une ligne
      </button>
    </div>
  );
}

export function RapportLiquidateurPanel({ isAU, valeurs: v, onChange, contexte }: RapportLiquidateurPanelProps) {
  const val = (k: keyof RapportLiquidateur) => (v[k] as string | undefined) ?? '';
  const set = (k: keyof RapportLiquidateur) => (x: string) => onChange({ [k]: x });
  const champ = (k: keyof RapportLiquidateur, label: string, extra: Record<string, unknown> = {}) => (
    <TextField label={label} value={val(k)} data-testid={`rapport-${k}`}
      onChange={(e) => set(k)(e.target.value)} {...extra} />
  );
  const date = (k: keyof RapportLiquidateur, label: string, hint?: string) => champ(k, label, { type: 'date', hint });
  const choix = (k: keyof RapportLiquidateur, label: string, hint?: string) => (
    <Select label={label} value={val(k)} data-testid={`rapport-${k}`} hint={hint}
      onChange={(e) => set(k)(e.target.value)} options={OUI_NON} />
  );
  const lignes = (t: CleTableau) => (v[t] as Ligne[] | undefined) ?? [];
  const tableau = (t: CleTableau, titre: string) => (
    <Tableau t={t} titre={titre} lignes={lignes(t)} onChange={(l) => onChange({ [t]: l })} />
  );
  /** Tableau facultatif : question oui / non, puis les lignes si "oui". */
  const facultatif = (t: CleTableau, question: string, titre: string, suite?: ReactNode) => {
    const cle = EXISTENCE[t] as keyof RapportLiquidateur;
    return (
      <>
        <div className="max-w-md">{choix(cle, question)}</div>
        {val(cle) === 'oui' && (
          <>
            {tableau(t, titre)}
            {suite}
          </>
        )}
      </>
    );
  };

  const k = calculer(v, contexte);
  const manques = manquesRapport(v, contexte, isAU);
  const dh = (c: number | null) => (c === null ? '—' : `${formatDh(c)} DH`);

  return (
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4" data-testid="rapport-liquidateur">
      <h4 className="text-sm font-semibold text-fg">Rapport final du liquidateur — opérations de liquidation</h4>
      <TexteAide cle="rapport-liquidateur" titre="Ce que le rapport du liquidateur demande">
        <p>
          Le rapport final rend compte {isAU ? 'à l’associé unique' : 'aux associés'} de toutes les opérations
          de la liquidation : réalisation de l’actif, apurement du passif, frais, personnel, contrats,
          diligences fiscales et sociales. Remplissez chaque tableau ligne par ligne, en dirhams. La
          plateforme calcule les totaux, le solde de chaque créancier, la trésorerie disponible et le
          résultat (boni ou mali) ; ils doivent concorder avec les comptes de l’étape 1 et les frais du
          procès-verbal de clôture. Les données déjà saisies (liquidateur, avis de clôture, retenue à la
          source, conservation des documents) sont reprises. Ensuite, générez le rapport avec le
          procès-verbal, relisez-le, puis validez-le.
        </p>
      </TexteAide>

      <Bloc titre="Période et rappel de la dissolution"
        aide="Le rapport rappelle la décision de dissolution et sa publicité. La date d’effet, l’inscription au registre du commerce et le journal d’annonces viennent de l’avis de clôture.">
        <div className="grid gap-3 md:grid-cols-2">
          {date('periodeDebut', 'Début de la période couverte *', 'En général, la date d’ouverture de la liquidation.')}
          {date('periodeFin', 'Fin de la période couverte *')}
          {!isAU && champ('dissolutionOrgane', 'Organe ayant décidé la dissolution *', {
            hint: 'Nommé en toutes lettres, par exemple « l’assemblée générale extraordinaire des associés ».',
          })}
          {date('dissolutionDateDecision', 'Date de la décision de dissolution *')}
          {champ('dissolutionMotifLibelle', 'Motif de la dissolution, rédigé en clair *')}
          {champ('dissolutionRcInscriptionNumero', 'Numéro de l’inscription modificative au registre du commerce', {
            hint: 'Exigé si la date de cette inscription figure dans l’avis de clôture.',
          })}
          {date('dissolutionDepotGreffeDate', 'Date du dépôt de l’acte de dissolution au greffe', 'Facultatif : sans date, la mention n’est pas imprimée.')}
          {date('dissolutionBoDateParution', 'Date de parution au Bulletin officiel', 'Facultatif.')}
          {date('inventaireDateArrete', 'Date d’arrêté de l’inventaire d’ouverture', 'Facultatif.')}
          {choix('prolongee', 'La liquidation s’est-elle prolongée au-delà de la période prévue ? *')}
          {val('prolongee') === 'oui' && (
            <>
              {champ('prolongationMotifs', 'Causes de la prolongation *')}
              {date('declarationResultatProvisoireDate', 'Date de la déclaration du résultat provisoire *')}
            </>
          )}
        </div>
      </Bloc>

      <Bloc titre="Réalisation de l’actif"
        aide="Une ligne par bien ou créance réalisé. La plus-value ou la moins-value de chaque réalisation se calcule par différence avec la valeur retenue à l’inventaire d’ouverture.">
        {tableau('realisations', 'Réalisation')}
        <p className="text-xs text-fg-muted" data-testid="rapport-total-actif">Total encaissé : <strong>{dh(k.actif)}</strong></p>
      </Bloc>

      <Bloc titre="Apurement du passif"
        aide="Une ligne par créancier. Le solde restant dû se calcule ; le rapport final déclare tout le passif connu acquitté.">
        {tableau('apurements', 'Créancier')}
        <p className="text-xs text-fg-muted" data-testid="rapport-total-passif">Total réglé : <strong>{dh(k.passif)}</strong></p>
      </Bloc>

      <Bloc titre="Créances irrécouvrables et passifs contestés">
        {facultatif('creancesIrrecouvrables', 'Des créances sont-elles irrécouvrables ? *', 'Créance')}
        {facultatif('passifsContestes', 'Des prétentions contre la société sont-elles contestées ? *', 'Passif contesté',
          <div className="max-w-xl">{champ('passifsContestesMesures', 'Mesures de consignation ou de provision retenues *')}</div>)}
      </Bloc>

      <Bloc titre="Cessions soumises à restriction"
        aide="Articles 365 et 366 de la loi n° 17-95 : cession à un ancien dirigeant, au commissaire aux comptes, au liquidateur ou à ses proches.">
        {facultatif('cessionsRestreintes', 'Des cessions entrent-elles dans le champ de ces articles ? *', 'Cession')}
      </Bloc>

      <Bloc titre="Frais et honoraires de liquidation"
        aide="Le total comprend la rémunération du liquidateur, si elle est due : ajoutez-la comme une ligne.">
        {tableau('frais', 'Frais')}
        <p className="text-xs text-fg-muted" data-testid="rapport-total-frais">Total des frais et honoraires : <strong>{dh(k.frais)}</strong></p>
        <div className="max-w-xs">{champ('fraisRembourses', 'Frais remboursés au liquidateur (DH) *', { inputMode: 'decimal', hint: '0 s’il n’y en a pas.' })}</div>
      </Bloc>

      <Bloc titre="Personnel et contrats en cours">
        {facultatif('personnel', 'La société occupait-elle des salariés pendant la période ? *', 'Salarié',
          <div className="grid gap-3 md:grid-cols-2">
            {champ('chargesPersonnelTotal', 'Total des salaires, indemnités et charges acquittés (DH) *', { inputMode: 'decimal' })}
            {choix('salariesDixOuPlus', 'Dix salariés ou plus habituellement ? *')}
            {val('salariesDixOuPlus') === 'oui' && (
              <>
                {date('autorisationDemandeDate', 'Date de la demande d’autorisation de fermeture *')}
                {champ('autorisationSuite', 'Suite donnée à la demande *')}
              </>
            )}
          </div>)}
        {facultatif('contrats', 'Des contrats étaient-ils en cours d’exécution ? *', 'Contrat',
          <div className="max-w-md">{choix('bauxExistent', 'Des baux d’immeubles figurent-ils parmi ces contrats ? *')}</div>)}
      </Bloc>

      <Bloc titre="Diligences fiscales et sociales"
        aide="Les dates limites ne sont jamais calculées par la plateforme : reportez celles du dossier.">
        {tableau('diligences', 'Diligence')}
        <div className="grid gap-3 md:grid-cols-2">
          {choix('tvaAssujettie', 'La société est-elle assujettie à la TVA ? *')}
          {val('tvaAssujettie') === 'oui' && date('tvaCessationDateLimite', 'Date limite de la déclaration de cessation (TVA) *')}
          {date('tpCessationDateLimite', 'Date limite de la déclaration de cessation (taxe professionnelle)', 'Exigée si la société a un identifiant de taxe professionnelle.')}
          {date('cnssRadiationDateLimite', 'Date limite de la radiation à la CNSS', 'Exigée si la société est affiliée à la CNSS.')}
          {champ('attestationsEtat', 'État des attestations fiscales et sociales *', { hint: 'Obtenues, demandées le…, en attente…' })}
        </div>
      </Bloc>

      <Bloc titre="Résultat de la liquidation"
        aide="Calcul : actif réalisé − passif réglé − frais et honoraires − impôt ; le capital est remboursé à concurrence de ce disponible ; le solde est un boni (réparti au prorata des parts) ou un mali.">
        <dl className="grid gap-1 text-xs text-fg-muted md:grid-cols-2" data-testid="rapport-resultat">
          <dt>Trésorerie disponible</dt><dd><strong>{dh(k.tresorerie)}</strong></dd>
          <dt>Résultat</dt>
          <dd><strong>{k.resultat ? `${k.resultat.sens} de ${formatDh(k.resultat.montant)} DH` : '—'}</strong></dd>
        </dl>
        <div className="grid gap-3 md:grid-cols-2">
          {k.resultat?.sens === 'boni' && champ('retenueObservations', 'Observations sur la retenue à la source', {
            hint: 'Imprimées seulement si la retenue à la source n’est pas applicable (choix du procès-verbal de clôture).',
          })}
          {k.resultat?.sens === 'mali' && champ('maliObservations', 'Observations sur l’origine du mali', { hint: 'Facultatif.' })}
          {champ('observationsGenerales', 'Observations générales du liquidateur', { hint: 'Facultatif.' })}
          {champ('resolutionsComplementaires', isAU ? 'Décisions complémentaires proposées' : 'Résolutions complémentaires proposées', { hint: 'Facultatif.' })}
        </div>
      </Bloc>

      <Bloc titre="Annexes et signature">
        {tableau('annexes', 'Annexe')}
        <div className="grid gap-3 md:grid-cols-2">
          {date('dateSignature', 'Date du rapport *', 'Le rapport est daté avant l’assemblée ou la décision de clôture.')}
          {champ('lieuSignature', 'Lieu de signature du rapport', { hint: 'À défaut, le lieu de signature du procès-verbal.' })}
        </div>
      </Bloc>

      {manques.length > 0 ? (
        <div role="status" className="rounded-md border border-warning/40 bg-warning/10 p-3 text-xs text-fg" data-testid="rapport-manques">
          <p className="mb-1 font-medium">Avant de générer le rapport, il reste à compléter :</p>
          <ul className="list-disc space-y-0.5 pl-5">
            {manques.map((x) => <li key={x}>{x}</li>)}
          </ul>
        </div>
      ) : (
        <p role="status" className="text-xs text-success" data-testid="rapport-complet">
          Le rapport est complet et concorde avec les comptes de clôture : vous pouvez le générer.
        </p>
      )}
    </section>
  );
}
