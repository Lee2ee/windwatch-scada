package com.windwatch.scada.repository;

import com.windwatch.scada.model.Turbine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TurbineRepository extends JpaRepository<Turbine, String> {
    List<Turbine> findByActiveTrueOrderByTurbineId();
    List<Turbine> findAllByOrderByTurbineId();
}
