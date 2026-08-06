from __future__ import annotations

from ecovillage_ai.models import InvestmentCompanyOutput, InvestmentCompanyPayload


def investment_company_context_codes(
    payload: InvestmentCompanyPayload,
    output: InvestmentCompanyOutput,
) -> list[str]:
    """요청한 만큼, 요청한 방식으로, 새 이름만 왔는지 확인한다.

    학생이 보는 기업 목록은 이 출력 그대로 저장된다. 개수가 어긋나거나 이미 쓴 이름이
    다시 오면 시나리오가 어긋나므로 여기서 거절하고 다시 만들게 한다.
    """

    codes: list[str] = []
    if output.investment_mode is not payload.investment_mode:
        codes.append("INVESTMENT_MODE_MISMATCH")
    if len(output.companies) != payload.company_count:
        codes.append("INVESTMENT_COMPANY_COUNT_MISMATCH")
    used = {" ".join(name.split()).casefold() for name in payload.used_names}
    if any(" ".join(company.name.split()).casefold() in used for company in output.companies):
        codes.append("INVESTMENT_COMPANY_NAME_REUSED")
    return list(dict.fromkeys(codes))
