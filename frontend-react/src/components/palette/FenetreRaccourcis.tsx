import { useAuthStore } from '../../store/authStore';
import { RACCOURCI_ECHAP, posteApple, raccourcisDuRole } from '../../lib/raccourcis';
import { Modal } from '../ui/Modal';
import { Touches } from './Touches';

/**
 * Lot L6b (E11) : fenetre "Raccourcis clavier" (touche ?, JURI, palette). La liste vient de
 * lib/raccourcis (source unique du lecteur de touches) et ne montre que les raccourcis du
 * role connecte.
 */
export function FenetreRaccourcis({ open, onClose }: { open: boolean; onClose: () => void }) {
  const role = useAuthStore((s) => s.user?.role);
  const apple = posteApple();
  const liste = raccourcisDuRole(role);
  return (
    <Modal
      open={open}
      onOpenChange={(o) => {
        if (!o) onClose();
      }}
      title="Raccourcis clavier"
      description="Ils ne s’appliquent jamais pendant la saisie dans un champ, ni quand une fenêtre est ouverte."
      size="md"
    >
      <table className="w-full text-sm" data-testid="raccourcis-liste">
        <caption className="sr-only">Raccourcis clavier disponibles</caption>
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-fg-subtle">
            <th scope="col" className="pb-2 pr-4 font-semibold">
              Touches
            </th>
            <th scope="col" className="pb-2 font-semibold">
              Effet
            </th>
          </tr>
        </thead>
        <tbody>
          {liste.map((r) => (
            <tr key={r.action} className="border-t border-border align-top">
              <td className="whitespace-nowrap py-2.5 pr-4">
                <Touches
                  touches={r.combinaison && apple ? r.touches.map((t) => (t === 'Ctrl' ? 'Cmd' : t)) : r.touches}
                  combinaison={r.combinaison}
                />
              </td>
              <td className="py-2.5 text-fg">{r.description}</td>
            </tr>
          ))}
          <tr className="border-t border-border align-top">
            <td className="whitespace-nowrap py-2.5 pr-4">
              <Touches touches={RACCOURCI_ECHAP.touches} />
            </td>
            <td className="py-2.5 text-fg">{RACCOURCI_ECHAP.description}</td>
          </tr>
        </tbody>
      </table>
      <p className="mt-4 text-xs leading-relaxed text-fg-muted">
        « g puis t » : appuyez sur g, relâchez, puis appuyez sur t dans la seconde et demie qui suit.
      </p>
    </Modal>
  );
}
