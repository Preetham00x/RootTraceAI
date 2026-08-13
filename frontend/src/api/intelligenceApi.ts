import { apiClient } from './client';
import type { IncidentBriefing, IncidentCluster, SreMetricsSummary } from '../types';

export const intelligenceApi = {
  getSreMetrics: async (days: number = 30): Promise<SreMetricsSummary> => {
    const response = await apiClient.get<any>('/api/metrics/sre', {
      params: { days },
    });
    const d = response.data || {};
    return {
      periodDays: d.windowDays ?? days,
      totalIncidents: d.totalIncidents ?? 0,
      resolvedIncidents: d.resolvedIncidents ?? 0,
      meanTimeToResolveMinutes: d.meanTimeToResolveMinutes ?? 0,
      meanTimeToDetectMinutes: d.meanTimeToDetectMinutes ?? 0,
      recurrenceRatePercentage: d.recurrenceRatePercentage ?? d.recurrenceRate ?? 0,
      severityCounts: d.severityCounts ?? {},
      calculatedAt: d.calculatedAt ?? new Date().toISOString(),
    };
  },

  getIncidentTrends: async (
    days: number = 30,
    interval: string = 'daily'
  ): Promise<{ dataPoints: { date: string; incidentCount: number; mttrMinutes: number }[] }> => {
    const response = await apiClient.get<any>(
      '/api/metrics/incidents/trends',
      { params: { days, interval } }
    );
    return { dataPoints: response.data.dataPoints || [] };
  },

  getClusters: async (
    service?: string,
    minClusterSize: number = 2
  ): Promise<{ clusters: IncidentCluster[] }> => {
    const response = await apiClient.get<any>('/api/incidents/clusters', {
      params: { service, minClusterSize },
    });
    const list = response.data.clusters || [];
    return {
      clusters: list.map((c: any) => ({
        clusterId: c.clusterId ?? '',
        service: c.service ?? 'Unknown',
        patternDescription: c.patternDescription || c.title || 'Cluster',
        incidentCount: c.incidentCount ?? 0,
        sampleIncidentTitles: c.sampleIncidentTitles || [],
        sharedSymptoms: c.sharedSymptoms || (c.primaryRootCause ? [c.primaryRootCause] : []),
        suggestedAction: c.suggestedAction || (c.hasOpenActionItems ? 'Resolve open action items' : 'Investigate cluster pattern'),
      })),
    };
  },

  getIncidentBriefing: async (incidentId: string): Promise<IncidentBriefing> => {
    const response = await apiClient.get<IncidentBriefing>(`/api/incidents/${incidentId}/intelligence`);
    return response.data;
  },
};
