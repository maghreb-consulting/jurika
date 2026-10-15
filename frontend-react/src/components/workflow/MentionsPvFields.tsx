/**
 * Lot L4d (E11d) : mentions et decisions des proces-verbaux du corpus de la dissolution
 * (PV_DISSOLUTION_ANTICIPEE_*) et de la cloture de la liquidation (PV_CLOTURE_LIQUIDATION_*)
 * qui ne viennent ni de la fiche de la societe ni de la seance. Chaque donnee qui decide
 * d'un paragraphe (commissaire aux comptes, quitus, effectif, retenue a la source, passif
 * eventuel...) est saisie, jamais supposee : sans elle, la generation la nomme.
 */
import type { ReactNode } from 'react';
import { Select } from '../ui/Select';
import { TextField, ZoneTexte } from '../ui/TextField';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { ValeurProposee } from './ValeurProposee';
import type { MentionsPv } from '../../lib/liquidationPv';

const OUI_NON = [
  { value: '', label: 'Choisir…' },
  { value: 'oui', label: 'Oui' },
  { value: 'non', label: 'Non' },
];

export interface MentionsPvFieldsProps {
  mode: 'dissolution' | 'liquidation';
  isAU: boolean;
  valeurs: MentionsPv;
  onChange: (patch: MentionsPv) => void;
  /** Reference du ticket, proposee comme reference du dossier. */
  referenceDossier?: string;
  /** Ville du siege ou du greffe, proposee comme lieu d'enregistrement. */
  villeEnregistrement?: string;
  /** Sens du quitus choisi dans l'avis de cloture (accorde, sous_reserve, sans_quitus). */
  quitusAvis?: string;
}

function Zone({ id, label, value, onChange, hint }: {
  id: string; label: string; value: string; onChange: (v: string) => void; hint?: string;
}) {
  return (
    <div className="md:col-span-2">
      <ZoneTexte id={id} label={label} hint={hint} data-testid={id} rows={3} value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

function Bloc({ titre, aide, children }: { titre: string; aide?: string; children: ReactNode }) {
  return (
    <div className="space-y-3 rounded-lg border border-border p-3">
      <h5 className="flex items-center gap-1 text-xs font-semibold uppercase tracking-wide text-fg-muted">
        {titre}
        {aide && <InfoBulle libelle={`À propos : ${titre}`} texte={aide} />}
      </h5>
      <div className="grid gap-3 md:grid-cols-2">{children}</div>
    </div>
  );
}

export function MentionsPvFields({
  mode, isAU, valeurs: v, onChange, referenceDossier, villeEnregistrement, quitusAvis,
}: MentionsPvFieldsProps) {
  const val = (k: string) => v[k] ?? '';
  const set = (k: string) => (x: string) => onChange({ [k]: x });
  const champ = (k: string, label: string, extra: Record<string, unknown> = {}) => (
    <TextField label={label} value={val(k)} data-testid={`mentions-${k}`}
      onChange={(e) => set(k)(e.target.value)} {...extra} />
  );
  const choix = (k: string, label: string, options = OUI_NON, hint?: string) => (
    <Select label={label} value={val(k)} data-testid={`mentions-${k}`} hint={hint}
      onChange={(e) => set(k)(e.target.value)} options={options} />
  );

  return (
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4" data-testid="mentions-pv">
      <h4 className="text-sm font-semibold text-fg">
        {mode === 'dissolution' ? 'Mentions du procès-verbal de dissolution' : 'Compte définitif et décisions de clôture'}
      </h4>
      <TexteAide cle={`mentions-pv-${mode}`} titre="Ce que le procès-verbal du cabinet demande encore">
        <p>
          {mode === 'dissolution'
            ? 'Le procès-verbal du cabinet mentionne le commissaire aux comptes, le quitus de la gérance, l’effectif salarié, le journal d’annonces légales, le folio du registre et le lieu d’enregistrement. '
            : 'Le procès-verbal de clôture reprend le compte définitif, l’approbation, le quitus, la répartition du solde, la retenue à la source, la conservation des documents et les passifs éventuels. '}
          Renseignez ces données : la plateforme calcule ce qui peut l’être (actif net, boni ou mali,
          répartition entre associés) et ne suppose rien. S’il manque une donnée, la génération vous
          la nomme. Ensuite, générez le procès-verbal, relisez-le, puis validez-le.
        </p>
      </TexteAide>

      <Bloc titre="Mentions de l’acte">
        <ValeurProposee id="mentions-dossierReference" label="Référence du dossier *"
          proposition={referenceDossier ?? ''} valeur={val('dossierReference') || undefined}
          onChange={set('dossierReference')} />
        {champ('registreFolio', isAU ? 'Folio du registre des décisions *' : 'Folio du registre des procès-verbaux *',
          { hint: 'Folio sur lequel le procès-verbal sera inscrit.' })}
        <ValeurProposee id="mentions-enregistrementLieu" label="Lieu d’enregistrement *"
          proposition={villeEnregistrement ?? ''} valeur={val('enregistrementLieu') || undefined}
          onChange={set('enregistrementLieu')} />
        {mode === 'dissolution' &&
          champ('annonceJournal', 'Journal d’annonces légales *', {
            hint: 'Journal où l’avis de dissolution sera publié (imprimé dans les pouvoirs pour les formalités).',
          })}
      </Bloc>

      <Bloc titre="Commissaire aux comptes"
        aide="Le procès-verbal mentionne le commissaire aux comptes en fonction, sa présence et le sort de son mandat pendant la liquidation. Sans commissaire, ces paragraphes disparaissent.">
        {choix('cacExiste', 'Un commissaire aux comptes est-il en fonction ? *')}
        {val('cacExiste') === 'oui' && (
          <>
            {champ('cacNom', 'Nom du commissaire aux comptes *')}
            {choix('cacPresent', 'Assiste-t-il à la réunion ? *')}
            {choix('cacMandatLiquidation', 'Sort de son mandat *', [
              { value: '', label: 'Choisir…' },
              { value: 'poursuite', label: 'Il poursuit sa mission pendant la liquidation' },
              { value: 'fin_de_mandat', label: 'Son mandat prend fin' },
              { value: 'reserve', label: 'Décision réservée' },
            ])}
            {val('cacMandatLiquidation') === 'fin_de_mandat' &&
              champ('cacFinMandatDateEffet', 'Date de fin du mandat *', { type: 'date' })}
          </>
        )}
      </Bloc>

      {mode === 'dissolution' && (
        <Bloc titre="Gérance et salariés"
          aide="Le quitus peut être donné à la gérance dès la dissolution ou réservé jusqu’à la clôture. Une entreprise de dix salariés ou plus doit obtenir l’autorisation de fermeture du gouverneur (articles 66, 67 et 69 du code du travail) : le procès-verbal le rappelle.">
          {choix('quitusGerance', 'Quitus donné à la gérance ? *', [
            { value: '', label: 'Choisir…' },
            { value: 'oui', label: 'Oui, quitus entier' },
            { value: 'non', label: 'Non, réservé à la clôture' },
          ])}
          {choix('salariesDixOuPlus', 'Dix salariés ou plus ? *')}
          {val('salariesDixOuPlus') === 'oui' && champ('salariesNombre', 'Effectif habituel *', { type: 'number' })}
        </Bloc>
      )}

      {mode === 'liquidation' && (
        <>
          <Bloc titre="Rappel de la dissolution"
            aide="Le procès-verbal rappelle l’inscription de la dissolution au registre du commerce (point de départ de la prescription de cinq ans, calculée) et la publication de la nomination du liquidateur.">
            {champ('dissolutionRcInscriptionDate', 'Inscription de la dissolution au RC *', { type: 'date' })}
            {champ('annonceJournal', 'Journal d’annonces légales de la nomination *')}
            {champ('annoncePublicationDate', 'Date de publication *', { type: 'date' })}
            {!isAU && choix('convocationAuteur', "Assemblée convoquée par *", [
              { value: '', label: 'Choisir…' },
              { value: 'liquidateur', label: 'Le liquidateur' },
              { value: 'mandataire', label: 'Un mandataire désigné en référé' },
            ])}
            {!isAU && val('convocationAuteur') === 'mandataire' && (
              <>
                {champ('ordonnanceAssocieDemandeur', 'Associé demandeur *')}
                {champ('ordonnanceTribunal', 'Ville du tribunal *')}
                {champ('ordonnanceNumero', 'Numéro de l’ordonnance *')}
                {champ('ordonnanceDate', 'Date de l’ordonnance *', { type: 'date' })}
                {champ('ordonnanceMandataireNom', 'Mandataire désigné *')}
              </>
            )}
          </Bloc>

          <Bloc titre="Compte définitif"
            aide="L’actif net disponible, le remboursement du nominal et le boni ou le mali sont calculés à partir des totaux de l’étape 1, des frais et de la rémunération du liquidateur. Le procès-verbal ne s’écrit pas sans les frais.">
            <Zone id="mentions-operationsResume" label="Résumé des opérations de liquidation *"
              value={val('operationsResume')} onChange={set('operationsResume')}
              hint="Complète la phrase : « Il expose les conditions dans lesquelles il a réalisé l’actif social… : … »." />
            {champ('frais', 'Frais et honoraires de liquidation (dirhams) *', { type: 'number' })}
            {champ('isMontant', 'Impôt sur les sociétés de la liquidation (dirhams)', {
              type: 'number', hint: 'Facultatif : à indiquer s’il est dû.',
            })}
            {choix('comptesBancairesClotures', 'Les comptes bancaires sont-ils clôturés ? *')}
            <Zone id="mentions-contestations" label="Contestations ou réclamations pendantes (facultatif)"
              value={val('contestations')} onChange={set('contestations')} />
            {choix('compteDefinitifApprobation', 'Le compte définitif est-il approuvé ? *', [
              { value: '', label: 'Choisir…' },
              { value: 'approuve', label: 'Approuvé' },
              { value: 'refuse', label: 'Refusé' },
            ])}
            {val('compteDefinitifApprobation') === 'refuse' && (
              <Zone id="mentions-refusMotifs" label="Motifs du refus *" value={val('refusMotifs')} onChange={set('refusMotifs')} />
            )}
          </Bloc>

          {val('compteDefinitifApprobation') !== 'refuse' && (
            <Bloc titre="Quitus et répartition du solde"
              aide="Le quitus se choisit dans l’avis de clôture ci-dessous ; ses réserves ou ses motifs de refus se saisissent ici. La retenue à la source sur le boni (article 13-I du code général des impôts) est indiquée par vous : la plateforme ne fixe ni son assiette ni son taux.">
              {!quitusAvis && (
                <p className="md:col-span-2 text-xs text-warning" role="status">
                  Choisissez le sens du quitus dans l’avis de clôture ci-dessous : le procès-verbal en dépend.
                </p>
              )}
              {quitusAvis === 'sous_reserve' && (
                <Zone id="mentions-quitusReserveTexte" label="Réserves du quitus *" value={val('quitusReserveTexte')} onChange={set('quitusReserveTexte')} />
              )}
              {quitusAvis === 'sans_quitus' && (
                <>
                  <Zone id="mentions-quitusRefusMotifs" label="Motifs du refus de quitus *" value={val('quitusRefusMotifs')} onChange={set('quitusRefusMotifs')} />
                  {champ('quitusRefusMandataireNom', 'Mandataire chargé des actions contre le liquidateur *')}
                </>
              )}
              {choix('retenueApplicable', 'Retenue à la source sur le boni ? *', OUI_NON,
                'Seulement en cas de boni ; sans boni, la réponse est sans effet.')}
              {val('retenueApplicable') === 'oui' && (
                <>
                  {champ('retenueAssiette', 'Assiette de la retenue (dirhams) *', { type: 'number' })}
                  {champ('retenueTaux', 'Taux applicable *', { placeholder: 'Ex. : 15 %' })}
                  {champ('retenueMontant', 'Montant global de la retenue (dirhams) *', { type: 'number' })}
                  {choix('retenueExonerationInvoquee', 'Un associé invoque-t-il une exonération ? *')}
                  {val('retenueExonerationInvoquee') === 'oui' && champ('retenueExonerationBeneficiaire', 'Associé bénéficiaire *')}
                </>
              )}
              {val('retenueApplicable') === 'non' &&
                champ('retenueNonApplicableMotif', 'Motif de l’absence de retenue *')}
              {champ('repartitionModalites', 'Modalités de paiement *', { placeholder: 'Ex. : par virement bancaire' })}
              {champ('repartitionDateLimite', 'Paiement au plus tard le *', { type: 'date' })}
              <Zone id="mentions-maliRepartition" label="Répartition du mali (facultatif)"
                value={val('maliRepartition')} onChange={set('maliRepartition')}
                hint="En cas de mali seulement : imputation entre associés selon l’état annexé." />
            </Bloc>
          )}

          <Bloc titre="Conservation des documents"
            aide="La déclaration du résultat final (45 jours après la clôture, date calculée) indique le lieu de conservation des documents comptables (article 150-II du code général des impôts).">
            {champ('conservationDepositaireNom', 'Dépositaire *')}
            {champ('conservationDepositaireQualite', 'Qualité du dépositaire *', { placeholder: 'Ex. : liquidateur' })}
            {champ('conservationLieu', 'Adresse de conservation *')}
            {champ('conservationDuree', 'Durée de conservation *', { placeholder: 'Ex. : dix ans' })}
          </Bloc>

          <Bloc titre="Passifs éventuels et radiation"
            aide="Sans passif éventuel, le procès-verbal constate qu’aucune provision n’est à constituer. Les pièces du dossier de radiation sont facultatives (une par ligne).">
            {choix('passifEventuelApplicable', 'Subsiste-t-il des passifs éventuels ? *')}
            {val('passifEventuelApplicable') === 'oui' && (
              <>
                {champ('passifEventuelNature', 'Nature des passifs éventuels *')}
                {champ('passifEventuelMontant', 'Montant estimé (dirhams) *', { type: 'number' })}
                {champ('passifEventuelModalites', 'Modalités de couverture *')}
                {champ('passifEventuelChargePersonne', 'Personne qui en assume la charge *')}
                {champ('passifEventuelSequestreNom', 'Séquestre (facultatif)')}
                {val('passifEventuelSequestreNom').trim() !== '' && champ('passifEventuelDuree', 'Durée de la consignation *')}
              </>
            )}
            <Zone id="mentions-piecesRadiation" label="Pièces du dossier de radiation (facultatif, une par ligne)"
              value={val('piecesRadiation')} onChange={set('piecesRadiation')} />
          </Bloc>
        </>
      )}
    </section>
  );
}
