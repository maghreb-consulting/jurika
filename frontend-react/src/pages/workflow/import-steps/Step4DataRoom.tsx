import { useState } from 'react';
import { CheckCircle2, ChevronRight, FolderOpen, Info } from 'lucide-react';

interface Props {
  existing?: Record<string, unknown>;
  /** Dossier deja attache au ticket -- autofill du champ dossierId. */
  preselectedDossierId?: string | null;
  /** Etapes amont (step1 / step2 / step3) pour resume. */
  data: Record<string, Record<string, unknown>>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
}

/**
 * Lit la couche metier d'une step en tolerant les 2 schemas (nicheage cote
 * backend "step1.info" et acces direct "step1"). Fix 2026-06-06 -- avant ce
 * patch, la recap affichait "-" partout car le front lisait stepData.step1.X
 * alors que le backend ecrit stepData.step1.info.X.
 */
function unwrap(
  data: Record<string, Record<string, unknown>>,
  stepKey: string,
  innerKey: string,
): Record<string, unknown> {
  const step = (data[stepKey] as Record<string, unknown> | undefined) ?? {};
  const inner = step[innerKey];
  if (inner && typeof inner === 'object' && !Array.isArray(inner)) {
    return inner as Record<string, unknown>;
  }
  return step;
}

/**
 * IMPORT -- Etape 4 : recap dossier + confirmation finale du rattachement.
 *
 * Le dossier est deja cree (IMPORT auto-create cote ticket-service) et les
 * documents sont deja uploades dans la dataroom au moment de leur depot
 * dans Step2 / Step3. Cette etape se contente d'afficher la synthese
 * et de demander l'accord explicite de l'utilisateur (RG-IM06).
 */
export function Step4DataRoom({
  existing,
  preselectedDossierId,
  data,
  saving,
  onSubmit,
}: Props) {
  const e = (existing?.dataroom as { dossierId?: string; confirmed?: boolean } | undefined) ?? {};
  const [dossierId, setDossierId] = useState<string>(
    e.dossierId ?? preselectedDossierId ?? '',
  );
  const [confirmed, setConfirmed] = useState<boolean>(e.confirmed ?? false);

  const info = unwrap(data, 'step1', 'info');
  const juridique = unwrap(data, 'step2', 'juridique');
  const docs = ((juridique.documents as unknown[]) ?? []) as Array<{
    filename: string;
    type: string;
    uploaded?: boolean;
  }>;
  const uploadedJuridique = docs.filter((d) => d.uploaded).length;

  const financier = unwrap(data, 'step3', 'financier');
  const fin = ((financier.documentsFinanciers as unknown[]) ?? []) as Array<{
    filename: string;
    annee?: number;
    categorie?: string;
    uploaded?: boolean;
  }>;
  const uploadedFin = fin.filter((d) => d.uploaded).length;

  const canSubmit = !!dossierId.trim() && confirmed;

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        // Garde-fou JS (dossier rattache + confirmation cochee) depuis que
        // `noValidate` neutralise la validation native.
        if (!canSubmit) return;
        void onSubmit({ dossierId: dossierId.trim(), dataroom: { dossierId: dossierId.trim(), confirmed } });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      <div className="rounded-xl border border-accent/20 bg-accent/10 p-4 text-xs text-fg">
        Les documents importes a l etape 2 et l etape 3 ont deja ete deposes
        dans la Data Room du dossier ({uploadedJuridique + uploadedFin}
        document(s) ranges). Validez cette etape pour finaliser le
        rattachement.
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 flex items-center gap-2 text-base font-bold text-fg">
          <FolderOpen className="h-5 w-5 text-accent" />
          Recap du dossier
        </h3>
        <dl className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
          <Row label="Raison sociale" value={String(info.raisonSociale ?? '—')} />
          <Row label="Forme juridique" value={String(info.formeJuridique ?? '—')} />
          <Row label="ICE" value={String(info.ice ?? '—')} />
          <Row label="RC" value={String(info.rcNumero ?? '—')} />
          <Row label="IF" value={String(info.ifNumero ?? '—')} />
          <Row
            label="Capital social"
            value={`${Number(info.capital ?? 0).toLocaleString('fr-MA')} MAD`}
          />
        </dl>
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 text-base font-bold text-fg">
          Documents deposes ({uploadedJuridique + uploadedFin})
        </h3>
        {docs.length === 0 && fin.length === 0 ? (
          <p className="text-xs text-fg-subtle">
            Aucun document importe. Vous pourrez en deposer plus tard depuis la Data Room.
          </p>
        ) : (
          <ul className="space-y-1 text-xs text-fg-muted">
            {docs.map((d, i) => (
              <li key={`j-${i}`} className="flex items-center gap-2">
                {d.uploaded ? (
                  <CheckCircle2 className="h-3 w-3 text-success" />
                ) : (
                  <span className="text-danger">⚠</span>
                )}
                Juridique : {d.filename} ({d.type})
              </li>
            ))}
            {fin.map((d, i) => (
              <li key={`f-${i}`} className="flex items-center gap-2">
                {d.uploaded ? (
                  <CheckCircle2 className="h-3 w-3 text-success" />
                ) : (
                  <span className="text-danger">⚠</span>
                )}
                Financier : {d.filename}
                {d.annee != null ? ` (${d.annee})` : ''}
                {d.categorie ? ` · ${d.categorie}` : ''}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <label className="mb-1 block text-xs font-medium text-fg">
          Identifiant du dossier
        </label>
        <input aria-label="Identifiant du dossier"
          type="text"
          value={dossierId}
          onChange={(ev) => setDossierId(ev.target.value)}
          placeholder="Auto-rempli depuis le ticket si disponible"
          className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
          readOnly={!!preselectedDossierId}
        />
        {!dossierId && (
          <p className="mt-1 flex items-center gap-1 text-[11px] text-warning">
            <Info className="h-3 w-3" />
            Dossier non rattache -- relancez le workflow depuis un ticket
            IMPORT pour beneficier de l auto-creation du dossier.
          </p>
        )}

        <label className="mt-4 flex items-start gap-2 text-sm text-fg">
          <input
            type="checkbox"
            checked={confirmed}
            onChange={(ev) => setConfirmed(ev.target.checked)}
            className="mt-1 h-4 w-4 rounded"
          />
          <span>
            Je confirme le rattachement des documents importes a la Data Room
            de ce dossier.
          </span>
        </label>
      </div>

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
          {saving ? 'Validation en cours...' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start gap-2">
      <CheckCircle2 className="mt-0.5 h-4 w-4 flex-shrink-0 text-success" />
      <div>
        <p className="text-xs font-semibold uppercase text-fg-subtle">{label}</p>
        <p className="text-sm text-fg">{value}</p>
      </div>
    </div>
  );
}
