import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { AdminAgentsComponent } from './admin-agents.component';
import { AgentAdminService } from '../../services/agent-admin.service';
import { Agent } from '../../models/agent.model';

describe('AdminAgentsComponent', () => {
  let component: AdminAgentsComponent;
  let fixture: ComponentFixture<AdminAgentsComponent>;
  let service: jasmine.SpyObj<AgentAdminService>;

  const agent: Agent = {
    id: 1, name: 'Ada', email: 'ada@bank.test', active: true,
    maxActiveCases: 10, currentActiveCount: 4, skills: [],
  };

  beforeEach(async () => {
    service = jasmine.createSpyObj('AgentAdminService', ['list', 'create', 'setActive']);
    service.list.and.returnValue(of([agent]));
    service.create.and.returnValue(of(agent));
    service.setActive.and.returnValue(of({ ...agent, active: false }));

    await TestBed.configureTestingModule({
      imports: [AdminAgentsComponent],
      providers: [provideNoopAnimations(), { provide: AgentAdminService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminAgentsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads agents on init', () => {
    fixture.detectChanges(); // triggers ngOnInit
    expect(service.list).toHaveBeenCalled();
    expect(component.agents.length).toBe(1);
    expect(component.loading).toBeFalse();
  });

  it('sets an error when the load fails', () => {
    service.list.and.returnValue(throwError(() => new Error('x')));
    fixture.detectChanges();
    expect(component.error).toBe('Failed to load agents');
  });

  it('toggleActive negates active and updates the agent from the response', () => {
    fixture.detectChanges();
    const a = component.agents[0];
    component.toggleActive(a);
    expect(service.setActive).toHaveBeenCalledWith(1, false);
    expect(a.active).toBeFalse();
  });

  it('createAgent rejects empty name/email without calling the service', () => {
    component.draft = { name: '', email: '', maxActiveCases: 10 };
    component.createAgent();
    expect(component.formError).toBe('Name and email are required');
    expect(service.create).not.toHaveBeenCalled();
  });

  it('createAgent posts a trimmed draft and reloads the list', () => {
    fixture.detectChanges();
    service.list.calls.reset();
    component.draft = { name: 'Grace', email: 'grace@bank.test', maxActiveCases: 8 };
    component.createAgent();
    expect(service.create).toHaveBeenCalledWith({
      name: 'Grace', email: 'grace@bank.test', maxActiveCases: 8,
    });
    expect(component.showForm).toBeFalse();
    expect(service.list).toHaveBeenCalled();
  });

  it('loadPct computes and clamps the load percentage', () => {
    expect(component.loadPct({ ...agent, currentActiveCount: 4, maxActiveCases: 10 })).toBe(40);
    expect(component.loadPct({ ...agent, currentActiveCount: 20, maxActiveCases: 10 })).toBe(100);
    expect(component.loadPct({ ...agent, currentActiveCount: 1, maxActiveCases: 0 })).toBe(0);
  });
});
