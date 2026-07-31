"use client";

import { ChangeEvent, useMemo, useState } from "react";

type ViewKey = "overview" | "setup" | "roster" | "shop" | "control" | "approvals";

const views: Array<{ key: ViewKey; label: string }> = [
  { key: "overview", label: "운영 현황" },
  { key: "setup", label: "학급 설정" },
  { key: "roster", label: "학생·급여" },
  { key: "shop", label: "상점 상품" },
  { key: "control", label: "수업 제어" },
  { key: "approvals", label: "승인·정산" }
];

const summaryRows = [
  ["학급 코드", "ABCD2345", "학생 로그인에 사용하는 8자리 코드"],
  ["운영 상태", "자율 활동", "상점·은행·퀴즈·거래 일부 열림"],
  ["학생", "28명", "명단 28명, 급여 역할 4개"],
  ["오늘 처리", "7건", "거래 승인 4건, 상점 확인 2건, 문의 1건"]
];

const modules = [
  ["상점", "열림", "학생 구매 가능", "재고 부족 상품 2개"],
  ["은행", "열림", "예금 가입 가능", "만기 예정 6건"],
  ["투자", "결과 공개", "2일차 결과 조회", "새 시나리오 준비 필요"],
  ["거래", "승인 필요", "친구 동의 후 교사 승인", "대기 4건"],
  ["퀴즈", "수업 모드", "선생님 시작 시 참여", "문항 8개 공개"]
];

const policyRows = [
  ["화폐명", "비빔", "학생 화면의 금액 단위"],
  ["월급일", "매일 1회", "교사 실행 후 명세서 공개"],
  ["소득세", "10%", "공동기금으로 자동 이동"],
  ["상점 승인", "권리 상품만 승인", "일반 상품은 즉시 구매"],
  ["거래 승인", "필수", "양측 동의 후 교사 확인"]
];

const students = [
  ["1", "김하늘", "시장 분석가", "150비빔", "정상"],
  ["2", "박서윤", "은행 매니저", "150비빔", "정상"],
  ["3", "이도윤", "상점 도우미", "130비빔", "급여 확인"],
  ["4", "최민지", "기록 담당", "120비빔", "비밀번호 재설정"]
];

const approvals = [
  ["거래", "김하늘 → 박서윤", "30비빔", "교사 승인 대기"],
  ["상점", "숙제 패스권", "2,000비빔", "권리 상품 확인"],
  ["대출", "이도윤", "500비빔", "신용 점수 확인"],
  ["투표", "공동기금 사용처", "340비빔", "결과 정산 대기"]
];

const apiRows = [
  ["학급 생성", "POST /classrooms", "학급 기본 정보 저장"],
  ["설정 조회", "GET /classrooms/{id}/configuration", "교실 설정 복원"],
  ["명단·급여", "PUT /classrooms/{id}/roster-and-salaries", "학생과 월급 정책 저장"],
  ["운영 모드", "POST /classrooms/{id}/access-mode-commands", "자율/수업 상태 전환"],
  ["급여 지급", "POST /classrooms/{id}/payouts", "월급 명세서 생성"]
];

export default function TeacherConsole() {
  const [view, setView] = useState<ViewKey>("overview");
  const title = useMemo(() => views.find((item) => item.key === view)?.label ?? "운영 현황", [view]);

  return (
    <main className="teacher-shell">
      <aside className="side">
        <div className="brand">
          <span>🏘️</span>
          <div>
            <strong>이코빌리지</strong>
            <small>교사 콘솔</small>
          </div>
        </div>
        <nav>
          {views.map((item) => (
            <button key={item.key} className={view === item.key ? "active" : ""} onClick={() => setView(item.key)}>
              {item.label}
            </button>
          ))}
        </nav>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p>4학년 1반 · 1주 과정</p>
            <h1>{title}</h1>
          </div>
          <div className="teacher-meta">
            <span>담임 교사</span>
            <b>신현경</b>
          </div>
        </header>

        {view === "overview" && <Overview />}
        {view === "setup" && <Setup />}
        {view === "roster" && <Roster />}
        {view === "shop" && <ShopAdmin />}
        {view === "control" && <Control />}
        {view === "approvals" && <Approvals />}
      </section>
    </main>
  );
}

function ShopAdmin() {
  const [preview, setPreview] = useState("");
  const [fileName, setFileName] = useState("선택된 사진 없음");

  function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      setPreview("");
      setFileName("선택된 사진 없음");
      return;
    }

    setFileName(file.name);
    const reader = new FileReader();
    reader.onload = () => setPreview(String(reader.result));
    reader.readAsDataURL(file);
  }

  return (
    <div className="page-flow">
      <PlainSection title="선생님 상품 등록" description="실물 상품이나 권리 상품을 올리고 학생 상점에 보여줄 사진을 붙입니다.">
        <div className="shop-admin-form">
          <label>
            상품명
            <input placeholder="예: 연필 교환권" />
          </label>
          <label>
            가격
            <input placeholder="예: 30비빔" />
          </label>
          <label>
            카테고리
            <select defaultValue="teacher">
              <option value="teacher">선생님 상품</option>
              <option value="digital">디지털 상품</option>
            </select>
          </label>
          <label className="image-upload">
            상품 사진
            <input type="file" accept="image/*" onChange={handleImageChange} />
            <span>{fileName}</span>
          </label>
          <div className="image-preview">
            {preview ? <img src={preview} alt="상품 사진 미리보기" /> : <span>사진 미리보기</span>}
          </div>
          <button className="wide-action">상품 올리기</button>
        </div>
      </PlainSection>
      <PlainSection title="학생 상점 노출" description="등록한 사진과 상품 정보는 학생 화면의 선생님 상품 탭에 표시됩니다.">
        <DataTable
          headers={["상품", "가격", "카테고리", "사진"]}
          rows={[
            ["연필 교환권", "30비빔", "선생님 상품", "등록 가능"],
            ["간식 우선권", "50비빔", "선생님 상품", "등록 가능"]
          ]}
        />
      </PlainSection>
    </div>
  );
}

function Overview() {
  return (
    <div className="page-flow">
      <PlainSection title="오늘 운영 요약" description="교사가 먼저 확인해야 하는 상태만 위로 올렸어요.">
        <DefinitionList rows={summaryRows} />
      </PlainSection>
      <PlainSection title="모듈 상태" description="학생에게 보이는 기능과 교사가 처리해야 하는 일을 함께 확인해요.">
        <DataTable headers={["모듈", "상태", "학생 노출", "교사 확인"]} rows={modules} />
      </PlainSection>
    </div>
  );
}

function Setup() {
  return (
    <div className="page-flow">
      <PlainSection title="학급 기본 설정" description="백엔드 학급 생성·설정 조회 API에 맞춰 들어갈 값입니다.">
        <DefinitionList rows={policyRows} />
      </PlainSection>
      <PlainSection title="연결될 API" description="프론트 구현 시 이 흐름을 기준으로 화면 상태를 나눕니다.">
        <DataTable headers={["기능", "API", "화면 역할"]} rows={apiRows} />
      </PlainSection>
    </div>
  );
}

function Roster() {
  return (
    <div className="page-flow">
      <PlainSection title="학생 명단과 급여 역할" description="출석번호, 닉네임, 역할, 지급액을 한 화면에서 검토합니다.">
        <DataTable headers={["번호", "닉네임", "역할", "월급", "상태"]} rows={students} />
      </PlainSection>
      <PlainSection title="저장 전 확인" description="저장하면 학생 로그인, 월급명세서, 지갑 계산에 이어집니다.">
        <ActionLine label="명단 저장" value="28명 반영 예정" action="저장" />
        <ActionLine label="비밀번호 초기화" value="요청 1건" action="확인" />
      </PlainSection>
    </div>
  );
}

function Control() {
  return (
    <div className="page-flow">
      <PlainSection title="수업 모드 제어" description="학생 화면을 자율 활동 또는 선생님 수업으로 전환합니다.">
        <ActionLine label="현재 모드" value="자율 활동" action="수업 시작" />
        <ActionLine label="퀴즈 세션" value="문항 8개 준비됨" action="퀴즈 열기" />
        <ActionLine label="공용 화면" value="투표 결과 표시 가능" action="화면 공유" />
      </PlainSection>
      <PlainSection title="학생에게 열려 있는 기능" description="잠긴 기능은 학생 화면에서 이유와 함께 비활성화됩니다.">
        <DataTable headers={["기능", "학생 상태", "제어 방식", "메모"]} rows={modules} />
      </PlainSection>
    </div>
  );
}

function Approvals() {
  return (
    <div className="page-flow">
      <PlainSection title="승인 대기" description="학생 돈이 움직이거나 권리 상품이 지급되는 작업만 모았습니다.">
        <DataTable headers={["구분", "대상", "금액", "상태"]} rows={approvals} />
      </PlainSection>
      <PlainSection title="급여 정산" description="지급 전에는 총액과 세금, 공동기금 이동액을 먼저 확인합니다.">
        <ActionLine label="총 급여" value="4,200비빔" action="지급" />
        <ActionLine label="소득세" value="420비빔 공동기금 이동" action="내역 보기" />
      </PlainSection>
    </div>
  );
}

function PlainSection({
  title,
  description,
  children
}: Readonly<{ title: string; description: string; children: React.ReactNode }>) {
  return (
    <section className="plain-section">
      <div className="section-title">
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      {children}
    </section>
  );
}

function DefinitionList({ rows }: Readonly<{ rows: string[][] }>) {
  return (
    <dl className="definition-list">
      {rows.map(([label, value, note]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>
            <strong>{value}</strong>
            <span>{note}</span>
          </dd>
        </div>
      ))}
    </dl>
  );
}

function DataTable({ headers, rows }: Readonly<{ headers: string[]; rows: string[][] }>) {
  return (
    <table className="data-table">
      <thead>
        <tr>
          {headers.map((header) => <th key={header}>{header}</th>)}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.join("-")}>
            {row.map((cell, index) => <td key={`${cell}-${index}`}>{cell}</td>)}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ActionLine({ label, value, action }: Readonly<{ label: string; value: string; action: string }>) {
  return (
    <div className="action-line">
      <div>
        <b>{label}</b>
        <span>{value}</span>
      </div>
      <button>{action}</button>
    </div>
  );
}
