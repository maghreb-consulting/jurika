import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Calculator,
  CheckCircle2,
  ChevronRight,
  Info,
  Lock,
} from 'lucide-react';
import { useStepAutosave } from '../useStepAutosave';
import type { DirtyGetter } from '../useWorkflow';

interface Props {
  existing?: Record<string, unknown>;
  /**
   * 2026-06-25 (refonte IMPORT) — Societe existante : on masque la liberation
   * 25 %, le compte de depot bancaire et la date de commencement (« a la
   * constitution »). Seuls le capital total + le nombre de parts (+ duree)
   * restent requis. Defaut {@code false} : la CREATION reste inchangee.
   */
  importMode?: boolean;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  onSave?: (payload: Record<string, unknown>) => Promise<void>;
  /** P1 2026-06-21 — Voir useStepAutosave. */
  registerDirty?: (step: number, getter: DirtyGetter) => () => void;
}

export function Step3Capital({ existing, importMode = false, saving, onSubmit, registerDirty }: Props) {
  // Persistance : donnee stockee sous `capital` (executeStep) ou a plat (draft).
  const e =
    (existing?.capital as Record<string, unknown>) ??
    (existing as Record<string, unknown>) ??
    {};
  const [hasNumeraire, setHasNumeraire] = useState<boolean>(
    (e.hasNumeraire as boolean) ?? true,
  );
  const [numeraire, setNumeraire] = useState<number>(
    Number(e.apportNumeraire ?? 100000),
  );
  const [hasNature, setHasNature] = useState<boolean>(
    (e.hasNature as boolean) ?? false,
  );
  const [nature, setNature] = useState<number>(Number(e.apportNature ?? 0));
  const [natureDesc, setNatureDesc] = useState<string>(
    (e.descriptionNature as string) ?? '',
  );
  const [hasIndustrie, setHasIndustrie] = useState<boolean>(
    (e.hasIndustrie as boolean) ?? false,
  );
  const [industrie, setIndustrie] = useState<number>(Number(e.apportIndustrie ?? 0));
  const [industrieDesc, setIndustrieDesc] = useState<string>(
    (e.descriptionIndustrie as string) ?? '',
  );
  /**
   * EX2 2026-06-09 — Refonte capital libere :
   *   Apport NATURE et INDUSTRIE = libere a 100% obligatoirement (verrouille).
   *   Seul l'apport NUMERAIRE est partiellement liberable.
   *   capitalLibere = (nature + industrie) + numeraireLibere
   *   Seuil legal 25% calcule sur le CAPITAL TOTAL (numeraire + nature + industrie).
   *
   * Compat ascendante : on migre l'ancienne valeur `capitalLibere` en supposant
   * qu'elle representait deja la part numeraire seulement si nature+industrie=0.
   * Sinon on tente de retrouver numeraireLibere par soustraction.
   */
  const initialNumeraireLibere = (() => {
    const raw = e.apportNumeraireLibere;
    if (raw != null) return Number(raw);
    // Legacy : si capitalLibere est present, on retire la part nature+industrie
    const legacy = Number(e.capitalLibere ?? 25000);
    const nat = (e.hasNature as boolean) ? Number(e.apportNature ?? 0) : 0;
    const ind = (e.hasIndustrie as boolean) ? Number(e.apportIndustrie ?? 0) : 0;
    return Math.max(0, legacy - nat - ind);
  })();
  const [numeraireLibere, setNumeraireLibere] = useState<number>(initialNumeraireLibere);
  const [nbParts, setNbParts] = useState<number>(Number(e.nombreParts ?? 1000));
  const [duree, setDuree] = useState<number>(Number(e.dureeAnnees ?? 99));
  const [dateCommencement, setDateCommencement] = useState<string>(
    (e.dateCommencement as string) ?? '',
  );

  // 2026-06-19 — Sprint « variables par etape » : depot bancaire (toujours
  // requis pour les statuts), commissaire aux apports (si nature > 100k MAD —
  // Loi 5-96 Art. 53), apport fonds de commerce (toggle conditionnel).
  const [depotBanqueNom, setDepotBanqueNom] = useState<string>(
    (e.depotBanqueNom as string) ?? '',
  );
  const [depotNumero, setDepotNumero] = useState<string>(
    (e.depotNumero as string) ?? '',
  );
  const [commissaireApportsNom, setCommissaireApportsNom] = useState<string>(
    (e.commissaireApportsNom as string) ?? '',
  );
  // 2026-08 (Phase 2-B) — Options de constitution (voie directeur).
  const [modeLiberation, setModeLiberation] = useState<string>(
    (e.modeLiberation as string) ?? 'intégrale',
  );
  const [depotFondsBloque, setDepotFondsBloque] = useState<boolean>(
    e.depotFondsBloque === true || e.depotFondsBloque === 'oui',
  );
  const [modeSignature, setModeSignature] = useState<string>(
    (e.modeSignature as string) ?? 'séparée',
  );
  const [signaturePlafond, setSignaturePlafond] = useState<number>(
    Number(e.signaturePlafond ?? 0),
  );

  const capitalSocial = useMemo(
    () =>
      (hasNumeraire ? numeraire : 0) +
      (hasNature ? nature : 0) +
      (hasIndustrie ? industrie : 0),
    [numeraire, hasNumeraire, nature, hasNature, industrie, hasIndustrie],
  );
  // EX2 : nature + industrie sont 100% liberes par definition.
  const natureLibere = hasNature ? nature : 0;
  const industrieLibere = hasIndustrie ? industrie : 0;
  // numeraireLibere clampe entre 0 et numeraire effectif (sinon ratio fausse).
  const numeraireLibereSafe = hasNumeraire
    ? Math.max(0, Math.min(numeraireLibere, numeraire))
    : 0;
  // capitalLibere total = nature(100%) + industrie(100%) + numeraire partiel.
  const libere = natureLibere + industrieLibere + numeraireLibereSafe;
  const valeurNominale = nbParts > 0 ? capitalSocial / nbParts : 0;
  const percentLibere =
    capitalSocial > 0 ? Math.round((libere / capitalSocial) * 100) : 0;
  const isBlocked = capitalSocial > 0 && libere < capitalSocial * 0.25;
  const numeraireLiberePct = numeraire > 0
    ? Math.round((numeraireLibereSafe / numeraire) * 100)
    : 0;

  const dateFin = useMemo(() => {
    if (!dateCommencement) return '';
    const d = new Date(dateCommencement);
    if (Number.isNaN(d.getTime())) return '';
    d.setFullYear(d.getFullYear() + duree);
    return d.toISOString().split('T')[0];
  }, [dateCommencement, duree]);

  const canSubmit = importMode
    ? // IMPORT : capital total + parts + duree. Pas de 25%, depot ni date.
      capitalSocial > 0 &&
      nbParts > 0 &&
      duree > 0 &&
      (hasNumeraire || hasNature || hasIndustrie)
    : capitalSocial > 0 &&
      !isBlocked &&
      nbParts > 0 &&
      duree > 0 &&
      !!dateCommencement &&
      // 2026-08 (contrat CREATION) — depot bancaire requis UNIQUEMENT si les
      // fonds sont deposes en compte bloque (depotFondsBloque=oui). Le backend
      // n'exige plus DEPOT_BANQUE_NOM / DEPOT_NUMERO sinon.
      (!depotFondsBloque ||
        (depotBanqueNom.trim().length > 0 && depotNumero.trim().length > 0)) &&
      (hasNumeraire || hasNature || hasIndustrie);

  // P1 2026-06-21 — Getter "dirty" pour flush avant navigation.
  useStepAutosave(
    3,
    () => ({
      apportNumeraire: hasNumeraire ? numeraire : 0,
      apportNumeraireLibere: numeraireLibereSafe,
      apportNature: hasNature ? nature : 0,
      descriptionNature: hasNature ? natureDesc : '',
      apportIndustrie: hasIndustrie ? industrie : 0,
      descriptionIndustrie: hasIndustrie ? industrieDesc : '',
      capitalLibere: libere,
      capitalSocialMad: capitalSocial,
      valeurNominale,
      nombreParts: nbParts,
      dureeAnnees: duree,
      dateCommencement,
      dateFin,
      hasNumeraire,
      hasNature,
      hasIndustrie,
      depotBanqueNom,
      depotNumero,
      depot: { banque: depotBanqueNom, numero: depotNumero },
      commissaireApportsNom: hasNature && nature > 100000 ? commissaireApportsNom : '',
      modeLiberation,
      depotFondsBloque: depotFondsBloque ? 'oui' : 'non',
      modeSignature,
      signaturePlafond: modeSignature === 'séparée avec plafond' ? signaturePlafond : 0,
    }),
    registerDirty,
  );

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        onSubmit({
          apportNumeraire: hasNumeraire ? numeraire : 0,
          apportNumeraireLibere: numeraireLibereSafe,
          apportNature: hasNature ? nature : 0,
          descriptionNature: hasNature ? natureDesc : '',
          apportIndustrie: hasIndustrie ? industrie : 0,
          descriptionIndustrie: hasIndustrie ? industrieDesc : '',
          // EX2 : capitalLibere total = nature(100%) + industrie(100%) + numeraireLibere
          capitalLibere: libere,
          capitalSocialMad: capitalSocial,
          valeurNominale,
          nombreParts: nbParts,
          dureeAnnees: duree,
          dateCommencement,
          dateFin,
          hasNumeraire,
          hasNature,
          hasIndustrie,
          // 2026-06-19 — Depot + commissaire + fonds + modalite duree
          depotBanqueNom,
          depotNumero,
          depot: { banque: depotBanqueNom, numero: depotNumero },
          commissaireApportsNom: hasNature && nature > 100000 ? commissaireApportsNom : '',
          modeLiberation,
          depotFondsBloque: depotFondsBloque ? 'oui' : 'non',
          modeSignature,
          signaturePlafond: modeSignature === 'séparée avec plafond' ? signaturePlafond : 0,
        });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      {/* Apports */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-1 flex items-center gap-2">
          <Calculator className="h-5 w-5 text-accent" />
          <h3 className="text-lg font-bold text-fg">Apports & Capital</h3>
        </div>
        <p className="mb-6 text-xs text-fg-subtle">
          Devise : Dirham marocain (MAD). Au moins un type d'apport doit etre
          renseigne.
        </p>

        {/* Numeraire — seul apport partiellement liberable */}
        <div className="mb-4 rounded-lg border border-border bg-bg-overlay p-4">
          <label className="mb-3 flex cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              checked={hasNumeraire}
              onChange={(ev) => setHasNumeraire(ev.target.checked)}
              className="h-4 w-4 rounded"
            />
            <span className="text-sm font-medium text-fg">
              Apport en numeraire
            </span>
            <span className="text-xs text-fg-subtle">(en MAD, liberation partielle possible)</span>
          </label>
          {hasNumeraire && (
            <div className="space-y-3">
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div>
                  <label className="mb-1 block text-xs text-fg-subtle">
                    Montant total (MAD)
                  </label>
                  <div className="relative">
                    <input aria-label="Montant total (MAD)"
                      type="number"
                      min={0}
                      value={numeraire}
                      onChange={(ev) => setNumeraire(Number(ev.target.value))}
                      className="h-10 w-full rounded-lg border-2 border-border bg-bg-raised px-3 pr-16 text-sm focus:border-accent focus:outline-none"
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-fg-subtle">
                      MAD
                    </span>
                  </div>
                </div>
                {!importMode && (
                <div>
                  <label className="mb-1 block text-xs text-fg-subtle">
                    Dont libere (MAD) — RG legale liberable partiellement
                  </label>
                  <div className="relative">
                    <input aria-label="Dont libere (MAD) — RG legale liberable partiellement"
                      type="number"
                      min={0}
                      max={numeraire}
                      value={numeraireLibere}
                      onChange={(ev) =>
                        setNumeraireLibere(Number(ev.target.value))
                      }
                      className="h-10 w-full rounded-lg border-2 border-border bg-bg-raised px-3 pr-16 text-sm focus:border-accent focus:outline-none"
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-fg-subtle">
                      {numeraireLiberePct}%
                    </span>
                  </div>
                  {numeraireLibere > numeraire && (
                    <p className="mt-1 text-[11px] font-medium text-danger">
                      Plafonne au montant total de l'apport ({numeraire.toLocaleString('fr-MA')} MAD).
                    </p>
                  )}
                </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Nature — VERROUILLE 100% libere (EX2) */}
        <div className="mb-4 rounded-lg border border-border bg-bg-overlay p-4">
          <label className="mb-3 flex cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              checked={hasNature}
              onChange={(ev) => setHasNature(ev.target.checked)}
              className="h-4 w-4 rounded"
            />
            <span className="text-sm font-medium text-fg">
              Apport en nature
            </span>
            <span className="text-xs text-fg-subtle">(optionnel)</span>
            {hasNature && (
              <span className="ml-auto inline-flex items-center gap-1 rounded-full border border-success/40 bg-success/10 px-2 py-0.5 text-[10px] font-bold uppercase text-success">
                <Lock className="h-3 w-3" /> 100% libere
              </span>
            )}
          </label>
          {hasNature && (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_2fr]">
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">
                  Montant (MAD)
                </label>
                <input aria-label="Montant (MAD)"
                  type="number"
                  min={0}
                  value={nature}
                  onChange={(ev) => setNature(Number(ev.target.value))}
                  className="h-9 w-full rounded-lg border-2 border-border bg-bg-raised px-3 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">
                  Description
                </label>
                <input aria-label="Description"
                  type="text"
                  value={natureDesc}
                  onChange={(ev) => setNatureDesc(ev.target.value)}
                  placeholder="Ex: Vehicule, materiel, mobilier…"
                  className="h-9 w-full rounded-lg border-2 border-border bg-bg-raised px-3 text-sm"
                />
              </div>
            </div>
          )}
        </div>

        {/* Industrie — VERROUILLE 100% libere (EX2) */}
        <div className="mb-5 rounded-lg border border-border bg-bg-overlay p-4">
          <label className="mb-3 flex cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              checked={hasIndustrie}
              onChange={(ev) => setHasIndustrie(ev.target.checked)}
              className="h-4 w-4 rounded"
            />
            <span className="text-sm font-medium text-fg">
              Apport en industrie
            </span>
            <span className="text-xs text-fg-subtle">(optionnel)</span>
            {hasIndustrie && (
              <span className="ml-auto inline-flex items-center gap-1 rounded-full border border-success/40 bg-success/10 px-2 py-0.5 text-[10px] font-bold uppercase text-success">
                <Lock className="h-3 w-3" /> 100% libere
              </span>
            )}
          </label>
          {hasIndustrie && (
            <div className="grid grid-cols-1 gap-3 md:grid-cols-[1fr_2fr]">
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">
                  Montant (MAD)
                </label>
                <input aria-label="Montant (MAD)"
                  type="number"
                  min={0}
                  value={industrie}
                  onChange={(ev) => setIndustrie(Number(ev.target.value))}
                  className="h-9 w-full rounded-lg border-2 border-border bg-bg-raised px-3 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">
                  Description
                </label>
                <input aria-label="Description"
                  type="text"
                  value={industrieDesc}
                  onChange={(ev) => setIndustrieDesc(ev.target.value)}
                  placeholder="Ex: Savoir-faire technique"
                  className="h-9 w-full rounded-lg border-2 border-border bg-bg-raised px-3 text-sm"
                />
              </div>
            </div>
          )}
        </div>

        {/* Capital auto */}
        <div className="rounded-lg border border-accent/20 bg-accent/10 p-5">
          <div className="mb-2 flex items-center gap-2">
            <Info className="h-4 w-4 text-accent" />
            <span className="text-sm font-bold text-accent">
              Capital social (calcul automatique)
            </span>
          </div>
          <p className="text-3xl font-black text-fg">
            {capitalSocial.toLocaleString('fr-MA')}{' '}
            <span className="text-lg font-medium text-fg-subtle">MAD</span>
          </p>
        </div>
      </div>

      {/* Libération & Parts — EX2 : libere est calcule, pas saisi */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-5 text-base font-bold text-fg">
          Liberation & Parts sociales
        </h3>

        {/* Tableau decomposition liberation (transparence pour user) — masque en IMPORT */}
        {!importMode && (
        <div className="mb-4 overflow-hidden rounded-lg border border-border">
          <table className="w-full text-xs">
            <thead className="bg-bg-overlay text-fg-subtle">
              <tr>
                <th className="px-3 py-2 text-left font-medium">Type d'apport</th>
                <th className="px-3 py-2 text-right font-medium">Souscrit (MAD)</th>
                <th className="px-3 py-2 text-right font-medium">Libere (MAD)</th>
                <th className="px-3 py-2 text-right font-medium">% libere</th>
              </tr>
            </thead>
            <tbody className="bg-bg-raised">
              {hasNumeraire && (
                <tr className="border-t border-border">
                  <td className="px-3 py-2">Numeraire</td>
                  <td className="px-3 py-2 text-right">{numeraire.toLocaleString('fr-MA')}</td>
                  <td className="px-3 py-2 text-right">{numeraireLibereSafe.toLocaleString('fr-MA')}</td>
                  <td className="px-3 py-2 text-right font-medium">{numeraireLiberePct}%</td>
                </tr>
              )}
              {hasNature && (
                <tr className="border-t border-border">
                  <td className="px-3 py-2 flex items-center gap-1"><Lock className="h-3 w-3 text-success" />Nature</td>
                  <td className="px-3 py-2 text-right">{nature.toLocaleString('fr-MA')}</td>
                  <td className="px-3 py-2 text-right">{nature.toLocaleString('fr-MA')}</td>
                  <td className="px-3 py-2 text-right font-bold text-success">100%</td>
                </tr>
              )}
              {hasIndustrie && (
                <tr className="border-t border-border">
                  <td className="px-3 py-2 flex items-center gap-1"><Lock className="h-3 w-3 text-success" />Industrie</td>
                  <td className="px-3 py-2 text-right">{industrie.toLocaleString('fr-MA')}</td>
                  <td className="px-3 py-2 text-right">{industrie.toLocaleString('fr-MA')}</td>
                  <td className="px-3 py-2 text-right font-bold text-success">100%</td>
                </tr>
              )}
              <tr className="border-t-2 border-border bg-bg-overlay font-bold">
                <td className="px-3 py-2">Total</td>
                <td className="px-3 py-2 text-right">{capitalSocial.toLocaleString('fr-MA')}</td>
                <td className={`px-3 py-2 text-right ${isBlocked ? 'text-danger' : 'text-success'}`}>{libere.toLocaleString('fr-MA')}</td>
                <td className={`px-3 py-2 text-right ${isBlocked ? 'text-danger' : 'text-success'}`}>{percentLibere}%</td>
              </tr>
            </tbody>
          </table>
        </div>
        )}

        <div className="mb-4">
          <label className="mb-1 block text-xs font-medium text-fg">
            Nombre total de parts sociales
          </label>
          <input aria-label="Nombre total de parts sociales"
            type="number"
            min={1}
            value={nbParts}
            onChange={(ev) => setNbParts(Number(ev.target.value))}
            className="h-10 w-full max-w-xs rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
          />
        </div>

        {/* 2026-06-19 — Cowork : si le capital n'est pas un multiple du nombre de
            parts, la valeur nominale tombe avec des decimales -> chaque associe
            arrondit independamment et Σ(apports) != capital. On le signale.
            Tolerance 0.005 MAD pour absorber les artefacts flottants. */}
        {capitalSocial > 0 && nbParts > 0 && (() => {
          const reste = capitalSocial - Math.round(valeurNominale * 100) / 100 * nbParts;
          const isInteger = Math.abs(valeurNominale - Math.round(valeurNominale)) < 1e-9;
          if (isInteger && Math.abs(reste) < 0.005) return null;
          return (
            <div className="mb-4 flex items-start gap-2 rounded-lg border-l-4 border-warning bg-warning/10 p-3">
              <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
              <div className="text-xs text-warning">
                <p>
                  <strong>Valeur nominale non entiere :</strong>{' '}
                  {valeurNominale.toLocaleString('fr-MA', { maximumFractionDigits: 4 })} MAD.
                  Le capital n'est pas un multiple exact du nombre de parts.
                </p>
                <p className="mt-1">
                  Recommande : ajustez le nombre de parts pour obtenir une valeur
                  nominale entiere (ex.{' '}
                  {nbParts > 1 ? capitalSocial.toString().slice(0, -1) || '100' : '100'}{' '}
                  parts a {Math.max(1, Math.round(valeurNominale)).toLocaleString('fr-MA')}{' '}
                  MAD), sinon le dernier associe absorbera le reliquat d'arrondi
                  (Step 6) pour garantir Σ = capital.
                </p>
              </div>
            </div>
          );
        })()}

        <div className="mb-4 flex items-center gap-6 rounded-lg bg-bg-overlay p-4">
          <div>
            <span className="text-xs text-fg-subtle">Valeur nominale</span>
            <p className="text-lg font-bold text-fg">
              {valeurNominale.toLocaleString('fr-MA', { maximumFractionDigits: 2 })}{' '}
              MAD
            </p>
          </div>
          {!importMode && (
          <>
          <div className="h-10 w-px bg-border" />
          <div>
            <span className="text-xs text-fg-subtle">% libere global</span>
            <p
              className={`text-lg font-bold ${
                isBlocked ? 'text-danger' : 'text-success'
              }`}
            >
              {percentLibere}%
            </p>
          </div>
          </>
          )}
          <div className="h-10 w-px bg-border" />
          <div>
            <span className="text-xs text-fg-subtle">Capital social</span>
            <p className="text-lg font-bold text-fg">
              {capitalSocial.toLocaleString('fr-MA')} MAD
            </p>
          </div>
        </div>

        {!importMode && capitalSocial > 0 && isBlocked && (
          <div className="flex items-start gap-3 rounded-lg border-l-4 border-danger bg-danger/10 p-4">
            <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-danger" />
            <div>
              <p className="text-sm font-semibold text-danger">
                Liberation insuffisante — Etape bloquee
              </p>
              <p className="text-xs text-danger">
                Le capital libere total ({libere.toLocaleString('fr-MA')} MAD) doit etre
                au minimum 25% du capital social total (
                {Math.ceil(capitalSocial * 0.25).toLocaleString('fr-MA')} MAD minimum).
                Augmentez la part de numeraire liberee (nature/industrie sont deja a 100%).
                Regle conforme a la Loi 5-96, Article 51.
              </p>
            </div>
          </div>
        )}

        {!importMode && capitalSocial > 0 && !isBlocked && (
          <div className="flex items-start gap-3 rounded-lg border-l-4 border-success bg-success/10 p-4">
            <CheckCircle2 className="mt-0.5 h-5 w-5 flex-shrink-0 text-success" />
            <p className="text-sm text-success">
              Liberation du capital conforme — {percentLibere}% libere sur le capital total ({'>='} 25% legal).
            </p>
          </div>
        )}
      </div>

      {/* Duree */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-5 text-base font-bold text-fg">
          Duree de la societe
        </h3>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Duree (annees)
            </label>
            <input aria-label="Duree (annees)"
              type="number"
              min={1}
              max={99}
              value={duree}
              onChange={(ev) => setDuree(Number(ev.target.value))}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm"
            />
          </div>
          {!importMode && (
          <>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Date de commencement d'exploitation
            </label>
            <input aria-label="Date de commencement d'exploitation"
              type="date"
              value={dateCommencement}
              onChange={(ev) => setDateCommencement(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Date de fin <span className="text-fg-subtle">(auto)</span>
            </label>
            <input aria-label="Date de fin (auto)"
              type="date"
              value={dateFin}
              readOnly
              className="h-10 w-full rounded-lg border-2 border-border bg-border px-3 text-sm text-fg-subtle"
            />
          </div>
          </>
          )}
        </div>
      </div>

      {/* 2026-08 (contrat CREATION) — Compte de depot du capital : requis
          uniquement quand les fonds sont en compte bloque (depotFondsBloque).
          Masque en IMPORT (pas de depot « a la constitution »). */}
      {!importMode && depotFondsBloque && (
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 flex items-center gap-2 text-lg font-bold text-fg">
          <Lock className="h-5 w-5 text-accent" /> Compte de depot du capital
        </h3>
        <p className="mb-4 text-xs text-fg-subtle">
          Etablissement et numero du compte bloque sur lequel les apports en numeraire
          sont deposes. Apparait dans la clause de depot des Statuts.
        </p>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Banque de depot <span className="text-danger">*</span>
            </label>
            <input aria-label="Banque de depot"
              type="text"
              value={depotBanqueNom}
              onChange={(ev) => setDepotBanqueNom(ev.target.value)}
              placeholder="Ex: Attijariwafa Bank, Agence Anfa"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              N° du compte de depot <span className="text-danger">*</span>
            </label>
            <input aria-label="N° du compte de depot"
              type="text"
              value={depotNumero}
              onChange={(ev) => setDepotNumero(ev.target.value)}
              placeholder="Ex: 007 780 1234567890123 45"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
        </div>
      </div>
      )}

      {/* 2026-08 (Phase 2-B) — Options de constitution (voie directeur) */}
      <div className="rounded-xl border border-border bg-bg-overlay p-6 shadow-sm">
        <h3 className="mb-4 text-base font-bold text-fg">Options de constitution</h3>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Libération des apports en numéraire
            </label>
            <select
              value={modeLiberation}
              onChange={(ev) => setModeLiberation(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="intégrale">Intégrale</option>
              <option value="partielle">Partielle (1/4 min., solde ≤ 5 ans)</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Mode de signature sociale (art. 15)
            </label>
            <select
              value={modeSignature}
              onChange={(ev) => setModeSignature(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="séparée">Séparée (sans limitation)</option>
              <option value="séparée avec plafond">Séparée avec plafond</option>
              <option value="conjointe">Conjointe (deux au moins)</option>
            </select>
          </div>
          {modeSignature === 'séparée avec plafond' && (
            <div>
              <label className="mb-1 block text-xs font-medium text-fg">
                Plafond par opération (DH)
              </label>
              <input aria-label="Plafond par opération (DH)"
                type="number"
                min={0}
                value={signaturePlafond || ''}
                onChange={(ev) => setSignaturePlafond(Number(ev.target.value))}
                placeholder="Ex: 50000"
                className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
              />
            </div>
          )}
          <label className="flex items-center gap-2 self-end pb-2 text-sm text-fg">
            <input
              type="checkbox"
              checked={depotFondsBloque}
              onChange={(ev) => setDepotFondsBloque(ev.target.checked)}
              className="h-4 w-4 rounded border-border"
            />
            Fonds déposés en compte bloqué (obligatoire si &gt; 100 000 DH)
          </label>
        </div>
      </div>

      {/* 2026-06-19 — Commissaire aux apports — conditionnel si nature > 100 000 MAD (Loi 5-96 Art. 53) */}
      {hasNature && nature > 100000 && (
        <div className="rounded-xl border border-warning/30 bg-warning/5 p-6 shadow-sm">
          <h3 className="mb-3 flex items-center gap-2 text-base font-bold text-fg">
            <AlertTriangle className="h-4 w-4 text-warning" /> Commissaire aux apports (obligatoire)
          </h3>
          <p className="mb-3 text-xs text-fg-subtle">
            Apport en nature &gt; 100 000 MAD : la designation d'un commissaire aux apports
            est requise (Loi 5-96 Article 53).
          </p>
          <input
            type="text"
            value={commissaireApportsNom}
            onChange={(ev) => setCommissaireApportsNom(ev.target.value)}
            placeholder="Nom du commissaire aux apports"
            className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
          />
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
