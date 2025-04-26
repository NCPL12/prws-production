package ncpl.bms.reports.model.dto;

import lombok.Data;

import java.util.Date;

@Data
public class StoredAlarmReportDTO {
    private int id;
    private String reportName;
    private Date generatedOn;
}
