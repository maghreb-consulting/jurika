/**
 * Lot L6b (E13) : copie dans le presse-papiers, aussi en HTTP.
 *
 * `navigator.clipboard` n'existe que dans un contexte securise (HTTPS ou localhost). Sur la
 * plateforme servie en HTTP (Z440), `navigator.clipboard.writeText(...)` levait une TypeError
 * dans le gestionnaire de clic : erreur dans la console et copie muette. Repli : zone de texte
 * hors ecran et `document.execCommand('copy')`. Rend vrai si la copie a reussi ; ne leve jamais.
 */
export async function copierTexte(texte: string): Promise<boolean> {
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(texte);
      return true;
    }
  } catch {
    // refus du navigateur : on tente le repli ci-dessous.
  }
  if (typeof document === 'undefined') return false;
  const zone = document.createElement('textarea');
  zone.value = texte;
  zone.setAttribute('readonly', '');
  zone.style.position = 'fixed';
  zone.style.top = '-1000px';
  zone.style.opacity = '0';
  document.body.appendChild(zone);
  try {
    zone.select();
    return typeof document.execCommand === 'function' && document.execCommand('copy');
  } catch {
    return false;
  } finally {
    zone.remove();
  }
}
