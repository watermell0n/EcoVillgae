<script setup lang="ts">
import { computed } from 'vue'

import type { GuidedLearning } from '../../features/learning/api/learning'

const props = defineProps<{ learning: GuidedLearning }>()

const emit = defineEmits<{
  previous: []
  next: []
}>()

const pageAccent = computed(() => {
  const accents: Record<string, string> = {
    '월급과 실수령액': 'green',
    '세금이란?': 'orange',
    '세금의 활용': 'blue',
    '소비와 저축': 'gold',
    '예금이란?': 'purple',
    '이자란?': 'violet',
    '투자란?': 'red',
    '위험과 분산투자': 'orange',
    '금융세가 뭐예요?': 'blue',
    '인플레이션이 뭐예요?': 'purple',
  }
  return accents[props.learning.currentPage.title] ?? 'blue'
})

function displayLearningText(value: string) {
  return props.learning.currentPage.topicTitle === '합리적인 소비'
    ? value.replaceAll(':', '')
    : value
}
</script>

<template>
  <div class="teacher-student-mirror" aria-label="학생과 동일한 경제 학습 화면">
    <div class="teacher-student-mirror__label">
      <span>학생 화면과 동일하게 표시 중</span>
      <strong>교사 설명용</strong>
    </div>

    <div class="learning-stage-view">
      <article :class="['learning-card', `learning-card--${pageAccent}`]" aria-live="polite">
        <header class="learning-card__progress">
          <p class="eyebrow">{{ learning.currentPage.topicTitle }}</p>
          <strong>{{ learning.currentIndex + 1 }} / {{ learning.pages.length }}</strong>
        </header>
        <div class="learning-card__hero">
          <span class="learning-card__icon" aria-hidden="true">{{ learning.currentPage.icon ?? '💡' }}</span>
          <h1>{{ learning.currentPage.title }}</h1>
        </div>
        <p class="learning-card__content">{{ displayLearningText(learning.currentPage.content) }}</p>
        <ul v-if="learning.currentPage.keyPoints.length > 0">
          <li v-for="point in learning.currentPage.keyPoints" :key="point">{{ displayLearningText(point) }}</li>
        </ul>
        <aside v-if="learning.currentPage.example" class="learning-card__example">
          <span>📌 예시</span>
          <strong>{{ displayLearningText(learning.currentPage.example) }}</strong>
        </aside>
      </article>
    </div>

    <div class="teacher-student-mirror__navigation">
      <button class="button-secondary" type="button" :disabled="learning.currentIndex === 0" @click="emit('previous')">← 이전 페이지</button>
      <span>{{ learning.currentIndex + 1 }} / {{ learning.pages.length }}</span>
      <button type="button" :disabled="learning.currentIndex >= learning.pages.length - 1" @click="emit('next')">다음 페이지 →</button>
    </div>
  </div>
</template>
