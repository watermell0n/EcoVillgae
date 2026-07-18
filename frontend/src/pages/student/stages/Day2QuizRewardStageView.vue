<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  getStudentDay2QuizReward,
  type Day2QuizRewardStudent,
} from '../../../features/day2/api/quizReward'
import { getApiErrorMessage } from '../../../shared/api/errors'

const reward = ref<Day2QuizRewardStudent | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const rewardMessage = computed(() => {
  if (!reward.value?.submitted) return '퀴즈를 제출하지 않아 이번 보상은 지급되지 않았어요.'
  if (reward.value.correctCount === 0) return '참여수당을 받았어요. 틀린 문제도 다음 활동을 위한 좋은 연습이에요.'
  return `${reward.value.correctCount}개의 정답과 참여 보상이 지갑에 반영되었어요.`
})

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
    <p v-if="loading" class="state-message">경제 퀴즈 보상을 계산하고 있습니다.</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <section v-else-if="reward" class="day2-result-card quiz-reward-result" aria-labelledby="day2-reward-title">
      <header class="quiz-reward-result__header">
        <div>
          <p class="eyebrow">경제 퀴즈 보상 정산 결과</p>
          <h1 id="day2-reward-title" class="page-title">
            {{ reward.submitted ? `${reward.correctCount}문제를 맞혔어요!` : '제출한 답안이 없어요' }}
          </h1>
          <p class="page-description">{{ rewardMessage }}</p>
        </div>
        <div class="quiz-reward-score" :class="{ 'is-empty': !reward.submitted }">
          <span>{{ reward.submitted ? '정답 수' : '미제출' }}</span>
          <strong>{{ reward.submitted ? reward.correctCount : '—' }}</strong>
          <small>{{ reward.submitted ? '문제 정답' : '보상 없음' }}</small>
        </div>
      </header>

      <div v-if="reward.submitted" class="quiz-reward-equation" aria-label="퀴즈 보상 계산식">
        <div><span>참여수당</span><strong>{{ formatBibim(reward.participationReward) }}</strong></div>
        <b aria-hidden="true">+</b>
        <div><span>{{ reward.correctCount }}문제 정답 보상</span><strong>{{ formatBibim(reward.correctReward) }}</strong></div>
        <b aria-hidden="true">=</b>
        <div class="is-total"><span>내가 받은 총 보상</span><strong>{{ formatBibim(reward.totalReward) }}</strong></div>
      </div>

      <dl class="day2-reward-breakdown">
        <div><dt>참여수당</dt><dd>+{{ formatBibim(reward.participationReward) }}</dd></div>
        <div><dt>정답 보상</dt><dd>+{{ formatBibim(reward.correctReward) }}</dd></div>
        <div class="is-total"><dt>총 보상</dt><dd>+{{ formatBibim(reward.totalReward) }}</dd></div>
        <div><dt>보상 후 지갑</dt><dd>{{ formatBibim(reward.walletAfter) }}</dd></div>
      </dl>
      <aside class="quiz-reward-tip">
        <span aria-hidden="true">💡</span>
        <p><strong>보상은 한 번만 지급돼요.</strong> 선생님이 정산한 참여수당과 정답 보상이 지갑에 안전하게 반영되었습니다.</p>
      </aside>
    </section>
  </div>
</template>
