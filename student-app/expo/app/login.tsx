import { zodResolver } from "@hookform/resolvers/zod";
import { Redirect, router } from "expo-router";
import { Controller, useForm, type Control } from "react-hook-form";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { z } from "zod";

import { useAuth } from "@/auth/AuthProvider";
import { colors } from "@/theme/tokens";

const loginSchema = z.object({
  classroomCode: z.string().trim().min(4, "학급 코드를 입력해 주세요."),
  nickname: z.string().trim().min(1, "닉네임을 입력해 주세요.").max(20),
  password: z.string().min(4, "비밀번호는 4자 이상이에요.")
});

type LoginFormInput = z.input<typeof loginSchema>;
type LoginFormOutput = z.output<typeof loginSchema>;

export default function LoginScreen() {
  const { identity, login } = useAuth();
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting }
  } = useForm<LoginFormInput, unknown, LoginFormOutput>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      classroomCode: "",
      nickname: "",
      password: ""
    }
  });

  if (identity) return <Redirect href="/(tabs)" />;

  const submit = handleSubmit(async (values) => {
    try {
      await login(values);
      router.replace("/(tabs)");
    } catch (error) {
      setError("root", {
        message: error instanceof Error ? error.message : "로그인하지 못했어요."
      });
    }
  });

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={styles.keyboard}
      >
        <ScrollView contentContainerStyle={styles.shell} keyboardShouldPersistTaps="handled">
          <Text style={[styles.floatingCoin, styles.coinLeft]}>💰</Text>
          <Text style={[styles.floatingCoin, styles.coinRight]}>🪙</Text>
          <View style={styles.formCard}>
            <Text style={styles.welcomeIcon}>🏘️</Text>
            <Text accessibilityRole="header" style={styles.formTitle}>이코빌리지</Text>
            <Text style={styles.description}>학급 코드로 계정을 만들거나 다시 로그인해요</Text>
            <Field control={control} error={errors.classroomCode?.message} label="학급 코드" name="classroomCode" />
            <Field control={control} error={errors.nickname?.message} label="닉네임" name="nickname" />
            <Field control={control} error={errors.password?.message} label="비밀번호" name="password" secureTextEntry />
            {errors.root?.message ? <Text style={styles.formError}>{errors.root.message}</Text> : null}
            <Pressable
              accessibilityRole="button"
              disabled={isSubmitting}
              onPress={submit}
              style={({ pressed }) => [styles.submit, pressed && styles.pressed, isSubmitting && styles.disabled]}
            >
              {isSubmitting ? <ActivityIndicator color={colors.white} /> : <Text style={styles.submitText}>계정 만들기 / 로그인</Text>}
            </Pressable>
            <Text style={styles.loginNote}>비밀번호를 잊었다면 선생님께 알려주세요.</Text>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

type FieldProps = {
  control: Control<LoginFormInput, unknown, LoginFormOutput>;
  error?: string;
  keyboardType?: "default" | "number-pad";
  label: string;
  name: keyof LoginFormInput;
  secureTextEntry?: boolean;
};

function Field({ control, error, keyboardType = "default", label, name, secureTextEntry }: FieldProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <Controller
        control={control}
        name={name}
        render={({ field: { onBlur, onChange, value } }) => (
          <TextInput
            accessibilityLabel={label}
            autoCapitalize="none"
            keyboardType={keyboardType}
            onBlur={onBlur}
            onChangeText={onChange}
            secureTextEntry={secureTextEntry}
            style={[styles.input, error ? styles.inputError : null]}
            value={String(value)}
          />
        )}
      />
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.sky100 },
  keyboard: { flex: 1 },
  shell: {
    position: "relative",
    flexGrow: 1,
    alignItems: "center",
    justifyContent: "center",
    overflow: "hidden",
    padding: 36,
    backgroundColor: colors.sky100
  },
  floatingCoin: { position: "absolute", zIndex: -1, fontSize: 36, opacity: 0.62 },
  coinLeft: { left: "9%", top: "16%" },
  coinRight: { right: "13%", top: "28%" },
  formCard: {
    width: "100%",
    maxWidth: 520,
    padding: 42,
    gap: 14,
    borderWidth: 1,
    borderColor: colors.lineSoft,
    borderRadius: 28,
    backgroundColor: "rgba(255,255,255,0.96)",
    shadowColor: colors.navy900,
    shadowOpacity: 0.14,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 12 },
    elevation: 5
  },
  welcomeIcon: { marginBottom: -2, fontSize: 52, textAlign: "center" },
  formTitle: { color: colors.ink900, fontSize: 42, fontWeight: "900", textAlign: "center" },
  description: { marginBottom: 4, color: colors.muted600, fontSize: 16, lineHeight: 24, fontWeight: "700", textAlign: "center" },
  field: { gap: 8 },
  label: { color: colors.ink900, fontWeight: "900", fontSize: 14 },
  input: {
    minHeight: 54,
    paddingHorizontal: 16,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: "#C9DFFD",
    color: colors.ink900,
    backgroundColor: colors.washBlue,
    fontSize: 17,
    fontWeight: "700"
  },
  inputError: { borderColor: colors.red500 },
  errorText: { color: colors.red500, fontSize: 12 },
  formError: {
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderWidth: 1,
    borderColor: "#FFD0C8",
    borderRadius: 10,
    color: colors.red500,
    backgroundColor: "#FFF1ED",
    fontWeight: "800"
  },
  submit: {
    minHeight: 54,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 12,
    backgroundColor: colors.orange500,
    marginTop: 4
  },
  submitText: { color: colors.white, fontSize: 17, fontWeight: "900" },
  loginNote: { color: colors.muted600, fontSize: 13, fontWeight: "700", textAlign: "center" },
  pressed: { opacity: 0.84 },
  disabled: { opacity: 0.55 }
});
