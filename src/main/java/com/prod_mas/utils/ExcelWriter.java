package com.prod_mas.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelWriter {

    public static void appendTrainingResult(String path, String model, double durationMs) {
        File file = new File(path);
        Workbook wb;

        try {
            if (!file.exists()) {
                wb = new XSSFWorkbook();
                Sheet sheet = wb.createSheet("Training");

                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Model");
                header.createCell(1).setCellValue("Duration (ms)");
            } else {
                FileInputStream fis = new FileInputStream(file);
                wb = new XSSFWorkbook(fis);
                fis.close();
            }

            Sheet sheet = wb.getSheetAt(0);
            int rowIndex = sheet.getLastRowNum() + 1;

            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue(model);
            row.createCell(1).setCellValue(durationMs);

            FileOutputStream fos = new FileOutputStream(file);
            wb.write(fos);
            fos.close();
            wb.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
