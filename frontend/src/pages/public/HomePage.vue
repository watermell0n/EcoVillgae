<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getHealth } from '../../shared/api/health'
import StatusBadge from '../../shared/components/StatusBadge.vue'

type ConnectionState = 'checking' | 'connected' | 'failed'

const connectionState = ref<ConnectionState>('checking')
const errorMessage = ref('')

async function checkBackend() {
  connectionState.value = 'checking'
  errorMessage.value = ''

  try {
    await getHealth()
    connectionState.value = 'connected'
  } catch {
    connectionState.value = 'failed'
    errorMessage.value = '백엔드에 연결할 수 없습니다. Docker Compose 실행 상태를 확인해 주세요.'
  }
}

onMounted(checkBackend)
</script>

<template>
  <main class="home-page">
    <section class="hero-card">
      <div class="hero-card__content">
        <p class="eyebrow">교실 경제활동 서비스</p>
        <h1>비빔으로 배우는<br />우리 반 경제생활</h1>
        <p class="hero-card__description">
          교사가 수업 단계를 진행하면 학생 화면이 함께 바뀌는 2일 과정 경제교육 서비스입니다.
        </p>

        <StatusBadge
          v-if="connectionState === 'connected'"
          label="백엔드 연결됨"
          tone="success"
        />
        <StatusBadge
          v-else-if="connectionState === 'checking'"
          label="연결 확인 중"
          tone="warning"
        />
        <StatusBadge v-else label="연결 실패" tone="danger" />

        <p v-if="errorMessage" class="connection-error">{{ errorMessage }}</p>
        <button v-if="connectionState === 'failed'" type="button" @click="checkBackend">
          다시 확인
        </button>
      </div>

      <div class="money-illustration" aria-hidden="true">
        <span class="money-illustration__symbol">비빔</span>
        <span class="money-illustration__coin money-illustration__coin--one">₿</span>
        <span class="money-illustration__coin money-illustration__coin--two">₿</span>
      </div>
    </section>

    <section class="role-grid" aria-label="서비스 역할">
      <article>
        <span>학생</span>
        <h2>경제활동 체험</h2>
        <p>퀴즈, 예금, 투자와 소비를 직접 경험합니다.</p>
      </article>
      <article>
        <span>교사</span>
        <h2>수업 흐름 제어</h2>
        <p>현재 단계와 학생 화면을 한 대시보드에서 관리합니다.</p>
      </article>
      <article>
        <span>관리자</span>
        <h2>운영 상태 확인</h2>
        <p>학급 진행 상태와 정산·오류 로그를 확인합니다.</p>
      </article>
    </section>
  </main>
</template>
