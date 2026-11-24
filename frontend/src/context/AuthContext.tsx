import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import {
  UserProfile,
  loginStaff as apiLoginStaff,
  logoutStaff as apiLogoutStaff,
  refreshAccessToken,
  setAccessToken,
  getAccessToken
} from '@/lib/authClient';

interface AuthContextType {
  user: UserProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (identifier: string, pass: string, tenantId?: string) => Promise<UserProfile>;
  logout: () => Promise<void>;
  hasRole: (role: string) => boolean;
  hasPermission: (perm: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Silent refresh on mount
  useEffect(() => {
    let mounted = true;
    refreshAccessToken()
      .then((data) => {
        if (mounted) {
          setUser(data.user);
        }
      })
      .catch(() => {
        // Unauthenticated or expired, clean memory
        if (mounted) {
          setUser(null);
          setAccessToken(null);
        }
      })
      .finally(() => {
        if (mounted) {
          setIsLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  const login = useCallback(async (identifier: string, pass: string, tenantId?: string): Promise<UserProfile> => {
    setIsLoading(true);
    try {
      const data = await apiLoginStaff(identifier, pass, tenantId);
      setUser(data.user);
      return data.user;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    setIsLoading(true);
    try {
      await apiLogoutStaff();
    } finally {
      setUser(null);
      setAccessToken(null);
      setIsLoading(false);
    }
  }, []);

  const hasRole = useCallback((role: string) => {
    return user?.role === role;
  }, [user]);

  const hasPermission = useCallback((perm: string) => {
    return user?.permissions.includes(perm) ?? false;
  }, [user]);

  const contextValue = useMemo<AuthContextType>(
    () => ({
      user,
      isAuthenticated: !!user && !!getAccessToken(),
      isLoading,
      login,
      logout,
      hasRole,
      hasPermission,
    }),
    [user, isLoading, login, logout, hasRole, hasPermission]
  );

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};