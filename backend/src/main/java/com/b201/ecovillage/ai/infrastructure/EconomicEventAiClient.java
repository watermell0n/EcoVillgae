package com.b201.ecovillage.ai.infrastructure;

import com.b201.ecovillage.ai.contract.EconomicEventContract;

public interface EconomicEventAiClient {

    EconomicEventContract.Response draft(EconomicEventContract.Request request);
}
