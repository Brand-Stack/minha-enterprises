package com.app.billing.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class MlWiseHierarchyExcel {

    private static final String INPUT_CSV =
            "C:\\Users\\SrinivasanGovindaras\\Downloads\\ManufacturingItems_Jan 23 2026 18_43_58.csv";

    private static final String OUTPUT_XLSX =
            "C:\\Users\\SrinivasanGovindaras\\Downloads\\ManufacturingItems_ML_Wise_Final.xlsx";

    public static void main(String[] args) throws Exception {

        Workbook workbook = new XSSFWorkbook();
        BufferedReader br = new BufferedReader(new FileReader(INPUT_CSV));

        Map<String, Sheet> mlSheets = new LinkedHashMap<>();
        Map<Integer, String> levelValueMap = new HashMap<>();

        String currentML = null;
        String[] header = null;

        String line;
        while ((line = br.readLine()) != null) {
            String[] cols = line.split(",", -1);

            // -------- HEADER --------
            if (header == null) {
                header = cols;
                continue;
            }

            int level = Integer.parseInt(cols[0].trim());
            String item = cols[1].trim();

            // -------- NEW ML --------
            if (level == 1) {
                currentML = item;
                levelValueMap.clear();

                if (!mlSheets.containsKey(currentML)) {
                    Sheet sheet = workbook.createSheet(currentML);
                    mlSheets.put(currentML, sheet);

                    Row headerRow = sheet.createRow(0);
                    headerRow.createCell(0).setCellValue(header[0]);
                    headerRow.createCell(1).setCellValue(header[1]);
                    headerRow.createCell(2).setCellValue("LEVEL_1");
                    headerRow.createCell(3).setCellValue("LEVEL_2");
                    headerRow.createCell(4).setCellValue("LEVEL_3");

                    for (int i = 2; i < header.length; i++) {
                        headerRow.createCell(i + 3).setCellValue(header[i]);
                    }
                }
            }

            if (currentML == null) continue;

            // -------- CLEAR DEEPER LEVELS --------
            levelValueMap.keySet().removeIf(l -> l > level);

            // -------- UPDATE CURRENT LEVEL --------
            levelValueMap.put(level, item);

            Sheet sheet = mlSheets.get(currentML);
            Row row = sheet.createRow(sheet.getLastRowNum() + 1);

            // -------- BASE --------
            row.createCell(0).setCellValue(level);
            row.createCell(1).setCellValue(item);

            // -------- LEVEL FILL (AWARE OF COLUMN A) --------
            row.createCell(2).setCellValue(level >= 1 ? levelValueMap.getOrDefault(1, "") : "");
            row.createCell(3).setCellValue(level >= 2 ? levelValueMap.getOrDefault(2, "") : "");
            row.createCell(4).setCellValue(level >= 3 ? levelValueMap.getOrDefault(3, "") : "");

            // -------- SHIFT REST --------
            for (int i = 2; i < cols.length; i++) {
                row.createCell(i + 3).setCellValue(cols[i]);
            }
        }

        br.close();

        try (FileOutputStream fos = new FileOutputStream(OUTPUT_XLSX)) {
            workbook.write(fos);
        }
        workbook.close();

        System.out.println("✅ ML-wise hierarchy with proper stop logic applied");
    }
}
