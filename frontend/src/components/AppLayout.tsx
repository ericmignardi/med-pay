import { FileText, LogOut, ScrollText, ShieldCheck, Stamp } from 'lucide-react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';

import { useAuth } from '@/auth/useAuth';

import type { Role } from '@/types/api';

interface NavItem {
  to: string;
  label: string;
  role: Role;
  icon: typeof FileText;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/claims', label: 'Claims', role: 'CLAIMS_PROCESSOR', icon: FileText },
  { to: '/review', label: 'Review queue', role: 'MEDICAL_REVIEWER', icon: Stamp },
  { to: '/audit/journals', label: 'Ledger audit', role: 'AUDITOR', icon: ScrollText },
];

export function AppLayout() {
  const { user, signOut, hasRole } = useAuth();
  const navigate = useNavigate();

  // Only the tabs the signed-in roles can actually reach — a visible tab that lands on
  // /403 is a worse experience than no tab.
  const visibleItems = NAV_ITEMS.filter((item) => hasRole(item.role));

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-6 w-6 text-sky-700" aria-hidden="true" />
            <span className="text-base font-semibold tracking-tight text-slate-900">
              MedPay Ledger
            </span>
          </div>

          <nav className="flex flex-1 flex-wrap gap-1" aria-label="Main">
            {visibleItems.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium ${
                    isActive
                      ? 'bg-sky-50 text-sky-800'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`
                }
              >
                <Icon className="h-4 w-4" aria-hidden="true" />
                {label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-3 text-sm">
            <div className="text-right">
              <p className="font-medium text-slate-900">{user?.fullName}</p>
              <p className="text-xs text-slate-500">{user?.roles.join(' · ')}</p>
            </div>
            <button
              type="button"
              onClick={() => {
                signOut();
                navigate('/login', { replace: true });
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
