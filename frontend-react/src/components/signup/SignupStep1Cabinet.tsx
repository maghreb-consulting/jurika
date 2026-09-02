import { useId, useState } from 'react';
import { Building, Check, Hash, Mail, MapPin, User } from 'lucide-react';
import { emailMsg, isFilled } from '../../lib/formValidation';
import { PhoneNumberInput, isValidPhone } from '../../components/ui/PhoneNumberInput';
import { MOROCCO_PROVINCES } from '../../data/morocco-localities';
import {
  PROFESSIONAL_TYPES,
  entityNameLabel,
  entityNameRequired,
  type ProfessionalType,
} from '../../types/professional';

/**
 * Simplification inscription (2026-07-13) — Formulaire UNIQUE.
 *
 * Fusionne les anciennes etapes "Cabinet" et "Admin" en un seul ecran, sans
 * distinction personne physique / morale. Champs collectes :
 *  - Type de profil (requis)
 *  - Nom + Prenom (identifient la personne = compte SUPERVISEUR)
 *  - Denomination (nomme le workspace) — PRE-REMPLIE avec « Prenom Nom »,
 *    modifiable ; on n'ecrase jamais une saisie manuelle.
 *  - Email professionnel (unique — sert de contact cabinet + titulaire)
 *  - GSM (+212, sert au 2FA SMS)
 *  - Ville (siege social)
 *  - ICE : OPTIONNEL (15 chiffres si renseigne, completable plus tard dans
 *    les parametres du cabinet).
 *
 * Champs retires vs l'ancien wizard : IF, RC, email de contact cabinet
 * (double email supprime).
 */
export interface SignupProfileData {
  firstName: string;
  lastName: string;
  workspaceName: string;
  email: string;
  phone: string;
  city: string;
  /** ICE optionnel. '' tolere (completable ensuite). */
  ice: string;
}

interface Props {
  initial: SignupProfileData;
  onNext: (data: SignupProfileData) => void;
  /** Optionnel : revenir au choix du forfait (step 0). */
  onBack?: () => void;
  /** Type de profil choisi (porte par SignupPage). Pilote le libelle du champ nom. */
  professionalType?: ProfessionalType | null;
  onProfessionalTypeChange?: (type: ProfessionalType) => void;
}

export function SignupStep1Cabinet({
  initial,
  onNext,
  onBack,
  professionalType = null,
  onProfessionalTypeChange,
}: Props) {
  const [data, setData] = useState<SignupProfileData>(initial);
  // Pre-remplissage denomination : on ne (re)genere « Prenom Nom » que tant que
  // l'utilisateur n'a pas edite la denomination manuellement. Un draft resume
  // avec une denomination non-vide est considere comme deja « touche ».
  const [nameTouched, setNameTouched] = useState<boolean>(initial.workspaceName.trim() !== '');
  const [error, setError] = useState<string | null>(null);
  // Datalist des villes/provinces marocaines (meme reference que Step2Siege).
  const cityListId = useId();

  const nameLabel = entityNameLabel(professionalType);
  // Champs par type de profil (2026-07-27) : la denomination de structure est
  // obligatoire pour les personnes morales (Entreprise / Centre d'affaires) et
  // OPTIONNELLE pour les profils individuels (avocat, notaire, etc.), qui
  // peuvent exercer en leur nom propre. Si laisse vide pour un individuel, on
  // retombe sur « Prenom Nom » au submit.
  const nameRequired = entityNameRequired(professionalType);

  function autoDenomination(firstName: string, lastName: string): string {
    return `${firstName} ${lastName}`.trim();
  }

  function onFirstNameChange(value: string) {
    setData((d) => ({
      ...d,
      firstName: value,
      workspaceName: nameTouched ? d.workspaceName : autoDenomination(value, d.lastName),
    }));
  }

  function onLastNameChange(value: string) {
    setData((d) => ({
      ...d,
      lastName: value,
      workspaceName: nameTouched ? d.workspaceName : autoDenomination(d.firstName, value),
    }));
  }

  function onWorkspaceNameChange(value: string) {
    setData((d) => ({ ...d, workspaceName: value }));
    // S'il vide le champ, on reactive le pre-remplissage automatique.
    setNameTouched(value.trim() !== '');
  }

  function update<K extends keyof SignupProfileData>(key: K, value: SignupProfileData[K]) {
    setData((d) => ({ ...d, [key]: value }));
  }

  // Focus best-effort du 1er champ invalide (a11y) — aucune bulle native (form noValidate).
  function focusField(testId: string) {
    document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)?.focus();
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    // Validation JS AVANT tout appel (le <form> porte noValidate : c'est ce bloc
    // qui decide de la validite, avec messages FR affiches dans le bandeau stylé).
    if (!professionalType) return setError('Veuillez choisir votre type de profil.');
    if (!isFilled(data.firstName)) { focusField('signup-first-name'); return setError('Nom et prenom obligatoires.'); }
    if (!isFilled(data.lastName)) { focusField('signup-last-name'); return setError('Nom et prenom obligatoires.'); }
    // Denomination : obligatoire uniquement pour les structures (Entreprise /
    // Centre d'affaires). Pour un profil individuel laisse vide, on retombe sur
    // « Prenom Nom » plus bas (on ne bloque pas la soumission).
    let workspaceName = data.workspaceName.trim();
    if (nameRequired && workspaceName === '') {
      focusField('signup-workspace-name');
      return setError(`${nameLabel} obligatoire.`);
    }
    if (workspaceName === '') {
      workspaceName = autoDenomination(data.firstName, data.lastName);
    }
    const emailErr = emailMsg(data.email);
    if (emailErr) { focusField('signup-admin-email'); return setError(emailErr); }
    // Telephone : E.164 valide pour le pays choisi (Maroc par defaut). La
    // validation par pays (longueur, plage nationale) est deleguee a
    // libphonenumber-js via isValidPhone().
    if (!isValidPhone(data.phone.trim())) { focusField('signup-admin-phone'); return setError('Numero de telephone invalide pour le pays selectionne.'); }
    if (!isFilled(data.city)) { focusField('signup-city'); return setError('Ville obligatoire.'); }
    const ice = data.ice.trim();
    if (ice !== '' && !/^\d{15}$/.test(ice)) { focusField('signup-ice'); return setError('ICE : 15 chiffres requis (ou laissez vide).'); }
    onNext({
      firstName: data.firstName.trim(),
      lastName: data.lastName.trim(),
      workspaceName,
      email: data.email.trim(),
      phone: data.phone.trim(),
      city: data.city.trim(),
      ice,
    });
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8" data-testid="signup-step-cabinet">
      <div className="rounded-2xl bg-bg-raised p-6 shadow-md sm:p-8">
        <h2 className="mb-2 font-heading text-2xl font-semibold text-fg">Votre profil</h2>
        <p className="mb-6 text-sm text-fg-subtle">
          Quelques informations pour creer votre espace. Vous pourrez completer le reste plus tard.
        </p>

        {/* Selecteur de type de profil (requis). */}
        <fieldset className="mb-6" data-testid="signup-profile-type">
          <legend className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-fg">
            Type de profil <span className="text-danger">*</span>
          </legend>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {PROFESSIONAL_TYPES.map(({ code, label }) => {
              const selected = professionalType === code;
              return (
                <button
                  key={code}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => onProfessionalTypeChange?.(code)}
                  data-testid={`signup-profile-type-${code}`}
                  className={[
                    'flex items-center justify-between gap-1 rounded-lg border px-3 py-2 text-left text-xs font-medium transition-colors',
                    selected
                      ? 'border-accent bg-accent/10 text-fg'
                      : 'border-border-hi bg-bg-raised text-fg-muted hover:border-accent/50 hover:bg-bg-overlay',
                  ].join(' ')}
                >
                  <span>{label}</span>
                  {selected && <Check className="h-3.5 w-3.5 flex-shrink-0 text-accent" />}
                </button>
              );
            })}
          </div>
        </fieldset>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field icon={<User className="h-3.5 w-3.5" />} label="Prenom" required>
              <input
                type="text"
                required
                value={data.firstName}
                onChange={(e) => onFirstNameChange(e.target.value)}
                maxLength={80}
                data-testid="signup-first-name"
                className={inputCls}
              />
            </Field>
            <Field icon={<User className="h-3.5 w-3.5" />} label="Nom" required>
              <input
                type="text"
                required
                value={data.lastName}
                onChange={(e) => onLastNameChange(e.target.value)}
                maxLength={80}
                data-testid="signup-last-name"
                className={inputCls}
              />
            </Field>
          </div>

          <Field
            icon={<Building className="h-3.5 w-3.5" />}
            label={nameRequired ? nameLabel : `${nameLabel} (optionnel)`}
            required={nameRequired}
            hint={
              nameRequired
                ? 'Raison sociale de votre structure (obligatoire).'
                : 'Optionnel — laissez vide si vous exercez en votre nom propre (nous utiliserons « Prenom Nom »).'
            }
          >
            <input
              type="text"
              required={nameRequired}
              value={data.workspaceName}
              onChange={(e) => onWorkspaceNameChange(e.target.value)}
              placeholder="Cabinet ATLAS Consulting SARL"
              maxLength={150}
              data-testid="signup-workspace-name"
              className={inputCls}
            />
          </Field>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field icon={<Mail className="h-3.5 w-3.5" />} label="Email professionnel" required>
              <input
                type="email"
                required
                value={data.email}
                onChange={(e) => update('email', e.target.value)}
                placeholder="prenom.nom@cabinet-atlas.ma"
                maxLength={150}
                data-testid="signup-admin-email"
                className={inputCls}
              />
            </Field>
            {/* Telephone : selecteur d'indicatif pays (drapeau + code) + numero.
                Maroc par defaut ; valeur stockee en E.164 dans data.phone. */}
            <PhoneNumberInput
              label="Telephone"
              required
              name="phone"
              data-testid="signup-admin-phone"
              value={data.phone}
              onChange={(v) => update('phone', v)}
              defaultCountry="MA"
              hint="Numero mobile ou fixe. Sert au contact et, si active, au 2FA par SMS."
            />
            {/* Ville : liste deroulante recherchable des villes/provinces
                marocaines (meme reference que Step2Siege). Free-text tolere. */}
            <Field icon={<MapPin className="h-3.5 w-3.5" />} label="Ville" required>
              <input
                type="text"
                required
                list={cityListId}
                value={data.city}
                onChange={(e) => update('city', e.target.value)}
                placeholder="Casablanca"
                maxLength={80}
                autoComplete="off"
                data-testid="signup-city"
                className={inputCls}
              />
              <datalist id={cityListId}>
                {MOROCCO_PROVINCES.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </Field>
            <Field
              icon={<Hash className="h-3.5 w-3.5" />}
              label="ICE (optionnel)"
              hint="15 chiffres. Vous pourrez le completer plus tard dans les parametres du cabinet."
            >
              <input
                type="text"
                value={data.ice}
                onChange={(e) => update('ice', e.target.value.replace(/\D/g, '').slice(0, 15))}
                placeholder="002345678000089"
                maxLength={15}
                inputMode="numeric"
                data-testid="signup-ice"
                className={inputCls}
              />
            </Field>
          </div>

          {error && (
            <div className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" data-testid="signup-step1-error">
              {error}
            </div>
          )}
          <div className="mt-2 flex gap-3">
            {onBack && (
              <button
                type="button"
                onClick={onBack}
                data-testid="signup-step1-back"
                className={btnSecondary}
              >
                ← Forfait
              </button>
            )}
            <button
              type="submit"
              data-testid="signup-step1-next"
              className={btnPrimary}
            >
              Continuer → Securite
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

const inputCls = 'w-full rounded-lg border border-border-hi px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30';
const btnPrimary = 'flex-1 rounded-lg bg-accent px-4 py-3 text-sm font-semibold text-bg transition-colors hover:bg-accent-hover';
const btnSecondary = 'rounded-lg border border-border-hi bg-bg-raised px-4 py-3 text-sm font-semibold text-fg hover:bg-bg-overlay transition-colors';

interface FieldProps {
  icon: React.ReactNode;
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}
function Field({ icon, label, required, hint, children }: FieldProps) {
  return (
    <label className="block">
      <span className="mb-1 flex items-center gap-1.5 text-xs font-semibold text-fg">
        <span className="text-fg-subtle">{icon}</span>
        {label}
        {required && <span className="text-danger">*</span>}
      </span>
      {children}
      {hint && <span className="mt-1 block text-[11px] leading-snug text-fg-subtle">{hint}</span>}
    </label>
  );
}
