import { useQuery } from "@tanstack/react-query";
import { router, usePathname } from "expo-router";
import { createContext, useContext, useEffect, useMemo, type PropsWithChildren } from "react";

import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthProvider";
import { fetchClassroomAccess, hasModule, type ClassroomAccess, type ModuleGroup } from "./api";

type ClassroomContextValue = {
  access?: ClassroomAccess;
  error: Error | null;
  isLoading: boolean;
  isOffline: boolean;
  hasCachedData: boolean;
  enabled(group: ModuleGroup): boolean;
  refetch(): Promise<unknown>;
};

const ClassroomContext = createContext<ClassroomContextValue | null>(null);

const routeGroups: Record<string, ModuleGroup> = {
  "/wallet": "wallet",
  "/shop": "shop",
  "/bank": "bank",
  "/investment": "investment",
  "/trade": "trade",
  "/quiz": "quiz",
  "/classroom": "meeting",
  "/character": "character",
  "/paystub": "paystub"
};

export function ClassroomProvider({ children }: PropsWithChildren) {
  const { identity } = useAuth();
  const pathname = usePathname();
  const query = useQuery({
    queryKey: ["classroom-access", identity?.classroomId],
    queryFn: () => fetchClassroomAccess(identity!.classroomId),
    enabled: Boolean(identity?.classroomId),
    staleTime: 15_000,
    gcTime: 30 * 60_000,
    refetchOnMount: "always"
  });

  const value = useMemo<ClassroomContextValue>(() => ({
    access: query.data,
    error: query.error,
    isLoading: Boolean(identity) && query.isPending,
    isOffline: query.error instanceof ApiError && query.error.kind === "offline",
    hasCachedData: Boolean(query.data && query.error),
    enabled: (group) => hasModule(query.data, group),
    refetch: query.refetch
  }), [identity, query.data, query.error, query.isPending, query.refetch]);

  useEffect(() => {
    const group = routeGroups[pathname];
    if (!group || query.isPending || !query.data) return;
    if (!hasModule(query.data, group)) router.replace("/(tabs)");
  }, [pathname, query.data, query.isPending]);

  return <ClassroomContext.Provider value={value}>{children}</ClassroomContext.Provider>;
}

export function useClassroom() {
  const value = useContext(ClassroomContext);
  if (!value) throw new Error("useClassroom must be used inside ClassroomProvider");
  return value;
}
