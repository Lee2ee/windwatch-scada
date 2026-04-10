package com.windwatch.scada.api;

import com.windwatch.scada.dto.LlmResponseDto;
import com.windwatch.scada.service.ai.CloudLlmService;
import com.windwatch.scada.service.ai.LocalLlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmApiController {
    private final LocalLlmService localLlmService;
    private final CloudLlmService cloudLlmService;

    @PostMapping("/ask")
    public ResponseEntity<LlmResponseDto> ask(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String context = request.get("context");
        String engine = request.getOrDefault("engine", "LOCAL");

        LlmResponseDto result = "CLOUD".equalsIgnoreCase(engine)
            ? cloudLlmService.ask(question, context)
            : localLlmService.ask(question, context);
        return ResponseEntity.ok(result);
    }
}
