import type { ComponentType } from 'react';
import type { NomScene } from './scenes';
import { SceneCreation, SceneFrise, type ProprietesScene } from './ScenesTicket';
import { SceneJuri, SceneStatuts, SceneTheme } from './ScenesEcrans';
import { SceneDataRoom } from './SceneDataRoom';

/**
 * Lot L6c E4 : contenu des scenes, charge a la demande (React.lazy, DemonstrationAnimee) : les
 * ecrans reels et leurs composants ne pesent pas sur le premier affichage de la vitrine (le
 * titre de l'accroche, mesure du LCP, n'attend pas leur code).
 */
const CONTENUS: Record<NomScene, ComponentType<ProprietesScene>> = {
  'creation-ticket': SceneCreation,
  'frise-demarches': SceneFrise,
  'data-room': SceneDataRoom,
  'statuts-apercu': SceneStatuts,
  'juri-aide': SceneJuri,
  'theme-clair-sombre': SceneTheme,
};

export default function ContenuScene({ nom, ...props }: ProprietesScene & { nom: NomScene }) {
  const Contenu = CONTENUS[nom];
  return <Contenu {...props} />;
}
