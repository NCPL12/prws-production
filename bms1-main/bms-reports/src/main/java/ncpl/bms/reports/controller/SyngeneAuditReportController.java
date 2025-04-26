package ncpl.bms.reports.controller;

import ncpl.bms.reports.service.SyngeneAuditReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/audit-report")
@CrossOrigin(origins = "http://localhost:4200")
public class SyngeneAuditReportController {

    @Autowired
    private SyngeneAuditReportService auditService;

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadAuditReport(@RequestParam String startDate,
                                                      @RequestParam String endDate) {
        byte[] pdf = auditService.generateAuditReportPdf(startDate, endDate);

        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.noContent().build();
        }

        // ✅ Save automatically after generating
        auditService.saveAuditReportPdf(pdf);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=syngene_audit_report.pdf")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .body(pdf);
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<byte[]> viewStoredAuditReport(@PathVariable int id) {
        byte[] pdfBytes = auditService.getStoredAuditReportById(id);

        if (pdfBytes == null || pdfBytes.length == 0) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=stored_audit_report_" + id + ".pdf")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .body(pdfBytes);
    }
}
