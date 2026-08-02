package com.b201.ecovillage.ai.infrastructure;

import com.b201.ecovillage.ai.contract.TeacherChatContract;

public interface TeacherChatAiClient {

    TeacherChatContract.Response answer(TeacherChatContract.Request request);
}
