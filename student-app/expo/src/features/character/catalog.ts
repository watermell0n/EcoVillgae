import type { CosmeticDTO, Slot, Theme } from './types';

const ROOM_ID = 'demo-room';

type Seed = [name: string, spriteKey: string, price: number, theme: Theme];

/**
 * 첨부된 기본·계절·스포츠·직업·판타지 상품표의 64종을 사용한다.
 * 상품 미리보기와 실제 착용은 같은 spriteKey를 코드형 픽셀 렌더러에 전달한다.
 */
const SEEDS: Partial<Record<Slot, Seed[]>> = {
  HAT: [
    ['빨간 캡', 'hat_red_cap', 30, 'BASIC'],
    ['비니', 'hat_teal_beanie', 30, 'BASIC'],
    ['밀짚모자', 'hat_straw', 25, 'SEASONAL'],
    ['비치 모자', 'hat_beach', 30, 'SEASONAL'],
    ['방울 비니', 'hat_pom_beanie', 30, 'SEASONAL'],
    ['야구 모자 · 파랑', 'hat_baseball_blue', 30, 'SPORTS'],
    ['야구 모자 · 빨강', 'hat_baseball_red', 30, 'SPORTS'],
    ['야구 모자 · 남색', 'hat_baseball_navy', 30, 'SPORTS'],
    ['야구 모자 · 초록', 'hat_baseball_green', 30, 'SPORTS'],
    ['야구 모자 · 노랑', 'hat_baseball_yellow', 30, 'SPORTS'],
    ['요리사 모자', 'hat_chef', 30, 'CAREER'],
    ['탐험가 모자', 'hat_safari', 35, 'CAREER'],
    ['우주 헬멧', 'hat_space_helmet', 45, 'CAREER'],
    ['안전 경찰 모자', 'hat_safety_police', 30, 'CAREER'],
    ['파일럿 모자', 'hat_pilot', 35, 'CAREER'],
    ['왕관', 'hat_crown', 80, 'FANTASY'],
    ['마법사 모자', 'hat_wizard', 40, 'FANTASY'],
    ['기사 투구', 'hat_knight', 40, 'FANTASY'],
    ['공룡 후드', 'hat_dinosaur', 35, 'FANTASY'],
  ],
  FACE: [
    ['선글라스', 'face_sunglasses', 25, 'BASIC'],
    ['헤드폰', 'face_headphones', 35, 'BASIC'],
    ['물안경', 'face_goggles', 20, 'SEASONAL'],
    ['스키 고글', 'face_ski_goggles', 30, 'SEASONAL'],
  ],
  NECK: [
    ['목도리', 'neck_scarf', 25, 'SEASONAL'],
    ['니트 목도리', 'neck_knit_scarf', 25, 'SEASONAL'],
  ],
  TOP: [
    ['파란 티셔츠', 'top_blue_tshirt', 35, 'BASIC'],
    ['노란 후드', 'top_yellow_hoodie', 45, 'BASIC'],
    ['초록 조끼', 'top_green_vest', 40, 'BASIC'],
    ['수영복', 'top_swimsuit', 40, 'SEASONAL'],
    ['패딩 점퍼', 'top_padding', 55, 'SEASONAL'],
    ['축구 유니폼', 'top_soccer', 45, 'SPORTS'],
    ['핀스트라이프', 'top_baseball_pinstripe', 55, 'SPORTS'],
    ['야구복 · 빨강', 'top_baseball_red', 50, 'SPORTS'],
    ['야구복 · 파랑', 'top_baseball_blue', 50, 'SPORTS'],
    ['야구복 · 남색', 'top_baseball_navy', 50, 'SPORTS'],
    ['원정 회색', 'top_baseball_gray', 50, 'SPORTS'],
    ['우주복', 'top_spacesuit', 60, 'CAREER'],
    ['안전 경찰복', 'top_safety_police', 50, 'CAREER'],
    ['법조인 정장', 'top_legal_professional', 55, 'CAREER'],
    ['파일럿 제복', 'top_pilot', 55, 'CAREER'],
    ['마법사 로브', 'top_wizard_robe', 55, 'FANTASY'],
    ['판타지 갑옷', 'top_fantasy_armor', 55, 'FANTASY'],
    ['공룡 옷', 'top_dinosaur', 55, 'FANTASY'],
  ],
  BOTTOM: [
    ['멜빵바지', 'bottom_overalls', 45, 'BASIC'],
  ],
  SHOES: [
    ['운동화', 'shoes_sneakers', 30, 'BASIC'],
    ['빨간 장화', 'shoes_red_boots', 35, 'SEASONAL'],
    ['샌들', 'shoes_sandals', 25, 'SEASONAL'],
    ['아쿠아슈즈', 'shoes_aqua', 25, 'SEASONAL'],
    ['스노우 부츠', 'shoes_snow_boots', 40, 'SEASONAL'],
    ['우주 부츠', 'shoes_space_boots', 40, 'CAREER'],
  ],
  BACK: [
    ['배낭', 'back_backpack', 40, 'BASIC'],
    ['서류 가방', 'back_briefcase', 30, 'CAREER'],
    ['요정 날개', 'back_fairy_wings', 50, 'FANTASY'],
    ['공룡 꼬리', 'back_dinosaur_tail', 30, 'FANTASY'],
  ],
  HAND: [
    ['야구 글러브', 'hand_baseball_glove', 30, 'SPORTS'],
    ['별빛 마법봉', 'hand_magic_wand', 30, 'FANTASY'],
    ['장난감 검', 'hand_toy_sword', 30, 'FANTASY'],
  ],
  EFFECT: [
    ['튜브', 'effect_swim_tube', 35, 'SEASONAL'],
    ['몽실 구름', 'effect_fluffy_cloud', 45, 'FANTASY'],
  ],
  BACKGROUND: [
    ['봄 벚꽃', 'bg_spring', 80, 'SEASONAL'],
    ['여름 바다', 'bg_summer_sea', 80, 'SEASONAL'],
    ['가을 단풍', 'bg_autumn', 80, 'SEASONAL'],
    ['겨울 눈', 'bg_winter', 80, 'SEASONAL'],
    ['우주 배경', 'bg_space', 80, 'CAREER'],
  ],
};

function build(): CosmeticDTO[] {
  const out: CosmeticDTO[] = [];
  let displayOrder = 0;
  for (const [slot, seeds] of Object.entries(SEEDS) as Array<[Slot, Seed[]]>) {
    for (const [name, spriteKey, price, theme] of seeds) {
      out.push({
        id: spriteKey,
        roomId: ROOM_ID,
        name,
        slot,
        price,
        spriteKey,
        theme,
        displayOrder: ++displayOrder,
        status: 'ACTIVE',
      });
    }
  }
  return out;
}

export const CATALOG: CosmeticDTO[] = build();

export const CATALOG_BY_ID: Record<string, CosmeticDTO> = Object.fromEntries(
  CATALOG.map((cosmetic) => [cosmetic.id, cosmetic]),
);
