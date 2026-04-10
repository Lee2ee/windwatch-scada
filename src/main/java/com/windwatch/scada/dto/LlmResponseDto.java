package com.windwatch.scada.dto;

import lombok.Data;
import java.util.List;

@Data
public class LlmResponseDto {
    private String answer;
    private List<Reference> references;
    private String modelUsed;
    private long processingTimeMs;

    @Data
    public static class Reference {
        private String title;
        private String excerpt;
        private String source;
    }
}
