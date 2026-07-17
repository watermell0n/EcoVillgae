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
      <p class="eyebrow">학생용</p>
      <h1 id="student-join-title" class="page-title">우리 반 수업에 참여하기</h1>
      <p class="page-description">선생님에게 받은 나만의 참여 코드를 입력해 주세요.</p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>개인 참여 코드</span>
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
          {{ submitting ? '확인 중...' : '입장하기' }}
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
