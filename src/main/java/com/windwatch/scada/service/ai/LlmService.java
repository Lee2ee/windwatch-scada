package com.windwatch.scada.service.ai;

import com.windwatch.scada.dto.LlmResponseDto;

public interface LlmService {
    LlmResponseDto ask(String question, String context);
    String getEngineType();
}
