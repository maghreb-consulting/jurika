import { useMemo, useState } from 'react';
import {
  Building2,
  ChevronRight,
  Plus,
  Sparkles,
  Trash2,
  UserPlus,
  Users,
} from 'lucide-react';
import type { FormeJuridique } from '../../../types/ticket';

interface Props {
  existing?: Record<string, unknown>;
  /**
   * Suggestions remontees par Step2 (champs extraits par /ai/extract sur
   * les documents juridiques importes). Cle = champ de ce form
   * (raisonSociale, ice, rcNumero, ifNumero, capital, formeJuridique,
   * siegeAdresse, siegeVille). Non destructif : applique uniquement sur
   * action explicite de l'utilisateur ("Appliquer" ou "Tout appliquer").
   */
  suggestedFields?: Record<string, string>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
}

interface Gerant {
  id: string;
  nom: string;
  prenom: string;
  cin: string;
  email?: string;
  tel?: string;
}

/**
 * 2026-06-24 — Associe importe. Modele volontairement aligne sur la Creation
 * (Step6Associes) : {nom, prenom, nombreParts, pourcentageDetention}. La societe
 * etant DEJA existante, la rigueur Creation (capital == Sigma(apports)) n'est
 * pas imposee : la repartition est reconstituee a titre indicatif et sert a une
 * future Modification.
 */
interface Associe {
  id: string;
  nom: string;
  prenom: string;
  nombreParts: number;
  pourcentageDetention: number;
}

interface Form extends Record<string, unknown> {
  raisonSociale: string;
  ice: string;
  rcNumero: string;
  ifNumero: string;
  formeJuridique: FormeJuridique;
  capital: number;
  capitalLibere: number;
  siegeAdresse: string;
  siegeVille: string;
  gerants: Gerant[];
  associes: Associe[];
}

function newGerant(): Gerant {
  return {
    id:
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `g-${Date.now()}-${Math.random()}`,
    nom: '',
    prenom: '',
    cin: '',
    email: '',
    tel: '',
  };
}

function newAssocie(): Associe {
  return {
    id:
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `a-${Date.now()}-${Math.random()}`,
    nom: '',
    prenom: '',
    nombreParts: 0,
    pourcentageDetention: 0,
  };
}

function formatIce(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 15);
  return digits.replace(/(.{3})(?=.)/g, '$1 ').trim();
}

const ICE_PATTERN = /^[0-9 ]{15,19}$/;

/**
 * IMPORT workflow -- Etape 1 : saisie des informations cles de la societe
 * deja constituee. Les justificatifs (RC/IF/CNIE/Statuts) sont uploades a
 * l etape 2.
 *
 * Fix 2026-06-06 :
 *  - Lecture corrigee : le backend ecrit {@code step1.info.X}, plus
 *    {@code step1.infoSociete.X}.
 *  - Champs siege ajoutes (siegeAdresse, siegeVille) -- alimentent
 *    {@code applyImportConsolidation} cote workflow-service.
 *  - Panneau de suggestions LLM integre : si Step2 a extrait des champs
 *    (raisonSociale, ICE, RC, IF, capital, siege...), un bandeau jaune
 *    propose de les appliquer aux champs vides.
 */
export function Step1InfoSociete({ existing, suggestedFields, saving, onSubmit }: Props) {
  // Fix 2026-06-06 : backend ecrit {@code step1.info.X} (cf
  // WorkflowUseCases.executeStep + ImportWorkflow.handleInfos). L'ancien
  // code lisait {@code existing.infoSociete} -> reouverture du wizard
  // affichait un form vide.
  const e = (existing?.info as Partial<Form> | undefined) ?? {};
  const [form, setForm] = useState<Form>({
    raisonSociale: (e.raisonSociale as string) ?? '',
    ice: (e.ice as string) ?? '',
    rcNumero: (e.rcNumero as string) ?? '',
    ifNumero: (e.ifNumero as string) ?? '',
    formeJuridique: ((e.formeJuridique as FormeJuridique) ?? 'SARL') === 'SARL_AU' ? 'SARL_AU' : 'SARL',
    capital: Number(e.capital ?? 100000),
    capitalLibere: Number(e.capitalLibere ?? 100000),
    siegeAdresse: (e.siegeAdresse as string) ?? '',
    siegeVille: (e.siegeVille as string) ?? '',
    gerants:
      Array.isArray(e.gerants) && e.gerants.length > 0
        ? (e.gerants as Gerant[]).map((g) => ({ ...newGerant(), ...g }))
        : [newGerant()],
    associes:
      Array.isArray(e.associes) && e.associes.length > 0
        ? (e.associes as Associe[]).map((a) => ({ ...newAssocie(), ...a }))
        : [newAssocie()],
  });

  const liberePercent = form.capital > 0 ? (form.capitalLibere / form.capital) * 100 : 0;
  const iceDigits = form.ice.replace(/\D/g, '');
  const iceValid = iceDigits.length === 15;
  const liberePctOk = form.capital > 0 && form.capitalLibere >= form.capital * 0.25;

  // 2026-06-24 — Sommes indicatives des associes (validation legere, non bloquante
  // au-dela du "au moins 1 associe nomme").
  const totalParts = useMemo(
    () => form.associes.reduce((s, a) => s + (Number(a.nombreParts) || 0), 0),
    [form.associes],
  );
  const totalPct = useMemo(
    () => form.associes.reduce((s, a) => s + (Number(a.pourcentageDetention) || 0), 0),
    [form.associes],
  );
  const anyPctRenseigne = totalPct > 0;
  const pctCoherent = !anyPctRenseigne || Math.abs(totalPct - 100) < 0.5;

  const canSubmit = useMemo(
    () =>
      !!form.raisonSociale.trim() &&
      iceValid &&
      !!form.rcNumero.trim() &&
      !!form.ifNumero.trim() &&
      form.capital > 0 &&
      liberePctOk &&
      form.gerants.length >= 1 &&
      form.gerants.every((g) => g.nom.trim() && g.prenom.trim() && g.cin.trim()) &&
      // Validation legere : au moins 1 associe nomme. La coherence des parts /
      // pourcentages n'est qu'indicative (societe deja existante).
      form.associes.length >= 1 &&
      form.associes.every((a) => a.nom.trim() && a.prenom.trim()),
    [form, iceValid, liberePctOk],
  );

  function updateAssocie(id: string, patch: Partial<Associe>) {
    setForm((p) => ({
      ...p,
      associes: p.associes.map((a) => (a.id === id ? { ...a, ...patch } : a)),
    }));
  }

  function addAssocie() {
    setForm((p) => ({ ...p, associes: [...p.associes, newAssocie()] }));
  }

  function removeAssocie(id: string) {
    setForm((p) =>
      p.associes.length > 1
        ? { ...p, associes: p.associes.filter((a) => a.id !== id) }
        : p,
    );
  }

  function updateGerant(id: string, patch: Partial<Gerant>) {
    setForm((p) => ({
      ...p,
      gerants: p.gerants.map((g) => (g.id === id ? { ...g, ...patch } : g)),
    }));
  }

  function addGerant() {
    setForm((p) => ({ ...p, gerants: [...p.gerants, newGerant()] }));
  }

  function removeGerant(id: string) {
    setForm((p) =>
      p.gerants.length > 1
        ? { ...p, gerants: p.gerants.filter((g) => g.id !== id) }
        : p,
    );
  }

  /** RG-IM05 : applique une suggestion LLM uniquement si le champ est vide. */
  function applySuggestion(field: string, value: string) {
    setForm((p) => {
      const current = (p as unknown as Record<string, unknown>)[field];
      if (typeof current === 'string' && current.trim()) return p;
      if (typeof current === 'number' && current > 0 && field !== 'capital') return p;
      if (field === 'ice') {
        const digits = value.replace(/\D/g, '').slice(0, 15);
        return { ...p, ice: digits.replace(/(.{3})(?=.)/g, '$1 ').trim() };
      }
      if (field === 'capital') {
        const num = Number(String(value).replace(/[^\d.,]/g, '').replace(',', '.'));
        if (!isFinite(num) || num <= 0) return p;
        return { ...p, capital: Math.round(num), capitalLibere: Math.max(p.capitalLibere, Math.round(num)) };
      }
      if (field === 'formeJuridique') {
        const norm = String(value).toUpperCase().replace(/[\s\W]/g, '_');
        if (norm.includes('AU')) return { ...p, formeJuridique: 'SARL_AU' };
        if (norm.includes('SARL')) return { ...p, formeJuridique: 'SARL' };
        return p;
      }
      return { ...p, [field]: String(value) } as Form;
    });
  }

  function applyAllEmpty() {
    if (!suggestedFields) return;
    Object.entries(suggestedFields).forEach(([k, v]) => applySuggestion(k, v));
  }

  const hasSuggestions = !!suggestedFields && Object.keys(suggestedFields).length > 0;

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        // Validation JS in-app avant tout appel : la saisie est deja gatee par
        // `canSubmit` (bouton desactive), on garde un garde-fou explicite depuis
        // que `noValidate` neutralise les bulles natives du navigateur.
        if (!canSubmit) return;
        // RG-IM02 : payload ICE normalise (digits only). Cle "info" -> niche
        // cote backend sous step1.info (cf ImportWorkflow.handleInfos).
        void onSubmit({
          ...form,
          ice: iceDigits,
          info: { ...form, ice: iceDigits },
        });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      {hasSuggestions && (
        <div className="rounded-xl border-2 border-indigo-300 bg-indigo-50 p-4 text-xs">
          <div className="mb-2 flex items-center justify-between">
            <p className="flex items-center gap-2 font-semibold text-indigo-700">
              <Sparkles className="h-4 w-4" />
              Suggestions extraites des documents (etape 2)
            </p>
            <button
              type="button"
              onClick={applyAllEmpty}
              className="rounded-full bg-indigo-600 px-3 py-1 text-[11px] font-semibold text-white hover:bg-indigo-700"
            >
              Appliquer aux champs vides
            </button>
          </div>
          <ul className="grid grid-cols-1 gap-1 text-indigo-900 md:grid-cols-2">
            {Object.entries(suggestedFields!).map(([k, v]) => (
              <li key={k} className="flex items-center justify-between gap-2">
                <span>
                  <strong>{k}</strong> : {v}
                </span>
                <button
                  type="button"
                  onClick={() => applySuggestion(k, v)}
                  className="rounded border border-indigo-300 bg-white px-2 py-0.5 text-[10px] font-medium text-indigo-700 hover:bg-indigo-100"
                >
                  Appliquer
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-4 flex items-center gap-2">
          <Building2 className="h-5 w-5 text-accent" />
          <h3 className="text-lg font-bold text-fg">Identite de la societe</h3>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="md:col-span-2">
            <label className="mb-1 block text-xs font-medium text-fg">
              Raison sociale
            </label>
            <input aria-label="Raison sociale"
              type="text"
              value={form.raisonSociale}
              onChange={(ev) => setForm((p) => ({ ...p, raisonSociale: ev.target.value }))}
              placeholder="Ex : ATLAS SARL"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-fg">ICE</label>
            <input aria-label="ICE"
              type="text"
              value={form.ice}
              onChange={(ev) => setForm((p) => ({ ...p, ice: formatIce(ev.target.value) }))}
              placeholder="000 000 000 000 000"
              inputMode="numeric"
              pattern={ICE_PATTERN.source}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
            <p className="mt-1 text-[11px] text-fg-subtle">
              15 chiffres. Les espaces et tirets sont ignores.
            </p>
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              N° RC (Registre de Commerce)
            </label>
            <input aria-label="N° RC (Registre de Commerce)"
              type="text"
              value={form.rcNumero}
              onChange={(ev) => setForm((p) => ({ ...p, rcNumero: ev.target.value }))}
              placeholder="Ex : 123456"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              N° IF (Identifiant Fiscal)
            </label>
            <input aria-label="N° IF (Identifiant Fiscal)"
              type="text"
              value={form.ifNumero}
              onChange={(ev) => setForm((p) => ({ ...p, ifNumero: ev.target.value }))}
              placeholder="Ex : 12345678"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Forme juridique
            </label>
            <div className="flex gap-3">
              <label className="flex flex-1 cursor-pointer items-center gap-2 rounded-lg border-2 border-border bg-bg-overlay px-3 py-2 text-sm hover:border-accent">
                <input
                  type="radio"
                  name="forme"
                  value="SARL"
                  checked={form.formeJuridique === 'SARL'}
                  onChange={() => setForm((p) => ({ ...p, formeJuridique: 'SARL' }))}
                />
                <span className="font-medium text-fg">SARL</span>
              </label>
              <label className="flex flex-1 cursor-pointer items-center gap-2 rounded-lg border-2 border-border bg-bg-overlay px-3 py-2 text-sm hover:border-accent">
                <input
                  type="radio"
                  name="forme"
                  value="SARL_AU"
                  checked={form.formeJuridique === 'SARL_AU'}
                  onChange={() => setForm((p) => ({ ...p, formeJuridique: 'SARL_AU' }))}
                />
                <span className="font-medium text-fg">SARL AU</span>
              </label>
            </div>
          </div>

          <div className="md:col-span-2">
            <label className="mb-1 block text-xs font-medium text-fg">
              Adresse du siege
            </label>
            <input aria-label="Adresse du siege"
              type="text"
              value={form.siegeAdresse}
              onChange={(ev) => setForm((p) => ({ ...p, siegeAdresse: ev.target.value }))}
              placeholder="Ex : 12 rue des Cedres, Maarif"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>

          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Ville
            </label>
            <input aria-label="Ville"
              type="text"
              value={form.siegeVille}
              onChange={(ev) => setForm((p) => ({ ...p, siegeVille: ev.target.value }))}
              placeholder="Ex : Casablanca"
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-4 text-base font-bold text-fg">Capital social</h3>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Capital social (MAD)
            </label>
            <input aria-label="Capital social (MAD)"
              type="number"
              min={0}
              value={form.capital}
              onChange={(ev) => setForm((p) => ({ ...p, capital: Number(ev.target.value) }))}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
            <p className="mt-1 text-[11px] text-fg-subtle">
              Minimum legal : 25 % du capital social doit etre libere.
            </p>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Capital libere (MAD)
            </label>
            <input aria-label="Capital libere (MAD)"
              type="number"
              min={0}
              value={form.capitalLibere}
              onChange={(ev) =>
                setForm((p) => ({ ...p, capitalLibere: Number(ev.target.value) }))
              }
              className={`h-10 w-full rounded-lg border-2 px-3 text-sm focus:outline-none ${
                liberePctOk
                  ? 'border-border bg-bg-overlay focus:border-accent'
                  : 'border-danger bg-danger/10 focus:border-danger'
              }`}
            />
            <p
              className={`mt-1 text-[11px] ${
                liberePctOk ? 'text-success' : 'text-danger'
              }`}
            >
              Minimum legal (25 %) : {Math.ceil(form.capital * 0.25).toLocaleString('fr-MA')} MAD.
              Votre saisie : {form.capitalLibere.toLocaleString('fr-MA')} MAD{' '}
              ({liberePercent.toFixed(0)} %) {liberePctOk ? '✓' : '✗'}
            </p>
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-bold text-fg">Dirigeants / Gerants</h3>
          <button
            type="button"
            onClick={addGerant}
            className="inline-flex items-center gap-1 rounded-lg border-2 border-dashed border-border px-3 py-1 text-xs text-accent hover:border-accent hover:bg-accent/10"
          >
            <UserPlus className="h-4 w-4" /> Ajouter
          </button>
        </div>

        <div className="space-y-3">
          {form.gerants.map((g, i) => (
            <div
              key={g.id}
              className="rounded-lg border border-border bg-bg-overlay p-4"
            >
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-semibold text-fg">
                  Gerant {i + 1}
                </span>
                {form.gerants.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeGerant(g.id)}
                    className="rounded p-1 text-danger hover:bg-danger/10"
                    aria-label="Retirer ce gerant"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                <input
                  type="text"
                  value={g.nom}
                  onChange={(ev) => updateGerant(g.id, { nom: ev.target.value })}
                  placeholder="Nom"
                  aria-label="Nom du gerant"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                />
                <input
                  type="text"
                  value={g.prenom}
                  onChange={(ev) => updateGerant(g.id, { prenom: ev.target.value })}
                  placeholder="Prenom"
                  aria-label="Prenom du gerant"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                />
                <input
                  type="text"
                  value={g.cin}
                  onChange={(ev) =>
                    updateGerant(g.id, { cin: ev.target.value.toUpperCase() })
                  }
                  placeholder="CIN (ex : X 123456)"
                  aria-label="N° CIN du gerant"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm uppercase"
                />
                <input
                  type="email"
                  value={g.email ?? ''}
                  onChange={(ev) => updateGerant(g.id, { email: ev.target.value })}
                  placeholder="Email (optionnel)"
                  aria-label="Email du gerant (optionnel)"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                />
                <input
                  type="tel"
                  value={g.tel ?? ''}
                  onChange={(ev) => updateGerant(g.id, { tel: ev.target.value })}
                  placeholder="Telephone (optionnel)"
                  aria-label="Telephone du gerant (optionnel)"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm md:col-span-2"
                />
              </div>
            </div>
          ))}
        </div>
        {form.gerants.length === 0 && (
          <button
            type="button"
            onClick={addGerant}
            className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border py-3 text-sm text-accent hover:border-accent hover:bg-accent/10"
          >
            <Plus className="h-4 w-4" /> Ajouter un gerant
          </button>
        )}
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-1 flex items-center justify-between">
          <h3 className="flex items-center gap-2 text-base font-bold text-fg">
            <Users className="h-4 w-4 text-accent" /> Associes
          </h3>
          <button
            type="button"
            onClick={addAssocie}
            className="inline-flex items-center gap-1 rounded-lg border-2 border-dashed border-border px-3 py-1 text-xs text-accent hover:border-accent hover:bg-accent/10"
          >
            <UserPlus className="h-4 w-4" /> Ajouter
          </button>
        </div>
        <p className="mb-4 text-[11px] text-fg-subtle">
          Repartition du capital de la societe importee — sert a une future
          modification. Au moins un associe est requis ; la coherence des parts
          est indicative.
        </p>

        <div className="space-y-3">
          {form.associes.map((a, i) => (
            <div key={a.id} className="rounded-lg border border-border bg-bg-overlay p-4">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-semibold text-fg">Associe {i + 1}</span>
                {form.associes.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeAssocie(a.id)}
                    className="rounded p-1 text-danger hover:bg-danger/10"
                    aria-label="Retirer cet associe"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
                <input
                  type="text"
                  value={a.nom}
                  onChange={(ev) => updateAssocie(a.id, { nom: ev.target.value })}
                  placeholder="Nom"
                  aria-label="Nom de l'associe"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                />
                <input
                  type="text"
                  value={a.prenom}
                  onChange={(ev) => updateAssocie(a.id, { prenom: ev.target.value })}
                  placeholder="Prenom"
                  aria-label="Prenom de l'associe"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                />
                <div>
                  <input
                    type="number"
                    min={0}
                    value={a.nombreParts || ''}
                    onChange={(ev) =>
                      updateAssocie(a.id, {
                        nombreParts: Math.max(0, Math.round(Number(ev.target.value) || 0)),
                      })
                    }
                    placeholder="Nombre de parts"
                    aria-label="Nombre de parts de l'associe"
                    className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                  />
                </div>
                <div className="relative">
                  <input
                    type="number"
                    min={0}
                    max={100}
                    step="0.01"
                    value={a.pourcentageDetention || ''}
                    onChange={(ev) =>
                      updateAssocie(a.id, {
                        pourcentageDetention: Math.max(0, Number(ev.target.value) || 0),
                      })
                    }
                    placeholder="% detention"
                    aria-label="Pourcentage de detention de l'associe"
                    className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 pr-7 text-sm"
                  />
                  <span className="pointer-events-none absolute right-3 top-2 text-xs text-fg-subtle">
                    %
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Recap indicatif (non bloquant). */}
        <div className="mt-3 flex flex-wrap items-center gap-3 text-[11px]">
          <span className="rounded-full bg-bg-overlay px-2 py-0.5 text-fg-muted">
            Total parts : <strong className="text-fg">{totalParts.toLocaleString('fr-MA')}</strong>
          </span>
          <span
            className={`rounded-full px-2 py-0.5 ${
              pctCoherent
                ? 'bg-success/10 text-success'
                : 'bg-warning/10 text-warning'
            }`}
          >
            Total detention : {totalPct.toFixed(2)} %{' '}
            {anyPctRenseigne ? (pctCoherent ? '✓' : '(≈ 100 % attendu)') : ''}
          </span>
        </div>
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
