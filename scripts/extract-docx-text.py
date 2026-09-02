#!/usr/bin/env python3
"""Extract plain text from .docx files for audit purposes."""
import sys, re, zipfile, os, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

def docx_to_text(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read('word/document.xml').decode('utf-8')
    # Replace paragraph breaks with newlines, then strip tags
    xml = re.sub(r'</w:p\s*>', '\n', xml)
    xml = re.sub(r'<w:br/?>', '\n', xml)
    xml = re.sub(r'<[^>]+>', '', xml)
    # Decode XML entities
    xml = xml.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>').replace('&quot;', '"').replace('&apos;', "'")
    # Collapse only ascii spaces, not non-breaking space
    return xml

if __name__ == '__main__':
    for p in sys.argv[1:]:
        print(f"==================== {os.path.basename(p)} ====================")
        print(docx_to_text(p))
        print()
