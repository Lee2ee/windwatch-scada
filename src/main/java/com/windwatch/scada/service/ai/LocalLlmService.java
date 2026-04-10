package com.windwatch.scada.service.ai;

import com.windwatch.scada.dto.LlmResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalLlmService implements LlmService {
    @Value("${windwatch.ai.local.llm-url}")
    private String llmUrl;
    private final RestTemplate restTemplate;

    @Override
    public LlmResponseDto ask(String question, String context) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("question", question);
            requestBody.put("context", context != null ? context : "");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<LlmResponseDto> response = restTemplate.postForEntity(llmUrl, entity, LlmResponseDto.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                LlmResponseDto result = response.getBody();
                result.setModelUsed("Llama-3 (Local Ollama)");
                result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                return result;
            }
        } catch (Exception e) {
            log.warn("Local LLM unavailable, using mock: {}", e.getMessage());
        }
        return mockResponse(question, System.currentTimeMillis() - startTime);
    }

    private LlmResponseDto mockResponse(String question, long elapsed) {
        LlmResponseDto result = new LlmResponseDto();
        result.setAnswer("[보안 모드 - Llama-3 Local]\n\n" +
            "질문: \"" + question + "\"\n\n" +
            "분석 결과: 기어박스 온도 상승의 주요 원인은 윤활유 열화, 베어링 마모, 또는 냉각 시스템 이상일 수 있습니다.\n\n" +
            "권장 조치:\n1. 즉시 기어박스 오일 샘플 채취 및 점검\n2. 냉각 팬 동작 상태 확인\n3. 진동 센서 데이터와 교차 분석\n4. 필요 시 터빈 출력 제한 운전");
        result.setModelUsed("Llama-3 (Local Ollama - Mock)");
        result.setProcessingTimeMs(elapsed + 1200);
        List<LlmResponseDto.Reference> refs = new ArrayList<>();
        LlmResponseDto.Reference ref1 = new LlmResponseDto.Reference();
        ref1.setTitle("풍력발전기 기어박스 유지보수 매뉴얼 v3.2");
        ref1.setExcerpt("기어박스 온도가 85°C를 초과할 경우 즉각적인 점검이 필요합니다...");
        ref1.setSource("내부 문서 / Maintenance-GB-001");
        refs.add(ref1);
        LlmResponseDto.Reference ref2 = new LlmResponseDto.Reference();
        ref2.setTitle("IEC 61400-4 기어박스 설계 표준");
        ref2.setExcerpt("운전 온도 한계치는 최대 90°C이며, 연속 운전 권장 온도는 75°C입니다...");
        ref2.setSource("IEC Standard / 61400-4:2012");
        refs.add(ref2);
        result.setReferences(refs);
        return result;
    }

    @Override
    public String getEngineType() { return "LOCAL"; }
}
