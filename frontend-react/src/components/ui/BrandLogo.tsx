import { useThemeStore } from '../../store/themeStore';
// Nouveau logo JURIKA (2026-07-27) : embleme officiel (ecusson navy + monogramme
// J dore + balance/IA) livre en deux fonds — navy (surfaces sombres) et clair
// (surfaces claires) — plus le lockup horizontal complet. Tuiles 512px (127 Ko),
// nettes a 36-48px retina inclus.
import emblemNavyUrl from '../../assets/brand/jurika-emblem-navy.png';
import emblemClairUrl from '../../assets/brand/jurika-emblem-clair.png';
import logoFullUrl from '../../assets/brand/jurika-logo-full.png';

/**
 * Logo JURIKA — source de verite unique pour l'affichage de la marque.
 *
 *  - `variant='emblem'` (defaut) : ecusson carre. Le fond (`tone`) doit matcher
 *    la surface : `navy` sur fond sombre, `clair` sur fond clair. `tone='auto'`
 *    (defaut) resout automatiquement selon le theme applicatif (data-theme) :
 *    theme sombre -> navy, theme clair -> clair.
 *  - `variant='full'` : lockup horizontal (embleme + « JURIKA » + baseline).
 *    Fond velin clair inclus -> a reserver aux surfaces claires.
 *
 * `size` = hauteur en px (ratio preserve, `width: auto`).
 */
interface BrandLogoProps {
  variant?: 'emblem' | 'full';
  /** Fond de l'embleme. 'auto' (defaut) suit le theme applicatif. */
  tone?: 'auto' | 'navy' | 'clair';
  /** Hauteur en pixels. Defaut 36. */
  size?: number;
  className?: string;
}

export function BrandLogo({ variant = 'emblem', tone = 'auto', size = 36, className }: BrandLogoProps) {
  const mode = useThemeStore((s) => s.mode);
  const resolvedTone = tone === 'auto' ? (mode === 'dark' ? 'navy' : 'clair') : tone;
  const src =
    variant === 'full'
      ? logoFullUrl
      : resolvedTone === 'navy'
        ? emblemNavyUrl
        : emblemClairUrl;
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
