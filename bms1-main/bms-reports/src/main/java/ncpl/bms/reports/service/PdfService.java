    package ncpl.bms.reports.service;
    import java.text.SimpleDateFormat;
    import java.util.*;
    import java.time.LocalDate;
    import java.time.format.DateTimeFormatter;
    import com.lowagie.text.Paragraph;
    import com.lowagie.text.*;
    import com.lowagie.text.pdf.*;
    import lombok.extern.slf4j.Slf4j;
    import ncpl.bms.reports.model.dao.ReportTemplate;
    import ncpl.bms.reports.model.dto.GroupDTO;
    import ncpl.bms.reports.model.dto.ReportDTO;
    import ncpl.bms.reports.util.DateConverter;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.core.io.ClassPathResource;
    import org.springframework.stereotype.Component;
    import java.io.IOException;
    import java.text.SimpleDateFormat;
    import java.util.Date;
    import com.lowagie.text.Document;
    import java.util.regex.*;
    import com.lowagie.text.Element;
    import com.lowagie.text.PageSize;
    import com.lowagie.text.Phrase;
    import com.lowagie.text.pdf.PdfPCell;
    import com.lowagie.text.pdf.PdfPTable;
    import com.lowagie.text.pdf.PdfWriter;
    import java.io.ByteArrayOutputStream;
    import java.sql.PreparedStatement;
    import org.springframework.jdbc.core.JdbcTemplate;
    import java.time.LocalDateTime;
    import java.io.ByteArrayInputStream;
    import java.util.List;

    @Component
    @Slf4j
    public class PdfService {

        @Autowired
        private ReportDataService reportDataService;

        @Autowired
        private ReportTemplateService templateService;

        @Value("${report.address}")
        private String address;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Value("${report.heading}")
        private String reportHeading;

        @Autowired
        private DateConverter dateConverter;

        private List<Map<String, Object>> reportDataList = null;
        public String getSubArea(Long templateId) {
            String sql = "SELECT report_group FROM report_template WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, new Object[]{templateId}, String.class);
        }

        private String convertMillisToDate(Long millis) {
            if (millis == null) {
                return "N/A"; // Return a default value for null timestamps
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            return sdf.format(new Date(millis));
        }

        public String getReportName(Long templateId) {
            String sql = "SELECT name FROM report_template WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, new Object[]{templateId}, String.class);
        }

        public String getDynamicReportHeading(Long templateId) {
            String sql = "SELECT ph.HeaderName " +
                    "FROM report_template rt " +
                    "JOIN ParameterHeaders ph ON rt.parameters LIKE CONCAT('%', ph.SetPointName, '%') " +
                    "WHERE rt.id = ?";

            try {
                List<String> headers = jdbcTemplate.queryForList(sql, new Object[]{templateId}, String.class);

                if (headers == null || headers.isEmpty()) {
                    return "Report"; // Fallback if nothing matched
                }

                // Join with 'and' if multiple
                if (headers.size() == 1) {
                    return headers.get(0) + " Report";
                } else {
                    String joined = String.join(", ", headers.subList(0, headers.size() - 1)) +
                            " and " + headers.get(headers.size() - 1);
                    return joined + " Report";
                }

            } catch (Exception e) {
                log.warn("Error generating dynamic report heading, using fallback.", e);
                return "Report";
            }
        }
        public Map<String, Map<String, Map<String, Object>>> calculateStatistics(Long templateId, String fromDate, String toDate) {
            List<Map<String, Object>> data = reportDataService.generateReportData(templateId, fromDate, toDate);

            Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();

            for (String key : data.get(0).keySet()) {
                if (key.equalsIgnoreCase("timestamp")) continue;

                double maxVal = Double.NEGATIVE_INFINITY;
                double minVal = Double.POSITIVE_INFINITY;
                long maxTime = 0L, minTime = 0L;
                double total = 0;
                int count = 0;

                for (Map<String, Object> row : data) {
                    Object valObj = row.get(key);
                    Object timeObj = row.get("timestamp");

                    if (valObj == null || timeObj == null) continue;

                    try {
                        double val = Double.parseDouble(valObj.toString());
                        long time = Long.parseLong(timeObj.toString());

                        if (val > maxVal) {
                            maxVal = val;
                            maxTime = time;
                        }
                        if (val < minVal) {
                            minVal = val;
                            minTime = time;
                        }

                        total += val;
                        count++;
                    } catch (Exception e) {
                        // skip bad data
                    }
                }

                Map<String, Map<String, Object>> statMap = new LinkedHashMap<>();
                Map<String, Object> maxMap = new HashMap<>();
                Map<String, Object> minMap = new HashMap<>();
                Map<String, Object> avgMap = new HashMap<>();

                if (count > 0) {
                    maxMap.put("value", (int) maxVal);
                    maxMap.put("timestamp", maxTime);

                    minMap.put("value", (int) minVal);
                    minMap.put("timestamp", minTime);

                    avgMap.put("value", (int) (total / count));
                }
                statMap.put("max", maxMap);
                statMap.put("min", minMap);
                statMap.put("avg", avgMap);
                result.put(key, statMap);
            }

            return result;
        }

        public void generatePdf(Long templateId, String fromDateTime, String toDate, String username,  String assignedTo, String assigned_approver) throws Exception {
            reportDataList = reportDataService.generateReportData(templateId, fromDateTime, toDate);

            // Convert the date range to 'dd-MM-yyyy HH:mm:ss' format
            SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            String formattedFromDateTime = dateTimeFormatter.format(new Date(Long.parseLong(fromDateTime)));
            String formattedToDateTime = dateTimeFormatter.format(new Date(Long.parseLong(toDate)));

            Document document = new Document(PageSize.A4.rotate());
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, byteArrayOutputStream);

            // Use TablePageEvent if required for header/footer
            TablePageEvent event = new TablePageEvent(formattedFromDateTime, formattedToDateTime, username, templateId, this);
            writer.setPageEvent(event);

            document.open();


            Map<String, Object> stringObjectMap = reportDataList.get(0);
            int columnCount = stringObjectMap.size();
            int rowCount = 0;
            int rowsPerPage = 20;
            Map<String, Map<String, Map<String, Object>>> statistics = calculateStatistics(templateId, fromDateTime, toDate);

            PdfPTable table = new PdfPTable(stringObjectMap.size());
            table.setWidthPercentage(100f);
            table.setSpacingBefore(5);

            PdfPCell cell = new PdfPCell();
            addTableHeader(templateId, stringObjectMap, table);

            System.out.println("Fetching ReportTemplate for templateId: " + templateId);
            // Extract "From" and "To" values for each parameter
            Map<String, String> formattedParameterRanges = extractFormattedParameterRanges(templateId);

            Map<String, double[]> parameterRanges = extractParameterRanges(templateId);
            rowCount = 0; // Reset row counter

            for (Map<String, Object> map : reportDataList) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    Object rawValue = entry.getValue();
                    String valueStr;

                    if (rawValue == null || rawValue.toString().trim().isEmpty() || "null".equalsIgnoreCase(rawValue.toString())) {
                        valueStr = "null"; // display literal 'null'
                    } else {
                        valueStr = rawValue.toString();
                    }


                    // Handle timestamp
                    if (entry.getKey().equalsIgnoreCase("timestamp")) {
                        try {
                            valueStr = convertMillisToDate(Long.parseLong(valueStr));
                        } catch (Exception ignored) {}
                    }

                    PdfPCell valueCell = new PdfPCell(new Phrase(valueStr));
                    valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);

                    // Conditional coloring
                    try {
                        double value = Double.parseDouble(valueStr);
                        double[] range = parameterRanges.get(extractBaseParameter(entry.getKey()));
                        if (range != null) {
                            double from = range[0], to = range[1];
                            if (value > to) valueCell.setBackgroundColor(CMYKColor.RED);
                            else if (value < from) valueCell.setBackgroundColor(CMYKColor.CYAN);
                        }
                    } catch (NumberFormatException ignored) {}

                    table.addCell(valueCell);
                }

                rowCount++;

                // 🔁 Add page break every 20 rows (except last page)
                if (rowCount % rowsPerPage == 0 && rowCount != reportDataList.size()) {
                    document.add(table);  // Add current table to PDF
                    document.newPage();   // Start a new page

                    // Reinitialize table and add header again
                    table = new PdfPTable(columnCount);
                    table.setWidthPercentage(100f);
                    table.setSpacingBefore(5);
                    addTableHeader(templateId, stringObjectMap, table);
                }
            }

            // ✅ Add the main data table
            document.add(table);

    // ✅ Create a new page for the statistics
            document.newPage();

    // ✅ Create and fill the statistics table
            PdfPTable statisticsTable = new PdfPTable(columnCount);
            statisticsTable.setWidthPercentage(100f);
            statisticsTable.setSpacingBefore(10);

    // Optional: Add statistics summary title
    //        Paragraph statsTitle = new Paragraph("Statistics Summary", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
    //        statsTitle.setAlignment(Element.ALIGN_CENTER);
    //        statsTitle.setSpacingAfter(10);
    //        document.add(statsTitle);

    // Add headers + stat rows
            addTableHeader(templateId, stringObjectMap, statisticsTable);
            addStatisticsRow("Max", statistics, statisticsTable);
            addStatisticsRow("Min", statistics, statisticsTable);
            addStatisticsRow("Avg", statistics, statisticsTable);

    // ✅ Add the final stats table to document
            document.add(statisticsTable);

    // ✅ NOW close the document
            document.close();

            String templateName = templateService.getById(templateId).getName().replaceAll("[^a-zA-Z0-9]", "_"); // Replace non-alphanumeric characters with underscores
            String pdfFileName = templateName + "_" + formattedFromDateTime + "_TO_" + formattedToDateTime + ".pdf";
            Date currentDate = new Date(Calendar.getInstance().getTimeInMillis());
            long currentTimeMillis = currentDate.getTime();
            String currentDateStr = Long.toString(currentTimeMillis);

            //int chk = (assigned_approver == null) ? 0 : 1;
            int chk = (assigned_approver == null || assigned_approver.trim().isEmpty()) ? 0 : 1;
            // Insert the PDF into the database using JdbcTemplate
            String sql = "INSERT INTO stored_reports (name, from_date, to_date, pdf_data, generated_by, generated_date, assigned_review, assigned_approver, is_approver_required) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, pdfFileName);  // Set the formatted file name
                ps.setString(2, fromDateTime);
                ps.setString(3, toDate);
                ps.setBytes(4, byteArrayOutputStream.toByteArray());
                ps.setString(5, username);
                ps.setString(6, currentDateStr);
                ps.setString(7, assignedTo);
                ps.setString(8, assigned_approver);
                log.info("APPROVER IS {}", assigned_approver);
                log.info("chk is {} " ,chk);
                ps.setBoolean(9, chk==1);
                return ps;
            });
        }


    //    private String extractBaseParameter(String columnName) {
    //        if (columnName.contains("_From_")) {
    //            return columnName.substring(0, columnName.indexOf("_From_"));
    //        }
    //        return columnName;
    //    }
    //private String extractUnit(String columnName) {
    //    if (columnName.contains("_Unit_")) {
    //        return columnName.substring(columnName.indexOf("_Unit_") + 6); // extract after "_Unit_"
    //    }
    //    return ""; // Return empty if no unit
    //}


    //    private String extractBaseParameter(String columnName) {
    //        String base = columnName;
    //        if (base.contains("_From_")) base = base.substring(0, base.indexOf("_From_"));
    //        if (base.contains("_To_")) base = base.substring(0, base.indexOf("_To_"));
    //        if (base.contains("_Unit_")) base = base.substring(0, base.indexOf("_Unit_"));
    //        return base;
    //    }

        private Map<String, String> extractFormattedParameterRanges(Long templateId) {
            ReportTemplate template = templateService.getById(templateId);
            if (template == null) {
                throw new RuntimeException("ReportTemplate not found for templateId: " + templateId);
            }

            List<String> parameters = template.getParameters();
            if (parameters == null || parameters.isEmpty()) {
                throw new RuntimeException("No parameters found for templateId: " + templateId);
            }

            Map<String, String> formattedParameterRanges = new HashMap<>();

            for (String parameter : parameters) {
                String baseName = extractBaseParameter(parameter);
                if (baseName.startsWith("SYNGENE_")) {
                    baseName = baseName.substring("SYNGENE_".length());
                }
                String unit = extractUnit(parameter);

                String formatted = unit.isEmpty() ? baseName : String.format("%s(%s)", baseName, unit);
                formattedParameterRanges.put(parameter, formatted);
            }
            return formattedParameterRanges;
        }

        private String removeSuffix(String columnName) {
            if (columnName.contains("_From_")) {
                return columnName.substring(0, columnName.indexOf("_From_"));
            }
            return columnName;
        }

        private static final Pattern RANGE_PATTERN = Pattern.compile("_From_(\\d+(?:\\.\\d+)?)_To_(\\d+(?:\\.\\d+)?)");
        private static final Pattern UNIT_PATTERN = Pattern.compile("_Unit_([a-zA-Z]+)$");

        private double getFromValue(String paramName) {
            Matcher matcher = RANGE_PATTERN.matcher(paramName);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
            return Double.NEGATIVE_INFINITY;
        }


        private double getToValue(String paramName) {
            Matcher matcher = RANGE_PATTERN.matcher(paramName);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(2));
            }
            return Double.POSITIVE_INFINITY;
        }

        private String extractUnit(String paramName) {
            Matcher matcher = UNIT_PATTERN.matcher(paramName);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return "";
        }
        private String extractBaseParameter(String columnName) {
            return columnName.replaceAll("(_From_.*|_Unit_.*)$", "");
        }


        private void addStatisticsRow(String label, Map<String, Map<String, Map<String, Object>>> statistics, PdfPTable table) {
            Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            List<String> parameterKeys = new ArrayList<>(statistics.keySet());

            boolean hasTimestamp = !label.equalsIgnoreCase("Avg");

            // Label cell
            PdfPCell labelCell = new PdfPCell(new Phrase(label, fontBold));
            labelCell.setBackgroundColor(CMYKColor.YELLOW);
            labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            if (hasTimestamp) {
                labelCell.setRowspan(2); // Only Max & Min
            }
            table.addCell(labelCell);

            // Value Row
            for (String parameter : parameterKeys) {
                Map<String, Object> statData = statistics.get(parameter).get(label.toLowerCase());
                String valueStr = "null";

                if (statData != null && statData.get("value") != null) {
                    valueStr = statData.get("value").toString();
                }

                PdfPCell valueCell = new PdfPCell(new Phrase(valueStr, fontNormal));
                valueCell.setBackgroundColor(CMYKColor.YELLOW);
                valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(valueCell);
            }

            // Timestamp Row (for Max & Min only)
            if (hasTimestamp) {
                for (String parameter : parameterKeys) {
                    Map<String, Object> statData = statistics.get(parameter).get(label.toLowerCase());
                    String dateStr = "";

                    if (statData != null && statData.get("timestamp") != null) {
                        try {
                            long millis = Long.parseLong(statData.get("timestamp").toString());
                            dateStr = convertMillisToDate(millis);
                        } catch (Exception ignored) {
                            dateStr = "N/A";
                        }
                    }

                    PdfPCell dateCell = new PdfPCell(new Phrase(dateStr, fontNormal));
                    dateCell.setBackgroundColor(CMYKColor.YELLOW);
                    dateCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    dateCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    table.addCell(dateCell);
                }
            }
        }

        private void addTableHeader(Long templateId, Map<String, Object> stringObjectMap, PdfPTable table) {
            Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, CMYKColor.BLACK);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(CMYKColor.GRAY);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);

            ReportTemplate template = templateService.getById(templateId);
            Map<String, String> formattedParameters = extractFormattedParameterRanges(templateId);

            // Timestamp header
            cell.setPhrase(new Phrase("Timestamp", font));
            table.addCell(cell);

            // Parameter headers with range only if explicitly set
            for (String parameter : template.getParameters()) {
                String baseFormattedName = formattedParameters.get(parameter);
                double fromValue = getFromValue(parameter);
                double toValue = getToValue(parameter);

                String headerText = baseFormattedName;

                // Only add range if explicitly provided
                if (fromValue != Double.NEGATIVE_INFINITY && toValue != Double.POSITIVE_INFINITY) {
                    headerText += String.format("\nRange: %.0f - %.0f", fromValue, toValue);
                }

                cell.setPhrase(new Phrase(headerText, font));
                table.addCell(cell);
            }

            table.setHeaderRows(1);
        }

        private class TablePageEvent extends PdfPageEventHelper {

            private final String fromDateTime;
            private final String toDateTime;
            private final String username;
            private final Long templateId;
            private final PdfService pdfService;
            private String reviewedBy = "";
            private String reviewDate = "";


            public TablePageEvent(String fromDateTime, String toDateTime, String username, Long templateId, PdfService pdfService) {
                this.fromDateTime = fromDateTime;
                this.toDateTime = toDateTime;
                this.username = username;
                this.templateId = templateId;
                this.pdfService = pdfService;
                try {
                    ReportDTO latestReport = pdfService.findLatestGeneratedReport(templateId, fromDateTime, toDateTime, username);
                    if (latestReport != null) {
                        this.reviewedBy = latestReport.getReviewedBy();
                        if (latestReport.getReviewDate() != null) {
                            long millis = Long.parseLong(latestReport.getReviewDate());
                            this.reviewDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(millis));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch review info for footer", e);
                }
            }

            @Override
            public void onStartPage(PdfWriter writer, Document document) {
                try {
                    Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
                    Font fontContent = FontFactory.getFont(FontFactory.HELVETICA, 11);

                    PdfPTable headerTable = new PdfPTable(3);
                    headerTable.setWidthPercentage(100);
                    float[] columnWidths = {20f, 60f, 20f};
                    headerTable.setWidths(columnWidths);

                    // Fetch dynamic values
                    String subArea = pdfService.getSubArea(templateId);

                    // Extract parameter ranges dynamically
                    Map<String, String> formattedParameterRanges = extractFormattedParameterRanges(templateId);

                    // Format the extracted parameter ranges as a string
                    //                StringBuilder rangeText = new StringBuilder("\nRanges:");
                    //                for (Map.Entry<String, double[]> entry : parameterRanges.entrySet()) {
                    //                    rangeText.append("[").append((int) entry.getValue()[0]).append(" - ").append((int) entry.getValue()[1]).append("]\n");
                    //
                    //                }


                    // Logo Cell - Left Aligned
                    PdfPCell cell1 = new PdfPCell();
                    try {
                        Image image = Image.getInstance(new ClassPathResource("static/images/logo.png").getURL());
                        image.scaleToFit(100, 70);
                        image.setAlignment(Element.ALIGN_LEFT);
                        cell1.addElement(image);
                    } catch (IOException e) {
                        throw new RuntimeException("Error loading logo image", e);
                    }
                    cell1.setBorder(Rectangle.NO_BORDER);
                    cell1.setPaddingLeft(5);
                    cell1.setVerticalAlignment(Element.ALIGN_MIDDLE);

                    // Address Cell - Center Aligned
                    PdfPCell cell2 = new PdfPCell(new Paragraph(address, fontTitle));
                    cell2.setBorder(Rectangle.NO_BORDER);
                    cell2.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell2.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell2.setPadding(5);

                    PdfPCell cell3 = new PdfPCell(new Paragraph(""));
                    cell3.setBorder(Rectangle.NO_BORDER);

                    String dynamicHeading = pdfService.getDynamicReportHeading(templateId);
                    PdfPCell cell4 = new PdfPCell(new Paragraph(dynamicHeading, fontTitle));

                    cell4.setBorder(Rectangle.NO_BORDER);
                    cell4.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell4.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell4.setColspan(3);
                    cell4.setPaddingTop(5);
                    cell4.setPaddingBottom(10);

                    // Left Section - Start Date, Start Time, Area, Sub Area (Dynamic)
                    PdfPCell cell5 = new PdfPCell();
                    DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
                    DateTimeFormatter desiredDateFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
                    DateTimeFormatter desiredTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

                    LocalDateTime fromDate = LocalDateTime.parse(fromDateTime, inputFormat);
                    String displayStartDate = fromDate.format(desiredDateFormat); // 21-Mar-2025
                    String displayStartTime = fromDate.format(desiredTimeFormat); // 10:30:00

                    Paragraph paragraph5 = new Paragraph(
                            "Start Date: " + displayStartDate +
                                    "\nStart Time: " + displayStartTime +
                                    "\nArea: S20A" +
                                    "\nSub Area: " + subArea, fontContent);

                    paragraph5.setLeading(12f, 0f);
                    cell5.addElement(paragraph5);
                    cell5.setBorder(Rectangle.NO_BORDER);
                    cell5.setHorizontalAlignment(Element.ALIGN_LEFT);
                    cell5.setPaddingLeft(5);
                    cell5.setNoWrap(true);

                    PdfPCell emptyCell = new PdfPCell(new Paragraph(""));
                    emptyCell.setBorder(Rectangle.NO_BORDER);

                    // Right Section - End Date, End Time, and Dynamic Parameter Ranges

                    LocalDateTime toDate = LocalDateTime.parse(toDateTime, inputFormat);
                    String displayEndDate = toDate.format(desiredDateFormat); // e.g. 21-Mar-2025
                    String displayEndTime = toDate.format(desiredTimeFormat); // e.g. 18:45:00

                    PdfPCell cell6 = new PdfPCell(new Paragraph(
                            "End Date: " + displayEndDate +
                                    "\nEnd Time: " + displayEndTime
                    ));

                    cell6.setBorder(Rectangle.NO_BORDER);
                    cell6.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    cell6.setPaddingRight(5);

                    headerTable.addCell(cell1);
                    headerTable.addCell(cell2);
                    headerTable.addCell(cell3);
                    headerTable.addCell(cell4);
                    headerTable.addCell(cell5);
                    headerTable.addCell(emptyCell);
                    headerTable.addCell(cell6);

                    document.add(headerTable);

                } catch (DocumentException e) {
                    throw new RuntimeException("Error creating header", e);
                }
            }

            @Override
            public void onEndPage(PdfWriter writer, Document document) {
                PdfPTable footerTable = new PdfPTable(2);
                footerTable.setWidthPercentage(100);
                try {
                    footerTable.setWidths(new float[]{50f, 50f});
                } catch (DocumentException e) {
                    throw new RuntimeException(e);
                }
                Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
                LocalDateTime currentDate = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-MMMM-yyyy HH:mm:ss");

                String formattedDate = currentDate.format(formatter);

                PdfPCell cell1 = new PdfPCell(new Phrase("Generated By: Operator " + username + "\nDate: " + formattedDate, fontTitle));
                cell1.setBorder(Rectangle.NO_BORDER);
                cell1.setHorizontalAlignment(Element.ALIGN_LEFT);
                cell1.setPaddingLeft(10);

                String reviewInfo = "";
                if (reviewedBy != null && !reviewedBy.isEmpty()) {
                    reviewInfo = "Reviewed By: " + reviewedBy + "\nReview Date: " + reviewDate;
                }
                PdfPCell cell2 = new PdfPCell(new Phrase(reviewInfo, fontTitle));
                cell2.setBorder(Rectangle.NO_BORDER);
                cell2.setHorizontalAlignment(Element.ALIGN_RIGHT);

                footerTable.addCell(cell1);
                footerTable.addCell(cell2);

                PdfPTable pageNumberTable = new PdfPTable(1);
                pageNumberTable.setWidthPercentage(100);

                String pageNumber = "Page " + writer.getPageNumber();
                PdfPCell pageNumberCell = new PdfPCell(new Phrase(pageNumber, fontTitle));
                pageNumberCell.setBorder(Rectangle.NO_BORDER);
                pageNumberCell.setHorizontalAlignment(Element.ALIGN_CENTER);

                pageNumberTable.addCell(pageNumberCell);
                try {
                    footerTable.setTotalWidth(document.right() - document.left());
                    pageNumberTable.setTotalWidth(document.right() - document.left());

                    float footerYPosition = document.bottomMargin() + 20; // Adjust upward by 20 units
                    float pageNumberYPosition = document.bottom() - 10;

                    footerTable.writeSelectedRows(0, -1, document.leftMargin(), footerYPosition, writer.getDirectContent());
                    pageNumberTable.writeSelectedRows(0, -1, document.leftMargin(), pageNumberYPosition, writer.getDirectContent());

                } catch (DocumentException e) {
                    e.printStackTrace();
                }
            }
        }

        public List<ReportDTO> getAllReports() {
            String sql = "SELECT id, name, from_date, to_date, generated_by, generated_date, is_approved, approved_by, approved_date, assigned_review, reviewed_by, review_date, is_approver_required, assigned_approver FROM stored_reports ORDER BY generated_date DESC";
            return jdbcTemplate.query(sql, (rs, rowNum) -> new ReportDTO(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("from_date"),
                    rs.getString("to_date"),
                    null, // pdfData will be set in getReportById
                    rs.getString("generated_by"),
                    rs.getString("generated_date"),
                    rs.getBoolean("is_approved"),
                    rs.getString("approved_by"),
                    rs.getString("approved_date"),
                    rs.getString("assigned_review"),  // Retrieve assignedReview
                    rs.getString("reviewed_by"),      // Retrieve reviewedBy
                    rs.getString("review_date") ,      // Retrieve reviewDate
                    rs.getBoolean("is_approver_required"),
                    rs.getString("assigned_approver")
            ));
        }
        public ReportDTO getReportById(Long reportId) {
            String sql = "SELECT name, from_date, to_date, pdf_data, generated_by, generated_date, is_approved, approved_by, approved_date, assigned_review, reviewed_by, review_date, is_approver_required, assigned_approver FROM stored_reports WHERE id = ?";
            return jdbcTemplate.queryForObject(sql, new Object[]{reportId}, (rs, rowNum) -> new ReportDTO(
                    reportId,
                    rs.getString("name"),
                    rs.getString("from_date"),
                    rs.getString("to_date"),
                    rs.getBytes("pdf_data"),
                    rs.getString("generated_by"),
                    rs.getString("generated_date"),
                    rs.getBoolean("is_approved"),
                    rs.getString("approved_by"),
                    rs.getString("approved_date"),
                    rs.getString("assigned_review"),  // Retrieve assignedReview
                    rs.getString("reviewed_by"),      // Retrieve reviewedBy
                    rs.getString("review_date"),      // Retrieve reviewDate
                    rs.getBoolean("is_approver_required"),
                    rs.getString("assigned_approver")
            ));
        }
        public List<GroupDTO> getAllGroups() {
            String sql = "SELECT id, name FROM group_names";
            return jdbcTemplate.query(sql, (rs, rowNum) -> new GroupDTO(
                    rs.getLong("id"),
                    rs.getString("name")
            ));
        }

        public void stampReviewInfo(Long reportId, String reviewer) throws Exception {
            // Fetch the existing PDF from DB
            ReportDTO reportDTO = getReportById(reportId);
            if (reportDTO == null) {
                throw new Exception("Report not found");
            }

            // Read the PDF
            PdfReader pdfReader = new PdfReader(new ByteArrayInputStream(reportDTO.getPdfData()));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PdfStamper pdfStamper = new PdfStamper(pdfReader, byteArrayOutputStream);

            // Prepare review info
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-MMMM-yyyy HH:mm:ss");
            String formattedDate = now.format(formatter);

            Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            PdfPTable reviewTable = new PdfPTable(1);
            reviewTable.setTotalWidth(180);
            reviewTable.setWidthPercentage(100);

            PdfPCell reviewCell = new PdfPCell(new Phrase("\nReviewed By:Supervisor: " + reviewer + "\nDate: " + formattedDate, font));
            reviewCell.setBorder(Rectangle.NO_BORDER);
            reviewCell.setPadding(10);
            reviewCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            reviewTable.addCell(reviewCell);

            // Stamp on every page
            int totalPages = pdfReader.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                PdfContentByte canvas = pdfStamper.getOverContent(i);
                Rectangle pageSize = pdfReader.getPageSize(i);

                float x = pageSize.getRight() - 260;
                float y = pageSize.getBottom() + 73;
                reviewTable.writeSelectedRows(0, -1, x, y, canvas);
            }


            pdfStamper.close();
            pdfReader.close();

            // Save updated PDF and update DB
            long reviewTimeMillis = System.currentTimeMillis();
            String sql = "UPDATE stored_reports SET pdf_data = ?, reviewed_by = ?, review_date = ? WHERE id = ?";
            jdbcTemplate.update(sql, byteArrayOutputStream.toByteArray(), reviewer, String.valueOf(reviewTimeMillis), reportId);
        }

        public void approveReport(Long reportId, String username) throws Exception {
            // Fetch the existing PDF from the database
            ReportDTO reportDTO = getReportById(reportId);
            if (reportDTO == null) {
                throw new Exception("Report not found");
            }

            // Read the existing PDF
            PdfReader pdfReader = new PdfReader(new ByteArrayInputStream(reportDTO.getPdfData()));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PdfStamper pdfStamper = new PdfStamper(pdfReader, byteArrayOutputStream);

            // Define the approval table
            PdfPTable approvalTable = new PdfPTable(1);
            approvalTable.setTotalWidth(180); // Set fixed width
            approvalTable.setWidthPercentage(100);

            Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            // Get the current date
            LocalDateTime currentDateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-MMMM-yyyy HH:mm:ss");
            String formattedDate = currentDateTime.format(formatter);

            // Create Approval Cell with No Border
            PdfPCell approvalCell = new PdfPCell(new Phrase("\nApproved By:Supervisor: " + username +"\nDate: " + formattedDate, fontTitle));
            approvalCell.setBorder(Rectangle.NO_BORDER);
            approvalCell.setPadding(10);
            approvalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            approvalTable.addCell(approvalCell);

            int totalPages = pdfReader.getNumberOfPages();

            // Loop through all pages and stamp the approval table
            for (int i = 1; i <= totalPages; i++) {
                PdfContentByte content = pdfStamper.getOverContent(i);
                Rectangle pageSize = pdfReader.getPageSize(i);
    //                    float xPos = pageSize.getRight();
                float xPos = pageSize.getRight() - approvalTable.getTotalWidth() +210;

                float yPos = pageSize.getBottom() + 73;
                approvalTable.writeSelectedRows(0, -1, xPos, yPos, content);
            }


            // Close the PDF
            pdfStamper.close();
            pdfReader.close();

            // Update the database with the approved PDF
            long approvedTimeMillis = System.currentTimeMillis();
            String sql = "UPDATE stored_reports SET pdf_data = ?, is_approved = ?, approved_by = ?, approved_date = ? WHERE id = ?";
            jdbcTemplate.update(sql, byteArrayOutputStream.toByteArray(), true, username, String.valueOf(approvedTimeMillis), reportId);
        }

        //            public void reviewReport(Long reportId, String username) throws Exception {
    //                // Update the PDF and approval details in the database
    //                long reviewedTimeMillis = System.currentTimeMillis();
    //                String sql = "UPDATE stored_reports SET reviewed_by = ?, review_date = ? WHERE id = ?";
    //                jdbcTemplate.update(sql, username, String.valueOf(reviewedTimeMillis), reportId);
    //            }
        public void reviewReport(Long reportId, String username) throws Exception {
            stampReviewInfo(reportId, username);
        }

        private Map<String, double[]> extractParameterRanges(Long templateId) {
            ReportTemplate template = templateService.getById(templateId);
            Map<String, double[]> parameterRanges = new HashMap<>();

            for (String parameter : template.getParameters()) {
                String cleanParameter = extractBaseParameter(parameter); // same as you're already using
                double fromValue = getFromValue(parameter);
                double toValue = getToValue(parameter);
                parameterRanges.put(cleanParameter, new double[]{fromValue, toValue});
            }

            return parameterRanges;
        }
        public ReportDTO findLatestGeneratedReport(Long templateId, String fromDateTime, String toDateTime, String generatedBy) {
            String templateName = getReportName(templateId).replaceAll("[^a-zA-Z0-9]", "_");
            String likeName = templateName + "%";

            String sql = "SELECT id, name, from_date, to_date, pdf_data, generated_by, generated_date, is_approved, " +
                    "approved_by, approved_date, assigned_review, reviewed_by, review_date, " +
                    "is_approver_required, assigned_approver " +
                    "FROM stored_reports WHERE name LIKE ? AND from_date = ? AND to_date = ? AND generated_by = ? " +
                    "ORDER BY generated_date DESC ";

            List<ReportDTO> reports = jdbcTemplate.query(sql,
                    new Object[]{likeName, fromDateTime, toDateTime, generatedBy},
                    (rs, rowNum) -> new ReportDTO(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("from_date"),
                            rs.getString("to_date"),
                            rs.getBytes("pdf_data"),
                            rs.getString("generated_by"),
                            rs.getString("generated_date"),
                            rs.getBoolean("is_approved"),
                            rs.getString("approved_by"),
                            rs.getString("approved_date"),
                            rs.getString("assigned_review"),
                            rs.getString("reviewed_by"),
                            rs.getString("review_date"),
                            rs.getBoolean("is_approver_required"),
                            rs.getString("assigned_approver")
                    )
            );

            return reports.isEmpty() ? null : reports.get(0);
        }

    }