/**
 * Saisie des données de la SUCCURSALE — lot DIVERS §B/§C (2026-08-13).
 *
 * Étape 2 des workflows SUCCURSALE_MA et SUCCURSALE_ETR (spec directeur : « saisie
 * identique »). C'est la **source unique** des variables `$SUCCURSALE_*` : le PV et
 * l'annonce légale consomment ce même bloc — aucun champ concurrent ailleurs.
 *
 * Contenu :
 *  - localisation : enseigne, adresse, ville, activité, date d'ouverture ;
 *  - **ville du greffe de la succursale** — distincte de celle du siège : la succursale
 *    s'immatricule au RC de son lieu d'exploitation (loi 15-95, art. 37 et 40) ;
 *  - **dotation** optionnelle : montant en chiffres (le montant en lettres est calculé
 *    côté serveur, jamais saisi) ;
 *  - **responsable / directeur** optionnel : personne physique ou morale, avec la même
 *    logique que la création (sélection **BD d'abord** parmi gérants et associés, sinon
 *    saisie externe assistée par l'OCR CIN recto-verso).
 */
import { useMemo } from 'react';
import { Building2, Coins, UserCheck } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';
import { Case } from '../ui/Choix';
import { IdentityExtractor } from '../identity/IdentityExtractor';
import { TypePieceProposee, ValeurProposee } from './ValeurProposee';
import { TexteAide } from '../ui/Aide';
import { buildLiquidateurOptions, EXTERNE_KEY } from './liquidateurOptions';
import type { DossierParties } from '../../services/workflow.service';
import type {
  ResponsableTypePersonne,
  SuccursaleResponsableState,
  SuccursaleSaisieErrors,
  SuccursaleSaisieState,
} from './succursaleSaisie';

export type {
  ResponsableSource,
  ResponsableTypePersonne,
  SuccursaleResponsableState,
  SuccursaleSaisieErrors,
  SuccursaleSaisieState,
} from './succursaleSaisie';

export interface SuccursaleSaisieFormProps {
  value: SuccursaleSaisieState;
  onChange: (next: SuccursaleSaisieState) => void;
  /** Parties prenantes du dossier mère (sélection BD du responsable). Null en ÉTRANGÈRE. */
  parties?: DossierParties | null;
  partiesLoading?: boolean;
  /** Dossier cible pour l'archivage Data Room de la CIN scannée. */
  dossierId?: string | null;
  /** Erreurs révélées par la page hôte (après tentative de validation). */
  errors?: SuccursaleSaisieErrors;
  /** Libellé du rôle (« responsable » en MA, « représentant résident » en ÉTRANGÈRE). */
  roleLabel?: string;
}

export function SuccursaleSaisieForm({
  value,
  onChange,
  parties,
  partiesLoading = false,
  dossierId,
  errors = {},
  roleLabel = 'Responsable de la succursale',
}: SuccursaleSaisieFormProps) {
  const set = (patch: Partial<SuccursaleSaisieState>) => onChange({ ...value, ...patch });
  const setResp = (patch: Partial<SuccursaleResponsableState>) =>
    onChange({ ...value, responsable: { ...value.responsable, ...patch } });

  // Sélection BD : gérants puis associés du dossier mère (aucune re-saisie d'identité).
  const options = useMemo(() => buildLiquidateurOptions(parties), [parties]);
  const r = value.responsable;
  const isMorale = r.typePersonne === 'MORALE';

  return (
    <div className="space-y-5">
      <TexteAide cle="succursale-saisie" titre="Ce qu’il faut faire">
        Renseignez la succursale (enseigne, activité, adresse, ville et greffe où elle sera
        immatriculée, date d’ouverture), puis, s’il y a lieu, sa dotation et son responsable.
        Ces données sont reprises telles quelles dans le procès-verbal et l’annonce légale de
        l’étape suivante : rien ne sera redemandé.
      </TexteAide>
      {/* ---------------- Localisation ---------------- */}
      <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4">
        <div className="flex items-center gap-2">
          <Building2 className="h-4 w-4 text-fg-muted" />
          <h4 className="text-sm font-semibold text-fg">Identification et localisation</h4>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <TextField
            label="Enseigne *"
            value={value.enseigne}
            data-testid="succ-enseigne"
            onChange={(e) => set({ enseigne: e.target.value })}
            error={errors.enseigne}
          />
          <TextField
            label="Activité *"
            value={value.activite}
            data-testid="succ-activite"
            onChange={(e) => set({ activite: e.target.value })}
            error={errors.activite}
          />
          <div className="md:col-span-2">
            <TextField
              label="Adresse *"
              value={value.adresse}
              data-testid="succ-adresse"
              onChange={(e) => set({ adresse: e.target.value })}
              error={errors.adresse}
            />
          </div>
          <TextField
            label="Ville *"
            value={value.ville}
            data-testid="succ-ville"
            onChange={(e) =>
              set({
                ville: e.target.value,
                // Le greffe suit la ville tant qu'il n'a pas été saisi explicitement.
                villeGreffe: value.villeGreffe ? value.villeGreffe : e.target.value,
              })
            }
            error={errors.ville}
          />
          <TextField
            label="Ville du greffe de la succursale *"
            value={value.villeGreffe}
            data-testid="succ-ville-greffe"
            onChange={(e) => set({ villeGreffe: e.target.value })}
            error={errors.villeGreffe}
            hint="La succursale s’immatricule au RC de son propre lieu d’exploitation, distinct de celui du siège."
          />
          <TextField
            label="Date d’ouverture *"
            type="date"
            value={value.dateOuverture}
            data-testid="succ-date-ouverture"
            onChange={(e) => set({ dateOuverture: e.target.value })}
            error={errors.dateOuverture}
          />
        </div>
      </section>

      {/* ---------------- Dotation ---------------- */}
      <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4">
        <div className="flex items-center gap-2">
          <Coins className="h-4 w-4 text-warning" />
          <h4 className="text-sm font-semibold text-fg">Dotation (facultative)</h4>
        </div>
        <Case
          label="Une dotation est affectée à la succursale"
          checked={value.dotationPresente}
          data-testid="succ-dotation-presente"
          onChange={(e) => set({ dotationPresente: e.target.checked })}
        />
        {value.dotationPresente && (
          <div className="grid gap-3 md:grid-cols-2">
            <TextField
              label="Montant de la dotation (MAD) *"
              type="number"
              value={value.dotationMontant}
              data-testid="succ-dotation-montant"
              onChange={(e) => set({ dotationMontant: e.target.value })}
              error={errors.dotation}
              hint="Le montant en lettres est calculé automatiquement — il n’est jamais saisi."
            />
          </div>
        )}
      </section>

      {/* ---------------- Responsable ---------------- */}
      <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4">
        <div className="flex items-center gap-2">
          <UserCheck className="h-4 w-4 text-fg-muted" />
          <h4 className="text-sm font-semibold text-fg">{roleLabel} (facultatif)</h4>
        </div>
        <Case
          label={`Un ${roleLabel.toLowerCase()} est désigné`}
          checked={r.present}
          data-testid="succ-responsable-present"
          onChange={(e) => setResp({ present: e.target.checked })}
        />

        {r.present && (
          <div className="space-y-4">
            <Select
              label="Type de personne"
              value={r.typePersonne}
              onChange={(e) =>
                setResp({ typePersonne: e.target.value as ResponsableTypePersonne })
              }
              options={[
                { value: 'PHYSIQUE', label: 'Personne physique' },
                { value: 'MORALE', label: 'Personne morale' },
              ]}
            />

            {/* Sélection BD d'abord : un gérant / associé déjà connu n'est jamais re-saisi. */}
            {!isMorale && options.length > 0 && (
              <Select
                label="Choisir une partie prenante du dossier"
                value={r.source === 'BD' ? r.partieKey : EXTERNE_KEY}
                data-testid="succ-responsable-partie"
                onChange={(e) => {
                  const key = e.target.value;
                  if (key === EXTERNE_KEY || !key) {
                    setResp({ source: 'EXTERNE', partieKey: '' });
                    return;
                  }
                  const opt = options.find((o) => o.key === key);
                  if (!opt) return;
                  setResp({
                    source: 'BD',
                    partieKey: key,
                    civilite: opt.civilite,
                    prenom: opt.prenom,
                    nom: opt.nom,
                    pieceNumero: opt.cin,
                    adresse: opt.adresse,
                  });
                }}
                options={[
                  { value: '', label: partiesLoading ? 'Chargement…' : '— Sélectionner —' },
                  ...options.map((o) => ({ value: o.key, label: o.label })),
                  { value: EXTERNE_KEY, label: 'Autre personne (saisie externe)' },
                ]}
                hint="Les gérants et associés du dossier sont proposés en premier : leur identité n’est jamais ressaisie."
              />
            )}

            {/* OCR CIN recto-verso — uniquement en saisie externe (personne physique). */}
            {!isMorale && r.source === 'EXTERNE' && (
              <IdentityExtractor
                mode="cin"
                dossierId={dossierId ?? undefined}
                onApply={(values) => {
                  const patch: Partial<SuccursaleResponsableState> = {};
                  if (values.nom) patch.nom = values.nom;
                  if (values.prenom) patch.prenom = values.prenom;
                  if (values.cin) patch.pieceNumero = values.cin.toUpperCase();
                  if (values.nationalite) patch.nationalite = values.nationalite;
                  if (values.adresse) patch.adresse = values.adresse;
                  if (values.sexe) {
                    patch.civilite = values.sexe.toUpperCase() === 'F' ? 'Mme' : 'M.';
                  }
                  setResp(patch);
                }}
              />
            )}

            <div className="grid gap-3 md:grid-cols-2">
              {isMorale ? (
                <>
                  <div className="md:col-span-2">
                    <TextField
                      label="Dénomination *"
                      value={r.denomination}
                      data-testid="succ-responsable-denomination"
                      onChange={(e) => setResp({ denomination: e.target.value })}
                    />
                  </div>
                  <TextField
                    label="Représentant légal"
                    value={r.representantNom}
                    onChange={(e) => setResp({ representantNom: e.target.value })}
                  />
                  <TextField
                    label="Nationalité / pays"
                    value={r.nationalite}
                    onChange={(e) => setResp({ nationalite: e.target.value })}
                  />
                </>
              ) : (
                <>
                  <Select
                    label="Civilité"
                    value={r.civilite}
                    onChange={(e) => setResp({ civilite: e.target.value })}
                    options={[
                      { value: 'M.', label: 'M.' },
                      { value: 'Mme', label: 'Mme' },
                      { value: 'Mlle', label: 'Mlle' },
                    ]}
                  />
                  <TextField
                    label="Prénom"
                    value={r.prenom}
                    data-testid="succ-responsable-prenom"
                    onChange={(e) => setResp({ prenom: e.target.value })}
                  />
                  <TextField
                    label="Nom *"
                    value={r.nom}
                    data-testid="succ-responsable-nom"
                    onChange={(e) => setResp({ nom: e.target.value })}
                  />
                  <ValeurProposee
                    id="succ-responsable-nationalite"
                    label="Nationalité"
                    proposition="marocaine"
                    valeur={r.nationalite || undefined}
                    onChange={(v) => setResp({ nationalite: v })}
                  />
                </>
              )}
              <div className="md:col-span-2">
                <TextField
                  label="Adresse *"
                  value={r.adresse}
                  data-testid="succ-responsable-adresse"
                  onChange={(e) => setResp({ adresse: e.target.value })}
                />
              </div>
              {isMorale ? (
                <TextField
                  label="Type de pièce"
                  value={r.pieceType}
                  onChange={(e) => setResp({ pieceType: e.target.value })}
                  placeholder="RC"
                />
              ) : (
                <TypePieceProposee
                  id="succ-responsable-piece-type"
                  valeur={r.pieceType}
                  onChange={(v) => setResp({ pieceType: v })}
                />
              )}
              <TextField
                label={isMorale ? 'N° RC *' : 'N° de pièce *'}
                value={r.pieceNumero}
                data-testid="succ-responsable-piece"
                onChange={(e) => setResp({ pieceNumero: e.target.value })}
              />
              <div className="md:col-span-2">
                <TextField
                  label="Pouvoirs conférés *"
                  value={r.pouvoirs}
                  data-testid="succ-responsable-pouvoirs"
                  onChange={(e) => setResp({ pouvoirs: e.target.value })}
                  hint="Publiés dans l’annonce légale : « … avec les pouvoirs suivants : … »."
                />
              </div>
            </div>

            {errors.responsable && (
              <p className="text-xs text-danger" role="alert">
                {errors.responsable}
              </p>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
