export type StudentIdentity = {
  classroomId: string;
  studentId: string;
  nickname: string;
  role: "STU";
};
export type StudentSession = StudentIdentity & {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
};

export type StudentLoginInput = {
  classroomCode: string;
  attendanceNumber: number;
  nickname: string;
  password: string;
  deviceId: string;
};
