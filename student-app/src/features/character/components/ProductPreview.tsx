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
 */
export function ProductPreview({
  item,
  compact = false,
}: {
  item: CosmeticDTO;
  compact?: boolean;
}) {
  const equipped = useMemo(
    () => ({ ...EMPTY_EQUIPPED, [item.slot]: item.id }),
    [item.id, item.slot],
  );

  return (
    <div className="product-visual" aria-label={`${item.name} 착용 미리보기`}>
      <CharacterPixelCanvas
        className={compact ? 'character-pixel-canvas--compact' : 'character-pixel-canvas--card'}
        displayScale={compact ? CHARACTER_SCALE.thumbnail : CHARACTER_SCALE.detail}
        equipped={equipped}
        label={`${item.name} 도트 착용 모습`}
      />
    </div>
  );
}
