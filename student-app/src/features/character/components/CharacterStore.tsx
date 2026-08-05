import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type RefObject,
} from 'react';
import { ApiError } from '../../../api/client';
import { calculateStoreTotal } from '../../../api/studentFeatures';
import {
  acquireMockCharacter,
  compareStoreItems,
  equippedMap,
  fetchCharacterProfile,
  fetchCharacterStore,
  fetchOwnedCharacters,
  isPurchasableStatus,
  saveCharacterEquipment,
  setCharacterEquipped,
  type CharacterStoreItem,
} from '../characterApi';
import {
  CHARACTER_SCALE,
  SLOT_LABEL,
  conflictsFor,
  hasCharacterAsset,
  type EquippedMap,
  type Slot,
} from '@character-core';
import { CharacterStage } from './CharacterStage';
import { ProductPreview } from './ProductPreview';

/** 상점 UI 분류. 내부 착용 슬롯과 1:1이 아니다 — PROP이 특수 슬롯 4종을 묶는다. */
type Category = 'ALL' | 'HAT' | 'FACE' | 'TOP' | 'BOTTOM' | 'SHOES' | 'BACKGROUND' | 'PROP';

const CATEGORIES: Array<{ value: Category; icon: string; label: string }> = [
  { value: 'ALL', icon: '★', label: '추천' },
  { value: 'HAT', icon: '⌒', label: '모자' },
  { value: 'FACE', icon: '☺', label: '얼굴' },
  { value: 'TOP', icon: '♜', label: '상의' },
  { value: 'BOTTOM', icon: '▥', label: '하의' },
  { value: 'SHOES', icon: '◡', label: '신발' },
  { value: 'BACKGROUND', icon: '❖', label: '배경' },
  { value: 'PROP', icon: '✦', label: '등장식' },
];

/**
 * 등장식 탭이 묶는 내부 슬롯.
 *
 * 탭을 슬롯 수만큼 두면 태블릿에서 탭이 두 줄을 넘고 각 탭에 상품이 2~3개뿐이다.
 * 데이터 구조는 그대로 두고 상점 UI 분류만 합친다(가이드라인 §5.3).
 */
const PROP_SLOTS: Slot[] = ['BACK', 'NECK', 'HAND', 'EFFECT'];

/** 착용 스트립에 보여 주는 칸. PROP은 위 4종의 대표를 보여준다(가이드라인 §7.1). */
const DISPLAY_SLOTS: Array<{ key: Category; label: string }> = [
  { key: 'HAT', label: '모자' },
  { key: 'FACE', label: '얼굴' },
  { key: 'TOP', label: '상의' },
  { key: 'BOTTOM', label: '하의' },
  { key: 'SHOES', label: '신발' },
  { key: 'BACKGROUND', label: '배경' },
  { key: 'PROP', label: '등장식' },
];

/**
 * 카드 한 장이 차지해야 하는 최소 폭.
 *
 * 카드 안 미리보기 캔버스는 128px(배율 2) 고정이다. 열이 이보다 좁아지면 카드가
 * `overflow: hidden`으로 캔버스를 잘라내 캐릭터가 반쪽만 보인다. 캔버스를 줄이는 것도
 * 안 된다 — 비정수 배율이 되어 도트가 깨진다(규격 §1.2). 그래서 열 수를 줄인다.
 */
const CARD_MIN_WIDTH = 128;
const GRID_GAP = 12;
const MAX_COLUMNS = 6;

/**
 * 열 수별 한 페이지 상품 수. 열 수의 배수라야 마지막 줄이 어긋나지 않는다.
 * 데스크톱 6열 = 18개, 태블릿 3열 = 6개는 가이드라인 §5.4의 값이다.
 */
const PAGE_SIZE_BY_COLUMNS: Record<number, number> = {
  1: 4,
  2: 6,
  3: 6,
  4: 12,
  5: 15,
  6: 18,
};

/**
 * 화면별 상한 (가이드라인 §8). 태블릿·모바일에서는 폭이 남더라도 이 수를 넘기지 않는다.
 * 카드가 작아 여러 장 들어가는 것과 화면에 맞는 밀도는 다른 문제다.
 */
function columnCapFor(viewportWidth: number): number {
  if (viewportWidth <= 640) return 2;
  if (viewportWidth <= 1024) return 3;
  return MAX_COLUMNS;
}

/**
 * 가이드라인이 정한 열 수를 기본으로 쓰고, 그 수로 카드가 캔버스보다 좁아질 때만 줄인다.
 * 1440px에서 6열을 강행하면 카드가 107px가 되어 128px 캔버스가 잘렸다.
 */
function columnsFor(gridWidth: number, viewportWidth: number): number {
  const cap = columnCapFor(viewportWidth);
  if (gridWidth <= 0) return cap;
  const fits = Math.floor((gridWidth + GRID_GAP) / (CARD_MIN_WIDTH + GRID_GAP));
  return Math.min(cap, Math.max(1, fits));
}

/**
 * 열 수를 화면 폭이 아니라 **그리드가 실제로 받은 폭**에서 계산한다.
 *
 * 폭 기준 breakpoint로 잡았더니 1440px에서 6열이 되어 카드가 107px로 좁아지고
 * 128px 캔버스가 잘렸다. 사이드바·좌측 패널·패딩이 모두 폭을 먹기 때문에 화면 폭만으로는
 * 카드가 몇 장 들어가는지 알 수 없다. 컨테이너를 재면 그 계산이 필요 없다.
 */
function useGridLayout(ref: RefObject<HTMLDivElement | null>): {
  columns: number;
  perPage: number;
} {
  const [columns, setColumns] = useState(MAX_COLUMNS);

  useLayoutEffect(() => {
    const node = ref.current;
    if (!node) return;
    // 그리드 폭은 부모가 정하므로 열 수를 바꿔도 다시 흔들리지 않는다.
    const measure = () => setColumns(
      columnsFor(node.getBoundingClientRect().width, window.innerWidth),
    );
    measure();

    // 창 크기·회전이 주 신호다. 사이드바 접힘처럼 창 크기가 그대로인 변화까지 잡으려고
    // ResizeObserver를 함께 붙인다.
    window.addEventListener('resize', measure);
    window.addEventListener('orientationchange', measure);
    const observer = typeof ResizeObserver === 'function' ? new ResizeObserver(measure) : null;
    observer?.observe(node);
    return () => {
      window.removeEventListener('resize', measure);
      window.removeEventListener('orientationchange', measure);
      observer?.disconnect();
    };
  }, [ref]);

  return { columns, perPage: PAGE_SIZE_BY_COLUMNS[columns] ?? columns * 3 };
}

type CharacterShopProps = {
  classroomId: string;
  currencyName: string;
  walletBalance: number;
  onBuy: (item: CharacterStoreItem) => Promise<boolean>;
};

type PreviewOverrides = Partial<Record<Slot, string | null>>;

/** 상품 카드 상태. 표시와 클릭 동작을 이 네 가지로 통일한다(가이드라인 §5.5). */
type CardState = 'EQUIPPED' | 'OWNED' | 'BUYABLE' | 'SHORT';

function nextPreviewOverrides(
  current: PreviewOverrides,
  item: CharacterStoreItem,
  equipped: EquippedMap,
): PreviewOverrides {
  const next = { ...current };
  const composed = { ...equipped, ...current };

  // 이미 이 상품을 보고 있으면 벗긴다. 저장 상태와 같아지면 드래프트에서 지운다.
  if (composed[item.slot] === item.id) {
    if (equipped[item.slot] === item.id) next[item.slot] = null;
    else delete next[item.slot];
    return next;
  }

  for (const conflictSlot of conflictsFor(item, composed)) next[conflictSlot] = null;
  if (equipped[item.slot] === item.id) delete next[item.slot];
  else next[item.slot] = item.id;
  return next;
}

function matchesCategory(item: CharacterStoreItem, category: Category): boolean {
  if (category === 'ALL') return true;
  if (category === 'PROP') return PROP_SLOTS.includes(item.slot);
  return item.slot === category;
}

function cardStateFor(item: CharacterStoreItem, walletBalance: number, total: number): CardState {
  if (item.equipped) return 'EQUIPPED';
  if (item.owned) return 'OWNED';
  return walletBalance < total ? 'SHORT' : 'BUYABLE';
}

/**
 * 렌더할 수 없는 상품은 구매·착용을 막는다(가이드라인 §12.3).
 *
 * 임시 안전장치다. 배포 시점에는 카탈로그 64종 전부 에셋이 있어 언제나 true다.
 * 새 상품을 DB에만 추가하면 상점에는 보이지만 착용해도 변화가 없는데, 그 상태로
 * 재화를 쓰게 두지 않는다.
 */
function isRenderable(item: CharacterStoreItem): boolean {
  return hasCharacterAsset(item.spriteKey);
}

/**
 * 사용자에게 보여 줄 문구를 고른다.
 *
 * `ApiError.message`는 서버가 준 한국어 안내라 그대로 쓸 수 있다. 그 밖의 오류는
 * `TypeError: Failed to fetch`처럼 영어 원문이라 초등학생 화면에 띄울 수 없다.
 * `error instanceof Error ? error.message : fallback`으로 쓰면 네트워크 오류가 그대로 샌다.
 */
function userMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.message ? error.message : fallback;
}

export function CharacterShop({
  classroomId,
  currencyName,
  walletBalance,
  onBuy,
}: CharacterShopProps) {
  const [items, setItems] = useState<CharacterStoreItem[]>([]);
  const [profileVersion, setProfileVersion] = useState(0);
  const [category, setCategory] = useState<Category>('ALL');
  const [page, setPage] = useState(1);
  const [previewOverrides, setPreviewOverrides] = useState<PreviewOverrides>({});
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [propsExpanded, setPropsExpanded] = useState(false);
  const [message, setMessage] = useState<{ tone: 'info' | 'error'; text: string } | null>(null);
  const [loading, setLoading] = useState(true);
  /** 목록을 못 불러온 상태. "상품이 없다"와 "불러오지 못했다"를 구분한다. */
  const [loadFailed, setLoadFailed] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);
  const [purchasingId, setPurchasingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const gridRef = useRef<HTMLDivElement>(null);
  const { columns, perPage: itemsPerPage } = useGridLayout(gridRef);

  async function loadStore(): Promise<CharacterStoreItem[]> {
    const [nextItems, profile] = await Promise.all([
      fetchCharacterStore(classroomId),
      fetchCharacterProfile(classroomId),
    ]);
    setItems(nextItems);
    setProfileVersion(profile.state_version);
    setLoadFailed(false);
    return nextItems;
  }

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([fetchCharacterStore(classroomId), fetchCharacterProfile(classroomId)])
      .then(([nextItems, profile]) => {
        if (!active) return;
        setItems(nextItems);
        setProfileVersion(profile.state_version);
        setLoadFailed(false);
        // 다시 불러오기가 성공했으면 직전 오류 문구를 남기지 않는다.
        setMessage((current) => (current?.tone === 'error' ? null : current));
      })
      .catch((error: unknown) => {
        if (!active) return;
        setLoadFailed(true);
        setMessage({
          tone: 'error',
          text: userMessage(error, '캐릭터 상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'),
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [classroomId, reloadToken]);

  /**
   * 상점은 활성 상품 전체를 노출한다.
   *
   * 이전에는 "64×64 에셋이 등록된 상품"만 통과시키는 필터가 있어서 10종만 보였고,
   * 목록·미리보기·착용·카테고리·상세가 모두 그 배열에서 파생돼 실제 사용 가능한 상품이
   * 10종으로 묶여 있었다. 렌더 통일이 끝났으므로 필터를 없앤다(가이드라인 §5.1).
   */
  const visibleItems = useMemo(
    () => items.filter(isPurchasableStatus).sort(compareStoreItems),
    [items],
  );
  const categoryItems = useMemo(
    () => visibleItems.filter((item) => matchesCategory(item, category)),
    [category, visibleItems],
  );
  const totalPages = Math.max(1, Math.ceil(categoryItems.length / itemsPerPage));
  const pageItems = categoryItems.slice((page - 1) * itemsPerPage, page * itemsPerPage);

  const equipped = useMemo(() => equippedMap(visibleItems), [visibleItems]);
  const previewEquipped = useMemo<EquippedMap>(
    () => ({ ...equipped, ...previewOverrides }),
    [equipped, previewOverrides],
  );
  const itemById = useMemo(
    () => new Map(visibleItems.map((item) => [item.id, item])),
    [visibleItems],
  );
  const selected = selectedId ? itemById.get(selectedId) ?? null : null;
  const draftSlots = useMemo(
    () => (Object.keys(previewOverrides) as Slot[]).filter(
      (slot) => (previewOverrides[slot] ?? null) !== equipped[slot],
    ),
    [equipped, previewOverrides],
  );

  // 카테고리를 바꾸면 1페이지에서 시작하고, 이전 안내·오류 문구를 지운다.
  // 문구를 남겨 두면 "구매했어요"나 실패 메시지가 관계없는 화면까지 따라다닌다.
  useEffect(() => {
    setPage(1);
    setMessage(null);
  }, [category]);

  // 폭이 줄거나 상품이 빠져 총 페이지가 작아지면 현재 페이지를 보정한다.
  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  function resetPreview() {
    setPreviewOverrides({});
  }

  function applyPreviewEquipment(item: CharacterStoreItem) {
    setPreviewOverrides((current) => nextPreviewOverrides(current, item, equipped));
  }

  /**
   * 카드 클릭은 미리보기까지만 한다. 구매는 상세 영역의 버튼에서만 일어난다.
   * 카드 클릭과 결제가 이어지면 실수로 재화를 쓰게 된다(가이드라인 §5.6).
   */
  function handleCardClick(item: CharacterStoreItem) {
    setMessage(null);
    setSelectedId(item.id);
    if ((item.owned || item.equipped) && isRenderable(item)) applyPreviewEquipment(item);
  }

  async function buy(item: CharacterStoreItem) {
    if (!isRenderable(item)) {
      setMessage({ tone: 'error', text: '아직 도트 이미지가 준비되지 않은 상품이에요.' });
      return;
    }
    try {
      setPurchasingId(item.id);
      const purchased = await onBuy(item);
      if (!purchased) {
        setMessage({ tone: 'error', text: `${currencyName} 잔액이 부족해요.` });
        return;
      }
      acquireMockCharacter(item.id);
      await loadStore();
      // 구매는 보유 상태까지만 바꾼다. 착용은 사용자가 따로 고른다(가이드라인 §13.3).
      setMessage({ tone: 'info', text: `${item.name}을(를) 구매했어요. 눌러서 입혀 보세요.` });
    } catch (error) {
      setMessage({
        tone: 'error',
        text: userMessage(error, '구매하지 못했어요. 잠시 후 다시 시도해 주세요.'),
      });
    } finally {
      setPurchasingId(null);
    }
  }

  /** 드래프트를 서버에 한 번에 확정한다. 실패하면 드래프트를 그대로 남긴다. */
  async function savePreviewEquipment() {
    if (draftSlots.length === 0) return;
    try {
      setSaving(true);
      const profile = await saveCharacterEquipment(
        classroomId,
        profileVersion,
        previewEquipped,
        visibleItems,
      );
      setProfileVersion(profile.state_version);
      await loadStore();
      resetPreview();
      setMessage({ tone: 'info', text: '지금 모습으로 저장했어요.' });
    } catch (error) {
      if (error instanceof ApiError && error.code === 'STATE_VERSION_CONFLICT') {
        // 드래프트는 지우지 않는다. 최신 상태를 불러오고 사용자가 다시 확인하게 한다.
        await loadStore().catch(() => undefined);
        setMessage({
          tone: 'error',
          text: '캐릭터 정보가 다른 화면에서 변경되었습니다.'
            + ' 최신 상태를 불러왔어요. 변경할 상품을 다시 확인해 주세요.',
        });
        return;
      }
      // 드래프트는 남긴다. 사용자가 고른 조합을 실패 때문에 버리지 않는다.
      setMessage({
        tone: 'error',
        text: userMessage(error, '저장하지 못했어요. 잠시 후 다시 시도해 주세요.'),
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="character-store-page">
      <header className="character-store-header">
        <div>
          <p className="eyebrow">캐릭터 상점</p>
          <h2>마음에 드는 도트 아이템을 골라 보세요</h2>
        </div>
        <div className="character-wallet" aria-label={`보유 ${walletBalance}${currencyName}`}>
          <span aria-hidden="true">●</span>
          보유 <strong>{walletBalance}</strong>{currencyName}
        </div>
      </header>

      <section className="character-store">
        <aside className="character-preview-panel">
          <CharacterStage equipped={previewEquipped} />

          <EquippedStrip
            equipped={previewEquipped}
            expanded={propsExpanded}
            itemById={itemById}
            onSelect={(item) => setSelectedId(item.id)}
            onToggleProps={() => setPropsExpanded((value) => !value)}
            savedEquipped={equipped}
          />

          <SelectedProductSummary
            currencyName={currencyName}
            item={selected}
            onBuy={buy}
            onPreview={applyPreviewEquipment}
            previewEquipped={previewEquipped}
            purchasing={purchasingId === selected?.id}
            walletBalance={walletBalance}
          />

          <div className="character-panel-actions">
            <button
              className="original-look-button"
              disabled={draftSlots.length === 0 || saving}
              onClick={resetPreview}
              type="button"
            >
              ↻ 원래 모습으로
            </button>
            <button
              className="save-look-button"
              disabled={draftSlots.length === 0 || saving}
              onClick={() => void savePreviewEquipment()}
              type="button"
            >
              {saving ? '저장 중…' : '저장된 모습 유지'}
            </button>
          </div>
          {draftSlots.length > 0 ? (
            <p className="character-draft-hint">
              {draftSlots.length}개 슬롯을 아직 저장하지 않았어요.
            </p>
          ) : null}
        </aside>

        <div className="character-catalog-panel">
          <div className="character-theme-tabs" aria-label="캐릭터 상품 분류">
            {CATEGORIES.map(({ value, icon, label }) => (
              <button
                aria-pressed={category === value}
                className={category === value ? 'active' : ''}
                key={value}
                onClick={() => setCategory(value)}
                type="button"
              >
                <span aria-hidden="true">{icon}</span>
                {label}
              </button>
            ))}
          </div>

          {message ? (
            <p className={`shop-message ${message.tone}`} role="status">{message.text}</p>
          ) : null}
          {loading ? <p className="character-empty">상품을 불러오는 중이에요.</p> : null}
          {/*
            "불러오지 못했다"와 "이 분류에 상품이 없다"를 섞으면 안 된다. 서버가 죽었을 때
            "판매 중인 상품이 없어요"가 뜨면 사용자는 상점이 빈 것으로 오해한다.
          */}
          {!loading && loadFailed ? (
            <div className="character-load-failed">
              <p>상품 목록을 불러오지 못했어요.</p>
              <button onClick={() => setReloadToken((value) => value + 1)} type="button">
                다시 불러오기
              </button>
            </div>
          ) : null}
          {!loading && !loadFailed && categoryItems.length === 0 ? (
            <p className="character-empty">이 분류에 판매 중인 상품이 없어요.</p>
          ) : null}

          <div
            className="character-product-grid"
            ref={gridRef}
            style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
          >
            {pageItems.map((item) => (
              <ProductCard
                currencyName={currencyName}
                item={item}
                key={item.id}
                onClick={handleCardClick}
                previewing={previewEquipped[item.slot] === item.id}
                purchasing={purchasingId === item.id}
                selected={selectedId === item.id}
                walletBalance={walletBalance}
              />
            ))}
          </div>

          {categoryItems.length > 0 ? (
            <nav className="character-pagination" aria-label="상품 페이지">
              <button
                aria-label="이전 페이지"
                disabled={page <= 1}
                onClick={() => setPage((value) => Math.max(1, value - 1))}
                type="button"
              >
                ‹
              </button>
              <span>{page} / {totalPages}</span>
              <button
                aria-label="다음 페이지"
                disabled={page >= totalPages}
                onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
                type="button"
              >
                ›
              </button>
            </nav>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function ProductCard({
  currencyName,
  item,
  onClick,
  previewing,
  purchasing,
  selected,
  walletBalance,
}: {
  currencyName: string;
  item: CharacterStoreItem;
  onClick: (item: CharacterStoreItem) => void;
  previewing: boolean;
  purchasing: boolean;
  selected: boolean;
  walletBalance: number;
}) {
  const total = calculateStoreTotal(item.price, 1, item.vatRateBps);
  const state = cardStateFor(item, walletBalance, total);
  const shortage = total - walletBalance;

  return (
    <article
      className={[
        'character-product-card',
        selected ? 'selected' : '',
        previewing ? 'previewing' : '',
        state === 'EQUIPPED' ? 'equipped' : '',
      ].filter(Boolean).join(' ')}
    >
      <button className="character-card-hit" onClick={() => onClick(item)} type="button">
        <ProductPreview item={item} scale={CHARACTER_SCALE.thumbnail} />
        <span className="character-product-info">
          <b>{item.name}</b>
          <strong>{total}{currencyName}</strong>
        </span>
      </button>
      <p className={`character-card-state ${state.toLowerCase()}`}>
        {state === 'EQUIPPED' ? '✓ 착용 중' : null}
        {state === 'OWNED' ? '✓ 보유 중' : null}
        {state === 'BUYABLE' ? (purchasing ? '구매 중…' : '구매하기') : null}
        {state === 'SHORT' ? `🔒 ${shortage}${currencyName} 부족` : null}
      </p>
    </article>
  );
}

function SelectedProductSummary({
  currencyName,
  item,
  onBuy,
  onPreview,
  previewEquipped,
  purchasing,
  walletBalance,
}: {
  currencyName: string;
  item: CharacterStoreItem | null;
  onBuy: (item: CharacterStoreItem) => Promise<void>;
  onPreview: (item: CharacterStoreItem) => void;
  previewEquipped: EquippedMap;
  purchasing: boolean;
  walletBalance: number;
}) {
  if (!item) {
    return (
      <p className="character-preview-help">
        상품을 누르면 설명과 가격을 볼 수 있어요. 보유한 상품은 눌러서 바로 입혀 볼 수 있어요.
      </p>
    );
  }

  const total = calculateStoreTotal(item.price, 1, item.vatRateBps);
  const state = cardStateFor(item, walletBalance, total);
  const wearing = previewEquipped[item.slot] === item.id;
  const renderable = isRenderable(item);

  return (
    <div className="selected-product-summary">
      <ProductPreview item={item} scale={CHARACTER_SCALE.detail} />
      <div className="selected-product-copy">
        <div className="selected-product-title">
          <b>{item.name}</b>
          <span className="slot-badge">{SLOT_LABEL[item.slot]}</span>
        </div>
        <p className="selected-product-description">
          {item.description ?? '상품 설명을 준비하고 있어요.'}
        </p>
        <div className="selected-product-price">
          <span>가격</span>
          <strong>{total}{currencyName}</strong>
        </div>
        <div className="selected-product-action">
          {state === 'BUYABLE' || state === 'SHORT' ? (
            <button
              className="character-item-action"
              disabled={state === 'SHORT' || purchasing || !renderable}
              onClick={() => void onBuy(item)}
              type="button"
            >
              {purchasing
                ? '구매 중…'
                : state === 'SHORT'
                  ? `${total - walletBalance}${currencyName} 부족`
                  : '구매하기'}
            </button>
          ) : (
            <button
              className="character-wear-action"
              disabled={!renderable}
              onClick={() => onPreview(item)}
              type="button"
            >
              {wearing ? '벗어 보기' : '입어 보기'}
            </button>
          )}
          <span className={`character-card-state ${state.toLowerCase()}`}>
            {state === 'EQUIPPED' ? '착용 중' : null}
            {state === 'OWNED' ? '보유 중' : null}
            {state === 'BUYABLE' ? '구매 가능' : null}
            {state === 'SHORT' ? '잔액 부족' : null}
          </span>
        </div>
      </div>
    </div>
  );
}

function EquippedStrip({
  equipped,
  expanded,
  itemById,
  onSelect,
  onToggleProps,
  savedEquipped,
}: {
  equipped: EquippedMap;
  expanded: boolean;
  itemById: Map<string, CharacterStoreItem>;
  onSelect: (item: CharacterStoreItem) => void;
  onToggleProps: () => void;
  savedEquipped: EquippedMap;
}) {
  const propItems = PROP_SLOTS
    .map((slot) => ({ slot, id: equipped[slot] }))
    .filter((entry): entry is { slot: Slot; id: string } => Boolean(entry.id));

  const isUnsaved = (slot: Slot) => (equipped[slot] ?? null) !== (savedEquipped[slot] ?? null);

  function cellFor(key: Category, label: string) {
    if (key === 'PROP') {
      // 대표 상품은 목록에 이름이 있는 것 중 첫 번째다. 판매 중지된 상품이 착용된 채로
      // 남아 있으면 itemById에 없어 이름을 못 찾는데, 그때 "undefined +1"이 나오면 안 된다.
      const named = propItems
        .map((entry) => itemById.get(entry.id) ?? null)
        .filter((item): item is CharacterStoreItem => item !== null);
      const item = named[0] ?? null;
      const unsaved = PROP_SLOTS.some(isUnsaved);
      return (
        <button
          aria-expanded={expanded}
          className={[
            'character-slot-cell',
            item ? 'filled' : '',
            unsaved ? 'unsaved' : '',
          ].filter(Boolean).join(' ')}
          key={key}
          onClick={onToggleProps}
          type="button"
        >
          <span className="character-slot-label">{label}</span>
          {item ? <ProductPreview compact item={item} scale={1} /> : <span className="empty">-</span>}
          <small>
            {item
              ? `${item.name}${propItems.length > 1 ? ` +${propItems.length - 1}` : ''}`
              : '없음'}
          </small>
        </button>
      );
    }

    const slot = key as Slot;
    const item = equipped[slot] ? itemById.get(equipped[slot] as string) ?? null : null;
    const unsaved = isUnsaved(slot);
    return (
      <button
        className={[
          'character-slot-cell',
          item ? 'filled' : '',
          unsaved ? 'unsaved' : '',
        ].filter(Boolean).join(' ')}
        disabled={!item}
        key={key}
        onClick={() => item && onSelect(item)}
        type="button"
      >
        <span className="character-slot-label">{label}</span>
        {item ? <ProductPreview compact item={item} scale={1} /> : <span className="empty">-</span>}
        <small>{item?.name ?? '없음'}</small>
      </button>
    );
  }

  return (
    <div className="character-equipped-strip">
      <p className="character-strip-title">현재 착용 아이템</p>
      <div className="character-slot-row">
        {DISPLAY_SLOTS.map(({ key, label }) => cellFor(key, label))}
      </div>
      {expanded ? (
        <ul className="character-prop-detail">
          {PROP_SLOTS.map((slot) => {
            const id = equipped[slot];
            const item = id ? itemById.get(id) ?? null : null;
            return (
              <li key={slot}>
                <span>{SLOT_LABEL[slot]}</span>
                <b>{item?.name ?? '-'}</b>
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}

export function CharacterWardrobe({ classroomId }: { classroomId: string }) {
  const [items, setItems] = useState<CharacterStoreItem[]>([]);
  const [message, setMessage] = useState('');

  async function refresh() {
    setItems(await fetchOwnedCharacters(classroomId));
  }

  useEffect(() => {
    let active = true;
    fetchOwnedCharacters(classroomId)
      .then((next) => {
        if (active) setItems(next);
      })
      .catch((error: unknown) => {
        if (active) {
          setMessage(error instanceof Error ? error.message : '보관함을 불러오지 못했어요.');
        }
      });
    return () => {
      active = false;
    };
  }, [classroomId]);

  const equipped = useMemo(() => equippedMap(items), [items]);

  /**
   * 보관함은 단일 아이템 즉시 착용이므로 단건 명령 API를 그대로 쓴다.
   * 여러 슬롯을 함께 바꾸는 상점 저장만 일괄 저장 API를 쓴다(가이드라인 §11.3).
   */
  async function toggle(item: CharacterStoreItem) {
    try {
      await setCharacterEquipped(classroomId, item, !item.equipped);
      await refresh();
      setMessage(item.equipped ? `${item.name}을(를) 벗었어요.` : `${item.name}을(를) 착용했어요.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '착용 상태를 바꾸지 못했어요.');
    }
  }

  return (
    <section className="character-store wardrobe">
      <aside className="character-preview-panel">
        <div className="character-preview-heading">
          <div>
            <p className="eyebrow">내 캐릭터</p>
            <h2>현재 착용 모습</h2>
          </div>
        </div>
        <CharacterStage equipped={equipped} />
      </aside>
      <div className="character-catalog-panel">
        <div className="character-preview-heading">
          <div>
            <p className="eyebrow">캐릭터 보관함</p>
            <h2>보유 아이템 {items.length}개</h2>
          </div>
        </div>
        {message ? <p className="shop-message" role="status">{message}</p> : null}
        <div className="wardrobe-list">
          {items.map((item) => (
            <button
              className={item.equipped ? 'wardrobe-item equipped' : 'wardrobe-item'}
              key={item.id}
              onClick={() => void toggle(item)}
              type="button"
            >
              <ProductPreview compact item={item} />
              <span>
                <b>{item.name}</b>
                <small>{SLOT_LABEL[item.slot]}</small>
              </span>
              <strong>{item.equipped ? '착용 중' : '착용'}</strong>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
