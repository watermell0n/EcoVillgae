// 교사 웹 화면들이 공유하는 UI 타입입니다.
// 소유: 공용 — 교사 프런트와 AI 컴포넌트가 함께 쓰므로 변경 시 팀에 공유합니다.

import type { ModuleCode } from "./api";

export type LogTone = "success" | "warning" | "error";

export type StudentRow = {
  attendance: string;
  nickname: string;
  role: string;
  salary: string;
  status: string;
};

export type ModuleRow = {
  code: ModuleCode;
  name: string;
  status: string;
  studentState: string;
  teacherTodo: string;
  stateVersion: number;
  policyVersion: number | null;
};

export type ClassSettings = {
  classroomId: string;
  schoolId: string;
  stateVersion: number;
  className: string;
  schoolName: string;
  grade: string;
  classNumber: string;
  description: string;
  classCode: string;
  startDate: string;
  endDate: string;
  currency: string;
  period: string;
  studentCount: string;
  incomeTax: string;
};

export type RunAction = <T>(
  label: string,
  path: string,
  init: RequestInit,
  onSuccess: (data: T) => void
) => Promise<T | null>;
