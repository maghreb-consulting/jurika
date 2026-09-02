import { useId, useMemo, useState } from 'react';
import {
  Upload,
  FileText,
  CheckCircle,
  Building2,
  ChevronRight,
  AlertTriangle,
} from 'lucide-react';
import {
  MOROCCO_PROVINCES,
  communesForProvince,
} from '../../../data/morocco-localities';
import { useStepAutosave } from '../useStepAutosave';

interface Props {
  existing?: Record<string, unknown>;
  /**
   * 2026-06-25 (refonte IMPORT) — Masque les justificatifs + dates de bail « a
   * la constitution » (les documents seront deposes a l'etape d'upload
   * juridique dediee). On ne garde que la localisation du siege. Defaut
   * {@code false} : la CREATION reste inchangee.
   */
  importMode?: boolean;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  onSave?: (payload: Record<string, unknown>) => Promise<void>;
  /** P1 2026-06-21 — Cowork : enregistre un getter "dirty" pour flush au navigation. */
  registerDirty?: (
    step: number,
    getter: () => Record<string, unknown> | null,
  ) => () => void;
  /**
   * 2026-06-11 — Critique : sans ce callback, le justificatif de siege
   * (bail / domiciliation + attestation) n'etait JAMAIS pousse au registre
   * de pieces ni a la Data Room du dossier — le nom de fichier seul etait
   * stocke dans le payload Step2 et perdu cote backend.
   */
  onPieceUploaded?: (
    code: string,
    label: string,
    file: File,
  ) => Promise<void> | void;
}

type JustifType = 'BAIL' | 'DOMICILIATION';

export function Step2Siege({ existing, importMode = false, saving, onSubmit, onPieceUploaded, registerDirty }: Props) {
  // Persistance : au retour sur l'etape, la donnee peut etre stockee soit sous
  // `siege` (sortie executeStep) soit a plat (draft autosave). On lit les deux.
  const e =
    (existing?.siege as Record<string, unknown>) ??
    (existing as Record<string, unknown>) ??
    {};
  const [adresse, setAdresse] = useState<string>((e.adresse as string) ?? '');
  // Fix C2 (2026-08-16) — plus AUCUN pré-remplissage arbitraire.
  //
  // « Casablanca » était posé en dur comme province par défaut, en création COMME
  // en import. Un champ pré-rempli se lit comme une donnée vérifiée : la valeur
  // partait telle quelle dans les actes de toute société domiciliée ailleurs, sans
  // que personne ne l'ait choisie. Le champ démarre donc vide et doit être saisi.
  const [province, setProvince] = useState<string>((e.province as string) ?? '');
  const [commune, setCommune] = useState<string>((e.commune as string) ?? '');
  const [codePostal, setCodePostal] = useState<string>((e.codePostal as string) ?? '');
  // 2026-08 (dé-dup) — ville du greffe = SAISIE UNIQUE du tribunal de commerce
  // compétent (l'ancien champ « Tribunal compétent » faisait doublon). Défaut : la
  // province/ville du siège. Consommée par la voie directeur (VILLE_GREFFE).
  // Fix C2 — la ville du greffe se DÉDUIT de la province saisie (déduction
  // traçable), jamais d'un « Casablanca » en dur.
  const [villeGreffe, setVilleGreffe] = useState<string>(
    (e.villeGreffe as string) ?? (e.province as string) ?? '',
  );
  // F1 2026-06-09 — ids unique pour les datalist (sinon collision avec autres steps).
  const provinceListId = useId();
  const communeListId = useId();
  const [justificatifType, setJustifType] = useState<JustifType>(
    (e.justificatifType as JustifType) ?? 'BAIL',
  );
  const [dateDebut, setDateDebut] = useState<string>((e.dateDebut as string) ?? '');
  const [dateFin, setDateFin] = useState<string>((e.dateFin as string) ?? '');
  const [mainDoc, setMainDoc] = useState<string | null>(
    (e.mainDocName as string) ?? null,
  );
  // 2026-06-19 (Cowork) — Justificatif BAIL : 2 fichiers distincts (le contrat
  // OU le titre de propriete + le certificat de propriete). Avant : un seul
  // input alors que le libelle annonçait deux pieces, le certificat n'etait
  // jamais archive.
  const [certifDoc, setCertifDoc] = useState<string | null>(
    (e.certificatProprieteDocName as string) ?? null,
  );
  const [attestDoc, setAttestDoc] = useState<string | null>(
    (e.attestationDocName as string) ?? null,
  );

  function handleProvince(v: string) {
    setProvince(v);
    setCommune('');
    setVilleGreffe(v);
  }

  // F1 — Communes proposees pour la province courante (liste non exhaustive,
  // le user peut taper une commune libre si elle n'est pas listee).
  const communes = useMemo(() => communesForProvince(province), [province]);

  function handleMainFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    setMainDoc(f.name);
    // 2026-06-11 — Pousse au registre + Data Room (juridique).
    // 2026-06-19 (Cowork) — En BAIL : le 1er fichier = contrat de bail ou
    // titre de propriete (selon situation). Le certificat de propriete est
    // un 2e fichier distinct branche sur handleCertifFile.
    const code = justificatifType === 'BAIL' ? 'CONTRAT_BAIL' : 'JUSTIFICATIF_SIEGE';
    const label = justificatifType === 'BAIL'
      ? 'Contrat de bail / titre de propriete'
      : 'Contrat de domiciliation';
    void onPieceUploaded?.(code, label, f);
  }
  function handleCertifFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    setCertifDoc(f.name);
    void onPieceUploaded?.(
      'CERTIFICAT_PROPRIETE',
      'Certificat de propriete',
      f,
    );
  }
  function handleAttestFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    setAttestDoc(f.name);
    void onPieceUploaded?.(
      'ATTESTATION_DOMICILIATION',
      'Attestation de domiciliation',
      f,
    );
  }

  const canSubmit = importMode
    ? // IMPORT : seule la localisation est requise (justificatifs deposes plus tard).
      !!adresse && !!province && !!commune && !!codePostal
    : !!adresse &&
      !!province &&
      !!commune &&
      !!dateDebut &&
      !!dateFin &&
      !!mainDoc &&
      // 2026-06-19 (Cowork) — BAIL exige aussi le certificat de propriete.
      (justificatifType === 'BAIL'
        ? !!certifDoc
        : !!attestDoc);

  // P1 2026-06-21 — autosave manquant sur Step2 -> adresse/siege perdus a la
  // navigation retour. Le getter miroite le payload onSubmit.
  useStepAutosave(
    2,
    () => ({
      adresse,
      province,
      commune,
      codePostal,
      villeGreffe,
      justificatifType,
      dateDebut,
      dateFin,
      mainDocName: mainDoc,
      certificatProprieteDocName: certifDoc,
      attestationDocName: attestDoc,
    }),
    registerDirty,
  );

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        onSubmit({
          adresse,
          province,
          commune,
          codePostal,
          villeGreffe,
          justificatifType,
          dateDebut,
          dateFin,
          mainDocName: mainDoc,
          certificatProprieteDocName: certifDoc,
          attestationDocName: attestDoc,
        });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-1 flex items-center gap-2">
          <Building2 className="h-5 w-5 text-accent" />
          <h3 className="text-lg font-bold text-fg">Siege social</h3>
        </div>
        <p className="mb-6 text-xs text-fg-subtle">
          Adresse complete du siege social et justificatif d'occupation.
        </p>

        {/* Adresse */}
        <div className="mb-5">
          <label className="mb-1 block text-xs font-medium text-fg">
            Adresse du siege social
          </label>
          <textarea aria-label="Adresse du siege social"
            rows={2}
            value={adresse}
            onChange={(ev) => setAdresse(ev.target.value)}
            placeholder="Numero, rue, quartier, ville"
            className="w-full resize-none rounded-lg border-2 border-border bg-bg-overlay px-3 py-2 text-sm focus:border-accent focus:outline-none"
          />
        </div>

        {/* Type justificatif + uploads — masques en mode IMPORT (les pieces sont
            deposees a l'etape d'upload juridique dediee). */}
        {!importMode && (
        <>
        <div className="mb-5">
          <label className="mb-3 block text-xs font-medium text-fg">
            Type de justificatif
          </label>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => {
                setJustifType('BAIL');
                setMainDoc(null);
                setCertifDoc(null);
                setAttestDoc(null);
              }}
              className={`flex-1 rounded-lg border-2 p-4 text-left transition ${
                justificatifType === 'BAIL'
                  ? 'border-accent bg-accent/10'
                  : 'border-border hover:border-border-hi'
              }`}
            >
              <p className="text-sm font-semibold text-fg">Contrat de bail</p>
              <p className="mt-1 text-xs text-fg-subtle">+ Certificat de propriete</p>
            </button>
            <button
              type="button"
              onClick={() => {
                setJustifType('DOMICILIATION');
                setMainDoc(null);
                setCertifDoc(null);
                setAttestDoc(null);
              }}
              className={`flex-1 rounded-lg border-2 p-4 text-left transition ${
                justificatifType === 'DOMICILIATION'
                  ? 'border-accent bg-accent/10'
                  : 'border-border hover:border-border-hi'
              }`}
            >
              <p className="text-sm font-semibold text-fg">
                Contrat de domiciliation
              </p>
              <p className="mt-1 text-xs text-fg-subtle">
                + Attestation de reception courrier
              </p>
            </button>
          </div>
        </div>

        {/* Upload principal — BAIL : contrat de bail OU titre de propriete.
            DOMICILIATION : contrat de domiciliation. */}
        <div className="mb-5">
          <label className="mb-2 block text-xs font-medium text-fg">
            {justificatifType === 'BAIL'
              ? 'Contrat de bail / titre de propriete'
              : 'Contrat de domiciliation'}
          </label>
          {!mainDoc ? (
            <label className="block cursor-pointer rounded-lg border-2 border-dashed border-border p-8 text-center transition hover:border-accent hover:bg-bg-overlay">
              <Upload className="mx-auto mb-2 h-8 w-8 text-fg-subtle" />
              <p className="text-sm text-fg-subtle">
                Glissez ou cliquez pour importer
              </p>
              <input
                type="file"
                accept="application/pdf,image/*"
                className="hidden"
                onChange={handleMainFile}
              />
            </label>
          ) : (
            <div className="flex items-center gap-3 rounded-lg border-2 border-success bg-success/10 p-3">
              <FileText className="h-5 w-5 text-danger" />
              <p className="flex-1 text-sm font-medium text-fg">{mainDoc}</p>
              <CheckCircle className="h-5 w-5 text-success" />
              <button
                type="button"
                onClick={() => setMainDoc(null)}
                className="text-xs text-danger hover:underline"
              >
                Remplacer
              </button>
            </div>
          )}
        </div>

        {/* 2026-06-19 (Cowork) — BAIL : 2e zone Certificat de propriete. */}
        {justificatifType === 'BAIL' && (
          <div className="mb-5">
            <label className="mb-2 block text-xs font-medium text-fg">
              Certificat de propriete
            </label>
            {!certifDoc ? (
              <label className="block cursor-pointer rounded-lg border-2 border-dashed border-border p-6 text-center transition hover:border-accent hover:bg-bg-overlay">
                <Upload className="mx-auto mb-1 h-6 w-6 text-fg-subtle" />
                <p className="text-sm text-fg-subtle">
                  Importer le certificat de propriete
                </p>
                <input
                  type="file"
                  accept="application/pdf,image/*"
                  className="hidden"
                  onChange={handleCertifFile}
                />
              </label>
            ) : (
              <div className="flex items-center gap-3 rounded-lg border-2 border-success bg-success/10 p-3">
                <FileText className="h-5 w-5 text-danger" />
                <p className="flex-1 text-sm font-medium text-fg">{certifDoc}</p>
                <CheckCircle className="h-5 w-5 text-success" />
                <button
                  type="button"
                  onClick={() => setCertifDoc(null)}
                  className="text-xs text-danger hover:underline"
                >
                  Remplacer
                </button>
              </div>
            )}
          </div>
        )}

        {/* Attestation domiciliation */}
        {justificatifType === 'DOMICILIATION' && (
          <div className="mb-5">
            <label className="mb-2 block text-xs font-medium text-fg">
              Attestation de reception courrier
            </label>
            {!attestDoc ? (
              <label className="block cursor-pointer rounded-lg border-2 border-dashed border-border p-6 text-center transition hover:border-accent hover:bg-bg-overlay">
                <Upload className="mx-auto mb-1 h-6 w-6 text-fg-subtle" />
                <p className="text-sm text-fg-subtle">
                  Importer l'attestation de reception courrier
                </p>
                <input
                  type="file"
                  accept="application/pdf,image/*"
                  className="hidden"
                  onChange={handleAttestFile}
                />
              </label>
            ) : (
              <div className="flex items-center gap-3 rounded-lg border-2 border-success bg-success/10 p-3">
                <FileText className="h-5 w-5 text-danger" />
                <p className="flex-1 text-sm font-medium text-fg">{attestDoc}</p>
                <CheckCircle className="h-5 w-5 text-success" />
                <button
                  type="button"
                  onClick={() => setAttestDoc(null)}
                  className="text-xs text-danger hover:underline"
                >
                  Remplacer
                </button>
              </div>
            )}
          </div>
        )}
        </>
        )}

        {/* Localisation — F1 2026-06-09 : combobox (datalist + input free-text). */}
        <div className="mb-5 grid grid-cols-1 gap-4 md:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Province / Prefecture
            </label>
            <input aria-label="Province / Prefecture"
              type="text"
              list={provinceListId}
              value={province}
              onChange={(ev) => handleProvince(ev.target.value)}
              placeholder="Choisir ou saisir une province"
              autoComplete="off"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
            <datalist id={provinceListId}>
              {MOROCCO_PROVINCES.map((p) => (
                <option key={p} value={p} />
              ))}
            </datalist>
            <p className="mt-0.5 text-[10px] text-fg-subtle">
              Liste : 75 prefectures/provinces du Maroc. Si la votre n'apparait pas, saisissez-la librement.
            </p>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Commune
            </label>
            <input aria-label="Commune"
              type="text"
              list={communeListId}
              value={commune}
              onChange={(ev) => setCommune(ev.target.value)}
              placeholder={communes.length ? 'Choisir ou saisir' : 'Saisir la commune'}
              autoComplete="off"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
            <datalist id={communeListId}>
              {communes.map((c) => (
                <option key={c} value={c} />
              ))}
            </datalist>
            <p className="mt-0.5 text-[10px] text-fg-subtle">
              {communes.length
                ? `${communes.length} commune(s) suggeree(s) pour ${province}. Free-text accepte.`
                : `Aucune suggestion pour ${province} — saisie libre.`}
            </p>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Code postal
            </label>
            <input aria-label="Code postal"
              type="text"
              value={codePostal}
              onChange={(ev) => setCodePostal(ev.target.value)}
              placeholder="20000"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
        </div>

        {/* 2026-08 (dé-dup) — « Tribunal compétent » (doublon) retiré : la ville du
            greffe ci-dessous est la SAISIE UNIQUE du tribunal de commerce compétent.
            La durée de la société est saisie à l'étape 3 (Capital), pas ici. */}
        <div className="mb-5 grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Ville du greffe
            </label>
            <input aria-label="Ville du greffe"
              type="text"
              value={villeGreffe}
              onChange={(ev) => setVilleGreffe(ev.target.value)}
              placeholder="Ex: Casablanca"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
            <p className="mt-0.5 text-[10px] text-fg-subtle">
              Greffe du tribunal de commerce competent (defaut : ville du siege).
            </p>
          </div>
        </div>

        {/* Dates contrat — masquees en mode IMPORT (bail « a la constitution »). */}
        {!importMode && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Date de debut du contrat
            </label>
            <input aria-label="Date de debut du contrat"
              type="date"
              value={dateDebut}
              onChange={(ev) => setDateDebut(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Date de fin du contrat
            </label>
            <input aria-label="Date de fin du contrat"
              type="date"
              value={dateFin}
              onChange={(ev) => setDateFin(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
        </div>
        )}
      </div>

      {!canSubmit && (
        <div className="flex items-start gap-2 rounded-lg border-l-4 border-warning bg-warning/10 p-3 text-xs">
          <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
          <p className="text-warning">
            {importMode
              ? "Renseignez l'adresse, la province, la commune et le code postal du siege."
              : "Renseignez l'adresse complete, la commune, les dates du contrat et le(s) justificatif(s) avant de continuer."}
          </p>
        </div>
      )}

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={!canSubmit || saving}
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            canSubmit && !saving
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
