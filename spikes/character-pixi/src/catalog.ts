import type { CosmeticDTO, Slot, Theme } from './types';

// docs/plans/2026-07-29-character-cosmetic-pricing-and-seed.md의 77종 기준.
// 실제 제품에서는 백엔드 character_cosmetics 시드(Flyway)가 원본이 된다.
const ROOM_ID = 'demo-room';

type Seed = [name: string, spriteKey: string, price: number, theme: Theme];

const SEEDS: Record<Slot, Seed[]> = {
  HAT: [
    ['빨간 캡', 'hat_red_cap', 30, 'BASIC'],
    ['파티모자', 'hat_party', 20, 'EVENT'],
    ['왕관', 'hat_crown', 80, 'FANTASY'],
    ['밀짚모자', 'hat_straw', 25, 'SEASONAL'],
    ['비니', 'hat_beanie', 25, 'BASIC'],
    ['비치 모자', 'hat_beach', 30, 'SEASONAL'],
    ['방울 비니', 'hat_pom_beanie', 30, 'SEASONAL'],
    ['마법사 모자', 'hat_wizard', 40, 'FANTASY'],
    ['요리사 모자', 'hat_chef', 30, 'CAREER'],
    ['탐험가 모자', 'hat_safari', 35, 'CAREER'],
    ['야구 모자(파랑)', 'hat_baseball_blue', 30, 'SPORTS'],
    ['야구 모자(빨강)', 'hat_baseball_red', 30, 'SPORTS'],
    ['야구 모자(남색)', 'hat_baseball_navy', 30, 'SPORTS'],
    ['야구 모자(초록)', 'hat_baseball_green', 30, 'SPORTS'],
    ['야구 모자(노랑)', 'hat_baseball_yellow', 30, 'SPORTS'],
    ['우주 헬멧', 'hat_space_helmet', 45, 'CAREER'],
    ['기사 투구', 'hat_knight', 40, 'FANTASY'],
    ['공룡 후드', 'hat_dinosaur', 35, 'FANTASY'],
    ['안전 경찰 모자', 'hat_safety_police', 30, 'CAREER'],
    ['파일럿 모자', 'hat_pilot', 35, 'CAREER'],
    ['면사포', 'hat_veil', 30, 'EVENT'],
  ],
  FACE: [
    ['선글라스', 'face_sunglasses', 20, 'BASIC'],
    ['물안경', 'face_goggles', 20, 'SEASONAL'],
    ['스키 고글', 'face_ski_goggles', 30, 'SEASONAL'],
    ['헤드폰', 'face_headphones', 40, 'BASIC'],
  ],
  NECK: [
    ['목도리', 'neck_scarf', 25, 'SEASONAL'],
    ['나비넥타이', 'neck_bowtie', 15, 'EVENT'],
    ['니트 목도리', 'neck_knit_scarf', 25, 'SEASONAL'],
    ['목걸이', 'neck_necklace', 25, 'EVENT'],
  ],
  TOP: [
    ['파란 티셔츠', 'top_blue_tee', 40, 'BASIC'],
    ['노란 후드', 'top_yellow_hoodie', 50, 'BASIC'],
    ['초록 조끼', 'top_green_vest', 35, 'BASIC'],
    ['멜빵바지', 'top_overalls', 45, 'BASIC'],
    ['수영복', 'top_swimsuit', 40, 'SEASONAL'],
    ['패딩 점퍼', 'top_padding', 55, 'SEASONAL'],
    ['정장', 'top_suit', 60, 'EVENT'],
    ['마법사 로브', 'top_wizard_robe', 55, 'FANTASY'],
    ['한복', 'top_hanbok', 65, 'EVENT'],
    ['축구 유니폼', 'top_soccer', 45, 'SPORTS'],
    ['야구 유니폼(핀스트라이프)', 'top_baseball_pinstripe', 55, 'SPORTS'],
    ['야구 유니폼(빨강)', 'top_baseball_red', 50, 'SPORTS'],
    ['야구 유니폼(파랑)', 'top_baseball_blue', 50, 'SPORTS'],
    ['야구 유니폼(남색)', 'top_baseball_navy', 50, 'SPORTS'],
    ['야구 유니폼(원정 회색)', 'top_baseball_gray', 50, 'SPORTS'],
    ['우주복', 'top_spacesuit', 60, 'CAREER'],
    ['판타지 갑옷', 'top_fantasy_armor', 55, 'FANTASY'],
    ['공룡 옷', 'top_dinosaur', 55, 'FANTASY'],
    ['안전 경찰복', 'top_safety_police', 50, 'CAREER'],
    ['법조인 정장', 'top_legal_professional', 55, 'CAREER'],
    ['파일럿 제복', 'top_pilot', 55, 'CAREER'],
    ['웨딩드레스', 'top_wedding_dress', 65, 'EVENT'],
  ],
  BOTTOM: [
    ['줄무늬 바지', 'bottom_striped_pants', 40, 'BASIC'],
    ['청바지', 'bottom_jeans', 40, 'BASIC'],
    ['반바지', 'bottom_shorts', 30, 'SEASONAL'],
    ['치마', 'bottom_skirt', 35, 'BASIC'],
    ['정장 바지', 'bottom_slacks', 45, 'EVENT'],
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
    ['배낭', 'back_backpack', 45, 'BASIC'],
    ['요정 날개', 'back_fairy_wings', 50, 'FANTASY'],
    ['공룡 꼬리', 'back_dinosaur_tail', 30, 'FANTASY'],
  ],
  HAND: [
    ['야구 글러브', 'hand_baseball_glove', 30, 'SPORTS'],
    ['별빛 마법봉', 'hand_magic_wand', 30, 'FANTASY'],
    ['장난감 검', 'hand_toy_sword', 30, 'FANTASY'],
    ['서류 가방', 'hand_briefcase', 30, 'CAREER'],
    ['꽃다발', 'hand_bouquet', 25, 'EVENT'],
  ],
  EFFECT: [
    ['튜브', 'effect_swim_tube', 35, 'SEASONAL'],
    ['몽실 구름', 'effect_fluffy_cloud', 45, 'FANTASY'],
  ],
  BACKGROUND: [
    ['봄 벚꽃', 'bg_spring', 45, 'SEASONAL'],
    ['여름 바다', 'bg_summer_sea', 50, 'SEASONAL'],
    ['가을 단풍', 'bg_autumn', 45, 'SEASONAL'],
    ['겨울 눈', 'bg_winter', 50, 'SEASONAL'],
    ['우주', 'bg_space', 55, 'CAREER'],
  ],
};

function build(): CosmeticDTO[] {
  const out: CosmeticDTO[] = [];
  let order = 0;
  (Object.keys(SEEDS) as Slot[]).forEach((slot) => {
    for (const [name, spriteKey, price, theme] of SEEDS[slot]) {
      out.push({
        id: spriteKey,
        roomId: ROOM_ID,
        name,
        slot,
        price,
        spriteKey,
        theme,
        displayOrder: ++order,
        status: 'ACTIVE',
      });
    }
  });
  return out;
}

export const CATALOG: CosmeticDTO[] = build();

export const CATALOG_BY_ID: Record<string, CosmeticDTO> = Object.fromEntries(
  CATALOG.map((cosmetic) => [cosmetic.id, cosmetic]),
);
