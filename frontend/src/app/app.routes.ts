import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./components/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'dashboard', canActivate: [authGuard],
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent),
  },
  {
    path: 'fraud-review', canActivate: [authGuard],
    loadComponent: () => import('./components/fraud-review/fraud-review.component').then(m => m.FraudReviewComponent),
  },
  {
    path: 'reports', canActivate: [authGuard],
    loadComponent: () => import('./components/reports/reports.component').then(m => m.ReportsComponent),
  },
  {
    path: 'admin', canActivate: [authGuard],
    loadComponent: () => import('./components/admin-agents/admin-agents.component').then(m => m.AdminAgentsComponent),
  },
  {
    path: 'admin/sla-config', canActivate: [authGuard],
    loadComponent: () => import('./components/admin-sla-config/admin-sla-config.component').then(m => m.AdminSlaConfigComponent),
  },
  {
    path: 'admin/ml-risk-bands', canActivate: [authGuard],
    loadComponent: () => import('./components/admin-ml-risk-bands/admin-ml-risk-bands.component').then(m => m.AdminMlRiskBandsComponent),
  },
  {
    path: 'customers', canActivate: [authGuard],
    loadComponent: () => import('./components/customers/customers.component').then(m => m.CustomersComponent),
  },
  {
    path: 'customers/:id', canActivate: [authGuard],
    loadComponent: () => import('./components/customer-detail/customer-detail.component').then(m => m.CustomerDetailComponent),
  },
  {
    path: 'accounts/:id', canActivate: [authGuard],
    loadComponent: () => import('./components/account-detail/account-detail.component').then(m => m.AccountDetailComponent),
  },
  {
    path: 'cases', canActivate: [authGuard],
    loadComponent: () => import('./components/cases/cases.component').then(m => m.CasesComponent),
  },
  {
    path: 'incidents', canActivate: [authGuard],
    loadComponent: () => import('./components/incident-console/incident-console.component').then(m => m.IncidentConsoleComponent),
  },
  {
    path: 'incidents/:correlationId', canActivate: [authGuard],
    loadComponent: () => import('./components/incident-console/incident-console.component').then(m => m.IncidentConsoleComponent),
  },
  {
    path: 'audit/:entityType/:entityId', canActivate: [authGuard],
    loadComponent: () => import('./components/audit-timeline/audit-timeline.component').then(m => m.AuditTimelineComponent),
  },
];
