import { apiClient } from './client';
import type { IncidentBriefing, IncidentCluster, SreMetricsSummary } from '../types';

export const intelligenceApi = {
  getSreMetrics: async (days: number = 30): Promise<SreMetricsSummary> => {
    const response = await apiClient.get<SreMetricsSummary>('/api/metrics/sre', {
      params: { days },
    });
    return response.data;
  },

  getIncidentTrends: async (
    days: number = 30,
    interval: string = 'daily'
  ): Promise<{ dataPoints: { date: string; incidentCount: number; mttrMinutes: number }[] }> => {
    const response = await apiClient.get<{ dataPoints: { date: string; incidentCount: number; mttrMinutes: number }[] }>(
      '/api/metrics/incidents/trends',
      { params: { days, interval } }
    );
    return response.data;
  },

  getClusters: async (
    service?: string,
    minClusterSize: number = 2
  ): Promise<{ clusters: IncidentCluster[] }> => {
    const response = await apiClient.get<{ clusters: IncidentCluster[] }>('/api/incidents/clusters', {
      params: { service, minClusterSize },
    });
    return response.data;
  },

  getIncidentBriefing: async (incidentId: string): Promise<IncidentBriefing> => {
    const response = await apiClient.get<IncidentBriefing>(`/api/incidents/${incidentId}/intelligence`);
    return response.data;
  },
};
