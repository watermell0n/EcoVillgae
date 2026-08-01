import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from "react";

import { loginStudent, restoreStudentIdentity } from "./api";
import { clearSession, getOrCreateDeviceId, saveSession } from "./token-vault";
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
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [identity, setIdentity] = useState<StudentIdentity | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    restoreStudentIdentity()
      .then(setIdentity)
      .catch(clearSession)
      .finally(() => setReady(true));
  }, []);

  const login = useCallback(async (values: LoginValues) => {
    const deviceId = await getOrCreateDeviceId();
    const session = await loginStudent({ ...values, deviceId });
    await saveSession(session);
    setIdentity({
      classroomId: session.classroomId,
      studentId: session.studentId,
      nickname: session.nickname,
      role: session.role
    });
  }, []);

  const logout = useCallback(async () => {
    await clearSession();
    setIdentity(null);
  }, []);

  const value = useMemo(() => ({ identity, ready, login, logout }), [identity, ready, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
