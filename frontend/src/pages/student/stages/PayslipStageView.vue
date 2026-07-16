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
        <div>
          <p class="eyebrow">1일차 월급명세서</p>
          <h1>{{ payslip.nickname }}님의 소득을 확인해요.</h1>
        </div>
        <span>{{ payslip.participantNumber }}번</span>
      </header>

      <section class="payslip-sheet" aria-label="소득과 세금 내역">
        <div class="payslip-line">
          <span>역할 소득</span>
          <strong>{{ formatBibim(payslip.roleIncome) }}</strong>
        </div>
        <div class="payslip-line">
          <span>퀴즈 참여수당</span>
          <strong>{{ formatBibim(payslip.participationReward) }}</strong>
        </div>
        <div class="payslip-line">
          <span>정답수당 <small>{{ payslip.correctCount }}문제 정답</small></span>
          <strong>{{ formatBibim(payslip.correctReward) }}</strong>
        </div>
        <div class="payslip-line payslip-line--subtotal">
          <span>총소득</span>
          <strong>{{ formatBibim(payslip.grossIncome) }}</strong>
        </div>
        <div class="payslip-line payslip-line--tax">
          <span>소득세</span>
          <strong>− {{ formatBibim(payslip.incomeTax) }}</strong>
        </div>
        <div class="payslip-line payslip-line--total">
          <span>실수령액</span>
          <strong>{{ formatBibim(payslip.netIncome) }}</strong>
        </div>
      </section>

      <div class="payslip-balance">
        <span>현재 지갑</span>
        <strong>{{ formatBibim(payslip.walletBalance) }}</strong>
      </div>
      <p class="payslip-explanation">
        <strong>총소득</strong>은 일과 퀴즈로 번 돈을 모두 더한 금액이고,
        <strong>실수령액</strong>은 총소득에서 공동체를 위한 소득세를 뺀 금액이에요.
      </p>
    </template>
  </div>
</template>
