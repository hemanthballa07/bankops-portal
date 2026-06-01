import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { IncidentConsoleComponent } from './incident-console.component';
import { IncidentService } from '../../services/incident.service';
import { IncidentResponse, IncidentSummary } from '../../models/incident.model';

describe('IncidentConsoleComponent', () => {
  let component: IncidentConsoleComponent;
  let fixture: ComponentFixture<IncidentConsoleComponent>;
  let incidentService: jasmine.SpyObj<IncidentService>;

  // Minimal valid IncidentResponse — transaction/case_ are optional and omitted
  // so the mock stays decoupled from the Transaction/SupportCase model shapes.
  const mockIncidentResponse: IncidentResponse = {
    correlationId: 'test-correlation-id-123',
    logEvents: [
      { id: 1, correlationId: 'test-correlation-id-123', level: 'INFO', message: 'Transaction created', contextJson: '{"accountId":1}', createdAt: '2025-01-15T10:30:00Z' },
      { id: 2, correlationId: 'test-correlation-id-123', level: 'INFO', message: 'Balance updated', createdAt: '2025-01-15T10:30:01Z' },
    ],
  };

  const mockSummary: IncidentSummary = {
    id: 'incident-1',
    timestamp: '2025-01-15T10:30:00Z',
    service: 'transaction-service',
    endpoint: '/api/accounts/*/transactions',
    status: 200,
    latency: 120,
    correlationId: 'test-correlation-id-123',
    actor: 'user',
    severity: 'SEV4',
    statusText: 'COMPLETED',
  };

  beforeEach(async () => {
    const incidentServiceSpy = jasmine.createSpyObj<IncidentService>('IncidentService', ['getIncidentByCorrelationId']);

    await TestBed.configureTestingModule({
      imports: [IncidentConsoleComponent],
      providers: [
        provideNoopAnimations(), // MatSidenav registers @transform animation host listeners
        { provide: IncidentService, useValue: incidentServiceSpy },
        // Component injects ActivatedRoute in its constructor; createComponent
        // needs a provider. ngOnInit only reads snapshot.paramMap.get(...).
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(IncidentConsoleComponent);
    component = fixture.componentInstance;
    incidentService = TestBed.inject(IncidentService) as jasmine.SpyObj<IncidentService>;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('searchIncidents() maps an incident response into the results table', () => {
    incidentService.getIncidentByCorrelationId.and.returnValue(of(mockIncidentResponse));

    component.filters.correlationId = 'test-correlation-id-123';
    component.searchIncidents();

    expect(incidentService.getIncidentByCorrelationId).toHaveBeenCalledWith('test-correlation-id-123');
    expect(component.incidents.length).toBe(1);
    expect(component.incidents[0].correlationId).toBe('test-correlation-id-123');
    expect(component.totalElements).toBe(1);
    expect(component.loading).toBeFalse();
  });

  it('searchIncidents() sets error when the incident is not found', () => {
    incidentService.getIncidentByCorrelationId.and.returnValue(
      throwError(() => ({ error: { message: 'Incident not found' } }))
    );

    component.filters.correlationId = 'invalid-id';
    component.searchIncidents();

    expect(component.error).toBe('Incident not found');
    expect(component.loading).toBeFalse();
  });

  it('viewIncidentDetail() opens the drawer and loads the detail', () => {
    incidentService.getIncidentByCorrelationId.and.returnValue(of(mockIncidentResponse));

    component.viewIncidentDetail(mockSummary);

    expect(component.drawerOpen).toBeTrue();
    expect(component.selectedIncident).toBe(mockSummary);
    expect(component.incidentDetail).toEqual(mockIncidentResponse);
  });

  it('getLogLevelClass maps log levels to css classes', () => {
    expect(component.getLogLevelClass('ERROR')).toBe('log-error');
    expect(component.getLogLevelClass('WARN')).toBe('log-warn');
    expect(component.getLogLevelClass('INFO')).toBe('log-info');
    expect(component.getLogLevelClass('DEBUG')).toBe('log-debug');
  });

  it('getStatusClass maps HTTP status codes to css classes', () => {
    expect(component.getStatusClass(200)).toBe('status-success');
    expect(component.getStatusClass(404)).toBe('status-warning');
    expect(component.getStatusClass(500)).toBe('status-error');
  });
});
