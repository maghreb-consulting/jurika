#!/usr/bin/env python
"""Extract text from .doc/.docx for analysis. Supports both formats."""
import sys
import os
import re
import zipfile
import struct

def extract_docx(path):
    try:
        import docx2txt
        return docx2txt.process(path)
    except Exception as e:
        # Fallback : raw XML
        try:
            with zipfile.ZipFile(path) as z:
                with z.open("word/document.xml") as f:
                    xml = f.read().decode("utf-8", errors="ignore")
                # Crude text extract
                text = re.sub(r"<[^>]+>", " ", xml)
                text = re.sub(r"\s+", " ", text)
                return text
        except Exception as e2:
            return f"[error: {e}, {e2}]"

def extract_doc(path):
    """Crude .doc text extraction by reading raw bytes."""
    try:
        with open(path, "rb") as f:
            data = f.read()
        # WordDocument stream is binary, but text segments are extractable
        # by looking for printable ASCII/UTF-16 sequences
        chunks = []
        # Try UTF-16
        try:
            utf16_text = data.decode("utf-16-le", errors="ignore")
            for line in re.split(r"[\x00-\x1f]+", utf16_text):
                if len(line) > 20 and re.search(r"[a-zA-Z]{5,}", line):
                    chunks.append(line.strip())
        except Exception:
            pass
        # Also try UTF-8
        utf8_text = data.decode("latin-1", errors="ignore")
        for line in re.split(r"[\x00-\x08\x0b\x0c\x0e-\x1f]+", utf8_text):
            if len(line) > 30 and re.search(r"\b[a-zA-Z]{5,}\b", line):
                chunks.append(line.strip())
        # Dedupe and clean
        seen = set()
        out = []
        for c in chunks:
            c = re.sub(r"[^\x20-\x7e\xa0-\xff\n\r]", "", c)
            c = re.sub(r"\s+", " ", c).strip()
            if len(c) < 30: continue
            if c in seen: continue
            seen.add(c)
            out.append(c)
        return "\n".join(out)
    except Exception as e:
        return f"[error: {e}]"

if __name__ == "__main__":
    path = sys.argv[1]
    ext = os.path.splitext(path)[1].lower()
    if ext == ".docx":
        print(extract_docx(path))
    elif ext == ".doc":
        print(extract_doc(path))
    else:
        print("Unsupported extension: " + ext)
