package com.roottrace.integration;

import com.roottrace.incident.Incident;
import com.roottrace.integration.dto.CreateJiraTicketRequest;
import com.roottrace.integration.dto.JiraTicketResponse;
import com.roottrace.postmortem.PostmortemActionItem;

public interface ExternalTicketService {

    JiraTicketResponse createTicketForActionItem(
            Incident incident,
            PostmortemActionItem actionItem,
            CreateJiraTicketRequest request
    );
}
