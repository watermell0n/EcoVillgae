package com.b201.ecovillage.ai.infrastructure;

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;

public interface SetupPrecheckAiClient {

    SetupPrecheckContract.Response explain(SetupPrecheckContract.Request request);
}
