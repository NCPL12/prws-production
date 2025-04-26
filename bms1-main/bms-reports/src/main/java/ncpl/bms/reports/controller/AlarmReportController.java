package ncpl.bms.reports.controller;

import ncpl.bms.reports.service.AlarmReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/v1/alarm-report")
@CrossOrigin(origins = "http://localhost:4200")
public class AlarmReportController {

    @Autowired
    private AlarmReportService alarmService;

    // ✅ 1. Download Alarm Report → Save into Database automatically
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadAndSaveAlarmReport(@RequestParam String startDate,
                                                             @RequestParam String endDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long startMillis = sdf.parse(startDate).getTime();
            long endMillis = sdf.parse(endDate).getTime();

            // Generate the PDF
            byte[] pdf = alarmService.generateAlarmReportPdf(startMillis, endMillis);

            // Save the generated PDF into DB
            if (pdf != null && pdf.length > 0) {
                alarmService.saveAlarmReportToDatabase(pdf);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=alarm_report.pdf")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                    .body(pdf);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }

    // ✅ 2. View Stored Alarm Report by ID
    @GetMapping("/view/{id}")
    public ResponseEntity<byte[]> viewStoredAlarmReport(@PathVariable int id) {
        try {
            byte[] pdfBytes = alarmService.getStoredAlarmReportById(id);

            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=stored_alarm_report_" + id + ".pdf")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                    .body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }
}
