<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  getStudentDay2QuizReward,
  type Day2QuizRewardStudent,
} from '../../../features/day2/api/quizReward'
import { getApiErrorMessage } from '../../../shared/api/errors'

const reward = ref<Day2QuizRewardStudent | null>(null)
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    reward.value = await getStudentDay2QuizReward()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '퀴즈 보상 정보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
})

function formatBibim(amount: number) {
  return `${amount.toLocaleString('ko-KR')}비빔`
}
</script>

<template>
  <div class="day2-reward-view">
    <p v-if="loading" class="state-message">2일차 퀴즈 보상을 계산하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <section v-else-if="reward" class="day2-result-card" aria-labelledby="day2-reward-title">
      <p class="eyebrow">2일차 복습 퀴즈</p>
      <h1 id="day2-reward-title" class="page-title">
        {{ reward.submitted ? `${reward.correctCount}문제를 맞혔어요!` : '아직 제출한 답안이 없어요' }}
      </h1>
      <p class="page-description">
        제출한 학생은 참여수당 10비빔과 정답 한 문제당 5비빔을 받습니다.
      </p>
      <dl class="day2-reward-breakdown">
        <div><dt>참여수당</dt><dd>+{{ formatBibim(reward.participationReward) }}</dd></div>
        <div><dt>정답 보상</dt><dd>+{{ formatBibim(reward.correctReward) }}</dd></div>
        <div class="is-total"><dt>총 보상</dt><dd>+{{ formatBibim(reward.totalReward) }}</dd></div>
        <div><dt>보상 후 지갑</dt><dd>{{ formatBibim(reward.walletAfter) }}</dd></div>
      </dl>
      <p class="control-caution">선생님이 보상 정산을 완료하면 지갑에 한 번만 반영됩니다.</p>
    </section>
  </div>
</template>
