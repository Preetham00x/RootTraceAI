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
    const response = await apiClient.get<SloEvaluation[]>(`/api/services/${serviceName}/slos/evaluations`);
    return response.data;
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
