import type { Component } from 'vue'

import DaySummaryStageView from './DaySummaryStageView.vue'
import Day2FinanceStageView from './Day2FinanceStageView.vue'
import Day2EducationStageView from './Day2EducationStageView.vue'
import Day2FinalShopStageView from './Day2FinalShopStageView.vue'
import Day2FinalResultStageView from './Day2FinalResultStageView.vue'
import Day2FundStageView from './Day2FundStageView.vue'
import Day2InflationStageView from './Day2InflationStageView.vue'
import Day2QuizRewardStageView from './Day2QuizRewardStageView.vue'
import Day2QuizResultStageView from './Day2QuizResultStageView.vue'
import FreeActivityStageView from './FreeActivityStageView.vue'
import LearningStageView from './LearningStageView.vue'
import IncomeSettlementStageView from './IncomeSettlementStageView.vue'
import PayslipStageView from './PayslipStageView.vue'
import PlaceholderStageView from './PlaceholderStageView.vue'
import QuizStageView from './QuizStageView.vue'
import SettlementStageView from './SettlementStageView.vue'
import WaitingStageView from './WaitingStageView.vue'

const stageComponents: Record<string, Component> = {
  DAY1_LOBBY: WaitingStageView,
  DAY1_QUIZ: QuizStageView,
  DAY1_QUIZ_RESULT: SettlementStageView,
  DAY1_INCOME_SETTLEMENT: IncomeSettlementStageView,
  DAY1_PAYSLIP_GUIDED: PayslipStageView,
  DAY1_KNOWLEDGE_GUIDED: LearningStageView,
  DAY1_SHOP_GUIDED: LearningStageView,
  DAY1_FINANCE_GUIDED: LearningStageView,
  DAY1_FREE_ACTIVITY: FreeActivityStageView,
  DAY1_SUMMARY: DaySummaryStageView,
  DAY1_CLOSED: DaySummaryStageView,
  DAY2_LOGIN: Day2FinanceStageView,
  DAY2_SETTLEMENT_WAIT: Day2FinanceStageView,
  DAY2_FINANCE_SETTLEMENT: Day2FinanceStageView,
  DAY2_RESULT_GUIDED: Day2FinanceStageView,
  DAY2_QUIZ: QuizStageView,
  DAY2_QUIZ_RESULT: Day2QuizResultStageView,
  DAY2_QUIZ_SETTLEMENT: Day2QuizRewardStageView,
  DAY2_TAX_INFLATION_GUIDED: Day2EducationStageView,
  DAY2_INFLATION_APPLY: Day2InflationStageView,
  DAY2_FINAL_SHOP: Day2FinalShopStageView,
  DAY2_FUND_COST: Day2FundStageView,
  DAY2_FUND_VOTE: Day2FundStageView,
  DAY2_FUND_RESULT: Day2FundStageView,
  DAY2_FINAL_RESULT: Day2FinalResultStageView,
  DAY2_SURVEY: Day2FinalResultStageView,
  DAY2_COMPLETED: Day2FinalResultStageView,
}

export function getStudentStageComponent(stage?: string) {
  if (!stage) return WaitingStageView
  return stageComponents[stage] ?? PlaceholderStageView
}

export function isPlaceholderStage(stage?: string) {
  return getStudentStageComponent(stage) === PlaceholderStageView
}
