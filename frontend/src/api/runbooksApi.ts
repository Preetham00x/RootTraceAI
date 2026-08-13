import { apiClient } from './client';
import type { RunbookExecution } from '../types';

export const runbooksApi = {
  getExecutions: async (incidentId: string): Promise<RunbookExecution[]> => {
    const response = await apiClient.get<RunbookExecution[]>(
      `/api/incidents/${incidentId}/runbooks/executions`
    );
    return response.data;
  },

  requestExecution: async (
    incidentId: string,
    stepId: string,
    command?: string
  ): Promise<RunbookExecution> => {
    const response = await apiClient.post<RunbookExecution>(
      `/api/incidents/${incidentId}/runbooks/${stepId}/execute`,
      { command }
    );
    return response.data;
  },

  approveExecution: async (
    incidentId: string,
    stepId: string,
    executionId?: string
  ): Promise<RunbookExecution> => {
    const response = await apiClient.post<RunbookExecution>(
      `/api/incidents/${incidentId}/runbooks/${stepId}/approve`,
      null,
      { params: { executionId } }
    );
    return response.data;
  },
};
