// RootTraceAI TypeScript Type Definitions matching backend DTOs

export type UserRole = 'ADMIN' | 'ENGINEER' | 'VIEWER';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  createdAt?: string;
  updatedAt?: string;
}

export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'MITIGATED' | 'RESOLVED' | 'CLOSED';
export type IncidentSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type RiskTier = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type SloStatus = 'HEALTHY' | 'WARNING' | 'BREACHED';
export type RunbookStatus = 'REQUESTED' | 'APPROVED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type PostmortemStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED';
export type ActionItemPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type ActionItemStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

// Command Center DTOs
export interface ReliabilityPenalty {
  category: string;
  deduction: number;
  reason: string;
}

export interface ReliabilityScore {
  score: number;
  riskTier: RiskTier;
  targetScore: number;
  trend: 'IMPROVING' | 'STABLE' | 'DEGRADING';
  penalties: ReliabilityPenalty[];
  evaluatedAt: string;
}

export interface CommandCenterOverview {
  reliabilityScore: ReliabilityScore;
  totalServices: number;
  servicesAtRisk: number;
  totalIncidents30d: number;
  activeIncidents: number;
  criticalIncidents30d: number;
  highIncidents30d: number;
  meanTimeToResolveMinutes: number;
  meanTimeToDetectMinutes: number;
  totalSlos: number;
  healthySlos: number;
  warningSlos: number;
  breachedSlos: number;
  averageErrorBudgetConsumedPercentage: number;
  openPostmortemActionItems: number;
  overduePostmortemActionItems: number;
  failedRunbookExecutions30d: number;
  calculatedAt: string;
}

export interface ServiceHealthSummary {
  serviceName: string;
  healthScore: number;
  riskTier: RiskTier;
  environment: string;
  totalIncidents30d: number;
  activeIncidents: number;
  criticalIncidents30d: number;
  meanTimeToResolveMinutes: number;
  totalSlos: number;
  breachedSlos: number;
  averageErrorBudgetConsumedPercentage: number;
  openActionItems: number;
  overdueActionItems: number;
  recentRunbookFailures: number;
  lastIncidentAt: string | null;
}

export interface ReliabilityRecommendation {
  serviceName: string;
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  category: string;
  title: string;
  description: string;
  actionableStep: string;
}

export interface ServiceHealthDetail {
  serviceName: string;
  healthScore: number;
  riskTier: RiskTier;
  environment: string;
  riskFactors: string[];
  totalIncidents30d: number;
  activeIncidents: number;
  criticalIncidents30d: number;
  highIncidents30d: number;
  meanTimeToResolveMinutes: number;
  meanTimeToDetectMinutes: number;
  recurrenceRatePercentage: number;
  slos: SloSummaryItem[];
  averageErrorBudgetConsumedPercentage: number;
  highestBurnRate: number;
  incidentTrends: Record<string, number>;
  reliabilityTrends: Record<string, number>;
  commonRootCauses: Record<string, number>;
  openActionItems: ActionItemSummary[];
  recentRunbooks: RunbookSummaryItem[];
  recommendations: ReliabilityRecommendation[];
}

export interface SloSummaryItem {
  sloId: string;
  name: string;
  targetPercentage: number;
  currentCompliancePercentage: number;
  status: SloStatus;
  budgetConsumedPercentage: number;
  burnRate1h: number;
}

export interface ActionItemSummary {
  id: string;
  title: string;
  priority: ActionItemPriority;
  status: ActionItemStatus;
  assignedTo: string | null;
  dueDate: string | null;
  overdue: boolean;
}

export interface RunbookSummaryItem {
  id: string;
  command: string;
  status: RunbookStatus;
  executedAt: string;
}

export interface ActiveIncidentItem {
  id: string;
  title: string;
  service: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  environment: string;
  createdAt: string;
  ageMinutes: number;
  sloBreached: boolean;
  burnRateTier: string;
  serviceRiskTier: RiskTier;
  priorityScore: number;
  recommendedAttention: 'IMMEDIATE' | 'URGENT' | 'NORMAL';
}

export interface ReliabilityEvent {
  id: string;
  eventType: 'INCIDENT_CREATED' | 'INCIDENT_RESOLVED' | 'SLO_BREACH' | 'SLO_WARNING' | 'RUNBOOK_FAILED' | 'POSTMORTEM_PUBLISHED' | 'ACTION_ITEM_OVERDUE';
  serviceName: string;
  title: string;
  description: string;
  severity: string;
  referenceId: string;
  occurredAt: string;
}

export interface ExecutiveReliabilityAdvisor {
  executiveSummary: string;
  keyConcerns: string[];
  servicesRequiringAttention: string[];
  recommendedActions: string[];
  positiveSignals: string[];
  generatedBy: string;
  generatedAt: string;
}

// Incident Domain DTOs
export interface Incident {
  id: string;
  title: string;
  description: string;
  service: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  environment: string;
  createdBy: { id: string; name: string };
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  resolution: string | null;
}

export interface IncidentSummary {
  id: string;
  title: string;
  service: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  environment: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// AI Diagnosis DTOs
export interface DiagnosisSummary {
  id: string;
  probableRootCause: string;
  confidenceScore: number;
  feedbackRating: number | null;
  createdAt: string;
}

export interface DiagnosisDetail {
  id: string;
  probableRootCause: string;
  confidenceScore: number;
  reasoning: string;
  recommendedMitigation: string;
  contributingFactors: string[];
  evidenceCitations: string[];
  feedbackRating: number | null;
  feedbackNotes: string | null;
  createdAt: string;
}

// Investigation Plan DTOs
export interface InvestigationStep {
  id: string;
  stepOrder: number;
  title: string;
  description: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
  evidence: string | null;
  assignedTo: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InvestigationPlan {
  id: string;
  incidentId: string;
  sourceDiagnosisId: string | null;
  title: string;
  createdBy: { id: string; name: string };
  steps: InvestigationStep[];
  createdAt: string;
  updatedAt: string;
}

// Postmortem DTOs
export interface PostmortemActionItem {
  id: string;
  postmortemId: string;
  title: string;
  description: string;
  category: string;
  priority: ActionItemPriority;
  status: ActionItemStatus;
  assignedTo: string | null;
  dueDate: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Postmortem {
  id: string;
  incidentId: string;
  title: string;
  summary: string;
  impactSummary: string;
  rootCauseAnalysis: string;
  resolutionSummary: string;
  timeline: string;
  lessonsLearned: string;
  status: PostmortemStatus;
  downtimeMinutes: number;
  createdBy: { id: string; name: string };
  publishedAt: string | null;
  actionItems: PostmortemActionItem[];
  createdAt: string;
  updatedAt: string;
}

// Incident Command 360 DTO
export interface IncidentCommand {
  incident: Incident;
  latestDiagnosis: DiagnosisDetail | null;
  investigationPlan: InvestigationPlan | null;
  relatedIncidents: RelatedIncidentItem[];
  duplicateCandidate: RelatedIncidentItem | null;
  postmortem: Postmortem | null;
  sloImpact: IncidentSloImpact[];
  runbookExecutions: RunbookExecution[];
  jiraTickets: JiraTicket[];
  timeline: IncidentTimelineEvent[];
  recommendations: string[];
}

export interface RelatedIncidentItem {
  incidentId: string;
  title: string;
  service: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  similarityScore: number;
  sharedRootCause: string;
  duplicateConfidence: number;
  resolutionSummary: string;
  createdAt: string;
}

export interface IncidentSloImpact {
  sloId: string;
  sloName: string;
  targetPercentage: number;
  currentCompliance: number;
  status: SloStatus;
  budgetConsumedPercentage: number;
  burnRateMultiplier: number;
}

export interface RunbookExecution {
  id: string;
  incidentId: string;
  investigationStepId: string | null;
  command: string;
  executionStatus: RunbookStatus;
  requestedBy: { id: string; name: string };
  approvedBy: { id: string; name: string } | null;
  output: string | null;
  errorOutput: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface JiraTicket {
  id: string;
  incidentId: string;
  actionItemId: string | null;
  provider: string;
  externalTicketId: string;
  externalUrl: string;
  status: string;
}

export interface IncidentTimelineEvent {
  eventType: string;
  description: string;
  occurredAt: string;
}

// SLO DTOs
export interface ServiceSlo {
  id: string;
  serviceName: string;
  name: string;
  description: string;
  targetPercentage: number;
  sliType: string;
  windowDays: number;
  warningThresholdPercentage: number;
  metricQuery: string;
  createdAt: string;
  updatedAt: string;
}

export interface SloEvaluation {
  sloId: string;
  sloName: string;
  serviceName: string;
  targetPercentage: number;
  compliancePercentage: number;
  status: SloStatus;
  errorBudgetTotalPercentage: number;
  errorBudgetRemainingPercentage: number;
  budgetConsumedPercentage: number;
  burnRate1h: number;
  burnRate6h: number;
  burnRate24h: number;
  burnRate3d: number;
  evaluatedAt: string;
}

export interface ReliabilityDashboard {
  serviceName: string;
  overallCompliance: number;
  slos: SloEvaluation[];
  riskTier: RiskTier;
  riskScore: number;
  activeIncidents: number;
  burnRateAlert: boolean;
}

// Intelligence DTOs
export interface SreMetricsSummary {
  periodDays: number;
  totalIncidents: number;
  resolvedIncidents: number;
  meanTimeToResolveMinutes: number;
  meanTimeToDetectMinutes: number;
  recurrenceRatePercentage: number;
  severityCounts: Record<string, number>;
  calculatedAt: string;
}

export interface IncidentCluster {
  clusterId: string;
  service: string;
  patternDescription: string;
  incidentCount: number;
  sampleIncidentTitles: string[];
  sharedSymptoms: string[];
  suggestedAction: string;
}

export interface IncidentBriefing {
  incidentId: string;
  executiveSummary: string;
  recurringIssueDetected: boolean;
  historicalRootCauses: string[];
  provenInvestigationSteps: string[];
  postmortemLessonsLearned: string[];
  uncompletedActionItems: string[];
  recommendedTriageActions: string[];
  generatedAt: string;
}

// Integration DTOs
export interface KubernetesPod {
  name: string;
  namespace: string;
  phase: string;
  ready: boolean;
  restarts: number;
  nodeName: string;
  age: string;
}
