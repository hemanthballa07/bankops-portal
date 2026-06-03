import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { CaseService } from '../../services/case.service';
import { TransactionService } from '../../services/transaction.service';
import { CaseKpi, SupportCase } from '../../models/case.model';
import { Transaction } from '../../models/transaction.model';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';
import { AmountPipe } from '../../shared/pipes/amount.pipe';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    TimeAgoPipe,
    AmountPipe,
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  kpis: CaseKpi | null = null;
  heldTransactions: Transaction[] = [];
  recentCases: SupportCase[] = [];
  loading = true;

  constructor(
    private caseService: CaseService,
    private transactionService: TransactionService,
  ) {}

  ngOnInit(): void {
    forkJoin({
      kpis: this.caseService.getKpis().pipe(catchError(() => of(null))),
      held: this.transactionService.getHeldTransactions().pipe(catchError(() => of([]))),
      cases: this.caseService.getCases('OPEN').pipe(catchError(() => of([]))),
    }).subscribe(({ kpis, held, cases }) => {
      this.kpis = kpis;
      this.heldTransactions = (held as Transaction[]).slice(0, 5);
      this.recentCases = (cases as SupportCase[]).slice(0, 5);
      this.loading = false;
    });
  }

  get heldCount(): number {
    return this.heldTransactions.length;
  }
}
