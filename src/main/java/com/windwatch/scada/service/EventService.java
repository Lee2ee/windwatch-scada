package com.windwatch.scada.service;

import com.windwatch.scada.model.ScadaEvent;
import com.windwatch.scada.repository.ScadaEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {
    private final ScadaEventRepository eventRepository;

    public Page<ScadaEvent> searchEvents(String turbineId, String severity, String status,
                                          String from, String to, int page, int size) {
        LocalDateTime fromDt = from != null && !from.isEmpty() ? LocalDateTime.parse(from + "T00:00:00") : null;
        LocalDateTime toDt = to != null && !to.isEmpty() ? LocalDateTime.parse(to + "T23:59:59") : null;

        String turbineIdParam = (turbineId != null && !turbineId.isEmpty()) ? turbineId : null;
        String severityParam = (severity != null && !severity.isEmpty()) ? severity : null;
        String statusParam = (status != null && !status.isEmpty()) ? status : null;

        return eventRepository.searchEvents(
            turbineIdParam, severityParam, statusParam, fromDt, toDt,
            PageRequest.of(page, size, Sort.by("occurredAt").descending())
        );
    }

    public List<ScadaEvent> getActiveAlarms() {
        return eventRepository.findByStatusOrderByOccurredAtDesc("ACTIVE");
    }

    @Transactional
    public void acknowledgeEvent(Long id, String username) {
        eventRepository.findById(id).ifPresent(event -> {
            event.setStatus("ACKNOWLEDGED");
            event.setResolvedBy(username);
            eventRepository.save(event);
        });
    }

    @Transactional
    public void resolveEvent(Long id, String username) {
        eventRepository.findById(id).ifPresent(event -> {
            event.setStatus("RESOLVED");
            event.setResolvedBy(username);
            event.setResolvedAt(LocalDateTime.now());
            eventRepository.save(event);
        });
    }

    public List<ScadaEvent> getAllForExport() {
        return eventRepository.findAll(Sort.by("occurredAt").descending());
    }
}
