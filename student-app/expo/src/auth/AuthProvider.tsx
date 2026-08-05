import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from "react";

import { hydrateStudentIdentity, loginStudent, logoutStudent, restoreStudentIdentity } from "./api";
import { clearSession, getOrCreateDeviceId, saveSession, saveSessionIdentity } from "./token-vault";
import type { StudentIdentity } from "./types";

type LoginValues = {
  classroomCode: string;
  nickname: string;
  password: string;
};

type AuthContextValue = {
  identity: StudentIdentity | null;
  ready: boolean;
  login(values: LoginValues): Promise<void>;
  logout(): Promise<void>;
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

  const value = useMemo(() => ({ identity, ready, login, logout }), [identity, ready, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
