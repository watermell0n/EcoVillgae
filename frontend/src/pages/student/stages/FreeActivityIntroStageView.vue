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
  id: GuidedActivityView
  icon: string
  title: string
  description: string
  points: string[]
}> = [
  { id: 'SHOP', icon: '🏪', title: '마을 상점', description: '필요한 물건을 고르고 가격·재고·남은 돈을 비교해요.', points: ['가격과 재고 확인', '구매 수량 확인', '구매 후 잔액 비교'] },
  { id: 'DEPOSIT', icon: '🏦', title: '예금', description: '일부 비빔을 은행에 맡기고 다음 날 받을 이자를 계산해요.', points: ['10비빔 단위 선택', '예상 이자 확인', '확정 후 취소 불가'] },
  { id: 'INVESTMENT', icon: '📈', title: '투자', description: '세 회사의 위험과 예상 결과를 비교하고 나누어 투자해요.', points: ['3개 회사 비교', '위험 수준 확인', '분산투자로 위험 줄이기'] },
  { id: 'KNOWLEDGE', icon: '📚', title: '금융지식', description: '수업에서 배운 소비·저축·투자 내용을 내 속도로 다시 확인해요.', points: ['학습 주제 선택', '핵심 내용 복습', '예시로 다시 확인'] },
  { id: 'WALLET', icon: '👛', title: '지갑', description: '현재 잔액과 예금·투자·구매 내역을 한곳에서 확인해요.', points: ['사용 가능 잔액', '총자산 확인', '최근 거래 확인'] },
]

const selected = computed(() => modules.find((module) => module.id === props.activeView) ?? null)

function select(view: GuidedActivityView) {
  if (props.teacherControl) emit('select', view)
}
</script>

<template>
  <div class="free-activity-intro" role="status" aria-live="polite">
    <header class="free-activity-intro__header">
      <div>
        <p class="eyebrow">{{ teacherControl ? '학생에게 보여 주는 화면' : '선생님과 함께 보는 화면' }}</p>
        <h1>자유 경제활동 이용 방법</h1>
        <p>{{ teacherControl ? '설명할 메뉴를 선택하면 학생 화면도 같은 내용으로 바뀝니다.' : '지금은 설명 시간이에요. 선생님이 활동을 열어 줄 때까지 화면을 함께 살펴보세요.' }}</p>
      </div>
      <span class="guided-lock-badge">🔒 학생 조작 잠금</span>
    </header>

    <nav class="guided-activity-tabs" aria-label="자유 경제활동 설명 메뉴">
      <button
        type="button"
        :class="{ 'is-active': activeView === 'HOME' }"
        :disabled="!teacherControl"
        @click="select('HOME')"
      >🏘️ 전체 안내</button>
      <button
        v-for="module in modules"
        :key="module.id"
        type="button"
        :class="{ 'is-active': activeView === module.id }"
        :disabled="!teacherControl"
        @click="select(module.id)"
      >{{ module.icon }} {{ module.title }}</button>
    </nav>

    <div v-if="!selected" class="free-activity-intro__map" aria-label="자유 경제활동 장소 안내">
      <article v-for="module in modules" :key="module.id">
        <span aria-hidden="true">{{ module.icon }}</span><strong>{{ module.title }}</strong><p>{{ module.description }}</p>
      </article>
    </div>

    <article v-else class="guided-activity-detail" :key="selected.id">
      <span class="guided-activity-detail__icon" aria-hidden="true">{{ selected.icon }}</span>
      <div><p class="eyebrow">교사 설명 화면</p><h2>{{ selected.title }}</h2><p>{{ selected.description }}</p></div>
      <ul><li v-for="point in selected.points" :key="point">✓ {{ point }}</li></ul>
    </article>

    <div class="free-activity-intro__locked">
      <span class="free-activity-intro__lock" aria-hidden="true">🔒</span>
      <div><strong>{{ teacherControl ? '설명이 끝나면 활동 시작을 눌러 주세요' : '학생 활동 잠금 중' }}</strong><p>{{ teacherControl ? '활동 시작 후 상점·예금·투자 등을 학생이 직접 조작할 수 있습니다.' : '선생님이 활동 시작을 누르면 자동으로 조작할 수 있어요.' }}</p></div>
      <span class="waiting-live-status"><span />실시간 연결됨</span>
    </div>
  </div>
</template>
