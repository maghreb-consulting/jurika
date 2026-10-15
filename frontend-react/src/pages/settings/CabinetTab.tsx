import { useEffect, useRef, useState } from 'react';
import { Building, Mail, Upload, Trash2, Image as ImageIcon } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { SectionOnglet } from './EcranOuOnglet';
import { TextField } from '../../components/ui/TextField';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { workspaceService, type WorkspaceProfile } from '../../services/workspace.service';
import { extractError } from '../../lib/api';
import { PROFESSIONAL_TYPE_LABELS, isProfessionalType } from '../../types/professional';

/**
 * Onglet « Cabinet » des parametres (SUPERVISEUR).
 *
 * 2026-07-14 — Papier a en-tete : au-dela de l'ICE/ville, on configure ici le
 * nom affiche, l'adresse, le telephone, le site web, le RC et l'IF du cabinet,
 * ainsi que son logo. Un apercu en direct montre l'en-tete et le pied de page
 * des documents de synthese en PDF (fiche client, fiche dossier, etat des debours ; lot
 * L5b E3, RG-FIC-08) : le logo JURIKA a gauche, le bloc du cabinet a droite.
 */
// Couleurs du document IMPRIME (papier a en-tete des PDF) : identiques dans les deux themes,
// l'apercu montre la feuille telle qu'elle sortira. Seule exception aux jetons de l'ecran.
const PAPIER = { fond: '#ffffff', encre: '#050d1f', or: '#a8853d', gris: '#64748b' } as const; // couleurs-imprimees

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
      setFormError('ICE : saisissez ses 15 chiffres, ou laissez le champ vide.');
      document.querySelector<HTMLElement>('[data-testid="cabinet-ice-input"]')?.focus();
      return;
    }
    if (city.trim().length < 2) {
      setFormError('Ville du siège : saisissez au moins 2 caractères.');
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

  const enTete = {
    titre: 'Cabinet',
    description:
      'Coordonnées et papier à en-tête de votre cabinet — utilisés en en-tête et pied de page des documents de synthèse en PDF (fiche client, fiche dossier, état des débours).',
    aide: (
      <TexteAide cle="parametres-cabinet" titre="Compléter la fiche du cabinet">
        <p>
          Renseignez les coordonnées du cabinet (la ville du siège est obligatoire), puis cliquez sur
          « Enregistrer ». L’aperçu se met à jour pendant la saisie. Le logo s’enregistre dès qu’il est
          choisi. La dénomination et l’adresse de contact s’affichent ici en lecture seule.
        </p>
      </TexteAide>
    ),
  };

  if (loading) {
    return (
      <SectionOnglet {...enTete}>
        <div data-testid="cabinet-tab-loading">
          <SqueletteZone libelle="Chargement des informations du cabinet">
            <div className="space-y-4">
              <CardSkeleton className="h-56" />
              <CardSkeleton className="h-32" />
              <CardSkeleton className="h-72" />
            </div>
          </SqueletteZone>
        </div>
      </SectionOnglet>
    );
  }
  if (loadError || !profile) {
    return (
      <SectionOnglet {...enTete}>
        <div data-testid="cabinet-tab-error">
          <Message ton="erreur">{loadError ?? 'Impossible de charger les informations du cabinet.'}</Message>
        </div>
      </SectionOnglet>
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
    <SectionOnglet {...enTete}>
      <div className="space-y-4" data-testid="cabinet-tab">
        {profile.iceMissing && (
          <div data-testid="cabinet-ice-missing">
            <Message ton="attention">
              L’ICE du cabinet n’est pas encore renseigné. Complétez-le ci-dessous : il sera exigé pour
              les documents et les dossiers de vos clients.
            </Message>
          </div>
        )}

        {/* Apercu du papier a en-tete */}
        <Card
          title={
            <span className="flex items-center gap-2">
              <ImageIcon className="h-4 w-4 text-fg-subtle" aria-hidden="true" /> Aperçu du papier à en-tête
              <InfoBulle
                libelle="Où apparaît ce papier à en-tête ?"
                texte="Sur les documents de synthèse téléchargés en PDF : fiche client, fiche dossier et état des débours. Le logo JURIKA figure à gauche de l’en-tête, votre logo et vos coordonnées à droite ; l’ICE, le RC et l’IF du cabinet en pied de page. Les actes générés suivent, eux, les modèles du cabinet."
              />
            </span>
          }
        >
          {/* La feuille reproduit le document IMPRIME : memes couleurs dans les deux themes
              (papier blanc, encre marine, filet or), d'ou les couleurs fixes de PAPIER. */}
          <div
            className="rounded-lg border border-border-hi p-5"
            style={{ background: PAPIER.fond, color: PAPIER.encre }}
          >
            <div className="flex items-start justify-end gap-4 border-b-2 pb-3 text-right" style={{ borderColor: PAPIER.or }}>
              <div className="min-w-0">
                <p className="text-lg font-semibold leading-tight">{effectiveName}</p>
                {adresse.trim() && <p className="text-xs" style={{ color: PAPIER.gris }}>{adresse.trim()}</p>}
                {contactParts.length > 0 && (
                  <p className="text-xs" style={{ color: PAPIER.gris }}>{contactParts.join('  ·  ')}</p>
                )}
              </div>
              {logoUrl && (
                <img
                  src={logoUrl}
                  alt="Logo du cabinet"
                  className="h-12 w-auto max-w-[160px] object-contain"
                  data-testid="cabinet-logo-preview"
                />
              )}
            </div>
            <p className="mt-6 text-center text-2xl font-semibold">Fiche client</p>
            <div className="mt-6 border-t pt-2 text-center text-[11px]" style={{ borderColor: PAPIER.or, color: PAPIER.gris }}>
              {legalParts.length > 0 && <p>{legalParts.join('   ·   ')}</p>}
              <p>Édité par JURIKA le {new Date().toLocaleDateString('fr-FR')} · Page 1 / 1</p>
            </div>
          </div>
        </Card>

        {/* Logo */}
        <Card title="Logo" description="PNG ou JPG, 1 Mo maximum. Le logo s’enregistre dès qu’il est choisi.">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex h-16 w-40 items-center justify-center rounded-lg border border-border bg-bg-overlay">
              {logoUrl ? (
                <img src={logoUrl} alt="Logo" className="max-h-14 max-w-[150px] object-contain" />
              ) : (
                <span className="text-xs text-fg-muted">Aucun logo</span>
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
              icon={<Upload />}
            >
              {profile.hasLogo ? 'Remplacer' : 'Téléverser'}
            </Button>
            {profile.hasLogo && (
              <Button type="button" variant="danger" size="sm" disabled={logoBusy} onClick={removeLogo} icon={<Trash2 />}>
                Supprimer
              </Button>
            )}
          </div>
          {logoError && (
            <div className="mt-3" data-testid="cabinet-logo-error">
              <Message ton="erreur">{logoError}</Message>
            </div>
          )}
        </Card>

        {/* Lecture seule */}
        <Card title="Identité" description="Enregistrée à l’inscription du cabinet ; elle ne se modifie pas ici.">
          <dl className="space-y-2 text-sm">
            <ReadOnly icon={<Building className="h-3.5 w-3.5" />} label="Dénomination" value={profile.name} />
            <ReadOnly label="Type de profil" value={typeLabel} />
            <ReadOnly icon={<Mail className="h-3.5 w-3.5" />} label="Adresse électronique de contact" value={profile.contactEmail ?? '—'} />
          </dl>
        </Card>

        {/* Edition */}
        <Card title="Informations modifiables">
          <form onSubmit={handleSave} className="space-y-4" noValidate>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <TextField
                label="Nom affiché sur les documents"
                hint="En-tête des PDF. Laissé vide, la dénomination du cabinet s’affiche."
                value={docName}
                onChange={(e) => setDocName(e.target.value.slice(0, 150))}
                placeholder={profile.name}
                maxLength={150}
                data-testid="cabinet-docname-input"
              />
              <TextField
                label="ICE (15 chiffres)"
                hint="Laissez vide si vous ne l’avez pas encore."
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
                label="Téléphone"
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
                label="Ville du siège"
                required
                value={city}
                onChange={(e) => setCity(e.target.value)}
                placeholder="Casablanca"
                maxLength={80}
                data-testid="cabinet-city-input"
              />
              <div className="sm:col-span-2">
                <TextField
                  label="Adresse du siège"
                  value={adresse}
                  onChange={(e) => setAdresse(e.target.value.slice(0, 300))}
                  placeholder="12, rue des Consultants, Maarif, Casablanca"
                  maxLength={300}
                  data-testid="cabinet-adresse-input"
                />
              </div>
            </div>

            {formError && (
              <div data-testid="cabinet-form-error">
                <Message ton="erreur">{formError}</Message>
              </div>
            )}
            {saved && (
              <div data-testid="cabinet-saved">
                <Message ton="succes">Informations enregistrées.</Message>
              </div>
            )}

            <div className="flex justify-end">
              <Button type="submit" loading={saving} data-testid="cabinet-save">
                Enregistrer
              </Button>
            </div>
          </form>
        </Card>
      </div>
    </SectionOnglet>
  );
}

function ReadOnly({ icon, label, value }: { icon?: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-0.5">
      <dt className="flex items-center gap-1.5 text-fg-muted">
        {icon && <span className="text-fg-subtle" aria-hidden="true">{icon}</span>}
        {label}
      </dt>
      <dd className="break-all text-right font-medium text-fg">{value}</dd>
    </div>
  );
}
