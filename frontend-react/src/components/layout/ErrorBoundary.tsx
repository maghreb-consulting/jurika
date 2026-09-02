import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RotateCw } from 'lucide-react';
import { Button } from '../ui/Button';

interface Props {
  children: ReactNode;
  /**
   * Libellé optionnel affiché dans le fallback (ex. nom de la section).
   */
  label?: string;
}

interface State {
  error: Error | null;
}

/**
 * 2026-06-30 (Lot — freeze navigation /chat) — ErrorBoundary autour de l'<Outlet/>.
 *
 * Problème résolu : sans boundary, si une page (ex. ChatPage) lève une exception
 * au rendu, React démonte tout l'arbre sous <Routes> et NE LE remonte plus à la
 * navigation suivante → « l'URL change mais la vue reste figée, il faut re-saisir
 * l'URL » (seul un reload complet remonte BrowserRouter).
 *
 * Ici le boundary entoure UNIQUEMENT l'Outlet : la topnav / sidebar restent
 * montées et cliquables, donc la navigation continue de fonctionner. Couplé à
 * une `key={pathname}` côté AppShell, le boundary se réinitialise à chaque
 * changement de route : une erreur sur /chat ne contamine pas /dashboard, et
 * revenir sur la page après l'avoir quittée tente un nouveau rendu propre.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Log non bloquant — visible en console pour diagnostic, ne casse rien.
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary] rendu interrompu :', error, info.componentStack);
  }

  private handleReset = (): void => {
    this.setState({ error: null });
  };

  render(): ReactNode {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <div
        role="alert"
        className="flex min-h-[40vh] flex-col items-center justify-center gap-4 rounded-2xl border border-border bg-bg-raised p-10 text-center"
      >
        <span className="flex h-14 w-14 items-center justify-center rounded-full bg-danger/10 text-danger">
          <AlertTriangle className="h-7 w-7" />
        </span>
        <div className="space-y-1">
          <h2 className="text-xl font-semibold text-fg">Une erreur est survenue</h2>
          <p className="max-w-md text-sm text-fg-subtle">
            {this.props.label
              ? `La section « ${this.props.label} » n'a pas pu s'afficher.`
              : "Cette page n'a pas pu s'afficher."}{' '}
            Vous pouvez réessayer ou continuer à naviguer via le menu.
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-center gap-2">
          <Button onClick={this.handleReset} variant="secondary" size="sm">
            <RotateCw className="mr-1.5 h-4 w-4" /> Réessayer
          </Button>
          <Button onClick={() => window.location.reload()} size="sm">
            Recharger la page
          </Button>
        </div>
      </div>
    );
  }
}
