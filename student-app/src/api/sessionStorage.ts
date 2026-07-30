import type { StudentSession } from "./auth";

const DEVICE_ID_KEY = "ecovillage.student.deviceId";
const SESSION_KEY = "ecovillage.student.session";

export function getDeviceId() {
  const saved = localStorage.getItem(DEVICE_ID_KEY);
  if (saved) {
    return saved;
  }

  const next = crypto.randomUUID();
  localStorage.setItem(DEVICE_ID_KEY, next);
  return next;
}

export function saveStudentSession(session: StudentSession) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function loadStudentSession(): StudentSession | null {
  const saved = localStorage.getItem(SESSION_KEY);
  if (!saved) {
    return null;
  }

  try {
    return JSON.parse(saved) as StudentSession;
  } catch {
    localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function clearStudentSession() {
  localStorage.removeItem(SESSION_KEY);
}
