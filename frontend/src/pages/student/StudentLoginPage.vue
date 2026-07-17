<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getApiErrorMessage } from '../../shared/api/errors'
import { SERVICE_NAME } from '../../shared/config/brand'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const entry = computed(() => authStore.pendingStudentEntry)
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

onMounted(async () => {
  if (!entry.value || entry.value.requiredAction !== 'LOGIN') {
    await router.replace({ name: 'student-join' })
  }
})

async function submit() {
  submitting.value = true
  errorMessage.value = ''

  try {
    const student = await authStore.loginStudent(password.value)
    await router.push({ name: 'student-room', params: { roomId: student.roomId } })
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '로그인 중 오류가 발생했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page student-theme">
    <RouterLink class="brand-link" to="/student/join">{{ SERVICE_NAME }}</RouterLink>

    <section v-if="entry" class="auth-card" aria-labelledby="student-login-title">
      <p class="eyebrow">{{ entry.roomName }} · {{ entry.participantNumber }}번</p>
      <h1 id="student-login-title" class="page-title">다시 만나서 반가워요!</h1>
      <p class="page-description">{{ entry.participationCode }}에 설정한 비밀번호를 입력해 주세요.</p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>비밀번호</span>
          <input
            v-model="password"
            type="password"
            maxlength="30"
            autocomplete="current-password"
            required
          />
        </label>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button type="submit" :disabled="submitting">
          {{ submitting ? '로그인 중...' : '수업으로 돌아가기' }}
        </button>
      </form>

      <p class="helper-text">비밀번호를 잊었다면 선생님에게 알려 주세요.</p>
    </section>
  </main>
</template>
