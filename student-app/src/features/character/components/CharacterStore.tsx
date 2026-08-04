import { useEffect, useMemo, useState } from 'react';
import { calculateStoreTotal } from '../../../api/studentFeatures';
import { conflictsFor } from '../assetProfile';
import { basicLayerProfileFor } from '../basicLayerProfile';
import {
  acquireMockCharacter,
  equippedMap,
  fetchCharacterStore,
  fetchOwnedCharacters,
  setCharacterEquipped,
  type CharacterStoreItem,
} from '../characterApi';
import { SLOT_LABEL, THEME_LABEL, type EquippedMap, type Slot } from '../types';
import { CharacterStage } from './CharacterStage';
import { ProductPreview } from './ProductPreview';

type Category =
  | 'ALL'
  | 'HAT'
  | 'FACE'
  | 'TOP'
  | 'BOTTOM'
  | 'SHOES'
  | 'PROP'
  | 'BACKGROUND';

const CATEGORIES: Array<{ value: Category; icon: string; label: string }> = [
  { value: 'ALL', icon: '★', label: '추천' },
  { value: 'HAT', icon: '⌒', label: '모자' },
  { value: 'FACE', icon: '☺', label: '얼굴' },
  { value: 'TOP', icon: '♜', label: '상의' },
  { value: 'BOTTOM', icon: '▥', label: '하의' },
  { value: 'SHOES', icon: '◡', label: '신발' },
  { value: 'PROP', icon: '✦', label: '소품' },
];

type CharacterShopProps = {
  classroomId: string;
  currencyName: string;
  walletBalance: number;
  onBuy: (item: CharacterStoreItem) => Promise<boolean>;
};

type PreviewOverrides = Partial<Record<Slot, string | null>>;

function previewEquipment(
  items: CharacterStoreItem[],
  overrides: PreviewOverrides,
): EquippedMap {
  return { ...equippedMap(items), ...overrides };
}

function nextPreviewOverrides(
  current: PreviewOverrides,
  item: CharacterStoreItem,
  equipped: EquippedMap,
): PreviewOverrides {
  const next = { ...current };
  if (next[item.slot] === item.id) {
    delete next[item.slot];
    for (const conflictSlot of conflictsFor(item, { ...equipped, ...current })) {
      delete next[conflictSlot];
    }
    return next;
  }

  const composed = { ...equipped, ...current };
  for (const conflictSlot of conflictsFor(item, composed)) {
    next[conflictSlot] = null;
  }
  next[item.slot] = item.id;
  return next;
}

function matchesCategory(item: CharacterStoreItem, category: Category): boolean {
  if (category === 'ALL') return true;
  if (category === 'PROP') {
    return ['BACK', 'NECK', 'HAND', 'EFFECT'].includes(item.slot);
  }
  return item.slot === category;
}

function hasMigratedLayer(item: CharacterStoreItem): boolean {
  return basicLayerProfileFor(item.spriteKey) !== null;
}

export function CharacterShop({
  classroomId,
  currencyName,
  walletBalance,
  onBuy,
}: CharacterShopProps) {
  const [items, setItems] = useState<CharacterStoreItem[]>([]);
  const [category, setCategory] = useState<Category>('ALL');
  const [previewOverrides, setPreviewOverrides] = useState<PreviewOverrides>({});
  const [lastSelectedId, setLastSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [purchasingId, setPurchasingId] = useState<string | null>(null);

  async function refresh(): Promise<CharacterStoreItem[]> {
    const next = await fetchCharacterStore(classroomId);
    setItems(next);
    return next;
  }

  useEffect(() => {
    let active = true;
    setLoading(true);
    fetchCharacterStore(classroomId)
      .then((next) => {
        if (!active) return;
        setItems(next);
      })
      .catch((error: unknown) => {
        if (active) {
          setMessage(error instanceof Error ? error.message : '캐릭터 상품을 불러오지 못했어요.');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [classroomId]);

  const migratedItems = useMemo(() => items.filter(hasMigratedLayer), [items]);
  const visibleItems = useMemo(
    () => migratedItems.filter((item) => matchesCategory(item, category)),
    [category, migratedItems],
  );
  const equipped = useMemo(() => equippedMap(migratedItems), [migratedItems]);
  const selected = migratedItems.find((item) => item.id === lastSelectedId) ?? null;
  const previewEquipped = useMemo(
    () => previewEquipment(migratedItems, previewOverrides),
    [migratedItems, previewOverrides],
  );
  const previewCount = Object.values(previewOverrides).filter(Boolean).length;

  function resetPreview() {
    setPreviewOverrides({});
    setLastSelectedId(null);
  }

  function togglePreview(item: CharacterStoreItem) {
    const removing = previewOverrides[item.slot] === item.id;
    setPreviewOverrides((current) => nextPreviewOverrides(current, item, equipped));
    setLastSelectedId(removing ? null : item.id);
  }

  async function toggle(item: CharacterStoreItem) {
    try {
      await setCharacterEquipped(classroomId, item, !item.equipped);
      await refresh();
      resetPreview();
      setMessage(item.equipped ? `${item.name}을(를) 벗었어요.` : `${item.name}을(를) 착용했어요.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '착용 상태를 바꾸지 못했어요.');
    }
  }

  async function buy(item: CharacterStoreItem) {
    if (item.owned) {
      await toggle(item);
      return;
    }
    try {
      setPurchasingId(item.id);
      const purchased = await onBuy(item);
      if (!purchased) {
        setMessage(`${currencyName} 잔액이 부족해요.`);
        return;
      }

      acquireMockCharacter(item.id);
      let next = await refresh();
      const ownedItem = next.find((candidate) => candidate.id === item.id);
      if (ownedItem?.owned && !ownedItem.equipped) {
        await setCharacterEquipped(classroomId, ownedItem, true);
        next = await refresh();
      }
      setItems(next);
      resetPreview();
      setMessage(`${item.name}을(를) 구매하고 바로 착용했어요.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '구매하지 못했어요.');
    } finally {
      setPurchasingId(null);
    }
  }

  return (
    <section className="character-store">
      <aside className="character-preview-panel">
        <div className="character-preview-heading">
          <div>
            <p className="eyebrow">착용 미리보기</p>
            <h2>{selected?.name ?? '나의 원래 모습'}</h2>
          </div>
          <span className="body-pill">BASE 체형</span>
        </div>

        <CharacterStage equipped={previewEquipped} />

        <div className="character-preview-summary">
          <span>
            {previewCount > 0
              ? `${previewCount}개 슬롯을 함께 미리 착용 중`
              : '현재 저장된 착용 조합'}
          </span>
          <button
            className="original-look-button"
            disabled={previewCount === 0}
            onClick={resetPreview}
            type="button"
          >
            ↻ 원래 모습
          </button>
        </div>

        {selected ? (
          <div className="selected-product-summary">
            <ProductPreview compact item={selected} />
            <div>
              <b>{selected.name}</b>
              <span>{SLOT_LABEL[selected.slot]}</span>
              <strong>
                {calculateStoreTotal(selected.price, 1, selected.vatRateBps)}
                {currencyName}
              </strong>
            </div>
          </div>
        ) : null}
      </aside>

      <div className="character-catalog-panel">
        <header className="character-catalog-header">
          <div>
            <p className="eyebrow">캐릭터 상점</p>
            <h2>마음에 드는 도트 아이템을 골라 보세요</h2>
          </div>
          <div className="character-wallet" aria-label={`보유 ${walletBalance}${currencyName}`}>
            <span>●</span>
            보유 <strong>{walletBalance}</strong>{currencyName}
          </div>
        </header>

        <div className="character-theme-tabs" aria-label="캐릭터 상품 분류">
          {CATEGORIES.map(({ value, icon, label }) => (
            <button
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

        {message ? <p className="shop-message" role="status">{message}</p> : null}
        {loading ? <p className="character-empty">상품을 불러오는 중이에요.</p> : null}
        {!loading && visibleItems.length === 0 ? (
          <p className="character-empty">이 분류에 판매 중인 상품이 없어요.</p>
        ) : null}

        <div className="character-product-grid">
          {visibleItems.map((item) => {
            const totalPrice = calculateStoreTotal(item.price, 1, item.vatRateBps);
            const insufficient = walletBalance < totalPrice;
            const purchasing = purchasingId === item.id;
            const previewing = previewOverrides[item.slot] === item.id;
            return (
              <article
                className={[
                  'character-product-card',
                  previewing ? 'selected' : '',
                  item.equipped ? 'equipped' : '',
                ].filter(Boolean).join(' ')}
                key={item.id}
                onClick={() => togglePreview(item)}
              >
                <ProductPreview item={item} />
                <div className="character-product-info">
                  <span className="product-theme">{THEME_LABEL[item.theme]}</span>
                  <b>{item.name}</b>
                  <strong>{totalPrice}{currencyName}</strong>
                </div>
                <div className="character-card-actions">
                  <button
                    className="preview-toggle-button"
                    onClick={(event) => {
                      event.stopPropagation();
                      togglePreview(item);
                    }}
                    type="button"
                  >
                    {previewing ? '미리 벗기' : '미리 착용'}
                  </button>
                  <button
                    className="character-item-action"
                    disabled={purchasingId !== null || (!item.owned && insufficient)}
                    onClick={(event) => {
                      event.stopPropagation();
                      void buy(item);
                    }}
                    type="button"
                  >
                    {item.equipped
                      ? '착용 해제'
                      : item.owned
                        ? '착용하기'
                        : purchasing
                          ? '구매 중…'
                          : insufficient
                            ? `${currencyName} 부족`
                            : '구매하기'}
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      </div>
    </section>
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
