import fs from 'node:fs';
import path from 'node:path';

/**
 * Lot L6b (E4) : synthese du parcours visuel, ecrite meme quand des ecrans echouent.
 *   <VISUEL_RAPPORT>/defauts.json   tous les constats, par ecran, role, largeur, theme
 *   <VISUEL_RAPPORT>/rapport.md     tableau lisible (ecrans sains, defauts par categorie)
 * Les captures sont dans VISUEL_SORTIE ; le rapport html de Playwright dans <RAPPORT>/html.
 */
interface Constat {
  categorie: string;
  message: string;
}
interface Fragment {
  ecran: string;
  groupe: string;
  chemin: string;
  profil: string;
  largeur: number;
  theme: string;
  capture: string | null;
  absent: boolean;
  constats: Constat[];
}

export default async function synthese(): Promise<void> {
  const rapport = process.env.VISUEL_RAPPORT;
  if (!rapport) return;
  const dossier = path.join(rapport, 'fragments');
  if (!fs.existsSync(dossier)) return;
  const fragments: Fragment[] = fs
    .readdirSync(dossier)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => JSON.parse(fs.readFileSync(path.join(dossier, f), 'utf8')) as Fragment);
  fs.writeFileSync(path.join(rapport, 'defauts.json'), JSON.stringify(fragments, null, 2));

  const parCategorie = new Map<string, number>();
  for (const f of fragments) for (const c of f.constats) parCategorie.set(c.categorie, (parCategorie.get(c.categorie) ?? 0) + 1);
  const sains = fragments.filter((f) => !f.absent && f.constats.length === 0);
  const absents = fragments.filter((f) => f.absent);
  const lignes: string[] = [];
  lignes.push('# Verification visuelle (lot L6b, E4)', '');
  lignes.push(`Execution : ${new Date().toISOString()}`, '');
  lignes.push(`- Combinaisons verifiees (ecran x role x largeur x theme) : ${fragments.length}`);
  lignes.push(`- Sans defaut : ${sains.length}`);
  lignes.push(`- Avec defaut : ${fragments.length - sains.length - absents.length}`);
  lignes.push(`- Ecrans absents (prevus, pas encore construits) : ${absents.length}`, '');
  lignes.push('## Defauts par categorie', '', '| Categorie | Nombre |', '|---|---|');
  for (const [k, v] of [...parCategorie.entries()].sort()) lignes.push(`| ${k} | ${v} |`);
  lignes.push('', '## Detail par ecran', '');
  for (const f of fragments) {
    const etat = f.absent ? 'ABSENT' : f.constats.length ? `${f.constats.length} defaut(s)` : 'sain';
    lignes.push(`### ${f.ecran} (${f.profil}) ${f.largeur} px ${f.theme} : ${etat}`);
    lignes.push(`Route : \`${f.chemin}\`${f.capture ? ` ; capture : \`${path.basename(f.capture)}\`` : ''}`, '');
    for (const c of f.constats) lignes.push(`- [${c.categorie}] ${c.message.replace(/\|/g, '/')}`);
    lignes.push('');
  }
  fs.writeFileSync(path.join(rapport, 'rapport.md'), lignes.join('\n'));
  console.log(`Rapport visuel : ${path.join(rapport, 'rapport.md')} (${fragments.length} combinaisons, ${sains.length} sans defaut)`);
}
