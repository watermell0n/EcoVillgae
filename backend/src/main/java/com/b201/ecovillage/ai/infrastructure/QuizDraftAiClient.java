package com.b201.ecovillage.ai.infrastructure;

import com.b201.ecovillage.ai.contract.QuizDraftContract;

public interface QuizDraftAiClient {

    QuizDraftContract.Response draft(QuizDraftContract.Request request);
}
