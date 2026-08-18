package com.calfus.ragassistant.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step 1-3 of the ingestion pipeline: PDF parsing (with OCR fallback for
 * scanned pages and embedded images) followed by document cleaning.
 *
 * Design decisions this class implements (per the agreed architecture):
 *  - Pages are read in a loop internally (so we can detect/OCR images per
 *    page). Two output shapes are available: parseAndClean() merges every
 *    page into ONE string (kept for the original whole-document use case and
 *    its test), and parseAndCleanPerPage() keeps pages separate so callers
 *    that need to cite a page number (e.g. chunk -> source citation) can.
 *  - Per page: PDFTextStripper extracts the real text layer, AND every
 *    embedded image on that page is OCR'd separately and appended.
 *  - If a page's text layer is empty/near-empty, it's treated as a fully
 *    scanned page: the whole page is rendered as an image and OCR'd instead.
 *  - Boilerplate detection (repeated headers/footers) always looks at every
 *    page's lines together to know what "repeats", even in the per-page
 *    variant - only the removal step is applied per page there, so page
 *    boundaries survive cleaning.
 */
@Service
public class PdfIngestionService {

    private static final int MIN_REAL_TEXT_LENGTH = 20; // below this -> treat page as scanned
    private static final int RENDER_DPI = 200;
    private static final int BOILERPLATE_MIN_REPEATS = 3; // line seen 3+ times -> likely header/footer
    private static final int BOILERPLATE_MAX_LINE_LENGTH = 80; // only short lines are boilerplate candidates

    private final String tessdataPath;
    private final String ocrLanguage;

    public PdfIngestionService(
            @Value("${ocr.tessdata-path:tessdata}") String tessdataPath,
            @Value("${ocr.language:eng}") String ocrLanguage) {
        this.tessdataPath = tessdataPath;
        this.ocrLanguage = ocrLanguage;
    }

    /**
     * Full Step 1-3 entry point: parse the given PDF (with OCR fallback) and
     * return the cleaned, whole-document text ready for chunking. Use this
     * when you don't need to know which page a piece of text came from.
     */
    public String parseAndClean(File pdfFile) throws IOException, TesseractException {
        String rawText = String.join("\n", parsePages(pdfFile));
        return clean(rawText);
    }

    /**
     * Same parsing + cleaning as parseAndClean(), but keeps pages separate
     * instead of merging them - index 0 is page 1, index 1 is page 2, etc.
     * Use this when downstream chunks need to cite a page number.
     */
    public List<String> parseAndCleanPerPage(File pdfFile) throws IOException, TesseractException {
        List<String> rawPages = parsePages(pdfFile);
        Set<String> boilerplate = computeBoilerplateLines(rawPages);

        List<String> cleanedPages = new ArrayList<>(rawPages.size());
        for (String rawPage : rawPages) {
            cleanedPages.add(cleanSinglePage(rawPage, boilerplate));
        }
        return cleanedPages;
    }

    // ---------------------------------------------------------------------
    // Parsing (text extraction + OCR)
    // ---------------------------------------------------------------------

    private List<String> parsePages(File pdfFile) throws IOException, TesseractException {
        List<String> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            Tesseract tesseract = newTesseract();

            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document);

                if (pageText.trim().length() < MIN_REAL_TEXT_LENGTH) {
                    // Case 2: no real text layer -> likely a fully scanned page.
                    BufferedImage pageImage = renderer.renderImageWithDPI(i, RENDER_DPI);
                    pageText = tesseract.doOCR(pageImage);
                } else {
                    // Case 1: real text layer exists -> also OCR any embedded
                    // images on this page (diagrams, screenshots, etc.) and
                    // append their recovered text.
                    String embeddedImageText = ocrEmbeddedImages(document.getPage(i), tesseract);
                    if (!embeddedImageText.isBlank()) {
                        pageText = pageText + "\n" + embeddedImageText;
                    }
                }

                pages.add(pageText);
            }
        }

        return pages;
    }

    private String ocrEmbeddedImages(PDPage page, Tesseract tesseract) throws TesseractException, IOException {
        StringBuilder result = new StringBuilder();
        PDResources resources = page.getResources();
        if (resources == null) {
            return "";
        }

        for (COSName name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDImageXObject imageXObject) {
                String ocrText = tesseract.doOCR(imageXObject.getImage());
                if (!ocrText.isBlank()) {
                    result.append(ocrText).append("\n");
                }
            }
        }
        return result.toString();
    }

    private Tesseract newTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(ocrLanguage);
        return tesseract;
    }

    // ---------------------------------------------------------------------
    // Cleaning (runs once, on the whole merged document)
    // ---------------------------------------------------------------------

    private String clean(String text) {
        Set<String> boilerplate = computeBoilerplateLines(List.of(text));
        text = text.replaceAll("-\\n", "");                  // rejoin hyphenated line breaks
        text = text.replaceAll("\\r\\n|\\r", "\n");            // normalize line endings
        text = removeLines(text, boilerplate);                  // strip boilerplate headers/footers
        text = text.replaceAll("[ \\t]+", " ");                // collapse repeated spaces/tabs
        text = text.replaceAll("\\n{3,}", "\n\n");             // collapse 3+ blank lines to one
        text = Normalizer.normalize(text, Normalizer.Form.NFKC); // fix stray encoding artifacts
        return text.trim();
    }

    /** Cleaning for one page, given a boilerplate set already computed across ALL pages. */
    private String cleanSinglePage(String text, Set<String> boilerplate) {
        text = text.replaceAll("-\\n", "");
        text = text.replaceAll("\\r\\n|\\r", "\n");
        text = removeLines(text, boilerplate);
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return text.trim();
    }

    /**
     * Finds lines that repeat often enough (across the given page(s)) to be
     * a header/footer, e.g. "Confidential - Internal Use Only" showing up on
     * every page. Plain-English steps:
     *   1. Count how many times each short line appears, across all pages.
     *   2. Keep only the lines that appeared BOILERPLATE_MIN_REPEATS times
     *      or more -- those are the ones we'll treat as boilerplate.
     */
    private Set<String> computeBoilerplateLines(List<String> pages) {
        Map<String, Integer> lineCounts = new HashMap<>();

        for (String page : pages) {
            String[] linesOnThisPage = page.split("\n");
            for (String line : linesOnThisPage) {
                String trimmedLine = line.trim();
                boolean tooLongToBeBoilerplate = trimmedLine.length() >= BOILERPLATE_MAX_LINE_LENGTH;
                if (trimmedLine.isEmpty() || tooLongToBeBoilerplate) {
                    continue; // skip blank lines and long lines -- boilerplate is usually short
                }

                int countSoFar = lineCounts.getOrDefault(trimmedLine, 0);
                lineCounts.put(trimmedLine, countSoFar + 1);
            }
        }

        Set<String> boilerplateLines = new HashSet<>();
        for (Map.Entry<String, Integer> entry : lineCounts.entrySet()) {
            String line = entry.getKey();
            int timesSeen = entry.getValue();
            if (timesSeen >= BOILERPLATE_MIN_REPEATS) {
                boilerplateLines.add(line);
            }
        }
        return boilerplateLines;
    }

    private String removeLines(String text, Set<String> boilerplate) {
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\n")) {
            if (!boilerplate.contains(line.trim())) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }
}