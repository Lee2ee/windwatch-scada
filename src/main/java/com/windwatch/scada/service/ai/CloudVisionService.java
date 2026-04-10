package com.windwatch.scada.service.ai;

import com.windwatch.scada.dto.VisionResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudVisionService implements VisionService {
    private final Random random = new Random();

    @Override
    public VisionResultDto analyze(MultipartFile image) {
        long startTime = System.currentTimeMillis();
        // Cloud Vision API integration placeholder - returns mock with higher confidence
        try { Thread.sleep(800); } catch (InterruptedException ignored) {}

        VisionResultDto result = new VisionResultDto();
        boolean hasDefect = random.nextInt(3) > 0;
        result.setDefectDetected(hasDefect);
        result.setModelUsed("Cloud Vision AI (GPT-4V)");
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        List<VisionResultDto.Detection> detections = new ArrayList<>();
        if (hasDefect) {
            VisionResultDto.Detection d = new VisionResultDto.Detection();
            String[] defectTypes = {"blade_crack", "surface_erosion", "leading_edge_pitting", "delamination"};
            d.setLabel(defectTypes[random.nextInt(defectTypes.length)]);
            d.setConfidence(0.88 + random.nextDouble() * 0.10);
            d.setBbox(new int[]{random.nextInt(200), random.nextInt(200), 150 + random.nextInt(200), 100 + random.nextInt(150)});
            detections.add(d);
        }
        result.setDetections(detections);
        return result;
    }

    @Override
    public String getModelType() { return "CLOUD"; }
}
