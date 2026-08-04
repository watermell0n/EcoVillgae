export type StudentIdentity = {
  classroomId: string;
  studentId: string;
  nickname: string;
  role: "STU";
  classroomLabel?: string;
  currencyName?: string;
  startScreen?: string;
};
export type StudentSession = StudentIdentity & {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
};

export type StudentLoginInput = {
  classroomCode: string;
  nickname: string;
  password: string;
  deviceId: string;
};
