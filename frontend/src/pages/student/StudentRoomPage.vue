<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getRoomStage } from '../../features/room/model/stages'
import { getApiErrorMessage, isUnauthorized } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { useAuthStore } from '../../stores/auth'
import { useRoomSessionStore } from '../../stores/roomSession'
import { getStudentStageComponent, isPlaceholderStage } from './stages/stageComponents'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const errorMessage = ref('')
const student = computed(() => authStore.student)
const state = computed(() => roomSessionStore.studentState)
const stageDefinition = computed(() => getRoomStage(state.value?.currentStage))
const stageComponent = computed(() => getStudentStageComponent(state.value?.currentStage))
const stageComponentProps = computed(() =>
  isPlaceholderStage(state.value?.currentStage)
    ? { stageLabel: stageDefinition.value?.label ?? state.value?.currentStage ?? '대기 중' }
    : {},
)
const connectionLabel = computed(() =>
  roomSessionStore.connectionStatus === 'CONNECTED' ? '연결됨' : '다시 연결 중',
)
const connectionTone = computed(() =>
  roomSessionStore.connectionStatus === 'CONNECTED' ? 'success' : 'warning',
)

onMounted(async () => {
  try {
    const loadedStudent = await authStore.loadStudent()
    if (route.params.roomId !== loadedStudent.roomId) {
      await router.replace({ name: 'student-room', params: { roomId: loadedStudent.roomId } })
    }
    await roomSessionStore.refreshStudent(loadedStudent.syncVersion)
    roomSessionStore.startStudentPolling()
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

onUnmounted(() => {
  roomSessionStore.stopPolling()
})

watch(
  () => roomSessionStore.pollError,
  async (error) => {
    if (error && isUnauthorized(error)) {
      roomSessionStore.stopPolling()
      await router.replace({ name: 'student-join' })
    }
  },
)
</script>

<template>
  <main class="student-room-page">
    <p v-if="loading" class="state-message">수업에 연결하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="student && state">
      <header class="student-room-header">
        <RouterLink class="brand-link" to="/">비빔</RouterLink>
        <div class="student-room-header__info">
          <span>{{ state.currentDay }}일차</span>
          <strong>{{ student.nickname }}</strong>
          <span>{{ state.walletBalance }}비빔</span>
          <StatusBadge :label="connectionLabel" :tone="connectionTone" />
        </div>
      </header>

      <section class="student-stage-card" :aria-label="stageDefinition?.label ?? state.currentStage">
        <p class="student-stage-card__mode">
          {{ state.mode === 'FREE' ? '지금 자유롭게 이용할 수 있어요' : state.mode === 'CLOSED' ? '이 활동은 종료되었어요' : '선생님과 함께 보는 중' }}
        </p>
        <component :is="stageComponent" v-bind="stageComponentProps" />
        <div class="waiting-room-name">
          {{ student.roomName }} · {{ student.participantNumber }}번 · sync {{ state.syncVersion }}
        </div>
      </section>
    </template>
  </main>
</template>
