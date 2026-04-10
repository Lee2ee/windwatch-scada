package com.windwatch.scada.service;

import com.windwatch.scada.model.Turbine;
import com.windwatch.scada.repository.TurbineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTurbineService {

    private final TurbineRepository turbineRepository;
    private final TurbineService turbineService;

    public List<Turbine> getAllTurbines() {
        return turbineRepository.findAllByOrderByTurbineId();
    }

    @Transactional
    public void createTurbine(String turbineId, String turbineName, String location,
                              double ratedCapacityKw) {
        if (turbineRepository.existsById(turbineId)) {
            throw new IllegalArgumentException("이미 존재하는 터빈 ID입니다: " + turbineId);
        }
        if (!turbineId.matches("[A-Z]{2}-\\d{3,}")) {
            throw new IllegalArgumentException("터빈 ID 형식은 영문 2자리-숫자 3자리 이상이어야 합니다. 예: WT-006");
        }
        Turbine t = new Turbine();
        t.setTurbineId(turbineId.toUpperCase());
        t.setTurbineName(turbineName);
        t.setLocation(location);
        t.setRatedCapacityKw(ratedCapacityKw);
        t.setActive(true);
        turbineRepository.save(t);
        turbineService.invalidateCache();
    }

    @Transactional
    public void toggleActive(String turbineId) {
        turbineRepository.findById(turbineId).ifPresent(t -> {
            t.setActive(!t.getActive());
            turbineRepository.save(t);
            turbineService.invalidateCache();
            if (!t.getActive()) {
                turbineService.removeSimState(turbineId);
            }
        });
    }

    @Transactional
    public void updateTurbine(String turbineId, String turbineName, String location,
                              double ratedCapacityKw) {
        turbineRepository.findById(turbineId).ifPresent(t -> {
            t.setTurbineName(turbineName);
            t.setLocation(location);
            t.setRatedCapacityKw(ratedCapacityKw);
            turbineRepository.save(t);
            turbineService.invalidateCache();
        });
    }

    @Transactional
    public void deleteTurbine(String turbineId) {
        turbineRepository.deleteById(turbineId);
        turbineService.invalidateCache();
        turbineService.removeSimState(turbineId);
    }
}
