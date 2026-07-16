<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { getApiErrorMessage } from '../../../shared/api/errors'
import { useDay2FinanceStore } from '../../../stores/day2Finance'
import { useRoomSessionStore } from '../../../stores/roomSession'

const financeStore = useDay2FinanceStore()
const roomSessionStore = useRoomSessionStore()
const errorMessage = ref('')
const result = computed(() => financeStore.studentResult)
const stage = computed(() => roomSessionStore.studentState?.currentStage)
const isCompleted = computed(() => (
  result.value?.settlementStatus === 'COMPLETED' && result.value.resultAvailable
))

const heading = computed(() => {
  if (stage.value === 'DAY2_LOGIN') return '1일차 자산을 다시 불러왔어요.'
  if (stage.value === 'DAY2_SETTLEMENT_WAIT') return '금융 결과를 계산할 준비가 됐어요.'
  if (stage.value === 'DAY2_FINANCE_SETTLEMENT') {
    return isCompleted.value ? '금융 정산이 완료됐어요.' : '선생님이 금융 결과를 정산하고 있어요.'
  }
  return '내 예금과 투자 결과를 확인해요.'
})

onMounted(() => void refresh())

watch(
  () => [
    roomSessionStore.studentState?.currentStage,
    roomSessionStore.studentState?.syncVersion,
  ] as const,
  (next, previous) => {
    if (previous && (next[0] !== previous[0] || next[1] !== previous[1])) void refresh()
  },
)

async function refresh() {
  try {
    errorMessage.value = ''
    await financeStore.refreshStudent()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '2일차 금융 정보를 불러오지 못했습니다.')
  }
}

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}

function formatRate(bps: number) {
  const rate = bps / 100
  return `${rate > 0 ? '+' : ''}${rate}%`
}
</script>

<template>
  <div class="day2-finance-view">
    <p v-if="financeStore.loading && !result" class="state-message">저장된 자산을 불러오고 있습니다.</p>
    <div v-else-if="errorMessage && !result" class="quiz-load-error" role="alert">
      <strong>2일차 금융 정보를 불러오지 못했어요.</strong>
      <p>{{ errorMessage }}</p>
      <button type="button" @click="refresh">다시 불러오기</button>
    </div>
    <template v-else-if="result">
      <header class="day2-finance-header">
        <div class="stage-view__icon stage-view__icon--green" aria-hidden="true">
          {{ isCompleted ? '✓' : '↻' }}
        </div>
        <div>
          <p class="eyebrow">{{ isCompleted ? '2일차 금융 정산 완료' : '2일차 자산 복구' }}</p>
          <h1>{{ heading }}</h1>
          <p v-if="stage === 'DAY2_LOGIN'">참여 코드와 비밀번호로 저장된 지갑·예금·투자를 안전하게 복구했습니다.</p>
          <p v-else-if="!isCompleted">정산 전에는 결과 금액을 공개하지 않아요. 선생님이 정산을 완료하면 확정된 결과를 확인할 수 있어요.</p>
          <p v-else>예금 이자와 투자 손익을 반영하고, 이자와 양수 투자 순이익에 금융세를 적용했습니다.</p>
        </div>
      </header>

      <section v-if="!isCompleted" class="stage-wait-panel" aria-live="polite">
        <div class="stage-view__icon stage-view__icon--orange" aria-hidden="true">⏳</div>
        <div>
          <h2>금융 정산을 기다리고 있어요.</h2>
          <p>정산이 끝나면 예금 이자, 투자 손익, 금융세가 반영된 확정 금액이 이 화면에 표시됩니다.</p>
        </div>
      </section>

      <template v-else>
        <section class="day2-balance-grid" aria-label="2일차 금융 자산 요약">
          <article><span>정산 전 지갑</span><strong>{{ formatBibim(result.walletBefore) }}</strong></article>
          <article><span>예금 원금</span><strong>{{ formatBibim(result.depositPrincipal) }}</strong><small>이자 +{{ formatBibim(result.depositInterest) }}</small></article>
          <article><span>투자 원금</span><strong>{{ formatBibim(result.investmentPrincipal) }}</strong><small :class="{ 'is-loss': result.investmentProfit < 0 }">손익 {{ result.investmentProfit > 0 ? '+' : '' }}{{ formatBibim(result.investmentProfit) }}</small></article>
          <article class="is-highlight"><span>정산 후 지갑</span><strong>{{ formatBibim(result.walletAfter) }}</strong><small>금융세 −{{ formatBibim(result.financialTax) }}</small></article>
        </section>

        <section class="day2-result-breakdown">
          <div><p class="eyebrow">예금 결과</p><h2>원금과 이자</h2></div>
          <dl>
            <div><dt>예금 원금</dt><dd>{{ formatBibim(result.depositPrincipal) }}</dd></div>
            <div><dt>받은 이자</dt><dd>+{{ formatBibim(result.depositInterest) }}</dd></div>
            <div><dt>과세 대상 수익</dt><dd>{{ formatBibim(result.taxableProfit) }}</dd></div>
            <div><dt>금융세</dt><dd>−{{ formatBibim(result.financialTax) }}</dd></div>
          </dl>
        </section>

        <section class="day2-result-breakdown">
          <div><p class="eyebrow">투자 결과</p><h2>투자처별 수익률과 손익</h2></div>
          <p v-if="result.allocations.length === 0" class="state-message">1일차에 확정한 투자가 없습니다.</p>
          <div v-else class="day2-allocation-list">
            <article v-for="allocation in result.allocations" :key="allocation.assetId">
              <div><strong>{{ allocation.assetName }}</strong><span>{{ allocation.riskLevel }} 위험</span></div>
              <dl>
                <div><dt>원금</dt><dd>{{ formatBibim(allocation.principal) }}</dd></div>
                <div><dt>수익률</dt><dd :class="{ 'is-loss': allocation.returnBps < 0 }">{{ formatRate(allocation.returnBps) }}</dd></div>
                <div><dt>손익</dt><dd :class="{ 'is-loss': allocation.profit < 0 }">{{ allocation.profit > 0 ? '+' : '' }}{{ formatBibim(allocation.profit) }}</dd></div>
                <div><dt>평가액</dt><dd>{{ formatBibim(allocation.finalValue) }}</dd></div>
              </dl>
            </article>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>
