<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { getApiErrorMessage } from '../../shared/api/errors'
import { DEMO_PARTICIPATION_CODES, SERVICE_NAME } from '../../shared/config/brand'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const participationCode = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const examples = DEMO_PARTICIPATION_CODES

async function submit() {
  submitting.value = true
  errorMessage.value = ''

  try {
    const entry = await authStore.enterStudent(participationCode.value)
    if (entry.requiredAction === 'SETUP') {
      await router.push({ name: 'student-setup' })
      return
    }
    if (entry.requiredAction === 'LOGIN') {
      await router.push({ name: 'student-login' })
      return
    }
    if (entry.requiredAction === 'ROOM_CLOSED') {
      errorMessage.value = '이미 종료된 수업입니다.'
      return
    }
    errorMessage.value = '선생님이 수업을 준비하고 있습니다. 잠시 후 다시 시도해 주세요.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '참여 코드를 확인하지 못했습니다.')
  } finally {
    submitting.value = false
  }
}

function useExample(code: string) {
  participationCode.value = code
  errorMessage.value = ''
}
</script>

<template>
  <main class="auth-page student-theme">
    <RouterLink class="brand-link" to="/">{{ SERVICE_NAME }}</RouterLink>

    <section class="auth-card" aria-labelledby="student-join-title">
      <p class="student-auth-mark" aria-hidden="true">🏘️</p>
      <h1 id="student-join-title" class="page-title">이코빌리지 마을에 입장하기</h1>
      <p class="page-description">우리 반 경제생활이 시작되는 곳이에요.</p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>선생님이 알려준 입장 코드</span>
          <input
            v-model.trim="participationCode"
            class="code-input"
            name="participationCode"
            autocomplete="off"
            placeholder="예: ECO-01"
            maxlength="20"
            required
          />
        </label>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

        <button type="submit" :disabled="submitting">
          {{ submitting ? '마을 문을 여는 중...' : '마을 입장하기' }}
        </button>
      </form>

      <div class="demo-hint">
        <strong>데모 참여 코드</strong>
        <div class="code-examples">
          <button v-for="code in examples" :key="code" type="button" @click="useExample(code)">
            {{ code }}
          </button>
        </div>
      </div>
    </section>
  </main>
</template>
