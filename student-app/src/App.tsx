import { useMemo, useState } from "react";
import { studentHomeEnvelope, transactions } from "./data/studentHome";
import type { ModuleCard } from "./types/screen";

type PageKey = "village" | "wallet" | "paystub" | "shop" | "character" | "class";

const menuItems: Array<{ key: PageKey; label: string; icon: string; disabled?: boolean }> = [
  { key: "village", label: "마을 광장", icon: "🏘️" },
  { key: "wallet", label: "내 지갑", icon: "💰" },
  { key: "paystub", label: "월급명세서", icon: "💼" },
  { key: "shop", label: "이코 상점", icon: "🏪" },
  { key: "character", label: "내 캐릭터", icon: "🙂" },
  { key: "class", label: "선생님 수업", icon: "📚", disabled: true }
];

function App() {
  const [page, setPage] = useState<PageKey>("village");
  const envelope = studentHomeEnvelope;
  const title = useMemo(() => menuItems.find((item) => item.key === page)?.label ?? "마을 광장", [page]);

  return (
    <div className="student-shell">
      <Sidebar current={page} onMove={setPage} />
      <main className="student-main">
        <Header title={title} />
        <section className="content-area">
          {page === "village" && <VillagePage onMove={setPage} />}
          {page === "wallet" && <WalletPage />}
          {page === "paystub" && <PaystubPage />}
          {page === "shop" && <ShopPage />}
          {page === "character" && <CharacterPage />}
        </section>
      </main>
      <aside className="right-summary" aria-label="학생 상태 요약">
        <p className="eyebrow">내 상태</p>
        <strong>{envelope.studentView.walletBalance}{envelope.classroom.currencyName}</strong>
        <span>{envelope.classroom.dayIndex}일차 · {envelope.mode}</span>
        <div className="summary-line" />
        <p>열린 기능만 사용할 수 있어요. 돈이 움직이는 행동은 서버 확인 후 반영돼요.</p>
      </aside>
    </div>
  );
}

function Sidebar({ current, onMove }: { current: PageKey; onMove: (page: PageKey) => void }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span>🏘️</span>
        <div>
          <strong>이코빌리지</strong>
          <small>학생 웹</small>
        </div>
      </div>
      <nav>
        {menuItems.map((item) => (
          <button
            key={item.key}
            className={current === item.key ? "side-item active" : "side-item"}
            disabled={item.disabled}
            onClick={() => onMove(item.key)}
          >
            <span>{item.icon}</span>
            {item.label}
          </button>
        ))}
      </nav>
      <div className="teacher-lock">
        <b>수업 중 잠금</b>
        <p>선생님이 활동을 시작하면 마을 기능은 자동으로 잠겨요.</p>
      </div>
    </aside>
  );
}

function Header({ title }: { title: string }) {
  const envelope = studentHomeEnvelope;

  return (
    <header className="app-header">
      <div>
        <p className="eyebrow">우리 반 경제생활</p>
        <h1>{title}</h1>
      </div>
      <div className="header-pills">
        <span>{envelope.classroom.dayIndex}일차</span>
        <span>{envelope.studentView.walletBalance}{envelope.classroom.currencyName}</span>
        <span>{envelope.studentView.nickname}</span>
      </div>
    </header>
  );
}

function VillagePage({ onMove }: { onMove: (page: PageKey) => void }) {
  const cards = studentHomeEnvelope.data.cards;

  return (
    <>
      <section className="hero">
        <div>
          <p className="eyebrow">자율시간</p>
          <h2>오늘 사용할 마을 기능을 골라요</h2>
          <p>선생님이 허용한 기능만 보이고, 닫힌 기능은 이유를 함께 보여줘요.</p>
        </div>
        <div className="mode-card">
          <b>AUTONOMOUS</b>
          <span>상점·지갑·캐릭터 이용 가능</span>
        </div>
      </section>
      <section className="village-grid">
        {cards.map((card) => (
          <VillageCard key={card.id} card={card} onMove={onMove} />
        ))}
      </section>
    </>
  );
}

function VillageCard({ card, onMove }: { card: ModuleCard; onMove: (page: PageKey) => void }) {
  const disabled = card.state === "locked" || card.state === "construction";
  const pageMap: Record<string, PageKey> = {
    paystub: "paystub",
    character: "character",
    shop: "shop",
    bank: "wallet",
    investment: "wallet",
    meeting: "village"
  };

  return (
    <button className={`village-card tone-${card.tone}`} disabled={disabled} onClick={() => onMove(pageMap[card.id] ?? "village")}>
      <span className="status-badge">{card.state === "new" ? "NEW" : card.state === "locked" ? "잠김" : card.state === "resultOnly" ? "조회" : "열림"}</span>
      <span className="card-icon">{card.icon}</span>
      <strong>{card.title}</strong>
      <p>{card.subtitle}</p>
      <em>{card.cta}</em>
    </button>
  );
}

function WalletPage() {
  return (
    <section className="two-column">
      <div className="wallet-hero">
        <p>지금 내 지갑</p>
        <strong>158비빔</strong>
        <span>오늘 +158비빔</span>
      </div>
      <div className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">거래 내역</p>
            <h2>오늘 돈의 흐름</h2>
          </div>
        </div>
        <div className="transaction-list">
          {transactions.map((tx) => (
            <article className="transaction-row" key={tx.id}>
              <div>
                <b>{tx.title}</b>
                <span>{tx.id} · 처리 완료</span>
              </div>
              <div className={tx.amount < 0 ? "money negative" : "money positive"}>
                {tx.amount > 0 ? "+" : ""}{tx.amount}비빔
                <small>잔액 {tx.after}비빔</small>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function PaystubPage() {
  return (
    <section className="paystub-layout">
      <div className="receipt">
        <div className="receipt-top">
          <h2>급여 명세서</h2>
          <p>이코빌리지 · 1일차</p>
        </div>
        <ReceiptRow label="1인 1역 월급" amount="+150비빔" />
        <ReceiptRow label="퀴즈 정답수당" amount="+25비빔" />
        <ReceiptRow label="소득세" amount="-17비빔" danger />
        <div className="final-pay">
          <span>내 지갑에 들어온 돈</span>
          <strong>158비빔</strong>
          <small>175비빔 - 17비빔 = 158비빔</small>
        </div>
      </div>
      <div className="tax-explain">
        <p className="eyebrow">세금 안내</p>
        <h2>세금은 어디로 가나요?</h2>
        <p>소득세 17비빔은 우리 반 공동기금으로 모여요. 나중에 함께 투표해서 사용할 곳을 정해요.</p>
        <div className="money-flow">
          <b>총 받은 돈<br />175비빔</b>
          <span>-17비빔</span>
          <b>내 지갑<br />158비빔</b>
        </div>
        <button className="primary-action">확인했어요</button>
      </div>
    </section>
  );
}

function ReceiptRow({ label, amount, danger }: { label: string; amount: string; danger?: boolean }) {
  return (
    <div className={danger ? "receipt-row danger" : "receipt-row"}>
      <span>{label}</span>
      <b>{amount}</b>
    </div>
  );
}

function ShopPage() {
  return (
    <section className="panel">
      <p className="eyebrow">이코 상점</p>
      <h2>구매 가능한 상품</h2>
      <div className="product-grid">
        {["연필 교환권", "간식 우선권", "파란 후드", "빨간 모자"].map((name, index) => (
          <article className="product-card" key={name}>
            <span>{index < 2 ? "🎁" : "👕"}</span>
            <b>{name}</b>
            <p>{30 + index * 10}비빔</p>
            <button>{index === 3 ? "잔액 부족" : "상세 보기"}</button>
          </article>
        ))}
      </div>
    </section>
  );
}

function CharacterPage() {
  return (
    <section className="character-layout">
      <div className="character-slot">
        <span>DOT<br />CHARACTER</span>
      </div>
      <div className="panel">
        <p className="eyebrow">캐릭터 슬롯</p>
        <h2>아이템 상태</h2>
        {["빨간 모자", "파란 후드", "줄무늬 바지", "노란 백팩"].map((item, index) => (
          <div className={index === 1 ? "item-row equipped" : "item-row"} key={item}>
            <b>{item}</b>
            <span>{index === 1 ? "착용 중" : "보유 중"}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

export default App;
