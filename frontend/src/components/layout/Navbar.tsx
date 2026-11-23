import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { UtensilsCrossed, ChevronRight, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Card';
import { checkApiHealth } from '@/lib/apiClient';
import { useAuth } from '@/context/AuthContext';

export const Navbar: React.FC = () => {
  const { user, isAuthenticated, logout, hasPermission } = useAuth();
  const [apiOnline, setApiOnline] = useState<boolean | null>(null);

  useEffect(() => {
    checkApiHealth().then((res) => {
      setApiOnline(res.success && res.data.status === 'UP');
    });
  }, []);

  const role = user?.role?.toUpperCase() || '';
  const isManagerOrAdmin = ['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER'].includes(role);
  const canViewWaiter = isManagerOrAdmin || role === 'WAITER';
  const canViewKitchen = isManagerOrAdmin || role === 'KITCHEN_STAFF';
  const canViewFinance =
    isManagerOrAdmin ||
    role === 'CASHIER' ||
    role === 'SUPER_ADMIN' ||
    (role === 'WAITER' && (hasPermission('BILL_COLLECT_PAYMENT') || hasPermission('PAYMENT_PROCESS')));

  return (
    <header className="sticky top-0 z-50 backdrop-blur-md bg-stone-50/90 border-b border-stone-200/80 transition-all">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-18 flex items-center justify-between">
        {/* Brand Logo */}
        <Link to="/" className="flex items-center gap-3 group">
          <div className="w-10 h-10 rounded-xl bg-slate-900 flex items-center justify-center text-emerald-400 shadow-md shadow-slate-900/10 group-hover:scale-105 transition-transform duration-200">
            <UtensilsCrossed className="w-5 h-5 stroke-[2.2]" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-xl tracking-tight text-slate-900">Dine<span className="text-emerald-600">Flow</span></span>
              <Badge variant="emerald" className="hidden sm:inline-flex text-[10px] py-0 px-1.5">v1.0</Badge>
            </div>
            <p className="text-[11px] text-stone-500 font-medium tracking-wide">Scan. Order. Enjoy.</p>
          </div>
        </Link>

        {/* Role-Specific Navigation Links */}
        <nav aria-label="Primary navigation" className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-600">
          <Link to="/" className="hover:text-slate-900 transition-colors">Platform Home</Link>
          {isAuthenticated ? (
            <>
              {isManagerOrAdmin && (
                <>
                  <Link to="/admin/menu" className="hover:text-slate-900 transition-colors font-semibold text-emerald-700">Menu Catalog</Link>
                  <Link to="/admin/promotions" className="hover:text-slate-900 transition-colors font-semibold text-emerald-700">Promotions</Link>
                </>
              )}
              {canViewWaiter && (
                <Link to="/waiter/dashboard" className="hover:text-slate-900 transition-colors font-semibold text-blue-700">Waiter Ops</Link>
              )}
              {canViewKitchen && (
                <Link to="/kitchen/kds" className="hover:text-slate-900 transition-colors font-semibold text-amber-700">Kitchen KDS</Link>
              )}
              {canViewFinance && (
                <Link to="/admin/finance" className="hover:text-slate-900 transition-colors font-semibold text-emerald-700">Finance Ops</Link>
              )}
              {isManagerOrAdmin && (
                <Link to="/admin/reports" className="hover:text-slate-900 transition-colors font-semibold text-emerald-700">Reports</Link>
              )}
            </>
          ) : (
            <>
              <Link to="/staff/login" className="hover:text-slate-900 transition-colors font-semibold text-emerald-700">Staff Login</Link>
              <a href="#experiences" className="hover:text-slate-900 transition-colors">5 Applications</a>
              <a href="#architecture" className="hover:text-slate-900 transition-colors">Architecture</a>
            </>
          )}
        </nav>

        {/* Actions & Health Telemetry Badge */}
        <div className="flex items-center gap-3">
          <div className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-stone-100 border border-stone-200/60 text-xs">
            <span className="relative flex h-2 w-2">
              <span className={apiOnline ? 'relative inline-flex rounded-full h-2 w-2 bg-emerald-500' : 'relative inline-flex rounded-full h-2 w-2 bg-amber-500'}></span>
            </span>
            <span className="text-stone-600 font-medium">API: {apiOnline === null ? 'Checking...' : apiOnline ? 'Healthy' : 'Preview Mode'}</span>
          </div>

          {isAuthenticated ? (
            <Button variant="outline" size="sm" onClick={() => logout()} aria-label="Sign out">
              <LogOut className="w-3.5 h-3.5 mr-1.5" />
              Sign Out
            </Button>
          ) : (
            <a href="#workflow">
              <Button variant="primary" size="sm" className="hidden sm:inline-flex">
                Explore Platform
                <ChevronRight className="w-4 h-4 ml-1" />
              </Button>
            </a>
          )}
        </div>
      </div>
    </header>
  );
};
