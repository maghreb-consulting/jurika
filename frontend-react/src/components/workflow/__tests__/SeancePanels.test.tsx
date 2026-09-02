import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ConvocationPanel, FeuillePresencePanel } from '../SeanceDocPanels';
import { IncidentSeancePanel, incidentPvFilename } from '../IncidentSeancePanel';

/**
 * Point 1 (audit directeur) — les documents de séance partagés (Convocation / Incident /
 * Feuille de présence) sont désormais posés sur les 3 pages succursale. Ils doivent rester
 * OPTIONNELS et repliés par défaut (mêmes composants que Dissolution/Modification/PV AGO).
 *
 * Point 3 — nommage conforme « Type + Dénomination » des PV d'incident.
 */
describe('Documents de séance — Point 3 : nommage conforme', () => {
  it('mappe le code technique vers un libellé lisible + dénomination', () => {
    expect(incidentPvFilename('PV_DEFAUT_QUORUM_SARL', 'ACME SARL')).toBe(
      'PV — Défaut de quorum - ACME SARL.docx',
    );
    expect(incidentPvFilename('PV_IRREGULARITE_CONVOCATION_SARL_AU', 'BETA SAS')).toBe(
      'PV — Irrégularité de convocation - BETA SAS.docx',
    );
  });

  it('retombe sur « Société » si la dénomination est absente', () => {
    expect(incidentPvFilename('PV_DEFAUT_QUORUM_SARL_AU', '')).toBe(
      'PV — Défaut de quorum - Société.docx',
    );
    expect(incidentPvFilename('PV_DEFAUT_QUORUM_SARL', null)).toBe(
      'PV — Défaut de quorum - Société.docx',
    );
  });

  it('renvoie undefined pour un code non-incident (garde le nom backend)', () => {
    expect(incidentPvFilename('PV_APPROBATION_COMPTES_SARL', 'ACME')).toBeUndefined();
  });
});

describe('Documents de séance — Point 1 : optionnels et repliés par défaut', () => {
  const societe = { denomination: 'ACME SARL', villeGreffe: 'Casablanca' };

  it('ConvocationPanel : optionnel, replié', () => {
    render(<ConvocationPanel formeJuridique="SARL" societe={societe} />);
    const toggle = screen.getByTestId('seance-doc-toggle-CONVOCATION_AG');
    expect(toggle).toHaveTextContent(/optionnel/i);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('FeuillePresencePanel : optionnel, replié', () => {
    render(<FeuillePresencePanel formeJuridique="SARL" societe={societe} />);
    const toggle = screen.getByTestId('seance-doc-toggle-FEUILLE_PRESENCE_AG');
    expect(toggle).toHaveTextContent(/optionnel/i);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('IncidentSeancePanel : optionnel, replié', () => {
    render(<IncidentSeancePanel formeJuridique="SARL" societe={societe} />);
    const toggle = screen.getByTestId('incident-seance-toggle');
    expect(toggle).toHaveTextContent(/optionnel/i);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });
});
