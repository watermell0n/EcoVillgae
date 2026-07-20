<script setup lang="ts">
import { computed } from 'vue'

import type { GuidedActivityView } from '../../../features/room/api/room'

const props = withDefaults(defineProps<{
  activeView?: GuidedActivityView
  teacherControl?: boolean
}>(), {
  activeView: 'HOME',
  teacherControl: false,
})

const emit = defineEmits<{ select: [view: GuidedActivityView] }>()

const modules: Array<{
  id: Exclude<GuidedActivityView, 'HOME'>
  icon: string
  title: string
  description: string
  points: string[]
}> = [
  {
    id: 'SHOP',
    icon: '🛒',
    title: '마을 상점',
    description: '필요한 물건을 고르고 가격과 남은 돈을 비교해요.',
    points: ['가격과 재고 확인', '구매 수량 확인', '구매 뒤 잔액 비교'],
  },
  {
    id: 'DEPOSIT',
    icon: '🏦',
    title: '예금',
    description: '일부 비빔을 맡기고 약속한 이자를 계산해요.',
    points: ['10비빔 단위 선택', '예상 이자 확인', '확정 전 금액 확인'],
  },
  {
    id: 'INVESTMENT',
    icon: '📈',
    title: '투자',
    description: '여러 투자처의 위험도와 투자 이야기를 살펴봐요.',
    points: ['투자처 비교', '위험도 확인', '여러 곳에 나누어 선택'],
  },
  {
    id: 'KNOWLEDGE',
    icon: '📚',
    title: '금융지식',
    description: '수업에서 배운 소비·저축·투자 내용을 다시 확인해요.',
    points: ['학습 주제 선택', '핵심 내용 복습', '예시 다시 확인'],
  },
  {
    id: 'WALLET',
    icon: '👛',
    title: '지갑',
    description: '현재 잔액과 예금·투자·구매 내역을 한곳에서 확인해요.',
    points: ['사용 가능 잔액', '총자산 확인', '최근 거래 확인'],
  },
]

const selected = computed(() => modules.find((module) => module.id === props.activeView) ?? null)

function select(view: GuidedActivityView) {
  if (props.teacherControl) emit('select', view)
}
</script>

<template>
  <div class="free-activity-stage-view free-activity-stage-view--locked" role="status" aria-live="polite">
    <div class="economy-sticky-controls">
      <header class="economy-header">
        <div>
          <p class="eyebrow">1일차 자유 경제활동</p>
          <h1>{{ selected?.title ?? '내 비빔을 어떻게 사용할까요?' }}</h1>
        </div>
        <div class="shop-wallet" aria-label="활동 시작 상태">
          <span>현재 상태</span>
          <strong>🔒 시작 전</strong>
        </div>
      </header>

      <nav class="economy-module-tabs" aria-label="자유 경제활동 메뉴">
        <button type="button" :class="{ 'is-active': activeView === 'HOME' }" :disabled="!teacherControl" @click="select('HOME')">홈</button>
        <button
          v-for="module in modules"
          :key="module.id"
          type="button"
          :class="{ 'is-active': activeView === module.id }"
          :disabled="!teacherControl"
          @click="select(module.id)"
        >{{ module.title }}</button>
      </nav>
    </div>

    <div class="free-activity-intro__locked">
      <span class="free-activity-intro__lock" aria-hidden="true">🔒</span>
      <div>
        <strong>{{ teacherControl ? '학생 화면을 함께 보며 활동을 설명해 주세요' : '선생님의 활동 설명을 듣는 중이에요' }}</strong>
        <p>상점·예금·투자 등 실제 활동 화면을 미리 보고 있어요. 선생님이 활동을 시작하면 학생 화면의 선택 버튼이 열립니다.</p>
      </div>
    </div>

    <section v-if="!selected" class="economy-home" aria-label="자유 경제활동 메뉴 미리보기">
      <div class="economy-activity-grid">
        <button
          v-for="module in modules"
          :key="module.id"
          type="button"
          :disabled="!teacherControl"
          @click="select(module.id)"
        >
          <span aria-hidden="true">{{ module.icon }}</span>
          <strong>{{ module.title }}</strong>
          <small>{{ module.description }}</small>
          <b>🔒 시작 전</b>
        </button>
      </div>
    </section>

    <article v-else class="guided-activity-detail locked-activity-preview" :key="selected.id">
      <span class="guided-activity-detail__icon" aria-hidden="true">{{ selected.icon }}</span>
      <div>
        <p class="eyebrow">활동 화면 미리보기</p>
        <h2>{{ selected.title }}</h2>
        <p>{{ selected.description }}</p>
      </div>
      <ul>
        <li v-for="point in selected.points" :key="point">✓ {{ point }}</li>
      </ul>
    </article>
  </div>
</template>
