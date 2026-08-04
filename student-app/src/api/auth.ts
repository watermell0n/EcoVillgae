import { apiRequest } from "./client";

export type StudentLoginPayload = {
  classroomCode: string;
  attendanceNumber: number;
  nickname: string;
  password: string;
  deviceId: string;
};

export type StudentSession = {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  classroomId: string;
  currencyName: string;
  walletBalance: number;
  studentId: string;
  role: "STU";
};

type StudentSessionResponse = {
  access_token: string;
  refresh_token: string;
  expires_at: string;
  classroom_id: string;
  currency_name: string;
  wallet_balance: number;
  student_id: string;
  role: "STU";
};

export async function createStudentSession(payload: StudentLoginPayload): Promise<StudentSession> {
  if (import.meta.env.VITE_USE_MOCK_STUDENT_LOGIN === "true") {
    return Promise.resolve({
      accessToken: "mock-access-token",
      refreshToken: "mock-refresh-token",
      expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
      classroomId: "mock-classroom",
      currencyName: import.meta.env.VITE_MOCK_CURRENCY_NAME ?? "비빔",
      walletBalance: 158,
      studentId: `mock-student-${payload.attendanceNumber}`,
      role: "STU" as const
    });
  }

  const session = await apiRequest<StudentSessionResponse>("/auth/student-sessions", {
    method: "POST",
    body: JSON.stringify({
      classroom_code: payload.classroomCode,
      attendance_number: payload.attendanceNumber,
      nickname: payload.nickname,
      password: payload.password,
      device_id: payload.deviceId
    })
  });

  return {
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    expiresAt: session.expires_at,
    classroomId: session.classroom_id,
    currencyName: session.currency_name,
    walletBalance: session.wallet_balance,
    studentId: session.student_id,
    role: session.role
  };
}
