import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import Day1ActivityIntroStageView from './Day1ActivityIntroStageView.vue'
import LearningStageView from './LearningStageView.vue'
import { getStudentStageComponent } from './stageComponents'

describe('student stage components', () => {
  it('uses a complete activity introduction for the day-one profile stage', () => {
    expect(getStudentStageComponent('DAY1_PROFILE')).toBe(Day1ActivityIntroStageView)

    const wrapper = mount(Day1ActivityIntroStageView)
    expect(wrapper.text()).toContain('오늘 할 일을 차례대로 확인해요')
    expect(wrapper.text()).toContain('독서 퀴즈 풀기')
    expect(wrapper.text()).not.toContain('이후 구현에서 교체됩니다')
  })

  it('uses the paged learning view for day 2 financial knowledge education', () => {
    expect(getStudentStageComponent('DAY2_TAX_INFLATION_GUIDED')).toBe(LearningStageView)
  })
})
