import { useState } from 'react';
import {
  CheckCircle,
  FileText,
  Printer,
} from 'lucide-react';
import { useStepAutosave } from '../useStepAutosave';
import type { DirtyGetter } from '../useWorkflow';

interface Props {
  data: Record<string, Record<string, unknown>>;
  saving: boolean;
  onSubmit: () => Promise<void>;
  /**
   * 2026-08 (contrat CREATION) — persiste les paramètres de l'acte
   * (step9.acteParams) avant génération / finalisation.
   */
  onSave?: (payload: Record<string, unknown>) => Promise<void> | void;
  /** 2026-08 — flush au navigation (comme les autres étapes). */
  registerDirty?: (step: number, getter: DirtyGetter) => () => void;
}

/**
 * Déniche la couche métier d'une étape — fix A2 + A3 (2026-08-16).
 *
 * Le backend range chaque étape sous une clé dédiée
 * ({@code step1.denomination}, {@code step2.siege}, {@code step3.capital},
 * {@code step4.activite}), tandis qu'un brouillon enregistré à plat ne l'imbrique
 * PAS. La synthèse lisait `data.step1.denomination` sans dénicher : selon le
 * chemin emprunté elle récupérait tantôt la chaîne, tantôt l'OBJET step1 entier.
 *
 * De là les deux défauts observés :
 *  - A2 : « Dénomination [object Object] » et un fichier
 *    `Statuts_[object_Object].pdf` — intermittent, car il fallait un aller-retour
 *    étape 7 ↔ 9 pour basculer d'une représentation à l'autre ;
 *  - A3 : « Tribunal » et « Date début exercice » vides alors qu'ils étaient
 *    saisis, simplement parce qu'ils vivent une couche plus bas.
 */
function unwrapStep(
  step: unknown,
  innerKey: string,
): Record<string, unknown> {
  const bag = (step as Record<string, unknown>) ?? {};
  const inner = bag[innerKey];
  return inner && typeof inner === 'object' && !Array.isArray(inner)
    ? (inner as Record<string, unknown>)
    : bag;
}

/** Première valeur scalaire NON vide parmi plusieurs clés candidates. */
function pick(bags: Array<Record<string, unknown>>, ...keys: string[]): string {
  for (const bag of bags) {
    for (const k of keys) {
      const v = bag[k];
      if (v == null) continue;
      // Un objet n'est jamais une valeur affichable : c'est précisément ce qui
      // produisait « [object Object] ».
      if (typeof v === 'object') continue;
      const s = String(v).trim();
      if (s) return s;
    }
  }
  return '';
}

export function Step9Synthese({ data, saving, onSubmit, onSave, registerDirty }: Props) {
  const den = unwrapStep(data.step1, 'denomination');
  const siege = unwrapStep(data.step2, 'siege');
  const cap = unwrapStep(data.step3, 'capital');
  const act = unwrapStep(data.step4, 'activite');
  /** Bags bruts : certains champs restent au niveau de l'étape (non imbriqués). */
  const denRaw = (data.step1 as Record<string, unknown>) ?? {};
  const siegeRaw = (data.step2 as Record<string, unknown>) ?? {};
  const capRaw = (data.step3 as Record<string, unknown>) ?? {};
  const actRaw = (data.step4 as Record<string, unknown>) ?? {};
  // 2026-08 (contrat CREATION) — pré-remplissage : ville du siège pour lieuSignature.
  const siegeInner = (siege.siege as Record<string, unknown>) ?? siege;
  const defaultLieu =
    (siegeInner.villeGreffe as string) ??
    (siegeInner.province as string) ??
    'Casablanca';
  const acteExisting = ((data.step9 as { acteParams?: Record<string, unknown> })?.acteParams) ?? {};
  const dirs = ((data.step5 as { dirigeants?: Array<{ nom: string; prenom: string; fonction?: string; isStatutaire: boolean }> })?.dirigeants ??
    []) as Array<{ nom: string; prenom: string; fonction?: string; isStatutaire: boolean }>;
  const ass = ((data.step6 as { associes?: Array<{ nom: string; prenom: string; nombreParts: number; pourcentageDetention?: number }> })?.associes ??
    []) as Array<{ nom: string; prenom: string; nombreParts: number; pourcentageDetention?: number }>;

  // 2026-08 (contrat CREATION) — Paramètres de l'acte (societe.* voie directeur).
  const [commissaireComptesNom, setCommissaireComptesNom] = useState<string>(
    (acteExisting.commissaireComptesNom as string) ?? '',
  );
  const [dureeMandatCac, setDureeMandatCac] = useState<number>(
    Number(acteExisting.dureeMandatCac ?? 3),
  );
  const [exerciceDebut, setExerciceDebut] = useState<string>(
    (acteExisting.exerciceDebut as string) ?? '1er janvier',
  );
  const [exerciceFin, setExerciceFin] = useState<string>(
    (acteExisting.exerciceFin as string) ?? '31 décembre',
  );
  const [premierExerciceCloture, setPremierExerciceCloture] = useState<string>(
    (acteExisting.premierExerciceCloture as string) ?? '',
  );
  const [engagementsMandat, setEngagementsMandat] = useState<string>(
    (acteExisting.engagementsMandat as string) ?? '',
  );
  const [lieuSignature, setLieuSignature] = useState<string>(
    (acteExisting.lieuSignature as string) ?? defaultLieu,
  );
  const [nombreOriginaux, setNombreOriginaux] = useState<number>(
    Number(acteExisting.nombreOriginaux ?? 6),
  );
  const [articleDesignationStatuts, setArticleDesignationStatuts] = useState<string>(
    (acteExisting.articleDesignationStatuts as string) ?? '',
  );
  const [heureActe, setHeureActe] = useState<string>(
    (acteExisting.heureActe as string) ?? '10 heures',
  );

  const [success, setSuccess] = useState(false);

  // 2026-08 (contrat CREATION) — Paramètres de l'acte consommés par le builder
  // (societe.*). Persistés sous step9.acteParams.
  const acteParams = {
    commissaireComptesNom,
    dureeMandatCac,
    exerciceDebut,
    exerciceFin,
    premierExerciceCloture,
    engagementsMandat,
    lieuSignature,
    nombreOriginaux,
    articleDesignationStatuts,
    heureActe,
  };

  // Flush au navigation (persiste step9.acteParams sans clic explicite).
  useStepAutosave(9, () => ({ acteParams }), registerDirty);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Persiste les paramètres de l'acte avant la finalisation.
    await onSave?.({ acteParams });
    await onSubmit();
    setSuccess(true);
  }

  // Fix A2 — jamais d'objet stringifié : `pick` ignore les valeurs non scalaires.
  const denomination = pick([den, denRaw], 'denomination', 'raisonSociale') || '—';
  // Fix B2 — forme juridique RÉELLE (elle vit à l'étape 1, imbriquée ou à plat).
  const forme = pick([den, denRaw], 'formeJuridique', 'forme').toUpperCase();
  const isAU = forme === 'SARL_AU' || forme === 'SARL AU';
  const formeLabel = isAU ? 'SARL AU' : 'SARL';
  const capitalSocial = Number(cap.capitalSocialMad ?? 0);
  const capitalLibere = Number(cap.capitalLibere ?? 0);
  const pctLibere = capitalSocial > 0 ? Math.round((capitalLibere / capitalSocial) * 100) : 0;
  const nbParts = Number(cap.nombreParts ?? 0);
  const valeurNominale = Number(cap.valeurNominale ?? 0);

  const recap = [
    { label: 'Denomination', value: denomination },
    // Fix B2 (2026-08-16) — la forme était codée en dur : une SARL AU était
    // présentée comme une « SARL » dans sa propre synthèse de création.
    { label: 'Forme juridique', value: formeLabel },
    { label: 'N° Certificat Negatif', value: String(den.cnNumero ?? '—') },
    { label: 'ICE', value: String(den.ice ?? '—') },
    {
      label: 'Capital social',
      value: `${capitalSocial.toLocaleString('fr-MA')} MAD`,
    },
    {
      label: 'Capital libere',
      value: `${capitalLibere.toLocaleString('fr-MA')} MAD (${pctLibere}%)`,
    },
    {
      label: 'Parts sociales',
      value: `${nbParts} parts x ${valeurNominale.toFixed(2)} MAD`,
    },
    { label: 'Siege social', value: pick([siege, siegeRaw], 'adresse') || '—' },
    {
      // Fix A3 — le tribunal est saisi tantôt à l'étape 1 (tribunal du certificat
      // négatif), tantôt à l'étape 2 (ville du greffe) : on regarde les deux, et
      // sous les deux représentations (imbriquée / à plat).
      label: 'Tribunal',
      value:
        pick(
          [siege, siegeRaw, den, denRaw],
          'tribunal',
          'tribunalCompetent',
          'villeGreffe',
          'rcVille',
        ) || '—',
    },
    {
      label: 'Activite',
      value: (pick([act, actRaw], 'description', 'objet') || '—').slice(0, 120),
    },
    { label: 'Duree', value: `${pick([cap, capRaw], 'dureeAnnees') || 99} ans` },
    {
      // Fix A3 — la date de commencement d'exercice a migré à l'étape Capital
      // (dé-dup 2026-08) : on lit les deux étapes plutôt qu'une seule.
      label: 'Date debut exercice',
      value:
        pick(
          [cap, capRaw, act, actRaw],
          'dateDebutExercice',
          'dateCommencementExercice',
          'exerciceDebut',
        ) || '—',
    },
    {
      label: 'Gerant(s)',
      value:
        dirs.length > 0
          ? dirs
              .map(
                (d) =>
                  `${d.prenom} ${d.nom}${d.isStatutaire ? ' (statutaire)' : ''}`,
              )
              .join(', ')
          : '—',
    },
    {
      label: 'Associes',
      value:
        ass.length > 0
          ? ass
              .map(
                (a) =>
                  `${a.prenom} ${a.nom} (${(a.pourcentageDetention ?? 0).toFixed(0)}%)`,
              )
              .join(', ')
          : '—',
    },
  ];

  if (success) {
    return (
      <div className="mx-auto max-w-[640px] space-y-6 text-center">
        <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-success text-bg-raised animate-bounce">
          <CheckCircle className="h-12 w-12" />
        </div>
        <h2 className="text-2xl font-black text-success">
          Dossier finalise !
        </h2>
        <p className="text-sm text-fg-subtle">
          La creation de <strong>{denomination}</strong> a ete finalisee. Tous les
          documents sont disponibles dans la Data Room du client.
        </p>
        <div className="rounded-xl border border-success/20 bg-success/10 p-5 text-left text-sm">
          <p className="mb-2 font-semibold text-success">Prochaines etapes</p>
          <ul className="list-disc space-y-1 pl-5 text-fg-muted">
            <li>Depot des documents au greffe du tribunal de commerce</li>
            <li>Publication de l'annonce au Journal d'Annonces Legales (JAL)</li>
            <li>Recuperation du numero RC et finalisation administrative</li>
          </ul>
        </div>
      </div>
    );
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="mx-auto max-w-[860px] space-y-6">
      <div className="rounded-xl bg-gradient-to-r from-[#10B981] to-[#059669] p-6 text-bg-raised">
        <div className="mb-2 flex items-center gap-3">
          <CheckCircle className="h-8 w-8" />
          <h2 className="text-2xl font-black">
            {/* Fix B2 — titre aligné sur la forme réelle du dossier. */}
            Synthese — Creation {formeLabel}
          </h2>
        </div>
        <p className="text-green-100">
          Toutes les etapes sont completees. Voici le recapitulatif du dossier.
        </p>
      </div>

      {/* 2026-08 (contrat CREATION) — Paramètres de l'acte (voie directeur). */}
      <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
        <div className="border-b border-border p-5">
          <h3 className="text-base font-bold text-fg">Parametres de l'acte</h3>
          <p className="mt-1 text-xs text-fg-subtle">
            Elements de redaction repris dans les statuts et l'acte (exercice social,
            commissaire aux comptes, lieu et heure de signature…).
          </p>
        </div>
        <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Commissaire aux comptes (optionnel)
            </label>
            <input aria-label="Commissaire aux comptes (optionnel)"
              type="text"
              value={commissaireComptesNom}
              onChange={(e) => setCommissaireComptesNom(e.target.value)}
              placeholder="Nom du commissaire aux comptes"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Duree du mandat du CAC (annees)
            </label>
            <input aria-label="Duree du mandat du CAC (annees)"
              type="number"
              min={1}
              value={dureeMandatCac}
              onChange={(e) => setDureeMandatCac(Number(e.target.value))}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Debut de l'exercice social
            </label>
            <input aria-label="Debut de l'exercice social"
              type="text"
              value={exerciceDebut}
              onChange={(e) => setExerciceDebut(e.target.value)}
              placeholder="1er janvier"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Fin de l'exercice social
            </label>
            <input aria-label="Fin de l'exercice social"
              type="text"
              value={exerciceFin}
              onChange={(e) => setExerciceFin(e.target.value)}
              placeholder="31 décembre"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Cloture du premier exercice (optionnel)
            </label>
            <input aria-label="Cloture du premier exercice (optionnel)"
              type="text"
              value={premierExerciceCloture}
              onChange={(e) => setPremierExerciceCloture(e.target.value)}
              placeholder="Ex: 31 décembre 2026"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Lieu de signature
            </label>
            <input aria-label="Lieu de signature"
              type="text"
              value={lieuSignature}
              onChange={(e) => setLieuSignature(e.target.value)}
              placeholder="Ex: Casablanca"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Heure de l'acte
            </label>
            <input aria-label="Heure de l'acte"
              type="text"
              value={heureActe}
              onChange={(e) => setHeureActe(e.target.value)}
              placeholder="10 heures"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Nombre d'originaux
            </label>
            <input aria-label="Nombre d'originaux"
              type="number"
              min={1}
              value={nombreOriginaux}
              onChange={(e) => setNombreOriginaux(Number(e.target.value))}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div className="md:col-span-2">
            <label className="mb-1 block text-xs font-medium text-fg">
              Article de designation des statuts (optionnel)
            </label>
            <input aria-label="Article de designation des statuts (optionnel)"
              type="text"
              value={articleDesignationStatuts}
              onChange={(e) => setArticleDesignationStatuts(e.target.value)}
              placeholder="Ex: Article 14"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
          <div className="md:col-span-2">
            <label className="mb-1 block text-xs font-medium text-fg">
              Engagements repris au titre du mandat (optionnel)
            </label>
            <textarea aria-label="Engagements repris au titre du mandat (optionnel)"
              rows={2}
              value={engagementsMandat}
              onChange={(e) => setEngagementsMandat(e.target.value)}
              placeholder="Actes accomplis pour le compte de la societe en formation…"
              className="w-full resize-none rounded-lg border-2 border-border bg-bg-overlay px-3 py-2 text-sm focus:border-accent focus:outline-none"
            />
          </div>
        </div>
      </div>

      {/* Recap */}
      <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
        <div className="border-b border-border p-5">
          <h3 className="text-base font-bold text-fg">
            Recapitulatif de la societe
          </h3>
        </div>
        <div className="p-5">
          <div className="grid grid-cols-1 gap-x-8 gap-y-3 md:grid-cols-2">
            {recap.map((item) => (
              <div
                key={item.label}
                className="flex items-start gap-2 border-b border-border py-2"
              >
                <span className="w-40 flex-shrink-0 pt-0.5 text-xs text-fg-subtle">
                  {item.label}
                </span>
                <span className="text-sm font-medium text-fg">
                  {item.value}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Documents */}
      <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
        <div className="border-b border-border p-5">
          <h3 className="text-base font-bold text-fg">
            Documents generes
          </h3>
        </div>
        <div className="space-y-2 p-5">
          <DocLine name={`Statuts_${denomination.replace(/\s/g, '_')}.pdf`} />
          {dirs
            .filter((d) => !d.isStatutaire)
            .map((d) => (
              <DocLine
                key={`${d.prenom}-${d.nom}`}
                name={`Acte_Nomination_${d.prenom}_${d.nom}.pdf`}
              />
            ))}
        </div>
      </div>

      {/* Actions finales */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-4 grid grid-cols-1 gap-4 md:grid-cols-2">
          <button
            type="button"
            className="flex h-12 items-center justify-center gap-2 rounded-xl border-2 border-border text-sm font-medium text-fg transition hover:border-accent"
          >
            <Printer className="h-4 w-4" /> Imprimer la synthese
          </button>
          <button
            type="button"
            className="flex h-12 items-center justify-center gap-2 rounded-xl border-2 border-border text-sm font-medium text-fg transition hover:border-accent"
          >
            <FileText className="h-4 w-4" /> Exporter en PDF
          </button>
        </div>
        <button
          type="submit"
          disabled={saving}
          className={`flex h-14 w-full items-center justify-center gap-3 rounded-xl font-bold transition ${
            !saving
              ? 'bg-success text-bg-raised shadow-lg shadow-[#10B981]/20 hover:bg-success/85'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          <CheckCircle className="h-6 w-6" />
          {saving ? 'Finalisation en cours…' : 'Finaliser le dossier'}
        </button>
      </div>
    </form>
  );
}

function DocLine({ name }: { name: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-success/20 bg-success/10 p-3">
      <FileText className="h-5 w-5 text-danger" />
      <span className="flex-1 text-sm font-medium text-fg">{name}</span>
      <CheckCircle className="h-4 w-4 text-success" />
      <button
        type="button"
        className="text-xs text-accent hover:underline"
      >
        Telecharger
      </button>
    </div>
  );
}
