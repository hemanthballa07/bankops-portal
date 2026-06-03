import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatCheckboxChange } from '@angular/material/checkbox';
import { of, throwError } from 'rxjs';

import { CasesComponent } from './cases.component';
import { CaseService } from '../../services/case.service';
import { CustomerService } from '../../services/customer.service';
import { MlRiskBandService } from '../../services/ml-risk-band.service';
import { CaseTimelineComponent } from '../case-timeline/case-timeline.component';
import { SupportCase } from '../../models/case.model';
import { Customer } from '../../models/customer.model';

describe('CasesComponent', () => {
  let component: CasesComponent;
  let fixture: ComponentFixture<CasesComponent>;
  let caseService: jasmine.SpyObj<CaseService>;
  let customerService: jasmine.SpyObj<CustomerService>;
  let bandService: jasmine.SpyObj<MlRiskBandService>;

  const supportCase = (over: Partial<SupportCase> = {}): SupportCase => ({
    id: 1,
    customerId: 1,
    status: 'OPEN',
    severity: 'MEDIUM',
    summary: 'Suspicious activity',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...over,
  });

  const customer: Customer = {
    id: 1,
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@bank.test',
    phone: '555-0100',
    createdAt: new Date().toISOString(),
  };

  beforeEach(async () => {
    caseService = jasmine.createSpyObj('CaseService', [
      'getCases',
      'createCase',
      'updateCaseStatus',
      'assignCase',
      'addCaseNote',
      'resolveCase',
    ]);
    customerService = jasmine.createSpyObj('CustomerService', ['searchCustomers']);

    caseService.getCases.and.returnValue(of([supportCase()]));
    caseService.createCase.and.returnValue(of(supportCase()));
    caseService.updateCaseStatus.and.returnValue(of(supportCase({ status: 'IN_PROGRESS' })));
    caseService.assignCase.and.returnValue(of(supportCase({ assignedTo: 'admin' })));
    caseService.addCaseNote.and.returnValue(
      of({ id: 7, author: 'admin', content: 'note', createdAt: new Date().toISOString() }),
    );
    caseService.resolveCase.and.returnValue(of(supportCase({ status: 'RESOLVED' })));
    customerService.searchCustomers.and.returnValue(of([customer]));
    bandService = jasmine.createSpyObj('MlRiskBandService', ['getBands', 'update']);
    bandService.getBands.and.returnValue(of({ medThreshold: 0.4, highThreshold: 0.7 } as any));

    await TestBed.configureTestingModule({
      imports: [CasesComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: CaseService, useValue: caseService },
        { provide: CustomerService, useValue: customerService },
        { provide: MlRiskBandService, useValue: bandService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CasesComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('mlBandClass/mlBandLabel/mlPercent compute the ML risk chip from configured bands', () => {
    expect(component.mlBandClass(0.2)).toBe('low');
    expect(component.mlBandClass(0.5)).toBe('med');
    expect(component.mlBandClass(0.85)).toBe('high');
    expect(component.mlBandLabel(0.85)).toBe('High');
    expect(component.mlPercent(0.82)).toBe('82%');
  });

  describe('initialization', () => {
    it('loads customers and cases on init', () => {
      component.ngOnInit();
      expect(customerService.searchCustomers).toHaveBeenCalled();
      expect(caseService.getCases).toHaveBeenCalled();
      expect(component.customers.length).toBe(1);
      expect(component.dataSource.data.length).toBe(1);
    });

    it('logs and recovers when customer load fails', () => {
      const errSpy = spyOn(console, 'error');
      customerService.searchCustomers.and.returnValue(throwError(() => new Error('x')));
      component.loadCustomers();
      expect(errSpy).toHaveBeenCalled();
      expect(component.customers.length).toBe(0);
    });

    it('logs when case load fails', () => {
      const errSpy = spyOn(console, 'error');
      caseService.getCases.and.returnValue(throwError(() => new Error('x')));
      component.loadCases();
      expect(errSpy).toHaveBeenCalled();
    });
  });

  describe('enhanceCase', () => {
    beforeEach(() => {
      component.customers = [customer];
    });

    it('maps severity to an SLA target', () => {
      expect(component.enhanceCase(supportCase({ severity: 'HIGH' })).slaRemaining).toBe(24);
      expect(component.enhanceCase(supportCase({ severity: 'MEDIUM' })).slaRemaining).toBe(48);
      expect(component.enhanceCase(supportCase({ severity: 'LOW' })).slaRemaining).toBe(72);
    });

    it('maps severity to a numeric priority', () => {
      expect(component.enhanceCase(supportCase({ severity: 'CRITICAL' })).priority).toBe(0);
      expect(component.enhanceCase(supportCase({ severity: 'HIGH' })).priority).toBe(1);
      expect(component.enhanceCase(supportCase({ severity: 'MEDIUM' })).priority).toBe(2);
      expect(component.enhanceCase(supportCase({ severity: 'LOW' })).priority).toBe(3);
    });

    it('resolves the customer name, falling back to the id', () => {
      expect(component.enhanceCase(supportCase({ customerId: 1 })).customerName).toBe('Ada Lovelace');
      expect(component.enhanceCase(supportCase({ customerId: 99 })).customerName).toBe('ID: 99');
    });
  });

  describe('updateKPIs', () => {
    it('aggregates open, unassigned, at-risk, and high-severity counts', () => {
      component.updateKPIs([
        { ...supportCase({ id: 1, status: 'OPEN', severity: 'HIGH' }), slaRemaining: 2 },
        { ...supportCase({ id: 2, status: 'OPEN', severity: 'LOW', assignedTo: 'admin' }), slaRemaining: 30 },
        { ...supportCase({ id: 3, status: 'RESOLVED', severity: 'CRITICAL' }), slaRemaining: 1 },
      ]);
      expect(component.queueKpis.openCases).toBe(2);
      expect(component.queueKpis.unassigned).toBe(2); // ids 1 and 3 have no assignee
      expect(component.queueKpis.slaAtRisk).toBe(1); // only id 1 (<4h and not RESOLVED)
      expect(component.queueKpis.highSeverity).toBe(2); // HIGH + CRITICAL
    });
  });

  describe('bulk selection', () => {
    beforeEach(() => {
      component.dataSource.data = [
        { ...supportCase({ id: 1 }), selected: false },
        { ...supportCase({ id: 2 }), selected: false },
      ];
    });

    it('toggleAll selects and deselects every row', () => {
      component.toggleAll({ checked: true } as MatCheckboxChange);
      expect(component.dataSource.data.every((c) => c.selected)).toBeTrue();
      expect(component.isAllSelected()).toBeTrue();
      expect(component.getSelectedCount()).toBe(2);

      component.toggleAll({ checked: false } as MatCheckboxChange);
      expect(component.isAllSelected()).toBeFalse();
      expect(component.getSelectedCount()).toBe(0);
    });
  });

  describe('case actions', () => {
    it('onCreateCase posts, resets the form, and reloads on success', () => {
      component.newCase = { customerId: 1, summary: 'New fraud case', severity: 'HIGH' };
      caseService.getCases.calls.reset();
      component.onCreateCase();
      expect(caseService.createCase).toHaveBeenCalledWith({ customerId: 1, summary: 'New fraud case', severity: 'HIGH' });
      expect(component.showCreateForm).toBeFalse();
      expect(component.newCase).toEqual({ customerId: 0, summary: '', severity: 'MEDIUM' });
      expect(caseService.getCases).toHaveBeenCalled();
    });

    it('onCreateCase alerts on failure', () => {
      const alertSpy = spyOn(window, 'alert');
      caseService.createCase.and.returnValue(throwError(() => new Error('fail')));
      component.onCreateCase();
      expect(alertSpy).toHaveBeenCalledWith('Failed to create case');
    });

    it('updateStatus patches the status and reloads', () => {
      caseService.getCases.calls.reset();
      component.updateStatus(5, 'CLOSED');
      expect(caseService.updateCaseStatus).toHaveBeenCalledWith(5, { status: 'CLOSED' });
      expect(caseService.getCases).toHaveBeenCalled();
    });

    it('assignToMe assigns to admin and reloads', () => {
      caseService.getCases.calls.reset();
      component.assignToMe(5);
      expect(caseService.assignCase).toHaveBeenCalledWith(5, { assignedTo: 'admin' });
      expect(caseService.getCases).toHaveBeenCalled();
    });

    it('openTimeline opens the timeline dialog with the case id', () => {
      // CasesComponent imports MatDialogModule, so its own injector resolves the
      // real MatDialog. Spy on that instance rather than overriding the provider
      // (which would be shadowed) so the dialog never actually renders.
      const dialog = (component as unknown as { dialog: MatDialog }).dialog;
      const openSpy = spyOn(dialog, 'open').and.returnValue({} as ReturnType<MatDialog['open']>);
      component.openTimeline(42);
      expect(openSpy).toHaveBeenCalled();
      const [comp, config] = openSpy.calls.mostRecent().args as [unknown, { data: { caseId: number } }];
      expect(comp).toBe(CaseTimelineComponent);
      expect(config.data.caseId).toBe(42);
    });
  });

  describe('case details drawer', () => {
    it('selectCase sets the selection and clears the inputs', () => {
      component.newNote = 'old';
      component.resolution = 'old';
      const c = { ...supportCase({ id: 9 }) };
      component.selectCase(c);
      expect(component.selectedCase).toBe(c);
      expect(component.newNote).toBe('');
      expect(component.resolution).toBe('');
    });

    it('closeDetails clears the selection', () => {
      component.selectedCase = { ...supportCase() };
      component.closeDetails();
      expect(component.selectedCase).toBeNull();
    });

    it('toggleCreateForm flips the flag', () => {
      expect(component.showCreateForm).toBeFalse();
      component.toggleCreateForm();
      expect(component.showCreateForm).toBeTrue();
    });
  });

  describe('addNote', () => {
    it('does nothing without a selected case', () => {
      component.selectedCase = null;
      component.newNote = 'hello';
      component.addNote();
      expect(caseService.addCaseNote).not.toHaveBeenCalled();
    });

    it('does nothing for a blank note', () => {
      component.selectedCase = { ...supportCase() };
      component.newNote = '   ';
      component.addNote();
      expect(caseService.addCaseNote).not.toHaveBeenCalled();
    });

    it('posts the note and optimistically prepends it', () => {
      component.selectedCase = { ...supportCase({ id: 3, notes: [] }) };
      component.newNote = 'Need more info';
      component.addNote();
      expect(caseService.addCaseNote).toHaveBeenCalledWith(3, { content: 'Need more info' });
      // Optimistic prepend with the placeholder id 999, authored by admin.
      expect(component.selectedCase!.notes![0].id).toBe(999);
      expect(component.selectedCase!.notes![0].author).toBe('admin');
    });
  });

  describe('resolveCase', () => {
    it('does nothing without a selected case', () => {
      component.selectedCase = null;
      component.resolution = 'done';
      component.resolveCase();
      expect(caseService.resolveCase).not.toHaveBeenCalled();
    });

    it('does nothing for a blank resolution', () => {
      component.selectedCase = { ...supportCase() };
      component.resolution = '   ';
      component.resolveCase();
      expect(caseService.resolveCase).not.toHaveBeenCalled();
    });

    it('resolves, clears the selection, and reloads', () => {
      caseService.getCases.calls.reset();
      component.selectedCase = { ...supportCase({ id: 4 }) };
      component.resolution = 'Confirmed legitimate';
      component.resolveCase();
      expect(caseService.resolveCase).toHaveBeenCalledWith(4, { resolution: 'Confirmed legitimate' });
      expect(component.selectedCase).toBeNull();
      expect(component.resolution).toBe('');
      expect(caseService.getCases).toHaveBeenCalled();
    });
  });
});
