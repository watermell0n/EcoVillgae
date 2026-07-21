<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getApiErrorMessage } from '../../shared/api/errors'
import { SERVICE_NAME } from '../../shared/config/brand'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const entry = computed(() => authStore.pendingStudentEntry)
const isPasswordReset = computed(() => entry.value?.requiredAction === 'RESET_PASSWORD')
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
  if (!entry.value || !['SETUP', 'RESET_PASSWORD'].includes(entry.value.requiredAction)) {
    await router.replace({ name: 'student-join' })
  }
})

async function submit() {
  errorMessage.value = ''
  const normalizedNickname = nickname.value.trim()
  password.value = removePasswordWhitespace(password.value)
  passwordConfirmation.value = removePasswordWhitespace(passwordConfirmation.value)
  if (!isPasswordReset.value && (normalizedNickname.length < 1 || normalizedNickname.length > 20)) {
    errorMessage.value = '이름은 앞뒤 공백을 빼고 1~20글자로 입력해 주세요. 한 글자 이름도 사용할 수 있어요.'
    return
  }
  if (password.value.length < 4) {
    errorMessage.value = '비밀번호는 공백을 제외하고 4자 이상 입력해 주세요.'
    return
  }
  if (password.value !== passwordConfirmation.value) {
    errorMessage.value = '두 비밀번호가 같지 않아요. 다시 확인해 주세요.'
    return
  }

  submitting.value = true
  try {
    const setupNickname = isPasswordReset.value ? (entry.value?.nickname ?? '') : normalizedNickname
    const student = await authStore.setupStudent(setupNickname, password.value)
    await router.push({ name: 'student-room', params: { roomId: student.roomId } })
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '계정 설정 중 문제가 생겼어요. 입력한 내용을 다시 확인해 주세요.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page student-theme">
    <span class="brand-link brand-link--static" aria-label="서비스 이름">{{ SERVICE_NAME }}</span>

    <section v-if="entry" class="auth-card" aria-labelledby="student-setup-title">
      <p class="eyebrow">{{ entry.roomName }} · {{ entry.participantNumber }}번</p>
      <h1 id="student-setup-title" class="page-title">
        {{ isPasswordReset ? '새 비밀번호를 정해요' : '마을에서 사용할 이름을 정해요' }}
      </h1>
      <p class="page-description">
        {{ isPasswordReset
          ? `${entry.nickname ?? '기존'} 이름은 그대로 사용하고 비밀번호만 새로 정해요.`
          : '친구들이 알아볼 이름과 나만의 비밀번호를 정하면 바로 시작할 수 있어요.' }}
      </p>

      <form class="form-stack" @submit.prevent="submit">
        <label v-if="!isPasswordReset">
          <span>마을에서 부를 이름</span>
          <input
            v-model="nickname"
            minlength="1"
            maxlength="20"
            placeholder="친구들이 알아볼 이름"
            required
          />
          <small class="field-help">앞뒤 공백을 빼고 1~20글자까지 쓸 수 있어요. 한 글자 이름도 괜찮아요.</small>
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
          <small class="field-help">공백 없이 4~30글자로 정해요.</small>
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
          {{ submitting ? '시작을 준비하고 있어요…' : isPasswordReset ? '새 비밀번호로 시작하기' : '마을 생활 시작하기' }}
        </button>
      </form>

      <p class="helper-text">비밀번호를 잊으면 선생님에게 초기화를 부탁하세요.</p>
    </section>
  </main>
</template>
