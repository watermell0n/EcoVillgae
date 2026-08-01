package com.b201.ecovillage.ai.infrastructure;

import com.b201.ecovillage.ai.contract.BalancingContract;

public interface BalancingAiClient {

    BalancingContract.Response explain(BalancingContract.Request request);
}
