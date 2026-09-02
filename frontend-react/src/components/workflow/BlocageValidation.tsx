/**
 * Motif de blocage d'une action — correction globale 2026-08-16, défaut A4.
 *
 * PRINCIPE TRANSVERSE : **on ne désactive jamais un bouton d'action sans dire
 * pourquoi.** En simulation, l'étape 5 de la création restait bloquée sans le
 * moindre message : « Valider » était grisé tant que la CIN n'avait pas été
 * EXTRAITE par OCR — joindre le fichier ne suffisait pas, et rien ne le disait.
 * L'employé n'avait aucun moyen de deviner ce qui manquait.
 *
 * Ce composant rend la liste des conditions non satisfaites juste à côté du
 * bouton. Il ne rend rien quand il n'y a aucun blocage : le cas nominal reste
 * visuellement inchangé.
 */
import { AlertTriangle } from 'lucide-react';

export interface BlocageValidationProps {
  /** Motifs non satisfaits. Les entrées vides/fausses sont ignorées. */
  raisons: Array<string | false | null | undefined>;
  /** Identifiant de test (une valeur par étape). */
  testId?: string;
}

export function BlocageValidation({ raisons, testId }: BlocageValidationProps) {
  const items = raisons.filter((r): r is string => typeof r === 'string' && r.trim() !== '');
  if (items.length === 0) return null;
  return (
    <div
      role="status"
      data-testid={testId ?? 'blocage-validation'}
      className="flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900"
    >
      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" aria-hidden />
      <div>
        <p className="font-medium">
          {items.length === 1
            ? 'Il reste une condition à remplir :'
            : `Il reste ${items.length} conditions à remplir :`}
        </p>
        <ul className="mt-1 list-disc space-y-0.5 pl-4">
          {items.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}
