/**
 * 도트 캐릭터 논리 캔버스. 단일 규격이며 예외를 두지 않는다.
 * 규격: docs/design/CHARACTER_ASSET_SPEC.md §1, §2
 */

export const CHARACTER_CANVAS_WIDTH = 64;
export const CHARACTER_CANVAS_HEIGHT = 64;

/**
 * 미러 축은 픽셀이 아니라 x=31과 x=32의 경계다.
 * 좌우 대칭인 픽셀 런 [a, b]는 a + b === 63을 만족한다. 따라서 대칭 요소의 폭은 짝수다.
 */
export function mirrorX(x: number): number {
  return CHARACTER_CANVAS_WIDTH - 1 - x;
}

/** 대칭 판정용. 런 [a, b]가 캔버스 중심을 기준으로 대칭인지. */
export function isMirroredRun(left: number, right: number): boolean {
  return left + right === CHARACTER_CANVAS_WIDTH - 1;
}

/**
 * 표시 크기는 정수 배율만 허용한다. 비정수 배율은 도트 경계를 뭉갠다.
 * 배율을 컨테이너 크기에서 계산하지 말고 이 함수로 고정한다.
 */
export function characterDisplaySize(scale: number): { width: number; height: number } {
  if (!Number.isInteger(scale) || scale < 1) {
    throw new Error(`캐릭터 확대 배율은 1 이상의 정수여야 한다: ${scale}`);
  }
  return {
    width: CHARACTER_CANVAS_WIDTH * scale,
    height: CHARACTER_CANVAS_HEIGHT * scale,
  };
}

/** 화면별 권장 배율. 규격 §1.1 */
export const CHARACTER_SCALE = {
  /** 상점 카드 썸네일, 착용 슬롯 스트립 */
  thumbnail: 2,
  /** 상품 상세 */
  detail: 3,
  /** 상점 미리보기 */
  preview: 4,
  /** 캐릭터 전체 화면 */
  stage: 5,
} as const;
