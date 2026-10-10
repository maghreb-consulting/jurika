import { useEffect, useState } from 'react';
import { Drawer } from '../../../components/ui/Drawer';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/TextField';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import type {
  DossierJuridiqueView,
  UpdateIdentifiantsPayload,
  VersionTaxeProfessionnelle,
} from '../../../types/dataroom';

interface Props {
  open: boolean;
  view: DossierJuridiqueView;
  onClose: () => void;
  onSaved: () => void | Promise<void>;
  /**
   * RG-U02-04 — lecture seule (superviseur / non-responsable). Le formulaire est
   * consultable mais non editable : champs desactives, pas de bouton Enregistrer.
   */
  readOnly?: boolean;
}

type FormState = {
  ice: string;
  rcNumero: string;
  rcTribunal: string;
  identifiantFiscal: string;
  taxeProfessionnelle: string;
  cnss: string;
  adresseSiege: string;
  ville: string;
  capitalSocialMad: string;
  dateConstitution: string;
  taxeProfessionnelleDateEffet: string;
};

function initial(view: DossierJuridiqueView): FormState {
  return {
    ice: view.ice ?? '',
    rcNumero: view.rcNumero ?? '',
    rcTribunal: view.rcTribunal ?? '',
    identifiantFiscal: view.identifiantFiscal ?? '',
    taxeProfessionnelle: view.taxeProfessionnelle ?? '',
    cnss: view.cnss ?? '',
    adresseSiege: view.adresseSiege ?? '',
    ville: view.ville ?? '',
    capitalSocialMad:
      view.capitalSocialMad === null || view.capitalSocialMad === undefined
        ? ''
        : String(view.capitalSocialMad),
    dateConstitution: view.dateConstitution ?? '',
    taxeProfessionnelleDateEffet: '',
  };
}

/**
 * Fiche client (2026-07-14) — formulaire « Identifiants de la société ».
 * Permet à l'employé responsable / au superviseur de compléter les identifiants
 * post-immatriculation (RC, IF, patente, CNSS...) requis par la Fiche client.
 */
export function IdentifiantsDrawer({ open, view, onClose, onSaved, readOnly = false }: Props) {
  const [form, setForm] = useState<FormState>(() => initial(view));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [versions, setVersions] = useState<VersionTaxeProfessionnelle[] | null>(null);

  // Lot L1 (RG-FIC-02) : versions successives de la taxe professionnelle.
  useEffect(() => {
    if (!open) return undefined;
    let actif = true;
    dataroomService
      .listVersionsTp(view.dossierId)
      .then((v) => actif && setVersions(v))
      .catch(() => actif && setVersions([]));
    return () => {
      actif = false;
    };
  }, [open, view.dossierId]);

  function set<K extends keyof FormState>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  const trimOrNull = (s: string): string | null => {
    const t = s.trim();
    return t === '' ? null : t;
  };

  async function save() {
    setSaving(true);
    setError(null);
    try {
      const capitalRaw = form.capitalSocialMad.trim();
      const capital = capitalRaw === '' ? null : Number(capitalRaw);
      if (capital !== null && (Number.isNaN(capital) || capital < 0)) {
        setError('Le capital social doit être un nombre positif.');
        setSaving(false);
        return;
      }
      const payload: UpdateIdentifiantsPayload = {
        ice: trimOrNull(form.ice),
        rcNumero: trimOrNull(form.rcNumero),
        rcTribunal: trimOrNull(form.rcTribunal),
        identifiantFiscal: trimOrNull(form.identifiantFiscal),
        taxeProfessionnelle: trimOrNull(form.taxeProfessionnelle),
        cnss: trimOrNull(form.cnss),
        adresseSiege: trimOrNull(form.adresseSiege),
        ville: trimOrNull(form.ville),
        capitalSocialMad: capital,
        dateConstitution: trimOrNull(form.dateConstitution),
        taxeProfessionnelleDateEffet: trimOrNull(form.taxeProfessionnelleDateEffet),
      };
      await dataroomService.updateIdentifiants(view.dossierId, payload);
      await onSaved();
      onClose();
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Identifiants de la société"
      subtitle={view.raisonSociale}
      width="lg"
      footer={
        readOnly ? (
          <div className="flex items-center justify-end">
            <Button variant="secondary" size="sm" onClick={onClose}>
              Fermer
            </Button>
          </div>
        ) : (
          <div className="flex items-center justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={onClose} disabled={saving}>
              Annuler
            </Button>
            <Button size="sm" onClick={save} loading={saving}>
              Enregistrer
            </Button>
          </div>
        )
      }
    >
      {error && (
        <p className="mb-4 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </p>
      )}
      {readOnly ? (
        <p className="mb-4 rounded-lg border border-border bg-bg-overlay px-3 py-2 text-sm text-fg-subtle">
          Consultation en lecture seule. Seul l'employé responsable du dossier peut
          modifier ces identifiants (RG-U02-04).
        </p>
      ) : (
        <p className="mb-4 text-sm text-fg-subtle">
          Complétez les identifiants obtenus après l'immatriculation. Les champs
          laissés vides seront enregistrés comme non renseignés.
        </p>
      )}
      <fieldset
        disabled={readOnly}
        className="grid grid-cols-1 gap-4 border-0 p-0 disabled:opacity-70 sm:grid-cols-2"
      >
        <TextField
          label="ICE"
          value={form.ice}
          onChange={(e) => set('ice', e.target.value)}
          mono
        />
        <TextField
          label="Numéro RC"
          value={form.rcNumero}
          onChange={(e) => set('rcNumero', e.target.value)}
        />
        <TextField
          label="Tribunal du RC"
          value={form.rcTribunal}
          onChange={(e) => set('rcTribunal', e.target.value)}
        />
        <TextField
          label="Identifiant fiscal (IF)"
          value={form.identifiantFiscal}
          onChange={(e) => set('identifiantFiscal', e.target.value)}
          mono
        />
        <TextField
          label="Taxe professionnelle (patente)"
          value={form.taxeProfessionnelle}
          onChange={(e) => set('taxeProfessionnelle', e.target.value)}
          mono
        />
        <TextField
          label="Date d’effet de la taxe professionnelle"
          type="date"
          value={form.taxeProfessionnelleDateEffet}
          onChange={(e) => set('taxeProfessionnelleDateEffet', e.target.value)}
          hint="Facultative : vous pourrez la compléter plus tard."
        />
        <TextField
          label="CNSS"
          value={form.cnss}
          onChange={(e) => set('cnss', e.target.value)}
          mono
        />
        <TextField
          label="Ville"
          value={form.ville}
          onChange={(e) => set('ville', e.target.value)}
        />
        <TextField
          label="Capital social (MAD)"
          type="number"
          min={0}
          step="0.01"
          value={form.capitalSocialMad}
          onChange={(e) => set('capitalSocialMad', e.target.value)}
        />
        <TextField
          label="Date de constitution"
          type="date"
          value={form.dateConstitution}
          onChange={(e) => set('dateConstitution', e.target.value)}
        />
        <div className="sm:col-span-2">
          <TextField
            label="Adresse du siège"
            value={form.adresseSiege}
            onChange={(e) => set('adresseSiege', e.target.value)}
          />
        </div>
      </fieldset>

      <section aria-labelledby="versions-tp" className="mt-6 space-y-2">
        <h3 id="versions-tp" className="flex items-center gap-1 text-sm font-semibold text-fg">
          Versions de la taxe professionnelle
          <InfoBulle
            libelle="Pourquoi plusieurs versions ?"
            texte="Quand le numéro de taxe professionnelle change, l’ancien est conservé : chaque version garde sa date de prise d’effet, et la plus récente est en vigueur."
          />
        </h3>
        {!readOnly && (
          <TexteAide cle="versions-tp" titre="Changer ou dater la taxe professionnelle">
            <p>
              Saisissez le nouveau numéro et sa date d’effet, puis enregistrez : une nouvelle version
              est créée et les précédentes restent consultables. Si la date d’effet n’est pas encore
              connue, laissez-la vide ; vous la compléterez plus tard en saisissant la date avec le
              même numéro.
            </p>
          </TexteAide>
        )}
        {versions && versions.length === 0 && (
          <p className="text-sm text-fg-subtle">Aucune taxe professionnelle enregistrée.</p>
        )}
        {versions && versions.length > 0 && (
          <ol className="divide-y divide-border rounded-md border border-border">
            {[...versions].reverse().map((v) => (
              <li key={v.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                <span className="font-mono text-fg">{v.numero}</span>
                <span className="text-fg-muted">
                  {v.dateEffet
                    ? `En vigueur à partir du ${new Date(v.dateEffet).toLocaleDateString('fr-MA')}`
                    : 'Date d’effet à compléter'}
                </span>
                {v.enVigueur && (
                  <span className="rounded-full bg-accent/10 px-2 py-0.5 text-xs font-medium text-accent">
                    En vigueur
                  </span>
                )}
              </li>
            ))}
          </ol>
        )}
      </section>
    </Drawer>
  );
}
