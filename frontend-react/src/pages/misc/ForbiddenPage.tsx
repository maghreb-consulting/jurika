import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { Button } from '../../components/ui/Button';

export function ForbiddenPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-bg-overlay px-4 text-center">
      <ShieldAlert className="h-14 w-14 text-rose-500" />
      <h1 className="text-3xl font-bold text-fg">Acces refuse</h1>
      <p className="max-w-md text-fg-muted">
        Vous n'avez pas les permissions necessaires pour acceder a cette page.
      </p>
      <Link to="/dashboard">
        <Button>Retour au dashboard</Button>
      </Link>
    </div>
  );
}
