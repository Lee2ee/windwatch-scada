package com.windwatch.scada.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publishToTopic(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend(topic, payload);
        } catch (Exception e) {
            log.error("Failed to publish to topic {}: {}", topic, e.getMessage());
        }
    }

    public void publishAlarm(Object alarmPayload) {
        publishToTopic("/topic/alarms", alarmPayload);
    }

    public void publishTurbineData(Object turbineData) {
        publishToTopic("/topic/turbine-data", turbineData);
    }
}
