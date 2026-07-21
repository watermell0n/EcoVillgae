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

function removePasswordWhitespace(value: string) {
  return value.replace(/\s/g, '')
}

function handlePasswordInput(event: Event) {
  const input = event.target as HTMLInputElement
  const sanitizedPassword = removePasswordWhitespace(input.value)
  input.value = sanitizedPassword
  password.value = sanitizedPassword
}

function preventSpaceInput(event: KeyboardEvent) {
  if (event.key === ' ') event.preventDefault()
}

onMounted(async () => {
  if (!entry.value || entry.value.requiredAction !== 'LOGIN') {
    await router.replace({ name: 'student-join' })
  }
})

async function submit() {
  submitting.value = true
  errorMessage.value = ''
  password.value = removePasswordWhitespace(password.value)

  try {
    const student = await authStore.loginStudent(password.value)
    await router.push({ name: 'student-room', params: { roomId: student.roomId } })
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '비밀번호를 확인하지 못했어요. 다시 입력해 주세요.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page student-theme">
    <span class="brand-link brand-link--static" aria-label="서비스 이름">{{ SERVICE_NAME }}</span>

    <section v-if="entry" class="auth-card" aria-labelledby="student-login-title">
      <p class="eyebrow">{{ entry.roomName }} · {{ entry.participantNumber }}번</p>
      <h1 id="student-login-title" class="page-title">다시 만나서 반가워요!</h1>
      <p class="page-description">{{ entry.nickname ?? `${entry.participantNumber}번 학생` }}이 정한 비밀번호를 입력해 주세요.</p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>비밀번호</span>
          <input
            :value="password"
            type="password"
            maxlength="30"
            autocomplete="current-password"
            required
            @input="handlePasswordInput"
            @keydown="preventSpaceInput"
          />
        </label>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button type="submit" :disabled="submitting">
          {{ submitting ? '교실로 들어가고 있어요…' : '내 교실로 돌아가기' }}
        </button>
      </form>

      <p class="helper-text">비밀번호를 잊으면 선생님에게 비밀번호 초기화를 부탁하세요.</p>
    </section>
  </main>
</template>
