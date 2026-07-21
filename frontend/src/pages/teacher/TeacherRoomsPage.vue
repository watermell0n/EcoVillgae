<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createTeacherRoom, deleteTeacherRoom } from '../../features/room/api/room'
import { getRoomStage } from '../../features/room/model/stages'
import { isUnauthorized, getApiErrorMessage } from '../../shared/api/errors'
import StatusBadge from '../../shared/components/StatusBadge.vue'
import { SERVICE_NAME } from '../../shared/config/brand'
import { copyText } from '../../shared/utils/clipboard'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const errorMessage = ref('')
const showCreateRoom = ref(false)
const roomName = ref('')
const expectedStudentCount = 24
const creatingRoom = ref(false)
const createErrorMessage = ref('')
const deletingRoomId = ref('')
const copiedJoinRoomId = ref('')
const noticeMessage = ref('')
const teacher = computed(() => authStore.teacher)

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
  return getRoomStage(stage)?.label ?? stage
}

function roomStatusLabel(status: string) {
  if (status === 'PREPARING') return '준비 중'
  if (status === 'COMPLETED') return '종료'
  if (status === 'ARCHIVED') return '보관'
  return '진행 중'
}

function roomStatusTone(status: string): 'success' | 'warning' | 'neutral' {
  if (status === 'PREPARING') return 'warning'
  if (status === 'COMPLETED' || status === 'ARCHIVED') return 'neutral'
  return 'success'
}

function openCreateRoom() {
  roomName.value = ''
  createErrorMessage.value = ''
  showCreateRoom.value = true
}

function closeCreateRoom() {
  if (creatingRoom.value) return
  showCreateRoom.value = false
}

async function submitCreateRoom() {
  if (creatingRoom.value) return
  creatingRoom.value = true
  createErrorMessage.value = ''
  try {
    const createdRoom = await createTeacherRoom(roomName.value, expectedStudentCount)
    await authStore.loadTeacher()
    showCreateRoom.value = false
    await router.push({
      name: 'teacher-room-control',
      params: { roomId: createdRoom.roomId },
    })
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    createErrorMessage.value = getApiErrorMessage(error, '수업 방을 만들지 못했습니다.')
  } finally {
    creatingRoom.value = false
  }
}

async function removeRoom(roomId: string, name: string) {
  if (deletingRoomId.value) return
  if (!window.confirm(`‘${name}’ 방을 학급 목록에서 삭제할까요?`)) return
  deletingRoomId.value = roomId
  errorMessage.value = ''
  try {
    await deleteTeacherRoom(roomId)
    if (authStore.teacher) {
      authStore.teacher.rooms = authStore.teacher.rooms.filter((room) => room.roomId !== roomId)
    }
    noticeMessage.value = `‘${name}’ 방을 학급 목록에서 삭제했습니다.`
    await authStore.loadTeacher()
  } catch (error) {
    if (isUnauthorized(error)) {
      await router.replace({ name: 'teacher-login' })
      return
    }
    errorMessage.value = getApiErrorMessage(error, '수업 방을 삭제하지 못했습니다.')
  } finally {
    deletingRoomId.value = ''
  }
}

async function copyStudentJoinLink(roomId: string) {
  const url = `${window.location.origin}/student/join?roomId=${encodeURIComponent(roomId)}`
  if (await copyText(url)) {
    copiedJoinRoomId.value = roomId
    noticeMessage.value = '학생 입장 주소를 복사했습니다. 학생들은 이 주소에서 자기 번호만 입력하면 됩니다.'
    errorMessage.value = ''
    return
  }
  copiedJoinRoomId.value = ''
  errorMessage.value = '학생 입장 주소를 복사하지 못했습니다. 다시 시도해 주세요.'
}
</script>

<template>
  <main class="dashboard-page teacher-rooms-page student-theme">
    <header class="dashboard-header">
      <div>
        <RouterLink class="brand-link" to="/">{{ SERVICE_NAME }}</RouterLink>
        <p v-if="teacher">{{ teacher.displayName }}의 수업 공간</p>
      </div>
      <StatusBadge label="교사 로그인됨" tone="success" />
    </header>

    <section class="dashboard-title-row">
      <div>
        <p class="eyebrow">나의 수업</p>
        <h1 class="page-title">방 목록</h1>
      </div>
      <button type="button" @click="openCreateRoom">
        새로운 방 만들기
      </button>
    </section>

    <p v-if="loading" class="state-message">방 목록을 불러오고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="noticeMessage" class="control-notice" role="status">{{ noticeMessage }}</p>

    <section v-else class="room-list" aria-label="수업 방 목록">
      <article v-for="room in teacher?.rooms" :key="room.roomId" class="room-card">
        <div class="room-card__topline">
          <StatusBadge :label="roomStatusLabel(room.status)" :tone="roomStatusTone(room.status)" />
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
        <div class="room-card__actions">
          <RouterLink
            class="button-link"
            :to="{ name: 'teacher-room-control', params: { roomId: room.roomId } }"
          >
            수업 계속하기
          </RouterLink>
          <button
            class="button-secondary room-join-link-button"
            type="button"
            @click="copyStudentJoinLink(room.roomId)"
          >{{ copiedJoinRoomId === room.roomId ? '입장 주소 복사됨' : '학생 입장 주소 복사' }}</button>
          <button
            class="button-secondary room-delete-button"
            type="button"
            :disabled="deletingRoomId === room.roomId"
            @click="removeRoom(room.roomId, room.name)"
          >{{ deletingRoomId === room.roomId ? '삭제 중…' : '방 삭제' }}</button>
        </div>
      </article>

      <p v-if="!teacher?.rooms.length" class="state-message">아직 생성된 방이 없습니다.</p>
    </section>

    <div v-if="showCreateRoom" class="modal-backdrop">
      <section class="confirm-modal room-create-modal" role="dialog" aria-modal="true" aria-labelledby="room-create-title">
        <p class="eyebrow">새로운 수업</p>
        <h2 id="room-create-title">수업 방을 만들어요.</h2>
          <p>24명의 학생 번호와 번호별 1인1역이 자동으로 생성됩니다.</p>
        <form class="form-stack" @submit.prevent="submitCreateRoom">
          <label>
            <span>방 이름</span>
            <input
              v-model.trim="roomName"
              name="roomName"
              maxlength="100"
              placeholder="예: 햇살반 경제교실"
              required
            />
          </label>
          <div class="fixed-student-count" role="status">
            <span>학생 인원</span>
            <strong>24명</strong>
            <small>등록된 1인1역 명단과 자동으로 연결됩니다.</small>
          </div>
          <p v-if="createErrorMessage" class="form-error" role="alert">{{ createErrorMessage }}</p>
          <div class="confirm-modal__actions">
            <button class="button-secondary" type="button" :disabled="creatingRoom" @click="closeCreateRoom">
              취소
            </button>
            <button type="submit" :disabled="creatingRoom">
              {{ creatingRoom ? '생성 중…' : '방 만들기' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </main>
</template>
