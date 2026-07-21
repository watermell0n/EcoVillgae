<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import {
  getCurrentQuiz,
  saveQuizAnswer,
  submitQuiz,
  type StudentQuiz,
} from '../../../features/quiz/api/quiz'
import { getApiErrorCode, getApiErrorMessage, isUnauthorized } from '../../../shared/api/errors'
import { useRoomSessionStore } from '../../../stores/roomSession'

const roomSessionStore = useRoomSessionStore()

const emit = defineEmits<{
  pageChange: [page: string]
  completionChange: [completed: boolean]
}>()

const quiz = ref<StudentQuiz | null>(null)
const currentIndex = ref(0)
const loading = ref(true)
const savingQuestionId = ref<string | null>(null)
const recentlySavedQuestionId = ref<string | null>(null)
const submitting = ref(false)
const showSubmitConfirm = ref(false)
const errorMessage = ref('')
const waitingForOpen = ref(false)
const closedByTeacher = ref(false)
let applyingBrowserHistory = false

const currentQuestion = computed(() => quiz.value?.questions[currentIndex.value])
const unansweredCount = computed(
  () => quiz.value?.questions.filter((question) => !question.savedAnswer).length ?? 0,
)
const answeredQuestions = computed(
  () => quiz.value?.questions.filter((question) => question.savedAnswer).length ?? 0,
)
const isSubmitted = computed(() => quiz.value?.submissionStatus === 'SUBMITTED')
const progressWidth = computed(() => {
  if (!quiz.value) return '0%'
  return `${((currentIndex.value + 1) / quiz.value.questionCount) * 100}%`
})

watch(currentQuestion, (question) => {
  if (question && !isSubmitted.value) {
    emit('pageChange', `QUIZ_QUESTION_${question.orderNo}`)
  }
})

onMounted(() => {
  window.addEventListener('popstate', handleBrowserBack)
  void loadQuiz()
})

onUnmounted(() => {
  window.removeEventListener('popstate', handleBrowserBack)
})

watch(
  () => roomSessionStore.studentState?.syncVersion,
  (next, previous) => {
    if (previous !== undefined && next !== previous && !isSubmitted.value) void loadQuiz()
  },
)

async function loadQuiz() {
  loading.value = true
  errorMessage.value = ''
  waitingForOpen.value = false
  closedByTeacher.value = false
  emit('completionChange', false)
  try {
    quiz.value = await getCurrentQuiz()
    emit('completionChange', quiz.value.submissionStatus === 'SUBMITTED')
    if (quiz.value.submissionStatus === 'SUBMITTED') emit('pageChange', 'QUIZ_SUBMITTED')
    const firstUnansweredIndex = quiz.value.questions.findIndex((question) => !question.savedAnswer)
    currentIndex.value = firstUnansweredIndex >= 0 ? firstUnansweredIndex : 0
    replaceQuestionHistory(currentIndex.value)
  } catch (error) {
    if (isUnauthorized(error)) {
      window.location.assign('/student/join')
      return
    }
    if (getApiErrorCode(error) === 'QUIZ_NOT_OPEN') {
      if (quiz.value) {
        closedByTeacher.value = true
      } else {
        waitingForOpen.value = true
      }
      return
    }
    errorMessage.value = getApiErrorMessage(error, '퀴즈를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function selectAnswer(answer: string) {
  const question = currentQuestion.value
  if (!quiz.value || !question || isSubmitted.value || savingQuestionId.value) return

  const previousAnswer = question.savedAnswer
  question.savedAnswer = answer
  savingQuestionId.value = question.questionId
  recentlySavedQuestionId.value = null
  errorMessage.value = ''
  try {
    const result = await saveQuizAnswer(quiz.value.quizId, question.questionId, answer)
    question.savedAnswer = result.answer
    quiz.value.answeredCount = result.answeredCount
    quiz.value.submissionStatus = 'IN_PROGRESS'
    recentlySavedQuestionId.value = question.questionId
  } catch (error) {
    question.savedAnswer = previousAnswer
    errorMessage.value = getApiErrorMessage(error, '답안을 저장하지 못했습니다.')
  } finally {
    savingQuestionId.value = null
  }
}

function moveQuestion(index: number) {
  if (!quiz.value || index < 0 || index >= quiz.value.questionCount) return
  currentIndex.value = index
  errorMessage.value = ''
  pushQuestionHistory(index)
}

function quizHistoryState(index: number) {
  return {
    ...window.history.state,
    studentRoomView: 'QUIZ',
    studentRoomStage: roomSessionStore.studentState?.effectiveStage
      ?? roomSessionStore.studentState?.currentStage,
    studentRoomSyncVersion: roomSessionStore.studentState?.syncVersion,
    quizQuestionIndex: index,
  }
}

function replaceQuestionHistory(index: number) {
  window.history.replaceState(quizHistoryState(index), '', window.location.href)
}

function pushQuestionHistory(index: number) {
  if (applyingBrowserHistory) return
  window.history.pushState(quizHistoryState(index), '', window.location.href)
}

function handleBrowserBack(event: PopStateEvent) {
  if (!quiz.value || isSubmitted.value) return
  const historyState = event.state as Record<string, unknown> | null
  const activeStage = roomSessionStore.studentState?.effectiveStage
    ?? roomSessionStore.studentState?.currentStage
  if (historyState?.studentRoomView !== 'QUIZ'
    || historyState.studentRoomStage !== activeStage
    || historyState.studentRoomSyncVersion !== roomSessionStore.studentState?.syncVersion
    || typeof historyState.quizQuestionIndex !== 'number') return
  const index = historyState.quizQuestionIndex
  if (index < 0 || index >= quiz.value.questionCount) return
  applyingBrowserHistory = true
  currentIndex.value = index
  errorMessage.value = ''
  applyingBrowserHistory = false
}

function selectedOptionText(question: StudentQuiz['questions'][number]) {
  if (!question.savedAnswer) return '선택한 답 없음'
  const selectedOption = question.options.find((option) => option.id === question.savedAnswer)
  return selectedOption ? `${selectedOption.id}. ${selectedOption.text}` : question.savedAnswer
}

function formatSubmittedAt(submittedAt: string | null) {
  if (!submittedAt) return '제출 시간 확인 중'
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(submittedAt))
}

async function confirmSubmit() {
  if (!quiz.value || submitting.value) return

  submitting.value = true
  errorMessage.value = ''
  try {
    const result = await submitQuiz(quiz.value.quizId)
    quiz.value.submissionStatus = result.status
    quiz.value.submittedAt = result.submittedAt
    quiz.value.answeredCount = result.answeredCount
    showSubmitConfirm.value = false
    replaceQuestionHistory(currentIndex.value)
    emit('pageChange', 'QUIZ_SUBMITTED')
    emit('completionChange', true)
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '퀴즈를 제출하지 못했습니다.')
    showSubmitConfirm.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="quiz-stage-view">
    <p v-if="loading" class="state-message">퀴즈 문제를 불러오고 있습니다.</p>

    <section v-else-if="waitingForOpen" class="quiz-complete" aria-labelledby="quiz-waiting-title">
      <div class="stage-view__icon" aria-hidden="true">⏳</div>
      <p class="eyebrow">퀴즈 준비</p>
      <h1 id="quiz-waiting-title" class="page-title">선생님이 퀴즈를 시작할 때까지 기다려 주세요.</h1>
      <p class="page-description">시작 신호가 오면 이 화면에 문제가 자동으로 나타나요.</p>
    </section>

    <section v-else-if="closedByTeacher" class="quiz-complete" aria-labelledby="quiz-closed-title">
      <div class="stage-view__icon stage-view__icon--muted" aria-hidden="true">■</div>
      <p class="eyebrow">퀴즈 마감</p>
      <h1 id="quiz-closed-title" class="page-title">선생님이 퀴즈를 마감했습니다.</h1>
      <p class="page-description">이제 답안을 저장하거나 제출할 수 없어요. 다음 활동을 기다려 주세요.</p>
    </section>

    <div v-else-if="errorMessage && !quiz" class="quiz-load-error">
      <p class="form-error" role="alert">{{ errorMessage }}</p>
      <button type="button" @click="loadQuiz">다시 불러오기</button>
    </div>

    <section v-else-if="quiz && isSubmitted" class="quiz-complete quiz-result" aria-labelledby="quiz-complete-title">
      <header class="quiz-result__header">
        <div class="stage-view__icon stage-view__icon--green" aria-hidden="true">✓</div>
        <div>
          <p class="eyebrow">제출 완료</p>
          <h1 id="quiz-complete-title" class="page-title">퀴즈 제출이 완료되었습니다.</h1>
          <p class="page-description">내가 제출한 답을 확인하고 결과 발표를 기다려 주세요.</p>
        </div>
      </header>

      <div class="quiz-result__summary" aria-label="퀴즈 제출 요약">
        <article>
          <span>응답한 문제</span>
          <strong>{{ answeredQuestions }} / {{ quiz.questionCount }}</strong>
        </article>
        <article :class="{ 'has-warning': unansweredCount > 0 }">
          <span>미응답 문제</span>
          <strong>{{ unansweredCount }}문제</strong>
        </article>
        <article>
          <span>제출 시간</span>
          <strong>{{ formatSubmittedAt(quiz.submittedAt) }}</strong>
        </article>
      </div>

      <section class="quiz-answer-review" aria-labelledby="quiz-answer-review-title">
        <div>
          <p class="eyebrow">내가 제출한 답</p>
          <h2 id="quiz-answer-review-title">답안 제출 내역</h2>
        </div>
        <ol>
          <li v-for="question in quiz.questions" :key="question.questionId">
            <span :class="{ 'is-empty': !question.savedAnswer }">{{ question.orderNo }}</span>
            <div>
              <strong>{{ question.prompt }}</strong>
              <p>{{ selectedOptionText(question) }}</p>
            </div>
            <small>{{ question.savedAnswer ? '응답 완료' : '미응답' }}</small>
          </li>
        </ol>
      </section>

      <aside class="quiz-result__notice">
        <span aria-hidden="true">🔒</span>
        <p><strong>아직 문제를 풀고 있는 친구가 있어요.</strong> 정답 수와 보상은 퀴즈가 끝난 뒤 함께 확인해요.</p>
      </aside>
    </section>

    <section v-else-if="quiz && currentQuestion" class="quiz-player" aria-labelledby="quiz-question-title">
      <header class="quiz-player__header">
        <div>
          <p class="eyebrow">{{ quiz.title }}</p>
          <strong>{{ currentIndex + 1 }} / {{ quiz.questionCount }}</strong>
        </div>
        <span>{{ quiz.answeredCount }}문제 답변 완료</span>
      </header>

      <div class="quiz-progress" aria-hidden="true">
        <span :style="{ width: progressWidth }" />
      </div>

      <div class="quiz-question">
        <p>문제 {{ currentQuestion.orderNo }}</p>
        <h1 id="quiz-question-title">{{ currentQuestion.prompt }}</h1>
        <div class="quiz-options" role="group" :aria-label="`${currentQuestion.orderNo}번 문제 선택지`">
          <button
            v-for="option in currentQuestion.options"
            :key="option.id"
            type="button"
            class="quiz-option"
            :class="{ 'is-selected': currentQuestion.savedAnswer === option.id }"
            :aria-pressed="currentQuestion.savedAnswer === option.id"
            :disabled="savingQuestionId === currentQuestion.questionId"
            @click="selectAnswer(option.id)"
          >
            <span>{{ option.id }}</span>
            <strong>{{ option.text }}</strong>
          </button>
        </div>
        <p v-if="savingQuestionId === currentQuestion.questionId" class="quiz-save-state">저장 중…</p>
        <p
          v-else-if="recentlySavedQuestionId === currentQuestion.questionId"
          class="quiz-save-state quiz-save-state--saved"
          role="status"
        >
          답안이 저장되었습니다.
        </p>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      </div>

      <div class="quiz-question-map" aria-label="문제 이동">
        <button
          v-for="(question, index) in quiz.questions"
          :key="question.questionId"
          type="button"
          :class="{
            'is-current': index === currentIndex,
            'is-answered': question.savedAnswer,
          }"
          :aria-label="`${index + 1}번 문제${question.savedAnswer ? ', 답변 완료' : ', 미응답'}`"
          @click="moveQuestion(index)"
        >
          {{ index + 1 }}
        </button>
      </div>

      <footer class="quiz-player__footer">
        <button
          class="button-secondary"
          type="button"
          :disabled="currentIndex === 0"
          @click="moveQuestion(currentIndex - 1)"
        >
          ← 이전 문제
        </button>
        <button
          v-if="currentIndex < quiz.questionCount - 1"
          type="button"
          @click="moveQuestion(currentIndex + 1)"
        >
          다음 문제 →
        </button>
        <button v-else type="button" @click="showSubmitConfirm = true">
          최종 제출하기
        </button>
      </footer>
    </section>

    <div v-if="showSubmitConfirm && quiz" class="modal-backdrop">
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="submit-confirm-title">
        <p class="eyebrow">최종 제출</p>
        <h2 id="submit-confirm-title">퀴즈를 제출할까요?</h2>
        <p v-if="unansweredCount > 0" class="confirm-modal__warning">
          아직 답하지 않은 문제가 {{ unansweredCount }}개 있습니다.
        </p>
        <p>제출한 뒤에는 답안을 바꿀 수 없습니다.</p>
        <div class="confirm-modal__actions">
          <button class="button-secondary" type="button" :disabled="submitting" @click="showSubmitConfirm = false">
            계속 풀기
          </button>
          <button type="button" :disabled="submitting" @click="confirmSubmit">
            {{ submitting ? '제출 중…' : '제출 확정' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
