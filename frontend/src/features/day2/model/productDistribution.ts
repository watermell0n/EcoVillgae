import type { ProductDistributionItem } from '../api/finalResult'

export interface StudentDistributionGroup {
  studentId: string
  participantNumber: number
  nickname: string | null
  totalQuantity: number
  items: ProductDistributionItem[]
}

export interface DayDistributionGroup {
  dayNumber: 1 | 2
  totalQuantity: number
  students: StudentDistributionGroup[]
}

export function groupProductDistribution(items: ProductDistributionItem[]): DayDistributionGroup[] {
  return ([1, 2] as const).map((dayNumber) => {
    const students = new Map<string, {
      participantNumber: number
      nickname: string | null
      products: Map<string, ProductDistributionItem>
    }>()

    for (const item of items.filter((candidate) => candidate.dayNumber === dayNumber)) {
      const student = students.get(item.studentId) ?? {
        participantNumber: item.participantNumber,
        nickname: item.nickname,
        products: new Map<string, ProductDistributionItem>(),
      }
      const existing = student.products.get(item.productId)
      student.products.set(item.productId, existing
        ? {
            ...existing,
            quantity: existing.quantity + item.quantity,
            paidAmount: existing.paidAmount + item.paidAmount,
          }
        : { ...item })
      students.set(item.studentId, student)
    }

    const groupedStudents = [...students.entries()]
      .map(([studentId, student]) => {
        const studentItems = [...student.products.values()]
          .sort((left, right) => left.productName.localeCompare(right.productName, 'ko-KR'))
        return {
          studentId,
          participantNumber: student.participantNumber,
          nickname: student.nickname,
          totalQuantity: studentItems.reduce((total, item) => total + item.quantity, 0),
          items: studentItems,
        }
      })
      .sort((left, right) => left.participantNumber - right.participantNumber)

    return {
      dayNumber,
      totalQuantity: groupedStudents.reduce((total, student) => total + student.totalQuantity, 0),
      students: groupedStudents,
    }
  })
}
