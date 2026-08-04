package com.b201.ecovillage.finance.api;

import java.util.List;

public record FinancialProductsResponse(
    List<FinancialProductViewResponse> financialProducts
) {
}
