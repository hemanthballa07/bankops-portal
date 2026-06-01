export interface Agent {
  id: number;
  name: string;
  email: string;
  active: boolean;
  maxActiveCases: number;
  currentActiveCount: number;
  skills: string[];
}

export interface CreateAgentRequest {
  name: string;
  email: string;
  maxActiveCases: number;
  skills?: string[];
}

export interface UpdateAgentRequest {
  name: string;
  maxActiveCases: number;
  active: boolean;
  skills?: string[];
}
