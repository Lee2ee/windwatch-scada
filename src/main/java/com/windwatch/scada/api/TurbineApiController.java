package com.windwatch.scada.api;

import com.windwatch.scada.model.TurbineData;
import com.windwatch.scada.service.TurbineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/turbine")
@RequiredArgsConstructor
public class TurbineApiController {
    private final TurbineService turbineService;

    @GetMapping("/{turbineId}/history")
    public List<TurbineData> getHistory(@PathVariable String turbineId) {
        return turbineService.getLatestData(turbineId);
    }
}
