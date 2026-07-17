export const SERVICE_NAME = '이코빌리지'
export const CURRENCY_UNIT = '비빔'
export const DEMO_PARTICIPATION_CODES = ['ECO-01', 'ECO-02', 'ECO-03'] as const

export function formatCurrency(amount: number) {
  return `${amount.toLocaleString('ko-KR')}${CURRENCY_UNIT}`
}
