import { useEffect, useRef, useState } from 'react';
import {
  Building,
  Hash,
  Mail,
  AlertTriangle,
  CheckCircle2,
  Upload,
  Trash2,
  Image as ImageIcon,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { workspaceService, type WorkspaceProfile } from '../../services/workspace.service';
import { extractError } from '../../lib/api';
import { PROFESSIONAL_TYPE_LABELS, isProfessionalType } from '../../types/professional';

/**
 * Onglet « Cabinet » des parametres (SUPERVISEUR).
 *
 * 2026-07-14 — Papier a en-tete : au-dela de l'ICE/ville, on configure ici le
 * nom affiche, l'adresse, le telephone, le site web, le RC et l'IF du cabinet,
 * ainsi que son logo. Un apercu en direct montre l'en-tete et le pied de page
 * des PDF generes (Fiche client, etat des debours...).
 */
export function CabinetTab() {
  const [profile, setProfile] = useState<WorkspaceProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [ice, setIce] = useState('');
  const [city, setCity] = useState('');
  const [docName, setDocName] = useState('');
  const [adresse, setAdresse] = useState('');
  const [telephone, setTelephone] = useState('');
  const [siteWeb, setSiteWeb] = useState('');
  const [rc, setRc] = useState('');
  const [iff, setIff] = useState('');

  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [logoBusy, setLogoBusy] = useState(false);
  const [logoError, setLogoError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  function applyProfile(p: WorkspaceProfile) {
    setProfile(p);
    setIce(p.ice ?? '');
    setCity(p.city ?? '');
    setDocName(p.nomAfficheDocuments ?? '');
    setAdresse(p.adresse ?? '');
    setTelephone(p.telephone ?? '');
    setSiteWeb(p.siteWeb ?? '');
    setRc(p.rcNumber ?? '');
    setIff(p.ifFiscal ?? '');
  }

  async function loadLogo(has: boolean) {
    setLogoUrl((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return null;
    });
    if (!has) return;
    const url = await workspaceService.getLogoObjectUrl().catch(() => null);
    if (url) setLogoUrl(url);
  }

  useEffect(() => {
    let cancelled = false;
    workspaceService
      .getProfile()
      .then((p) => {
        if (cancelled) return;
        applyProfile(p);
        void loadLogo(p.hasLogo);
      })
      .catch((e) => { if (!cancelled) setLoadError(extractError(e).message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Revoke l'object-URL du logo au demontage.
  useEffect(() => () => { if (logoUrl) URL.revokeObjectURL(logoUrl); }, [logoUrl]);

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaved(false);
    // Validation JS AVANT l'appel API (form noValidate : pas de bulle native).
    const trimmedIce = ice.trim();
    if (trimmedIce !== '' && !/^\d{15}$/.test(trimmedIce)) {
      setFormError('ICE : 15 chiffres requis (ou laissez vide).');
      document.querySelector<HTMLElement>('[data-testid="cabinet-ice-input"]')?.focus();
      return;
    }
    if (city.trim().length < 2) {
      setFormError('Ville : 2 caracteres minimum.');
      document.querySelector<HTMLElement>('[data-testid="cabinet-city-input"]')?.focus();
      return;
    }
    setSaving(true);
    try {
      const updated = await workspaceService.updateProfile({
        ice: trimmedIce,
        city: city.trim(),
        nomAfficheDocuments: docName.trim(),
        adresse: adresse.trim(),
        telephone: telephone.trim(),
        siteWeb: siteWeb.trim(),
        rcNumber: rc.trim(),
        ifFiscal: iff.trim(),
      });
      applyProfile(updated);
      setSaved(true);
    } catch (err) {
      setFormError(extractError(err).message);
    } finally {
      setSaving(false);
    }
  }

  async function onLogoSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = ''; // permet de re-selectionner le meme fichier
    if (!file) return;
    setLogoError(null);
    setLogoBusy(true);
    try {
      await workspaceService.uploadLogo(file);
      const p = await workspaceService.getProfile();
      applyProfile(p);
      await loadLogo(true);
    } catch (err) {
      setLogoError(extractError(err).message);
    } finally {
      setLogoBusy(false);
    }
  }

  async function removeLogo() {
    setLogoError(null);
    setLogoBusy(true);
    try {
      await workspaceService.deleteLogo();
      const p = await workspaceService.getProfile();
      applyProfile(p);
      await loadLogo(false);
    } catch (err) {
      setLogoError(extractError(err).message);
    } finally {
      setLogoBusy(false);
    }
  }

  if (loading) {
    return <p className="text-sm text-fg-muted" data-testid="cabinet-tab-loading">Chargement…</p>;
  }
  if (loadError || !profile) {
    return (
      <p className="text-sm text-danger" data-testid="cabinet-tab-error">
        {loadError ?? 'Impossible de charger les informations du cabinet.'}
      </p>
    );
  }

  const typeLabel = isProfessionalType(profile.professionalType)
    ? PROFESSIONAL_TYPE_LABELS[profile.professionalType]
    : '—';

  const effectiveName = docName.trim() !== '' ? docName.trim() : profile.name;
  const contactParts = [telephone.trim(), profile.contactEmail ?? '', siteWeb.trim()].filter(
    (s) => s && s.length > 0,
  );
  const legalParts = [
    ice.trim() ? `ICE : ${ice.trim()}` : '',
    rc.trim() ? `RC : ${rc.trim()}` : '',
    iff.trim() ? `IF : ${iff.trim()}` : '',
  ].filter((s) => s.length > 0);

  return (
    <div className="space-y-4" data-testid="cabinet-tab">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
          <Building className="h-5 w-5 text-accent" /> Cabinet
        </h2>
        <p className="mt-1 text-sm text-fg-muted">
          Coordonnees et papier a en-tete de votre cabinet — utilises en en-tete et pied
          de page des documents generes (Fiche client, etat des debours…).
        </p>
      </div>

      {profile.iceMissing && (
        <div
          className="flex items-start gap-2 rounded-xl border border-warning/40 bg-warning/10 p-4 text-sm text-fg"
          data-testid="cabinet-ice-missing"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
          <span>
            Votre ICE n'est pas encore renseigne. Completez-le ci-dessous : il sera
            requis pour les documents et dossiers de vos clients.
          </span>
        </div>
      )}

      {/* Apercu du papier a en-tete */}
      <section className="rounded-2xl border border-border bg-bg-raised p-5">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          <ImageIcon className="h-4 w-4" /> Apercu du papier a en-tete
        </h3>
        <div className="rounded-xl border border-border-hi bg-white p-5 text-[#050d1f]">
          <div className="flex items-start gap-4 border-b-2 border-[#a8853d] pb-3">
            {logoUrl && (
              <img
                src={logoUrl}
                alt="Logo du cabinet"
                className="h-12 w-auto max-w-[160px] object-contain"
                data-testid="cabinet-logo-preview"
              />
            )}
            <div className="min-w-0">
              <p className="font-heading text-lg font-semibold leading-tight">{effectiveName}</p>
              {adresse.trim() && <p className="text-xs text-[#64748b]">{adresse.trim()}</p>}
              {contactParts.length > 0 && (
                <p className="text-xs text-[#64748b]">{contactParts.join('  ·  ')}</p>
              )}
            </div>
          </div>
          <p className="mt-6 text-center text-2xl font-semibold font-heading">Fiche client</p>
          <div className="mt-6 border-t border-[#a8853d] pt-2 text-center text-[11px] text-[#64748b]">
            {legalParts.length > 0 && <p>{legalParts.join('   ·   ')}</p>}
            <p>Genere par JURIKA le {new Date().toLocaleDateString('fr-FR')} · Page 1 / 1</p>
          </div>
        </div>
      </section>

      {/* Logo */}
      <section className="rounded-2xl border border-border bg-bg-raised p-5">
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-fg-subtle">Logo</h3>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex h-16 w-40 items-center justify-center rounded-lg border border-border bg-bg-overlay">
            {logoUrl ? (
              <img src={logoUrl} alt="Logo" className="max-h-14 max-w-[150px] object-contain" />
            ) : (
              <span className="text-xs text-fg-subtle">Aucun logo</span>
            )}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="image/png,image/jpeg"
            className="hidden"
            onChange={onLogoSelected}
            data-testid="cabinet-logo-input"
          />
          <Button
            type="button"
            variant="secondary"
            size="sm"
            loading={logoBusy}
            onClick={() => fileInput.current?.click()}
          >
            <Upload className="mr-1.5 h-4 w-4" /> {profile.hasLogo ? 'Remplacer' : 'Televerser'}
          </Button>
          {profile.hasLogo && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={logoBusy}
              onClick={removeLogo}
              className="text-danger hover:bg-danger/10"
            >
              <Trash2 className="mr-1.5 h-4 w-4" /> Supprimer
            </Button>
          )}
        </div>
        <p className="mt-2 text-xs text-fg-subtle">PNG ou JPG, 1 Mo maximum.</p>
        {logoError && (
          <p className="mt-2 flex items-center gap-1.5 text-sm text-danger" data-testid="cabinet-logo-error">
            <AlertTriangle className="h-4 w-4" /> {logoError}
          </p>
        )}
      </section>

      {/* Lecture seule */}
      <section className="rounded-2xl border border-border bg-bg-raised p-5">
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-fg-subtle">Identite</h3>
        <dl className="space-y-2 text-sm">
          <ReadOnly icon={<Building className="h-3.5 w-3.5" />} label="Denomination" value={profile.name} />
          <ReadOnly label="Type de profil" value={typeLabel} />
          <ReadOnly icon={<Mail className="h-3.5 w-3.5" />} label="Email de contact" value={profile.contactEmail ?? '—'} />
        </dl>
      </section>

      {/* Edition */}
      <form onSubmit={handleSave} className="rounded-2xl border border-border bg-bg-raised p-5 space-y-4" noValidate>
        <h3 className="text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          Informations modifiables
        </h3>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField
            label="Nom affiche sur les documents"
            hint="En-tete des PDF. Vide = denomination du cabinet."
            value={docName}
            onChange={(e) => setDocName(e.target.value.slice(0, 150))}
            placeholder={profile.name}
            maxLength={150}
            data-testid="cabinet-docname-input"
          />
          <TextField
            label="ICE (15 chiffres)"
            hint="Laissez vide si vous ne l'avez pas encore."
            value={ice}
            onChange={(e) => setIce(e.target.value.replace(/\D/g, '').slice(0, 15))}
            placeholder="002345678000089"
            inputMode="numeric"
            mono
            data-testid="cabinet-ice-input"
          />
          <TextField
            label="RC (registre du commerce)"
            value={rc}
            onChange={(e) => setRc(e.target.value.slice(0, 20))}
            placeholder="45219"
            maxLength={20}
            data-testid="cabinet-rc-input"
          />
          <TextField
            label="Identifiant fiscal (IF)"
            value={iff}
            onChange={(e) => setIff(e.target.value.slice(0, 8))}
            placeholder="12345678"
            maxLength={8}
            mono
            data-testid="cabinet-if-input"
          />
          <TextField
            label="Telephone"
            value={telephone}
            onChange={(e) => setTelephone(e.target.value.slice(0, 30))}
            placeholder="+212 5 22 00 00 00"
            maxLength={30}
            data-testid="cabinet-tel-input"
          />
          <TextField
            label="Site web"
            value={siteWeb}
            onChange={(e) => setSiteWeb(e.target.value.slice(0, 200))}
            placeholder="www.mon-cabinet.ma"
            maxLength={200}
            data-testid="cabinet-web-input"
          />
          <TextField
            label="Ville du siege"
            value={city}
            onChange={(e) => setCity(e.target.value)}
            placeholder="Casablanca"
            maxLength={80}
            data-testid="cabinet-city-input"
          />
          <div className="sm:col-span-2">
            <TextField
              label="Adresse du siege"
              value={adresse}
              onChange={(e) => setAdresse(e.target.value.slice(0, 300))}
              placeholder="12, rue des Consultants, Maarif, Casablanca"
              maxLength={300}
              data-testid="cabinet-adresse-input"
            />
          </div>
        </div>

        {formError && (
          <p className="flex items-center gap-1.5 text-sm text-danger" data-testid="cabinet-form-error">
            <AlertTriangle className="h-4 w-4" /> {formError}
          </p>
        )}
        {saved && (
          <p className="flex items-center gap-1.5 text-sm text-success" data-testid="cabinet-saved">
            <CheckCircle2 className="h-4 w-4" /> Informations enregistrees.
          </p>
        )}

        <Button type="submit" loading={saving} data-testid="cabinet-save">
          <Hash className="mr-1.5 h-4 w-4" /> Enregistrer
        </Button>
      </form>
    </div>
  );
}

function ReadOnly({ icon, label, value }: { icon?: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="flex items-center gap-1.5 text-fg-subtle">
        {icon && <span className="text-fg-subtle">{icon}</span>}
        {label}
      </dt>
      <dd className="text-right font-medium text-fg">{value}</dd>
    </div>
  );
}
