package ncpl.bms.reports.service;

import ncpl.bms.reports.model.dto.StoredAlarmReportDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.List;

@Service
public class StoredAlarmReportService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Fetch all stored alarm reports
    public List<StoredAlarmReportDTO> getAllStoredAlarmReports() {
        String sql = "SELECT id, report_name, generated_on FROM StoredAlarmReport ORDER BY generated_on DESC";

        return jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            StoredAlarmReportDTO dto = new StoredAlarmReportDTO();
            dto.setId(rs.getInt("id"));
            dto.setReportName(rs.getString("report_name"));
            dto.setGeneratedOn(rs.getTimestamp("generated_on"));
            return dto;
        });
    }
}
