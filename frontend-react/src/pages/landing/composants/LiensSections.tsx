import { Link } from 'react-router-dom';
import { SECTIONS_VITRINE } from '../contenu';

/**
 * Liens vers les sections de l'accueil : ancre locale sur l'accueil, lien vers "/#section"
 * ailleurs (la page d'accueil defile alors jusqu'a la section, voir LandingPage).
 */
export function LiensSections({
  surAccueil,
  classeLien,
  onChoisir,
}: {
  surAccueil: boolean;
  classeLien: string;
  onChoisir?: () => void;
}) {
  return (
    <>
      {SECTIONS_VITRINE.map((s) =>
        surAccueil ? (
          <a key={s.id} href={`#${s.id}`} className={classeLien} onClick={onChoisir}>
            {s.libelle}
          </a>
        ) : (
          <Link key={s.id} to={{ pathname: '/', hash: `#${s.id}` }} className={classeLien} onClick={onChoisir}>
            {s.libelle}
          </Link>
        ),
      )}
    </>
  );
}
