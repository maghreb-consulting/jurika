import { Outlet } from 'react-router-dom';
import { Sidebar, SidebarTopbar } from './Sidebar';
import { SupervisorRemarks } from './SupervisorRemarks';

/**
 * Alternate shell using a vertical Sidebar + minimal Topbar
 * instead of the dark AppTopNav. NOT wired into the router by default —
 * use it on dashboards that prefer the Sidebar variant.
 *
 * Floating SupervisorRemarks panel is always mounted; it is itself
 * role-gated and renders null for non-supervisors.
 */
export function AppShellSidebar() {
  return (
    <div className="flex min-h-screen bg-bg text-fg">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <SidebarTopbar />
        <main className="flex-1 overflow-x-hidden">
          <div className="mx-auto max-w-7xl px-6 py-6">
            <Outlet />
          </div>
        </main>
      </div>
      <SupervisorRemarks />
    </div>
  );
}
