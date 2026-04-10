package com.windwatch.scada.service.ai;

import com.windwatch.scada.dto.VisionResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalVisionService implements VisionService {
    @Value("${windwatch.ai.local.vision-url}")
    private String visionUrl;

    private final RestTemplate restTemplate;
    private final Random random = new Random();

    @Override
    public VisionResultDto analyze(MultipartFile image) {
        long startTime = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", image.getResource());
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<VisionResultDto> response = restTemplate.postForEntity(visionUrl, requestEntity, VisionResultDto.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                VisionResultDto result = response.getBody();
                result.setModelUsed("YOLOv8 (Local)");
                result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                return result;
            }
        } catch (Exception e) {
            log.warn("Local vision service unavailable, using mock data: {}", e.getMessage());
        }
        return mockResult(System.currentTimeMillis() - startTime);
    }

    private VisionResultDto mockResult(long elapsed) {
        VisionResultDto result = new VisionResultDto();
        boolean hasDefect = random.nextBoolean();
        result.setDefectDetected(hasDefect);
        result.setModelUsed("YOLOv8 (Local - Mock)");
        result.setProcessingTimeMs(elapsed + 300);
        List<VisionResultDto.Detection> detections = new ArrayList<>();
        if (hasDefect) {
            VisionResultDto.Detection d = new VisionResultDto.Detection();
            String[] defectTypes = {"blade_crack", "surface_erosion", "lightning_damage", "leading_edge_erosion"};
            d.setLabel(defectTypes[random.nextInt(defectTypes.length)]);
            d.setConfidence(0.7 + random.nextDouble() * 0.25);
            d.setBbox(new int[]{random.nextInt(200), random.nextInt(200), 100 + random.nextInt(200), 80 + random.nextInt(150)});
            detections.add(d);
        }
        result.setDetections(detections);
        return result;
    }

    @Override
    public String getModelType() { return "LOCAL"; }
}
