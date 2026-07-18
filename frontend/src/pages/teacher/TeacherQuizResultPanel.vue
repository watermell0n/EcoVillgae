<script setup lang="ts">
import { computed } from 'vue'

import type {
  TeacherQuizQuestionSummary,
  TeacherQuizSummary,
} from '../../features/quiz/api/quiz'

const props = defineProps<{
  summary: TeacherQuizSummary
  dayNumber: 1 | 2
  nextStageLabel: string
}>()

const averageCorrectCount = computed(() => {
  if (props.summary.submittedCount === 0) return '0.0'
  const total = props.summary.students.reduce(
    (sum, student) => sum + (student.correctCount ?? 0),
    0,
  )
  return (total / props.summary.submittedCount).toFixed(1)
})

function correctRate(question: TeacherQuizQuestionSummary) {
  if (props.summary.submittedCount === 0) return 0
  return Math.round((question.correctCount / props.summary.submittedCount) * 100)
}

function correctAnswerText(question: TeacherQuizQuestionSummary) {
  const option = question.options.find((item) => item.id === question.correctAnswer)
  return option ? `${option.id}. ${option.text}` : question.correctAnswer
}
</script>

<template>
  <div class="teacher-quiz-result">
    <header class="teacher-quiz-result__header">
      <div>
        <span>{{ props.dayNumber === 1 ? '1일차 퀴즈 결과' : '경제 퀴즈 결과' }}</span>
        <h3>{{ props.dayNumber === 1 ? '학생들과 정답을 확인해 주세요.' : '학생들과 정답과 해설을 확인해 주세요.' }}</h3>
      </div>
      <strong>{{ summary.title }}</strong>
    </header>

    <dl class="teacher-quiz-result__summary">
      <div>
        <dt>결과 집계</dt>
        <dd>{{ summary.submittedCount }} / {{ summary.eligibleStudentCount }}명</dd>
      </div>
      <div>
        <dt>평균 정답</dt>
        <dd>{{ averageCorrectCount }} / {{ summary.questionCount }}</dd>
      </div>
      <div :class="{ 'has-warning': summary.notSubmittedCount > 0 }">
        <dt>보충 퀴즈 대상</dt>
        <dd>{{ summary.notSubmittedCount }}명</dd>
      </div>
    </dl>

    <section class="teacher-quiz-question-results" aria-labelledby="teacher-question-results-title">
      <div class="teacher-quiz-result__section-heading">
        <h4 id="teacher-question-results-title">문항별 정답률</h4>
        <span>제출 학생 기준</span>
      </div>
      <ol>
        <li v-for="question in summary.questions" :key="question.questionId">
          <div class="teacher-quiz-question-results__title">
            <span>문제 {{ question.orderNo }}</span>
            <strong>{{ correctRate(question) }}%</strong>
          </div>
          <p>{{ question.prompt }}</p>
          <div class="teacher-quiz-question-results__bar" aria-hidden="true">
            <span :style="{ width: `${correctRate(question)}%` }"></span>
          </div>
          <small>정답 {{ question.correctCount }}명 · {{ correctAnswerText(question) }}</small>
          <p v-if="props.dayNumber === 2 && question.explanation" class="teacher-quiz-question-results__explanation">
            {{ question.explanation }}
          </p>
        </li>
      </ol>
    </section>

    <section class="teacher-quiz-student-results" aria-labelledby="teacher-student-results-title">
      <div class="teacher-quiz-result__section-heading">
        <h4 id="teacher-student-results-title">학생별 결과</h4>
        <span>학생을 열어 문항별 결과를 확인할 수 있어요.</span>
      </div>
      <div class="teacher-quiz-student-results__list">
        <details v-for="student in summary.students" :key="student.studentId">
          <summary>
            <span>{{ student.participantNumber }}번 {{ student.nickname ?? '미설정' }}</span>
            <strong v-if="student.submissionStatus === 'SUBMITTED'">
              {{ student.correctCount }} / {{ summary.questionCount }}개 정답
            </strong>
            <strong v-else>보충 퀴즈 진행 필요</strong>
          </summary>
          <ul v-if="student.questions.length > 0" aria-label="문항별 정답 여부">
            <li
              v-for="question in student.questions"
              :key="question.questionId"
              :class="question.correct ? 'is-correct' : question.answered ? 'is-incorrect' : 'is-unanswered'"
            >
              문제 {{ question.orderNo }} · {{ question.correct ? '정답' : question.answered ? '오답' : '미응답' }}
            </li>
          </ul>
          <p v-else>아직 제출된 결과가 없습니다.</p>
        </details>
      </div>
    </section>

    <p class="control-caution">
      결과 확인이 끝나면 아래 버튼으로 {{ props.nextStageLabel }} 단계로 이동해 주세요.
    </p>
  </div>
</template>
