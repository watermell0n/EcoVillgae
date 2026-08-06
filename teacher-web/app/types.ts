// AI 패널이 page.tsx로부터 받는 값의 최소 계약입니다.
// 소유: AI 담당 — page.tsx는 자체 타입을 선언해 쓰므로 여기 정의는 그 구조적 부분집합으로
// 유지합니다. 필드를 추가할 때는 page.tsx의 ModuleRow에도 있는 필드만 넣습니다.

export type AiModuleRow = {
  name: string;
  // 화면에 보여 주는 한국어 상태입니다("열림"/"설정 대기"/"닫힘").
  status: string;
  // 서버가 준 원래 상태입니다("ACTIVE"/"PENDING_CONFIGURATION"/"DISABLED"/"STOPPING").
  // 판단은 이 값으로 합니다. 한국어 라벨은 표시에만 씁니다.
  serverStatus?: string;
  code?: string;
  // 학급 모듈의 resource_id입니다. 시작 전 점검은 이 값을 target_id로 되돌려 보내야 합니다.
  resourceId?: string;
  stateVersion?: number;
  config?: Record<string, unknown>;
};

export type InvestmentMode = "SHORT_TERM" | "LONG_TERM";

export const INVESTMENT_MODE_LABELS: Record<InvestmentMode, string> = {
  SHORT_TERM: "단기 투자",
  LONG_TERM: "장기 투자"
};

function toInvestmentMode(value: unknown): InvestmentMode | null {
  return value === "SHORT_TERM" || value === "LONG_TERM" ? value : null;
}

/**
 * 투자 모듈의 상태와, 교사가 쓰겠다고 고른 방식을 함께 읽습니다.
 *
 * 학급은 단기와 장기를 함께 운영할 수 있으므로 방식은 목록입니다. 목록이 생기기 전에 활성화된
 * 학급은 단일 값만 갖고 있어 그 값도 한 개짜리 목록으로 읽습니다.
 */
export function investmentModuleInfo(modules: readonly AiModuleRow[]) {
  const module = modules.find((item) => item.code === "INVESTMENT");
  const config = module?.config ?? {};
  const raw = config.investment_modes;
  const fromList = Array.isArray(raw)
    ? raw.map(toInvestmentMode).filter((value): value is InvestmentMode => value !== null)
    : [];
  const legacy = toInvestmentMode(config.investment_mode);
  const enabledModes = fromList.length > 0 ? fromList : legacy ? [legacy] : [];
  return {
    module,
    // 서버 상태로 판단합니다. 한국어 "열림"과 비교하면 화면 문구를 바꿀 때 조용히 깨집니다.
    // 다만 서버를 거치지 않고 만든 행에는 serverStatus가 없어 화면용 상태로 대신 봅니다.
    active: module?.serverStatus === undefined
      ? module?.status === "열림"
      : module.serverStatus === "ACTIVE",
    enabledModes,
    stateVersion: module?.stateVersion ?? 0
  };
}

// modules-and-policies 응답의 policy_versions 항목입니다.
// expected_policy_version은 이 목록의 version을 그대로 되돌려 보내야 합니다.
// values는 확정된 정책값입니다. 금리·세율 영역은 { rate_bps }, 급여는 { roles: [...] } 입니다.
export type AiPolicyVersionRow = {
  policy_area?: string;
  version?: number;
  values?: Record<string, unknown>;
};

// 값이 bps(베이시스 포인트)인 정책 영역입니다. 100 bps = 1%.
const RATE_POLICY_AREAS = ["INCOME_TAX_RATE", "DEPOSIT_RATE", "SAVINGS_RATE", "LOAN_RATE"];

export function isRatePolicyArea(policyArea: string | null) {
  return policyArea !== null && RATE_POLICY_AREAS.includes(policyArea);
}

// 300 -> "3.00%" 처럼 교사가 읽는 단위로 바꿉니다.
export function bpsToPercentText(bps: number) {
  return `${(bps / 100).toFixed(2)}%`;
}

export type AppliedPolicyValue = {
  label: string;
  text: string;
};

// 모듈에 지금 확정되어 있는 정책값입니다. 활성화 직후 "얼마가 적용됐는지" 화면에 보여줍니다.
export function appliedPolicyValues(
  policyVersions: readonly AiPolicyVersionRow[],
  moduleCode: string | undefined
): AppliedPolicyValue[] {
  const policyArea = modulePolicyArea(moduleCode);
  if (!policyArea) return [];
  const values = policyVersions.find((policy) => policy.policy_area === policyArea)?.values;
  if (!values) return [];

  if (isRatePolicyArea(policyArea)) {
    const rateBps = values.rate_bps;
    if (typeof rateBps !== "number") return [];
    return [{
      label: policyArea === "INCOME_TAX_RATE" ? "현재 소득세율" : "현재 금리",
      text: bpsToPercentText(rateBps)
    }];
  }

  // 급여는 역할이 여러 개일 수 있지만 모듈 카드에는 한 줄만 둡니다. 역할별 금액은 학생·급여
  // 화면에서 관리하며, 여기서는 지금 적용된 급여 수준만 알려 줍니다.
  if (policyArea === "SALARY" && Array.isArray(values.roles)) {
    const amounts = values.roles.flatMap((role) => {
      if (typeof role !== "object" || role === null) return [];
      const amount = (role as Record<string, unknown>).gross_amount;
      return typeof amount === "number" ? [amount] : [];
    });
    if (amounts.length === 0) return [];
    const lowest = Math.min(...amounts);
    const highest = Math.max(...amounts);
    return [{
      label: amounts.length === 1 ? "현재 1회 급여" : `현재 1회 급여 (역할 ${amounts.length}개)`,
      text: lowest === highest
        ? lowest.toLocaleString("ko-KR")
        : `${lowest.toLocaleString("ko-KR")} ~ ${highest.toLocaleString("ko-KR")}`
    }];
  }

  return [];
}

// 정책 영역의 현재 버전입니다. 아직 정책이 없으면 서버도 0으로 취급합니다.
export function policyVersionOf(
  policyVersions: readonly AiPolicyVersionRow[],
  policyArea: string | null
) {
  if (!policyArea) return 0;
  return policyVersions.find((policy) => policy.policy_area === policyArea)?.version ?? 0;
}

// SetupProposalTargetSnapshotService.modulePolicyArea와 같은 대응입니다.
export function modulePolicyArea(moduleCode: string | undefined) {
  switch (moduleCode) {
    case "ROLE_SALARY":
      return "SALARY";
    case "INCOME_TAX":
      return "INCOME_TAX_RATE";
    case "DEPOSIT":
      return "DEPOSIT_RATE";
    case "SAVINGS":
      return "SAVINGS_RATE";
    case "LOAN":
      return "LOAN_RATE";
    default:
      return null;
  }
}
