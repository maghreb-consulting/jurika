import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

const ACCESS_KEY = "jurika.accessToken";
const REFRESH_KEY = "jurika.refreshToken";
const USER_KEY = "jurika.user";
const WORKSPACE_KEY = "jurika.workspaceCode";

// SecureStore is not available on web - fallback to localStorage
const webStorage = {
  getItem: async (key: string) =>
    typeof window !== "undefined" ? window.localStorage.getItem(key) : null,
  setItem: async (key: string, value: string) => {
    if (typeof window !== "undefined") window.localStorage.setItem(key, value);
  },
  removeItem: async (key: string) => {
    if (typeof window !== "undefined") window.localStorage.removeItem(key);
  },
};

async function getItem(key: string): Promise<string | null> {
  if (Platform.OS === "web") return webStorage.getItem(key);
  return SecureStore.getItemAsync(key);
}

async function setItem(key: string, value: string): Promise<void> {
  if (Platform.OS === "web") return webStorage.setItem(key, value);
  return SecureStore.setItemAsync(key, value);
}

async function removeItem(key: string): Promise<void> {
  if (Platform.OS === "web") return webStorage.removeItem(key);
  return SecureStore.deleteItemAsync(key);
}

export const secureStorage = {
  getAccessToken: () => getItem(ACCESS_KEY),
  setAccessToken: (token: string) => setItem(ACCESS_KEY, token),

  getRefreshToken: () => getItem(REFRESH_KEY),
  setRefreshToken: (token: string) => setItem(REFRESH_KEY, token),

  getWorkspaceCode: () => getItem(WORKSPACE_KEY),
  setWorkspaceCode: (code: string) => setItem(WORKSPACE_KEY, code),

  async getUser<T = unknown>(): Promise<T | null> {
    const raw = await getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return null;
    }
  },
  async setUser(user: unknown) {
    await setItem(USER_KEY, JSON.stringify(user));
  },

  async clear(): Promise<void> {
    await Promise.all([
      removeItem(ACCESS_KEY),
      removeItem(REFRESH_KEY),
      removeItem(USER_KEY),
    ]);
  },
};
