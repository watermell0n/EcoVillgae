<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { isUnauthorized, getApiErrorMessage } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const errorMessage = ref('')
const teacher = computed(() => authStore.teacher)

const stageLabels: Record<string, string> = {
  DAY1_LOBBY: '1일차 입장 대기',
  DAY1_QUIZ: '1일차 월급 퀴즈',
  DAY1_PAYSLIP_GUIDED: '월급명세서 교육',
  DAY1_FREE_ACTIVITY: '자유 경제활동',
}

onMounted(async () => {
  try {
    await authStore.loadTeacher()
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '방 목록을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

function stageLabel(stage: string) {
  return stageLabels[stage] ?? stage
}
</script>

<template>
  <main class="dashboard-page">
    <header class="dashboard-header">
      <div>
        <RouterLink class="brand-link" to="/">비빔</RouterLink>
        <p v-if="teacher">{{ teacher.displayName }}의 수업 공간</p>
      </div>
      <StatusBadge label="교사 로그인됨" tone="success" />
    </header>

    <section class="dashboard-title-row">
      <div>
        <p class="eyebrow">나의 수업</p>
        <h1 class="page-title">방 목록</h1>
      </div>
      <button type="button" disabled title="다음 개발 단계에서 연결됩니다.">
        새로운 방 만들기
      </button>
    </section>

    <p v-if="loading" class="state-message">방 목록을 불러오고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <section v-else class="room-list" aria-label="수업 방 목록">
      <article v-for="room in teacher?.rooms" :key="room.roomId" class="room-card">
        <div class="room-card__topline">
          <StatusBadge label="진행 중" tone="success" />
          <span>{{ room.currentDay }}일차</span>
        </div>
        <h2>{{ room.name }}</h2>
        <dl class="room-stats">
          <div>
            <dt>접속 학생</dt>
            <dd>{{ room.connectedStudentCount }} / {{ room.expectedStudentCount }}</dd>
          </div>
          <div>
            <dt>현재 단계</dt>
            <dd>{{ stageLabel(room.currentStage) }}</dd>
          </div>
        </dl>
        <RouterLink
          class="button-link"
          :to="{ name: 'teacher-room-control', params: { roomId: room.roomId } }"
        >
          수업 계속하기
        </RouterLink>
      </article>

      <p v-if="!teacher?.rooms.length" class="state-message">아직 생성된 방이 없습니다.</p>
    </section>
  </main>
</template>
