import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AlarmReportComponent } from './alarm-report.component';

describe('AlarmReportComponent', () => {
  let component: AlarmReportComponent;
  let fixture: ComponentFixture<AlarmReportComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AlarmReportComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AlarmReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
