/**
 * Lot L4d (E8) : publicites de la societe saisies a l'import (annonces au journal d'annonces
 * legales et publications au Bulletin officiel). Meme regle que le serveur (ImportWorkflow).
 */
export interface Publicite {
  nature: 'CONSTITUTION' | 'MODIFICATION' | '';
  objet?: string;
  support: 'JAL' | 'BO' | '';
  journal?: string;
  dateParution: string;
  numeroParution: string;
  documentId?: string;
  fichier?: string;
}

export function publiciteVide(): Publicite {
  return { nature: '', support: '', dateParution: '', numeroParution: '' };
}

/** Ce qui manque à une publicité (message nommé), ou null — même règle que le serveur. */
export function manquePublicite(p: Publicite): string | null {
  const manques: string[] = [];
  if (!p.nature) manques.push('nature (constitution ou modification)');
  else if (p.nature === 'MODIFICATION' && !(p.objet ?? '').trim()) manques.push('objet de la modification');
  if (!p.support) manques.push('support (journal d’annonces légales ou Bulletin officiel)');
  else if (p.support === 'JAL' && !(p.journal ?? '').trim()) manques.push('journal d’annonces légales');
  if (!p.dateParution) manques.push('date de parution');
  if (!p.numeroParution.trim()) manques.push('numéro de parution');
  return manques.length ? `indiquez ${manques.join(', ')}.` : null;
}
