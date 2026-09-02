/**
 * Landing « effet wow » — cadres d'appareil réutilisables à la charte.
 *
 * BrowserFrame : fenêtre navigateur (feux macOS + barre d'URL app.jurika.ma).
 * MobileFrame  : coque mobile légère (encoche + écran).
 *
 * Les deux reprennent la même grammaire visuelle que le mockup Hero
 * (verre, ombre navy, filet or) mais namespacés `dvc-*` pour ne pas
 * entrer en collision avec `.mock-window` du Hero.
 */

function LockIcon() {
  return (
    <svg className="dvc__lock" viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="4" y="11" width="16" height="9" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </svg>
  );
}

export function BrowserFrame({ url = 'app.jurika.ma', children, className = '', tilt = false }) {
  return (
    <div className={`dvc dvc-browser ${className}`} {...(tilt ? { 'data-tilt': 'true' } : {})}>
      <div className="dvc__bar" aria-hidden="true">
        <span className="dvc__dot dvc__dot--red" />
        <span className="dvc__dot dvc__dot--yellow" />
        <span className="dvc__dot dvc__dot--green" />
        <span className="dvc__url"><LockIcon /> {url}</span>
      </div>
      <div className="dvc__body">{children}</div>
      <span className="dvc__glare" aria-hidden="true" />
    </div>
  );
}

export function MobileFrame({ children, className = '', notchLabel = '9:41' }) {
  return (
    <div className={`dvc-mobile ${className}`} aria-hidden="true">
      <div className="dvc-mobile__notch"><span>{notchLabel}</span></div>
      <div className="dvc-mobile__screen">{children}</div>
    </div>
  );
}
