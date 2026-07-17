<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '../../stores/auth'
import { getApiErrorMessage } from '../../shared/api/errors'
import { SERVICE_NAME } from '../../shared/config/brand'

const router = useRouter()
const authStore = useAuthStore()
const loginId = ref('teacher')
const password = ref('teacher1234')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  submitting.value = true
  errorMessage.value = ''

  try {
    await authStore.loginTeacher(loginId.value, password.value)
    await router.push({ name: 'teacher-rooms' })
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '로그인 중 오류가 발생했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <RouterLink class="brand-link" to="/">{{ SERVICE_NAME }}</RouterLink>

    <section class="auth-card" aria-labelledby="teacher-login-title">
      <p class="eyebrow">교사용</p>
      <h1 id="teacher-login-title" class="page-title">교사 로그인</h1>
      <p class="page-description">수업 방과 학생 접속 현황을 관리합니다.</p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>아이디</span>
          <input v-model.trim="loginId" name="loginId" autocomplete="username" required />
        </label>

        <label>
          <span>비밀번호</span>
          <input
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
          />
        </label>

        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

        <button type="submit" :disabled="submitting">
          {{ submitting ? '로그인 중...' : '로그인' }}
        </button>
      </form>

      <div class="demo-hint">
        <strong>로컬 데모 계정</strong>
        <span>teacher / teacher1234</span>
      </div>
    </section>
  </main>
</template>
