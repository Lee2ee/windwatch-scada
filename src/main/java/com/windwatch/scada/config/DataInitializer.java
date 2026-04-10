package com.windwatch.scada.config;

import com.windwatch.scada.model.ScadaEvent;
import com.windwatch.scada.model.Turbine;
import com.windwatch.scada.model.User;
import com.windwatch.scada.repository.ScadaEventRepository;
import com.windwatch.scada.repository.TurbineRepository;
import com.windwatch.scada.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class DataInitializer {
    private final UserRepository userRepository;
    private final TurbineRepository turbineRepository;
    private final ScadaEventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        initUsers();
        initTurbines();
        initSampleEvents();
    }

    private void initUsers() {
        if (userRepository.count() > 0) return;
        String pw = passwordEncoder.encode("password");
        userRepository.saveAll(List.of(
            createUser("admin",     pw, "ROLE_ADMIN",    "admin@windwatch.com"),
            createUser("operator",  pw, "ROLE_OPERATOR", "operator@windwatch.com"),
            createUser("viewer",    pw, "ROLE_VIEWER",   "viewer@windwatch.com")
        ));
    }

    private void initTurbines() {
        if (turbineRepository.count() > 0) return;
        turbineRepository.saveAll(List.of(
            createTurbine("WT-001", "1호기 (북서 해상)",  "제주 해상 북서구역 A-1", 2000.0),
            createTurbine("WT-002", "2호기 (북서 해상)",  "제주 해상 북서구역 A-2", 2000.0),
            createTurbine("WT-003", "3호기 (남동 해상)",  "제주 해상 남동구역 B-1", 2500.0),
            createTurbine("WT-004", "4호기 (남동 해상)",  "제주 해상 남동구역 B-2", 2500.0),
            createTurbine("WT-005", "5호기 (육상 단지)",  "서귀포 육상 C구역",      1500.0)
        ));
    }

    private void initSampleEvents() {
        if (eventRepository.count() > 0) return;
        Random rand = new Random();
        List<String> turbineIds = List.of("WT-001", "WT-002", "WT-003", "WT-004", "WT-005");
        String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
        String[][] paramMessages = {
            {"gearboxTemp",  "기어박스 온도 임계치 초과",    "85.0"},
            {"vibration",    "진동 임계치 초과",              "5.0"},
            {"powerOutput",  "발전량 급감 이상",              "100.0"},
            {"rotorRpm",     "회전속도 이상 감지",            "20.0"},
            {"windSpeed",    "강풍 경보 (컷아웃 임박)",       "22.0"},
            {"gearboxTemp",  "냉각 시스템 성능 저하 경고",    "75.0"},
            {"vibration",    "베어링 마모 경고",              "3.5"}
        };
        for (int i = 0; i < 60; i++) {
            String tid      = turbineIds.get(rand.nextInt(turbineIds.size()));
            String severity = severities[rand.nextInt(severities.length)];
            String[] pm     = paramMessages[rand.nextInt(paramMessages.length)];
            String status   = i < 8 ? "ACTIVE" : (i < 25 ? "ACKNOWLEDGED" : "RESOLVED");

            ScadaEvent e = new ScadaEvent();
            e.setTurbineId(tid);
            e.setSeverity(severity);
            e.setEventType("CRITICAL".equals(severity) || "HIGH".equals(severity) ? "ALARM" : "WARNING");
            e.setMessage(tid + " " + pm[1]);
            e.setParameter(pm[0]);
            e.setValue(Double.parseDouble(pm[2]) + rand.nextDouble() * 20 - 5);
            e.setThreshold(Double.parseDouble(pm[2]));
            e.setStatus(status);
            LocalDateTime occurred = LocalDateTime.now().minusHours(rand.nextInt(168));
            e.setOccurredAt(occurred);
            if ("RESOLVED".equals(status)) {
                e.setResolvedAt(occurred.plusHours(1 + rand.nextInt(4)));
                e.setResolvedBy("operator");
            } else if ("ACKNOWLEDGED".equals(status)) {
                e.setResolvedBy("operator");
            }
            eventRepository.save(e);
        }
    }

    private User createUser(String username, String password, String role, String email) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(password);
        u.setRole(role);
        u.setEmail(email);
        return u;
    }

    private Turbine createTurbine(String id, String name, String location, double capacity) {
        Turbine t = new Turbine();
        t.setTurbineId(id);
        t.setTurbineName(name);
        t.setLocation(location);
        t.setRatedCapacityKw(capacity);
        t.setActive(true);
        return t;
    }
}
