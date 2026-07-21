<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  getDayOneQuizResult,
  getDayTwoQuizResult,
  type QuizResultQuestion,
  type StudentQuizResult,
} from '../../../features/quiz/api/quiz'
import { getApiErrorMessage } from '../../../shared/api/errors'

const props = withDefaults(defineProps<{
  dayNumber?: 1 | 2
}>(), {
  dayNumber: 1,
})

const result = ref<StudentQuizResult | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const showWaitNotice = ref(true)

const incorrectCount = computed(() => {
  if (!result.value) return 0
  return result.value.answeredCount - result.value.correctCount
})
const unansweredCount = computed(() => {
  if (!result.value) return 0
  return result.value.questionCount - result.value.answeredCount
})

onMounted(() => {
  void loadResult()
})

async function loadResult() {
  loading.value = true
  errorMessage.value = ''
  try {
    result.value = props.dayNumber === 1
      ? await getDayOneQuizResult()
      : await getDayTwoQuizResult()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '퀴즈 결과를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

function answerText(question: QuizResultQuestion, answer: string | null) {
  if (!answer) return '답하지 않음'
  const option = question.options.find((item) => item.id === answer)
  return option ? `${option.id}. ${option.text}` : answer
}

function resultLabel(question: QuizResultQuestion) {
  if (!question.selectedAnswer) return '미응답'
  return question.correct ? '정답' : '오답'
}
</script>

<template>
  <div class="day1-quiz-result-stage" :class="{ 'has-fixed-notice': showWaitNotice }">
    <p v-if="loading" class="state-message">퀴즈 결과를 채점하고 있습니다.</p>

    <div v-else-if="errorMessage || !result" class="quiz-load-error" role="alert">
      <strong>퀴즈 결과를 불러오지 못했어요.</strong>
      <p>{{ errorMessage }}</p>
      <button type="button" @click="loadResult">다시 불러오기</button>
    </div>

    <template v-else>
      <header class="quiz-result__header">
        <div class="stage-view__icon stage-view__icon--green" aria-hidden="true">✓</div>
        <div>
          <p class="eyebrow">{{ props.dayNumber === 1 ? '1일차 퀴즈 결과' : '경제 퀴즈 결과' }}</p>
          <h1 class="page-title">내가 푼 문제를 확인해요.</h1>
          <p class="page-description">
            {{ props.dayNumber === 1 ? '선생님과 함께 정답을 살펴보세요.' : '선생님과 함께 정답과 해설을 살펴보세요.' }}
          </p>
        </div>
      </header>

      <div class="quiz-result__summary" aria-label="퀴즈 채점 결과">
        <article>
          <span>맞힌 문제</span>
          <strong>{{ result.correctCount }} / {{ result.questionCount }}</strong>
        </article>
        <article :class="{ 'has-warning': incorrectCount > 0 }">
          <span>틀린 문제</span>
          <strong>{{ incorrectCount }}문제</strong>
        </article>
        <article :class="{ 'has-warning': unansweredCount > 0 }">
          <span>답하지 않은 문제</span>
          <strong>{{ unansweredCount }}문제</strong>
        </article>
      </div>

      <section class="quiz-result-detail" aria-labelledby="quiz-result-detail-title">
        <div>
          <p class="eyebrow">문제별 확인</p>
          <h2 id="quiz-result-detail-title">{{ props.dayNumber === 1 ? '정답 확인' : '정답과 해설' }}</h2>
        </div>
        <ol>
          <li
            v-for="question in result.questions"
            :key="question.questionId"
            :class="{
              'is-correct': question.correct,
              'is-incorrect': question.selectedAnswer && !question.correct,
              'is-unanswered': !question.selectedAnswer,
            }"
          >
            <header>
              <span>문제 {{ question.orderNo }}</span>
              <strong>{{ resultLabel(question) }}</strong>
            </header>
            <h3>{{ question.prompt }}</h3>
            <dl>
              <div><dt>내가 고른 답</dt><dd>{{ answerText(question, question.selectedAnswer) }}</dd></div>
              <div><dt>정답</dt><dd>{{ answerText(question, question.correctAnswer) }}</dd></div>
            </dl>
            <p v-if="props.dayNumber === 2" class="quiz-result-detail__explanation">
              <strong>해설</strong>{{ question.explanation || '정답을 다시 한번 확인해 보세요.' }}
            </p>
          </li>
        </ol>
      </section>

      <aside v-if="showWaitNotice" class="settlement-wait-card" role="status">
        <span aria-hidden="true">⌛</span>
        <div>
          <strong>지금은 퀴즈 결과를 함께 확인하는 시간이에요.</strong>
          <p v-if="props.dayNumber === 1">결과 확인이 끝나면 선생님이 역할 월급과 퀴즈 정답수당을 더하고, 우리 반 공동기금으로 모을 소득세를 계산해요.</p>
          <p v-else>결과 확인이 끝나면 선생님이 맞힌 문제로 받을 돈과 우리 반 공동기금으로 모을 소득세를 계산해요.</p>
        </div>
        <button class="settlement-wait-card__close" type="button" aria-label="안내 닫기" @click="showWaitNotice = false">×</button>
        <div v-if="props.dayNumber === 1" class="settlement-formula" aria-label="실수령액 계산식">
          <span>역할 기본 월급 + 퀴즈 정답수당</span>
          <strong>− 소득세</strong>
          <span>= 실수령액</span>
        </div>
        <div v-else class="settlement-formula" aria-label="퀴즈 보상 계산식">
          <span>정답 수 × 5비빔</span>
          <strong>− 소득세</strong>
          <span>= 실제로 받는 돈</span>
        </div>
      </aside>
    </template>
  </div>
</template>
