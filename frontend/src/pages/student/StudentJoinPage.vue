<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getApiErrorMessage } from '../../shared/api/errors'
import { SERVICE_NAME } from '../../shared/config/brand'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const studentEntry = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const roomId = computed(() => typeof route.query.roomId === 'string' ? route.query.roomId : undefined)
const usesStudentNumber = computed(() => Boolean(roomId.value))

async function submit() {
  submitting.value = true
  errorMessage.value = ''

  try {
    const entry = await authStore.enterStudent(studentEntry.value, roomId.value)
    if (entry.requiredAction === 'SETUP' || entry.requiredAction === 'RESET_PASSWORD') {
      await router.push({ name: 'student-setup' })
      return
    }
    if (entry.requiredAction === 'LOGIN') {
      await router.push({ name: 'student-login' })
      return
    }
    if (entry.requiredAction === 'ROOM_CLOSED') {
      errorMessage.value = '이미 종료된 수업이에요. 선생님에게 확인해 주세요.'
      return
    }
    errorMessage.value = '선생님이 수업을 준비하고 있어요. 잠시 뒤 다시 눌러 주세요.'
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      usesStudentNumber.value
        ? '학생 번호를 확인하지 못했어요. 자기 번호를 다시 입력해 주세요.'
        : '입장 코드를 확인하지 못했어요. 선생님에게 받은 코드를 다시 입력해 주세요.',
    )
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page student-theme">
    <span class="brand-link brand-link--static" aria-label="서비스 이름">{{ SERVICE_NAME }}</span>

    <section class="auth-card" aria-labelledby="student-join-title">
      <p class="student-auth-mark" aria-hidden="true">🏡</p>
      <h1 id="student-join-title" class="page-title">우리 반 경제 마을에 들어가요</h1>
      <p class="page-description">
        {{ usesStudentNumber
          ? '출석부에서 사용하는 자기 번호만 입력하면 돼요.'
          : '선생님에게 받은 교실 입장 주소를 열면 자기 번호로 더 간단히 들어갈 수 있어요.' }}
      </p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>{{ usesStudentNumber ? '내 학생 번호' : '학생 번호 또는 입장 코드' }}</span>
          <input
            v-model.trim="studentEntry"
            class="code-input"
            name="studentEntry"
            :inputmode="usesStudentNumber ? 'numeric' : 'text'"
            autocomplete="off"
            :placeholder="usesStudentNumber ? '예: 7' : '예: 7 또는 ECO-07'"
            maxlength="20"
            required
          />
          <small v-if="usesStudentNumber" class="field-help">1번은 1, 12번은 12처럼 숫자만 입력하세요.</small>
          <small v-else class="field-help">공용 시연 교실에서는 자기 번호만 입력해도 돼요.</small>
        </label>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

        <button type="submit" :disabled="submitting">
          {{ submitting ? '교실을 찾고 있어요…' : '교실 들어가기' }}
        </button>
      </form>
    </section>
  </main>
</template>
