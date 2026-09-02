import { useState } from 'react';
import { Drawer } from '../../../components/ui/Drawer';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/TextField';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';
import type {
  DossierJuridiqueView,
  UpdateIdentifiantsPayload,
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
    </Drawer>
  );
}
