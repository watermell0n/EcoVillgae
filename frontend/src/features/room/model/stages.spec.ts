import { describe, expect, it } from 'vitest'

import { getAdjacentStages, getRoomStage, ROOM_STAGES } from './stages'

describe('room stages', () => {
  it('keeps the complete two-day stage order from the API specification', () => {
    expect(ROOM_STAGES).toHaveLength(25)
    expect(ROOM_STAGES[0]?.id).toBe('DAY1_LOBBY')
    expect(ROOM_STAGES.at(-1)?.id).toBe('DAY2_COMPLETED')
  })

  it('returns only the previous and next stage for navigation', () => {
    expect(getAdjacentStages('DAY1_QUIZ').previous?.id).toBe('DAY1_PROFILE')
    expect(getAdjacentStages('DAY1_QUIZ').next?.id).toBe('DAY1_INCOME_SETTLEMENT')
    expect(getAdjacentStages('DAY1_LOBBY').previous).toBeUndefined()
  })

  it('provides a Korean label and description for every stage', () => {
    expect(getRoomStage('DAY1_FREE_ACTIVITY')).toMatchObject({
      label: '자유 경제활동',
      day: 1,
    })
    expect(ROOM_STAGES.every((stage) => stage.label && stage.description)).toBe(true)
  })
})
