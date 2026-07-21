import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ParticipationCodeDialog from './ParticipationCodeDialog.vue'

const students = [
  {
    studentId: 'student-1',
    participantNumber: 1,
    participationCode: 'ECO-TEST-01',
    nickname: '비빔이',
    status: 'ACTIVE',
    online: true,
    connectionStatus: 'ONLINE' as const,
    lastSeenAt: '2026-07-18T00:00:00Z',
  },
]

describe('ParticipationCodeDialog', () => {
  it('shows student codes and emits copy and close actions', async () => {
    const wrapper = mount(ParticipationCodeDialog, {
      props: {
        students,
        loading: false,
        errorMessage: '',
        copiedCode: null,
      },
    })

    expect(wrapper.text()).toContain('학생 번호')
    expect(wrapper.text()).toContain('01')
    expect(wrapper.text()).not.toContain('ECO-TEST-01')
    expect(wrapper.text()).toContain('1번')
    expect(wrapper.text()).toContain('비빔이')

    await wrapper.get('[aria-label="01 학생 번호 복사"]').trigger('click')
    expect(wrapper.emitted('copy')).toEqual([['01']])

    const closeButtons = wrapper.findAll('.confirm-modal__actions button')
    expect(closeButtons).toHaveLength(1)
    expect(wrapper.find('.participation-code-dialog__actions').exists()).toBe(true)
    await closeButtons[0]!.trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
