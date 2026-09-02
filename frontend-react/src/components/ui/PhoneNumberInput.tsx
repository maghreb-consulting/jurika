import {
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { ChevronDown, Search } from 'lucide-react';
import { AsYouType, isValidPhoneNumber, parsePhoneNumberFromString } from 'libphonenumber-js';
import type { CountryCode } from 'libphonenumber-js';
import { twMerge } from 'tailwind-merge';
import {
  COUNTRIES,
  countryByIso2,
  sortedCountries,
  type Country,
} from '../../data/countries';
// Vrais drapeaux SVG (indispensable sur Windows ou les emojis-drapeaux ne
// s'affichent pas — Chrome montre « MA » a la place). flag-icons bundle des SVG
// vectoriels via des classes CSS `fi fi-<iso2>`. Import unique (Vite dedupe).
import 'flag-icons/css/flag-icons.min.css';

interface Props {
  /** Numero complet au format E.164 (ex. `+212612345678`). '' si vide. */
  value: string;
  /** Emet le numero complet recompose en E.164 (indicatif + numero national). */
  onChange: (value: string) => void;
  /** Pays par defaut (iso2, ex. `MA`). Utilise quand `value` est vide. */
  defaultCountry?: string;
  label?: string;
  error?: string;
  hint?: string;
  required?: boolean;
  id?: string;
  name?: string;
  disabled?: boolean;
  placeholder?: string;
  'data-testid'?: string;
}

/** Enleve accents + casse pour une recherche tolerante (nom ou indicatif). */
function normalize(s: string): string {
  return s
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

/**
 * Recompose l'E.164 a partir du pays + du numero national saisi. Le `0` de
 * tete (prefixe national) est retire (jamais present en E.164). Renvoie '' si
 * aucun chiffre national n'est saisi (le champ est alors considere vide).
 */
export function composeE164(country: Country, national: string): string {
  const digits = national.replace(/\D/g, '').replace(/^0+/, '');
  return digits ? `+${country.dialCode}${digits}` : '';
}

/**
 * Valide un numero E.164 pour le pays deduit de l'indicatif. S'appuie sur
 * libphonenumber-js (longueur + plage nationale selon le pays).
 */
export function isValidPhone(e164: string): boolean {
  if (!e164) return false;
  try {
    return isValidPhoneNumber(e164);
  } catch {
    return false;
  }
}

/** Decompose un E.164 en { pays, numero national } (best-effort). */
function decompose(e164: string, fallback: Country): { country: Country; national: string } {
  if (e164) {
    const parsed = parsePhoneNumberFromString(e164);
    if (parsed) {
      const country =
        (parsed.country && countryByIso2(parsed.country)) ||
        COUNTRIES.find((c) => c.dialCode === parsed.countryCallingCode) ||
        fallback;
      return { country, national: parsed.nationalNumber };
    }
  }
  return { country: fallback, national: '' };
}

/** Formate le numero national selon le pays (groupage lisible pendant la frappe). */
function formatNational(country: Country, national: string): string {
  const digits = national.replace(/\D/g, '');
  if (!digits) return '';
  try {
    const formatter = new AsYouType(country.iso2.toUpperCase() as CountryCode);
    formatter.input(`+${country.dialCode}${digits.replace(/^0+/, '')}`);
    const nat = formatter.getNumber()?.formatNational();
    if (nat) return nat;
  } catch {
    /* fallback ci-dessous */
  }
  return digits;
}

/**
 * PhoneNumberInput — 2026-07-28.
 *
 * Champ telephone en deux parties : (a) un selecteur de pays (bouton drapeau +
 * indicatif, ex. « 🇲🇦 +212 ») ouvrant une liste deroulante recherchable de tous
 * les pays ; (b) un input pour le numero national. La valeur emise via
 * {@link Props.onChange} est TOUJOURS au format E.164 (`+212612345678`).
 *
 * Charte : pas de bulle de validation navigateur (validation JS in-app dans le
 * formulaire parent). Drapeaux SVG via flag-icons (compat Windows).
 */
export function PhoneNumberInput({
  value,
  onChange,
  defaultCountry = 'MA',
  label,
  error,
  hint,
  required,
  id,
  name,
  disabled,
  placeholder,
  'data-testid': dataTestId,
}: Props) {
  const fallback = countryByIso2(defaultCountry) ?? COUNTRIES.find((c) => c.iso2 === 'ma')!;

  // Etat interne : pays selectionne + numero national (chiffres, prefixe 0
  // tolere). L'E.164 est recompose et remonte a chaque frappe.
  const initial = useMemo(() => decompose(value, fallback), []); // eslint-disable-line react-hooks/exhaustive-deps
  const [country, setCountry] = useState<Country>(initial.country);
  const [national, setNational] = useState<string>(initial.national);

  // Re-synchronise si le parent pousse une valeur externe (reprise de draft,
  // reset) differente de ce qu'on a compose — sans casser la frappe courante.
  useEffect(() => {
    if (value !== composeE164(country, national)) {
      const next = decompose(value, country);
      setCountry(next.country);
      setNational(next.national);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [highlight, setHighlight] = useState(0);

  const rootRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const reactId = useId();
  const inputId = id ?? name ?? reactId;
  const listboxId = `${reactId}-listbox`;

  const filtered = useMemo(() => {
    const q = normalize(search);
    if (!q) return sortedCountries;
    return sortedCountries.filter(
      (c) => normalize(c.name).includes(q) || c.dialCode.includes(q.replace(/\D/g, '')),
    );
  }, [search]);

  // Ferme au clic exterieur.
  useEffect(() => {
    if (!open) return;
    function onDown(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  // Focus la recherche a l'ouverture + reset highlight.
  useEffect(() => {
    if (open) {
      setSearch('');
      setHighlight(0);
      // microtask : le champ est monte apres le render d'ouverture
      queueMicrotask(() => searchRef.current?.focus());
    }
  }, [open]);

  // Garde l'option surlignee visible.
  useLayoutEffect(() => {
    if (!open) return;
    const el = listRef.current?.querySelector<HTMLElement>(`[data-idx="${highlight}"]`);
    // scrollIntoView est absent sous jsdom (tests) — garde defensive.
    el?.scrollIntoView?.({ block: 'nearest' });
  }, [highlight, open]);

  function selectCountry(next: Country) {
    setCountry(next);
    setOpen(false);
    onChange(composeE164(next, national));
    // rend la main a l'input numero pour enchainer la saisie
    queueMicrotask(() => {
      rootRef.current?.querySelector<HTMLInputElement>('input[type="tel"]')?.focus();
    });
  }

  function onNationalChange(raw: string) {
    // On conserve les chiffres (le 0 de tete est tolere a l'affichage, retire
    // pour l'E.164). Recompose et remonte immediatement.
    const digits = raw.replace(/[^\d]/g, '');
    setNational(digits);
    onChange(composeE164(country, digits));
  }

  function onListKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlight((h) => Math.min(h + 1, filtered.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const picked = filtered[highlight];
      if (picked) selectCountry(picked);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      setOpen(false);
    }
  }

  return (
    <div className="flex flex-col gap-1" ref={rootRef}>
      {label && (
        <label htmlFor={inputId} className="mb-1 flex items-center gap-1.5 text-xs font-semibold text-fg">
          {label}
          {required && <span className="text-danger">*</span>}
        </label>
      )}

      <div
        className={twMerge(
          'flex items-stretch rounded-lg border border-border-hi bg-bg-raised transition-colors',
          'focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/30',
          error && 'border-danger focus-within:border-danger focus-within:ring-danger/30',
          disabled && 'opacity-60',
        )}
      >
        {/* Selecteur de pays */}
        <div className="relative">
          <button
            type="button"
            disabled={disabled}
            onClick={() => setOpen((o) => !o)}
            aria-haspopup="listbox"
            aria-expanded={open}
            aria-label={`Indicatif pays : ${country.name} +${country.dialCode}`}
            data-testid={dataTestId ? `${dataTestId}-country` : undefined}
            className="flex h-full items-center gap-1.5 rounded-l-lg border-r border-border-hi px-2.5 py-2 text-sm text-fg hover:bg-bg-overlay focus:outline-none focus-visible:bg-bg-overlay"
          >
            <span className={`fi fi-${country.iso2} rounded-[2px]`} aria-hidden="true" />
            <span className="font-medium tabular-nums">+{country.dialCode}</span>
            <ChevronDown className="h-3.5 w-3.5 text-fg-subtle" aria-hidden="true" />
          </button>

          {open && (
            <div
              className="absolute left-0 top-full z-50 mt-1 w-72 max-w-[calc(100vw-2rem)] overflow-hidden rounded-lg border border-border-hi bg-bg-raised shadow-lg"
              onKeyDown={onListKeyDown}
            >
              <div className="flex items-center gap-2 border-b border-border px-2.5 py-2">
                <Search className="h-3.5 w-3.5 flex-shrink-0 text-fg-subtle" aria-hidden="true" />
                <input
                  ref={searchRef}
                  type="text"
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setHighlight(0);
                  }}
                  placeholder="Rechercher un pays ou un indicatif"
                  aria-label="Rechercher un pays"
                  aria-controls={listboxId}
                  autoComplete="off"
                  className="w-full bg-transparent text-sm text-fg placeholder:text-fg-subtle focus:outline-none"
                />
              </div>
              <ul
                ref={listRef}
                id={listboxId}
                role="listbox"
                aria-label="Liste des pays"
                className="max-h-64 overflow-y-auto py-1"
              >
                {filtered.length === 0 && (
                  <li className="px-3 py-2 text-xs text-fg-subtle">Aucun pays trouve.</li>
                )}
                {filtered.map((c, idx) => {
                  const selected = c.iso2 === country.iso2;
                  return (
                    <li key={c.iso2} data-idx={idx} role="option" aria-selected={selected}>
                      <button
                        type="button"
                        onClick={() => selectCountry(c)}
                        onMouseEnter={() => setHighlight(idx)}
                        className={twMerge(
                          'flex w-full items-center gap-2.5 px-3 py-1.5 text-left text-sm text-fg',
                          idx === highlight && 'bg-bg-overlay',
                          selected && 'font-semibold',
                        )}
                      >
                        <span className={`fi fi-${c.iso2} flex-shrink-0 rounded-[2px]`} aria-hidden="true" />
                        <span className="flex-1 truncate">{c.name}</span>
                        <span className="tabular-nums text-fg-subtle">+{c.dialCode}</span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </div>

        {/* Numero national */}
        <input
          id={inputId}
          name={name}
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          disabled={disabled}
          value={formatNational(country, national)}
          onChange={(e) => onNationalChange(e.target.value)}
          placeholder={placeholder ?? '6 12 34 56 78'}
          aria-invalid={error ? true : undefined}
          data-testid={dataTestId}
          className="w-full flex-1 rounded-r-lg bg-transparent px-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none"
        />
      </div>

      {hint && !error && <p className="mt-0.5 text-[11px] leading-snug text-fg-subtle">{hint}</p>}
      {error && (
        <p className="mt-0.5 text-xs text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export default PhoneNumberInput;
