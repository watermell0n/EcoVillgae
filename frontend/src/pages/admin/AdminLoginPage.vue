<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { getApiErrorMessage } from '../../shared/api/errors'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loginId = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await authStore.loginAdmin(loginId.value, password.value)
    await router.push({ name: 'admin-dashboard' })
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '관리자 로그인 중 오류가 발생했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page admin-theme">
    <RouterLink class="brand-link" to="/">비빔</RouterLink>

    <section class="auth-card" aria-labelledby="admin-login-title">
      <p class="eyebrow">운영 관리자</p>
      <h1 id="admin-login-title" class="page-title">관리자 로그인</h1>
      <p class="page-description">수업 연결 상태와 학생 활동 이력을 확인합니다.</p>

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
          {{ submitting ? '로그인 중…' : '관리자 콘솔 열기' }}
        </button>
      </form>
    </section>
  </main>
</template>
