import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, Save, X } from 'lucide-react';
import { Button } from '../ui/Button';
import { Message } from '../ui/Message';
import type { Ticket } from '../../types/ticket';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { Page } from '../layout/Page';
import { useAuthStore } from '../../store/authStore';
import { filDuParcours } from './etatsEtapes';
import { WorkflowRoadmap, type RoadmapStep } from './WorkflowRoadmap';
import { IndicateurEnregistrement } from './IndicateurEnregistrement';
import { EtatTicketParcours, MessageConsultationSuperviseur, MetaTicketParcours } from './EnTeteParcours';

interface FooterButtons {
  showPrev?: boolean;
  showSaveDraft?: boolean;
  showValidate?: boolean;
  validateLabel?: string;
  prevDisabled?: boolean;
  validateDisabled?: boolean;
}

interface Props {
  ticket: Ticket;
  steps: RoadmapStep[];
  currentStep: number;
  /** Etape affichee par l'UI (peut differer de currentStep si nav arriere). */
  viewStep?: number;
  /** Callback de navigation arriere via la frise. */
  onNavigate?: (step: number) => void;
  blocked?: boolean;
  blockedReason?: string;
  error?: string | null;
  saving: boolean;
  onPrev?: () => void;
  onSaveDraft?: () => void;
  onValidate?: () => void;
  onCancel?: () => void;
  footer?: FooterButtons;
  children: ReactNode;
}

/**
 * Coquille commune des parcours (lot L6b, E5) : en-tete de page standard (fil d'Ariane,
 * titre, etat, actions), aide, frise des etapes, contenu de l'etape, boutons de l'etape.
 *
 * Le superviseur suit le parcours sans le modifier (le serveur lui refuse l'enregistrement,
 * la validation des etapes, le depot des pieces et l'annulation) : ces boutons ne lui sont
 * pas montres et les champs sont en lecture seule.
 */
export function WorkflowShell({
  ticket,
  steps,
  currentStep,
  viewStep,
  onNavigate,
  blocked,
  blockedReason,
  error,
  saving,
  onPrev,
  onSaveDraft,
  onValidate,
  onCancel,
  footer,
  children,
}: Props) {
  const navigate = useNavigate();
  const superviseur = useAuthStore((s) => s.user?.role === 'SUPERVISEUR');
  const f: FooterButtons = {
    showPrev: true,
    showSaveDraft: true,
    showValidate: true,
    validateLabel: 'Valider et continuer',
    ...footer,
  };
  const brouillon = !superviseur && f.showSaveDraft && onSaveDraft;
  const valider = !superviseur && f.showValidate && onValidate;

  return (
    <Page
      titre={ticket.titre}
      fil={filDuParcours(ticket)}
      etat={<EtatTicketParcours ticket={ticket} />}
      description={<MetaTicketParcours ticket={ticket} />}
      actions={
        <>
          {!superviseur && <IndicateurEnregistrement />}
          <Button type="button" variant="secondary" icon={<ChevronLeft />} onClick={() => navigate(-1)}>
            Quitter
          </Button>
          {!superviseur && (
            <InfoBulle
              libelle="Que conserve « Quitter » ?"
              texte="Rien n’est perdu : les étapes validées sont conservées, et la saisie en cours de l’étape affichée est enregistrée automatiquement en brouillon au moment où vous quittez. Vous reprendrez le parcours là où vous l’avez laissé, depuis la page du ticket."
            />
          )}
          {onCancel && !superviseur && (
            <Button type="button" variant="danger" icon={<X />} onClick={onCancel}>
              Annuler le ticket
            </Button>
          )}
        </>
      }
      aide={
        superviseur ? undefined : (
          <TexteAide cle="parcours-etapes" titre="Comment avancer dans le parcours">
            Remplissez l’étape affichée, puis cliquez sur « {f.validateLabel} » : l’étape suivante
            s’ouvre une fois celle-ci validée. Ce qui manque est signalé à côté du bouton de
            validation. Vous pouvez revenir à une étape déjà validée en cliquant sur son numéro dans
            la frise.{' '}Votre saisie est enregistrée automatiquement en brouillon (l’heure du dernier
            enregistrement s’affiche en haut de la page) : changer d’étape, quitter ou fermer la page
            ne perd rien.
          </TexteAide>
        )
      }
    >
      <div className="space-y-5">
        {superviseur && <MessageConsultationSuperviseur />}

        <WorkflowRoadmap currentStep={currentStep} steps={steps} blocked={blocked} viewStep={viewStep} onNavigate={onNavigate} />

        {blocked && (
          <Message ton="erreur" titre="Étape bloquante">
            {blockedReason}
          </Message>
        )}

        {error && <Message ton="erreur">{error}</Message>}

        <section aria-label="Contenu de l’étape" className="rounded-xl border border-border bg-bg-raised p-6 shadow-card">
          {superviseur ? (
            <fieldset disabled className="m-0 min-w-0 border-0 p-0">
              {children}
            </fieldset>
          ) : (
            children
          )}
        </section>

        <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-4">
          <div>
            {f.showPrev && onPrev && (
              <Button type="button" variant="secondary" icon={<ChevronLeft />} onClick={onPrev} disabled={f.prevDisabled || saving}>
                Étape précédente
              </Button>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {brouillon && (
              <Button type="button" variant="ghost" icon={<Save />} onClick={onSaveDraft} disabled={saving}>
                Sauvegarder le brouillon
              </Button>
            )}
            {valider && (
              <Button type="button" onClick={onValidate} loading={saving} disabled={f.validateDisabled || saving}>
                {saving ? 'Validation en cours…' : f.validateLabel}
              </Button>
            )}
          </div>
        </footer>
      </div>
    </Page>
  );
}
