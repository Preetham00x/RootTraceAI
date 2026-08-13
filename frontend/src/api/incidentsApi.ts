import { apiClient } from './client';
import type {
  DiagnosisDetail,
  DiagnosisSummary,
  Incident,
  IncidentSeverity,
  IncidentStatus,
  IncidentSummary,
  InvestigationPlan,
  PageResponse,
  RelatedIncidentItem,
} from '../types';

export interface CreateIncidentPayload {
  title: string;
  description: string;
  service: string;
  severity: IncidentSeverity;
  environment: string;
}

export interface UpdateIncidentPayload {
  title?: string;
  description?: string;
  severity?: IncidentSeverity;
  status?: IncidentStatus;
  environment?: string;
  resolution?: string;
}

export const incidentsApi = {
  listIncidents: async (params?: {
    page?: number;
    size?: number;
    status?: IncidentStatus;
    severity?: IncidentSeverity;
    service?: string;
    environment?: string;
    search?: string;
  }): Promise<PageResponse<IncidentSummary>> => {
    const response = await apiClient.get<any>('/api/incidents', { params });
    const data = response.data;
    if (data && Array.isArray(data.content)) {
      data.content = data.content.map((inc: any) => ({
        ...inc,
        createdBy: typeof inc.createdBy === 'object' && inc.createdBy !== null
          ? (inc.createdBy.name || `${inc.createdBy.firstName || ''} ${inc.createdBy.lastName || ''}`.trim() || inc.createdBy.email || 'System')
          : String(inc.createdBy || 'System'),
      }));
    }
    return data;
  },

  getIncident: async (id: string): Promise<Incident> => {
    const response = await apiClient.get<Incident>(`/api/incidents/${id}`);
    return response.data;
  },

  createIncident: async (payload: CreateIncidentPayload): Promise<Incident> => {
    const response = await apiClient.post<Incident>('/api/incidents', payload);
    return response.data;
  },

  updateIncident: async (id: string, payload: UpdateIncidentPayload): Promise<Incident> => {
    const response = await apiClient.put<Incident>(`/api/incidents/${id}`, payload);
    return response.data;
  },

  resolveIncident: async (id: string, resolution: string): Promise<Incident> => {
    const response = await apiClient.patch<Incident>(`/api/incidents/${id}/resolve`, { resolution });
    return response.data;
  },

  // AI Diagnosis
  diagnoseIncident: async (incidentId: string): Promise<DiagnosisDetail> => {
    const response = await apiClient.post<DiagnosisDetail>(`/api/incidents/${incidentId}/diagnose`);
    return response.data;
  },

  getDiagnoses: async (incidentId: string): Promise<DiagnosisSummary[]> => {
    const response = await apiClient.get<DiagnosisSummary[]>(`/api/incidents/${incidentId}/diagnoses`);
    return response.data;
  },

  getDiagnosisDetail: async (incidentId: string, diagnosisId: string): Promise<DiagnosisDetail> => {
    const response = await apiClient.get<DiagnosisDetail>(`/api/incidents/${incidentId}/diagnoses/${diagnosisId}`);
    return response.data;
  },

  submitDiagnosisFeedback: async (
    incidentId: string,
    diagnosisId: string,
    rating: number,
    feedbackNotes?: string
  ): Promise<void> => {
    await apiClient.post(`/api/incidents/${incidentId}/diagnoses/${diagnosisId}/feedback`, {
      rating,
      feedbackNotes,
    });
  },

  // Investigation Plans
  generateInvestigationPlan: async (
    incidentId: string,
    diagnosisId?: string,
    focusAreas?: string[]
  ): Promise<InvestigationPlan> => {
    const response = await apiClient.post<InvestigationPlan>(
      `/api/incidents/${incidentId}/investigation-plans/generate`,
      { diagnosisId, focusAreas }
    );
    return response.data;
  },

  getInvestigationPlans: async (incidentId: string): Promise<InvestigationPlan[]> => {
    const response = await apiClient.get<InvestigationPlan[]>(
      `/api/incidents/${incidentId}/investigation-plans`
    );
    return response.data;
  },

  updateInvestigationStep: async (
    incidentId: string,
    planId: string,
    stepId: string,
    payload: { status?: string; evidence?: string; assignedTo?: string }
  ): Promise<void> => {
    await apiClient.patch(
      `/api/incidents/${incidentId}/investigation-plans/${planId}/steps/${stepId}`,
      payload
    );
  },

  // Related Incidents
  getRelatedIncidents: async (
    incidentId: string,
    limit: number = 5,
    threshold: number = 0.6
  ): Promise<{ relatedIncidents: RelatedIncidentItem[] }> => {
    const response = await apiClient.get<{ relatedIncidents: RelatedIncidentItem[] }>(
      `/api/incidents/${incidentId}/related`,
      { params: { limit, threshold } }
    );
    return response.data;
  },
};
