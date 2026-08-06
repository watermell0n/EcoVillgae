package com.b201.ecovillage.ai.infrastructure;

import com.b201.ecovillage.ai.contract.InvestmentCompanyContract;

public interface InvestmentCompanyAiClient {

    InvestmentCompanyContract.Response create(InvestmentCompanyContract.Request request);
}
