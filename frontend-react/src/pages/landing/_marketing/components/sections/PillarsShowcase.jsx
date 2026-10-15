import { useRef } from 'react';
import { useScrollReveal } from '../../hooks/useScrollReveal.js';
import { useTilt3D } from '../../hooks/useTilt3D.js';
import { WorkflowMockup } from '../mockups/WorkflowMockup.jsx';
import { DataRoomMockup } from '../mockups/DataRoomMockup.jsx';
import { CollaborationMockup } from '../mockups/CollaborationMockup.jsx';
import { CopiloteMockup } from '../mockups/CopiloteMockup.jsx';
import { DashboardMockup } from '../mockups/DashboardMockup.jsx';

/**
 * Les 5 piliers de JURIKA, chacun avec son ecran d'illustration, en alternance
 * gauche/droite. Textes alignes sur le cahier des charges (lot L7 E3) : parcours (16.1),
 * extraction des pieces (RG-VAR-09), generation deterministe (P1, P3), Data Room (11),
 * permissions du client (RG-CLI-01), demandes et requetes (RG-COM-07), chatbot (12.1),
 * tickets (RG-TKT-01), tableaux de bord (forfait Max, 4.5), socle de securite (4.4).
 *
 * Reveal scroll-driven (`data-reveal`) + tilt 3D leger sur les mockups
 * (`data-tilt`, pose par BrowserFrame). Degrade proprement en reduced-motion.
 */
const PILLARS = [
  {
    id: 'workflows',
    kicker: '01 · Parcours guidés',
    title: 'Chaque opération suit son parcours, de la saisie à la clôture',
    lead:
      "Création, modification, dissolution, liquidation : le parcours guide le juriste à l'aide des référentiels de démarches, sans ressaisie.",
    points: [
      'Création de SARL et de SARL à associé unique, modifications et vie sociale, assemblée générale ordinaire, succursales, dissolution et liquidation, import de dossier.',
      'Lecture automatique de la CIN et du certificat négatif : les valeurs lues vous sont présentées pour confirmation.',
      "Génération déterministe : à données identiques, acte identique. Une donnée manquante est nommée plutôt qu'un acte incomplet.",
      'Démarches cochées au fil du parcours ; alertes de délais légaux à partir du forfait Professionnel.',
    ],
    Mockup: WorkflowMockup,
  },
  {
    id: 'dataroom',
    kicker: '02 · Data Room',
    title: 'Une Data Room par dossier, partagée avec le client',
    lead:
      "Chaque société suivie a son espace documentaire, organisé par opération et strictement cloisonné d'un cabinet à l'autre.",
    points: [
      'Quatre sections : dossier juridique, dépôts du client, demandes du client et requêtes du cabinet.',
      'Une seule version en vigueur à chaque emplacement ; les versions remplacées restent consultables.',
      'Fiche client, fiche dossier et état des débours générés à partir des données du dossier.',
    ],
    Mockup: DataRoomMockup,
    reverse: true,
  },
  {
    id: 'collaboration',
    kicker: '03 · Accès du client',
    title: 'Un accès limité à son seul dossier pour chaque client',
    lead:
      "Invitez le client d'un dossier : il ne voit que les documents que vous lui rendez visibles, dans la limite des permissions accordées.",
    points: [
      'Permissions réglées par dossier : consultation des documents, téléchargement, dépôt de documents, envoi de demandes.',
      'Chaque modification des permissions est tracée.',
      'Demandes du client et requêtes du cabinet suivies par état : non traitée, en cours, traitée.',
    ],
    Mockup: CollaborationMockup,
  },
  {
    id: 'ia',
    kicker: '04 · Chatbot juridique',
    title: 'Un chatbot juridique qui cite ses sources',
    lead:
      "Une aide juridique générale en droit des sociétés marocain, qui répond à partir de sources fiables et les cite.",
    points: [
      'Réponses tirées des sources fiables déposées par JURIKA, avec la référence citée.',
      "Aucun accès aux dossiers : une question qui contient une donnée personnelle (CIN, ICE, téléphone…) est bloquée avant l'envoi.",
      'Chaque utilisateur retrouve ses conversations, qui ne sont visibles par personne d’autre.',
    ],
    Mockup: CopiloteMockup,
    reverse: true,
  },
  {
    id: 'pilotage',
    kicker: '05 · Suivi et traçabilité',
    title: "Suivez l'avancement, retrouvez chaque action",
    lead:
      "Le responsable du cabinet voit l'état exact de chaque ticket, par employé ; chaque action porte son auteur, sa date et son heure.",
    points: [
      "Cinq statuts de ticket, dans l'ordre : collecte d'information, génération des documents, déroulement de la démarche, clôture du dossier, ou annulation motivée.",
      'Dossiers confiés à un employé responsable, transfert accepté par le destinataire.',
      'Tableaux de bord de productivité et rapports d’activité avec le forfait Max.',
      'Double authentification, chiffrement des échanges et droits par rôle vérifiés côté serveur, pour tous les forfaits.',
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
            Toute la vie juridique des sociétés suivies par le cabinet, en un seul endroit.
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
