import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { GuidedLearning } from '../../features/learning/api/learning'
import TeacherLearningMirror from './TeacherLearningMirror.vue'

const rationalConsumption: GuidedLearning = {
  stage: 'DAY1_SHOP_EDUCATION',
  mode: 'GUIDED',
  syncVersion: 1,
  currentIndex: 0,
  pages: [],
  currentPage: {
    pageId: 'page-1',
    topicCode: 'SHOP',
    topicTitle: '합리적인 소비',
    pageOrder: 1,
    title: '필요와 원하는 것을 구분해요',
    content: '물건을 사기 전에 차례로 생각해요.',
    keyPoints: [
      '생활에 꼭 필요한 것부터 선택해요.',
      '가격과 지갑 잔액, 앞으로 쓸 돈을 함께 비교해요.',
    ],
    example: '공책을 먼저 사요.',
  },
}
rationalConsumption.pages = [rationalConsumption.currentPage]

describe('TeacherLearningMirror', () => {
  it('uses the marker-free rational consumption layout on the teacher page', () => {
    const wrapper = mount(TeacherLearningMirror, { props: { learning: rationalConsumption } })

    expect(wrapper.get('.learning-card').classes()).toContain('learning-card--rational')
    expect(wrapper.findAll('.learning-card li').map((item) => item.text())).toEqual(
      rationalConsumption.currentPage.keyPoints,
    )
  })
})
