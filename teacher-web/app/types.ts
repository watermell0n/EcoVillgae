// AI 패널이 page.tsx로부터 받는 값의 최소 계약입니다.
// 소유: AI 담당 — page.tsx는 자체 타입을 선언해 쓰므로 여기 정의는 그 구조적 부분집합으로
// 유지합니다. 필드를 추가할 때는 page.tsx의 ModuleRow에도 있는 필드만 넣습니다.

export type AiModuleRow = {
  name: string;
  status: string;
  code?: string;
  // 학급 모듈의 resource_id입니다. 시작 전 점검은 이 값을 target_id로 되돌려 보내야 합니다.
  resourceId?: string;
  stateVersion?: number;
  config?: Record<string, unknown>;
};

// modules-and-policies 응답의 policy_versions 항목입니다.
// expected_policy_version은 이 목록의 version을 그대로 되돌려 보내야 합니다.
export type AiPolicyVersionRow = {
  policy_area?: string;
  version?: number;
};

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
    case "LOAN":
      return "LOAN_RATE";
    default:
      return null;
  }
}
