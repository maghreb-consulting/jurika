import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { TexteAide } from '../../components/ui/Aide';
import { Drawer } from '../../components/ui/Drawer';
import { Button } from '../../components/ui/Button';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { Message } from '../../components/ui/Message';
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
  // Lot L7 (E5), RG-PLN-03 : quota de dossiers du mois atteint (402) : proposer le changement de forfait.
  const [quotaAtteint, setQuotaAtteint] = useState(false);

  // Type effectif envoye au backend : le vrai sous-type si "Divers", sinon
  // la categorie elle-meme (qui est deja un TicketType direct).
  const type: TicketType = categorie === 'DIVERS' ? sousType : categorie;
  // Lot L6b (E7) : a l'ouverture, le focus va au premier champ (le titre), pas au bouton
  // de fermeture. L'effet du parent s'execute apres l'ouverture du <dialog> (enfant).
  const titreRef = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    if (open) titreRef.current?.focus();
  }, [open]);
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
      setError('Veuillez choisir un type d’opération.');
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
      const e = extractError(err);
      setQuotaAtteint(e.code.startsWith('PLAN_LIMIT'));
      setError(e.message);
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
      subtitle="Ouvrez le ticket d’une opération juridique : le parcours correspondant démarre aussitôt."
      width="md"
      closeOnBackdrop={false}
      footer={
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" form="formulaire-nouveau-ticket" loading={loading} disabled={loading}>
            Créer et ouvrir le parcours
          </Button>
        </div>
      }
    >
      <form id="formulaire-nouveau-ticket" onSubmit={handleSubmit} noValidate className="space-y-4">
        <TexteAide cle="nouveau-ticket" titre="Ouvrir un ticket">
          <p>
            Un ticket suit une opération du début à la fin : création du ticket, génération des
            documents, déroulement de la démarche, clôture. Donnez-lui un titre parlant et choisissez
            le type d’opération. Pour une création ou un import, la société et sa Data Room sont créées
            en même temps. Vous arrivez ensuite sur le parcours, où vous saisissez les données.
          </p>
        </TexteAide>
        <TextField
          ref={titreRef}
          label="Titre"
          name="titre"
          value={titre}
          onChange={(e) => setTitre(e.target.value)}
          placeholder="Ex : Constitution Atlas Trading"
          required
        />
        <Select
          label="Type d’opération"
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
          <fieldset className="space-y-3 rounded-lg border border-border bg-bg-overlay p-3">
            <legend className="px-1 text-sm font-semibold text-heading">
              Société à {type === 'CREATION' ? 'créer' : 'importer'}
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
            <p className="text-xs text-fg-muted">
              Le dossier de la société et sa Data Room seront créés automatiquement.
            </p>
          </fieldset>
        )}

        <Select
          label="Priorité"
          value={priorite}
          onChange={(e) => setPriorite(e.target.value as TicketPriorite)}
          options={PRIORITY_OPTIONS}
        />
        <TextField
          label="Échéance (facultatif)"
          type="date"
          value={deadline}
          onChange={(e) => setDeadline(e.target.value)}
        />
        <ZoneTexte
          label="Description (facultatif)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
        />
        {error && (
          <Message
            ton="erreur"
            action={
              quotaAtteint ? (
                <Link to="/app/billing/change-plan" className="font-medium underline">
                  Changer de forfait
                </Link>
              ) : undefined
            }
          >
            {error}
          </Message>
        )}
      </form>
    </Drawer>
  );
}
