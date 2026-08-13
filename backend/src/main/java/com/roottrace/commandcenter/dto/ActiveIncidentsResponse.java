package com.roottrace.commandcenter.dto;

import java.util.List;

public record ActiveIncidentsResponse(
        int totalActive,
        List<ActiveIncidentResponse> incidents
) {}
