package org.schemaexporter;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class SchemaPdfExporter {

    public static void exportSchemaToPdf(Connection conn, String fullFilePath) throws Exception {
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
                        "WHERE a.Table_Name not in ('sysdiagrams')" +
                        "ORDER BY b.模組別, b.表格名稱, b.序號;";


        BaseFont bf = BaseFont.createFont("C:/Windows/Fonts/msjh.ttc,0", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font font = new Font(bf, 10);
        Font boldFont = new Font(bf, 12, Font.BOLD);

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        Map<String, Map<String, List<Map<String, String>>>> moduleMap = new LinkedHashMap<>();
        Map<String, String> tableUsageMap = new HashMap<>();
        List<String[]> missingDescriptions = new ArrayList<>();
        Set<String> seenNoModule = new HashSet<>();
        Set<String> processedTables = new HashSet<>();

        while (rs.next()) {
            String table = rs.getString("表格名稱");
            String module = rs.getString("模組別");
            String usage = rs.getString("用途說明") != null ? rs.getString("用途說明") : "";

            String tableName = rs.getString("Table_Name");

            // 1. 模組別不存在
            if (rs.getInt("模組別有值") == 0) {
                System.out.printf("⚠️ 資料表: [%s] --> 模組別不存在%n", tableName);
                continue;
            }

            // 2. 模組別存在，才判斷表格是否存在
            if (rs.getInt("表格是否存在") == 0) {
                System.out.printf("⚠️ 模組別: [%s]，資料表: [%s] --> 資料表不存在%n", module, tableName);
                continue;
            }

            processedTables.add(table);
            moduleMap.putIfAbsent(module, new LinkedHashMap<>());
            Map<String, List<Map<String, String>>> tableMap = moduleMap.get(module);
            tableMap.putIfAbsent(table, new ArrayList<>());
            tableUsageMap.putIfAbsent(table, usage);

            Map<String, String> row = new HashMap<>();
            row.put("序號", rs.getString("序號"));
            row.put("欄位", rs.getString("欄位"));
            row.put("資料型態", rs.getString("資料型態"));
            row.put("長度", String.valueOf(rs.getInt("長度")));
            row.put("not null", rs.getString("not null"));
            row.put("Index", rs.getString("Index"));
            row.put("Default", rs.getString("Default"));
            row.put("用途說明", usage);
            row.put("模組別", module);
            row.put("描述", rs.getString("描述"));

            if (rs.getInt("描述有值") == 0) {
                missingDescriptions.add(new String[]{module, table, rs.getString("欄位")});
            }

            tableMap.get(table).add(row);
        }

        Document document = new Document(PageSize.A4, 20, 20, 30, 20);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fullFilePath));
        document.open();

        PdfContentByte cb = writer.getDirectContent();
        PdfOutline rootOutline = cb.getRootOutline(); // 根目錄書籤

        for (String module : moduleMap.keySet()) {
            document.setPageSize(PageSize.A4);
            document.newPage();

            // 模組書籤（標籤）
            PdfDestination moduleDest = new PdfDestination(PdfDestination.FITH, writer.getVerticalPosition(true));
            PdfOutline moduleOutline = new PdfOutline(rootOutline, moduleDest, "模組: " + module);

            PdfPTable detailTable = new PdfPTable(4);
            detailTable.setWidthPercentage(80);
            detailTable.setSpacingAfter(20f);
            detailTable.setWidths(new float[]{2f, 2f, 4f, 4f});

            detailTable.addCell(new PdfPCell(new Phrase("系統別", boldFont)));
            detailTable.addCell(new PdfPCell(new Phrase("項次", boldFont)));
            detailTable.addCell(new PdfPCell(new Phrase("檔案英文", boldFont)));
            detailTable.addCell(new PdfPCell(new Phrase("檔案中文", boldFont)));

            Map<String, List<Map<String, String>>> tableMap = moduleMap.get(module);
            int index = 1;
            for (String table : tableMap.keySet()) {
                String usage = tableUsageMap.getOrDefault(table, "");
                detailTable.addCell(new PdfPCell(new Phrase(module, font)));
                detailTable.addCell(new PdfPCell(new Phrase(String.valueOf(index++), font)));
                detailTable.addCell(new PdfPCell(new Phrase(table, font)));
                detailTable.addCell(new PdfPCell(new Phrase(usage, font)));
            }
            document.add(detailTable);

            for (String table : tableMap.keySet()) {
                List<Map<String, String>> rows = tableMap.get(table);
                String usage = tableUsageMap.getOrDefault(table, "");

                document.setPageSize(PageSize.A4.rotate());
                document.setMargins(36, 36, 36, 36);
                document.newPage();

                //  表格書籤
                PdfDestination tableDest = new PdfDestination(PdfDestination.FITH, writer.getVerticalPosition(true));
                new PdfOutline(moduleOutline, tableDest, table);

                PdfPTable combinedTable = new PdfPTable(8);
                combinedTable.setWidthPercentage(100);
                combinedTable.setWidths(new float[]{2f, 3f, 2f, 1f, 1.5f, 1.5f, 2f, 3f});

                combinedTable.addCell(new PdfPCell(new Phrase(table, boldFont)) {{
                    setColspan(4);
                    setHorizontalAlignment(Element.ALIGN_CENTER);
                }});
                combinedTable.addCell(new PdfPCell(new Phrase(usage, boldFont)) {{
                    setColspan(4);
                    setHorizontalAlignment(Element.ALIGN_CENTER);
                }});

                String[] headers = {"序號", "欄位", "資料型態", "長度", "not null", "Index", "Default", "描述"};
                for (String header : headers) {
                    combinedTable.addCell(new PdfPCell(new Phrase(header, font)));
                }

                for (Map<String, String> data : rows) {
                    for (String header : headers) {
                        combinedTable.addCell(new PdfPCell(new Phrase(data.getOrDefault(header, ""), font)));
                    }
                }

                document.add(combinedTable);
            }
        }

        document.close();
        System.out.println("\n📦 Schema PDF 匯出完成: " + fullFilePath);

        if (!missingDescriptions.isEmpty()) {
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date());
            String errorFile = fullFilePath.substring(0, fullFilePath.lastIndexOf(File.separator) + 1)
                    + "Schema_缺少描述欄位資料_" + timestamp + ".pdf";
            Document errorDoc = new Document(PageSize.A4);
            PdfWriter.getInstance(errorDoc, new FileOutputStream(errorFile));
            errorDoc.open();

            errorDoc.add(new Paragraph("下列欄位缺少描述：", boldFont));
            errorDoc.add(new Paragraph(" "));

            PdfPTable errTable = new PdfPTable(2);
            errTable.setWidthPercentage(90);
            errTable.setWidths(new float[]{4f, 4f});
            errTable.addCell(new PdfPCell(new Phrase("表格名稱", boldFont)));
            errTable.addCell(new PdfPCell(new Phrase("欄位名稱", boldFont)));

            for (String[] item : missingDescriptions) {
                errTable.addCell(new PdfPCell(new Phrase(item[1], font))); // 表格名稱
                errTable.addCell(new PdfPCell(new Phrase(item[2], font))); // 欄位名稱
            }

            errorDoc.add(errTable);
            errorDoc.close();

            System.out.println("⚠️ 缺少描述欄位已輸出: " + errorFile);
        }

        System.out.println("\n✅ 已成功處理表格共 " + processedTables.size() + " 張。\n");
    }
}
