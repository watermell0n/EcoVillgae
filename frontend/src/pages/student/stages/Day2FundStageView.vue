<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import {
  castStudentVote,
  getStudentCurrentVote,
  getStudentFund,
  type FundOverview,
  type VoteSession,
} from '../../../features/day2/api/fundVote'
import { getApiErrorMessage } from '../../../shared/api/errors'
import { useRoomSessionStore } from '../../../stores/roomSession'

const roomSessionStore = useRoomSessionStore()
const fund = ref<FundOverview | null>(null)
const vote = ref<VoteSession | null>(null)
const selectedOptionId = ref('')
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const currentStage = computed(() => roomSessionStore.studentState?.effectiveStage
  ?? roomSessionStore.studentState?.currentStage)
const decidedOption = computed(() => vote.value?.options.find((option) => option.decided) ?? null)
let pollingTimer: ReturnType<typeof setInterval> | undefined

async function refresh() {
  try {
    fund.value = await getStudentFund()
    if (['DAY2_FUND_VOTE', 'DAY2_FUND_RESULT'].includes(currentStage.value ?? '')) {
      vote.value = await getStudentCurrentVote()
      selectedOptionId.value = vote.value.selectedOptionId ?? selectedOptionId.value
    }
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '공동기금 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function submitVote() {
  if (!vote.value || !selectedOptionId.value || submitting.value) return
  submitting.value = true
  try {
    vote.value = await castStudentVote(vote.value.voteSessionId, selectedOptionId.value)
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '투표를 제출하지 못했습니다.')
  } finally {
    submitting.value = false
  }
}

watch(currentStage, () => void refresh(), { immediate: true })

onMounted(() => {
  pollingTimer = setInterval(() => {
    if (currentStage.value === 'DAY2_FUND_VOTE') void refresh()
  }, 2_000)
})

onUnmounted(() => {
  if (pollingTimer) clearInterval(pollingTimer)
})

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}
</script>

<template>
  <div class="student-fund-view">
    <p v-if="loading" class="state-message">공동기금 정보를 확인하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <template v-else-if="fund">
      <section
        v-if="currentStage === 'DAY2_FUND_RESULT' && vote"
        class="student-vote-card"
        aria-labelledby="student-vote-result-title"
      >
        <p class="eyebrow">우리 반 공동기금 투표 결과</p>
        <h1 id="student-vote-result-title" class="page-title">
          {{ decidedOption?.title ?? '최종 선택 결과' }}
        </h1>
        <p class="page-description">{{ vote.question }}</p>
        <p v-if="decidedOption" class="finance-confirmed">
          {{ decidedOption.voteCount }}표를 받아 우리 반의 선택으로 결정되었어요.
        </p>
        <div class="student-vote-options" aria-label="공동기금 투표 득표 결과">
          <article
            v-for="option in vote.options"
            :key="option.optionId"
            :class="{ 'is-decided': option.decided }"
          >
            <span>
              <strong>{{ option.title }}</strong>
              <small>{{ option.description }}</small>
              <em>필요 금액 {{ formatBibim(option.requiredAmount) }}</em>
              <b>{{ option.voteCount ?? 0 }}표</b>
            </span>
          </article>
        </div>
        <p class="control-caution">선생님과 투표 결과를 함께 확인한 뒤 최종 결과로 이동합니다.</p>
      </section>

      <template v-else>
      <section class="student-fund-balance" aria-labelledby="student-fund-title">
        <p class="eyebrow">우리 반 공동기금</p>
        <h1 id="student-fund-title" class="page-title">{{ formatBibim(fund.balance) }}</h1>
        <p class="page-description">
          소득세 {{ formatBibim(fund.totalIncomeTax) }}와 금융세 {{ formatBibim(fund.totalFinancialTax) }}가 모였어요.
        </p>
        <p v-if="fund.appliedExpense" class="control-caution">
          {{ fund.appliedExpense.name }} 비용으로 {{ formatBibim(fund.appliedExpense.amount) }}을 함께 사용했습니다.
        </p>
      </section>

      <section v-if="currentStage === 'DAY2_FUND_COST'" class="fund-wait-card">
        <strong>공동기금은 공동체를 위해 사용해요</strong>
        <p>선생님이 공통 비용을 적용하고 투표 선택지를 준비하고 있습니다.</p>
      </section>

      <section v-else-if="vote" class="student-vote-card" aria-labelledby="student-vote-title">
        <p class="eyebrow">익명 투표</p>
        <h2 id="student-vote-title">{{ vote.question }}</h2>
        <p>{{ vote.submittedCount }} / {{ vote.eligibleStudentCount }}명 참여</p>

        <div class="student-vote-options">
          <label
            v-for="option in vote.options"
            :key="option.optionId"
            :class="{ 'is-selected': selectedOptionId === option.optionId, 'is-decided': option.decided }"
          >
            <input
              v-if="vote.status === 'OPEN' && vote.eligible"
              v-model="selectedOptionId"
              type="radio"
              name="fund-vote"
              :value="option.optionId"
              :disabled="vote.status !== 'OPEN' || vote.hasVoted || !option.affordable"
            >
            <span>
              <strong>{{ option.title }}</strong>
              <small>{{ option.description }}</small>
              <em>필요 금액 {{ formatBibim(option.requiredAmount) }}</em>
              <b v-if="option.voteCount !== null">{{ option.voteCount }}표</b>
            </span>
          </label>
        </div>

        <button
          v-if="vote.status === 'OPEN' && vote.eligible"
          class="settlement-action"
          type="button"
          :disabled="!vote.eligible || !selectedOptionId || vote.hasVoted || submitting"
          @click="submitVote"
        >
          {{ vote.hasVoted ? '투표 제출 완료' : submitting ? '제출 중…' : '이 선택으로 투표하기' }}
        </button>
        <p v-if="vote.status === 'OPEN' && !vote.eligible" class="control-caution">
          투표가 시작된 뒤 합류해 이번 선택에는 참여할 수 없습니다. 마감 후 결과를 확인할 수 있어요.
        </p>
        <p v-else-if="vote.status === 'READY'" class="control-caution">선생님이 곧 투표를 시작합니다.</p>
        <p v-else-if="vote.status === 'CLOSED'" class="control-caution">동점 결과를 선생님이 최종 결정하고 있습니다.</p>
        <p v-else class="control-caution">가장 많은 표를 받은 선택지가 표시되었습니다.</p>
      </section>
      </template>
    </template>
  </div>
</template>
