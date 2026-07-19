#!/usr/bin/env python3
"""
Needle Model Tool-Calling Test Suite
Tests the 26M param Needle model against DocDroid tools.
Run: pip install needle && python test_needle.py
Or:  git clone https://github.com/cactus-compute/needle.git && cd needle && source ./setup && python ../test_needle.py
"""

import json
import sys
import time
from dataclasses import dataclass, field
from typing import Optional

# ---------------------------------------------------------------------------
# Needle imports (fail early with clear message)
# ---------------------------------------------------------------------------
try:
    import jax
    from needle import SimpleAttentionNetwork, load_checkpoint, generate, get_tokenizer
except ImportError:
    print("ERROR: needle package not installed.")
    print("  git clone https://github.com/cactus-compute/needle.git")
    print("  cd needle && source ./setup")
    sys.exit(1)

# ---------------------------------------------------------------------------
# CRITICAL CONTEXT LIMIT ANALYSIS
# ---------------------------------------------------------------------------
# Needle encoder context = 1024 tokens
# Encoder input = [query_tokens..., <tools>, tools_tokens...] (truncated to 1024)
# Each tool definition = ~50-150 tokens depending on params
# 23 tools x ~80 tokens avg = ~1840 tokens of tools (EXCEEDS 1024!)
# With a 50-token query, only ~970 tokens of tools fit = ~12 tools visible
#
# SOLUTION: Test with tool subsets to find the model's sweet spot
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Tool definitions — FLAT format (NOT OpenAI) as Needle expects
# ---------------------------------------------------------------------------

ALL_TOOLS = [
    {"name": "pdf_edit", "description": "Merge/split/crop/resize PDF.", "parameters": {
        "operation": {"type": "string", "description": "merge,split,crop,resize", "required": True},
        "input_path": {"type": "string", "description": "PDF path", "required": True},
        "input_paths": {"type": "string", "description": "PDF paths comma-sep", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
        "page_ranges": {"type": "string", "description": "e.g. 1-3,5", "required": False},
        "left": {"type": "string", "description": "pt", "required": False},
        "top": {"type": "string", "description": "pt", "required": False},
        "right": {"type": "string", "description": "pt", "required": False},
        "bottom": {"type": "string", "description": "pt", "required": False},
        "size": {"type": "string", "description": "a4/letter/legal", "required": False},
    }},
    {"name": "pdf_pages", "description": "Extract/delete/reorder/rotate pages.", "parameters": {
        "operation": {"type": "string", "description": "extract,delete,reorder,rotate", "required": True},
        "input_path": {"type": "string", "description": "PDF path", "required": True},
        "pages": {"type": "string", "description": "e.g. 1,3,5 or all", "required": False},
        "new_order": {"type": "string", "description": "e.g. 3,1,2", "required": False},
        "degrees": {"type": "string", "description": "90/180/270", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "pdf_watermark", "description": "Add watermark/numbers/header/footer.", "parameters": {
        "operation": {"type": "string", "description": "text,image,page_numbers,header_footer", "required": True},
        "input_path": {"type": "string", "description": "PDF path", "required": True},
        "text": {"type": "string", "description": "Text", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "pdf_extract", "description": "Extract text/images/tables/metadata.", "parameters": {
        "operation": {"type": "string", "description": "text,images,tables,metadata", "required": True},
        "input_path": {"type": "string", "description": "PDF path", "required": True},
        "pages": {"type": "string", "description": "e.g. 1-5", "required": False},
    }},
    {"name": "pdf_security", "description": "Encrypt/decrypt/flatten PDF.", "parameters": {
        "operation": {"type": "string", "description": "encrypt,decrypt,flatten", "required": True},
        "input_path": {"type": "string", "description": "PDF path", "required": True},
        "password": {"type": "string", "description": "Password", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "pdf_forms", "description": "Fill or create PDF forms.", "parameters": {
        "operation": {"type": "string", "description": "fill,create", "required": True},
        "input_path": {"type": "string", "description": "PDF path", "required": False},
        "fields": {"type": "string", "description": "JSON fields", "required": True},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "pdf_convert", "description": "Convert PDF to/from images/HTML/text.", "parameters": {
        "operation": {"type": "string", "description": "to_images,from_images,from_html,from_text,overlay", "required": True},
        "input_path": {"type": "string", "description": "Input path", "required": False},
        "input_paths": {"type": "string", "description": "Paths comma-sep", "required": False},
        "format": {"type": "string", "description": "png/jpg", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "pdf_info", "description": "Get PDF page count/size/metadata.", "parameters": {
        "input_path": {"type": "string", "description": "PDF path", "required": True},
    }},
    {"name": "image_edit", "description": "Resize/crop/rotate/flip/adjust/filter.", "parameters": {
        "operation": {"type": "string", "description": "resize,crop,rotate,flip,brightness,contrast,saturation,grayscale,sepia,blur,sharpen,invert", "required": True},
        "input_path": {"type": "string", "description": "Image path", "required": True},
        "width": {"type": "string", "description": "px", "required": False},
        "height": {"type": "string", "description": "px", "required": False},
        "degrees": {"type": "string", "description": "Degrees", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "image_overlay", "description": "Add text/image/watermark/border/thumb.", "parameters": {
        "operation": {"type": "string", "description": "text,image,watermark,border,thumbnail", "required": True},
        "input_path": {"type": "string", "description": "Image path", "required": True},
        "overlay_path": {"type": "string", "description": "Overlay image", "required": False},
        "text": {"type": "string", "description": "Text", "required": False},
        "position": {"type": "string", "description": "center/top-left", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "image_convert", "description": "Convert/compress/metadata/create/batch.", "parameters": {
        "operation": {"type": "string", "description": "convert,compress,metadata,strip,dpi,create,batch_resize,batch_convert", "required": True},
        "input_path": {"type": "string", "description": "Image path", "required": False},
        "input_paths": {"type": "string", "description": "Paths comma-sep", "required": False},
        "format": {"type": "string", "description": "png/jpg/webp", "required": False},
        "quality": {"type": "string", "description": "1-100", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": False},
    }},
    {"name": "image_qr", "description": "Generate QR code from text/URL.", "parameters": {
        "content": {"type": "string", "description": "Text/URL", "required": True},
        "size": {"type": "string", "description": "px, default 300", "required": True},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "text_file", "description": "Read/create/find-replace/word-count.", "parameters": {
        "operation": {"type": "string", "description": "read,create,find_replace,word_count", "required": True},
        "input_path": {"type": "string", "description": "Path", "required": False},
        "content": {"type": "string", "description": "Text", "required": False},
        "output_path": {"type": "string", "description": "Path", "required": False},
    }},
    {"name": "docx", "description": "Read/create/edit/merge DOCX or to PDF.", "parameters": {
        "operation": {"type": "string", "description": "read,create,edit,merge,to_pdf,images", "required": True},
        "input_path": {"type": "string", "description": "DOCX path", "required": False},
        "content": {"type": "string", "description": "JSON", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "markdown_to_pdf", "description": "Convert Markdown to PDF.", "parameters": {
        "input_path": {"type": "string", "description": "MD path", "required": True},
        "output_path": {"type": "string", "description": "PDF path", "required": True},
    }},
    {"name": "spreadsheet", "description": "Read/create/edit/sort/merge/convert.", "parameters": {
        "operation": {"type": "string", "description": "read,create,edit_cell,sort,merge,csv_to_xlsx,to_pdf", "required": True},
        "input_path": {"type": "string", "description": "Path", "required": False},
        "input_paths": {"type": "string", "description": "Paths comma-sep", "required": False},
        "sheet": {"type": "string", "description": "Sheet name", "required": False},
        "cell": {"type": "string", "description": "e.g. A1", "required": False},
        "value": {"type": "string", "description": "Value", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "presentation", "description": "Read/create PPTX or to PDF.", "parameters": {
        "operation": {"type": "string", "description": "read,create,to_pdf,info", "required": True},
        "input_path": {"type": "string", "description": "PPTX path", "required": False},
        "slides": {"type": "string", "description": "JSON slides", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "audio", "description": "Get info/trim/convert audio.", "parameters": {
        "operation": {"type": "string", "description": "info,trim,convert", "required": True},
        "input_path": {"type": "string", "description": "Audio path", "required": True},
        "start": {"type": "string", "description": "Start", "required": False},
        "end": {"type": "string", "description": "End", "required": False},
        "format": {"type": "string", "description": "mp3/aac/wav/flac", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": False},
    }},
    {"name": "video", "description": "Get info/trim/extract audio/GIF/thumb.", "parameters": {
        "operation": {"type": "string", "description": "info,trim,extract_audio,to_gif,thumbnail", "required": True},
        "input_path": {"type": "string", "description": "Video path", "required": True},
        "start": {"type": "string", "description": "Start", "required": False},
        "end": {"type": "string", "description": "End", "required": False},
        "duration": {"type": "string", "description": "Sec", "required": False},
        "time": {"type": "string", "description": "Timestamp", "required": False},
        "format": {"type": "string", "description": "mp3/aac/wav", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": True},
    }},
    {"name": "archive", "description": "Create/extract/list ZIP.", "parameters": {
        "operation": {"type": "string", "description": "create,extract,list", "required": True},
        "input_path": {"type": "string", "description": "ZIP path", "required": False},
        "input_paths": {"type": "string", "description": "Paths comma-sep", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": False},
    }},
    {"name": "ocr", "description": "OCR: extract text from image/PDF.", "parameters": {
        "input_path": {"type": "string", "description": "Image or PDF path", "required": True},
    }},
    {"name": "file_info", "description": "Get file type/size/MIME.", "parameters": {
        "input_path": {"type": "string", "description": "File path", "required": True},
    }},
    {"name": "execute_python", "description": "Run Python code (last resort).", "parameters": {
        "code": {"type": "string", "description": "Python code", "required": True},
        "input_files": {"type": "string", "description": "Paths comma-sep", "required": False},
        "output_path": {"type": "string", "description": "Output path", "required": False},
    }},
]

# Minimal 8-tool set: most common doc operations
MINIMAL_TOOLS = [t for t in ALL_TOOLS if t["name"] in [
    "pdf_edit", "pdf_pages", "pdf_extract", "pdf_info",
    "image_edit", "image_convert",
    "text_file", "docx",
]]


@dataclass
class TestResult:
    query: str
    expected_tool: str
    expected_op: Optional[str]
    raw_output: str = ""
    parsed_ok: bool = False
    tool_correct: bool = False
    op_correct: bool = False
    error: Optional[str] = None
    time_ms: float = 0


def parse_needle_output(raw: str) -> Optional[dict]:
    raw = raw.strip()
    if not raw:
        return None
    if raw.startswith("<tool_call>"):
        raw = raw[len("<tool_call>"):]
    if raw.endswith("</tool_call>"):
        raw = raw[:-len("</tool_call>")]
    raw = raw.strip()
    if not raw:
        return None
    try:
        parsed = json.loads(raw)
        if isinstance(parsed, list) and len(parsed) > 0:
            return parsed[0]
        elif isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass
    import re
    match = re.search(r'\{[^{}]*"name"\s*:\s*"[^"]+"[^{}]*\}', raw, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            pass
    return None


def count_tokens(tokenizer, text):
    return len(tokenizer.encode(text))


def run_test(model, params, tokenizer, query, tools_json, expected_tool, expected_op=None, constrained=True) -> TestResult:
    result = TestResult(query=query, expected_tool=expected_tool, expected_op=expected_op)
    start = time.time()
    try:
        raw = generate(model, params, tokenizer, query=query, tools=tools_json, max_gen_len=256, stream=False, constrained=constrained)
    except Exception as e:
        result.error = f"generate() exception: {e}"
        return result
    result.time_ms = (time.time() - start) * 1000
    result.raw_output = raw.strip()
    parsed = parse_needle_output(raw)
    if parsed is None:
        result.error = "Failed to parse output as JSON"
        return result
    result.parsed_ok = True
    name = parsed.get("name", "")
    result.tool_correct = (name == result.expected_tool)
    if result.expected_op:
        args = parsed.get("arguments", {})
        op = args.get("operation", "")
        result.op_correct = (op == result.expected_op)
    else:
        result.op_correct = True
    return result


def main():
    print("=" * 70)
    print("NEEDLE MODEL TOOL-CALLING TEST SUITE")
    print("=" * 70)

    print("\nLoading Needle model (26M params)...")
    try:
        params, config = load_checkpoint("checkpoints/needle.pkl")
    except FileNotFoundError:
        print("ERROR: Model checkpoint not found at checkpoints/needle.pkl")
        print("  Run: needle playground  (auto-downloads weights)")
        sys.exit(1)

    model = SimpleAttentionNetwork(config)
    tokenizer = get_tokenizer()

    max_enc = getattr(config, 'max_enc_len', 1024)
    param_count = sum(x.size for x in jax.tree.leaves(params))
    print(f"Model loaded: {param_count:,} parameters")
    print(f"Encoder context limit: {max_enc} tokens")

    # --- Token budget analysis ---
    all_tools_json = json.dumps(ALL_TOOLS, separators=(",", ":"))
    minimal_tools_json = json.dumps(MINIMAL_TOOLS, separators=(",", ":"))

    all_tokens = count_tokens(tokenizer, all_tools_json)
    min_tokens = count_tokens(tokenizer, minimal_tools_json)

    print(f"\n{'=' * 70}")
    print("TOKEN BUDGET ANALYSIS")
    print(f"{'=' * 70}")
    print(f"  All 23 tools JSON:  {len(all_tools_json):>5} chars, {all_tokens:>4} tokens")
    print(f"  Minimal 8 tools:    {len(minimal_tools_json):>5} chars, {min_tokens:>4} tokens")
    print(f"  Encoder capacity:   {max_enc:>5} tokens")
    print(f"  Query overhead:     ~30-80 tokens")
    print(f"  Available for tools: ~{max_enc - 80} tokens")
    print()
    if all_tokens > max_enc - 80:
        print(f"  CRITICAL: 23 tools ({all_tokens} tokens) EXCEEDS encoder limit ({max_enc - 80} available)")
        print(f"  The model can only see ~{((max_enc - 80) / all_tokens * 100):.0f}% of all tools!")
        print(f"  RECOMMENDATION: Use tool RAG (top_k) or reduce to ~{max_enc // 80} tools")
    if min_tokens <= max_enc - 80:
        print(f"  OK: 8 tools ({min_tokens} tokens) fits within encoder limit")

    # Per-tool token counts
    print(f"\n  Per-tool token counts:")
    for tool in ALL_TOOLS:
        tj = json.dumps([tool], separators=(",", ":"))
        tt = count_tokens(tokenizer, tj)
        marker = " <<<" if tt > 80 else ""
        print(f"    {tool['name']:20s}: {tt:>3} tokens{marker}")

    # --- Test Suite ---
    all_tests = [
        ("Merge /tmp/a.pdf and /tmp/b.pdf into /tmp/merged.pdf", "pdf_edit", "merge"),
        ("Split /tmp/doc.pdf into individual pages", "pdf_pages", "extract"),
        ("Add a DRAFT watermark to /tmp/report.pdf", "pdf_watermark", "text"),
        ("Extract all text from /tmp/report.pdf", "pdf_extract", "text"),
        ("Encrypt /tmp/secret.pdf with password mypass123", "pdf_security", "encrypt"),
        ("Fill in the form fields of /tmp/form.pdf", "pdf_forms", "fill"),
        ("Convert /tmp/doc.pdf to PNG images", "pdf_convert", "to_images"),
        ("How many pages does /tmp/report.pdf have?", "pdf_info", None),
        ("Resize /tmp/photo.jpg to 800x600", "image_edit", "resize"),
        ("Add text overlay saying Hello on /tmp/photo.jpg", "image_overlay", "text"),
        ("Convert /tmp/photo.png to JPEG format", "image_convert", "convert"),
        ("Generate a QR code for https://example.com", "image_qr", None),
        ("Read the contents of /tmp/notes.txt", "text_file", "read"),
        ("Read /tmp/report.docx and extract its text", "docx", "read"),
        ("Convert /tmp/readme.md to PDF", "markdown_to_pdf", None),
        ("Read /tmp/data.xlsx and show me the contents", "spreadsheet", "read"),
        ("Create a presentation with 3 slides", "presentation", "create"),
        ("Get info about /tmp/song.mp3", "audio", "info"),
        ("Trim /tmp/video.mp4 from 00:10 to 00:30", "video", "trim"),
        ("Create a ZIP archive of /tmp/files", "archive", "create"),
        ("Extract text from this image using OCR", "ocr", None),
        ("What type of file is /tmp/document.pdf?", "file_info", None),
        ("Run Python code to calculate the sum of 1+1", "execute_python", None),
    ]

    # --- Phase 1: Test with ALL 23 tools ---
    print(f"\n{'=' * 70}")
    print("PHASE 1: Testing with ALL 23 tools (expect overflow)")
    print(f"{'=' * 70}")

    results_all = []
    for i, (query, tool, op) in enumerate(all_tests):
        sys.stdout.write(f"\r  [{i+1}/{len(all_tests)}] {tool:20s}")
        sys.stdout.flush()
        r = run_test(model, params, tokenizer, query, all_tools_json, tool, op, constrained=True)
        results_all.append(r)
        sym = "PASS" if (r.tool_correct and r.op_correct) else ("ERR " if r.error else "FAIL")
        sys.stdout.write(f" {sym} ({r.time_ms:.0f}ms)  ")
        sys.stdout.flush()

    tool_correct_all = sum(1 for r in results_all if r.tool_correct)
    op_correct_all = sum(1 for r in results_all if r.op_correct and r.tool_correct)
    errors_all = sum(1 for r in results_all if r.error)
    print(f"\n  Results: {tool_correct_all}/{len(all_tests)} tool correct, {errors_all} errors")

    # --- Phase 2: Test with MINIMAL 8 tools ---
    print(f"\n{'=' * 70}")
    print("PHASE 2: Testing with MINIMAL 8 tools (should fit context)")
    print(f"{'=' * 70}")

    minimal_tests = [t for t in all_tests if t[1] in [tool["name"] for tool in MINIMAL_TOOLS]]

    results_min = []
    for i, (query, tool, op) in enumerate(minimal_tests):
        sys.stdout.write(f"\r  [{i+1}/{len(minimal_tests)}] {tool:20s}")
        sys.stdout.flush()
        r = run_test(model, params, tokenizer, query, minimal_tools_json, tool, op, constrained=True)
        results_min.append(r)
        sym = "PASS" if (r.tool_correct and r.op_correct) else ("ERR " if r.error else "FAIL")
        sys.stdout.write(f" {sym} ({r.time_ms:.0f}ms)  ")
        sys.stdout.flush()

    tool_correct_min = sum(1 for r in results_min if r.tool_correct)
    errors_min = sum(1 for r in results_min if r.error)
    print(f"\n  Results: {tool_correct_min}/{len(minimal_tests)} tool correct, {errors_min} errors")

    # --- Phase 3: Test WITHOUT constrained decoding ---
    print(f"\n{'=' * 70}")
    print("PHASE 3: Minimal tools WITHOUT constrained decoding")
    print(f"{'=' * 70}")

    results_uncon = []
    for i, (query, tool, op) in enumerate(minimal_tests):
        sys.stdout.write(f"\r  [{i+1}/{len(minimal_tests)}] {tool:20s}")
        sys.stdout.flush()
        r = run_test(model, params, tokenizer, query, minimal_tools_json, tool, op, constrained=False)
        results_uncon.append(r)
        sym = "PASS" if (r.tool_correct and r.op_correct) else ("ERR " if r.error else "FAIL")
        sys.stdout.write(f" {sym} ({r.time_ms:.0f}ms)  ")
        sys.stdout.flush()

    tool_correct_uncon = sum(1 for r in results_uncon if r.tool_correct)
    errors_uncon = sum(1 for r in results_uncon if r.error)
    print(f"\n  Results: {tool_correct_uncon}/{len(minimal_tests)} tool correct, {errors_uncon} errors")

    # --- Summary ---
    print(f"\n{'=' * 70}")
    print("SUMMARY")
    print(f"{'=' * 70}")
    print(f"\n  Encoder context: {max_enc} tokens")
    print(f"  All 23 tools:    {all_tokens} tokens (>{max_enc - 80} available = OVERFLOW)")
    print(f"  Minimal 8 tools: {min_tokens} tokens (fits)")
    print()
    print(f"  Phase 1 (23 tools, constrained):  {tool_correct_all}/{len(all_tests)} correct ({100*tool_correct_all/len(all_tests):.0f}%)")
    print(f"  Phase 2 (8 tools, constrained):   {tool_correct_min}/{len(minimal_tests)} correct ({100*tool_correct_min/len(minimal_tests):.0f}%)")
    print(f"  Phase 3 (8 tools, unconstrained): {tool_correct_uncon}/{len(minimal_tests)} correct ({100*tool_correct_uncon/len(minimal_tests):.0f}%)")
    print()

    # --- Print all failures ---
    all_failures = [(r, "Phase1-23tools") for r in results_all if not (r.tool_correct and r.op_correct)] + \
                   [(r, "Phase2-8tools") for r in results_min if not (r.tool_correct and r.op_correct)] + \
                   [(r, "Phase3-unconstrained") for r in results_uncon if not (r.tool_correct and r.op_correct)]
    if all_failures:
        print(f"\n{'=' * 70}")
        print("FAILURES DETAIL")
        print(f"{'=' * 70}")
        for r, phase in all_failures:
            print(f"\n  [{phase}] Q: {r.query}")
            print(f"  Expected: {r.expected_tool}" + (f"/{r.expected_op}" if r.expected_op else ""))
            if r.error:
                print(f"  Error: {r.error}")
            else:
                print(f"  Got: {r.raw_output[:150]}")

    # --- Verdict ---
    print(f"\n{'=' * 70}")
    score = 100 * tool_correct_min / len(minimal_tests) if minimal_tests else 0
    if score >= 80:
        print(f"VERDICT: Model works ({score:.0f}% with 8 tools)")
        print(f"  -> Fix Android: use tool_rag_top_k=2 to select relevant tools per query")
        print(f"  -> Use FLAT tool format: [{{\"name\":..., \"parameters\":{{...}}}}]")
        print(f"  -> NOT OpenAI format with type:function wrapper")
    elif score >= 50:
        print(f"VERDICT: Model partial ({score:.0f}% with 8 tools)")
        print(f"  -> Try fewer tools (4-5) or finetune on your tool set")
    else:
        print(f"VERDICT: Model struggling ({score:.0f}%)")
        print(f"  -> Need to finetune or use a different model")
    print("=" * 70)


if __name__ == "__main__":
    main()
