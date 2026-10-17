import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { libelleAction } from '../libelleAction';

/**
 * Lot L6b (E9) : les vues de tracabilite affichent le libelle de l'action rendu par le
 * serveur (actionLabel), jamais le code brut ni un libelle calcule par l'ecran.
 * Defaut vu a l'ecran : "Vous — login success", "document generated"...
 */
const SRC = resolve(__dirname, '../..');

const VUES = [
  'components/tracabilite/EntityActivityPanel.tsx',
  'pages/tracabilite/TracabilitePage.tsx',
  'pages/admin/AuditLogPage.tsx',
  'pages/admin/WorkspaceDetailPage.tsx',
  'pages/dashboard/SuperAdminDashboard.tsx',
  'pages/dataroom/components/ClientAccessLogDrawer.tsx',
];

function sources(dossier: string): string[] {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const chemin = join(dossier, e.name);
    if (e.isDirectory()) return e.name === '__tests__' ? [] : sources(chemin);
    return /\.(ts|tsx)$/.test(e.name) && !/\.test\./.test(e.name) ? [chemin] : [];
  });
}

describe('libelleAction', () => {
  it('rend le libelle du serveur tel quel', () => {
    expect(libelleAction('Connexion réussie', 'LOGIN_SUCCESS')).toBe('Connexion réussie');
  });

  it('libelle absent : dit que le code n a pas de libelle, sans le deguiser', () => {
    expect(libelleAction(undefined, 'LOGIN_SUCCESS')).toBe('Action enregistrée (code LOGIN_SUCCESS)');
    expect(libelleAction('  ', 'DOCUMENT_GENERATED')).toBe('Action enregistrée (code DOCUMENT_GENERATED)');
    expect(libelleAction(null, null)).toBe('');
  });
});

describe('vues de tracabilite : libelle du serveur, jamais le code', () => {
  it.each(VUES)('%s affiche le libelle par libelleAction', (vue) => {
    const texte = readFileSync(join(SRC, vue), 'utf8');
    expect(texte).toContain('libelleAction(');
    // Le code brut rendu dans le JSX.
    expect(texte).not.toMatch(/\{\s*[a-z]\.action\s*\}/);
    // Un libelle fabrique a partir du code.
    expect(texte).not.toMatch(/\.action\.replace\(|action\.toLowerCase\(\)/);
  });

  it('aucune table de libelles des actions ne subsiste dans le front', () => {
    const fautifs = sources(SRC).filter((f) =>
      /ACTION_AUDIT_LABELS|ACCESS_LOG_ACTION_LABELS|libelleActionAudit/.test(readFileSync(f, 'utf8')),
    );
    expect(fautifs).toEqual([]);
  });
});
