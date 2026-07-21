<script setup lang="ts">
import type { Day1PurchaseSummary } from '../../features/day1/api/day1'

withDefaults(defineProps<{
  dayNumber?: 1 | 2
  purchases?: Day1PurchaseSummary[]
  purchaseTotal?: number
}>(), {
  dayNumber: 2,
  purchases: () => [],
  purchaseTotal: 0,
})

function formatBibim(amount: number) {
  const normalized = Object.is(amount, -0) ? 0 : amount
  return `${normalized.toLocaleString('ko-KR')}비빔`
}
</script>

<template>
  <section class="student-session-closed" :class="{ 'is-day-one': dayNumber === 1 }" role="status">
    <span aria-hidden="true">🌿</span>
    <template v-if="dayNumber === 1">
      <h1>1일차 활동이 마감되었습니다</h1>
      <p>오늘의 활동 결과가 안전하게 저장되었어요. 2일차에 다시 만나요!</p>
      <section class="student-day1-closed-purchases" aria-labelledby="student-day1-purchases-title">
        <h2 id="student-day1-purchases-title">1일차에 내가 산 상품</h2>
        <ul v-if="purchases.length">
          <li v-for="purchase in purchases" :key="purchase.purchaseId">
            <span>{{ purchase.productName }}</span>
            <b>{{ purchase.quantity }}개</b>
            <strong>{{ formatBibim(purchase.totalPrice) }}</strong>
          </li>
        </ul>
        <p v-else>구매한 상품이 없어요.</p>
        <div><span>총소비</span><strong>{{ formatBibim(purchaseTotal) }}</strong></div>
      </section>
    </template>
    <template v-else>
      <h1>수업이 종료되었습니다</h1>
      <p>활동 결과가 안전하게 저장되었어요. 참여해 줘서 고마워요!</p>
    </template>
  </section>
</template>
