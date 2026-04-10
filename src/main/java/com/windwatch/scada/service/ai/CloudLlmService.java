package com.windwatch.scada.service.ai;

import com.windwatch.scada.dto.LlmResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudLlmService implements LlmService {
    @Value("${windwatch.ai.cloud.openai-api-key}")
    private String openaiApiKey;
    private final RestTemplate restTemplate;

    @Override
    public LlmResponseDto ask(String question, String context) {
        long startTime = System.currentTimeMillis();
        // OpenAI API integration placeholder
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        LlmResponseDto result = new LlmResponseDto();
        result.setAnswer("[강력한 추론 모드 - GPT-4o]\n\n" +
            "심층 분석 결과:\n\n" +
            question + " 에 대한 종합적인 고장 원인 분석을 진행했습니다.\n\n" +
            "**가능성 높은 원인 (우선순위 순):**\n" +
            "1. **기어박스 윤활유 열화** (확률: 65%) - 운전 시간 및 온도 이력 기반\n" +
            "2. **냉각 시스템 부분 막힘** (확률: 25%) - 최근 온도 상승 패턴 분석\n" +
            "3. **부하 불균형** (확률: 10%) - 피치 각도 편차 데이터 참조\n\n" +
            "**권장 예방 정비 일정:** 72시간 이내 점검 권고");
        result.setModelUsed("GPT-4o (OpenAI Cloud)");
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        List<LlmResponseDto.Reference> refs = new ArrayList<>();
        LlmResponseDto.Reference ref = new LlmResponseDto.Reference();
        ref.setTitle("Gearbox Failure Analysis - Wind Turbine Best Practices");
        ref.setExcerpt("Temperature exceedance above 85°C indicates immediate maintenance intervention required...");
        ref.setSource("Cloud RAG / Industry Knowledge Base");
        refs.add(ref);
        result.setReferences(refs);
        return result;
    }

    @Override
    public String getEngineType() { return "CLOUD"; }
}
