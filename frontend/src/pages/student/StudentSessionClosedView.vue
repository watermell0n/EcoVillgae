<script setup lang="ts">
withDefaults(defineProps<{
  dayNumber?: 1 | 2
}>(), {
  dayNumber: 2,
})
</script>

<template>
  <section class="student-session-closed" role="status">
    <span aria-hidden="true">🌿</span>
    <template v-if="dayNumber === 1">
      <h1>1일차 활동이 마감되었습니다</h1>
      <p>오늘의 활동 결과가 안전하게 저장되었어요. 2일차에 다시 만나요!</p>
    </template>
    <template v-else>
      <h1>수업이 종료되었습니다</h1>
      <p>활동 결과가 안전하게 저장되었어요. 참여해 줘서 고마워요!</p>
    </template>
  </section>
</template>
