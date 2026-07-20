<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { getApiErrorMessage } from '../../../shared/api/errors'
import { useDay2FinanceStore } from '../../../stores/day2Finance'
import { useRoomSessionStore } from '../../../stores/roomSession'

const financeStore = useDay2FinanceStore()
const roomSessionStore = useRoomSessionStore()
const errorMessage = ref('')
const result = computed(() => financeStore.studentResult)
const stage = computed(() => roomSessionStore.studentState?.effectiveStage
  ?? roomSessionStore.studentState?.currentStage)
const isCompleted = computed(() => (
  result.value?.settlementStatus === 'COMPLETED' && result.value.resultAvailable
))
const isGuidedLesson = computed(() => stage.value === 'DAY2_RESULT_GUIDED')
const investmentLesson = computed(() => {
  const profit = result.value?.investmentProfit ?? 0
  if (profit > 0) return '투자한 자산의 가격이 올라 원금보다 많은 돈을 돌려받았어요.'
  if (profit < 0) return '투자한 자산의 가격이 내려 원금보다 적은 돈을 돌려받았어요.'
  return '투자 가격의 변화가 결과 금액에 영향을 주지 않았어요.'
})

const heading = computed(() => {
  if (stage.value === 'DAY2_LOGIN') return '1일차 자산을 다시 불러왔어요.'
  if (stage.value === 'DAY2_SETTLEMENT_WAIT') return '금융 결과를 계산할 준비가 됐어요.'
  if (stage.value === 'DAY2_FINANCE_SETTLEMENT') {
    return isCompleted.value ? '금융 결과 확인이 준비됐어요.' : '선생님이 금융 결과를 계산하고 있어요.'
  }
  if (stage.value === 'DAY2_RESULT_GUIDED') return '숫자 뒤에 숨은 금융 원리를 알아봐요.'
  return '내 예금과 투자 결과를 확인해요.'
})

onMounted(() => void refresh())

watch(
  () => [
    roomSessionStore.studentState?.effectiveStage
      ?? roomSessionStore.studentState?.currentStage,
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

function riskLabel(riskLevel: string) {
  if (riskLevel === 'LOW') return '낮은 위험'
  if (riskLevel === 'MEDIUM') return '보통 위험'
  if (riskLevel === 'HIGH') return '높은 위험'
  return `${riskLevel} 위험`
}

function allocationExplanation(returnBps: number) {
  if (returnBps > 0) return '가격이 올라 수익이 생겼어요.'
  if (returnBps < 0) return '가격이 내려 손실이 생겼어요.'
  return '가격이 그대로여서 손익이 없어요.'
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
          <p class="eyebrow">{{ isGuidedLesson ? '2일차 금융 결과 교육' : isCompleted ? '2일차 금융 결과 확인' : '2일차 자산 복구' }}</p>
          <h1>{{ heading }}</h1>
          <p v-if="stage === 'DAY2_LOGIN'">참여 코드와 비밀번호로 저장된 지갑·예금·투자를 안전하게 복구했습니다.</p>
          <p v-else-if="!isCompleted">계산 전에는 결과 금액을 공개하지 않아요. 선생님이 계산을 끝내면 확정된 결과를 확인할 수 있어요.</p>
          <p v-else-if="isGuidedLesson">내 결과를 따라가며 예금, 투자, 금융세가 어떻게 지갑 금액을 바꾸었는지 알아보세요.</p>
          <p v-else>예금 이자와 투자 손익을 반영하고, 이자와 양수 투자 순이익에 금융세를 적용했습니다.</p>
        </div>
      </header>

      <section v-if="!isCompleted" class="stage-wait-panel" aria-live="polite">
        <div class="stage-view__icon stage-view__icon--orange" aria-hidden="true">⏳</div>
        <div>
          <h2>금융 결과를 계산하고 있어요.</h2>
          <p>계산이 끝나면 예금 이자, 투자 손익, 금융세가 반영된 확정 금액이 이 화면에 표시됩니다.</p>
        </div>
      </section>

      <template v-else>
        <section class="day2-balance-grid" aria-label="2일차 금융 자산 요약">
          <article><span>계산 전 지갑</span><strong>{{ formatBibim(result.walletBefore) }}</strong></article>
          <article><span>예금 원금</span><strong>{{ formatBibim(result.depositPrincipal) }}</strong><small>이자 +{{ formatBibim(result.depositInterest) }}</small></article>
          <article><span>투자 원금</span><strong>{{ formatBibim(result.investmentPrincipal) }}</strong><small :class="{ 'is-loss': result.investmentProfit < 0 }">손익 {{ result.investmentProfit > 0 ? '+' : '' }}{{ formatBibim(result.investmentProfit) }}</small></article>
          <article class="is-highlight"><span>계산 후 지갑</span><strong>{{ formatBibim(result.walletAfter) }}</strong><small>금융세 −{{ formatBibim(result.financialTax) }}</small></article>
        </section>

        <template v-if="isGuidedLesson">
          <section class="finance-learning-journey" aria-labelledby="finance-learning-title">
            <div class="finance-learning-journey__heading">
              <p class="eyebrow">내 결과로 배우기</p>
              <h2 id="finance-learning-title">내 돈은 왜 이렇게 달라졌을까요?</h2>
              <p>돈 계산 결과를 세 단계로 따라가면 금융 상품의 특징을 쉽게 이해할 수 있어요.</p>
            </div>
            <div class="finance-learning-steps">
              <article>
                <span class="finance-learning-steps__number">1</span>
                <div>
                  <p class="eyebrow">예금</p>
                  <h3>기다린 대가로 이자를 받아요.</h3>
                  <p>예금은 은행에 돈을 맡기고, 미리 약속한 이자를 받는 금융 상품이에요.</p>
                  <strong>{{ formatBibim(result.depositPrincipal) }} + {{ formatBibim(result.depositInterest) }} 이자</strong>
                </div>
              </article>
              <article>
                <span class="finance-learning-steps__number">2</span>
                <div>
                  <p class="eyebrow">투자</p>
                  <h3>가격이 움직이면 손익도 달라져요.</h3>
                  <p>{{ investmentLesson }}</p>
                  <strong>
                    {{ formatBibim(result.investmentPrincipal) }}
                    {{ result.investmentProfit >= 0 ? '+' : '−' }}
                    {{ formatBibim(Math.abs(result.investmentProfit)) }} = {{ formatBibim(result.investmentValue) }}
                  </strong>
                </div>
              </article>
              <article>
                <span class="finance-learning-steps__number">3</span>
                <div>
                  <p class="eyebrow">금융세</p>
                  <h3>수익의 일부는 공동체에 기여해요.</h3>
                  <p>예금 이자와 이익이 난 투자 수익을 합친 금액이 금융세를 계산하는 기준이에요.</p>
                  <strong>{{ formatBibim(result.taxableProfit) }} 수익 − {{ formatBibim(result.financialTax) }} 세금</strong>
                </div>
              </article>
            </div>
          </section>

          <section class="finance-investment-lesson">
            <div>
              <p class="eyebrow">투자 결과 자세히 보기</p>
              <h2>수익이 클 기회가 있으면 손실 위험도 있어요.</h2>
            </div>
            <p v-if="result.allocations.length === 0" class="state-message">
              이번에는 투자하지 않았어요. 투자하지 않으면 가격 변화에 따른 수익과 손실도 생기지 않아요.
            </p>
            <div v-else class="finance-investment-cards">
              <article v-for="allocation in result.allocations" :key="allocation.assetId">
                <div>
                  <strong>{{ allocation.assetName }}</strong>
                  <span>{{ riskLabel(allocation.riskLevel) }}</span>
                </div>
                <p>{{ allocationExplanation(allocation.returnBps) }}</p>
                <dl>
                  <div><dt>수익률</dt><dd :class="{ 'is-loss': allocation.returnBps < 0 }">{{ formatRate(allocation.returnBps) }}</dd></div>
                  <div><dt>내 손익</dt><dd :class="{ 'is-loss': allocation.profit < 0 }">{{ allocation.profit > 0 ? '+' : '' }}{{ formatBibim(allocation.profit) }}</dd></div>
                </dl>
              </article>
            </div>
          </section>

          <aside class="finance-learning-takeaway" aria-label="금융 결과 핵심 정리">
            <strong>오늘의 핵심</strong>
            <ul>
              <li>예금은 약속한 이자를 받기 때문에 결과를 비교적 쉽게 예상할 수 있어요.</li>
              <li>투자는 가격 변화에 따라 이익도, 손실도 생길 수 있어요.</li>
              <li>수익과 세금을 함께 생각해야 실제로 남는 돈을 알 수 있어요.</li>
            </ul>
          </aside>
        </template>

        <template v-else>
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
                <div><strong>{{ allocation.assetName }}</strong><span>{{ riskLabel(allocation.riskLevel) }}</span></div>
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
    </template>
  </div>
</template>
