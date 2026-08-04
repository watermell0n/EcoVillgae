import { CATALOG_BY_ID } from './catalog';
import type { CosmeticDTO, EquippedMap, Slot } from './types';

const FULL_BODY_TOP_KEYS = new Set([
  'top_wizard_robe',
  'top_spacesuit',
  'top_fantasy_armor',
  'top_dinosaur',
]);

/**
 * 같은 슬롯은 교체하고 다른 슬롯은 유지한다.
 * 전신 의상과 하의처럼 실제로 겹칠 수 없는 조합만 명시적으로 충돌시킨다.
 */
export function conflictsFor(item: CosmeticDTO, equipped: EquippedMap): Slot[] {
  const conflicts = new Set<Slot>();
  if (item.slot === 'TOP' && FULL_BODY_TOP_KEYS.has(item.spriteKey)) {
    conflicts.add('BOTTOM');
  }
  if (item.slot === 'BOTTOM') {
    const currentTop = equipped.TOP ? CATALOG_BY_ID[equipped.TOP] : null;
    if (currentTop && FULL_BODY_TOP_KEYS.has(currentTop.spriteKey)) {
      conflicts.add('TOP');
    }
  }
  return [...conflicts];
}
