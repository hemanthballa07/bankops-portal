export interface SlaConfig {
  priority: 'P1' | 'P2' | 'P3';
  durationSeconds: number;
  updatedAt: string | null;
}
