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

function studentNumberCode(participantNumber: number) {
  return String(participantNumber).padStart(2, '0')
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
          <p class="eyebrow">학생 입장 지원</p>
          <h2 id="participation-code-dialog-title">학생 번호</h2>
        </div>
        <button class="button-secondary" type="button" :disabled="loading" @click="emit('refresh')">
          {{ loading ? '새로고침 중…' : '현황 새로고침' }}
        </button>
      </div>
      <p class="control-caution">학생 입장 주소와 함께 아래 두 자리 번호를 전달해 주세요. 처음 입장할 때와 재접속할 때 같은 번호를 사용합니다.</p>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-else-if="loading && !students.length" class="state-message">학생별 코드를 불러오고 있습니다.</p>
      <div v-else class="participation-code-list" role="table" aria-label="학생별 두 자리 번호">
        <div class="participation-code-list__head" role="row">
          <span>번호</span>
          <span>학생 번호</span>
          <span>닉네임</span>
          <span>상태</span>
        </div>
        <div v-for="student in students" :key="student.studentId" role="row">
          <strong>{{ student.participantNumber }}번</strong>
          <button
            type="button"
            class="participation-code-button"
            :class="{ 'is-copied': copiedCode === studentNumberCode(student.participantNumber) }"
            :aria-label="`${studentNumberCode(student.participantNumber)} 학생 번호 복사`"
            @click="emit('copy', studentNumberCode(student.participantNumber))"
          >
            <span>{{ studentNumberCode(student.participantNumber) }}</span>
            <small>{{ copiedCode === studentNumberCode(student.participantNumber) ? '복사됨' : '복사' }}</small>
          </button>
          <span>{{ student.nickname ?? '미설정' }}</span>
          <StatusBadge
            :label="studentStatusLabel(student)"
            :tone="studentStatusTone(student)"
          />
        </div>
      </div>
      <div class="confirm-modal__actions participation-code-dialog__actions">
        <button class="button-secondary" type="button" @click="emit('close')">닫기</button>
      </div>
    </section>
  </div>
</template>
