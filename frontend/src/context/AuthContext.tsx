import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import * as authApi from '../api/auth';
import { setAuthToken, setUnauthorizedHandler } from '../api/request';

interface User {
  id: string;
  username: string;
  roles: string[];
}

interface AuthContextValue {
  token: string | null;
  isInitializing: boolean;
  user: User | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

function decodeUser(token: string): User {
  const payload = JSON.parse(atob(token.split('.')[1]));
  return { id: payload.sub, username: payload.username, roles: payload.roles ?? [] };
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const initialized = useRef(false);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;

    setUnauthorizedHandler(() => {
      setToken(null);
      setUser(null);
      setAuthToken(null);
      localStorage.removeItem('refreshToken');
    });

    const storedRefresh = localStorage.getItem('refreshToken');
    if (!storedRefresh) {
      Promise.resolve().then(() => setIsInitializing(false));
      return;
    }

    authApi
      .refresh(storedRefresh)
      .then(({ accessToken, refreshToken }) => {
        setAuthToken(accessToken);
        setToken(accessToken);
        setUser(decodeUser(accessToken));
        localStorage.setItem('refreshToken', refreshToken);
      })
      .catch(() => {
        localStorage.removeItem('refreshToken');
      })
      .finally(() => {
        setIsInitializing(false);
      });
  }, []);

  const login = async (username: string, password: string) => {
    const { accessToken, refreshToken } = await authApi.login(username, password);
    setAuthToken(accessToken);
    setToken(accessToken);
    setUser(decodeUser(accessToken));
    localStorage.setItem('refreshToken', refreshToken);
  };

  const logout = async () => {
    const refreshToken = localStorage.getItem('refreshToken') ?? '';
    try {
      await authApi.logout(refreshToken);
    } finally {
      setAuthToken(null);
      setToken(null);
      setUser(null);
      localStorage.removeItem('refreshToken');
    }
  };

  return (
    <AuthContext.Provider value={{ token, isInitializing, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
