export type NotificationCategory = 'FRAUD_HOLD' | 'CASE_UNASSIGNED' | 'SLA_RISK' | 'BACKLOG';
export type NotificationSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export interface Notification {
  id: string;
  category: NotificationCategory;
  severity: NotificationSeverity;
  title: string;
  detail: string;
  entityType: string;
  entityId: number | null;
  link: string;
  timestamp: string | null;
}

export interface NotificationCounts {
  critical: number;
  warning: number;
  info: number;
  total: number;
}

export interface NotificationsSummary {
  items: Notification[];
  counts: NotificationCounts;
}

export const EMPTY_SUMMARY: NotificationsSummary = {
  items: [],
  counts: { critical: 0, warning: 0, info: 0, total: 0 },
};
