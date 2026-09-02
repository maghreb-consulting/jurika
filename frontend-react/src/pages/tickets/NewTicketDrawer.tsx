import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Drawer } from '../../components/ui/Drawer';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { ticketService } from '../../services/ticket.service';
import { extractError } from '../../lib/api';
import { requiredMsg } from '../../lib/formValidation';
import {
  FORME_JURIDIQUE_LABELS,
  FORMES_JURIDIQUES_SUPPORTED,
  TICKET_TYPE_LABELS,
  TYPES_AUTO_DOSSIER,
} from '../../types/ticket';
import type { FormeJuridique, TicketPriorite, TicketType } from '../../types/ticket';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: () => Promise<void>;
}

/**
 * Categories de 1er niveau presentees a l'utilisateur. Ce sont soit des
 * TicketType directs, soit le REGROUPEMENT UI "DIVERS" (qui n'est PAS un
 * TicketType : le back ne connait que les vrais sous-types).
 */
type TicketCategorie =
  | 'CREATION'
  | 'IMPORT'
  | 'MODIFICATION'
  | 'DISSOLUTION'
  | 'LIQUIDATION'
  | 'DIVERS';

/**
 * Sous-types regroupes sous "Divers". Tableau EXTENSIBLE : ajouter ici
 * d'autres types (ex. futur CONTRAT_BAIL) une fois le workflow correspondant
 * cree. Aucune modif backend / enum requise, ce n'est qu'un regroupement UI.
 */
const DIVERS_SOUS_TYPES: TicketType[] = [
  'SUCCURSALE_MA',
  'SUCCURSALE_ETR',
  'FERMETURE_SUCCURSALE',
  'PV_AGO',
];

const CATEGORIE_OPTIONS: { value: TicketCategorie; label: string }[] = [
  { value: 'CREATION', label: TICKET_TYPE_LABELS.CREATION },
  { value: 'IMPORT', label: TICKET_TYPE_LABELS.IMPORT },
  { value: 'MODIFICATION', label: TICKET_TYPE_LABELS.MODIFICATION },
  { value: 'DISSOLUTION', label: TICKET_TYPE_LABELS.DISSOLUTION },
  { value: 'LIQUIDATION', label: TICKET_TYPE_LABELS.LIQUIDATION },
  { value: 'DIVERS', label: 'Divers' },
];

const SOUS_TYPE_OPTIONS = DIVERS_SOUS_TYPES.map((value) => ({
  value,
  label: TICKET_TYPE_LABELS[value],
}));

const PRIORITY_OPTIONS: { value: TicketPriorite; label: string }[] = [
  { value: 'BASSE', label: 'Basse' },
  { value: 'NORMALE', label: 'Normale' },
  { value: 'HAUTE', label: 'Haute' },
  { value: 'URGENTE', label: 'Urgente' },
];
const FORME_OPTIONS = FORMES_JURIDIQUES_SUPPORTED.map((f) => ({
  value: f,
  label: FORME_JURIDIQUE_LABELS[f],
}));

export function NewTicketDrawer({ open, onClose, onCreated }: Props) {
  const navigate = useNavigate();
  const [titre, setTitre] = useState('');
  const [categorie, setCategorie] = useState<TicketCategorie>('CREATION');
  const [sousType, setSousType] = useState<TicketType>(DIVERS_SOUS_TYPES[0]);
  const [priorite, setPriorite] = useState<TicketPriorite>('NORMALE');
  const [raisonSociale, setRaisonSociale] = useState('');
  const [formeJuridique, setFormeJuridique] = useState<FormeJuridique>('SARL');
  const [description, setDescription] = useState('');
  const [deadline, setDeadline] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Type effectif envoye au backend : le vrai sous-type si "Divers", sinon
  // la categorie elle-meme (qui est deja un TicketType direct).
  const type: TicketType = categorie === 'DIVERS' ? sousType : categorie;
  const needsCompanyInfo = TYPES_AUTO_DOSSIER.includes(type);

  function focusField(name: string) {
    // Focus in-app du champ concerné (a11y), sans bulle native.
    document.querySelector<HTMLElement>(`[name="${name}"]`)?.focus();
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Fix 2026-06-07 (BUG 2 front) — Anti-double-submit. Sans cette
    // garde, un double-clic rapide OU un re-render React StrictMode
    // declenche 2 fois la creation -> 2 tickets -> 2 dossiers (le back
    // a son propre filet idempotence depuis BUG 2 mais on coupe le mal
    // a la racine cote UI pour eviter le 1er INSERT inutile et clarifier
    // l'UX (pas de double POST en flight).
    if (loading) return;
    // Validation JS in-app AVANT l'appel API (aucune bulle native).
    const titreErr = requiredMsg(titre, 'Le titre est requis.');
    if (titreErr) {
      setError(titreErr);
      focusField('titre');
      return;
    }
    // Regroupement UI "Divers" : un sous-type reel doit etre selectionne.
    if (categorie === 'DIVERS' && !sousType) {
      setError('Veuillez choisir un type d’operation.');
      return;
    }
    // Creation / Import : la raison sociale du dossier auto-cree est requise.
    if (needsCompanyInfo) {
      const rsErr = requiredMsg(raisonSociale, 'La raison sociale est requise.');
      if (rsErr) {
        setError(rsErr);
        focusField('raisonSociale');
        return;
      }
    }
    setError(null);
    setLoading(true);
    try {
      const created = await ticketService.create({
        titre,
        type,
        priorite,
        description: description || undefined,
        deadline: deadline ? new Date(deadline).toISOString() : undefined,
        companyInfo: needsCompanyInfo
          ? { raisonSociale: raisonSociale.trim(), formeJuridique }
          : undefined,
      });
      await onCreated();
      reset();
      navigate(`/workflows/${created.id}`);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  function reset() {
    setTitre('');
    setCategorie('CREATION');
    setSousType(DIVERS_SOUS_TYPES[0]);
    setPriorite('NORMALE');
    setRaisonSociale('');
    setFormeJuridique('SARL');
    setDescription('');
    setDeadline('');
    setError(null);
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Nouveau ticket"
      subtitle="Le ticket declenchera automatiquement le workflow correspondant"
      width="md"
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <TextField
          label="Titre"
          name="titre"
          value={titre}
          onChange={(e) => setTitre(e.target.value)}
          placeholder="Ex : Constitution Atlas Trading"
          required
        />
        <Select
          label="Type d'operation"
          value={categorie}
          onChange={(e) => setCategorie(e.target.value as TicketCategorie)}
          options={CATEGORIE_OPTIONS}
        />
        {categorie === 'DIVERS' && (
          <Select
            label="Type"
            value={sousType}
            onChange={(e) => setSousType(e.target.value as TicketType)}
            options={SOUS_TYPE_OPTIONS}
          />
        )}

        {needsCompanyInfo && (
          <fieldset className="space-y-3 rounded-lg border border-accent/40 bg-accent/10/40 p-3">
            <legend className="px-1 text-xs font-semibold uppercase tracking-wider text-accent">
              Societe a {type === 'CREATION' ? 'creer' : 'importer'}
            </legend>
            <TextField
              label="Raison sociale"
              name="raisonSociale"
              value={raisonSociale}
              onChange={(e) => setRaisonSociale(e.target.value)}
              placeholder="Ex : ATLAS TRADING SARL"
              required={needsCompanyInfo}
            />
            <Select
              label="Forme juridique"
              value={formeJuridique}
              onChange={(e) => setFormeJuridique(e.target.value as FormeJuridique)}
              options={FORME_OPTIONS}
            />
            <p className="text-[11px] text-accent/80">
              Un dossier d'entreprise et son Data Room seront crees automatiquement.
            </p>
          </fieldset>
        )}

        <Select
          label="Priorite"
          value={priorite}
          onChange={(e) => setPriorite(e.target.value as TicketPriorite)}
          options={PRIORITY_OPTIONS}
        />
        <TextField
          label="Echeance (optionnel)"
          type="date"
          value={deadline}
          onChange={(e) => setDeadline(e.target.value)}
        />
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-fg-muted">Description</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
          />
        </div>
        {error && (
          <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
            {error}
          </div>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={loading} disabled={loading}>
            Creer et lancer le workflow
          </Button>
        </div>
      </form>
    </Drawer>
  );
}
