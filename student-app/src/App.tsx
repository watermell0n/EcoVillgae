import { FormEvent, useMemo, useState } from "react";
import { ApiError } from "./api/client";
import { createStudentSession, type StudentSession } from "./api/auth";
import { clearStudentSession, getDeviceId, loadStudentSession, saveStudentSession } from "./api/sessionStorage";
import { moduleCards, studentHomeEnvelope, transactions } from "./data/studentHome";
import type { ModuleCard } from "./types/screen";

type AuthState = "landing" | "login" | "app";
type PageKey =
  | "village"
  | "wallet"
  | "paystub"
  | "shop"
  | "bank"
  | "investment"
  | "trade"
  | "quiz"
  | "character"
  | "class";

const menuItems: Array<{ key: PageKey; label: string; icon: string; disabled?: boolean }> = [
  { key: "village", label: "마을 광장", icon: "🏘️" },
  { key: "shop", label: "이코 상점", icon: "🏪" },
  { key: "bank", label: "이코 은행", icon: "🏦" },
  { key: "investment", label: "투자 광장", icon: "📈" },
  { key: "trade", label: "거래소", icon: "🤝" },
  { key: "quiz", label: "퀴즈", icon: "📝" },
  { key: "class", label: "선생님 수업", icon: "📚" }
];

const profileItems: Array<{ key: PageKey; label: string; icon: string }> = [
  { key: "character", label: "내 캐릭터", icon: "🙂" },
  { key: "wallet", label: "내 지갑", icon: "💰" },
  { key: "paystub", label: "월급명세서", icon: "💼" }
];

const products = [
  { id: "pencil", name: "연필 교환권", price: 30, stock: 12, type: "실물 상품", owned: false },
  { id: "snack", name: "간식 우선권", price: 50, stock: 4, type: "권리 상품", owned: false },
  { id: "hoodie", name: "파란 후드", price: 50, stock: 99, type: "캐릭터", owned: true },
  { id: "cap", name: "빨간 모자", price: 30, stock: 99, type: "캐릭터", owned: true }
];

const financeProducts = [
  { id: "deposit-1", title: "3일 예금", description: "오늘 넣으면 3일차에 이자를 받아요", amount: 100, rate: "10%" },
  { id: "saving-1", title: "매일 적금", description: "매일 조금씩 모으는 연습이에요", amount: 30, rate: "15%" }
];

const investmentAssets = [
  { name: "거북이상사", price: 55, change: "+5", memo: "안정적인 성장" },
  { name: "호랑운송", price: 35, change: "-5", memo: "물류비 상승" },
  { name: "독수리테크", price: 48, change: "+18", memo: "신제품 성공" }
];

function App() {
  const [session, setSession] = useState<StudentSession | null>(() => loadStudentSession());
  const [authState, setAuthState] = useState<AuthState>(() => loadStudentSession() ? "app" : "landing");
  const [page, setPage] = useState<PageKey>("village");
  const [nickname, setNickname] = useState("학생");
  const [walletBalance, setWalletBalance] = useState(studentHomeEnvelope.studentView.walletBalance);

  if (authState === "landing") {
    return <LandingScreen onStart={() => setAuthState("login")} />;
  }

  if (authState === "login") {
    return (
      <LoginScreen
        onBack={() => setAuthState("landing")}
        onLogin={(nextSession, nextNickname) => {
          setSession(nextSession);
          saveStudentSession(nextSession);
          setNickname(nextNickname || "학생");
          setAuthState("app");
        }}
      />
    );
  }

  const envelope = {
    ...studentHomeEnvelope,
    studentView: { ...studentHomeEnvelope.studentView, nickname, walletBalance }
  };
  const title = menuItems.find((item) => item.key === page)?.label ?? "마을 광장";

  return (
    <div className="student-shell">
      <Sidebar
        current={page}
        onMove={setPage}
      />
      <main className="student-main">
        <Header
          title={title}
          envelope={envelope}
          onMove={setPage}
          onLogout={() => {
            clearStudentSession();
            setSession(null);
            setPage("village");
            setAuthState("landing");
          }}
          onProfile={() => {
            setPage("character");
          }}
        />
        <section className="content-area">
          {page === "village" && <VillagePage onMove={setPage} />}
          {page === "wallet" && <WalletPage walletBalance={walletBalance} />}
          {page === "paystub" && <PaystubPage />}
          {page === "shop" && (
            <ShopPage
              walletBalance={walletBalance}
              onBuy={(price, name) => {
                if (walletBalance < price) {
                  return;
                }
                setWalletBalance((value) => value - price);
              }}
            />
          )}
          {page === "bank" && <BankPage />}
          {page === "investment" && <InvestmentPage />}
          {page === "trade" && <TradePage />}
          {page === "quiz" && <QuizPage />}
          {page === "character" && <CharacterPage />}
          {page === "class" && <ClassModePage />}
        </section>
      </main>
    </div>
  );
}

function LandingScreen({ onStart }: { onStart: () => void }) {
  return (
    <main className="auth-background">
      <div className="float coin-a">💰</div>
      <div className="float coin-b">🪙</div>
      <section className="welcome-panel">
        <span className="welcome-icon">🏘️</span>
        <p className="eyebrow">경제교육 웹앱</p>
        <h1>이코빌리지</h1>
        <p>우리 반 경제생활이 시작되는 곳</p>
        <button className="primary-action wide" onClick={onStart}>로그인하기</button>
      </section>
    </main>
  );
}

function LoginScreen({ onBack, onLogin }: { onBack: () => void; onLogin: (session: StudentSession, nickname: string) => void }) {
  const [form, setForm] = useState({
    classroomCode: "ECO-001",
    attendanceNumber: "3",
    nickname: "학생",
    password: ""
  });
  const [loginError, setLoginError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);
  const canSubmit = form.classroomCode && form.attendanceNumber && form.nickname && form.password.length >= 8 && !loginLoading;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit) {
      setLoginError("입력한 정보를 다시 확인해 주세요.");
      return;
    }

    setLoginError("");
    setLoginLoading(true);
    try {
      const nextSession = await createStudentSession({
        classroomCode: form.classroomCode.trim(),
        attendanceNumber: Number(form.attendanceNumber),
        nickname: form.nickname.trim(),
        password: form.password,
        deviceId: getDeviceId()
      });
      onLogin(nextSession, form.nickname.trim());
    } catch (error) {
      if (error instanceof ApiError) {
        setLoginError(error.message);
      } else {
        setLoginError("서버에 연결하지 못했어요. 백엔드가 켜져 있는지 확인해 주세요.");
      }
    } finally {
      setLoginLoading(false);
    }
  }

  return (
    <main className="auth-background">
      <form className="login-panel" onSubmit={submit}>
        <button className="ghost-back" type="button" onClick={onBack}>←</button>
        <span className="welcome-icon">🏘️</span>
        <h1>이코빌리지</h1>
        <p>선생님이 알려준 정보로 접속해요</p>
        <label>
          학급 코드
          <input value={form.classroomCode} disabled={loginLoading} onChange={(event) => setForm({ ...form, classroomCode: event.target.value })} />
        </label>
        <label>
          출석번호
          <input value={form.attendanceNumber} disabled={loginLoading} inputMode="numeric" onChange={(event) => setForm({ ...form, attendanceNumber: event.target.value })} />
        </label>
        <label>
          닉네임
          <input value={form.nickname} disabled={loginLoading} onChange={(event) => setForm({ ...form, nickname: event.target.value })} />
        </label>
        <label>
          비밀번호
          <input type="password" value={form.password} disabled={loginLoading} placeholder="8자 이상 입력" onChange={(event) => setForm({ ...form, password: event.target.value })} />
        </label>
        {loginError && <p className="form-error">{loginError}</p>}
        <button className="primary-action wide" disabled={!canSubmit}>{loginLoading ? "확인 중" : "마을 입장하기"}</button>
        <p className="login-note">비밀번호를 잊었다면 선생님께 알려주세요.</p>
      </form>
    </main>
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
          <button key={item.key} className={current === item.key ? "side-item active" : "side-item"} onClick={() => onMove(item.key)}>
            <span>{item.icon}</span>
            {item.label}
          </button>
        ))}
      </nav>
    </aside>
  );
}

function Header({
  title,
  envelope,
  onMove,
  onProfile,
  onLogout
}: {
  title: string;
  envelope: typeof studentHomeEnvelope;
  onMove: (page: PageKey) => void;
  onProfile: () => void;
  onLogout: () => void;
}) {
  const [profileOpen, setProfileOpen] = useState(false);

  function moveFromProfile(nextPage: PageKey) {
    onMove(nextPage);
    setProfileOpen(false);
  }

  return (
    <header className="app-header">
      <div>
        <h1>{title}</h1>
      </div>
      <div className="header-actions">
        <button className="notification-button" aria-label="알림">🔔</button>
        <button className="profile-button" onClick={() => setProfileOpen((open) => !open)} aria-expanded={profileOpen} aria-label="프로필 메뉴">
          <span className="avatar-head" />
          <span className="avatar-body" />
        </button>
        {profileOpen && (
          <section className="profile-popover">
            <strong>{envelope.studentView.walletBalance}{envelope.classroom.currencyName}</strong>
            <div className="profile-actions">
              {profileItems.map((item) => (
                <button key={item.key} onClick={() => moveFromProfile(item.key)}>
                  <span>{item.icon}</span>
                  <b>{item.label}</b>
                </button>
              ))}
            </div>
            <div className="profile-footer">
              <button onClick={() => {
                onProfile();
                setProfileOpen(false);
              }}>프로필</button>
              <button onClick={onLogout}>로그아웃</button>
            </div>
          </section>
        )}
      </div>
    </header>
  );
}

function VillagePage({ onMove }: { onMove: (page: PageKey) => void }) {
  const cards = useMemo(() => [
    ...moduleCards.filter((card) => card.id !== "paystub" && card.id !== "character"),
    { id: "trade", title: "친구 거래소", subtitle: "친구와 아이템·비빔 거래를 제안해요", icon: "🤝", tone: "sky", state: "active", cta: "거래하기" } as ModuleCard,
    { id: "quiz", title: "오늘의 퀴즈", subtitle: "선생님이 연 퀴즈에 참여해요", icon: "📝", tone: "green", state: "active", cta: "풀러가기" } as ModuleCard
  ], []);

  return (
    <>
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
    bank: "bank",
    investment: "investment",
    meeting: "class",
    trade: "trade",
    quiz: "quiz"
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

function WalletPage({ walletBalance }: { walletBalance: number }) {
  const todayIncome = transactions.filter((tx) => tx.amount > 0).reduce((sum, tx) => sum + tx.amount, 0);
  const todayExpense = transactions.filter((tx) => tx.amount < 0).reduce((sum, tx) => sum + Math.abs(tx.amount), 0);

  return (
    <section className="two-column">
      <div className="wallet-hero">
        <p>지금 내 지갑</p>
        <strong>{walletBalance}비빔</strong>
        <span>오늘 +{todayIncome - todayExpense}비빔</span>
      </div>
      <div className="panel">
        <p className="eyebrow">거래 내역</p>
        <h2>오늘 돈의 흐름</h2>
        <div className="transaction-list">
          {transactions.map((tx) => (
            <article className="transaction-row" key={tx.id}>
              <div>
                <b>{tx.title}</b>
                <span>{tx.amount > 0 ? "내 지갑에 들어왔어요" : "우리 반 공동기금으로 모였어요"}</span>
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
          <p>이코빌리지</p>
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

function ShopPage({ walletBalance, onBuy }: { walletBalance: number; onBuy: (price: number, name: string) => void }) {
  return (
    <section className="panel">
      <p className="eyebrow">상점</p>
      <h2>상품을 고르고 구매해요</h2>
      <div className="product-grid">
        {products.map((product) => {
          const disabled = walletBalance < product.price;
          return (
            <article className="product-card" key={product.id}>
              <span>{product.type === "캐릭터" ? "👕" : "🎁"}</span>
              <b>{product.name}</b>
              <p>{product.price}비빔 · 재고 {product.stock}</p>
              <small>{product.owned ? "보유 중" : product.type}</small>
              <button disabled={disabled} onClick={() => onBuy(product.price, product.name)}>{disabled ? "잔액 부족" : "구매하기"}</button>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function BankPage() {
  return (
    <section className="panel">
      <p className="eyebrow">이코 은행</p>
      <h2>예금과 적금 상품</h2>
      <div className="product-grid compact">
        {financeProducts.map((product) => (
          <article className="product-card" key={product.id}>
            <span>🏦</span>
            <b>{product.title}</b>
            <p>{product.amount}비빔부터 · 이자 {product.rate}</p>
            <small>{product.description}</small>
            <button>가입하기</button>
          </article>
        ))}
      </div>
    </section>
  );
}

function InvestmentPage() {
  return (
    <section className="panel">
      <p className="eyebrow">투자 광장</p>
      <h2>오늘의 투자 결과</h2>
      <div className="result-list">
        {investmentAssets.map((asset) => (
          <article className="result-row" key={asset.name}>
            <div>
              <b>{asset.name}</b>
              <span>{asset.memo}</span>
            </div>
            <strong className={asset.change.startsWith("+") ? "positive" : "negative"}>{asset.change}비빔</strong>
            <small>현재가 {asset.price}비빔</small>
          </article>
        ))}
      </div>
    </section>
  );
}

function TradePage() {
  return (
    <section className="two-column">
      <div className="panel">
        <p className="eyebrow">친구 거래소</p>
        <h2>거래 제안 만들기</h2>
        <label className="stack-label">상대 학생<input placeholder="예: 하늘" /></label>
        <label className="stack-label">보낼 비빔<input placeholder="0" /></label>
        <label className="stack-label">거래 메모<input placeholder="무엇을 거래하나요?" /></label>
        <button className="primary-action wide">제안 보내기</button>
      </div>
      <div className="panel">
        <p className="eyebrow">대기 중 거래</p>
        <h2>선생님 승인 전</h2>
        <div className="transaction-row">
          <div>
            <b>민준에게 보낸 제안</b>
            <span>파란 후드 ↔ 30비빔</span>
          </div>
          <div className="money">대기</div>
        </div>
      </div>
    </section>
  );
}

function QuizPage() {
  return (
    <section className="panel">
      <p className="eyebrow">오늘의 퀴즈</p>
      <h2>경제생활 퀴즈</h2>
      <div className="quiz-box">
        <b>소득세는 어디에 모이나요?</b>
        {["내 캐릭터 보관함", "우리 반 공동기금", "선생님 개인 지갑"].map((choice) => (
          <button key={choice}>{choice}</button>
        ))}
      </div>
    </section>
  );
}

function CharacterPage() {
  return (
    <section className="character-layout">
      <div className="character-slot">
        <span>캐릭터<br />이미지 자리</span>
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

function ClassModePage() {
  return (
    <section className="panel centered-panel">
      <span className="big-icon">📚</span>
      <p className="eyebrow">선생님 수업</p>
      <h2>수업이 시작되면 이 화면으로 전환돼요</h2>
      <p>지도 활동, 퀴즈, 공개 화면은 선생님이 시작한 세션 상태에 따라 열려요.</p>
    </section>
  );
}

export default App;
