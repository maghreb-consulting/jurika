import { useEffect, useState } from 'react';
import {
  CheckCircle,
  Pencil,
  AlertTriangle,
  ChevronRight,
} from 'lucide-react';
import type { FormeJuridique } from '../../../types/ticket';
import { IdentityExtractor } from '../../../components/identity/IdentityExtractor';
import { toIsoDate } from '../../../types/identity';
import { useStepAutosave } from '../useStepAutosave';
import type { DirtyGetter } from '../useWorkflow';
import { ajouterExtraits, retirerExtraits } from '../provenance';

/**
 * UI Polish 2026-06-05 — Helper : formate ICE en groupes "XXX XXX XXX XXX XXX"
 * tout en preservant uniquement les chiffres dans la valeur stockee.
 */
function formatIce(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 15);
  return digits.replace(/(.{3})(?=.)/g, '$1 ').trim();
}

interface Props {
  existing?: Record<string, unknown>;
  /**
   * Fix 2026-06-07 (BUG 3) — Si renseigne, la forme juridique est definie
   * a la creation du ticket et n'est PLUS modifiable a cette etape. Affichage
   * en lecture seule + badge "definie a la creation du ticket". Si {@code null},
   * le selecteur classique (SARL / SARL_AU) reste affiche (compat mode).
   */
  lockedFormeJuridique?: FormeJuridique | null;
  /**
   * 2026-06-25 (refonte IMPORT) — Mode « reprise d'une societe existante ».
   * Masque les champs purement CREATION (Certificat Negatif) car non
   * pertinents, et rend obligatoires le RC + l'IF (societe immatriculee).
   * Par defaut {@code false} : la CREATION reste strictement inchangee.
   */
  importMode?: boolean;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  onSave?: (payload: Record<string, unknown>) => Promise<void>;
  /**
   * P1 2026-06-21 — Enregistre un getter "dirty" lu par useWorkflow.flushDraft()
   * avant chaque navigation (goToStep/goPrev/goNext). Garantit que les champs
   * saisis ne sont pas perdus meme sans clic "Valider".
   */
  registerDirty?: (step: number, getter: DirtyGetter) => () => void;
  /** RG transverse 2026-06-05 : registre uploads cross-step (pieces persistantes). */
  onPieceUploaded?: (
    code: string,
    label: string,
    file: File,
  ) => Promise<void> | void;
  /**
   * 2026-06-15 — dossierId du ticket courant. Permet a IdentityExtractor
   * (mode="cn") d'archiver le PDF du Certificat Negatif dans la Data Room
   * du dossier.
   */
  dossierId?: string | null;
  /**
   * Fix A7 (2026-08-16) — raison sociale portée par le TICKET.
   *
   * Elle est saisie à l'ouverture du ticket, mais l'étape 1 repartait d'un champ
   * « Dénomination » vide : l'employé retapait un nom qu'il venait de donner, au
   * risque d'une divergence entre le ticket et les actes. On l'utilise comme
   * valeur initiale — jamais comme valeur imposée : elle reste éditable, et une
   * dénomination déjà persistée a toujours la priorité.
   */
  defaultDenomination?: string | null;
}

interface Form extends Record<string, unknown> {
  denomination: string;
  ice: string;
  /**
   * 2026-06-11 — Identifiant Fiscal (IF). Optionnel a la creation (souvent
   * obtenu apres immatriculation) mais ajoute au formulaire pour pouvoir
   * remplir les Statuts si le cabinet le connait deja (cas frequent quand
   * le ticket est ouvert apres demarches RC).
   */
  ifFiscal: string;
  /** 2026-06-25 — RC (Registre de Commerce). Requis en mode IMPORT. */
  rcNumero: string;
  /** 2026-06-19 — Sigle commercial (optionnel). Mappe vers {{sigle}} dans les Statuts. */
  sigle: string;
  cnNumero: string;
  cnDate: string;
  activiteCn: string;
  beneficiaire: string;
  /** 2026-08 — Metadonnees CN optionnelles (tracabilite ; non utilisees dans les actes). */
  tribunal: string;
  dateExpiration: string;
  formeJuridique: FormeJuridique;
}

function daysBetween(iso: string): number {
  if (!iso) return 0;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 0;
  return Math.floor((Date.now() - d.getTime()) / (1000 * 60 * 60 * 24));
}

/** Validation temps reel : ICE = 15 chiffres exactement. */
function iceInvalidMessage(value: string): string | null {
  if (!value) return null;
  const digits = value.replace(/\D/g, '');
  if (digits.length === 0) return null;
  if (digits.length !== 15) return `ICE invalide : ${digits.length}/15 chiffres`;
  return null;
}

export function Step1Denomination({ existing, lockedFormeJuridique, importMode = false, saving, onSubmit, registerDirty, dossierId, defaultDenomination }: Props) {
  // Persistance « bulletproof » 2026-08 — la donnée est stockée sous DEUX formes
  // possibles : nichée sous `denomination` (executeStep -> objet) OU à plat
  // (autosave/draft -> `denomination` est une simple chaîne + champs à plat).
  // L'ancien `existing?.denomination ?? existing` choisissait la chaîne quand le
  // brouillon était à plat (chaîne non nullish) et réinitialisait TOUS les
  // champs. On FUSIONNE donc les deux formes : la couche plate d'abord, puis
  // l'objet niché (prioritaire) par-dessus -> aucun champ perdu, quelle que soit
  // la forme stockée.
  const denNested =
    existing?.denomination && typeof existing.denomination === 'object'
      ? (existing.denomination as Record<string, unknown>)
      : {};
  const extracted = {
    ...((existing as Record<string, unknown> | undefined) ?? {}),
    ...denNested,
  } as Record<string, string>;
  const extractedForme =
    (extracted.formeJuridique as FormeJuridique) ??
    (existing?.formeJuridique as FormeJuridique);
  // Fix BUG 3 : la forme verrouillee (definie a la creation du ticket)
  // a PRIORITE sur tout le reste.
  const initialForme: FormeJuridique =
    lockedFormeJuridique ?? (extractedForme === 'SARL_AU' ? 'SARL_AU' : 'SARL');
  const [form, setForm] = useState<Form>({
    // Fix A7 — repli sur la raison sociale du ticket quand rien n'est encore saisi.
    denomination: extracted.denomination ?? (defaultDenomination ?? '').trim(),
    ice: extracted.ice ?? '',
    ifFiscal: extracted.ifFiscal ?? (extracted.ifNumero as string) ?? '',
    rcNumero: extracted.rcNumero ?? '',
    sigle: (extracted.sigle as string) ?? '',
    cnNumero: extracted.cnNumero ?? '',
    cnDate: extracted.cnDate ?? '',
    activiteCn: extracted.activiteCn ?? '',
    beneficiaire: extracted.beneficiaire ?? '',
    tribunal: (extracted.tribunal as string) ?? '',
    dateExpiration: (extracted.dateExpiration as string) ?? '',
    formeJuridique: initialForme,
  });
  // Synchroniser si lockedFormeJuridique arrive apres mount (fetch async).
  // On force la valeur du form pour ne JAMAIS renvoyer une forme qui
  // contredirait la decision prise a la creation du ticket.
  useEffect(() => {
    if (lockedFormeJuridique && form.formeJuridique !== lockedFormeJuridique) {
      setForm((p) => ({ ...p, formeJuridique: lockedFormeJuridique }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lockedFormeJuridique]);

  /**
   * 2026-06-15 — Etat d'archivage. IdentityExtractor (mode="cn") archive le
   * PDF du Certificat Negatif et nous renvoie archivedDocumentId via meta
   * sur onApply. On le stocke pour afficher la confirmation et le propager
   * au payload onSubmit (utile aux traces dataroom).
   */
  const [cnArchivedId, setCnArchivedId] = useState<string | null>(
    (existing?.cnArchivedDocumentId as string | null) ??
      ((existing?.denomination as Record<string, unknown> | undefined)
        ?.cnArchivedDocumentId as string | null) ??
      null,
  );
  // 2026-08 (persistance « bulletproof ») — nom d'affichage du CN archivé,
  // persisté (pas seulement l'id) pour afficher clairement « déjà importé » au
  // retour sur l'étape. L'IdentityExtractor n'expose pas le filename brut : on
  // synthétise un libellé lisible depuis la dénomination / le n° CN.
  const [cnFileName, setCnFileName] = useState<string | null>(
    (existing?.cnFileName as string | null) ??
      ((existing?.denomination as Record<string, unknown> | undefined)
        ?.cnFileName as string | null) ??
      null,
  );
  // Permet de RE-importer le CN sans re-forcer la saisie (bouton « Remplacer »).
  const [replacingCn, setReplacingCn] = useState(false);

  // P1 2026-06-21 — Le payload Step1 est juste le form + l'archive CN.
  // useStepAutosave enregistre ce getter pour le flush au navigation.
  useStepAutosave(
    1,
    () => ({ ...form, cnArchivedDocumentId: cnArchivedId, cnFileName }),
    registerDirty,
  );

  function bind<K extends keyof Form>(k: K) {
    return (e: React.ChangeEvent<HTMLInputElement>) =>
      // Lot L3 : un champ modifie a la main redevient une saisie (provenance).
      setForm((p) => ({ ...p, [k]: e.target.value, _extraits: retirerExtraits(p._extraits, [k as string]) ?? [] }));
  }

  /**
   * 2026-06-15 — Reçoit les valeurs CN extraites par IdentityExtractor
   * (mode="cn") après validation human-in-the-loop. Mapping snake_case
   * backend → camelCase form :
   *   numero_cn       -> cnNumero
   *   denomination    -> denomination
   *   ice             -> ice (formatte XXX XXX XXX XXX XXX)
   *   beneficiaire    -> beneficiaire
   *   activite        -> activiteCn
   *   date_delivrance -> cnDate
   * On ne remplit QUE les champs vides (préserve la saisie manuelle).
   * `meta.archivedDocumentId` est conservé pour confirmation UI + payload.
   */
  function handleCnExtracted(
    values: Record<string, string | undefined>,
    meta?: { archivedDocumentId: string | null; source: string },
  ) {
    setForm((prev) => {
      const next = { ...prev };
      const mapping: Array<[string, keyof Form]> = [
        ['numero_cn', 'cnNumero'],
        ['denomination', 'denomination'],
        ['ice', 'ice'],
        ['beneficiaire', 'beneficiaire'],
        ['activite', 'activiteCn'],
        ['date_delivrance', 'cnDate'],
      ];
      const extraits: string[] = [];
      for (const [src, dst] of mapping) {
        const v = values[src];
        if (typeof v === 'string' && v.trim() && !(next[dst] as string).trim()) {
          extraits.push(dst as string);
          // 2026-06-19 — `cnDate` est un <input type="date"> qui exige
          // YYYY-MM-DD ; l'extracteur rend du FR (JJ.MM.AAAA) -> conversion
          // via toIsoDate sinon le champ reste vide bien que pre-rempli.
          (next as Record<string, string>)[dst as string] =
              dst === 'ice'    ? formatIce(v)
            : dst === 'cnDate' ? toIsoDate(v)
            :                    v;
        }
      }
      // Lot L3 (D14 de L1) : valeurs lues sur le certificat negatif et confirmees : EXTRAITES.
      return { ...next, _extraits: ajouterExtraits(next._extraits, extraits) };
    });
    if (meta?.archivedDocumentId) {
      setCnArchivedId(meta.archivedDocumentId);
      // Libellé d'affichage persistable (l'extracteur ne fournit pas le nom du
      // fichier d'origine). On privilégie la dénomination puis le n° de CN.
      const label = (values.denomination || values.numero_cn || '')
        .toString()
        .trim();
      setCnFileName(
        label ? `Certificat Négatif — ${label}` : 'Certificat Négatif',
      );
      setReplacingCn(false);
    }
  }

  const iceError = iceInvalidMessage(form.ice);
  const allFilled = importMode
    ? // IMPORT : societe existante -> denomination + ICE + RC + IF requis, PAS de CN.
      !!form.denomination && !!form.ice && !iceError && !!form.rcNumero && !!form.ifFiscal
    : // CREATION : ICE requis (choix produit) + CN requis.
      !!form.denomination &&
      !!form.ice &&
      !iceError &&
      !!form.cnNumero &&
      !!form.cnDate &&
      !!form.activiteCn &&
      !!form.beneficiaire;
  // 2026-06-22 — sigle redevenu FACULTATIF : s'il est vide, il vaut
  // automatiquement « néant » (rempli côté payload). Pas de saisie forcée.
  const cnAge = daysBetween(form.cnDate);
  const cnTooOld = form.cnDate !== '' && cnAge > 90;

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        // 2026-06-15 — On expose l'archivedDocumentId du CN au backend pour
        // tracabilite (lien dossier <-> document juridique).
        // 2026-06-25 — En mode IMPORT, le backend attend `ifNumero` + `rcNumero`
        // (societe immatriculee). On les expose en alias de `ifFiscal`/`rcNumero`.
        const importExtras = importMode
          ? { ifNumero: form.ifFiscal, rcNumero: form.rcNumero }
          : {};
        onSubmit({
          ...form,
          ...importExtras,
          cnArchivedDocumentId: cnArchivedId,
          cnFileName,
        });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        {importMode ? (
          <>
            <div className="mb-1 flex items-center gap-2">
              <span className="text-lg">🏢</span>
              <h3 className="text-lg font-bold text-fg">Identite de la societe</h3>
              <span className="rounded bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">
                Reprise d'une societe existante
              </span>
            </div>
            <p className="mb-6 text-xs text-fg-subtle">
              Renseignez l'identite et l'immatriculation de la societe a importer.
              Le certificat negatif n'est pas requis (societe deja immatriculee).
            </p>
          </>
        ) : (
          <>
        <div className="mb-1 flex items-center gap-2">
          <span className="text-lg">📜</span>
          <h3 className="text-lg font-bold text-fg">Certificat Negatif</h3>
          <span className="rounded bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">
            Extraction IA assistee
          </span>
        </div>
        <p className="mb-6 text-xs text-fg-subtle">
          Saisissez les informations du Certificat Negatif. Vous pouvez egalement
          importer le PDF/image et lancer une extraction IA pour pre-remplir les
          champs (l'extraction reste une aide — la saisie manuelle est toujours
          possible).
        </p>

        {/*
         * 2026-06-15 — Assistant d'extraction CN dedie a l'Etape 1.
         * Mode "cn" : 1 seul upload, AUCUN toggle ancienne/nouvelle, AUCUN
         * verso. Pre-archivage automatique du PDF dans la Data Room du
         * dossier (archive=true + dossierId). Les champs CN extraits
         * (numero_cn, denomination, ice, beneficiaire, activite,
         * date_delivrance) pre-remplissent les inputs CI-DESSOUS quand
         * l'utilisateur clique "Appliquer au formulaire", en preservant les
         * saisies manuelles. Sans cet assistant, la saisie manuelle reste
         * 100% fonctionnelle.
         */}
        <div className="mb-6">
          {/* 2026-08 (persistance) — Si le CN est déjà archivé en Data Room, on
              affiche clairement l'état « déjà importé » (nom + référence) plutôt
              qu'un extracteur vide qui laisse croire à une perte de données. Le
              bouton « Remplacer » rouvre l'extracteur sans re-forcer la saisie. */}
          {cnArchivedId && !replacingCn ? (
            <div className="flex items-center justify-between gap-3 rounded-lg border border-success/30 bg-success/5 p-3">
              <div className="flex min-w-0 items-center gap-2">
                <CheckCircle className="h-4 w-4 flex-shrink-0 text-success" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-success">
                    Certificat Negatif deja importe
                  </p>
                  <p className="truncate text-[11px] text-fg-subtle">
                    {cnFileName ?? 'Archive dans la Data Room'} · ref&nbsp;
                    <code className="font-mono">{cnArchivedId.slice(0, 8)}</code>
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setReplacingCn(true)}
                className="flex-shrink-0 text-[11px] font-medium text-accent hover:underline"
              >
                Remplacer
              </button>
            </div>
          ) : (
            <>
              <IdentityExtractor
                mode="cn"
                dossierId={dossierId}
                onApply={handleCnExtracted}
              />
              {cnArchivedId && replacingCn && (
                <button
                  type="button"
                  onClick={() => setReplacingCn(false)}
                  className="mt-2 text-[11px] text-fg-subtle hover:underline"
                >
                  Annuler le remplacement — conserver le CN deja importe
                </button>
              )}
            </>
          )}
        </div>
          </>
        )}

        {/* Forme juridique : lecture seule si definie a la creation du ticket
            (BUG 3 fix 2026-06-07). Sinon, mode legacy radio. */}
        {lockedFormeJuridique ? (
          <div className="mb-4 flex items-center gap-3 rounded-lg border border-success/30 bg-success/5 p-3">
            <CheckCircle className="h-4 w-4 text-success" />
            <div className="flex-1">
              <p className="text-xs font-semibold uppercase text-fg-subtle">
                Forme juridique
              </p>
              <p className="text-sm font-medium text-fg">
                {lockedFormeJuridique === 'SARL_AU' ? 'SARL AU (associe unique)' : 'SARL (2+ associes)'}
              </p>
            </div>
            <span className="rounded-full bg-accent/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-accent">
              Definie a la creation
            </span>
          </div>
        ) : (
          <div className="mb-4 rounded-lg border border-border bg-bg-overlay p-4">
            <p className="mb-2 text-xs font-semibold uppercase text-fg-subtle">
              Forme juridique
            </p>
            <div className="flex gap-3">
              <label className="flex flex-1 cursor-pointer items-center gap-2 rounded-lg border-2 border-border bg-bg-raised px-3 py-2 text-sm hover:border-accent">
                <input
                  type="radio"
                  name="formeJuridique"
                  value="SARL"
                  checked={form.formeJuridique === 'SARL'}
                  onChange={() => setForm((p) => ({ ...p, formeJuridique: 'SARL' }))}
                />
                <span className="font-medium text-fg">SARL</span>
                <span className="text-xs text-fg-subtle">(2+ associes)</span>
              </label>
              <label className="flex flex-1 cursor-pointer items-center gap-2 rounded-lg border-2 border-border bg-bg-raised px-3 py-2 text-sm hover:border-accent">
                <input
                  type="radio"
                  name="formeJuridique"
                  value="SARL_AU"
                  checked={form.formeJuridique === 'SARL_AU'}
                  onChange={() => setForm((p) => ({ ...p, formeJuridique: 'SARL_AU' }))}
                />
                <span className="font-medium text-fg">SARL AU</span>
                <span className="text-xs text-fg-subtle">(associe unique)</span>
              </label>
            </div>
          </div>
        )}

        {/* Champs CN — visibles DES l'arrivee, modifiables, validation temps reel. */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {/* 2026-08 — ICE requis a la creation (choix produit) : affiche dans tous les modes. */}
          <div>
              <label className="mb-1 block text-xs font-medium text-fg">
                ICE <span className="text-danger">*</span>
              </label>
              <input aria-label="ICE"
                type="text"
                value={form.ice}
                onChange={(e) =>
                  setForm((p) => ({ ...p, ice: formatIce(e.target.value) }))
                }
                placeholder="000 000 000 000 000"
                inputMode="numeric"
                aria-invalid={!!iceError}
                className={`h-10 w-full rounded-lg border-2 bg-bg-overlay px-3 text-sm text-fg focus:outline-none ${
                  iceError
                    ? 'border-danger focus:border-danger'
                    : 'border-border focus:border-accent'
                }`}
              />
              {iceError ? (
                <p className="mt-1 text-[11px] font-medium text-danger">
                  {iceError}
                </p>
              ) : (
                <p className="mt-1 text-[11px] text-fg-subtle">
                  15 chiffres. Les espaces et tirets sont ignores.
                </p>
              )}
            </div>
          <Field
            label="Denomination / Raison sociale"
            required
            value={form.denomination}
            onChange={bind('denomination')}
            placeholder="Ex: ATLAS TRADING"
          />
          {importMode && (
            <>
              <Field
                label="RC (Registre de Commerce)"
                required
                value={form.rcNumero}
                onChange={bind('rcNumero')}
                placeholder="Ex: 123456"
              />
              <Field
                label="IF (Identifiant Fiscal)"
                required
                value={form.ifFiscal}
                onChange={bind('ifFiscal')}
                placeholder="Ex: 12345678"
              />
              <Field
                label="Sigle commercial (optionnel)"
                value={form.sigle}
                onChange={bind('sigle')}
                placeholder={'Ex: ATLAS — laisser vide = « néant »'}
              />
            </>
          )}
          {!importMode && (
            <>
              <Field
                label="N° Certificat Negatif"
                required
                value={form.cnNumero}
                onChange={bind('cnNumero')}
                placeholder="CN-2026-XXX"
              />
              <Field
                label="Date de delivrance"
                required
                type="date"
                value={form.cnDate}
                onChange={bind('cnDate')}
              />
              <Field
                label="Activite (CN)"
                required
                value={form.activiteCn}
                onChange={bind('activiteCn')}
                placeholder="Ex: Commerce de materiaux"
              />
              <Field
                label="Beneficiaire CN"
                required
                value={form.beneficiaire}
                onChange={bind('beneficiaire')}
                placeholder="Nom du beneficiaire"
              />
              <Field
                label="Tribunal (optionnel)"
                value={form.tribunal}
                onChange={bind('tribunal')}
                placeholder="Ex: Tribunal de commerce de Casablanca"
              />
              <Field
                label="Date d'expiration (optionnel)"
                type="date"
                value={form.dateExpiration}
                onChange={bind('dateExpiration')}
              />
            </>
          )}
        </div>

        {!importMode && (
          <p className="mt-3 flex items-center gap-1 text-xs text-fg-subtle">
            <Pencil className="h-3 w-3" /> Vous pouvez corriger les champs si
            l'extraction est inexacte.
          </p>
        )}

        {!importMode && cnTooOld && (
          <div className="mt-3 flex items-start gap-2 rounded-lg border-l-4 border-warning bg-warning/10 p-3 text-sm">
            <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
            <div>
              <p className="font-semibold text-warning">
                Certificat negatif ancien
              </p>
              <p className="text-xs text-warning">
                Le CN date de {cnAge} jours. Au-dela de 90 jours, il peut etre
                refuse par le greffe. Verifiez sa validite avant publication.
              </p>
            </div>
          </div>
        )}
      </div>

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={!allFilled || saving}
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            allFilled && !saving
              ? 'bg-accent text-bg-raised hover:bg-accent-hover'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {saving ? 'Enregistrement…' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}

function Field({
  label,
  required,
  ...rest
}: { label: string; required?: boolean } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-fg">
        {label} {required && <span className="text-danger">*</span>}
      </label>
      {/* `aria-label` derive du libelle : ce champ vit dans des listes (.map),
          ou un `id` statique se dupliquerait et casserait l'association. */}
      <input
        aria-label={label}
        {...rest}
        className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm text-fg focus:border-accent focus:outline-none"
      />
    </div>
  );
}
