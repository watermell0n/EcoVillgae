<script setup lang="ts">
import type { TeacherRoomStudent } from '../../features/room/api/room'
import StatusBadge from '../../shared/components/StatusBadge.vue'

defineProps<{
  students: TeacherRoomStudent[]
  loading: boolean
  errorMessage: string
  copiedCode: string | null
}>()

const emit = defineEmits<{
  close: []
  refresh: []
  copy: [code: string]
}>()

function studentStatusLabel(student: TeacherRoomStudent) {
  if (student.online) return '접속'
  if (student.status === 'ACTIVE') return '설정 완료'
  if (student.status === 'ABSENT') return '결석'
  return '입장 전'
}

function studentStatusTone(student: TeacherRoomStudent): 'success' | 'warning' | 'neutral' {
  if (student.online) return 'success'
  if (student.status === 'ACTIVE') return 'warning'
  return 'neutral'
}
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')">
    <section
      class="confirm-modal participation-code-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="participation-code-dialog-title"
    >
      <div class="room-preparation-heading">
        <div>
          <p class="eyebrow">학생 재접속 지원</p>
          <h2 id="participation-code-dialog-title">학생별 입장 코드</h2>
        </div>
        <button class="button-secondary" type="button" :disabled="loading" @click="emit('refresh')">
          {{ loading ? '새로고침 중…' : '현황 새로고침' }}
        </button>
      </div>
      <p class="control-caution">코드는 해당 학생에게만 전달해 주세요. 코드를 누르면 바로 복사됩니다.</p>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-else-if="loading && !students.length" class="state-message">학생별 코드를 불러오고 있습니다.</p>
      <div v-else class="participation-code-list" role="table" aria-label="학생별 입장 코드">
        <div class="participation-code-list__head" role="row">
          <span>번호</span>
          <span>개인 코드</span>
          <span>닉네임</span>
          <span>상태</span>
        </div>
        <div v-for="student in students" :key="student.studentId" role="row">
          <strong>{{ student.participantNumber }}번</strong>
          <button
            type="button"
            class="participation-code-button"
            :class="{ 'is-copied': copiedCode === student.participationCode }"
            :aria-label="`${student.participationCode} 입장 코드 복사`"
            @click="emit('copy', student.participationCode)"
          >
            <span>{{ student.participationCode }}</span>
            <small>{{ copiedCode === student.participationCode ? '복사됨' : '복사' }}</small>
          </button>
          <span>{{ student.nickname ?? '미설정' }}</span>
          <StatusBadge
            :label="studentStatusLabel(student)"
            :tone="studentStatusTone(student)"
          />
        </div>
      </div>
      <div class="confirm-modal__actions">
        <button class="button-secondary" type="button" @click="emit('close')">닫기</button>
      </div>
    </section>
  </div>
</template>
