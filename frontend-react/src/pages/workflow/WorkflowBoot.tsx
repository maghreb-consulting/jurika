/**
 * Point d'entrée commun des pages de workflow (2026-08-14).
 *
 * Toute page d'étape s'enveloppe ici :
 *
 * ```tsx
 * export function LiquidationWorkflowPage() {
 *   return <WorkflowBoot><LiquidationWorkflowPageBody /></WorkflowBoot>;
 * }
 * ```
 *
 * **Pourquoi ce détour.** Les pages initialisent leurs champs avec
 * `useState(stepData.stepN?.x ?? '')`. Or `useState` ne lit sa valeur initiale
 * qu'au PREMIER render — à cet instant la requête n'a pas répondu, `stepData`
 * vaut `{}`, et tous les champs se figent à vide. React ne les réinitialise
 * jamais ensuite. Le `if (loading) return <Loader/>` des pages n'y change rien :
 * il s'exécute APRÈS les hooks.
 *
 * Le symptôme : on valide une étape, on se déconnecte, on revient — l'étape est
 * marquée validée mais tous ses champs sont vides. Invisible tant qu'on enchaîne
 * les étapes sans recharger, puisque les états sont alors déjà peuplés.
 *
 * `WorkflowBoot` ne monte le corps de la page qu'une fois les données chargées :
 * les initialiseurs `useState` voient donc les valeurs persistées. Le corps de
 * chaque page continue d'appeler `useWorkflow()` sans changer une ligne.
 */
import type { ReactNode } from 'react';
import { WorkflowDataProvider } from './useWorkflow';

function WorkflowLoader() {
  return (
    <div className="flex h-64 items-center justify-center" data-testid="workflow-boot-loader">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
    </div>
  );
}

function WorkflowLoadError({ message }: { message: string }) {
  return (
    <div
      className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger"
      role="alert"
      data-testid="workflow-boot-error"
    >
      {message}
    </div>
  );
}

export function WorkflowBoot({ children }: { children: ReactNode }) {
  return (
    <WorkflowDataProvider
      fallback={<WorkflowLoader />}
      renderError={(message) => <WorkflowLoadError message={message} />}
    >
      {children}
    </WorkflowDataProvider>
  );
}
