<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getApiErrorMessage } from '../../shared/api/errors'
import { SERVICE_NAME } from '../../shared/config/brand'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const entry = computed(() => authStore.pendingStudentEntry)
const nickname = ref('')
const password = ref('')
const passwordConfirmation = ref('')
const submitting = ref(false)
const errorMessage = ref('')

function removePasswordWhitespace(value: string) {
  return value.replace(/\s/g, '')
}

function sanitizePasswordInput(event: Event) {
  const input = event.target as HTMLInputElement
  const sanitizedPassword = removePasswordWhitespace(input.value)
  input.value = sanitizedPassword
  return sanitizedPassword
}

function handlePasswordInput(event: Event) {
  password.value = sanitizePasswordInput(event)
}

function handlePasswordConfirmationInput(event: Event) {
  passwordConfirmation.value = sanitizePasswordInput(event)
}

function preventSpaceInput(event: KeyboardEvent) {
  if (event.key === ' ') event.preventDefault()
}

onMounted(async () => {
  if (!entry.value || entry.value.requiredAction !== 'SETUP') {
    await router.replace({ name: 'student-join' })
  }
})

async function submit() {
  errorMessage.value = ''
  password.value = removePasswordWhitespace(password.value)
  passwordConfirmation.value = removePasswordWhitespace(passwordConfirmation.value)
  if (password.value.length < 4) {
    errorMessage.value = '비밀번호는 공백을 제외하고 4자 이상 입력해 주세요.'
    return
  }
  if (password.value !== passwordConfirmation.value) {
    errorMessage.value = '비밀번호 확인이 일치하지 않습니다.'
    return
  }

  submitting.value = true
  try {
    const student = await authStore.setupStudent(nickname.value, password.value)
    await router.push({ name: 'student-room', params: { roomId: student.roomId } })
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '계정 설정 중 오류가 발생했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page student-theme">
    <RouterLink class="brand-link" to="/student/join">{{ SERVICE_NAME }}</RouterLink>

    <section v-if="entry" class="auth-card" aria-labelledby="student-setup-title">
      <p class="eyebrow">{{ entry.roomName }} · {{ entry.participantNumber }}번</p>
      <h1 id="student-setup-title" class="page-title">마을에서 사용할 이름을 정해요</h1>
      <p class="page-description">닉네임과 나만의 비밀 번호를 정하면 마을 활동을 시작할 수 있어요.</p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>마을에서 부를 이름</span>
          <input v-model.trim="nickname" maxlength="20" placeholder="친구들이 알아볼 이름" required />
        </label>
        <label>
          <span>비밀번호</span>
          <input
            :value="password"
            type="password"
            minlength="4"
            maxlength="30"
            autocomplete="new-password"
            required
            @input="handlePasswordInput"
            @keydown="preventSpaceInput"
          />
        </label>
        <label>
          <span>비밀번호 확인</span>
          <input
            :value="passwordConfirmation"
            type="password"
            minlength="4"
            maxlength="30"
            autocomplete="new-password"
            required
            @input="handlePasswordConfirmationInput"
            @keydown="preventSpaceInput"
          />
        </label>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button type="submit" :disabled="submitting">
          {{ submitting ? '마을을 준비하는 중...' : '마을 생활 시작하기' }}
        </button>
      </form>

      <p class="helper-text">비밀번호를 잊으면 선생님에게 초기화를 요청해 주세요.</p>
    </section>
  </main>
</template>
