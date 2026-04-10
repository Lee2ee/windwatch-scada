package com.windwatch.scada.service.ai;

import com.windwatch.scada.dto.VisionResultDto;
import org.springframework.web.multipart.MultipartFile;

public interface VisionService {
    VisionResultDto analyze(MultipartFile image);
    String getModelType();
}
