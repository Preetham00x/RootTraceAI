import { apiClient } from './client';
import type { KubernetesPod } from '../types';

export const integrationsApi = {
  getKubernetesPods: async (
    namespace: string = 'default',
    service: string = 'payment-service'
  ): Promise<KubernetesPod[]> => {
    const response = await apiClient.get<KubernetesPod[]>('/api/integrations/kubernetes/pods', {
      params: { namespace, service },
    });
    return response.data;
  },

  getAiHealth: async (): Promise<{
    status: string;
    provider: string;
    chatConfigured: boolean;
    embeddingConfigured: boolean;
  }> => {
    const response = await apiClient.get<{
      status: string;
      provider: string;
      chatConfigured: boolean;
      embeddingConfigured: boolean;
    }>('/api/ai/health');
    return response.data;
  },

  getActuatorHealth: async (): Promise<{ status: string }> => {
    const response = await apiClient.get<{ status: string }>('/actuator/health');
    return response.data;
  },
};
