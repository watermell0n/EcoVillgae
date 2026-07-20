<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getDay1Payslip, type Day1Payslip } from '../../../features/settlement/api/settlement'
import { getApiErrorMessage } from '../../../shared/api/errors'

const loading = ref(true)
const errorMessage = ref('')
const payslip = ref<Day1Payslip | null>(null)

onMounted(async () => {
  try {
    payslip.value = await getDay1Payslip()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '월급명세서를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}
</script>

<template>
  <div class="payslip-stage-view">
    <p v-if="loading" class="state-message">월급명세서를 불러오고 있습니다.</p>
    <div v-else-if="errorMessage" class="quiz-load-error" role="alert">
      <strong>명세서를 확인하지 못했어요.</strong>
      <p>{{ errorMessage }}</p>
    </div>
    <template v-else-if="payslip">
      <header class="payslip-header">
        <div><p class="eyebrow">1일차 월급명세서</p><h1>받은 돈과 세금을 확인해요</h1></div>
        <span>{{ payslip.participantNumber }}번 {{ payslip.nickname }}</span>
      </header>

      <div class="payslip-layout">
        <section class="payslip-sheet" aria-label="소득과 세금 내역">
          <div class="payslip-sheet__title">
            <span aria-hidden="true">💼</span>
            <strong>이코빌리지 급여 명세서</strong>
            <small>1일차 · 학급 경제 수업</small>
          </div>
          <div class="payslip-line"><span>1인1역 <small v-if="payslip.roleName">{{ payslip.roleName }}</small></span><strong>+{{ formatBibim(payslip.roleIncome) }}</strong></div>
          <div class="payslip-line"><span>퀴즈 참여수당</span><strong>+{{ formatBibim(payslip.participationReward) }}</strong></div>
          <div class="payslip-line"><span>퀴즈 정답수당 <small>{{ payslip.correctCount }}문제</small></span><strong>+{{ formatBibim(payslip.correctReward) }}</strong></div>
          <div class="payslip-line payslip-line--subtotal"><span>총소득</span><strong>{{ formatBibim(payslip.grossIncome) }}</strong></div>
          <div class="payslip-line payslip-line--tax"><span>소득세 <small>공동기금으로 이동해요</small></span><strong>−{{ formatBibim(payslip.incomeTax) }}</strong></div>
          <div class="payslip-line payslip-line--total"><span>내 지갑에 들어온 돈</span><strong>{{ formatBibim(payslip.netIncome) }}</strong></div>
        </section>

        <aside class="payslip-side">
          <section class="payslip-tax-guide">
            <span aria-hidden="true">🏛️</span>
            <div><strong>세금은 어디로 가나요?</strong><p>소득세는 우리 반 공동기금으로 모여요. 나중에 함께 투표해 사용할 곳을 정해요.</p></div>
          </section>
          <section class="payslip-wallet-card">
            <p>💰 내 지갑 상태</p>
            <span><small>이번에 받은 돈</small><strong>+{{ formatBibim(payslip.netIncome) }}</strong></span>
            <span><small>현재 지갑</small><strong>{{ formatBibim(payslip.walletBalance) }}</strong></span>
          </section>
          <p class="payslip-recorded">✓ 내 지갑 기록에 저장됐어요</p>
        </aside>
      </div>
    </template>
  </div>
</template>
