import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StudentSessionClosedView from './StudentSessionClosedView.vue'

describe('StudentSessionClosedView', () => {
  it('shows a dedicated day-one closing message', () => {
    const wrapper = mount(StudentSessionClosedView, { props: { dayNumber: 1 } })

    expect(wrapper.get('[role="status"]').text()).toContain('1일차 활동이 마감되었습니다')
    expect(wrapper.text()).toContain('활동 결과가 안전하게 저장되었어요')
    expect(wrapper.text()).toContain('2일차에 다시 만나요')
  })

  it('keeps the existing day-two course closing message', () => {
    const wrapper = mount(StudentSessionClosedView, { props: { dayNumber: 2 } })

    expect(wrapper.get('[role="status"]').text()).toContain('수업이 종료되었습니다')
    expect(wrapper.text()).toContain('참여해 줘서 고마워요')
  })
})
