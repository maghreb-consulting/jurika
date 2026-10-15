import { useState } from 'react';
import {
  CheckCircle,
  FileText,
  Printer,
} from 'lucide-react';
import { useStepAutosave } from '../useStepAutosave';
import type { DirtyGetter } from '../useWorkflow';
import { DOCUMENTS_PARCOURS } from './documents-creation';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import { Button } from '../../../components/ui/Button';
import { BandeauFinParcours } from '../../../components/workflow/BandeauFinParcours';
import { TextField, ZoneTexte } from '../../../components/ui/TextField';

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
  // Lot L3 : le lieu (et la date) de signature se saisissent a l'etape 7, une seule
  // fois, sans valeur par defaut ("Casablanca" etait invente ici).
  const complementsEtape7 = (data.step7 as { complements?: Record<string, string> })?.complements ?? {};
  const lieuSignatureEtape7 = (complementsEtape7.lieuSignature ?? '').trim();
  const acteExisting = ((data.step9 as { acteParams?: Record<string, unknown> })?.acteParams) ?? {};
  const dirs = ((data.step5 as { dirigeants?: Array<{ nom: string; prenom: string; fonction?: string; isStatutaire: boolean }> })?.dirigeants ??
    []) as Array<{ nom: string; prenom: string; fonction?: string; isStatutaire: boolean }>;
  const ass = ((data.step6 as { associes?: Array<{ nom: string; prenom: string; nombreParts: number; pourcentageDetention?: number }> })?.associes ??
    []) as Array<{ nom: string; prenom: string; nombreParts: number; pourcentageDetention?: number }>;

  // 2026-08 (contrat CREATION) — Paramètres de l'acte (societe.* voie directeur).
  const [premierExerciceCloture, setPremierExerciceCloture] = useState<string>(
    (acteExisting.premierExerciceCloture as string) ?? '',
  );
  const [engagementsMandat, setEngagementsMandat] = useState<string>(
    (acteExisting.engagementsMandat as string) ?? '',
  );
  const [articleDesignationStatuts, setArticleDesignationStatuts] = useState<string>(
    (acteExisting.articleDesignationStatuts as string) ?? '',
  );

  const [success, setSuccess] = useState(false);

  // 2026-08 (contrat CREATION) — Paramètres de l'acte consommés par le builder
  // (societe.*). Persistés sous step9.acteParams.
  const acteParams = {
    premierExerciceCloture,
    engagementsMandat,
    articleDesignationStatuts,
  };

  /*
   * L6 : les documents VALIDES a l'etape 7, sous leur libelle du parcours. Avant, la
   * liste etait ecrite en dur (« Statuts_X.pdf » meme sans statuts generes, au format
   * PDF retire) et son bouton « Telecharger » ne faisait rien.
   */
  const step7 = (data.step7 ?? {}) as Record<string, unknown>;
  const etatsDocs = (step7.documents ?? step7.generated ?? {}) as Record<string, { validated?: boolean }>;
  const documentsValides = Object.entries(etatsDocs)
    .filter(([, st]) => st?.validated === true)
    .map(([code]) => DOCUMENTS_PARCOURS.find((d) => d.code === code)?.libelle ?? 'Document de la création');

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
    { label: 'Dénomination', value: denomination },
    // Fix B2 (2026-08-16) — la forme était codée en dur : une SARL AU était
    // présentée comme une « SARL » dans sa propre synthèse de création.
    { label: 'Forme juridique', value: formeLabel },
    { label: 'N° du certificat négatif', value: String(den.cnNumero ?? '—') },
    { label: 'ICE', value: String(den.ice ?? '—') },
    {
      label: 'Capital social',
      value: `${capitalSocial.toLocaleString('fr-MA')} MAD`,
    },
    {
      label: 'Capital libéré',
      value: `${capitalLibere.toLocaleString('fr-MA')} MAD (${pctLibere}%)`,
    },
    {
      label: 'Parts sociales',
      value: `${nbParts} parts × ${valeurNominale.toFixed(2)} MAD`,
    },
    { label: 'Siège social', value: pick([siege, siegeRaw], 'adresse') || '—' },
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
      label: 'Activité',
      value: (pick([act, actRaw], 'description', 'objet') || '—').slice(0, 120),
    },
    { label: 'Durée', value: pick([cap, capRaw], 'dureeAnnees') ? `${pick([cap, capRaw], 'dureeAnnees')} ans` : 'non confirmée' },
    {
      // Fix A3 — la date de commencement d'exercice a migré à l'étape Capital
      // (dé-dup 2026-08) : on lit les deux étapes plutôt qu'une seule.
      label: 'Début de l’exercice',
      value:
        pick(
          [cap, capRaw, act, actRaw],
          'dateDebutExercice',
          'dateCommencementExercice',
          'exerciceDebut',
        ) || '—',
    },
    {
      label: 'Gérant(s)',
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
      label: 'Associés',
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
        <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-success text-success-fg animate-bounce">
          <CheckCircle className="h-12 w-12" />
        </div>
        <h2 className="text-2xl font-black text-success">
          Dossier finalisé
        </h2>
        <p className="text-sm text-fg-subtle">
          La création de <strong>{denomination}</strong> a été finalisée. Les
          documents validés sont disponibles dans la Data Room du dossier.
        </p>
        <div className="rounded-xl border border-success/20 bg-success-soft p-5 text-left text-sm">
          <p className="mb-2 font-semibold text-success">Prochaines étapes</p>
          <ul className="list-disc space-y-1 pl-5 text-fg-muted">
            <li>Dépôt des documents au greffe du tribunal de commerce</li>
            <li>Publication de l’annonce au journal d’annonces légales</li>
            <li>Obtention du numéro de RC et finalisation administrative</li>
          </ul>
        </div>
      </div>
    );
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="mx-auto max-w-[860px] space-y-6">
      {/* Fix B2 — titre aligné sur la forme réelle du dossier. */}
      <BandeauFinParcours titre={<>Synthèse — Création {formeLabel}</>}>
        Toutes les étapes sont complétées. Voici le récapitulatif du dossier.
      </BandeauFinParcours>
      <TexteAide cle="creation-etape-9" titre="Ce qu’il faut faire">
        Relisez le récapitulatif ; une erreur se corrige à l’étape concernée (cliquez sur
        son numéro). Complétez au besoin les paramètres de l’acte, puis cliquez sur
        « Finaliser le dossier ». Viennent ensuite le dépôt au greffe, la publication de
        l’annonce légale et l’obtention du numéro de RC.
      </TexteAide>

      {/* 2026-08 (contrat CREATION) — Paramètres de l'acte (voie directeur). */}
      <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
        <div className="border-b border-border p-5">
          <h3 className="text-base font-bold text-fg">Paramètres de l’acte</h3>
          <p className="mt-1 text-xs text-fg-subtle">
            Éléments de rédaction repris dans les statuts et l’acte. L’exercice social, le
            commissaire aux comptes, le nombre d’originaux et le lieu, la date et l’heure de
            signature se saisissent à l’étape 7, avant la génération.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-2">
          <div>
            <TextField
              label="Clôture du premier exercice (facultatif)"
              aria-label="Clôture du premier exercice (facultatif)"
              type="text"
              value={premierExerciceCloture}
              onChange={(e) => setPremierExerciceCloture(e.target.value)}
              placeholder="Ex. : 31 décembre 2026"
            />
          </div>
          <div className="md:col-span-2">
            <p className="mb-1 block text-xs font-medium text-fg">Saisis à l’étape 7, « Signature des actes »</p>
            <ul className="text-sm text-fg">
              {[
                ['Nombre d’exemplaires originaux', complementsEtape7.nombreOriginaux],
                ['Heure de l’acte', complementsEtape7.heureActe],
                ['Exercice social', complementsEtape7.exerciceDebut && complementsEtape7.exerciceFin
                  ? `du ${complementsEtape7.exerciceDebut} au ${complementsEtape7.exerciceFin}` : ''],
                ['Commissaire aux comptes', complementsEtape7.commissaireComptesNom
                  ? `${complementsEtape7.commissaireComptesNom}${complementsEtape7.dureeMandatCac ? `, ${complementsEtape7.dureeMandatCac} exercices` : ''}`
                  : 'aucun'],
              ].map(([l, v]) => (
                <li key={l}>
                  {l} : {v || 'non confirmé'}
                </li>
              ))}
            </ul>
          </div>
          <div>
            <p className="mb-1 block text-xs font-medium text-fg">Lieu de signature</p>
            <p className="text-sm text-fg">
              {lieuSignatureEtape7 || 'Non renseigné'}
              <span className="ml-1 text-xs text-fg-subtle">(saisi à l’étape 7, « Signature des actes »)</span>
            </p>
          </div>
          <div className="md:col-span-2">
            <label htmlFor="creation-article-designation" className="mb-1 flex items-center gap-1 text-xs font-medium text-fg">
              Article de désignation des statuts (facultatif)
              <InfoBulle
                libelle="Qu’est-ce que l’article de désignation ?"
                texte="L’article des statuts qui désigne les premiers gérants (par exemple « Article 14 ») ; il est cité dans l’acte. Laissez vide s’il n’y a pas lieu de le citer."
              />
            </label>
            <TextField
              id="creation-article-designation"
              aria-label="Article de désignation des statuts (facultatif)"
              type="text"
              value={articleDesignationStatuts}
              onChange={(e) => setArticleDesignationStatuts(e.target.value)}
              placeholder="Ex. : Article 14"
            />
          </div>
          <div className="md:col-span-2">
            <ZoneTexte
              label="Engagements repris au titre du mandat (facultatif)"
              aria-label="Engagements repris au titre du mandat (facultatif)"
              rows={2}
              value={engagementsMandat}
              onChange={(e) => setEngagementsMandat(e.target.value)}
              placeholder="Actes accomplis pour le compte de la société en formation…"
              className="resize-none"
            />
          </div>
        </div>
      </div>

      {/* Recap */}
      <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
        <div className="border-b border-border p-5">
          <h3 className="text-base font-bold text-fg">
            Récapitulatif de la société
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
            Documents validés
          </h3>
        </div>
        <div className="space-y-2 p-5">
          {documentsValides.length === 0 ? (
            <p className="text-sm text-fg-subtle">
              Aucun document validé pour l’instant. Revenez à l’étape 7 pour générer et
              valider les documents de la création.
            </p>
          ) : (
            documentsValides.map((libelle, i) => <DocLine key={`${i}-${libelle}`} name={libelle} />)
          )}
        </div>
      </div>

      {/* Actions finales */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-4">
          <Button type="button" variant="secondary" size="lg" fullWidth icon={<Printer />} onClick={() => window.print()}>
            Imprimer la synthèse (ou l’enregistrer en PDF)
          </Button>
        </div>
        <Button type="submit" size="lg" fullWidth icon={<CheckCircle />} loading={saving}>
          {saving ? 'Finalisation en cours…' : 'Finaliser le dossier'}
        </Button>
      </div>
    </form>
  );
}

function DocLine({ name }: { name: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-success/20 bg-success-soft p-3">
      <FileText className="h-5 w-5 text-danger" />
      <span className="flex-1 text-sm font-medium text-fg">{name}</span>
      <CheckCircle className="h-4 w-4 text-success" />
      <span className="text-xs text-fg-subtle">Dans la Data Room du dossier</span>
    </div>
  );
}
