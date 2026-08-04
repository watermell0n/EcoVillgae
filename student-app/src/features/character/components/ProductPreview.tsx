import { useMemo } from 'react';
import { EMPTY_EQUIPPED, type CosmeticDTO } from '../types';
import { CharacterPixelCanvas } from './CharacterStage';

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
        displayScale={compact ? 2 : 3}
        equipped={equipped}
        label={`${item.name} 도트 착용 모습`}
      />
    </div>
  );
}
