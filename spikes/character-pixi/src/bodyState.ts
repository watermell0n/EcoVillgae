import type { BodyState } from './types';

export const INCOME_BASE = 100;
export const DAYS = 2;

export const BODY_THRESHOLDS = {
  chubby: { enter: 0.6, leave: 0.5 },
  slim: { enter: 0.6, leave: 0.5 },
  verySlim: { enter: 0.9, leave: 0.8 },
} as const;

/**
 * 체형은 언제든 되돌아올 수 있으며 진입/이탈 기준을 다르게 둔다.
 * 경계값 근처에서 잔액이 변해도 티어가 깜빡이지 않는다.
 */
export function computeBodyState(
  previous: BodyState,
  balance: number,
  spendTotal: number,
  incomeBase = INCOME_BASE,
  days = DAYS,
): BodyState {
  const denominator = incomeBase * days;
  const saveRatio = balance / denominator;
  const spendRatio = spendTotal / denominator;

  const chubbyBoundary = previous === 'CHUBBY'
    ? BODY_THRESHOLDS.chubby.leave
    : BODY_THRESHOLDS.chubby.enter;
  if (saveRatio >= chubbyBoundary) return 'CHUBBY';

  const verySlimBoundary = previous === 'VERY_SLIM'
    ? BODY_THRESHOLDS.verySlim.leave
    : BODY_THRESHOLDS.verySlim.enter;
  if (spendRatio >= verySlimBoundary) return 'VERY_SLIM';

  const slimBoundary = previous === 'SLIM' || previous === 'VERY_SLIM'
    ? BODY_THRESHOLDS.slim.leave
    : BODY_THRESHOLDS.slim.enter;
  if (spendRatio >= slimBoundary) return 'SLIM';

  return 'BASE';
}
