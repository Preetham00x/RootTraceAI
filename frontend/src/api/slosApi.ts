import { apiClient } from './client';
import type {
  ReliabilityDashboard,
  ServiceSlo,
  SloEvaluation,
} from '../types';

export interface CreateSloPayload {
  name: string;
  description?: string;
  targetPercentage: number;
  sliType: string;
  windowDays: number;
  warningThresholdPercentage?: number;
  metricQuery: string;
}

export const slosApi = {
  getServiceSlos: async (serviceName: string): Promise<ServiceSlo[]> => {
    const response = await apiClient.get<ServiceSlo[]>(`/api/services/${serviceName}/slos`);
    return response.data;
  },

  createSlo: async (serviceName: string, payload: CreateSloPayload): Promise<ServiceSlo> => {
    const response = await apiClient.post<ServiceSlo>(`/api/services/${serviceName}/slos`, payload);
    return response.data;
  },

  getSloEvaluations: async (serviceName: string): Promise<SloEvaluation[]> => {
    const response = await apiClient.get<ReliabilityDashboard>(`/api/services/${serviceName}/reliability`);
    const slos = response.data.slos || [];
    return slos.map((s: any) => ({
      sloId: s.sloId ?? '',
      sloName: s.sloName ?? '',
      serviceName: s.serviceName ?? serviceName,
      targetPercentage: s.targetPercentage ?? 99.9,
      compliancePercentage: s.compliancePercentage ?? s.actualPercentage ?? 100,
      status: s.status ?? 'HEALTHY',
      errorBudgetTotalPercentage: s.errorBudgetTotalPercentage ?? 100,
      errorBudgetRemainingPercentage: s.errorBudgetRemainingPercentage ?? 100,
      budgetConsumedPercentage: s.budgetConsumedPercentage ?? 0,
      burnRate1h: s.burnRate1h ?? 0,
      burnRate6h: s.burnRate6h ?? 0,
      burnRate24h: s.burnRate24h ?? 0,
      burnRate3d: s.burnRate3d ?? 0,
      evaluatedAt: s.evaluatedAt ?? new Date().toISOString(),
    }));
  },

  getReliabilityDashboard: async (serviceName: string): Promise<ReliabilityDashboard> => {
    const response = await apiClient.get<ReliabilityDashboard>(`/api/services/${serviceName}/reliability`);
    return response.data;
  },

  recordSliMeasurement: async (
    serviceName: string,
    sloId: string,
    goodEvents: number,
    totalEvents: number
  ): Promise<void> => {
    await apiClient.post(`/api/services/${serviceName}/slos/${sloId}/measurements`, {
      goodEvents,
      totalEvents,
    });
  },
};
