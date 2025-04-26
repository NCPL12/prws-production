import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SyngeneAuditComponent } from './syngene-audit.component';

describe('SyngeneAuditComponent', () => {
  let component: SyngeneAuditComponent;
  let fixture: ComponentFixture<SyngeneAuditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SyngeneAuditComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(SyngeneAuditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
