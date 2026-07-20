<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { getApiErrorMessage } from '../../../shared/api/errors'
import { useLearningStore } from '../../../stores/learning'
import { useRoomSessionStore } from '../../../stores/roomSession'

const learningStore = useLearningStore()
const roomSessionStore = useRoomSessionStore()
const loading = ref(true)
const errorMessage = ref('')
const learning = computed(() => learningStore.guided)
const isRationalConsumption = computed(() => learning.value?.currentPage.topicTitle === '합리적인 소비')
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
  return accents[learning.value?.currentPage.title ?? ''] ?? 'blue'
})

onMounted(() => void refresh())

watch(
  () => roomSessionStore.studentState?.syncVersion,
  (next, previous) => {
    if (previous !== undefined && next !== previous) void refresh()
  },
)

async function refresh() {
  try {
    errorMessage.value = ''
    await learningStore.refreshStudent()
  } catch (error) {
    errorMessage.value = getApiErrorMessage(error, '교육 페이지를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

</script>

<template>
  <div class="learning-stage-view">
    <p v-if="loading" class="state-message">선생님이 보여 주는 페이지를 불러오고 있습니다.</p>
    <div v-else-if="errorMessage" class="quiz-load-error" role="alert">
      <strong>교육 페이지를 불러오지 못했어요.</strong>
      <p>{{ errorMessage }}</p>
      <button type="button" @click="refresh">다시 불러오기</button>
    </div>
    <article
      v-else-if="learning"
      :class="[
        'learning-card',
        `learning-card--${pageAccent}`,
        { 'learning-card--rational': isRationalConsumption },
      ]"
      aria-live="polite"
    >
      <header class="learning-card__progress">
        <p class="eyebrow">{{ learning.currentPage.topicTitle }}</p>
        <strong>{{ learning.currentIndex + 1 }} / {{ learning.pages.length }}</strong>
      </header>
      <div class="learning-card__hero">
        <span class="learning-card__icon" aria-hidden="true">{{ learning.currentPage.icon ?? '💡' }}</span>
        <h1>{{ learning.currentPage.title }}</h1>
      </div>
      <p class="learning-card__content">{{ learning.currentPage.content }}</p>
      <ul v-if="learning.currentPage.keyPoints.length > 0">
        <li v-for="point in learning.currentPage.keyPoints" :key="point">{{ point }}</li>
      </ul>
      <aside v-if="learning.currentPage.example" class="learning-card__example">
        <span>📌 예시</span>
        <strong>{{ learning.currentPage.example }}</strong>
      </aside>
    </article>
  </div>
</template>
