import { randomUUID } from "expo-crypto";
import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

import type { StudentIdentity, StudentSession } from "./types";

const REFRESH_TOKEN_KEY = "ecovillage.student.refreshToken";
const IDENTITY_KEY = "ecovillage.student.identity";
const DEVICE_ID_KEY = "ecovillage.student.deviceId";

let accessToken: string | null = null;

// SecureStore is a native-only persistence layer. Keep the browser preview
// entirely in memory so it never falls back to localStorage for refresh tokens.
const webVault = new Map<string, string>();

async function getStoredValue(key: string) {
  if (Platform.OS === "web") return webVault.get(key) ?? null;
  return SecureStore.getItemAsync(key);
}

async function setStoredValue(key: string, value: string) {
  if (Platform.OS === "web") {
    webVault.set(key, value);
    return;
  }

  await SecureStore.setItemAsync(key, value);
}

async function deleteStoredValue(key: string) {
  if (Platform.OS === "web") {
    webVault.delete(key);
    return;
  }

  await SecureStore.deleteItemAsync(key);
}

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(nextAccessToken: string | null) {
  accessToken = nextAccessToken;
}

export async function getRefreshToken() {
  return getStoredValue(REFRESH_TOKEN_KEY);
}

export async function saveRefreshToken(refreshToken: string) {
  await setStoredValue(REFRESH_TOKEN_KEY, refreshToken);
}

export async function getStoredIdentity(): Promise<StudentIdentity | null> {
  const value = await getStoredValue(IDENTITY_KEY);
  if (!value) return null;

  try {
    return JSON.parse(value) as StudentIdentity;
  } catch {
    await deleteStoredValue(IDENTITY_KEY);
    return null;
  }
}

export async function saveSession(session: StudentSession) {
  setAccessToken(session.accessToken);
  const identity: StudentIdentity = {
    classroomId: session.classroomId,
    studentId: session.studentId,
    nickname: session.nickname,
    role: session.role
  };

  await Promise.all([
    saveRefreshToken(session.refreshToken),
    setStoredValue(IDENTITY_KEY, JSON.stringify(identity))
  ]);
}

export async function clearSession() {
  setAccessToken(null);
  await Promise.all([
    deleteStoredValue(REFRESH_TOKEN_KEY),
    deleteStoredValue(IDENTITY_KEY)
  ]);
}

export async function getOrCreateDeviceId() {
  const saved = await getStoredValue(DEVICE_ID_KEY);
  if (saved) return saved;

  const created = randomUUID();
  await setStoredValue(DEVICE_ID_KEY, created);
  return created;
}
