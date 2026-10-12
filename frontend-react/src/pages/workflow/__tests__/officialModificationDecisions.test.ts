import { describe, expect, it } from 'vitest';
import {
  GROUP_ORDER,
  OFFICIAL_DECISIONS,
  RESOLUTION_TYPES,
  decisionsForForme,
  groupedDecisions,
  findDecision,
  unmappedDecisions,
} from '../officialModificationDecisions';

const ALL = [...OFFICIAL_DECISIONS.SARL, ...OFFICIAL_DECISIONS.SARL_AU];

describe('officialModificationDecisions — catalogue officiel', () => {
  it('ne laisse AUCUN item non mappé (règle Phase 0)', () => {
    expect(unmappedDecisions()).toEqual([]);
  });

  it('mappe chaque décision vers un RESOLUTION_TYPE connu du moteur directeur', () => {
    for (const d of ALL) {
      expect(RESOLUTION_TYPES.has(d.resolutionType), `${d.id} → ${d.resolutionType}`).toBe(true);
    }
  });

  it('utilise des identifiants uniques', () => {
    const ids = ALL.map((d) => d.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('route les décisions génériques vers un type générique et les signale', () => {
    const GENERIC_TYPES = new Set(['modification_statuts_autre', 'mise_harmonie_statuts']);
    for (const d of ALL) {
      if (d.generic) {
        expect(GENERIC_TYPES.has(d.resolutionType), `${d.id} generic → ${d.resolutionType}`).toBe(
          true,
        );
      }
    }
  });

  it('filtre les décisions selon la forme juridique', () => {
    expect(decisionsForForme('SARL')).toBe(OFFICIAL_DECISIONS.SARL);
    expect(decisionsForForme('SARL_AU')).toBe(OFFICIAL_DECISIONS.SARL_AU);
    // défaut → SARL
    expect(decisionsForForme(null)).toBe(OFFICIAL_DECISIONS.SARL);
  });

  it('expose les fusions/scissions typées en SARL et génériques en SARL AU', () => {
    // SARL : bloc typé operation_restructuration
    const sarlFusion = OFFICIAL_DECISIONS.SARL.filter(
      (d) => d.resolutionType === 'operation_restructuration',
    );
    expect(sarlFusion.length).toBe(3); // fusion / scission / apport partiel

    // SARL AU : la restructuration figure bien dans la liste (affichée) mais est générique
    const auRestruct = OFFICIAL_DECISIONS.SARL_AU.find((d) => d.id === 'au-ext-restructuration');
    expect(auRestruct).toBeDefined();
    expect(auRestruct?.generic).toBe(true);
    expect(auRestruct?.resolutionType).toBe('modification_statuts_autre');
    // Le modèle AU n'a pas de branche operation_restructuration : aucun item AU ne doit y pointer.
    expect(
      OFFICIAL_DECISIONS.SARL_AU.some((d) => d.resolutionType === 'operation_restructuration'),
    ).toBe(false);
  });

  it('réserve cession_parts_pluripersonnelle à la SARL AU', () => {
    expect(OFFICIAL_DECISIONS.SARL.some((d) => d.resolutionType === 'cession_parts_pluripersonnelle')).toBe(
      false,
    );
    expect(
      OFFICIAL_DECISIONS.SARL_AU.some((d) => d.resolutionType === 'cession_parts_pluripersonnelle'),
    ).toBe(true);
  });

  it('marque les décisions faisant entrer un nouvel associé (OCR étape 2)', () => {
    const sarlNew = OFFICIAL_DECISIONS.SARL.filter((d) => d.newAssocie).map((d) => d.id);
    expect(sarlNew).toContain('sarl-age-agrement-cession');
    expect(sarlNew).toContain('sarl-age-agrement-nouvel-associe');
    const auNew = OFFICIAL_DECISIONS.SARL_AU.filter((d) => d.newAssocie).map((d) => d.id);
    expect(auNew).toContain('au-ext-cession-pluripersonnelle');
  });

  it('regroupe les décisions par groupe (ordre officiel) et sous-groupe', () => {
    const sarl = groupedDecisions('SARL');
    expect(sarl.map((g) => g.group)).toEqual(GROUP_ORDER.SARL);
    // AGO commence par « Comptes, résultat et distributions »
    const ago = sarl.find((g) => g.group === 'AGO');
    expect(ago?.subGroups[0].title).toBe('Comptes, résultat et distributions');

    const au = groupedDecisions('SARL_AU');
    expect(au.map((g) => g.group)).toEqual(GROUP_ORDER.SARL_AU);
  });

  it('L5 (E5) : l’associé unique peut choisir l’affectation du résultat et la distribution de réserves (intitulés de la matrice du corpus)', () => {
    const affectation = OFFICIAL_DECISIONS.SARL_AU.find((d) => d.resolutionType === 'affectation_resultat');
    expect(affectation?.label).toBe(
      "Affectation du résultat de l'exercice : réserve légale, réserves facultatives, report à nouveau, dividende",
    );
    const reserves = OFFICIAL_DECISIONS.SARL_AU.find((d) => d.resolutionType === 'distribution_reserves');
    expect(reserves?.label).toBe(
      'Distribution exceptionnelle de sommes prélevées sur les réserves facultatives, avec indication des postes de prélèvement',
    );
    for (const d of [affectation, reserves]) {
      expect(d?.group).toBe('ORDINAIRE');
      expect(d?.subGroup).toBe('Comptes, résultat et distributions');
    }
    // L'intitulé de l'inventaire du directeur reste, routé vers les dividendes.
    expect(findDecision('au-ord-distribution')?.resolutionType).toBe('distribution_dividendes');
  });

  it('conserve les intitulés verbatim des documents officiels', () => {
    expect(findDecision('sarl-age-mise-harmonie')?.label).toBe(
      'Mise en harmonie et refonte des statuts (conformité à la loi n° 5-96)',
    );
    expect(findDecision('sarl-age-agrement-cession')?.label).toBe(
      'Agrément de cession de parts sociales à des tiers (art. 58)',
    );
    expect(findDecision('au-ext-cession-pluripersonnelle')?.label).toContain(
      'passage en SARL pluripersonnelle',
    );
  });
});
