import { useMemo } from 'react';
import {
  CHARACTER_SCALE,
  EMPTY_EQUIPPED,
  type CosmeticDTO,
} from '@character-core';
import { CharacterPixelCanvas } from './CharacterStage';

/**
 * 상품 미리보기는 언제나 몸에 착용한 모습이다. 잘라낸 단독 스프라이트가 없고,
 * 꼬리·날개처럼 몸이 없으면 형태를 알 수 없는 상품이 있다(규격 §2.5 검토 결과).
 *
 * 배율은 호출하는 쪽이 정한다. 규격 §1.1의 정수 배율만 허용하며,
 * 컨테이너가 캔버스를 줄이지 못하게 CSS에서 max-width를 걸지 않는다(§1.2).
 */
export function ProductPreview({
  item,
  compact = false,
  scale,
}: {
  item: CosmeticDTO;
  compact?: boolean;
  scale?: number;
}) {
  const equipped = useMemo(
    () => ({ ...EMPTY_EQUIPPED, [item.slot]: item.id }),
    [item.id, item.slot],
  );
  const displayScale = scale
    ?? (compact ? CHARACTER_SCALE.thumbnail : CHARACTER_SCALE.detail);

  // 카드·착용 슬롯·보관함 항목이 모두 button 안에서 이 컴포넌트를 쓴다. button은 phrasing
  // content만 담을 수 있으므로 래퍼를 div가 아니라 span으로 둔다(CSS가 grid로 배치한다).
  return (
    <span className="product-visual" aria-label={`${item.name} 착용 미리보기`}>
      <CharacterPixelCanvas
        className={compact ? 'character-pixel-canvas--compact' : 'character-pixel-canvas--card'}
        displayScale={displayScale}
        equipped={equipped}
        label={`${item.name} 도트 착용 모습`}
      />
    </span>
  );
}
