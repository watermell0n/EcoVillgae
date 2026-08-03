import { FormEvent, useEffect, useMemo, useState } from "react";
import { ApiError } from "./api/client";
import { createStudentSession, type StudentSession } from "./api/auth";
import { StudentRealtimeClient, type RealtimeSubscriptionRequest } from "./api/realtime";
import { clearStudentSession, getDeviceId, loadStudentSession, saveStudentSession } from "./api/sessionStorage";
import {
  castVote,
  createTrade,
  fetchDepositAndSavings,
  fetchGuidedActivity,
  fetchQuizQuestions,
  fetchQuizSession,
  fetchVote,
  purchaseStoreItem,
  subscribeDeposit,
  submitGameAnswer,
  type DepositAccountView,
  type GuidedActivity,
  type QuizQuestion,
  type QuizSession,
  type TradeCommandResponse,
  type VoteView
} from "./api/studentFeatures";
import { moduleCards, studentHomeEnvelope, transactions } from "./data/studentHome";
import {
  CharacterShop,
  CharacterWardrobe
} from "./features/character/components/CharacterStore";
import type { CharacterStoreItem } from "./features/character/characterApi";
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
  | "profile"
  | "class";

type WalletTransaction = {
  id: string;
  title: string;
  amount: number;
  after: number;
  memo: string;
};

type ActiveRealtimeActivity = {
  classroomId: string;
  activityId: string;
  activityType: string;
};

const ACTIVE_REALTIME_ACTIVITY_KEY = "ecovillage.student.realtime.active-activity";

function loadActiveRealtimeActivity(): ActiveRealtimeActivity | null {
  try {
    const value = sessionStorage.getItem(ACTIVE_REALTIME_ACTIVITY_KEY);
    if (!value) return null;
    const parsed = JSON.parse(value) as Partial<ActiveRealtimeActivity>;
    return typeof parsed.classroomId === "string"
      && typeof parsed.activityId === "string"
      && typeof parsed.activityType === "string"
      ? parsed as ActiveRealtimeActivity
      : null;
  } catch {
    return null;
  }
}

function storeActiveRealtimeActivity(activity: ActiveRealtimeActivity | null) {
  try {
    if (activity) {
      sessionStorage.setItem(ACTIVE_REALTIME_ACTIVITY_KEY, JSON.stringify(activity));
    } else {
      sessionStorage.removeItem(ACTIVE_REALTIME_ACTIVITY_KEY);
    }
  } catch {
    // The connection still recovers by sequence when session storage is unavailable.
  }
}

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

const pageTitles: Record<PageKey, string> = {
  village: "마을 광장",
  wallet: "내 지갑",
  paystub: "월급명세서",
  shop: "이코 상점",
  bank: "이코 은행",
  investment: "투자 광장",
  trade: "거래소",
  quiz: "퀴즈",
  character: "내 캐릭터",
  profile: "프로필",
  class: "선생님 수업"
};

const financeProducts = [
  { id: "deposit-1", title: "3일 예금", description: "오늘 넣으면 3일차에 이자를 받아요", amount: 100, rate: "10%" },
  { id: "saving-1", title: "매일 적금", description: "매일 조금씩 모으는 연습이에요", amount: 30, rate: "15%" }
];

const shortTermInvestments = [
  { name: "거북이상사", price: 50, expected: "안정형", resultDate: "내일 공개", description: "가격 변화가 작고 결과가 빨리 나와요." },
  { name: "호랑운송", price: 40, expected: "변동형", resultDate: "내일 공개", description: "좋은 소식과 나쁜 소식에 따라 크게 움직여요." },
  { name: "독수리테크", price: 30, expected: "도전형", resultDate: "내일 공개", description: "성공하면 많이 오르지만 손해도 날 수 있어요." }
];

const shortTermResults = [
  { name: "거북이상사", invested: 50, returned: 55, change: "+5", memo: "안정적인 성장으로 이익이 확정됐어요." },
  { name: "호랑운송", invested: 40, returned: 35, change: "-5", memo: "물류비가 올라 손실이 확정됐어요." }
];

const longTermInvestments = [
  {
    name: "초록에너지",
    holdingDays: 3,
    invested: 60,
    current: 72,
    aiNews: "AI 뉴스: 학교 마을에서 친환경 전기 사용이 늘어나며 초록에너지 주문이 증가했어요.",
    hint: "좋은 흐름이 이어지고 있지만 내일 재료비 발표를 확인해야 해요."
  },
  {
    name: "구름마켓",
    holdingDays: 2,
    invested: 45,
    current: 41,
    aiNews: "AI 뉴스: 새 상점이 생겨 경쟁이 심해졌고 오늘 매출이 조금 줄었어요.",
    hint: "더 기다릴지, 손실을 줄일지 선택해야 해요."
  }
];

function App() {
  const [session, setSession] = useState<StudentSession | null>(() => loadStudentSession());
  const [authState, setAuthState] = useState<AuthState>(() => loadStudentSession() ? "app" : "landing");
  const [page, setPage] = useState<PageKey>("village");
  const [nickname, setNickname] = useState("학생");
  const [walletBalance, setWalletBalance] = useState(studentHomeEnvelope.studentView.walletBalance);
  const [walletTransactions, setWalletTransactions] = useState<WalletTransaction[]>(
    () => transactions.map((transaction) => ({
      id: transaction.id,
      title: transaction.title,
      amount: transaction.amount,
      after: transaction.after,
      memo: transaction.amount > 0 ? "내 지갑에 들어왔어요" : "우리 반 공동기금으로 모였어요"
    }))
  );
  const [deposits, setDeposits] = useState<DepositAccountView[]>([]);
  const [quizQuestions, setQuizQuestions] = useState<QuizQuestion[]>([]);
  const [quizSession, setQuizSession] = useState<QuizSession | null>(null);
  const [vote, setVote] = useState<VoteView | null>(null);
  const [guidedActivity, setGuidedActivity] = useState<GuidedActivity | null>(null);
  const [activeRealtimeActivity, setActiveRealtimeActivity] =
    useState<ActiveRealtimeActivity | null>(() => loadActiveRealtimeActivity());
  const [realtimeRefresh, setRealtimeRefresh] = useState(0);
  const [tradeResult, setTradeResult] = useState<TradeCommandResponse | null>(null);
  const [actionMessage, setActionMessage] = useState("");
  const [profilePassword, setProfilePassword] = useState("economy123!");
  const activeClassroomId = session?.classroomId ?? studentHomeEnvelope.classroom.id;
  const activeRealtimeActivityForClass = activeRealtimeActivity?.classroomId === activeClassroomId
    ? activeRealtimeActivity
    : null;

  useEffect(() => {
    if (authState !== "app") {
      return;
    }

    let ignore = false;
    async function loadStudentFeatures() {
      try {
        const [accountData, questionData, sessionData, voteData, activityData] = await Promise.all([
          fetchDepositAndSavings(activeClassroomId),
          fetchQuizQuestions(activeClassroomId),
          fetchQuizSession(
            activeClassroomId,
            activeRealtimeActivityForClass?.activityType === "QUIZ"
              ? activeRealtimeActivityForClass.activityId
              : undefined
          ),
          fetchVote(
            activeClassroomId,
            activeRealtimeActivityForClass?.activityType === "VOTE"
              ? activeRealtimeActivityForClass.activityId
              : undefined
          ),
          fetchGuidedActivity(
            activeClassroomId,
            activeRealtimeActivityForClass?.activityId
          )
        ]);

        if (ignore) {
          return;
        }

        setDeposits(accountData.depositAccounts);
        setQuizQuestions(questionData.questions);
        setQuizSession(sessionData);
        setVote(voteData);
        setGuidedActivity(activityData);
      } catch (error) {
        if (!ignore) {
          setActionMessage(error instanceof Error ? error.message : "학생 데이터를 불러오지 못했어요.");
        }
      }
    }

    loadStudentFeatures();
    return () => {
      ignore = true;
    };
  }, [
    activeClassroomId,
    activeRealtimeActivityForClass?.activityId,
    activeRealtimeActivityForClass?.activityType,
    authState,
    realtimeRefresh
  ]);

  useEffect(() => {
    if (
      authState !== "app"
      || !session
      || session.accessToken.startsWith("mock-")
    ) {
      return;
    }

    const subscriptions: RealtimeSubscriptionRequest[] = [
      { channelId: "WS-CLS-001", resourceId: activeClassroomId },
      { channelId: "WS-STU-001", resourceId: session.studentId },
      { channelId: "WS-ECO-001", resourceId: activeClassroomId }
    ];
    const activityResourceId = activeRealtimeActivityForClass?.activityId ?? guidedActivity?.activityId;
    const quizResourceId = activeRealtimeActivityForClass?.activityType === "QUIZ"
      ? activeRealtimeActivityForClass.activityId
      : quizSession?.quizSessionId;
    const voteResourceId = activeRealtimeActivityForClass?.activityType === "VOTE"
      ? activeRealtimeActivityForClass.activityId
      : vote?.voteId;
    if (quizResourceId) {
      subscriptions.push({ channelId: "WS-QZ-001", resourceId: quizResourceId });
    }
    if (voteResourceId) {
      subscriptions.push({ channelId: "WS-VOT-001", resourceId: voteResourceId });
    }
    if (activityResourceId) {
      subscriptions.push({ channelId: "WS-ACT-001", resourceId: activityResourceId });
    }

    let refreshTimer: ReturnType<typeof setTimeout> | null = null;
    const refresh = () => {
      if (refreshTimer) clearTimeout(refreshTimer);
      refreshTimer = setTimeout(() => setRealtimeRefresh((value) => value + 1), 200);
    };
    const client = new StudentRealtimeClient({
      subscriptions,
      onEvent: (event) => {
        if (
          event.event_type === "classroom.app_mode.changed"
          && event.payload.mode === "GUIDED"
          && typeof event.payload.active_activity_id === "string"
        ) {
          const activeActivity: ActiveRealtimeActivity = {
            classroomId: event.classroom_id,
            activityId: event.payload.active_activity_id,
            activityType: typeof event.payload.activity_type === "string"
              ? event.payload.activity_type
              : "ACTIVITY"
          };
          setActiveRealtimeActivity(activeActivity);
          storeActiveRealtimeActivity(activeActivity);
        }
        if (
          event.event_type === "activity.started"
          || (
            event.event_type === "classroom.app_mode.changed"
            && event.payload.mode === "GUIDED"
          )
        ) {
          setPage("class");
        }
        if (
          event.event_type === "classroom.app_mode.changed"
          && event.payload.mode !== "GUIDED"
        ) {
          setActiveRealtimeActivity(null);
          storeActiveRealtimeActivity(null);
          setPage((current) => current === "class" ? "village" : current);
        }
        refresh();
      },
      onRecoveryRequired: refresh,
      onError: (error) => setActionMessage(error.message)
    });
    client.start();
    return () => {
      if (refreshTimer) clearTimeout(refreshTimer);
      client.stop();
    };
  }, [
    activeClassroomId,
    authState,
    session?.accessToken,
    session?.studentId,
    activeRealtimeActivityForClass?.activityId,
    activeRealtimeActivityForClass?.activityType,
    quizSession?.quizSessionId,
    vote?.voteId,
    guidedActivity?.activityId
  ]);

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
          setProfilePassword("economy123!");
          setAuthState("app");
        }}
      />
    );
  }

  const envelope = {
    ...studentHomeEnvelope,
    studentView: { ...studentHomeEnvelope.studentView, nickname, walletBalance }
  };
  const savedBalance = walletBalance
    + deposits.reduce((total, account) => total + account.principalAmount, 0);
  const storeSpendTotal = walletTransactions.reduce(
    (total, transaction) => (
      transaction.amount < 0 && transaction.memo === "상점에서 구매했어요"
        ? total + Math.abs(transaction.amount)
        : total
    ),
    0
  );
  const title = pageTitles[page];

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
            storeActiveRealtimeActivity(null);
            setActiveRealtimeActivity(null);
            setSession(null);
            setPage("village");
            setAuthState("landing");
          }}
          onProfile={() => {
            setPage("profile");
          }}
        />
        <section className="content-area">
          {page === "village" && <VillagePage onMove={setPage} />}
          {page === "wallet" && <WalletPage walletBalance={walletBalance} transactions={walletTransactions} />}
          {page === "paystub" && <PaystubPage />}
          {page === "shop" && (
            <ShopPage
              classroomId={activeClassroomId}
              walletBalance={walletBalance}
              savedBalance={savedBalance}
              spendTotal={storeSpendTotal}
              onBuy={async (item) => {
                if (walletBalance < item.price) {
                  return false;
                }
                await purchaseStoreItem(
                  activeClassroomId,
                  item.productId,
                  1,
                  item.productVersion
                );
                const nextBalance = walletBalance - item.price;
                setWalletBalance(nextBalance);
                setWalletTransactions((items) => [
                  {
                    id: `purchase-${Date.now()}`,
                    title: item.name,
                    amount: -item.price,
                    after: nextBalance,
                    memo: "상점에서 구매했어요"
                  },
                  ...items
                ]);
                return true;
              }}
            />
          )}
          {page === "bank" && (
            <BankPage
              deposits={deposits}
              onSubscribe={async (amount) => {
                const result = await subscribeDeposit(activeClassroomId, undefined, amount);
                const latest = await fetchDepositAndSavings(activeClassroomId);
                setDeposits(latest.depositAccounts);
                setActionMessage(`예금 가입 완료: ${result.status}`);
              }}
            />
          )}
          {page === "investment" && <InvestmentPage />}
          {page === "trade" && (
            <TradeApiPage
              tradeResult={tradeResult}
              onCreateTrade={async (description, amount) => {
                const result = await createTrade(activeClassroomId, description, amount);
                setTradeResult(result);
                setActionMessage(`거래 제안 완료: ${result.status}`);
              }}
            />
          )}
          {page === "quiz" && (
            <QuizApiPage
              questions={quizQuestions}
              session={quizSession}
              onAnswer={async () => {
                await submitGameAnswer(activeClassroomId);
                setActionMessage("퀴즈 답안을 제출했어요.");
              }}
            />
          )}
          {page === "character" && (
            <CharacterPage
              classroomId={activeClassroomId}
              savedBalance={savedBalance}
              spendTotal={storeSpendTotal}
            />
          )}
          {page === "profile" && (
            <ProfileSettingsPage
              nickname={nickname}
              currentPassword={profilePassword}
              onSave={(nextNickname, nextPassword) => {
                setNickname(nextNickname);
                setProfilePassword(nextPassword);
                setActionMessage("프로필을 바꿨어요.");
              }}
            />
          )}
          {page === "class" && (
            <ClassModeApiPage
              vote={vote}
              guidedActivity={guidedActivity}
              onVote={async (optionId) => {
                if (!vote) {
                  return;
                }
                const receipt = await castVote(activeClassroomId, vote.voteId, optionId);
                const latestVote = await fetchVote(activeClassroomId, vote.voteId);
                setVote({ ...latestVote, hasVoted: true });
                setActionMessage(`투표 완료: ${receipt.receiptHash.slice(0, 8)}`);
              }}
            />
          )}
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
    classroomCode: "ABCD2345",
    attendanceNumber: "7",
    nickname: "경제왕",
    password: "economy123!"
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

function WalletPage({ walletBalance, transactions }: { walletBalance: number; transactions: WalletTransaction[] }) {
  const todayIncome = transactions.filter((tx) => tx.amount > 0).reduce((sum, tx) => sum + tx.amount, 0);
  const todayExpense = transactions.filter((tx) => tx.amount < 0).reduce((sum, tx) => sum + Math.abs(tx.amount), 0);

  return (
    <section className="two-column">
      <div className="wallet-hero">
        <p>지금 내 지갑</p>
        <strong>{walletBalance}비빔</strong>
        <span>오늘 들어온 돈 +{todayIncome}비빔</span>
        <span>오늘 쓴 돈 -{todayExpense}비빔</span>
      </div>
      <div className="panel">
        <p className="eyebrow">내역</p>
        <h2>오늘 지갑 내역</h2>
        <div className="transaction-list">
          {transactions.map((tx) => (
            <article className="transaction-row" key={tx.id}>
              <div>
                <b>{tx.title}</b>
                <span>{tx.memo}</span>
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

function ShopPage({
  classroomId,
  walletBalance,
  savedBalance,
  spendTotal,
  onBuy
}: {
  classroomId: string;
  walletBalance: number;
  savedBalance: number;
  spendTotal: number;
  onBuy: (item: CharacterStoreItem) => Promise<boolean>;
}) {
  return (
    <section className="shop-page">
      <CharacterShop
        classroomId={classroomId}
        walletBalance={walletBalance}
        savedBalance={savedBalance}
        spendTotal={spendTotal}
        onBuy={onBuy}
      />
    </section>
  );
}

function BankPage({ deposits, onSubscribe }: { deposits: DepositAccountView[]; onSubscribe: (amount: number) => void }) {
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
            <button onClick={() => onSubscribe(product.amount)}>가입하기</button>
          </article>
        ))}
      </div>
      <div className="mini-section">
        <h3>내 예금</h3>
        {deposits.length === 0 ? (
          <p>아직 가입한 예금이 없어요.</p>
        ) : (
          deposits.map((deposit) => (
            <div className="data-row" key={deposit.resourceId}>
              <b>{deposit.label}</b>
              <span>{deposit.principalAmount}비빔 · {deposit.status}</span>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

function InvestmentPage() {
  const [tab, setTab] = useState<"short" | "long">("short");
  const [investmentMessage, setInvestmentMessage] = useState("");

  return (
    <section className="panel section-tabs-panel">
      <div className="category-tabs">
        <button className={tab === "short" ? "active" : ""} onClick={() => setTab("short")}>단기투자</button>
        <button className={tab === "long" ? "active" : ""} onClick={() => setTab("long")}>장기투자</button>
      </div>
      {investmentMessage && <p className="shop-message">{investmentMessage}</p>}
      {tab === "short" ? (
        <div className="investment-flow">
          <div className="section-title compact-title">
            <h2>단기투자</h2>
            <p>오늘 고르면 내일 결과가 확정돼요. 확정된 결과는 바꿀 수 없어요.</p>
          </div>
          <div className="result-list">
            {shortTermInvestments.map((asset) => (
              <article className="investment-card" key={asset.name}>
                <div>
                  <b>{asset.name}</b>
                  <span>{asset.description}</span>
                </div>
                <dl>
                  <div><dt>투자금</dt><dd>{asset.price}비빔</dd></div>
                  <div><dt>성격</dt><dd>{asset.expected}</dd></div>
                  <div><dt>결과</dt><dd>{asset.resultDate}</dd></div>
                </dl>
                <button onClick={() => setInvestmentMessage(`${asset.name}에 투자했어요. 결과는 내일 확정돼요.`)}>투자하기</button>
              </article>
            ))}
          </div>
          <div className="mini-section">
            <h3>확정된 결과</h3>
            {shortTermResults.map((asset) => (
              <div className="data-row" key={asset.name}>
                <b>{asset.name}</b>
                <span>{asset.invested}비빔 → {asset.returned}비빔 · {asset.change}비빔</span>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="investment-flow">
          <div className="section-title compact-title">
            <h2>장기투자</h2>
            <p>매일 AI가 새로운 상황을 알려줘요. 내용을 보고 계속 들고 갈지, 지금 뺄지 정해요.</p>
          </div>
          <div className="result-list">
            {longTermInvestments.map((asset) => (
            <article className="long-investment-card" key={asset.name}>
              <div>
                <b>{asset.name}</b>
                <span>{asset.holdingDays}일째 보유 중 · {asset.invested}비빔 → {asset.current}비빔</span>
              </div>
              <p>{asset.aiNews}</p>
              <small>{asset.hint}</small>
              <div className="investment-actions">
                <button onClick={() => setInvestmentMessage(`${asset.name}을 하루 더 지켜보기로 했어요.`)}>홀딩하기</button>
                <button onClick={() => setInvestmentMessage(`${asset.name}을 지금 정리했어요.`)}>빼기</button>
              </div>
            </article>
            ))}
          </div>
        </div>
      )}
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

function CharacterPage({
  classroomId,
  savedBalance,
  spendTotal
}: {
  classroomId: string;
  savedBalance: number;
  spendTotal: number;
}) {
  return (
    <CharacterWardrobe
      classroomId={classroomId}
      savedBalance={savedBalance}
      spendTotal={spendTotal}
    />
  );
}

function ProfileSettingsPage({
  nickname,
  currentPassword,
  onSave
}: {
  nickname: string;
  currentPassword: string;
  onSave: (nickname: string, password: string) => void;
}) {
  const [step, setStep] = useState<"check" | "edit">("check");
  const [passwordCheck, setPasswordCheck] = useState("");
  const [nextNickname, setNextNickname] = useState(nickname);
  const [nextPassword, setNextPassword] = useState("");
  const [nextPasswordConfirm, setNextPasswordConfirm] = useState("");
  const [message, setMessage] = useState("");

  function verifyPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (passwordCheck !== currentPassword) {
      setMessage("비밀번호가 맞지 않아요.");
      return;
    }

    setMessage("");
    setStep("edit");
  }

  function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedNickname = nextNickname.trim();
    if (!trimmedNickname) {
      setMessage("닉네임을 입력해 주세요.");
      return;
    }

    const passwordToSave = nextPassword || currentPassword;
    if (nextPassword && nextPassword.length < 8) {
      setMessage("새 비밀번호는 8자 이상으로 입력해 주세요.");
      return;
    }

    if (nextPassword && nextPassword !== nextPasswordConfirm) {
      setMessage("새 비밀번호가 서로 달라요.");
      return;
    }

    onSave(trimmedNickname, passwordToSave);
    setPasswordCheck("");
    setNextPassword("");
    setNextPasswordConfirm("");
    setMessage("저장했어요.");
  }

  return (
    <section className="profile-settings">
      {step === "check" ? (
        <form className="profile-form" onSubmit={verifyPassword}>
          <p className="eyebrow">프로필 확인</p>
          <h2>비밀번호를 입력해 주세요</h2>
          <p>닉네임과 비밀번호를 바꾸기 전에 한 번 확인할게요.</p>
          <label className="stack-label">
            현재 비밀번호
            <input
              type="password"
              value={passwordCheck}
              onChange={(event) => setPasswordCheck(event.target.value)}
              placeholder="비밀번호 입력"
            />
          </label>
          {message && <p className="form-error">{message}</p>}
          <button className="primary-action wide" type="submit">확인하기</button>
        </form>
      ) : (
        <form className="profile-form" onSubmit={saveProfile}>
          <p className="eyebrow">프로필 수정</p>
          <h2>내 정보를 바꿔요</h2>
          <label className="stack-label">
            닉네임
            <input value={nextNickname} onChange={(event) => setNextNickname(event.target.value)} />
          </label>
          <label className="stack-label">
            새 비밀번호
            <input
              type="password"
              value={nextPassword}
              onChange={(event) => setNextPassword(event.target.value)}
              placeholder="바꾸지 않으려면 비워두기"
            />
          </label>
          <label className="stack-label">
            새 비밀번호 확인
            <input
              type="password"
              value={nextPasswordConfirm}
              onChange={(event) => setNextPasswordConfirm(event.target.value)}
              placeholder="새 비밀번호 다시 입력"
            />
          </label>
          {message && <p className={message === "저장했어요." ? "form-success" : "form-error"}>{message}</p>}
          <button className="primary-action wide" type="submit">저장하기</button>
        </form>
      )}
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

function TradeApiPage({
  tradeResult,
  onCreateTrade
}: {
  tradeResult: TradeCommandResponse | null;
  onCreateTrade: (description: string, amount: number) => void;
}) {
  const [description, setDescription] = useState("친구에게 30비빔 보내기");
  const [amount, setAmount] = useState(30);

  return (
    <section className="two-column">
      <div className="panel">
        <p className="eyebrow">친구 거래소</p>
        <h2>거래 제안 만들기</h2>
        <label className="stack-label">상대 학생<input placeholder="예: 하늘" /></label>
        <label className="stack-label">보낼 비빔<input value={amount} onChange={(event) => setAmount(Number(event.target.value))} /></label>
        <label className="stack-label">거래 메모<input value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <button className="primary-action wide" onClick={() => onCreateTrade(description, amount)}>제안 보내기</button>
      </div>
      <div className="panel">
        <p className="eyebrow">대기 중 거래</p>
        <h2>선생님 승인 전</h2>
        <div className="transaction-row">
          <div>
            <b>{tradeResult ? `거래 ${tradeResult.tradeId.slice(0, 8)}` : "아직 보낸 거래가 없어요"}</b>
            <span>{tradeResult ? `상태 ${tradeResult.status}` : "제안을 보내면 여기에서 상태를 확인해요."}</span>
          </div>
          <div className="money">{tradeResult ? `v${tradeResult.stateVersion}` : "대기"}</div>
        </div>
      </div>
    </section>
  );
}

function QuizApiPage({
  questions,
  session,
  onAnswer
}: {
  questions: QuizQuestion[];
  session: QuizSession | null;
  onAnswer: () => void;
}) {
  const question = session?.questions[0];
  const bankQuestion = questions[0];
  const hasQuiz = Boolean(question || bankQuestion);

  return (
    <section className="panel quiz-panel">
      <strong className={hasQuiz ? "quiz-state ready" : "quiz-state"}>{hasQuiz ? "준비된 퀴즈가 있어요" : "지금은 준비된 퀴즈가 없어요"}</strong>
      {hasQuiz ? (
        <div className="quiz-box">
          <b>{question?.prompt ?? bankQuestion?.prompt}</b>
          {(question?.options ?? ["내 지갑", "우리 반 공동기금", "상점 주인"]).map((choice) => (
            <button key={choice} onClick={onAnswer}>{choice}</button>
          ))}
        </div>
      ) : (
        <p className="empty-note">선생님이 퀴즈를 열면 바로 풀 수 있어요.</p>
      )}
    </section>
  );
}

function ClassModeApiPage({
  vote,
  guidedActivity,
  onVote
}: {
  vote: VoteView | null;
  guidedActivity: GuidedActivity | null;
  onVote: (optionId: string) => void;
}) {
  void vote;
  void guidedActivity;
  void onVote;
  const classMaterials = [
    { title: "월급과 실수령액", type: "읽기 자료", state: "열기" },
    { title: "세금은 어디로 갈까?", type: "활동지", state: "열기" },
    { title: "예금과 투자 비교", type: "선생님 자료", state: "열기" }
  ];

  return (
    <section className="panel class-materials">
      <div className="section-title compact-title">
        <h2>수업 자료</h2>
        <p>선생님이 올린 자료를 보고 오늘 활동을 따라가요.</p>
      </div>
      <div className="result-list">
        {classMaterials.map((material) => (
          <button className="data-row option-button" key={material.title}>
            <b>{material.title}</b>
            <span>{material.type} · {material.state}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

export default App;
