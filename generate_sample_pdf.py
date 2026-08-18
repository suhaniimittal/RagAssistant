"""
Generates one test PDF that deliberately exercises every path the
PdfIngestionService needs to handle:

  Page 1 - normal digital text, includes a hyphenated word split across
           a line, plus the repeated footer (to test boilerplate removal).
  Page 2 - normal digital text, same repeated footer (2nd occurrence).
  Page 3 - normal digital text PLUS one embedded image containing its own
           text (simulates a diagram/screenshot) -> tests "Case 1": real
           text layer + OCR on an embedded image. Same repeated footer.
  Page 4 - the ENTIRE page is one rendered image with no real text layer
           at all -> tests "Case 2": fully scanned page, full-page OCR
           fallback.

Run: python3 generate_sample_pdf.py
Output: sample-pdfs/HR_Policy_Test.pdf
"""
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import letter
from PIL import Image, ImageDraw, ImageFont
import os

OUT_DIR = "sample-pdfs"
os.makedirs(OUT_DIR, exist_ok=True)
PDF_PATH = os.path.join(OUT_DIR, "HR_Policy_Test.pdf")
FOOTER = "Company Confidential -- Internal Use Only"

WIDTH, HEIGHT = letter


def draw_footer(c):
    c.setFont("Helvetica", 8)
    c.drawCentredString(WIDTH / 2, 30, FOOTER)


def make_text_image(path, lines, size=(700, 220), font_size=20):
    """Render plain text onto a PNG image, to simulate a diagram/scan."""
    img = Image.new("RGB", size, color="white")
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", font_size)
    except Exception:
        font = ImageFont.load_default()
    y = 15
    for line in lines:
        draw.text((15, y), line, fill="black", font=font)
        y += font_size + 12
    img.save(path)
    return path


c = canvas.Canvas(PDF_PATH, pagesize=letter)

# ---------------- Page 1 ----------------
c.setFont("Helvetica-Bold", 16)
c.drawString(72, 740, "HR Policy Handbook -- Section 1: Employment Basics")
c.setFont("Helvetica", 11)
text_lines_p1 = [
    "This document describes the standard employment policies applicable to all",
    "full-time employees. Every new hire receives a copy of this handbook during",
    "the onboarding process and is expected to review it within the first week of",
    "employment. Employees should direct questions about any of the informa-",
    "tion contained in this handbook to the Human Resources department at",
    "hr@company.com. Failure to follow the policies outlined here may result in",
    "disciplinary action as described in Section 8.",
]
y = 700
for line in text_lines_p1:
    c.drawString(72, y, line)
    y -= 18
draw_footer(c)
c.showPage()

# ---------------- Page 2 ----------------
c.setFont("Helvetica-Bold", 16)
c.drawString(72, 740, "Section 2: Leave Policy")
c.setFont("Helvetica", 11)
text_lines_p2 = [
    "Employees accrue paid leave based on their length of service. Casual leave",
    "and sick leave are credited at the start of each calendar year, while earned",
    "leave accrues monthly. Any unused earned leave may be carried forward to",
    "the next year, subject to a maximum cap as defined by the compen-",
    "sation team. Requests for leave must be submitted through the internal",
    "portal at least three working days in advance, except in emergencies.",
]
y = 700
for line in text_lines_p2:
    c.drawString(72, y, line)
    y -= 18
draw_footer(c)
c.showPage()

# ---------------- Page 3 (real text + embedded image) ----------------
c.setFont("Helvetica-Bold", 16)
c.drawString(72, 740, "Section 3: Interview Process Overview")
c.setFont("Helvetica", 11)
c.drawString(72, 710, "The diagram below summarizes the standard interview pipeline used by all")
c.drawString(72, 694, "hiring managers when evaluating a candidate for an open role.")

diagram_path = make_text_image(
    os.path.join(OUT_DIR, "_diagram.png"),
    ["STEP 1: Application Review", "STEP 2: Phone Screen", "STEP 3: Technical Interview", "STEP 4: Offer"],
)
c.drawImage(diagram_path, 90, 480, width=430, height=160, preserveAspectRatio=True)
draw_footer(c)
c.showPage()

# ---------------- Page 4 (fully scanned page, no real text layer) ----------------
scan_lines = [
    "Section 4: Acknowledgement Form (Scanned Copy)",
    "",
    "I acknowledge that I have received and read the HR Policy",
    "Handbook in full, and I agree to comply with all policies",
    "described within it during the course of my employment.",
    "",
    "Signature: ______________________     Date: ____________",
]
scan_path = make_text_image(
    os.path.join(OUT_DIR, "_scan.png"),
    scan_lines,
    size=(900, 500),
    font_size=22,
)
c.drawImage(scan_path, 0, 0, width=WIDTH, height=HEIGHT, preserveAspectRatio=False)
c.showPage()

c.save()
print(f"Wrote {PDF_PATH}")
