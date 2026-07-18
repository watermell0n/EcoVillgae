<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

import {
  approveStudentJoin,
  getTeacherStudentFlows,
  reopenStudentActivity,
  type TeacherFlowList,
  type TeacherStudentFlow,
  waiveStudentActivity,
} from '../../features/flow/api/teacherFlow'
import { getRoomStage } from '../../features/room/model/stages'
import { getApiErrorMessage } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'

const props = defineProps<{ roomId: string }>()
const data = ref<TeacherFlowList | null>(null)
const loading = ref(true)
const actingStudentId = ref('')
const errorMessage = ref('')
const noticeMessage = ref('')
const reasons = ref<Record<string, string>>({})
let timer: ReturnType<typeof setInterval> | undefined

const attentionStudents = computed(() =>
  data.value?.students.filter((student) =>
    student.flow && student.flow.flowMode !== 'LIVE' && student.flow.flowMode !== 'LOCKED',
  ) ?? [],
)

async function refresh() {
  try {
    data.value = await getTeacherStudentFlows(props.roomId)
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '학생별 진행 상태를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function approve(student: TeacherStudentFlow) {
  const succeeded = await run(student, () => approveStudentJoin(props.roomId, student.studentId))
  if (succeeded) {
    noticeMessage.value = `${student.participantNumber}번 학생을 현재 수업 화면으로 이동시켰습니다.`
  }
}

async function waive(student: TeacherStudentFlow) {
  if (!student.flow) return
  const reason = reasons.value[student.studentId]?.trim()
  if (!reason) {
    errorMessage.value = '면제 사유를 먼저 입력해 주세요.'
    return
  }
  await run(student, () => waiveStudentActivity(
    props.roomId,
    student.studentId,
    student.flow!.effectiveStage,
    reason,
  ))
}

async function reopen(student: TeacherStudentFlow) {
  if (!student.flow) return
  const reason = reasons.value[student.studentId]?.trim()
  if (!reason) {
    errorMessage.value = '복구 사유를 먼저 입력해 주세요.'
    return
  }
  await run(student, () => reopenStudentActivity(
    props.roomId,
    student.studentId,
    student.flow!.effectiveStage,
    reason,
  ))
}

async function run(student: TeacherStudentFlow, action: () => Promise<unknown>) {
  actingStudentId.value = student.studentId
  errorMessage.value = ''
  noticeMessage.value = ''
  try {
    await action()
    reasons.value[student.studentId] = ''
    await refresh()
    return true
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '학생 진행 상태를 변경하지 못했습니다.')
    return false
  } finally {
    actingStudentId.value = ''
  }
}

function tone(mode: string) {
  if (mode === 'AWAITING_JOIN') return 'success'
  if (mode === 'CATCH_UP') return 'warning'
  return 'neutral'
}

function modeLabel(mode: string, joinStatus: string) {
  if (joinStatus === 'APPROVED_PENDING_BOUNDARY') return '승인 완료 · 현재 단계 대기'
  return {
    CATCH_UP: '보충 중',
    AWAITING_JOIN: '합류 승인 대기',
    LOCKED: '종료',
    LIVE: '정상 참여',
  }[mode] ?? mode
}

onMounted(() => {
  void refresh()
  timer = setInterval(() => void refresh(), 5_000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <section class="student-flow-panel" aria-labelledby="student-flow-title">
    <div class="panel-heading">
      <p class="eyebrow">개인 진행 관리</p>
      <h2 id="student-flow-title">늦은 학생과 재접속 학생</h2>
    </div>
    <p v-if="loading" class="state-message">학생별 진행 상태를 확인하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>
    <p v-if="!loading && attentionStudents.length === 0" class="student-flow-empty">
      현재 별도 보충이나 합류 승인이 필요한 학생이 없습니다.
    </p>
    <div v-else class="student-flow-list">
      <article v-for="student in attentionStudents" :key="student.studentId" class="student-flow-item">
        <header>
          <div>
            <strong>{{ student.participantNumber }}번 {{ student.nickname ?? '미설정' }}</strong>
            <span>{{ student.online ? '접속 중' : '연결 끊김' }}</span>
          </div>
          <StatusBadge
            v-if="student.flow"
            :label="modeLabel(student.flow.flowMode, student.flow.joinStatus)"
            :tone="tone(student.flow.flowMode)"
          />
        </header>
        <template v-if="student.flow">
          <p>
            현재: {{ getRoomStage(student.flow.effectiveStage)?.label ?? student.flow.effectiveStage }}
            · {{ student.flow.completedCount }} / {{ student.flow.totalCount }} 완료
            · 보상 {{ student.flow.rewardRateBps / 100 }}%
          </p>
          <button
            v-if="student.flow.flowMode === 'AWAITING_JOIN' && student.flow.joinStatus !== 'APPROVED_PENDING_BOUNDARY'"
            type="button"
            :disabled="actingStudentId === student.studentId"
            @click="approve(student)"
          >
            현재 수업에 합류 승인
          </button>
          <p
            v-else-if="student.flow.joinStatus === 'APPROVED_PENDING_BOUNDARY'"
            class="student-flow-approved"
          >
            승인 완료 · 학생 화면은 현재 단계로 이동했으며 다음 안전 단계에서 자동 합류합니다.
          </p>
          <div v-else class="student-flow-override">
            <input
              v-model="reasons[student.studentId]"
              type="text"
              maxlength="200"
              placeholder="기술 문제 또는 면제 사유"
            >
            <button
              class="button-secondary"
              type="button"
              :disabled="actingStudentId === student.studentId"
              @click="reopen(student)"
            >
              100% 복구
            </button>
            <button
              class="button-secondary"
              type="button"
              :disabled="actingStudentId === student.studentId"
              @click="waive(student)"
            >
              이 활동 면제
            </button>
          </div>
        </template>
      </article>
    </div>
  </section>
</template>
