import { useRef } from 'react';
import { useScrollReveal } from '../../hooks/useScrollReveal.js';
import { useTilt3D } from '../../hooks/useTilt3D.js';
import { WorkflowMockup } from '../mockups/WorkflowMockup.jsx';
import { DataRoomMockup } from '../mockups/DataRoomMockup.jsx';
import { CollaborationMockup } from '../mockups/CollaborationMockup.jsx';
import { CopiloteMockup } from '../mockups/CopiloteMockup.jsx';
import { DashboardMockup } from '../mockups/DashboardMockup.jsx';

/**
 * Les 5 piliers de JURIKA, chacun avec son mockup d'écran, en alternance
 * gauche/droite. Copy vérifiée contre le code (workflows + extraction CIN OCR/KIE,
 * Data Room docs/permissions/fiche client, collaboration client
 * requêtes/demandes, IA RAG + copilote, pilotage tickets/dashboards/audit).
 *
 * Reveal scroll-driven (`data-reveal`) + tilt 3D léger sur les mockups
 * (`data-tilt`, posé par BrowserFrame). Dégrade proprement en reduced-motion.
 */
const PILLARS = [
  {
    id: 'workflows',
    kicker: '01 · Automatisation',
    title: 'Vos procédures juridiques, automatisées de bout en bout',
    lead:
      "De la création à la liquidation, chaque acte suit un workflow guidé, validé et tracé — sans ressaisie ni Word dispersé.",
    points: [
      'Création SARL / SARL AU, 28 types de modification, dissolution, liquidation, succursale (Maroc & étranger), import de dossier existant.',
      'Saisie des dirigeants et associés assistée par OCR/KIE : CIN et certificat négatif lus automatiquement, champs pré-remplis puis vérifiés.',
      'Génération documentaire déterministe : variables, boucles et clauses conditionnelles produisent statuts, PV et formulaires prêts.',
      'Machine à états du dossier et échéances légales suivies (dépôt RC, publication, formalités).',
    ],
    Mockup: WorkflowMockup,
  },
  {
    id: 'dataroom',
    kicker: '02 · Data Room multi-tenant',
    title: 'Une Data Room cloisonnée, du dépôt à l’archivage',
    lead:
      "Espace documentaire où chaque cabinet est strictement cloisonné : documents en vigueur, dépôts client et permissions fines.",
    points: [
      'Dossiers juridique, comptable et fiscal ; dépôts client versionnés et archivés.',
      'Permissions fines (voir / télécharger / imprimer) cloisonnées par cabinet et par dossier.',
      'Fiche client PDF — carte d’identité juridique de la société — générée par l’employé responsable ou le superviseur.',
    ],
    Mockup: DataRoomMockup,
    reverse: true,
  },
  {
    id: 'collaboration',
    kicker: '03 · Espace & collaboration client',
    title: 'Un espace sécurisé pour chaque client',
    lead:
      "Invitez le client d’un dossier : il n’accède qu’au sien. Requêtes du cabinet et demandes du client circulent au même endroit — suivies, validées, horodatées.",
    points: [
      "Espace cloisonné par dossier : l’invitation crée un compte limité au seul dossier concerné (isolation multi-tenant / RLS).",
      "Permissions fines — consultation, téléchargement, impression — et journal d’accès horodaté (preuve + conformité).",
      "Requêtes du cabinet et demandes du client suivies en Kanban daté, validation en deux temps (déposé → validé), notifications temps réel.",
    ],
    Mockup: CollaborationMockup,
  },
  {
    id: 'ia',
    kicker: '04 · Intelligence artificielle',
    title: 'Un assistant juridique qui cite ses sources',
    lead:
      "Chatbot RAG spécialisé en droit des sociétés marocain, et copilote qui propose des actions — sans jamais agir seul.",
    points: [
      "Réponses ancrées sur le corpus documentaire du cabinet, avec citation de l'article et de la référence.",
      "Copilote qui observe les signaux du dossier et propose des actions à l'employé.",
      "L'humain garde la main : aucune action automatique sur vos données.",
    ],
    Mockup: CopiloteMockup,
    reverse: true,
  },
  {
    id: 'pilotage',
    kicker: '05 · Pilotage & traçabilité',
    title: 'Pilotez le cabinet, prouvez chaque geste',
    lead:
      'Tickets à machine d’états, tableaux de bord graphiques par rôle et journal d’audit complet, cabinet par cabinet.',
    points: [
      'Tickets Nouveau → En cours → Clôturé, chacun déclenchant son workflow ; transfert de dossier entre employés.',
      'Dashboards graphiques par rôle : employé, superviseur, administrateur.',
      'Journal d’audit — qui a fait quoi, quand — et isolation multi-tenant (RLS) par cabinet.',
      'Sécurité : 2FA (TOTP + SMS), session unique, chiffrement au repos et en transit.',
    ],
    Mockup: DashboardMockup,
  },
];

function Pillar({ pillar }) {
  const { kicker, title, lead, points, Mockup, reverse } = pillar;
  return (
    <article className={`pillar ${reverse ? 'pillar--reverse' : ''}`} id={pillar.id}>
      <div className="pillar__text">
        <span className="eyebrow" data-reveal>{kicker}</span>
        <h3 className="pillar__title" data-reveal>{title}</h3>
        <p className="pillar__lead" data-reveal>{lead}</p>
        <ul className="pillar__points">
          {points.map((p, i) => (
            <li key={i} data-reveal style={{ transitionDelay: `${i * 90}ms` }}>
              <span className="pillar__check" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </span>
              <span>{p}</span>
            </li>
          ))}
        </ul>
      </div>
      <div className="pillar__visual" data-reveal>
        <Mockup />
      </div>
    </article>
  );
}

export function PillarsShowcase() {
  const ref = useRef(null);
  useScrollReveal({ scope: ref });
  useTilt3D({ scope: ref, maxAngle: 3, glare: true });

  return (
    <section className="section section-pillars" id="fonctionnalites" ref={ref}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow" data-reveal>La plateforme</span>
          <h2 className="section-title" data-reveal>Cinq piliers, un seul flux de travail</h2>
          <p className="section-sub" data-reveal>
            Tout le cycle de vie juridique des entreprises au Maroc — automatisé, documenté et cloisonné par cabinet.
          </p>
        </div>
      </div>
      <div className="container pillars">
        {PILLARS.map((p) => (
          <Pillar key={p.id} pillar={p} />
        ))}
      </div>
    </section>
  );
}
