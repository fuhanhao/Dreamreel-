"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { User } from "@dreamreel/shared-types";
import { fetchMe, login as apiLogin, register as apiRegister } from "@/lib/api";
import {
  clearAuthSession,
  getAuthToken,
  getAuthUser,
  isAdmin,
  setAuthSession,
} from "@/lib/auth";

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    const token = getAuthToken();
    if (!token) {
      setUser(null);
      return;
    }
    try {
      const res = await fetchMe();
      setUser(res.data);
      setAuthSession(token, res.data);
    } catch {
      clearAuthSession();
      setUser(null);
    }
  }, []);

  useEffect(() => {
    const token = getAuthToken();
    const cached = getAuthUser();
    const timer = window.setTimeout(() => {
      if (token && cached) {
        setUser(cached);
      } else if (cached && !token) {
        clearAuthSession();
        setUser(null);
      }
      void refreshUser().finally(() => setLoading(false));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [refreshUser]);

  useEffect(() => {
    const onUnauthorized = () => {
      clearAuthSession();
      setUser(null);
    };
    window.addEventListener("auth:unauthorized", onUnauthorized);
    return () => window.removeEventListener("auth:unauthorized", onUnauthorized);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiLogin({ email, password });
    setAuthSession(res.data.token, res.data.user);
    setUser(res.data.user);
  }, []);

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    const res = await apiRegister({ email, password, displayName });
    setAuthSession(res.data.token, res.data.user);
    setUser(res.data.user);
  }, []);

  const logout = useCallback(() => {
    clearAuthSession();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      login,
      register,
      logout,
      refreshUser,
      isAdmin: isAdmin(user),
    }),
    [user, loading, login, register, logout, refreshUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
