import { useCurrentUser } from '../../store/authStore';
import { EmployeeDashboard } from './EmployeeDashboard';
import { SupervisorDashboard } from './SupervisorDashboard';
import { SuperAdminDashboard } from './SuperAdminDashboard';
import { ClientDashboard } from './ClientDashboard';

export function DashboardRouter() {
  const user = useCurrentUser();
  if (!user) return null;
  switch (user.role) {
    case 'SUPER_ADMIN':
      return <SuperAdminDashboard />;
    case 'SUPERVISEUR':
      return <SupervisorDashboard />;
    case 'EMPLOYE':
      return <EmployeeDashboard />;
    case 'CLIENT':
      return <ClientDashboard />;
  }
}
