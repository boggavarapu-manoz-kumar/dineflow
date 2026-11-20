import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

export const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-stone-50">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-emerald-600"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/admin/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
};

export const RoleGuard: React.FC<{
  allowedRoles: string[];
  children: React.ReactNode;
}> = ({ allowedRoles, children }) => {
  const { user } = useAuth();

  if (!user || !allowedRoles.includes(user.role)) {
    return (
      <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-4">
        <h2 className="text-2xl font-bold text-slate-900">403 - Access Denied</h2>
        <p className="text-sm text-stone-600 mt-2">
          Your account role ({user?.role || 'Guest'}) lacks the permissions required to view this operational portal.
        </p>
      </div>
    );
  }

  return <>{children}</>;
};