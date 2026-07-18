import { describe, expect, it } from 'vitest'

import type { ProductDistributionItem } from '../api/finalResult'
import { groupProductDistribution } from './productDistribution'

describe('groupProductDistribution', () => {
  it('groups purchases by day, student, and product', () => {
    const item = (overrides: Partial<ProductDistributionItem>): ProductDistributionItem => ({
      dayNumber: 1,
      productId: 'pencil',
      productName: '연필 세트',
      studentId: 'student-1',
      participantNumber: 1,
      nickname: '비빔이',
      quantity: 1,
      paidAmount: 10,
      ...overrides,
    })

    const result = groupProductDistribution([
      item({}),
      item({ quantity: 2, paidAmount: 20 }),
      item({ dayNumber: 2, quantity: 1, paidAmount: 12 }),
      item({ studentId: 'student-2', participantNumber: 2, nickname: '마을이' }),
    ])

    expect(result).toHaveLength(2)
    expect(result[0].totalQuantity).toBe(4)
    expect(result[0].students).toHaveLength(2)
    expect(result[0].students[0].items).toHaveLength(1)
    expect(result[0].students[0].items[0]).toMatchObject({ quantity: 3, paidAmount: 30 })
    expect(result[1].totalQuantity).toBe(1)
    expect(result[1].students[0].items[0]).toMatchObject({ quantity: 1, paidAmount: 12 })
  })
})
