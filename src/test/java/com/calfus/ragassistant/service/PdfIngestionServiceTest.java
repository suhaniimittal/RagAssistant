package com.calfus.ragassistant.service;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standalone verification for Step 3: run parse+clean on the sample test
 * PDF (which deliberately contains a real-text page, a hyphen split across
 * a line, a repeated footer, a page with an embedded image, and a fully
 * scanned page) and check the output looks correct.
 */
class PdfIngestionServiceTest {

    private final PdfIngestionService service =
            new PdfIngestionService("tessdata", "eng");

    @Test
    void parseAndClean_producesCleanMergedText() throws Exception {
        File pdf = new File("sample-pdfs/HR_Policy_Test.pdf");
        assertTrue(pdf.exists(), "Sample PDF must exist - run generate_sample_pdf.py first");

        String result = service.parseAndClean(pdf);

        System.out.println("=========== CLEANED OUTPUT START ===========");
        System.out.println(result);
        System.out.println("=========== CLEANED OUTPUT END =============");

        // Real text from page 1 and 2 should be present
        assertTrue(result.contains("Employment Basics"));
        assertTrue(result.contains("Leave Policy"));

        // Hyphenated words split across lines should be rejoined
        assertTrue(result.contains("information"), "Expected 'informa-' + 'tion' to be rejoined into 'information'");
        assertTrue(result.contains("compensation"), "Expected 'compen-' + 'sation' to be rejoined into 'compensation'");

        // Repeated footer should be stripped (it appears on 3 real-text pages -> boilerplate)
        assertFalse(result.contains("Confidential"), "Repeated footer should have been removed as boilerplate");

        // OCR of the embedded diagram image on page 3 should recover its text
        assertTrue(result.contains("Application Review") || result.contains("Phone Screen"),
                "Expected OCR to recover text from the embedded diagram image");

        // OCR of the fully scanned page 4 should recover its text
        assertTrue(result.contains("Acknowledgement") || result.contains("Signature"),
                "Expected full-page OCR to recover text from the scanned page");
    }
}
