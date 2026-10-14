import { useThemeStore } from '../../store/themeStore';
// Lot L6 (E6) : logo valide "Moucharabieh" (livraison ~/docs/logo/3-moucharabieh,
// copie sous des noms ASCII). Vectoriel : net a toutes les tailles.
// - embleme.svg : tuile marine, pour les surfaces claires ;
// - embleme-ivoire.svg : tuile ivoire, pour les surfaces sombres ;
// - logo-horizontal-clair.svg : embleme + "JURIKA" marine et or, fond transparent ;
// - logo-horizontal-sombre-sans-fond.svg : variante sombre (tuile ivoire, lettres
//   blanches et or) dont le fond marine plein a ete retire pour se poser sur
//   l'en-tete sombre de l'application.
import embleme from '../../assets/brand/moucharabieh/embleme.svg';
import embleIvoire from '../../assets/brand/moucharabieh/embleme-ivoire.svg';
import horizontalClair from '../../assets/brand/moucharabieh/logo-horizontal-clair.svg';
import horizontalSombre from '../../assets/brand/moucharabieh/logo-horizontal-sombre-sans-fond.svg';

/**
 * Logo JURIKA -- source unique pour l'affichage de la marque.
 *
 *  - `variant='emblem'` (defaut) : embleme carre ;
 *  - `variant='full'` : logo horizontal (embleme + "JURIKA" + "Gestion juridique
 *    des entreprises").
 *
 * `tone` designe la SURFACE sur laquelle le logo est pose : `clair` (fond clair :
 * embleme marine, lettres marine), `navy` (fond sombre : embleme ivoire, lettres
 * blanches). `auto` (defaut) suit le theme de l'application.
 *
 * `size` = hauteur en pixels (proportions conservees).
 */
interface BrandLogoProps {
  variant?: 'emblem' | 'full';
  /** Surface sous le logo. 'auto' (defaut) suit le theme applicatif. */
  tone?: 'auto' | 'navy' | 'clair';
  /** Hauteur en pixels. Defaut 36. */
  size?: number;
  className?: string;
}

export function BrandLogo({ variant = 'emblem', tone = 'auto', size = 36, className }: BrandLogoProps) {
  const mode = useThemeStore((s) => s.mode);
  const sombre = tone === 'auto' ? mode === 'dark' : tone === 'navy';
  const src =
    variant === 'full' ? (sombre ? horizontalSombre : horizontalClair) : sombre ? embleIvoire : embleme;
  return (
    <img
      src={src}
      alt="JURIKA"
      height={size}
      style={{ height: size, width: 'auto' }}
      loading="eager"
      decoding="async"
      draggable={false}
      className={className}
    />
  );
}
