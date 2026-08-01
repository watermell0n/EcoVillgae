import type { BodyState } from './types';

export const BODY_THRESHOLDS = {
  chubby: { enter: 0.7, leave: 0.6 },
  slim: { enter: 0.65, leave: 0.55 },
  verySlim: { enter: 0.85, leave: 0.75 },
} as const;

/**
 * 저축 비중이 높으면 포근해지고 소비 비중이 높으면 가벼워진다.
 * 진입/이탈 기준을 다르게 두어 경계값 근처에서 체형이 깜빡이지 않게 한다.
 */
export function computeBodyState(
  previous: BodyState,
  balance: number,
  spendTotal: number,
): BodyState {
  const denominator = Math.max(1, balance + spendTotal);
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
