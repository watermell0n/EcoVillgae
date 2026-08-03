import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { AuthProvider } from "@/auth/AuthProvider";
import { ClassroomProvider } from "@/features/classroom/ClassroomProvider";
import { QueryProvider } from "@/providers/QueryProvider";

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryProvider>
          <AuthProvider>
            <ClassroomProvider>
              <StatusBar style="dark" />
              <Stack screenOptions={{ headerShown: false }} />
            </ClassroomProvider>
          </AuthProvider>
        </QueryProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
