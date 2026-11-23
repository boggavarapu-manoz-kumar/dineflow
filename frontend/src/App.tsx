import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { Navbar } from '@/components/layout/Navbar';
import { Footer } from '@/components/layout/Footer';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { ProtectedRoute, RoleGuard } from '@/components/guards/AuthGuards';

// Route-level code splitting: keeps initial customer QR & staff bundles small & fast
const LandingPage = lazy(() => import('@/pages/LandingPage').then(m => ({ default: m.LandingPage })));
const StaffLoginPage = lazy(() => import('@/pages/auth/StaffLoginPage').then(m => ({ default: m.StaffLoginPage })));
const SuperAdminLoginPage = lazy(() => import('@/pages/auth/SuperAdminLoginPage').then(m => ({ default: m.SuperAdminLoginPage })));
const CustomerEntryPage = lazy(() => import('@/pages/customer/CustomerEntryPage').then(m => ({ default: m.CustomerEntryPage })));
const CustomerMenuPage = lazy(() => import('@/pages/customer/CustomerMenuPage').then(m => ({ default: m.CustomerMenuPage })));
const CustomerOrderTrackingPage = lazy(() => import('@/pages/customer/CustomerOrderTrackingPage').then(m => ({ default: m.CustomerOrderTrackingPage })));
const MenuManagementPage = lazy(() => import('@/pages/staff/MenuManagementPage').then(m => ({ default: m.MenuManagementPage })));
const PromotionManagementPage = lazy(() => import('@/pages/staff/PromotionManagementPage').then(m => ({ default: m.PromotionManagementPage })));
const FinancialDashboardPage = lazy(() => import('@/pages/staff/FinancialDashboardPage').then(m => ({ default: m.FinancialDashboardPage })));
const FinancialAdminDashboardPage = lazy(() => import('@/pages/staff/FinancialAdminDashboardPage').then(m => ({ default: m.FinancialAdminDashboardPage })));
const WaiterDashboardPage = lazy(() => import('@/pages/staff/WaiterDashboardPage').then(m => ({ default: m.WaiterDashboardPage })));
const KitchenDisplayPage = lazy(() => import('@/pages/staff/KitchenDisplayPage').then(m => ({ default: m.KitchenDisplayPage })));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

const RouteLoader: React.FC = () => (
  <div className="min-h-[50vh] flex items-center justify-center text-sm text-stone-500 font-medium">
    Loading DineFlow workspace...
  </div>
);

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <div className="min-h-screen flex flex-col bg-stone-50 text-slate-900 selection:bg-emerald-600 selection:text-white">
            <Navbar />
            <main className="flex-1">
              <ErrorBoundary>
                <Suspense fallback={<RouteLoader />}>
                  <Routes>
                  {/* Public Marketing & Demo Views */}
                  <Route path="/" element={<LandingPage />} />

                  {/* Authentication Portals */}
                  <Route path="/admin/login" element={<StaffLoginPage />} />
                  <Route path="/staff/login" element={<StaffLoginPage />} />
                  <Route path="/super-admin/login" element={<SuperAdminLoginPage />} />

                  {/* Customer Table QR Entry & Digital Menu */}
                  <Route path="/customer/entry" element={<CustomerEntryPage />} />
                  <Route path="/customer/menu" element={<CustomerMenuPage />} />
                  <Route path="/customer/orders/:orderId" element={<CustomerOrderTrackingPage />} />

                  {/* Protected Staff Operations */}
                  <Route
                    path="/admin/dashboard"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER']}>
                          <MenuManagementPage />
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/admin/menu"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER']}>
                          <MenuManagementPage />
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/admin/promotions"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER']}>
                          <PromotionManagementPage />
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/admin/reports"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER']}>
                          <FinancialDashboardPage />
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="/admin/finance"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['SUPER_ADMIN', 'HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER', 'CASHIER', 'WAITER']}>
                          <FinancialAdminDashboardPage />
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />

                  {/* Waitstaff Operations */}
                  <Route
                    path="/waiter/dashboard"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['WAITER', 'BRANCH_MANAGER', 'HOTEL_ADMIN', 'HOTEL_OWNER']}>
                          <WaiterDashboardPage />
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />

                  {/* Kitchen Display System (KDS) */}
                  <Route
                    path="/kitchen/kds"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['KITCHEN_STAFF', 'BRANCH_MANAGER', 'HOTEL_ADMIN', 'HOTEL_OWNER']}>
                          <KitchenDisplayPage />
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />

                  <Route
                    path="/super-admin/dashboard"
                    element={
                      <ProtectedRoute>
                        <RoleGuard allowedRoles={['SUPER_ADMIN']}>
                          <div className="max-w-7xl mx-auto px-4 py-12">
                            <h1 className="text-3xl font-bold text-white bg-slate-950 p-6 rounded-2xl">
                              DineFlow Super Admin Portal
                            </h1>
                          </div>
                        </RoleGuard>
                      </ProtectedRoute>
                    }
                  />

                  <Route path="*" element={<NotFoundPage />} />
                  </Routes>
                </Suspense>
              </ErrorBoundary>
            </main>
            <Footer />
          </div>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;
