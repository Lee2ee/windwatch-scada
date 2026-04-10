package com.windwatch.scada.api;

import com.windwatch.scada.dto.VisionResultDto;
import com.windwatch.scada.service.ai.CloudVisionService;
import com.windwatch.scada.service.ai.LocalVisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vision")
@RequiredArgsConstructor
public class VisionApiController {
    private final LocalVisionService localVisionService;
    private final CloudVisionService cloudVisionService;

    @PostMapping("/analyze")
    public ResponseEntity<VisionResultDto> analyze(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "modelType", defaultValue = "LOCAL") String modelType) {

        VisionResultDto result = "CLOUD".equalsIgnoreCase(modelType)
            ? cloudVisionService.analyze(image)
            : localVisionService.analyze(image);
        return ResponseEntity.ok(result);
    }
}
