<script setup lang="ts">
defineProps<{
  label: string
  tone: 'success' | 'warning' | 'danger' | 'neutral'
}>()
</script>

<template>
  <span class="status-badge" :class="`status-badge--${tone}`">
    <span class="status-badge__dot" aria-hidden="true" />
    {{ label }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.45rem;
  width: fit-content;
  padding: 0.4rem 0.7rem;
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 700;
  white-space: nowrap;
}

.status-badge__dot {
  width: 0.48rem;
  height: 0.48rem;
  border-radius: 50%;
  background: currentColor;
}

.status-badge--success {
  color: #16704a;
  background: #e8f7ef;
}

.status-badge--warning {
  color: #995a09;
  background: #fff4db;
}

.status-badge--danger {
  color: #b42318;
  background: #feeceb;
}

.status-badge--neutral {
  color: #53606f;
  background: #eef2f5;
}
</style>

