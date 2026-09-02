#!/usr/bin/env python3
"""
Generate Guide_Onboarding_Cabinet.pdf from the markdown source.

Sprint 3 / TASK 5 — converts the JURIKA onboarding guide into a styled
A4 PDF for direct distribution to client law firms.

Dependencies: reportlab >= 4.x
Usage:
    python scripts/build_onboarding_pdf.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "v2" / "Guide_Onboarding_Cabinet.md"
DST = ROOT / "docs" / "v2" / "Guide_Onboarding_Cabinet.pdf"

BRAND_BLUE = HexColor("#2563EB")
DARK = HexColor("#0F172A")
MUTED = HexColor("#64748B")
AMBER = HexColor("#92400E")
AMBER_BG = HexColor("#FEF3C7")
RED = HexColor("#7F1D1D")
RED_BG = HexColor("#FEF2F2")


def make_styles() -> dict:
    base = getSampleStyleSheet()
    body = ParagraphStyle(
        "body",
        parent=base["BodyText"],
        fontName="Helvetica",
        fontSize=10.5,
        leading=15,
        spaceAfter=6,
        textColor=DARK,
    )
    h1 = ParagraphStyle(
        "h1",
        parent=base["Heading1"],
        fontName="Helvetica-Bold",
        fontSize=20,
        leading=24,
        spaceBefore=18,
        spaceAfter=10,
        textColor=BRAND_BLUE,
    )
    h2 = ParagraphStyle(
        "h2",
        parent=base["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=14,
        leading=18,
        spaceBefore=16,
        spaceAfter=6,
        textColor=BRAND_BLUE,
    )
    h3 = ParagraphStyle(
        "h3",
        parent=base["Heading3"],
        fontName="Helvetica-Bold",
        fontSize=11.5,
        leading=15,
        spaceBefore=10,
        spaceAfter=4,
        textColor=DARK,
    )
    quote = ParagraphStyle(
        "quote",
        parent=body,
        leftIndent=14,
        rightIndent=4,
        fontSize=10,
        textColor=MUTED,
        borderColor=BRAND_BLUE,
        borderPadding=(8, 8, 8, 8),
        backColor=HexColor("#F8FAFC"),
    )
    warning = ParagraphStyle(
        "warning",
        parent=body,
        leftIndent=10,
        backColor=AMBER_BG,
        textColor=AMBER,
        borderPadding=(8, 8, 8, 8),
        fontSize=10,
    )
    danger = ParagraphStyle(
        "danger",
        parent=body,
        leftIndent=10,
        backColor=RED_BG,
        textColor=RED,
        borderPadding=(8, 8, 8, 8),
        fontSize=10,
    )
    code = ParagraphStyle(
        "code",
        parent=body,
        fontName="Courier",
        fontSize=9,
        leading=12,
        leftIndent=12,
        backColor=HexColor("#F1F5F9"),
        borderPadding=(6, 6, 6, 6),
        textColor=DARK,
    )
    bullet = ParagraphStyle(
        "bullet",
        parent=body,
        leftIndent=18,
        bulletIndent=4,
        spaceAfter=2,
    )
    return {
        "body": body,
        "h1": h1,
        "h2": h2,
        "h3": h3,
        "quote": quote,
        "warning": warning,
        "danger": danger,
        "code": code,
        "bullet": bullet,
    }


def inline_md_to_html(text: str) -> str:
    """Convert minimal markdown inline syntax to ReportLab's mini-HTML."""
    # Escape XML entities first (but keep markdown markers)
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # Bold **
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    # Italic *
    text = re.sub(r"(?<!\*)\*([^*\n]+?)\*(?!\*)", r"<i>\1</i>", text)
    # Inline code `
    text = re.sub(
        r"`([^`]+?)`",
        r'<font face="Courier" color="#0F172A" backColor="#F1F5F9">\1</font>',
        text,
    )
    # Links [text](url)
    text = re.sub(
        r"\[([^\]]+)\]\(([^)]+)\)",
        r'<link href="\2" color="#2563EB"><u>\1</u></link>',
        text,
    )
    return text


def parse_table(lines: list[str], i: int) -> tuple[Table, int]:
    """Parse a GitHub-style markdown table starting at line i. Returns (Table, next_i)."""
    header = [c.strip() for c in lines[i].strip().strip("|").split("|")]
    # i+1 is the alignment row
    rows = [header]
    j = i + 2
    while j < len(lines) and lines[j].strip().startswith("|"):
        cells = [c.strip() for c in lines[j].strip().strip("|").split("|")]
        rows.append(cells)
        j += 1

    # Render inline markdown in cells -> Paragraphs
    style = ParagraphStyle(
        "td", fontName="Helvetica", fontSize=9, leading=12, textColor=DARK
    )
    head_style = ParagraphStyle(
        "th",
        parent=style,
        fontName="Helvetica-Bold",
        textColor=BRAND_BLUE,
    )
    rendered = []
    for r_idx, row in enumerate(rows):
        rendered_row = []
        for cell in row:
            s = head_style if r_idx == 0 else style
            rendered_row.append(Paragraph(inline_md_to_html(cell), s))
        rendered.append(rendered_row)

    n_cols = len(rendered[0])
    avail_width = A4[0] - 4 * cm
    col_width = avail_width / n_cols
    tbl = Table(rendered, colWidths=[col_width] * n_cols, repeatRows=1)
    tbl.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), HexColor("#F1F5F9")),
                ("BOX", (0, 0), (-1, -1), 0.5, HexColor("#CBD5E1")),
                ("INNERGRID", (0, 0), (-1, -1), 0.25, HexColor("#E2E8F0")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    return tbl, j


def build_story(md_text: str, styles: dict) -> list:
    story: list = []
    lines = md_text.splitlines()
    i = 0
    in_code_block = False
    code_lines: list[str] = []

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # Code fence
        if stripped.startswith("```"):
            if in_code_block:
                if code_lines:
                    text = "<br/>".join(
                        s.replace(" ", "&nbsp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        for s in code_lines
                    )
                    story.append(Paragraph(text, styles["code"]))
                    story.append(Spacer(1, 4))
                code_lines = []
                in_code_block = False
            else:
                in_code_block = True
            i += 1
            continue
        if in_code_block:
            code_lines.append(line)
            i += 1
            continue

        # Blank line
        if not stripped:
            story.append(Spacer(1, 4))
            i += 1
            continue

        # Horizontal rule
        if re.match(r"^-{3,}$", stripped):
            story.append(Spacer(1, 6))
            i += 1
            continue

        # Headings
        if stripped.startswith("# "):
            text = stripped[2:].strip()
            story.append(Paragraph(inline_md_to_html(text), styles["h1"]))
            i += 1
            continue
        if stripped.startswith("## "):
            text = stripped[3:].strip()
            story.append(Paragraph(inline_md_to_html(text), styles["h2"]))
            i += 1
            continue
        if stripped.startswith("### "):
            text = stripped[4:].strip()
            story.append(Paragraph(inline_md_to_html(text), styles["h3"]))
            i += 1
            continue

        # Tables
        if stripped.startswith("|") and i + 1 < len(lines) and re.match(
            r"^\|[\s:|-]+\|$", lines[i + 1].strip()
        ):
            tbl, next_i = parse_table(lines, i)
            story.append(Spacer(1, 4))
            story.append(tbl)
            story.append(Spacer(1, 8))
            i = next_i
            continue

        # Blockquotes / callouts (lines starting with >)
        if stripped.startswith(">"):
            block: list[str] = []
            while i < len(lines) and lines[i].lstrip().startswith(">"):
                content = lines[i].lstrip()[1:].lstrip()
                block.append(content)
                i += 1
            text = " ".join(block)
            style = styles["quote"]
            if text.startswith("⚠️") or text.startswith("⚠"):
                style = styles["warning"]
            elif "phishing" in text.lower() or "danger" in text.lower():
                style = styles["danger"]
            story.append(Paragraph(inline_md_to_html(text), style))
            story.append(Spacer(1, 4))
            continue

        # Numbered list (1. 2. ...)
        m = re.match(r"^(\d+)\.\s+(.*)$", stripped)
        if m:
            num, content = m.group(1), m.group(2)
            story.append(
                Paragraph(
                    f"<b>{num}.</b> {inline_md_to_html(content)}",
                    styles["bullet"],
                )
            )
            i += 1
            continue

        # Bullet list
        if stripped.startswith("- ") or stripped.startswith("* "):
            content = stripped[2:]
            story.append(
                Paragraph(
                    f"&bull; {inline_md_to_html(content)}",
                    styles["bullet"],
                )
            )
            i += 1
            continue

        # Paragraph (may span multiple lines until blank)
        para_lines = [stripped]
        i += 1
        while (
            i < len(lines)
            and lines[i].strip()
            and not lines[i].lstrip().startswith(("#", ">", "- ", "* ", "|", "```"))
            and not re.match(r"^\d+\.\s+", lines[i].lstrip())
            and not re.match(r"^-{3,}$", lines[i].strip())
        ):
            para_lines.append(lines[i].strip())
            i += 1
        text = " ".join(para_lines)
        story.append(Paragraph(inline_md_to_html(text), styles["body"]))

    return story


def page_header_footer(canvas, doc):
    canvas.saveState()
    # Footer
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(MUTED)
    canvas.drawString(
        2 * cm, 1.2 * cm, "JURIKA — Maghreb Consulting — support@jurika.ma"
    )
    canvas.drawRightString(
        A4[0] - 2 * cm, 1.2 * cm, f"Page {doc.page}"
    )
    canvas.restoreState()


def main() -> int:
    if not SRC.exists():
        print(f"Source not found: {SRC}", file=sys.stderr)
        return 1

    md = SRC.read_text(encoding="utf-8")
    styles = make_styles()
    story = build_story(md, styles)

    doc = SimpleDocTemplate(
        str(DST),
        pagesize=A4,
        leftMargin=2 * cm,
        rightMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=2 * cm,
        title="JURIKA — Guide d'onboarding cabinet",
        author="Maghreb Consulting",
        subject="Activation de votre compte JURIKA",
    )
    doc.build(story, onFirstPage=page_header_footer, onLaterPages=page_header_footer)
    print(f"OK -> {DST}  ({DST.stat().st_size // 1024} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
