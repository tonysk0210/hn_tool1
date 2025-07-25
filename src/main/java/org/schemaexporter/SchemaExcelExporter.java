package org.schemaexporter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SchemaExcelExporter {

    public static void exportSchemaToExcel(Connection conn, String outputDirPath) throws Exception {
        String sql =
                "SELECT b.*, a.Table_Name,\n" +
                        "    CASE WHEN ISNULL(b.模組別, '') = '' THEN 0 ELSE 1 END AS 模組別有值,\n" +
                        "    CASE WHEN b.表格名稱 IS NULL THEN 0 ELSE 1 END AS 表格是否存在,\n" +
                        "    CASE WHEN ISNULL(b.描述, '') = '' THEN 0 ELSE 1 END AS 描述有值\n" +
                        "FROM HN_Table_List a\n" +
                        "LEFT JOIN (\n" +
                        "    SELECT t.name AS 表格名稱,\n" +
                        "        ep2.value AS 用途說明,\n" +
                        "        ep3.value AS 模組別,\n" +
                        "        ROW_NUMBER() OVER (PARTITION BY t.name ORDER BY c.column_id) AS 序號,\n" +
                        "        c.name AS 欄位,\n" +
                        "        typ.name AS 資料型態,\n" +
                        "        c.max_length AS 長度,\n" +
                        "        CASE WHEN c.is_nullable = 0 THEN 'V' ELSE '' END AS [not null],\n" +
                        "        CASE WHEN i.is_primary_key = 1 THEN 'PKEY'\n" +
                        "             WHEN i.is_unique = 1 THEN 'UNIKEY'\n" +
                        "             ELSE '' END AS [Index],\n" +
                        "        dc.definition AS [Default],\n" +
                        "        ep.value AS 描述\n" +
                        "    FROM sys.columns c\n" +
                        "    JOIN sys.tables t ON c.object_id = t.object_id\n" +
                        "    JOIN sys.types typ ON c.user_type_id = typ.user_type_id\n" +
                        "    LEFT JOIN sys.default_constraints dc ON c.default_object_id = dc.object_id\n" +
                        "    LEFT JOIN sys.extended_properties ep ON c.object_id = ep.major_id AND c.column_id = ep.minor_id AND ep.name = 'MS_Description'\n" +
                        "    LEFT JOIN sys.extended_properties ep2 ON c.object_id = ep2.major_id AND ep2.minor_id = 0 AND ep2.name = '用途說明'\n" +
                        "    LEFT JOIN sys.extended_properties ep3 ON c.object_id = ep3.major_id AND ep3.minor_id = 0 AND ep3.name = '模組別'\n" +
                        "    LEFT JOIN sys.index_columns ic ON c.object_id = ic.object_id AND c.column_id = ic.column_id\n" +
                        "    LEFT JOIN sys.indexes i ON ic.object_id = i.object_id AND ic.index_id = i.index_id\n" +
                        "    WHERE t.is_ms_shipped = 0 AND t.name NOT IN ('sysdiagrams')\n" +
                        ") b ON a.Table_Name = b.表格名稱\n" +
                        "ORDER BY b.模組別, b.表格名稱, b.序號;";


        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        Map<String, Map<String, List<List<String>>>> moduleTableMap = new LinkedHashMap<>();
        Map<String, String> tableUsageMap = new HashMap<>();
        List<String[]> missingDescriptions = new ArrayList<>();
        Set<String> processedTables = new HashSet<>();

        while (rs.next()) {
            String table = rs.getString("表格名稱");
            String module = rs.getString("模組別");
            String usage = rs.getString("用途說明") != null ? rs.getString("用途說明") : "";
            String tableName = rs.getString("Table_Name");

            if (rs.getInt("模組別有值") == 0) {
                System.out.printf("⚠️ 資料表: [%s] --> 模組別不存在%n", tableName);
                continue;
            }

            if (rs.getInt("表格是否存在") == 0) {
                System.out.printf("⚠️ 模組別: [%s]，資料表: [%s] --> 資料表不存在%n", module, tableName);
                continue;
            }

            processedTables.add(table);
            moduleTableMap.putIfAbsent(module, new LinkedHashMap<>());
            Map<String, List<List<String>>> tableMap = moduleTableMap.get(module);
            tableMap.putIfAbsent(table, new ArrayList<>());
            tableUsageMap.putIfAbsent(table, usage);

            List<String> row = new ArrayList<>();
            row.add(rs.getString("序號"));
            row.add(rs.getString("欄位"));
            row.add(rs.getString("資料型態"));
            row.add(String.valueOf(rs.getInt("長度")));
            row.add(rs.getString("not null"));
            row.add(rs.getString("Index"));
            row.add(rs.getString("Default"));
            row.add(rs.getString("描述"));

            if (rs.getInt("描述有值") == 0) {
                missingDescriptions.add(new String[]{module, tableName, rs.getString("欄位")});
            }

            tableMap.get(table).add(row);
        }

        if (!moduleTableMap.isEmpty()) {
            System.out.println(); // 加這一行，錯誤訊息與成功訊息之間空一行
        }
        for (String module : moduleTableMap.keySet()) {
            Workbook workbook = new XSSFWorkbook();
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle leftHeaderStyle = createLeftHeaderStyle(workbook);
            CellStyle cellStyle = createBorderedStyle(workbook, false);
            CellStyle titleStyle = createHeaderStyle(workbook);
            CellStyle descriptionStyle = createDescriptionStyle(workbook);

            Map<String, List<List<String>>> tableMap = moduleTableMap.get(module);

            Sheet listSheet = workbook.createSheet("檔案清單");
            String[] listHeaders = {"系統別", "項次", "檔案英文", "檔案中文"};
            int[] listColumnWidths = {10, 8, 30, 35}; // 根據欄位內容調整寬度
            for (int i = 0; i < listColumnWidths.length; i++) {
                listSheet.setColumnWidth(i, listColumnWidths[i] * 256);
            }
            Row listHeaderRow = listSheet.createRow(0);
            for (int i = 0; i < listHeaders.length; i++) {
                Cell cell = listHeaderRow.createCell(i);
                cell.setCellValue(listHeaders[i]);
                cell.setCellStyle(leftHeaderStyle);
            }

            int rowIdx = 1;
            int index = 1;
            for (String tableName : tableMap.keySet()) {
                String usage = tableUsageMap.getOrDefault(tableName, "");
                Row row = listSheet.createRow(rowIdx++);
                String[] values = {module, String.valueOf(index++), tableName, usage};
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(cellStyle);
                }
            }

            for (Map.Entry<String, List<List<String>>> entry : tableMap.entrySet()) {
                String tableName = entry.getKey();
                List<List<String>> rows = entry.getValue();
                String usage = tableUsageMap.getOrDefault(tableName, "");

                Sheet sheet = workbook.createSheet(tableName);
                sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE); // 設定紙張為 A4
                sheet.getPrintSetup().setLandscape(true); // 設定為橫向列印


                Row infoRow = sheet.createRow(0);

// 表格名稱（0~3欄）
                for (int i = 0; i <= 3; i++) {
                    Cell cell = infoRow.createCell(i);
                    cell.setCellStyle(titleStyle);
                    if (i == 0) {
                        cell.setCellValue(tableName);
                    }
                }
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

// 用途說明（4~7欄）
                for (int i = 4; i <= 7; i++) {
                    Cell cell = infoRow.createCell(i);
                    cell.setCellStyle(titleStyle);
                    if (i == 4) {
                        cell.setCellValue(usage);
                    }
                }
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 4, 7));

                for (int i = 5; i <= 7; i++) {
                    infoRow.createCell(i).setCellStyle(titleStyle);
                }

                String[] headers = {"序號", "欄位", "資料型態", "長度", "not null", "Index", "Default", "描述"};

                Row headerRow = sheet.createRow(1);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(leftHeaderStyle); // ⬅️ 使用靠左樣式
                }

                for (int r = 0; r < rows.size(); r++) {
                    Row row = sheet.createRow(r + 2);
                    List<String> data = rows.get(r);
                    for (int c = 0; c < data.size(); c++) {
                        Cell cell = row.createCell(c);
                        cell.setCellValue(data.get(c) != null ? data.get(c) : "");
                        if (c == 7) {
                            cell.setCellStyle(descriptionStyle);
                        } else {
                            cell.setCellStyle(cellStyle);
                        }
                    }
                }

                int[] columnWidths = {6, 30, 25, 8, 12, 10, 14, 30};
                for (int i = 0; i < headers.length; i++) {
                    sheet.setColumnWidth(i, columnWidths[i] * 256);
                }
            }

            String safeModuleName = module.replaceAll("[\\/:*?\"<>|]", "_");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            String filePath = outputDirPath + File.separator + "Schema_" + safeModuleName + "_" + timestamp + ".xlsx";

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                workbook.write(out);
            }
            workbook.close();
            System.out.println("模組 " + module + " 的 Schema Excel 匯出成功：" + filePath);
        }

        if (!missingDescriptions.isEmpty()) {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("缺描述欄位");

            CellStyle borderedStyle = workbook.createCellStyle();
            borderedStyle.setBorderTop(BorderStyle.THIN);
            borderedStyle.setBorderBottom(BorderStyle.THIN);
            borderedStyle.setBorderLeft(BorderStyle.THIN);
            borderedStyle.setBorderRight(BorderStyle.THIN);
            borderedStyle.setAlignment(HorizontalAlignment.LEFT);
            borderedStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("表格名稱");
            header.createCell(1).setCellValue("欄位名稱");
            header.getCell(0).setCellStyle(borderedStyle);
            header.getCell(1).setCellStyle(borderedStyle);

            for (int i = 0; i < missingDescriptions.size(); i++) {
                Row row = sheet.createRow(i + 1);
                String[] item = missingDescriptions.get(i);
                Cell cell1 = row.createCell(0);
                Cell cell2 = row.createCell(1);
                cell1.setCellValue(item[1]);
                cell2.setCellValue(item[2]);
                cell1.setCellStyle(borderedStyle);
                cell2.setCellStyle(borderedStyle);
            }

            sheet.setColumnWidth(0, 25 * 256);
            sheet.setColumnWidth(1, 25 * 256);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            String filePath = outputDirPath + File.separator + "Schema_缺少描述欄位資料_" + timestamp + ".xlsx";

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                workbook.write(out);
            }
            workbook.close();
            System.out.println("⚠️ 缺少描述欄位已輸出：" + filePath);
        }

        System.out.println("\n✅ 已成功處理表格共 " + processedTables.size() + " 張。\n");
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12); // 設定標題字體大小
        font.setFontName("Microsoft JhengHei");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createBorderedStyle(Workbook wb, boolean bold) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) 12); // 內容文字大小
        font.setFontName("Microsoft JhengHei");
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createDescriptionStyle(Workbook wb) {
        CellStyle style = createBorderedStyle(wb, false);
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 12); //自訂描述欄位字體大小
        font.setFontName("Microsoft JhengHei");
        style.setFont(font);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private static CellStyle createLeftHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12); // 表頭字體大小
        font.setFontName("Microsoft JhengHei");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT); // 水平靠左
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }


}
