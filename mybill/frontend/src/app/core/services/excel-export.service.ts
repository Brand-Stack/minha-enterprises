import { Injectable } from '@angular/core';
import * as ExcelJS from 'exceljs';
import { saveAs } from 'file-saver';

@Injectable({
  providedIn: 'root'
})
export class ExcelExportService {
  
  /**
   * Export data to Excel with styled headers
   * @param data Array of objects to export
   * @param filename Base name (without extension) unless exactFilename is true (then may include .xlsx)
   * @param headers Optional custom headers (uses object keys if not provided)
   * @param opts exactFilename: save as given name + .xlsx, no date suffix
   */
  async exportToExcel(
    data: any[],
    filename: string,
    headers: string[] = [],
    opts?: { exactFilename?: boolean }
  ): Promise<void> {
    const workbook = new ExcelJS.Workbook();
    workbook.creator = 'Billing Application';
    workbook.created = new Date();
    
    const worksheet = workbook.addWorksheet('Sheet1', {
      views: [{ state: 'frozen', ySplit: 1 }] // Freeze header row
    });
    
    // Determine headers
    let headerRow: string[] = [];
    let dataKeys: string[] = [];
    
    if (headers.length > 0) {
      headerRow = headers;
      if (data.length > 0) {
        dataKeys = Object.keys(data[0]);
      }
    } else if (data.length > 0) {
      dataKeys = Object.keys(data[0]);
      headerRow = dataKeys.map(key => this.formatHeader(key));
    }
    
    if (headerRow.length === 0) {
      console.warn('No data to export');
      return;
    }
    
    // Add header row
    const headerRowObj = worksheet.addRow(headerRow);
    
    // Style header row
    headerRowObj.eachCell((cell, colNumber) => {
      // Header cell styling - Blue background with white text
      cell.fill = {
        type: 'pattern',
        pattern: 'solid',
        fgColor: { argb: 'FF4472C4' } // Blue background
      };
      cell.font = {
        bold: true,
        color: { argb: 'FFFFFFFF' }, // White text
        size: 11,
        name: 'Calibri'
      };
      cell.alignment = {
        horizontal: 'center',
        vertical: 'middle',
        wrapText: true
      };
      cell.border = {
        top: { style: 'thin', color: { argb: 'FF000000' } },
        bottom: { style: 'thin', color: { argb: 'FF000000' } },
        left: { style: 'thin', color: { argb: 'FF000000' } },
        right: { style: 'thin', color: { argb: 'FF000000' } }
      };
    });
    
    // Set header row height
    headerRowObj.height = 25;
    
    // Add data rows
    data.forEach((rowData, rowIndex) => {
      const values = dataKeys.map(key => {
        const value = rowData[key];
        // Format dates
        if (value instanceof Date) {
          return value.toLocaleDateString();
        }
        // Format null/undefined
        if (value === null || value === undefined) {
          return '';
        }
        return value;
      });
      
      const row = worksheet.addRow(values);
      
      // Alternate row colors for better readability
      const isEvenRow = rowIndex % 2 === 0;
      
      row.eachCell((cell, colNumber) => {
        // Data cell styling
        cell.font = {
          size: 10,
          name: 'Calibri'
        };
        cell.alignment = {
          vertical: 'middle',
          wrapText: true
        };
        cell.border = {
          top: { style: 'thin', color: { argb: 'FFD3D3D3' } },
          bottom: { style: 'thin', color: { argb: 'FFD3D3D3' } },
          left: { style: 'thin', color: { argb: 'FFD3D3D3' } },
          right: { style: 'thin', color: { argb: 'FFD3D3D3' } }
        };
        
        // Alternate row background
        if (isEvenRow) {
          cell.fill = {
            type: 'pattern',
            pattern: 'solid',
            fgColor: { argb: 'FFF5F5F5' } // Light gray background
          };
        }
      });
    });
    
    // Auto-fit column widths
    this.autoFitColumns(worksheet, headerRow, data, dataKeys);
    
    // Generate Excel buffer and save
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { 
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
    });
    const out = opts?.exactFilename
      ? (filename.toLowerCase().endsWith('.xlsx') ? filename : `${filename}.xlsx`)
      : `${filename}_${new Date().toISOString().split('T')[0]}.xlsx`;
    saveAs(blob, out);
  }
  
  /**
   * Export data with a title row above the headers
   */
  async exportToExcelWithTitle(data: any[], filename: string, title: string, headers: string[] = []): Promise<void> {
    const workbook = new ExcelJS.Workbook();
    workbook.creator = 'Billing Application';
    workbook.created = new Date();
    
    const worksheet = workbook.addWorksheet('Sheet1', {
      views: [{ state: 'frozen', ySplit: 2 }] // Freeze title and header rows
    });
    
    // Determine headers and data keys
    let headerRow: string[] = [];
    let dataKeys: string[] = [];
    
    if (headers.length > 0) {
      headerRow = headers;
      if (data.length > 0) {
        dataKeys = Object.keys(data[0]);
      }
    } else if (data.length > 0) {
      dataKeys = Object.keys(data[0]);
      headerRow = dataKeys.map(key => this.formatHeader(key));
    }
    
    if (headerRow.length === 0) {
      console.warn('No data to export');
      return;
    }
    
    // Add title row
    const titleRow = worksheet.addRow([title]);
    worksheet.mergeCells(1, 1, 1, headerRow.length);
    titleRow.getCell(1).fill = {
      type: 'pattern',
      pattern: 'solid',
      fgColor: { argb: 'FF1F4E79' } // Dark blue background
    };
    titleRow.getCell(1).font = {
      bold: true,
      color: { argb: 'FFFFFFFF' },
      size: 14,
      name: 'Calibri'
    };
    titleRow.getCell(1).alignment = {
      horizontal: 'center',
      vertical: 'middle'
    };
    titleRow.height = 30;
    
    // Add header row
    const headerRowObj = worksheet.addRow(headerRow);
    
    // Style header row
    headerRowObj.eachCell((cell, colNumber) => {
      cell.fill = {
        type: 'pattern',
        pattern: 'solid',
        fgColor: { argb: 'FF4472C4' } // Blue background
      };
      cell.font = {
        bold: true,
        color: { argb: 'FFFFFFFF' },
        size: 11,
        name: 'Calibri'
      };
      cell.alignment = {
        horizontal: 'center',
        vertical: 'middle',
        wrapText: true
      };
      cell.border = {
        top: { style: 'thin', color: { argb: 'FF000000' } },
        bottom: { style: 'thin', color: { argb: 'FF000000' } },
        left: { style: 'thin', color: { argb: 'FF000000' } },
        right: { style: 'thin', color: { argb: 'FF000000' } }
      };
    });
    headerRowObj.height = 25;
    
    // Add data rows
    data.forEach((rowData, rowIndex) => {
      const values = dataKeys.map(key => {
        const value = rowData[key];
        if (value instanceof Date) {
          return value.toLocaleDateString();
        }
        if (value === null || value === undefined) {
          return '';
        }
        return value;
      });
      
      const row = worksheet.addRow(values);
      const isEvenRow = rowIndex % 2 === 0;
      
      row.eachCell((cell) => {
        cell.font = { size: 10, name: 'Calibri' };
        cell.alignment = { vertical: 'middle', wrapText: true };
        cell.border = {
          top: { style: 'thin', color: { argb: 'FFD3D3D3' } },
          bottom: { style: 'thin', color: { argb: 'FFD3D3D3' } },
          left: { style: 'thin', color: { argb: 'FFD3D3D3' } },
          right: { style: 'thin', color: { argb: 'FFD3D3D3' } }
        };
        
        if (isEvenRow) {
          cell.fill = {
            type: 'pattern',
            pattern: 'solid',
            fgColor: { argb: 'FFF5F5F5' }
          };
        }
      });
    });
    
    // Auto-fit column widths
    this.autoFitColumns(worksheet, headerRow, data, dataKeys);
    
    // Generate and save
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { 
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
    });
    saveAs(blob, `${filename}_${new Date().toISOString().split('T')[0]}.xlsx`);
  }
  
  /**
   * Auto-fit column widths based on content
   */
  private autoFitColumns(worksheet: ExcelJS.Worksheet, headers: string[], data: any[], dataKeys: string[]): void {
    headers.forEach((header, index) => {
      let maxLength = header.length;
      
      // Check data lengths
      data.forEach(row => {
        const value = row[dataKeys[index]];
        const cellValue = value !== null && value !== undefined ? String(value) : '';
        maxLength = Math.max(maxLength, cellValue.length);
      });
      
      // Set column width (min 10, max 50)
      const width = Math.min(Math.max(maxLength + 2, 10), 50);
      worksheet.getColumn(index + 1).width = width;
    });
  }
  
  /**
   * Format object key to readable header
   */
  private formatHeader(key: string): string {
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/_/g, ' ')
      .replace(/^./, str => str.toUpperCase())
      .trim();
  }
}
