package com.app.billing.service;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Slf4j
public class PdfWatermarkHelper {

    private static ImageData bgDataCache = null;

    private static synchronized ImageData getBgData() {
        if (bgDataCache != null) {
            return bgDataCache;
        }
        try (InputStream is = new ClassPathResource("images/bgImage.png").getInputStream()) {
            byte[] bytes = is.readAllBytes();
            bgDataCache = ImageDataFactory.create(bytes);
        } catch (Exception e) {
            log.warn("Could not load bgImage.png from classpath: {}", e.getMessage());
        }
        return bgDataCache;
    }

    public static void applyWatermarkToAllPages(PdfDocument pdfDoc) {
        applyWatermarkToAllPages(pdfDoc, 0.15f);
    }

    public static void applyWatermarkToAllPages(PdfDocument pdfDoc, float opacity) {
        ImageData bgData = getBgData();
        if (bgData == null) {
            return;
        }
        int totalPages = pdfDoc.getNumberOfPages();
        for (int i = 1; i <= totalPages; i++) {
            try {
                PdfPage page = pdfDoc.getPage(i);
                Rectangle pageSize = page.getPageSize();
                PdfCanvas underCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);
                PdfExtGState gState = new PdfExtGState();
                gState.setFillOpacity(opacity);
                gState.setStrokeOpacity(opacity);
                underCanvas.saveState();
                underCanvas.setExtGState(gState);
                underCanvas.addImageWithTransformationMatrix(
                        bgData,
                        pageSize.getWidth(), 0,
                        0, pageSize.getHeight(),
                        0, 0,
                        false);
                underCanvas.restoreState();
            } catch (Exception e) {
                log.error("Failed to apply watermark on page {}", i, e);
            }
        }
    }
}
