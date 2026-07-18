<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  getStudentFinalResult,
  type StudentFinalResult,
} from '../../../features/day2/api/finalResult'
import { getApiErrorMessage } from '../../../shared/api/errors'
import { useRoomSessionStore } from '../../../stores/roomSession'

const roomSessionStore = useRoomSessionStore()
const result = ref<StudentFinalResult | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const currentStage = computed(() => roomSessionStore.studentState?.effectiveStage
  ?? roomSessionStore.studentState?.currentStage)
const surveyAvailable = computed(() => ['DAY2_SURVEY', 'DAY2_COMPLETED'].includes(currentStage.value ?? ''))

onMounted(async () => {
  try {
    result.value = await getStudentFinalResult()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '최종 결과를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

function formatBibim(amount: number) {
  const sign = amount > 0 ? '+' : ''
  return `${sign}${amount.toLocaleString('ko-KR')}비빔`
}
</script>

<template>
  <div class="student-final-result">
    <p v-if="loading" class="state-message">2일 동안의 경제활동을 정리하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <template v-else-if="result">
      <section class="final-result-hero" aria-labelledby="student-final-title">
        <p class="eyebrow">나의 최종 경제 명세서</p>
        <h1 id="student-final-title" class="page-title">최종 지갑 {{ formatBibim(result.finalWalletBalance) }}</h1>
        <p class="page-description">수입부터 세금, 금융 활동, 소비까지 모두 실제 원장 기록으로 계산했어요.</p>
      </section>

      <dl class="final-result-ledger">
        <div><dt>총소득</dt><dd>{{ formatBibim(result.grossIncome) }}</dd></div>
        <div><dt>소득세</dt><dd>−{{ Math.abs(result.incomeTax).toLocaleString('ko-KR') }}비빔</dd></div>
        <div><dt>예금 이자</dt><dd>{{ formatBibim(result.depositInterest) }}</dd></div>
        <div><dt>투자 손익</dt><dd>{{ formatBibim(result.investmentProfit) }}</dd></div>
        <div><dt>금융세</dt><dd>−{{ Math.abs(result.financialTax).toLocaleString('ko-KR') }}비빔</dd></div>
        <div><dt>2일차 퀴즈 보상</dt><dd>{{ formatBibim(result.day2QuizReward) }}</dd></div>
        <div><dt>총소비</dt><dd>−{{ result.totalPurchaseAmount.toLocaleString('ko-KR') }}비빔</dd></div>
      </dl>

      <section class="final-purchase-list">
        <h2>내가 구매한 상품</h2>
        <ul v-if="result.purchases.length">
          <li v-for="purchase in result.purchases" :key="purchase.productId">
            <span>{{ purchase.productName }} × {{ purchase.quantity }}</span>
            <strong>{{ purchase.totalPrice.toLocaleString('ko-KR') }}비빔</strong>
          </li>
        </ul>
        <p v-else>구매한 상품이 없습니다.</p>
      </section>

      <section v-if="result.voteResult" class="final-vote-result">
        <p class="eyebrow">우리 반 공동기금 선택</p>
        <h2>{{ result.voteResult.title }}</h2>
        <p>{{ result.voteResult.description }}</p>
        <strong>{{ result.voteResult.voteCount }} / {{ result.voteResult.submittedCount }}표</strong>
      </section>

      <a
        v-if="surveyAvailable"
        class="survey-link-button"
        :href="result.surveyUrl"
        target="_blank"
        rel="noopener noreferrer"
      >마무리 설문 새 탭에서 열기 ↗</a>
      <p v-if="surveyAvailable" class="control-caution">설문 응답 내용은 이코빌리지 서비스에 저장되지 않습니다.</p>
      <aside v-else class="survey-wait-notice" role="status">
        <span aria-hidden="true">⏳</span>
        <p><strong>선생님이 마무리 설문을 열어 줄 때까지 기다려 주세요.</strong> 설문 단계가 시작되면 이 화면에 참여 버튼이 나타납니다.</p>
      </aside>
    </template>
  </div>
</template>
