import fs from 'node:fs';
import path from 'node:path';

/**
 * Lot L6b (E4) : avant le parcours, vide les fragments du rapport et les captures de
 * l'execution precedente (un rapport ne melange jamais deux executions).
 */
export default async function preparation(): Promise<void> {
  const rapport = process.env.VISUEL_RAPPORT;
  const sortie = process.env.VISUEL_SORTIE;
  if (rapport) fs.rmSync(path.join(rapport, 'fragments'), { recursive: true, force: true });
  if (sortie && fs.existsSync(sortie)) {
    for (const f of fs.readdirSync(sortie)) if (f.endsWith('.png')) fs.rmSync(path.join(sortie, f));
  }
}
