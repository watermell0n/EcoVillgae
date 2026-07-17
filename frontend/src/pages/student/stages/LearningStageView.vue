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
    <article v-else-if="learning" class="learning-card">
      <header>
        <div>
          <p class="eyebrow">{{ learning.currentPage.topicTitle }}</p>
          <h1>{{ learning.currentPage.title }}</h1>
        </div>
        <strong>{{ learning.currentIndex + 1 }} / {{ learning.pages.length }}</strong>
      </header>
      <p class="learning-card__content">{{ learning.currentPage.content }}</p>
      <ul>
        <li v-for="point in learning.currentPage.keyPoints" :key="point">{{ point }}</li>
      </ul>
      <p class="learning-card__sync">선생님이 페이지를 넘기면 이 화면도 자동으로 바뀌어요.</p>
    </article>
  </div>
</template>
