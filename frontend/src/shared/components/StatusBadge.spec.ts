import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StatusBadge from './StatusBadge.vue'

describe('StatusBadge', () => {
  it('renders its label and tone', () => {
    const wrapper = mount(StatusBadge, {
      props: {
        label: '연결됨',
        tone: 'success',
      },
    })

    expect(wrapper.text()).toContain('연결됨')
    expect(wrapper.classes()).toContain('status-badge--success')
  })
})

