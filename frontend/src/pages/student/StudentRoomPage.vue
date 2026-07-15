<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const errorMessage = ref('')
const student = computed(() => authStore.student)

const stageLabels: Record<string, string> = {
  DAY1_LOBBY: '입장 대기',
  DAY1_QUIZ: '월급 퀴즈',
  DAY1_PAYSLIP_GUIDED: '월급명세서',
  DAY1_FREE_ACTIVITY: '자유 경제활동',
}

onMounted(async () => {
  try {
    const loadedStudent = await authStore.loadStudent()
    if (route.params.roomId !== loadedStudent.roomId) {
      await router.replace({ name: 'student-room', params: { roomId: loadedStudent.roomId } })
    }
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'student-join' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '수업 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

function stageLabel(stage?: string) {
  if (!stage) return '대기 중'
  return stageLabels[stage] ?? stage
}
</script>

<template>
  <main class="student-room-page">
    <p v-if="loading" class="state-message">수업에 연결하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="student">
      <header class="student-room-header">
        <RouterLink class="brand-link" to="/">비빔</RouterLink>
        <div class="student-room-header__info">
          <span>{{ student.currentDay }}일차</span>
          <strong>{{ student.nickname }}</strong>
          <span>{{ student.walletBalance }}비빔</span>
          <StatusBadge label="연결됨" tone="success" />
        </div>
      </header>

      <section class="waiting-card" aria-labelledby="waiting-title">
        <div class="waiting-card__icon" aria-hidden="true">⏳</div>
        <p class="eyebrow">현재 활동 · {{ stageLabel(student.currentStage) }}</p>
        <h1 id="waiting-title" class="page-title">선생님이 다음 활동을 준비하고 있어요.</h1>
        <p class="page-description">
          오늘은 퀴즈로 소득을 얻고, 월급명세서를 확인한 뒤 직접 소비 활동을 해볼 거예요.
        </p>
        <div class="waiting-room-name">{{ student.roomName }} · {{ student.participantNumber }}번</div>
      </section>
    </template>
  </main>
</template>
