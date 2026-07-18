import { describe, expect, it } from 'vitest'

import LearningStageView from './LearningStageView.vue'
import { getStudentStageComponent } from './stageComponents'

describe('student stage components', () => {
  it('uses the paged learning view for day 2 financial knowledge education', () => {
    expect(getStudentStageComponent('DAY2_TAX_INFLATION_GUIDED')).toBe(LearningStageView)
  })
})
