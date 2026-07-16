import type { Component } from 'vue'

import DaySummaryStageView from './DaySummaryStageView.vue'
import FreeActivityStageView from './FreeActivityStageView.vue'
import PayslipStageView from './PayslipStageView.vue'
import PlaceholderStageView from './PlaceholderStageView.vue'
import QuizStageView from './QuizStageView.vue'
import SettlementStageView from './SettlementStageView.vue'
import WaitingStageView from './WaitingStageView.vue'

const stageComponents: Record<string, Component> = {
  DAY1_LOBBY: WaitingStageView,
  DAY1_QUIZ: QuizStageView,
  DAY1_INCOME_SETTLEMENT: SettlementStageView,
  DAY1_PAYSLIP_GUIDED: PayslipStageView,
  DAY1_FREE_ACTIVITY: FreeActivityStageView,
  DAY1_SUMMARY: DaySummaryStageView,
  DAY2_SETTLEMENT_WAIT: PlaceholderStageView,
  DAY2_FINAL_RESULT: PlaceholderStageView,
}

export function getStudentStageComponent(stage?: string) {
  if (!stage) return WaitingStageView
  return stageComponents[stage] ?? PlaceholderStageView
}

export function isPlaceholderStage(stage?: string) {
  return getStudentStageComponent(stage) === PlaceholderStageView
}
