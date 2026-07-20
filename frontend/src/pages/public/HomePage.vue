<script setup lang="ts">
import { CURRENCY_UNIT, SERVICE_NAME } from '../../shared/config/brand'
</script>

<template>
  <a class="skip-link" href="#main-content">본문으로 바로가기</a>
  <main id="main-content" class="home-page" tabindex="-1">
    <section class="hero-card">
      <div class="hero-card__content">
        <p class="eyebrow">{{ SERVICE_NAME }} · 교실 경제활동 서비스</p>
        <h1 class="hero-card__title">{{ CURRENCY_UNIT }}으로 배우는 우리 반 경제생활</h1>
        <p class="hero-card__description">
          교사가 수업 단계를 진행하면 학생 화면이 함께 바뀌는 2일 과정 경제교육 서비스입니다.
        </p>

        <div class="role-actions">
          <RouterLink class="button-link" to="/student/join">학생으로 참여하기</RouterLink>
          <RouterLink class="button-link button-link--secondary" to="/teacher/login">
            교사 로그인
          </RouterLink>
          <RouterLink class="button-link button-link--secondary" to="/admin/login">
            관리자 로그인
          </RouterLink>
        </div>
      </div>

      <div class="money-illustration" aria-hidden="true">
        <span class="money-illustration__symbol">{{ CURRENCY_UNIT }}</span>
        <span class="money-illustration__coin money-illustration__coin--one">₿</span>
        <span class="money-illustration__coin money-illustration__coin--two">₿</span>
      </div>
    </section>

    <section class="role-grid" aria-label="서비스 역할">
      <article>
        <span>학생</span>
        <h2>경제활동 체험</h2>
        <p>퀴즈, 예금, 투자와 소비를 직접 경험합니다.</p>
      </article>
      <article>
        <span>교사</span>
        <h2>수업 흐름 제어</h2>
        <p>현재 단계와 학생 화면을 한 대시보드에서 관리합니다.</p>
      </article>
      <article>
        <span>관리자</span>
        <h2>운영 상태 확인</h2>
        <p>학급 진행 상태와 정산·오류 로그를 확인합니다.</p>
      </article>
    </section>
  </main>
</template>
