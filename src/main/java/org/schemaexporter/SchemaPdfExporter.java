package org.schemaexporter;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.FileOutputStream;
import java.sql.*;
import java.util.*;

public class SchemaPdfExporter {

    private static final String META_TABLE_NAME = "dbo.HN_Table_List";

    public static void exportSchemaToPdf(Connection conn, String fullFilePath) throws Exception {
        String sql = "SELECT " +
                "t.name AS 表格名稱, " +
                "ep2.value AS 用途說明, " +
                "ep3.value AS 模組別, " +
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

        //中文設定(微軟正黑體)
        BaseFont bf = BaseFont.createFont("C:/Windows/Fonts/msjh.ttc,0", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font font = new Font(bf, 10);
        Font boldFont = new Font(bf, 12, Font.BOLD);

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        //模組->資料表->欄位
        Map<String, Map<String, java.util.List<Map<String, String>>>> moduleMap = new LinkedHashMap<>();
        Map<String, String> tableUsageMap = new HashMap<>();//紀錄用途說明
        Set<String> processedTables = new HashSet<>();
        java.util.List<String> hasTableButNoModule = new ArrayList<>();//有表無模組
        Set<String> seenNoModule = new HashSet<>();  // 防止重複加入

        while (rs.next()) {

            String table = rs.getString("表格名稱");
            String module = rs.getString("模組別");
            String usage = rs.getString("用途說明") != null ? rs.getString("用途說明") : "";

            //無模組別顯示錯誤
            if (module == null || module.isBlank()) {
                if (!seenNoModule.contains(table)) {
                    hasTableButNoModule.add(String.format("⚠️ 資料表:[%s] -> 模組別不存在", table, usage));
                    seenNoModule.add(table);
                }
                continue;
            }

            processedTables.add(table);
            moduleMap.putIfAbsent(module, new LinkedHashMap<>());
            Map<String, java.util.List<Map<String, String>>> tableMap = moduleMap.get(module);
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

            tableMap.get(table).add(row);
        }

        //直式邊界調整
        Document document = new Document(PageSize.A4, 20, 20, 30, 20);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fullFilePath));
        document.open();

        //標籤
        PdfContentByte cb = writer.getDirectContent();
        PdfOutline root = cb.getRootOutline();

        if (moduleMap.isEmpty()) {
            document.add(new Paragraph("⚠️ 無符合條件之資料表，無法匯出內容", boldFont));
        } else {
            //模組別分類輸出
            for (Map.Entry<String, Map<String, java.util.List<Map<String, String>>>> moduleEntry : moduleMap.entrySet()) {
                String module = moduleEntry.getKey();
                Map<String, java.util.List<Map<String, String>>> tableMap = moduleEntry.getValue();

                document.setPageSize(PageSize.A4);
                document.newPage();
                //標籤(模組)
                PdfDestination moduleDest = new PdfDestination(PdfDestination.FIT);
                new PdfOutline(root, moduleDest, "模組: " + module);

                PdfPTable detailTable = new PdfPTable(4);
                detailTable.setWidthPercentage(80);
                detailTable.setSpacingAfter(20f);
                detailTable.setWidths(new float[]{2f, 2f, 4f, 4f});

                detailTable.addCell(new PdfPCell(new Phrase("系統別", boldFont)));
                detailTable.addCell(new PdfPCell(new Phrase("項次", boldFont)));
                detailTable.addCell(new PdfPCell(new Phrase("檔案英文", boldFont)));
                detailTable.addCell(new PdfPCell(new Phrase("檔案中文", boldFont)));

                int index = 1;
                for (String table : tableMap.keySet()) {
                    String usage = tableUsageMap.getOrDefault(table, "");
                    detailTable.addCell(new PdfPCell(new Phrase(module, font)));
                    detailTable.addCell(new PdfPCell(new Phrase(String.valueOf(index++), font)));
                    detailTable.addCell(new PdfPCell(new Phrase(table, font)));
                    detailTable.addCell(new PdfPCell(new Phrase(usage, font)));
                }
                document.add(detailTable);

                for (Map.Entry<String, java.util.List<Map<String, String>>> tableEntry : tableMap.entrySet()) {
                    String table = tableEntry.getKey();
                    java.util.List<Map<String, String>> rows = tableEntry.getValue();
                    String usage = rows.get(0).getOrDefault("用途說明", "");

                    //橫式邊界調整
                    document.setPageSize(PageSize.A4.rotate());
                    document.setMargins(36, 36, 36, 36);
                    document.newPage();

                    //標籤(表格)
                    PdfDestination tableDest = new PdfDestination(PdfDestination.FIT);
                    new PdfOutline(root, tableDest, "表格: " + table);

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
        }
        document.close();
        System.out.println("Schema PDF 匯出完成: " + fullFilePath);
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
            String metadataTableFullName) throws SQLException {

        String sql = String.format("SELECT t.Table_Name, t.Table_Desc, t.System_Name, ep3.value AS 模組別 " +
                "FROM %s t " +
                "LEFT JOIN sys.tables st ON t.Table_Name = st.name " +
                "LEFT JOIN sys.extended_properties ep3 " +
                "  ON st.object_id = ep3.major_id AND ep3.name = '模組別'", metadataTableFullName);

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            java.util.List<String> noTableButModule = new ArrayList<>();
            java.util.List<String> noTableNoModule = new ArrayList<>();

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

//            if (!hasTableButNoModule.isEmpty()) {
//                hasTableButNoModule.forEach(System.out::println);
//            }

            if (!noTableButModule.isEmpty()) {
                noTableButModule.forEach(System.out::println);
            }

            if (!noTableNoModule.isEmpty()) {
                System.out.println("\n無模組別且資料表不存在：");
                noTableNoModule.forEach(System.out::println);
            }

            if (noTableButModule.isEmpty() && noTableNoModule.isEmpty()) {
                System.out.println(" ✅所有表格皆成功處理且系統別資訊齊全。");
            }

            System.out.println("\n ✅ 已成功處理表格共 " + processedTables.size() + " 張：" + processedTables);
        }
    }

}
