package com.roottrace.integration;

import com.roottrace.incident.Incident;
import com.roottrace.intelligence.dto.IncidentBriefingResponse;

public interface ExternalNotificationService {

    void sendIncidentAlert(Incident incident, String channel);

    void sendIncidentBriefing(Incident incident, IncidentBriefingResponse briefing, String channel);
}
