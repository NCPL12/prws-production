import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-syngene-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './syngene-audit.component.html',
  styleUrl: './syngene-audit.component.css'
})
export class SyngeneAuditComponent {
  fromDate: string = '';
  toDate: string = '';
  errorMessage: string = '';
  storedReports: any[] = []; // 👈 new array for table
  private apiBaseUrl = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadStoredReports(); // 👈 load on page load
  }

  downloadAuditReport() {
    this.errorMessage = '';

    if (!this.isValidDateRange()) {
      this.errorMessage = 'Please select both From Date and To Date.';
      return;
    }

    if (!this.isToDateAfterFromDate()) {
      this.errorMessage = 'To Date must be after From Date.';
      return;
    }

    const formattedFrom = this.formatDateForSQL(this.fromDate);
    const formattedTo = this.formatDateForSQL(this.toDate);
    const downloadUrl = `${this.apiBaseUrl}/audit-report/download?startDate=${encodeURIComponent(formattedFrom)}&endDate=${encodeURIComponent(formattedTo)}`;
    window.open(downloadUrl, '_blank');
  }

  formatDateForSQL(date: string): string {
    return date.replace('T', ' ') + ':00';
  }

  isValidDateRange(): boolean {
    return this.fromDate !== '' && this.toDate !== '';
  }

  isToDateAfterFromDate(): boolean {
    return new Date(this.toDate) > new Date(this.fromDate);
  }

  validateYear(dateString: string, field: 'from' | 'to') {
    const date = new Date(dateString);
    const year = date.getFullYear();

    if (year < 1000 || year > 9999) {
      alert(`${field === 'from' ? 'From Date' : 'To Date'} must have a 4-digit year.`);
      if (field === 'from') this.fromDate = '';
      if (field === 'to') this.toDate = '';
    }
  }

  // 👇 New: Load Stored Reports for table
  loadStoredReports() {
    this.http.get<any[]>(`${this.apiBaseUrl}/stored-audit-report/list`)
      .subscribe(
        (data) => {
          this.storedReports = data;
        },
        (error) => {
          console.error('Failed to fetch stored audit reports', error);
        }
      );
  }

  // 👇 New: View Report by ID
  viewReport(id: number) {
    const viewUrl = `${this.apiBaseUrl}/audit-report/view/${id}`;
    window.open(viewUrl, '_blank');
  }
  
}
