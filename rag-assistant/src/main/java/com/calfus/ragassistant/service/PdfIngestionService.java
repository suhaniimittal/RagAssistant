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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;


@Service
public class PdfIngestionService {

    private static final int MIN_REAL_TEXT_LENGTH = 20; // below this -> treat page as scanned
    private static final int RENDER_DPI = 200;
    private static final Pattern PAGE_FOOTER_PATTERN =
            Pattern.compile("^\\s*page\\s+\\d+(\\s+of\\s+\\d+)?\\s*", Pattern.CASE_INSENSITIVE);

    private static final double MIN_REAL_WORD_RATIO = 0.5;

    // Above this fraction of tokens being bare numbers, a page reads as an
    // index/table of contents (a run of section titles each followed by its
    // own page number) rather than actual prose -- real paragraphs rarely
    // contain more than the occasional number.
    private static final double MAX_NUMBER_TOKEN_RATIO = 0.25;

    private final String tessdataPath;
    private final String ocrLanguage;

    public PdfIngestionService(
            @Value("${ocr.tessdata-path:tessdata}") String tessdataPath,
            @Value("${ocr.language:eng}") String ocrLanguage,
            @Value("${ocr.native-library-path:}") String nativeLibraryPath) {
        this.tessdataPath = tessdataPath;
        this.ocrLanguage = ocrLanguage;

        if (nativeLibraryPath != null && !nativeLibraryPath.isBlank()) {
            System.setProperty("jna.library.path", nativeLibraryPath);
        }
    }

    public List<String> parseAndCleanPerPage(File pdfFile) throws IOException, TesseractException {
        List<ParsedPage> rawPages = parsePages(pdfFile);

        List<String> cleanedPages = new ArrayList<>(rawPages.size());
        for (ParsedPage rawPage : rawPages) {
            String cleanedText = clean(rawPage.text());
            if (rawPage.fromOcr() && looksLikeGarbledText(cleanedText)) {
                cleanedText = "";
            } else if (looksLikeTableOfContents(cleanedText)) {
                cleanedText = "";
            }
            cleanedPages.add(cleanedText);
        }
        return cleanedPages;
    }


    private record ParsedPage(String text, boolean fromOcr) {
    }

    private List<ParsedPage> parsePages(File pdfFile) throws IOException, TesseractException {
        List<ParsedPage> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            Tesseract tesseract = newTesseract();

            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document);
                boolean fromOcr = false;

                if (pageText.trim().length() < MIN_REAL_TEXT_LENGTH) {
                    // Case 2: no real text layer -> likely a fully scanned page.
                    BufferedImage pageImage = renderer.renderImageWithDPI(i, RENDER_DPI);
                    pageText = tesseract.doOCR(pageImage);
                    fromOcr = true;
                } else {
                    // Case 1: real text layer exists -> also OCR any embedded
                    // images on this page (diagrams, screenshots, etc.) and
                    // append their recovered text.
                    String embeddedImageText = ocrEmbeddedImages(document.getPage(i), tesseract);
                    if (!embeddedImageText.isBlank()) {
                        pageText = pageText + "\n" + embeddedImageText;
                    }
                }

                pages.add(new ParsedPage(pageText, fromOcr));
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
    // Cleaning
    // ---------------------------------------------------------------------

    /** Used for both the whole-document text and each individual page's text. */
    private String clean(String text) {
        text = text.replaceAll("-\\n", "");                  // rejoin hyphenated line breaks
        text = text.replaceAll("\\r\\n|\\r", "\n");            // normalize line endings
        text = removePageNumberFooters(text);                  // strip a leading "Page X of Y" header
        text = text.replaceAll("[ \\t]+", " ");                // collapse repeated spaces/tabs
        text = text.replaceAll("\\n{3,}", "\n\n");             // collapse 3+ blank lines to one
        text = Normalizer.normalize(text, Normalizer.Form.NFKC); // fix stray encoding artifacts
        return text.trim();
    }

    private String removePageNumberFooters(String text) {
        // replaceFirst, not replaceAll: the pattern is anchored with ^ so it
        // can only ever match once anyway (at the very start of the text),
        // but replaceFirst makes that intent explicit.
        return PAGE_FOOTER_PATTERN.matcher(text).replaceFirst("");
    }

    private boolean looksLikeGarbledText(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length == 0) {
            return false;
        }
        long realWords = Arrays.stream(words)
                .filter(word -> word.replaceAll("[^A-Za-z]", "").length() >= 3)
                .count();
        return (double) realWords / words.length < MIN_REAL_WORD_RATIO;
    }

    private boolean looksLikeTableOfContents(String text) {
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length == 0) {
            return false;
        }
        long numberTokens = Arrays.stream(tokens)
                .filter(token -> token.matches("\\d+"))
                .count();
        return (double) numberTokens / tokens.length > MAX_NUMBER_TOKEN_RATIO;
    }
}
