<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { TeacherQuizSummary } from '../../features/quiz/api/quiz'

const props = defineProps<{ summary: TeacherQuizSummary }>()

const emit = defineEmits<{
  open: []
  close: []
}>()

const currentIndex = ref(0)
const currentQuestion = computed(() => props.summary.questions[currentIndex.value] ?? null)
const progressWidth = computed(() => `${((currentIndex.value + 1) / Math.max(props.summary.questionCount, 1)) * 100}%`)

watch(
  () => props.summary.quizId,
  () => { currentIndex.value = 0 },
)

function moveQuestion(index: number) {
  if (index < 0 || index >= props.summary.questions.length) return
  currentIndex.value = index
}
</script>

<template>
  <div class="teacher-student-mirror teacher-quiz-mirror" aria-label="학생과 동일한 퀴즈 문제 화면">
    <div class="teacher-student-mirror__label">
      <span>학생 화면과 동일하게 표시 중</span>
      <strong>{{ summary.status === 'READY' ? '학생에게는 시작 대기 화면 표시' : summary.status === 'OPEN' ? '학생 풀이 진행 중' : '퀴즈 마감' }}</strong>
    </div>

    <div class="quiz-stage-view">
      <section v-if="currentQuestion" class="quiz-player" aria-labelledby="teacher-quiz-question-title">
        <header class="quiz-player__header">
          <div>
            <p class="eyebrow">{{ summary.title }}</p>
            <strong>{{ currentIndex + 1 }} / {{ summary.questionCount }}</strong>
          </div>
          <span>교사 문제 화면</span>
        </header>

        <div class="quiz-progress" aria-hidden="true">
          <span :style="{ width: progressWidth }" />
        </div>

        <div class="quiz-question">
          <p>문제 {{ currentQuestion.orderNo }}</p>
          <h1 id="teacher-quiz-question-title">{{ currentQuestion.prompt }}</h1>
          <div class="quiz-options" role="list" :aria-label="`${currentQuestion.orderNo}번 문제 선택지`">
            <div v-for="option in currentQuestion.options" :key="option.id" class="quiz-option teacher-quiz-mirror__option" role="listitem">
              <span>{{ option.id }}</span>
              <strong>{{ option.text }}</strong>
            </div>
          </div>
        </div>

        <div class="quiz-question-map" aria-label="문제 이동">
          <button
            v-for="(question, index) in summary.questions"
            :key="question.questionId"
            type="button"
            :class="{ 'is-current': index === currentIndex }"
            :aria-label="`${question.orderNo}번 문제 보기`"
            @click="moveQuestion(index)"
          >{{ question.orderNo }}</button>
        </div>

        <footer class="quiz-player__footer">
          <button class="button-secondary" type="button" :disabled="currentIndex === 0" @click="moveQuestion(currentIndex - 1)">← 이전 문제</button>
          <button type="button" :disabled="currentIndex >= summary.questions.length - 1" @click="moveQuestion(currentIndex + 1)">다음 문제 →</button>
        </footer>
      </section>
    </div>

    <div class="teacher-student-mirror__action">
      <p v-if="summary.status === 'READY'">학생 화면은 잠겨 있습니다. 설명을 마쳤다면 퀴즈를 시작하세요.</p>
      <p v-else-if="summary.status === 'OPEN'">{{ summary.submittedCount }}명 제출 · {{ summary.inProgressCount }}명 풀이 중</p>
      <p v-else>퀴즈가 마감되었습니다. 다음 단계로 이동할 수 있습니다.</p>
      <button v-if="summary.status === 'READY'" type="button" @click="emit('open')">퀴즈 시작하기</button>
      <button v-else-if="summary.status === 'OPEN'" class="button-secondary" type="button" @click="emit('close')">퀴즈 마감하기</button>
    </div>
  </div>
</template>
