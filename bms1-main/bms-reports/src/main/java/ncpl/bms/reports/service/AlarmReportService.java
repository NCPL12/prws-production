package ncpl.bms.reports.service;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import ncpl.bms.reports.model.dto.AlarmRecordDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AlarmReportService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<AlarmRecordDTO> fetchAlarmLogs(long startMillis, long endMillis) {
        String sql = "SELECT " +
                "r.[timestamp], " +
                "r.[ackState], " +
                "r.[alarmClass], " +
                "r.[normalTime], " +
                "r.[ackTime], " +
                "s.[source], " +
                "c.[timeOfLastAlarm] " +   // ✅ added from OrionAlarmClass23
                "FROM [JCIHistorianDB].[dbo].[OrionAlarmRecord] r " +
                "LEFT JOIN [JCIHistorianDB].[dbo].[OrionAlarmSourceOrder] o ON r.[id] = o.[id] " +
                "LEFT JOIN [JCIHistorianDB].[dbo].[OrionAlarmSource] s ON o.[alarmSource] = s.[id] " +
                "LEFT JOIN [JCIHistorianDB].[dbo].[OrionAlarmClass23] c ON r.[alarmClass] = c.[id] " +  // ✅ join without any time filter
                "WHERE r.[timestamp] BETWEEN ? AND ? " +   // ✅ only r.timestamp is checked
                "ORDER BY r.[timestamp] ASC";

        return jdbcTemplate.query(sql, new Object[]{startMillis, endMillis}, (ResultSet rs, int rowNum) -> {
            AlarmRecordDTO dto = new AlarmRecordDTO();
            dto.setTimestamp(rs.getLong("timestamp"));
            dto.setSource(extractSourceName(rs.getString("source")));
            dto.setTimeOfLastAlarm(rs.getLong("timeOfLastAlarm"));
            dto.setAckState(rs.getLong("ackState"));
            dto.setAlarmClass(rs.getLong("alarmClass"));
            dto.setNormalTime(rs.getLong("normalTime"));
            dto.setAckTime(rs.getLong("ackTime"));
            dto.setMessageText("HUMIDITY NORMAL");
            return dto;
        });
    }

    private String extractSourceName(String fullSource) {
        if (fullSource == null) return "";
        Pattern pattern = Pattern.compile("(AHU_[^/]+|TFA_[^/]+|MT001)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(fullSource);
        return matcher.find() ? matcher.group() : fullSource;
    }

    private String formatEpoch(Long millis) {
        try {
            return new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date(millis));
        } catch (Exception e) {
            return String.valueOf(millis);
        }
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(8f);
        cell.setMinimumHeight(30f);
        cell.setBorder(Rectangle.BOX);
        return cell;
    }

    private PdfPCell createLeftAlignedCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(8f);
        cell.setMinimumHeight(30f);
        cell.setBorder(Rectangle.BOX);
        return cell;
    }

    private PdfPCell createRightAlignedCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(8f);
        cell.setMinimumHeight(30f);
        cell.setBorder(Rectangle.BOX);
        return cell;
    }

    private String getAlarmClassLabel(Long alarmClass) {
        if (alarmClass == null) return "Unknown";
        switch (alarmClass.intValue()) {
            case 0: return "Normal";
            case 1: return "Critical";
            case 2: return "Default";
            default: return "Unknown";
        }
    }
    private String getAckClassLabel(Long ackClass) {
        if (ackClass == null) return "Unknown";
        switch (ackClass.intValue()) {
            case 0: return "Ack";
            case 1: return "Unacked";
            default: return "Unknown";
        }
    }

    private PdfPTable createAlarmTable(Font headerFont) throws DocumentException {
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{5f, 5f, 5f, 3f, 3f, 3f,5f});
        table.setHeaderRows(1);

        String[] headers = {"Timestamp", "Source Name", "Ack State", "Message text", "Alarm Class", "Normal Time","Alarm Time"};
        for (String h : headers) {
            PdfPCell header = new PdfPCell(new Phrase(h, headerFont));
            header.setBackgroundColor(Color.LIGHT_GRAY);
            header.setHorizontalAlignment(Element.ALIGN_CENTER);
            header.setVerticalAlignment(Element.ALIGN_MIDDLE);
            header.setPadding(5f);
            table.addCell(header);
        }
        return table;
    }

    public byte[] getStoredAlarmReportById(int reportId) {
        try {
            String sql = "SELECT report_data FROM StoredAlarmReport WHERE id = ?";

            return jdbcTemplate.queryForObject(sql, new Object[]{reportId}, byte[].class);
        } catch (Exception e) {
            log.error("Error fetching stored alarm report from database", e);
            return null;
        }
    }// Save generated Alarm PDF bytes into Database
    public void saveAlarmReportToDatabase(byte[] pdfBytes) {
        try {
            String sql = "INSERT INTO StoredAlarmReport (report_name, generated_on, report_data) VALUES (?, ?, ?)";

            String reportName = "Alarm_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Date now = new Date();

            jdbcTemplate.update(sql, reportName, new java.sql.Timestamp(now.getTime()), pdfBytes);

            log.info("Alarm report saved successfully into StoredAlarmReport table.");
        } catch (Exception e) {
            log.error("Error saving alarm report to database", e);
        }
    }



    public byte[] generateAlarmReportPdf(long startMillis, long endMillis) {
        List<AlarmRecordDTO> logs = fetchAlarmLogs(startMillis, endMillis);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 110, 60);
            PdfWriter writer = PdfWriter.getInstance(document, out);

            writer.setPageEvent(new PdfPageEventHelper() {
                Font titleFontBold = new Font(Font.HELVETICA, 18, Font.BOLD);
                Font titleFont = new Font(Font.HELVETICA, 12);
                Font footerFont = new Font(Font.HELVETICA, 9);
                Image logo;

                {
                    try {
                        logo = Image.getInstance(new ClassPathResource("static/images/logo.png").getFile().getAbsolutePath());
                        logo.scaleToFit(90, 40);
                    } catch (IOException e) {
                        log.error("Logo load error", e);
                        logo = null;
                    }
                }

                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    try {
                        PdfContentByte cb = writer.getDirectContent();

                        // Header table with logo, title, and empty right cell
                        PdfPTable headerTable = new PdfPTable(3);
                        headerTable.setWidths(new float[]{2f, 6f, 2f});
                        headerTable.setTotalWidth(document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin());
                        headerTable.setLockedWidth(true);

                        PdfPCell logoCell = new PdfPCell();
                        logoCell.setBorder(Rectangle.NO_BORDER);
                        if (logo != null) {
                            logoCell.addElement(logo);
                        }
                        headerTable.addCell(logoCell);

                        PdfPCell titleCell = new PdfPCell(new Phrase("Alarm Report", titleFontBold));
                        titleCell.setBorder(Rectangle.NO_BORDER);
                        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                        headerTable.addCell(titleCell);

                        PdfPCell emptyCell = new PdfPCell(new Phrase(""));
                        emptyCell.setBorder(Rectangle.NO_BORDER);
                        headerTable.addCell(emptyCell);

                        headerTable.writeSelectedRows(0, -1, document.leftMargin(), document.getPageSize().getHeight() - 20, cb);

                        // Metadata table (Start/End Date & Time)
                        PdfPTable metaTable = new PdfPTable(2);
                        metaTable.setWidths(new float[]{1, 1});
                        metaTable.setTotalWidth(document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin());
                        metaTable.setLockedWidth(true);

                        Font infoFont = new Font(Font.HELVETICA, 9);
                        String startDate = formatEpoch(startMillis);
                        String endDate = formatEpoch(endMillis);

                        PdfPCell startCell = createLeftAlignedCell(
                                "Start Date: " + startDate.split(" ")[0] + "\nStart Time: " + startDate.split(" ")[1], infoFont);
                        PdfPCell endCell = createRightAlignedCell(
                                "End Date: " + endDate.split(" ")[0] + "\nEnd Time: " + endDate.split(" ")[1], infoFont);

                        startCell.setBorder(Rectangle.NO_BORDER);
                        endCell.setBorder(Rectangle.NO_BORDER);

                        metaTable.addCell(startCell);
                        metaTable.addCell(endCell);

                        metaTable.writeSelectedRows(0, -1, document.leftMargin(), document.getPageSize().getHeight() - 60, cb);

                        // Footer
                        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                                new Phrase("Generated on: " + new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date()), footerFont),
                                document.leftMargin(), 30, 0);

                        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                                new Phrase("Page " + writer.getPageNumber(), footerFont),
                                (document.right() + document.left()) / 2, 30, 0);

                    } catch (Exception e) {
                        log.error("Error in header/footer rendering", e);
                    }
                }

            });

            document.open();
            Font infoFont = new Font(Font.HELVETICA, 9);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font cellFont = new Font(Font.HELVETICA, 9);

            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{1, 1});
//            PdfPCell startCell = createLeftAlignedCell("Start Date: " + formatEpoch(startMillis).split(" ")[0] + "\nStart Time: " + formatEpoch(startMillis).split(" ")[1], infoFont);
//            PdfPCell endCell = createRightAlignedCell("End Date: " + formatEpoch(endMillis).split(" ")[0] + "\nEnd Time: " + formatEpoch(endMillis).split(" ")[1], infoFont);
//            startCell.setBorder(Rectangle.NO_BORDER);
//            endCell.setBorder(Rectangle.NO_BORDER);
//            metaTable.addCell(startCell);
//            metaTable.addCell(endCell);
            metaTable.setSpacingAfter(10);
            document.add(metaTable);

            PdfPTable table = createAlarmTable(headerFont);

            for (AlarmRecordDTO log : logs) {
                table.addCell(createCell(formatEpoch(log.getTimestamp()), cellFont));
                table.addCell(createCell(log.getSource(), cellFont));
//                table.addCell(createCell(String.valueOf(log.getSourceState()), cellFont));
                table.addCell(createCell(getAckClassLabel(log.getAckState()), cellFont));
                table.addCell(createCell("HUMIDITY NORMAL", cellFont));
                table.addCell(createCell(getAlarmClassLabel(log.getAlarmClass()), cellFont));
                table.addCell(createCell(formatEpoch(log.getNormalTime()), cellFont));
                table.addCell(createCell(formatEpoch(log.getTimeOfLastAlarm()), cellFont));
            }

            document.add(table);

            PdfPTable footerTable = new PdfPTable(3);
            footerTable.setWidthPercentage(100);
            footerTable.setSpacingBefore(20);

            document.add(footerTable);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("PDF generation failed", e);
            return null;
        }
    }
}
