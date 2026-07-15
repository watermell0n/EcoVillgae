<script setup lang="ts">
defineProps<{
  stageLabel: string
}>()
</script>

<template>
  <div class="stage-view">
    <div class="stage-view__icon stage-view__icon--muted" aria-hidden="true">…</div>
    <p class="eyebrow">현재 활동 · {{ stageLabel }}</p>
    <h1 class="page-title">선생님과 함께 다음 활동을 확인해요.</h1>
    <p class="page-description">
      단계 전환과 화면 동기화가 연결된 자리입니다. 이 활동의 상세 기능은 이후 구현에서 교체됩니다.
    </p>
  </div>
</template>
