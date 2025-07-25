package org.schemaexporter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SchemaExcelExporter {

    private static final String META_TABLE_NAME = "dbo.HN_Table_List";

    public static void exportSchemaToExcel(Connection conn, String outputDirPath) throws Exception {
        String sql = "SELECT " +
                "t.name AS 表格名稱, " +
                "ep2.value as 用途說明, " +
                "ep3.value as 模組別, " +
                "ROW_NUMBER() OVER (PARTITION BY t.name ORDER BY c.column_id) AS 序號, " +
                "c.name AS 欄位, " +
                "typ.name AS 資料型態, " +
                "c.max_length AS 長度, " +
                "CASE WHEN c.is_nullable = 0 THEN 'V' ELSE '' END AS [not null], " +
                "CASE " +
                "    WHEN i.is_primary_key = 1 THEN 'PKEY' " +
                "    WHEN i.is_unique = 1 THEN 'UNIKEY' " +
                "    ELSE '' " +
                "END AS [Index], " +
                "dc.definition AS [Default], " +
                "ep.value AS 描述 " +
                "FROM sys.columns c " +
                "JOIN sys.tables t ON c.object_id = t.object_id " +
                "JOIN sys.types typ ON c.user_type_id = typ.user_type_id " +
                "LEFT JOIN sys.default_constraints dc ON c.default_object_id = dc.object_id " +
                "LEFT JOIN sys.extended_properties ep " +
                "  ON c.object_id = ep.major_id AND c.column_id = ep.minor_id AND ep.name = 'MS_Description' " +
                "LEFT JOIN sys.extended_properties ep2 " +
                "  ON c.object_id = ep2.major_id AND ep2.name = '用途說明' " +
                "LEFT JOIN sys.extended_properties ep3 " +
                "  ON c.object_id = ep3.major_id AND ep3.name = '模組別' " +
                "LEFT JOIN sys.index_columns ic ON c.object_id = ic.object_id AND c.column_id = ic.column_id " +
                "LEFT JOIN sys.indexes i ON ic.object_id = i.object_id AND ic.index_id = i.index_id " +
                "WHERE t.is_ms_shipped = 0 " +
                "ORDER BY t.name, c.column_id;";

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        //模組->資料表->欄位
        Map<String, Map<String, List<List<String>>>> moduleTableMap = new LinkedHashMap<>();//模組別
        Map<String, String> tableUsageMap = new HashMap<>();//紀錄用途說明
        Set<String> processedTables = new HashSet<>();//用途
        List<String> hasTableButNoModule = new ArrayList<>();//有表無模組
        Set<String> seenNoModule = new HashSet<>();//防止重複

        while (rs.next()) {
            String tableName = rs.getString("表格名稱");
            String module = rs.getString("模組別");
            String usage = rs.getString("用途說明") != null ? rs.getString("用途說明") : "";

            if (module == null || module.isBlank()) {
                if (!seenNoModule.contains(tableName)) {
                    hasTableButNoModule.add(String.format("⚠️ 資料表:[%s] --> 模組別不存在", tableName));
                    seenNoModule.add(tableName);
                }
                continue;
            }

            processedTables.add(tableName);
            moduleTableMap.putIfAbsent(module, new LinkedHashMap<>());
            Map<String, List<List<String>>> tableMap = moduleTableMap.get(module);
            tableMap.putIfAbsent(tableName, new ArrayList<>());
            tableUsageMap.putIfAbsent(tableName, usage);

            List<String> row = new ArrayList<>();
            row.add(String.valueOf(rs.getInt("序號")));
            row.add(rs.getString("欄位"));
            row.add(rs.getString("資料型態"));
            row.add(String.valueOf(rs.getInt("長度")));
            row.add(rs.getString("not null"));
            row.add(rs.getString("Index"));
            row.add(rs.getString("Default"));
            row.add(rs.getString("描述"));

            tableMap.get(tableName).add(row);
        }

        //每個模組產生一份EXCEL
        for (String module : moduleTableMap.keySet()) {
            Workbook workbook = new XSSFWorkbook();

            //欄位樣式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setWrapText(true);
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            titleStyle.setBorderTop(BorderStyle.THIN);
            titleStyle.setBorderBottom(BorderStyle.THIN);
            titleStyle.setBorderLeft(BorderStyle.THIN);
            titleStyle.setBorderRight(BorderStyle.THIN);

            Map<String, List<List<String>>> tableMap = moduleTableMap.get(module);

            //模組明細表
            Sheet listSheet = workbook.createSheet("模組明細表");
            String[] listHeaders = {"系統別", "項次", "檔案英文", "檔案中文"};

            Row headerRow = listSheet.createRow(0);
            for (int i = 0; i < listHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(listHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            int index = 1;
            for (Map.Entry<String, List<List<String>>> entry : tableMap.entrySet()) {
                String tableName = entry.getKey();
                String usage = tableUsageMap.getOrDefault(tableName, "");

                Row row = listSheet.createRow(rowIdx++);
                String[] values = {module, String.valueOf(index++), tableName, usage};
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(cellStyle);
                }
            }

            for (int i = 0; i < listHeaders.length; i++) {
                listSheet.setColumnWidth(i, 20 * 256);
            }

            for (Map.Entry<String, List<List<String>>> entry : tableMap.entrySet()) {
                String tableName = entry.getKey();
                List<List<String>> rows = entry.getValue();
                String usage = tableUsageMap.getOrDefault(tableName, "");

                //資料表的欄位說明表
                Sheet sheet = workbook.createSheet(tableName);
                Row infoRow = sheet.createRow(0);

                //表名
                Cell left = infoRow.createCell(0);
                left.setCellValue(tableName);
                left.setCellStyle(titleStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

                //用途說明
                Cell right = infoRow.createCell(4);
                right.setCellValue(usage);
                right.setCellStyle(titleStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 4, 7));
                for (int i = 5; i <= 7; i++) {
                    Cell dummy = infoRow.createCell(i);
                    dummy.setCellStyle(titleStyle);
                }

                String[] headers = {"序號", "欄位", "資料型態", "長度", "not null", "Index", "Default", "描述"};
                Row header = sheet.createRow(1);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = header.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (int r = 0; r < rows.size(); r++) {
                    Row row = sheet.createRow(r + 2);
                    List<String> data = rows.get(r);
                    for (int c = 0; c < data.size(); c++) {
                        Cell cell = row.createCell(c);
                        cell.setCellValue(data.get(c) != null ? data.get(c) : "");
                        cell.setCellStyle(cellStyle);
                    }
                }

                int[] columnWidths = {6, 20, 15, 8, 10, 10, 15, 30};
                for (int i = 0; i < headers.length; i++) {
                    sheet.setColumnWidth(i, columnWidths[i] * 256);
                }
            }

            String safeModuleName = module.replaceAll("[\\/:*?\"<>|]", "_");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            //欄寬、儲存路徑與檔名
            String fullPath = outputDirPath + File.separator + "Schema_" + safeModuleName + "_" + timestamp + ".xlsx";

            try (FileOutputStream out = new FileOutputStream(fullPath)) {
                workbook.write(out);
            }
            workbook.close();
            System.out.println("模組 " + module + " 的 Schema Excel 匯出成功：" + fullPath);
        }


        // 顯示錯誤提示
        if (!hasTableButNoModule.isEmpty()) {
            hasTableButNoModule.forEach(System.out::println);
        }

        try {
            findAndPrintTablesWithoutColumns(conn, processedTables, META_TABLE_NAME);
        } catch (SQLException e) {
            System.err.println("\n資料有誤,請重新確認後輸入");
        }
    }

    public static void findAndPrintTablesWithoutColumns(
            Connection conn,
            Set<String> processedTables,
            String metadataTableFullName
    ) throws SQLException {

        String sql = String.format("SELECT t.Table_Name, t.Table_Desc, t.System_Name, ep3.value AS 模組別 " +
                "FROM %s t " +
                "LEFT JOIN sys.tables st ON t.Table_Name = st.name " +
                "LEFT JOIN sys.extended_properties ep3 " +
                "  ON st.object_id = ep3.major_id AND ep3.name = '模組別'", metadataTableFullName);

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            List<String> noTableButModule = new ArrayList<>();
            List<String> noTableNoModule = new ArrayList<>();

            while (rs.next()) {
                String table = rs.getString("Table_Name");
                String desc = rs.getString("Table_Desc");
                String system = rs.getString("System_Name");
                String module = rs.getString("模組別");

                boolean hasSystem = system != null && !system.trim().isEmpty();
                boolean inDatabase = processedTables.contains(table);

                if (!inDatabase) {
                    String msg = String.format(
                            "⚠️ 模組別: [%s]，資料表: [%s]，描述: %s --> 資料表不存在",
                            hasSystem ? system : "（無模組別）",
                            table != null ? table : "（無表格名稱）",
                            desc != null ? desc : "（無描述）"
                    );

                    if (hasSystem) {
                        noTableButModule.add(msg);
                    } else {
                        noTableNoModule.add(msg);
                    }
                }
            }

//            System.out.println("\n=== 錯誤資訊提示 ===");
//            hasTableButNoModule.forEach(System.out::println);
            noTableButModule.forEach(System.out::println);
            noTableNoModule.forEach(System.out::println);

            if (noTableButModule.isEmpty() && noTableNoModule.isEmpty()) {
                System.out.println(" ✅ 所有表格皆成功處理且系統別資訊齊全。");
            }


            System.out.println("\n ✅ 已成功處理表格共 " + processedTables.size() + " 張：" + processedTables);
        }
    }
}
