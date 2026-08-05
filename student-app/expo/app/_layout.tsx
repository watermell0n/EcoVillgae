import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { AuthProvider } from "@/auth/AuthProvider";
import { ClassroomProvider } from "@/features/classroom/ClassroomProvider";
import { StudentRealtimeProvider } from "@/features/student/StudentRealtimeProvider";
import { TradeProvider } from "@/features/trade/TradeProvider";
import { QueryProvider } from "@/providers/QueryProvider";

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryProvider>
          <AuthProvider>
            <StudentRealtimeProvider>
              <ClassroomProvider>
                <TradeProvider>
                  <StatusBar style="dark" />
                  <Stack screenOptions={{ headerShown: false }} />
                </TradeProvider>
              </ClassroomProvider>
            </StudentRealtimeProvider>
          </AuthProvider>
        </QueryProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
