import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'fraud-review',
    loadComponent: () => import('./components/fraud-review/fraud-review.component').then(m => m.FraudReviewComponent)
  },
  {
    path: 'customers',
    loadComponent: () => import('./components/customers/customers.component').then(m => m.CustomersComponent)
  },
  {
    path: 'customers/:id',
    loadComponent: () => import('./components/customer-detail/customer-detail.component').then(m => m.CustomerDetailComponent)
  },
  {
    path: 'accounts/:id',
    loadComponent: () => import('./components/account-detail/account-detail.component').then(m => m.AccountDetailComponent)
  },
  {
    path: 'cases',
    loadComponent: () => import('./components/cases/cases.component').then(m => m.CasesComponent)
  },
  {
    path: 'incidents',
    loadComponent: () => import('./components/incident-console/incident-console.component').then(m => m.IncidentConsoleComponent)
  },
  {
    path: 'incidents/:correlationId',
    loadComponent: () => import('./components/incident-console/incident-console.component').then(m => m.IncidentConsoleComponent)
  },
  {
    path: 'audit/:entityType/:entityId',
    loadComponent: () => import('./components/audit-timeline/audit-timeline.component').then(m => m.AuditTimelineComponent)
  }
];
