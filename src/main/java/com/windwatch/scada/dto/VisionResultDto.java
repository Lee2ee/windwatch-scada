package com.windwatch.scada.dto;

import lombok.Data;
import java.util.List;

@Data
public class VisionResultDto {
    private boolean defectDetected;
    private List<Detection> detections;
    private String processedImageBase64;
    private String modelUsed;
    private long processingTimeMs;

    @Data
    public static class Detection {
        private String label;
        private double confidence;
        private int[] bbox; // [x, y, width, height]
    }
}
