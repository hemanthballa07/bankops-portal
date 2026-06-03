import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MlRiskBandService } from '../../services/ml-risk-band.service';

@Component({
  selector: 'app-admin-ml-risk-bands',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './admin-ml-risk-bands.component.html',
  styleUrl: './admin-ml-risk-bands.component.scss',
})
export class AdminMlRiskBandsComponent implements OnInit {
  med = 0.4;
  high = 0.7;
  updatedAt: string | null = null;
  loading = false;
  saving = false;
  error: string | null = null;

  constructor(private service: MlRiskBandService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.service.getBands().subscribe({
      next: (b) => {
        this.med = b.medThreshold;
        this.high = b.highThreshold;
        this.updatedAt = b.updatedAt ?? null;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load ML risk bands';
        this.loading = false;
      },
    });
  }

  save(): void {
    if (!(this.med > 0 && this.high < 1 && this.med < this.high)) {
      this.error = 'Require 0 < Med < High < 1';
      return;
    }
    this.error = null;
    this.saving = true;
    this.service.update(this.med, this.high).subscribe({
      next: (b) => {
        this.med = b.medThreshold;
        this.high = b.highThreshold;
        this.updatedAt = b.updatedAt ?? null;
        this.saving = false;
      },
      error: () => {
        this.error = 'Failed to save ML risk bands';
        this.saving = false;
      },
    });
  }
}
