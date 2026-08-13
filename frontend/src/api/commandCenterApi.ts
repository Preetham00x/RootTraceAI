import { apiClient } from './client';
import type {
  ActiveIncidentItem,
  CommandCenterOverview,
  ExecutiveReliabilityAdvisor,
  IncidentCommand,
  ReliabilityEvent,
  ServiceHealthDetail,
  ServiceHealthSummary,
} from '../types';

export const commandCenterApi = {
  getOverview: async (days: number = 30): Promise<CommandCenterOverview> => {
    const response = await apiClient.get<CommandCenterOverview>('/api/command-center/overview', {
      params: { days },
    });
    return response.data;
  },

  getServiceSummaries: async (
    days: number = 30,
    limit: number = 50,
    sort: string = 'risk'
  ): Promise<ServiceHealthSummary[]> => {
    const response = await apiClient.get<ServiceHealthSummary[]>('/api/command-center/services', {
      params: { days, limit, sort },
    });
    return response.data;
  },

  getServiceDetail: async (serviceName: string, days: number = 30): Promise<ServiceHealthDetail> => {
    const response = await apiClient.get<ServiceHealthDetail>(`/api/command-center/services/${serviceName}`, {
      params: { days },
    });
    return response.data;
  },

  getActiveIncidents: async (): Promise<ActiveIncidentItem[]> => {
    const response = await apiClient.get<{ activeIncidents: ActiveIncidentItem[] }>(
      '/api/command-center/incidents/active'
    );
    return response.data.activeIncidents || [];
  },

  getIncidentCommand: async (incidentId: string): Promise<IncidentCommand> => {
    const response = await apiClient.get<IncidentCommand>(`/api/command-center/incidents/${incidentId}`);
    return response.data;
  },

  getAdvisorBriefing: async (days: number = 30): Promise<ExecutiveReliabilityAdvisor> => {
    const response = await apiClient.get<{ advisor: ExecutiveReliabilityAdvisor }>(
      '/api/command-center/advisor',
      { params: { days } }
    );
    return response.data.advisor;
  },

  getEvents: async (limit: number = 50, days: number = 30): Promise<ReliabilityEvent[]> => {
    const response = await apiClient.get<{ events: ReliabilityEvent[] }>('/api/command-center/events', {
      params: { limit, days },
    });
    return response.data.events || [];
  },
};
