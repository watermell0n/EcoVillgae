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
  studentId: string;
  role: "STU";
};

export function createStudentSession(payload: StudentLoginPayload) {
  return apiRequest<StudentSession>("/auth/student-sessions", {
    method: "POST",
    body: JSON.stringify({
      classroomCode: payload.classroomCode,
      attendanceNumber: payload.attendanceNumber,
      nickname: payload.nickname,
      password: payload.password,
      deviceId: payload.deviceId
    })
  });
}
