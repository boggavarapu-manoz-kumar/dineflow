import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { Navbar } from '@/components/layout/Navbar';
import { Footer } from '@/components/layout/Footer';
import { LandingPage } from '@/pages/LandingPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { StaffLoginPage } from '@/pages/auth/StaffLoginPage';
import { SuperAdminLoginPage } from '@/pages/auth/SuperAdminLoginPage';
import { CustomerEntryPage } from '@/pages/customer/CustomerEntryPage';
import { ProtectedRoute, RoleGuard } from '@/components/guards/AuthGuards';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <div className="min-h-screen flex flex-col bg-stone-50 text-slate-900 selection:bg-emerald-600 selection:text-white">
            <Navbar />
            <main className="flex-1">
              <Routes>
                {/* Public Marketing & Demo Views */}
                <Route path="/" element={<LandingPage />} />

                {/* Authentication Portals */}
                <Route path="/admin/login" element={<StaffLoginPage />} />
                <Route path="/staff/login" element={<StaffLoginPage />} />
                <Route path="/super-admin/login" element={<SuperAdminLoginPage />} />

                {/* Customer Table QR Entry */}
                <Route path="/customer/entry" element={<CustomerEntryPage />} />

                {/* Protected Staff Operations */}
                <Route
                  path="/admin/dashboard"
                  element={
                    <ProtectedRoute>
                      <RoleGuard allowedRoles={['HOTEL_OWNER', 'HOTEL_ADMIN', 'BRANCH_MANAGER']}>
                        <div className="max-w-7xl mx-auto px-4 py-12">
                          <h1 className="text-3xl font-bold">Hotel Operations Dashboard</h1>
                          <p className="text-stone-500 mt-2">Protected Hotel Administration Environment</p>
                        </div>
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
            </main>
            <Footer />
          </div>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;