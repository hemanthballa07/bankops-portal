export interface ReportSummary {
  transactionsByStatus: Record<string, number>;
  casesByState: Record<string, number>;
  casesBySeverity: Record<string, number>;
  caseKpis: {
    openCases: number;
    unassignedCases: number;
    slaAtRiskCases: number;
    highSeverityCases: number;
    unassignedHighSeverity: number;
  };
  totalTransactions: number;
  totalCases: number;
}
