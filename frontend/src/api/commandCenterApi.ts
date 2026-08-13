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
    const response = await apiClient.get<any>('/api/command-center/overview', {
      params: { days },
    });
    const data = response.data;
    return {
      reliabilityScore: {
        score: data.overallReliabilityScore ?? 100,
        riskTier: data.overallRiskTier ?? 'LOW',
        targetScore: 99.9,
        trend: 'STABLE',
        penalties: [],
        evaluatedAt: new Date().toISOString(),
      },
      totalServices: data.totalServices ?? 0,
      servicesAtRisk: data.servicesAtRisk ?? 0,
      totalIncidents30d: data.totalIncidents ?? 0,
      activeIncidents: data.activeIncidents ?? 0,
      criticalIncidents30d: data.criticalIncidents ?? 0,
      highIncidents30d: data.highIncidents ?? 0,
      meanTimeToResolveMinutes: data.meanMttrMinutes ?? 0,
      meanTimeToDetectMinutes: data.meanMttdMinutes ?? 0,
      totalSlos: data.sloCount ?? 0,
      healthySlos: data.healthySlos ?? 0,
      warningSlos: data.warningSlos ?? 0,
      breachedSlos: data.breachedSlos ?? 0,
      averageErrorBudgetConsumedPercentage: data.errorBudgetConsumptionPercent ?? 0,
      openPostmortemActionItems: data.openPostmortemActionItems ?? 0,
      overduePostmortemActionItems: data.overdueActionItems ?? 0,
      failedRunbookExecutions30d: data.failedRunbookExecutions ?? 0,
      calculatedAt: new Date().toISOString(),
    };
  },

  getServiceSummaries: async (
    days: number = 30,
    limit: number = 50,
    sort: string = 'risk'
  ): Promise<ServiceHealthSummary[]> => {
    const response = await apiClient.get<any[]>('/api/command-center/services', {
      params: { days, limit, sort },
    });
    return (response.data || []).map((s: any) => ({
      serviceName: s.serviceName ?? 'Unknown',
      healthScore: s.healthScore ?? 100,
      riskTier: s.riskTier ?? 'LOW',
      environment: s.environment ?? 'production',
      totalIncidents30d: s.incidentCount ?? 0,
      activeIncidents: s.activeIncidentCount ?? 0,
      criticalIncidents30d: s.criticalIncidentCount ?? 0,
      meanTimeToResolveMinutes: s.meanMttrMinutes ?? 0,
      totalSlos: s.sloCount ?? 0,
      breachedSlos: s.breachedSloCount ?? 0,
      averageErrorBudgetConsumedPercentage: s.errorBudgetConsumptionPercent ?? 0,
      openActionItems: s.openActionItems ?? 0,
      overdueActionItems: s.overdueActionItems ?? 0,
      recentRunbookFailures: s.failedRunbookExecutions ?? 0,
      lastIncidentAt: null
    }));
  },

  getServiceDetail: async (serviceName: string, days: number = 30): Promise<ServiceHealthDetail> => {
    const response = await apiClient.get<ServiceHealthDetail>(`/api/command-center/services/${serviceName}`, {
      params: { days },
    });
    return response.data;
  },

  getActiveIncidents: async (): Promise<ActiveIncidentItem[]> => {
    const response = await apiClient.get<any>('/api/command-center/incidents/active');
    const incidents = response.data.activeIncidents || response.data || [];
    const list = Array.isArray(incidents) ? incidents : [];
    
    return list.map((i: any) => ({
      id: i.id ?? '',
      title: i.title ?? 'Unknown Incident',
      service: i.service ?? 'Unknown',
      severity: i.severity ?? 'LOW',
      status: i.status ?? 'OPEN',
      environment: i.environment ?? 'production',
      createdAt: i.createdAt ?? new Date().toISOString(),
      ageMinutes: i.ageMinutes ?? 0,
      sloBreached: i.sloBreached ?? false,
      burnRateTier: i.burnRateTier ?? 'NORMAL',
      serviceRiskTier: i.serviceRiskTier ?? 'LOW',
      priorityScore: i.priorityScore ?? 0,
      recommendedAttention: i.recommendedAttention ?? 'NORMAL'
    }));
  },

  getIncidentCommand: async (incidentId: string): Promise<IncidentCommand> => {
    const response = await apiClient.get<IncidentCommand>(`/api/command-center/incidents/${incidentId}`);
    return response.data;
  },

  getAdvisorBriefing: async (days: number = 30): Promise<ExecutiveReliabilityAdvisor> => {
    const response = await apiClient.get<any>(
      '/api/command-center/advisor',
      { params: { days } }
    );
    const data = response.data.advisor || response.data || {};
    return {
      executiveSummary: data.executiveSummary ?? 'No summary available.',
      keyConcerns: data.keyConcerns ?? [],
      servicesRequiringAttention: (data.servicesRequiringAttention ?? []).map((s: any) => typeof s === 'string' ? s : `${s.serviceName}: ${s.reason}`),
      recommendedActions: (data.recommendedActions ?? []).map((a: any) => typeof a === 'string' ? a : `[${a.priority}] ${a.action}`),
      positiveSignals: data.positiveSignals ?? [],
      generatedBy: 'RootTrace AI',
      generatedAt: data.generatedAt ?? new Date().toISOString(),
    };
  },

  getEvents: async (limit: number = 50, days: number = 30): Promise<ReliabilityEvent[]> => {
    const response = await apiClient.get<any>('/api/command-center/events', {
      params: { limit, days },
    });
    const events = response.data.events || [];
    return events.map((e: any) => ({
      id: e.resourceId ?? Math.random().toString(),
      eventType: e.type ?? 'UNKNOWN',
      serviceName: e.serviceName ?? 'Unknown',
      title: e.summary ?? 'Event',
      description: e.summary ?? '',
      severity: e.severity ?? 'INFO',
      referenceId: e.resourceId ?? '',
      occurredAt: e.timestamp ?? new Date().toISOString(),
    }));
  },
};
