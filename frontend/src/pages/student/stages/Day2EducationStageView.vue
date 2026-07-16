<script setup lang="ts">
const lessons = [
  {
    icon: '🧾',
    title: '금융세',
    description: '예금 이자와 투자 순이익처럼 금융 활동에서 얻은 이익에도 세금이 붙을 수 있어요.',
    point: '우리 수업의 금융세는 10%이며 학급 공동기금에 모입니다.',
  },
  {
    icon: '⚖️',
    title: '위험과 수익',
    description: '큰 수익을 기대하는 투자일수록 손실 가능성도 함께 커지는 경우가 많아요.',
    point: '한 곳에 모두 투자하지 않고 나누면 위험을 줄이는 데 도움이 됩니다.',
  },
  {
    icon: '📈',
    title: '인플레이션',
    description: '물가가 오르면 같은 돈으로 살 수 있는 물건의 양이 줄어들어요.',
    point: '곧 상점 가격이 10% 오르는 모습을 직접 확인합니다.',
  },
]
</script>

<template>
  <section class="day2-education-view" aria-labelledby="day2-education-title">
    <p class="eyebrow">금융 지식 확장</p>
    <h1 id="day2-education-title" class="page-title">돈의 가치가 달라지는 이유를 알아봐요</h1>
    <p class="page-description">금융세, 투자의 위험과 수익, 인플레이션을 실제 활동과 연결해 봅니다.</p>
    <div class="day2-lesson-grid">
      <article v-for="lesson in lessons" :key="lesson.title">
        <span aria-hidden="true">{{ lesson.icon }}</span>
        <h2>{{ lesson.title }}</h2>
        <p>{{ lesson.description }}</p>
        <strong>{{ lesson.point }}</strong>
      </article>
    </div>
  </section>
</template>
