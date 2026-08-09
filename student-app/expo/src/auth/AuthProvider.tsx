import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from "react";

import {
  hydrateStudentIdentity,
  loginStudent,
  logoutStudent,
  refreshStudentIdentity,
  restoreStudentIdentity
} from "./api";
import { clearSession, getOrCreateDeviceId, saveSession, saveSessionIdentity } from "./token-vault";
import type { StudentIdentity } from "./types";

type LoginValues = {
  classroomCode: string;
  attendanceNumber: number;
  nickname: string;
  password: string;
};

type AuthContextValue = {
  identity: StudentIdentity | null;
  ready: boolean;
  login(values: LoginValues): Promise<void>;
  logout(): Promise<void>;
  refreshIdentity(): Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [identity, setIdentity] = useState<StudentIdentity | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    restoreStudentIdentity()
      .then(async (restored) => {
        setIdentity(restored);
        if (restored) {
          await saveSessionIdentity(restored);
        }
      })
      .catch(clearSession)
      .finally(() => setReady(true));
  }, []);

  const login = useCallback(async (values: LoginValues) => {
    const deviceId = await getOrCreateDeviceId();
    const session = await loginStudent({ ...values, deviceId });
    await saveSession(session);
    const sessionIdentity: StudentIdentity = {
      classroomId: session.classroomId,
      studentId: session.studentId,
      nickname: session.nickname,
      role: session.role
    };
    const hydrated = await hydrateStudentIdentity(sessionIdentity).catch(() => sessionIdentity);
    await saveSessionIdentity(hydrated);
    setIdentity(hydrated);
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutStudent();
    } finally {
      await clearSession();
      setIdentity(null);
    }
  }, []);

  const refreshIdentity = useCallback(async () => {
    if (!identity) return;
    const refreshed = await refreshStudentIdentity(identity);
    if (
      refreshed.nickname === identity.nickname
      && refreshed.studentId === identity.studentId
    ) return;
    await saveSessionIdentity(refreshed);
    setIdentity(refreshed);
  }, [identity]);

  const value = useMemo(
    () => ({ identity, ready, login, logout, refreshIdentity }),
    [identity, ready, login, logout, refreshIdentity]
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
