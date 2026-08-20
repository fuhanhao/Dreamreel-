"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import type { Project } from "@dreamreel/shared-types";
import { fetchProjects } from "@/lib/api";
import { useAuth } from "@/components/auth/auth-provider";

const STORAGE_KEY = "df-current-project-id";

interface CurrentProjectContextValue {
  projects: Project[];
  currentProject: Project | null;
  currentProjectId: string | null;
  loading: boolean;
  setCurrentProjectId: (id: string | null) => void;
  refreshProjects: () => Promise<void>;
}

const CurrentProjectContext = createContext<CurrentProjectContextValue | null>(null);

export function CurrentProjectProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [projects, setProjects] = useState<Project[]>([]);
  const [currentProjectId, setCurrentProjectIdState] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const refreshProjects = useCallback(async () => {
    if (!user) {
      setProjects([]);
      return;
    }
    setLoading(true);
    try {
      const res = await fetchProjects();
      const list = res.data ?? [];
      setProjects(list);
      setCurrentProjectIdState((prev) => {
        const stored =
          typeof window !== "undefined" ? window.localStorage.getItem(STORAGE_KEY) : null;
        const candidate = prev ?? stored;
        if (candidate && list.some((p) => p.id === candidate)) {
          return candidate;
        }
        return list[0]?.id ?? null;
      });
    } catch {
      setProjects([]);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void refreshProjects();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [refreshProjects]);

  const setCurrentProjectId = useCallback((id: string | null) => {
    setCurrentProjectIdState(id);
    if (typeof window !== "undefined") {
      if (id) window.localStorage.setItem(STORAGE_KEY, id);
      else window.localStorage.removeItem(STORAGE_KEY);
    }
  }, []);

  const currentProject = useMemo(
    () => projects.find((p) => p.id === currentProjectId) ?? null,
    [projects, currentProjectId],
  );

  const value = useMemo(
    () => ({
      projects,
      currentProject,
      currentProjectId,
      loading,
      setCurrentProjectId,
      refreshProjects,
    }),
    [projects, currentProject, currentProjectId, loading, setCurrentProjectId, refreshProjects],
  );

  return (
    <CurrentProjectContext.Provider value={value}>{children}</CurrentProjectContext.Provider>
  );
}

export function useCurrentProject() {
  const ctx = useContext(CurrentProjectContext);
  if (!ctx) {
    return {
      projects: [] as Project[],
      currentProject: null,
      currentProjectId: null,
      loading: false,
      setCurrentProjectId: (_id: string | null) => undefined,
      refreshProjects: async () => undefined,
    };
  }
  return ctx;
}
