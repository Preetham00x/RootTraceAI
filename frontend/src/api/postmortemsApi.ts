import { apiClient } from './client';
import type { ActionItemPriority, Postmortem, PostmortemActionItem, PostmortemStatus } from '../types';

export interface UpdatePostmortemPayload {
  title?: string;
  summary?: string;
  impactSummary?: string;
  rootCauseAnalysis?: string;
  resolutionSummary?: string;
  lessonsLearned?: string;
  status?: PostmortemStatus;
  downtimeMinutes?: number;
}

export interface CreateActionItemPayload {
  title: string;
  description: string;
  category: string;
  priority: ActionItemPriority;
  assignedTo?: string;
  dueDate?: string;
}

export const postmortemsApi = {
  getPostmortem: async (incidentId: string): Promise<Postmortem> => {
    const response = await apiClient.get<Postmortem>(`/api/incidents/${incidentId}/postmortem`);
    return response.data;
  },

  generatePostmortem: async (incidentId: string): Promise<Postmortem> => {
    const response = await apiClient.post<Postmortem>(`/api/incidents/${incidentId}/postmortem/generate`);
    return response.data;
  },

  updatePostmortem: async (incidentId: string, payload: UpdatePostmortemPayload): Promise<Postmortem> => {
    const response = await apiClient.patch<Postmortem>(`/api/incidents/${incidentId}/postmortem`, payload);
    return response.data;
  },

  exportMarkdown: async (incidentId: string): Promise<string> => {
    const response = await apiClient.get<string>(`/api/incidents/${incidentId}/postmortem/export`, {
      responseType: 'text',
    });
    return response.data;
  },

  createActionItem: async (
    incidentId: string,
    payload: CreateActionItemPayload
  ): Promise<PostmortemActionItem> => {
    const response = await apiClient.post<PostmortemActionItem>(
      `/api/incidents/${incidentId}/postmortem/action-items`,
      payload
    );
    return response.data;
  },

  createJiraTicket: async (
    incidentId: string,
    actionItemId: string,
    summary?: string,
    description?: string
  ): Promise<{ externalTicketId: string; externalUrl: string }> => {
    const response = await apiClient.post<{ externalTicketId: string; externalUrl: string }>(
      `/api/incidents/${incidentId}/postmortem/action-items/${actionItemId}/jira`,
      { summary, description }
    );
    return response.data;
  },
};
