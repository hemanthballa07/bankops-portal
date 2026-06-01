import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { AgentAdminService } from '../../services/agent-admin.service';
import { Agent } from '../../models/agent.model';

@Component({
  selector: 'app-admin-agents',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule, MatSlideToggleModule],
  templateUrl: './admin-agents.component.html',
  styleUrl: './admin-agents.component.scss',
})
export class AdminAgentsComponent implements OnInit {
  agents: Agent[] = [];
  loading = false;
  error: string | null = null;

  showForm = false;
  draft = { name: '', email: '', maxActiveCases: 10 };
  saving = false;
  formError: string | null = null;

  constructor(private agentService: AgentAdminService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.agentService.list().subscribe({
      next: (a) => { this.agents = a; this.loading = false; },
      error: () => { this.error = 'Failed to load agents'; this.loading = false; },
    });
  }

  toggleActive(agent: Agent): void {
    this.agentService.setActive(agent.id, !agent.active).subscribe({
      next: (updated) => { agent.active = updated.active; },
      error: () => { this.error = 'Failed to update agent'; },
    });
  }

  createAgent(): void {
    if (!this.draft.name.trim() || !this.draft.email.trim()) {
      this.formError = 'Name and email are required';
      return;
    }
    this.saving = true;
    this.formError = null;
    this.agentService.create({
      name: this.draft.name.trim(),
      email: this.draft.email.trim(),
      maxActiveCases: this.draft.maxActiveCases,
    }).subscribe({
      next: () => {
        this.saving = false;
        this.showForm = false;
        this.draft = { name: '', email: '', maxActiveCases: 10 };
        this.load();
      },
      error: (e) => {
        this.saving = false;
        this.formError = e?.error?.message || 'Could not create agent (duplicate email?)';
      },
    });
  }

  loadPct(agent: Agent): number {
    return agent.maxActiveCases > 0
      ? Math.min(100, Math.round((agent.currentActiveCount / agent.maxActiveCases) * 100))
      : 0;
  }
}
