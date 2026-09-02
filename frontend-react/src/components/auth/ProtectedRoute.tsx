import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import type { Role } from '../../types/auth';

interface Props {
  allowed?: Role[];
}

export function ProtectedRoute({ allowed }: Props) {
  const location = useLocation();
  const { isAuthenticated, isHydrating, user } = useAuthStore();

  if (isHydrating) {
    return <FullPageSpinner />;
  }
  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (allowed && !allowed.includes(user.role)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <Outlet />;
}

function FullPageSpinner() {
  return (
    <div className="flex h-screen w-screen items-center justify-center bg-bg-overlay">
      <div className="h-10 w-10 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
    </div>
  );
}
