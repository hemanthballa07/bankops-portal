import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IncidentConsoleComponent } from './incident-console.component';
import { IncidentService } from '../../services/incident.service';
import { of, throwError } from 'rxjs';
import { IncidentResponse } from '../../models/incident.model';

describe('IncidentConsoleComponent', () => {
  let component: IncidentConsoleComponent;
  let fixture: ComponentFixture<IncidentConsoleComponent>;
  let incidentService: jasmine.SpyObj<IncidentService>;

  const mockIncidentResponse: IncidentResponse = {
    correlationId: 'test-correlation-id-123',
    transaction: {
      id: 1,
      accountId: 1,
      type: 'DEPOSIT',
      amount: 100,
      status: 'COMPLETED',
      correlationId: 'test-correlation-id-123',
      createdAt: '2025-01-15T10:30:00Z'
    },
    case_: {
      id: 1,
      customerId: 1,
      status: 'INVESTIGATING',
      severity: 'HIGH',
      summary: 'Test case',
      createdAt: '2025-01-15T10:30:00Z',
      updatedAt: '2025-01-15T10:30:00Z'
    },
    logEvents: [
      {
        id: 1,
        correlationId: 'test-correlation-id-123',
        level: 'INFO',
        message: 'Transaction created',
        contextJson: '{"accountId": 1, "amount": 100}',
        createdAt: '2025-01-15T10:30:00Z'
      },
      {
        id: 2,
        correlationId: 'test-correlation-id-123',
        level: 'INFO',
        message: 'Balance updated',
        contextJson: '{"previousBalance": 0, "newBalance": 100}',
        createdAt: '2025-01-15T10:30:01Z'
      }
    ]
  };

  beforeEach(async () => {
    const incidentServiceSpy = jasmine.createSpyObj('IncidentService', ['getIncidentByCorrelationId']);

    await TestBed.configureTestingModule({
      imports: [IncidentConsoleComponent],
      providers: [
        { provide: IncidentService, useValue: incidentServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(IncidentConsoleComponent);
    component = fixture.componentInstance;
    incidentService = TestBed.inject(IncidentService) as jasmine.SpyObj<IncidentService>;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render timeline from mocked API response', () => {
    incidentService.getIncidentByCorrelationId.and.returnValue(of(mockIncidentResponse));
    
    component.correlationId = 'test-correlation-id-123';
    component.searchIncident();
    fixture.detectChanges();

    expect(component.incident).toBeDefined();
    expect(component.incident?.logEvents.length).toBe(2);
    
    const logItems = fixture.nativeElement.querySelectorAll('.log-item');
    expect(logItems.length).toBe(2);
  });

  it('should display transaction details', () => {
    incidentService.getIncidentByCorrelationId.and.returnValue(of(mockIncidentResponse));
    
    component.correlationId = 'test-correlation-id-123';
    component.searchIncident();
    fixture.detectChanges();

    const transactionSection = fixture.nativeElement.querySelector('.transaction-section');
    expect(transactionSection).toBeTruthy();
    expect(transactionSection.textContent).toContain('DEPOSIT');
    expect(transactionSection.textContent).toContain('$100.00');
  });

  it('should display case details when available', () => {
    incidentService.getIncidentByCorrelationId.and.returnValue(of(mockIncidentResponse));
    
    component.correlationId = 'test-correlation-id-123';
    component.searchIncident();
    fixture.detectChanges();

    const caseSection = fixture.nativeElement.querySelector('.case-section');
    expect(caseSection).toBeTruthy();
    expect(caseSection.textContent).toContain('INVESTIGATING');
  });

  it('should handle error when incident not found', () => {
    incidentService.getIncidentByCorrelationId.and.returnValue(
      throwError(() => ({ error: { message: 'Incident not found' } }))
    );
    
    component.correlationId = 'invalid-id';
    component.searchIncident();
    fixture.detectChanges();

    expect(component.error).toBe('Incident not found');
    expect(component.incident).toBeUndefined();
  });

  it('should apply correct CSS class based on log level', () => {
    expect(component.getLogLevelClass('ERROR')).toBe('log-error');
    expect(component.getLogLevelClass('WARN')).toBe('log-warn');
    expect(component.getLogLevelClass('INFO')).toBe('log-info');
    expect(component.getLogLevelClass('DEBUG')).toBe('log-debug');
  });
});

