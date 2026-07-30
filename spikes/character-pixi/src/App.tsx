import { useEffect, useMemo, useRef, useState } from 'react';
import { CharacterStage } from './components/CharacterStage';
import { ProductPreview } from './components/ProductPreview';
import { RigQaMatrix } from './components/RigQaMatrix';
import { assetProfileFor, validateRig } from './assetProfile';
import { computeBodyState, DAYS, INCOME_BASE } from './bodyState';
import * as api from './mockApi';
import { ApiError } from './mockApi';
import {
  BODY_LABEL,
  EMPTY_EQUIPPED,
  SLOT_LABEL,
  THEME_LABEL,
  type BodyState,
  type CosmeticDTO,
  type EquippedMap,
  type Slot,
  type Theme,
  type WalletDTO,
} from './types';

const SLOT_ORDER: Slot[] = [
  'HAT', 'FACE', 'NECK', 'TOP', 'BOTTOM', 'SHOES', 'BACK', 'HAND', 'EFFECT', 'BACKGROUND',
];
const THEME_ORDER: Theme[] = ['BASIC', 'SEASONAL', 'SPORTS', 'CAREER', 'FANTASY', 'EVENT'];
const BODY_STEPS: BodyState[] = ['CHUBBY', 'BASE', 'SLIM', 'VERY_SLIM'];

const THEME_ICON: Record<Theme, string> = {
  BASIC: '●',
  SEASONAL: '❄',
  SPORTS: '◆',
  CAREER: '■',
  FANTASY: '✦',
  EVENT: '★',
};

export default function App() {
  const [catalog, setCatalog] = useState<CosmeticDTO[]>([]);
  const [wallet, setWallet] = useState<WalletDTO>({ balance: 0, spendTotal: 0 });
  const [owned, setOwned] = useState<Set<string>>(new Set());
  const [equipped, setEquipped] = useState<EquippedMap>(EMPTY_EQUIPPED);
  const [autoBody, setAutoBody] = useState<BodyState>('BASE');
  const [bodyOverride, setBodyOverride] = useState<BodyState | null>('BASE');
  const [easterEggOn, setEasterEggOn] = useState(true);
  const [toast, setToast] = useState<string | null>(null);
  const [theme, setTheme] = useState<Theme | 'ALL'>('ALL');
  const [qaOpen, setQaOpen] = useState(false);
  const toastTimer = useRef<number | null>(null);

  useEffect(() => {
    void (async () => {
      const [cosmetics, character, nextWallet, ownership] = await Promise.all([
        api.listCosmetics(),
        api.getCharacter(),
        api.getWallet(),
        api.listOwnership(),
      ]);
      setCatalog(cosmetics);
      setOwned(new Set(ownership));
      setWallet(nextWallet);
      setEquipped({ ...EMPTY_EQUIPPED, ...character.equipped });
      setAutoBody(computeBodyState('BASE', nextWallet.balance, nextWallet.spendTotal));
    })();

    return () => {
      if (toastTimer.current !== null) window.clearTimeout(toastTimer.current);
    };
  }, []);

  useEffect(() => {
    setAutoBody((previous) => computeBodyState(previous, wallet.balance, wallet.spendTotal));
  }, [wallet]);

  const effectiveBody: BodyState = !easterEggOn ? 'BASE' : bodyOverride ?? autoBody;

  function flash(message: string) {
    setToast(message);
    if (toastTimer.current !== null) window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 1800);
  }

  async function handleBuy(item: CosmeticDTO) {
    try {
      const response = await api.purchase(item.id, `${item.id}-${Date.now()}`);
      setWallet(response.wallet);
      setOwned((current) => new Set(current).add(item.id));
      flash(`‘${item.name}’ 구매 완료`);
    } catch (error) {
      if (error instanceof ApiError) {
        flash(error.code === 'INSUFFICIENT_FUNDS' ? '비빔이 부족해요' : error.message);
      }
    }
  }

  async function handleEquipToggle(item: CosmeticDTO) {
    const isEquipped = equipped[item.slot] === item.id;
    const character = await api.equip(item.slot, isEquipped ? null : item.id);
    setEquipped({ ...EMPTY_EQUIPPED, ...character.equipped });
  }

  const grouped = useMemo(() => {
    const groups: Record<Slot, CosmeticDTO[]> = {
      HAT: [], FACE: [], NECK: [], TOP: [], BOTTOM: [], SHOES: [],
      BACK: [], HAND: [], EFFECT: [], BACKGROUND: [],
    };
    for (const cosmetic of catalog) {
      if (theme === 'ALL' || cosmetic.theme === theme) groups[cosmetic.slot].push(cosmetic);
    }
    return groups;
  }, [catalog, theme]);

  const equippedCount = Object.values(equipped).filter(Boolean).length;
  const rigErrors = useMemo(() => validateRig(), []);
  const assetModeCount = useMemo(() => {
    const count = { common: 0, handmade: 0, nineSlice: 0 };
    for (const item of catalog) {
      const mode = assetProfileFor(item).fitMode;
      if (mode === 'COMMON') count.common += 1;
      if (mode === 'TIER_HANDDRAWN') count.handmade += 1;
      if (mode === 'NINE_SLICE') count.nineSlice += 1;
    }
    return count;
  }, [catalog]);
  const denominator = INCOME_BASE * DAYS;
  const saveRatio = (wallet.balance / denominator).toFixed(2);
  const spendRatio = (wallet.spendTotal / denominator).toFixed(2);

  return (
    <div className="app">
      <header className="shop-header">
        <button className="back-button" type="button" aria-label="뒤로 가기">‹</button>
        <div className="brand-mark" aria-hidden="true">🏪</div>
        <div>
          <h1>캐릭터 상점</h1>
        </div>
        <div className="balance-chip">
          <span className="coin-icon">✦</span>
          <span>보유</span>
          <strong>{wallet.balance}</strong>
          <span>비빔</span>
        </div>
      </header>

      <main className="layout">
        <aside className="preview-panel">
          <div className="preview-title">
            <span>착용 미리보기</span>
            <div className="preview-badges">
              <span className="rig-badge">앵커 고정 · ×2</span>
              <span className="equipped-count">{equippedCount}개 착용</span>
            </div>
          </div>
          <CharacterStage equipped={equipped} bodyState={effectiveBody} />

          <div className="wallet">
            <div className="coin">
              <span>지갑 잔액</span>
              <strong>{wallet.balance} 비빔</strong>
            </div>
            <div className="coin coin--right">
              <span>누적 소비</span>
              <strong>{wallet.spendTotal} 비빔</strong>
            </div>
          </div>

          <div className="bodybox">
            <div className="bodyhead">
              <div>
                <span className="section-label">체형</span>
                <strong>{BODY_LABEL[effectiveBody]}</strong>
              </div>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={easterEggOn}
                  onChange={(event) => setEasterEggOn(event.target.checked)}
                />
                <span>체형 변화</span>
              </label>
            </div>

            {easterEggOn ? (
              <>
                <div className="body-steps">
                  {BODY_STEPS.map((body) => (
                    <button
                      key={body}
                      type="button"
                      className={bodyOverride === body ? 'on' : ''}
                      onClick={() => setBodyOverride(body)}
                    >
                      {BODY_LABEL[body]}
                    </button>
                  ))}
                  <button
                    type="button"
                    className={bodyOverride === null ? 'on' : ''}
                    onClick={() => setBodyOverride(null)}
                  >
                    자동
                  </button>
                </div>
                <p className="body-hint">저축비 {saveRatio} · 소비비 {spendRatio}</p>
              </>
            ) : (
              <p className="body-hint">기본 체형으로 고정됩니다.</p>
            )}
          </div>
        </aside>

        <section className="shop">
          <div className="theme-tabs" aria-label="테마 필터">
            <button type="button" className={theme === 'ALL' ? 'on' : ''} onClick={() => setTheme('ALL')}>
              <span>✦</span> 추천
            </button>
            {THEME_ORDER.map((item) => (
              <button
                type="button"
                key={item}
                className={theme === item ? 'on' : ''}
                onClick={() => setTheme(item)}
              >
                <span>{THEME_ICON[item]}</span> {THEME_LABEL[item]}
              </button>
            ))}
          </div>

          <div className="catalog">
            {SLOT_ORDER.map((slot) => grouped[slot].length > 0 && (
              <section className="slotgroup" key={slot}>
                <div className="slot-heading">
                  <h2>{SLOT_LABEL[slot]}</h2>
                  <span>{grouped[slot].length}개</span>
                </div>
                <div className="product-grid">
                  {grouped[slot].map((item) => {
                    const isOwned = owned.has(item.id);
                    const isEquipped = equipped[item.slot] === item.id;
                    return (
                      <article className={`product-card ${isEquipped ? 'equipped' : ''}`} key={item.id}>
                        <ProductPreview item={item} />
                        <div className="product-info">
                          <div>
                            <h3>{item.name}</h3>
                            <span className="theme-name">{THEME_LABEL[item.theme]}</span>
                          </div>
                          <strong className="product-price">{item.price}<span>비빔</span></strong>
                        </div>
                        {isOwned ? (
                          <button
                            type="button"
                            className={`product-action equip ${isEquipped ? 'on' : ''}`}
                            onClick={() => handleEquipToggle(item)}
                          >
                            {isEquipped ? '✓ 착용 중' : '착용하기'}
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="product-action buy"
                            disabled={wallet.balance < item.price}
                            onClick={() => handleBuy(item)}
                          >
                            {wallet.balance < item.price ? '비빔 부족' : '구매하기'}
                          </button>
                        )}
                      </article>
                    );
                  })}
                </div>
              </section>
            ))}
          </div>
        </section>
      </main>

      <section className="rig-qa-panel">
        <div className="rig-qa-heading">
          <div>
            <h2>전체 아이템 × 전체 체형 QA</h2>
            <p>
              공통 {assetModeCount.common} · 티어별 수작업 {assetModeCount.handmade}
              {' '}· 9슬라이스 {assetModeCount.nineSlice}
            </p>
          </div>
          <div className="rig-qa-actions">
            <span className={rigErrors.length === 0 ? 'rig-pass' : 'rig-fail'}>
              {rigErrors.length === 0 ? '리그 규칙 통과' : `${rigErrors.length}개 오류`}
            </span>
            <button type="button" onClick={() => setQaOpen((open) => !open)}>
              {qaOpen ? '매트릭스 닫기' : `매트릭스 열기 (${catalog.length * BODY_STEPS.length}컷)`}
            </button>
          </div>
        </div>
        {qaOpen ? <RigQaMatrix catalog={catalog} /> : null}
      </section>

      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}
