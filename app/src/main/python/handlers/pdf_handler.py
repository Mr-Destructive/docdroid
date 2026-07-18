import json
import os
from pypdf import PdfReader, PdfWriter
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4, letter
from reportlab.lib.units import inch


def merge_pdfs(args_json):
    args = json.loads(args_json)
    input_paths = [p.strip() for p in args["input_paths"].split(",")]
    output_path = args["output_path"]
    writer = PdfWriter()
    for path in input_paths:
        reader = PdfReader(path)
        for page in reader.pages:
            writer.add_page(page)
    with open(output_path, "wb") as f:
        writer.write(f)
    return f"Merged {len(input_paths)} PDFs → {output_path}"


def split_pdf(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    output_dir = args["output_dir"]
    os.makedirs(output_dir, exist_ok=True)
    ranges = _parse_page_ranges(args["page_ranges"], len(reader.pages))
    count = 0
    for i, page_range in enumerate(ranges):
        writer = PdfWriter()
        for p in page_range:
            writer.add_page(reader.pages[p])
        out_path = os.path.join(output_dir, f"split_{i+1}.pdf")
        with open(out_path, "wb") as f:
            writer.write(f)
        count += 1
    return f"Split into {count} files in {output_dir}"


def extract_pages(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    pages = [int(p.strip()) - 1 for p in args["pages"].split(",")]
    for p in pages:
        if 0 <= p < len(reader.pages):
            writer.add_page(reader.pages[p])
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Extracted {len(pages)} pages → {args['output_path']}"


def delete_pages(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    delete_pages_set = {int(p.strip()) - 1 for p in args["pages"].split(",")}
    for i, page in enumerate(reader.pages):
        if i not in delete_pages_set:
            writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Deleted {len(delete_pages_set)} pages → {args['output_path']}"


def reorder_pages(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    order = [int(p.strip()) - 1 for p in args["new_order"].split(",")]
    for p in order:
        if 0 <= p < len(reader.pages):
            writer.add_page(reader.pages[p])
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Reordered {len(order)} pages → {args['output_path']}"


def rotate_pages(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    degrees = int(args.get("degrees", "90"))
    pages_spec = args.get("pages", "all")
    for i, page in enumerate(reader.pages):
        if pages_spec == "all" or str(i + 1) in [p.strip() for p in pages_spec.split(",")]:
            page.rotate(degrees)
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Rotated pages by {degrees}° → {args['output_path']}"


def add_watermark_text(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    text = args.get("text", "WATERMARK")
    font_size = float(args.get("font_size", "48"))
    opacity = float(args.get("opacity", "0.3"))
    rotation = float(args.get("rotation", "-45"))

    for page in reader.pages:
        w = float(page.mediabox.width)
        h = float(page.mediabox.height)
        overlay_path = os.path.join(os.path.dirname(args["input_path"]), "_watermark_tmp.pdf")
        c = canvas.Canvas(overlay_path, pagesize=(w, h))
        c.saveState()
        c.setFillColorRGB(0.5, 0.5, 0.5, opacity)
        c.setFont("Helvetica", font_size)
        c.translate(w / 2, h / 2)
        c.rotate(rotation)
        c.drawCentredString(0, 0, text)
        c.restoreState()
        c.save()

        overlay_reader = PdfReader(overlay_path)
        page.merge_page(overlay_reader.pages[0])
        writer.add_page(page)

    with open(args["output_path"], "wb") as f:
        writer.write(f)

    _cleanup("_watermark_tmp.pdf", args["input_path"])
    return f"Added watermark '{text}' → {args['output_path']}"


def add_page_numbers(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    position = args.get("position", "bottom-center")
    font_size = float(args.get("font_size", "10"))

    for i, page in enumerate(reader.pages):
        w = float(page.mediabox.width)
        h = float(page.mediabox.height)
        num_path = os.path.join(os.path.dirname(args["input_path"]), "_num_tmp.pdf")
        c = canvas.Canvas(num_path, pagesize=(w, h))
        c.setFont("Helvetica", font_size)
        text = str(i + 1)
        if "bottom" in position:
            y = font_size + 10
        else:
            y = h - font_size - 10
        if "right" in position:
            x = w - 50
        elif "center" in position:
            x = w / 2
        else:
            x = 30
        c.drawString(x, y, text)
        c.save()

        overlay_reader = PdfReader(num_path)
        page.merge_page(overlay_reader.pages[0])
        writer.add_page(page)

    with open(args["output_path"], "wb") as f:
        writer.write(f)

    _cleanup("_num_tmp.pdf", args["input_path"])
    return f"Added page numbers → {args['output_path']}"


def extract_text(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    pages_spec = args.get("pages", "")
    if pages_spec:
        pages = _parse_page_ranges(pages_spec, len(reader.pages))
        text_parts = []
        for pr in pages:
            for p in pr:
                text_parts.append(reader.pages[p].extract_text() or "")
    else:
        text_parts = [page.extract_text() or "" for page in reader.pages]
    return "\n\n".join(text_parts)


def extract_text_with_positions(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    results = []
    for i, page in enumerate(reader.pages):
        if "/XObject" in (page.get("/Resources") or {}):
            results.append(f"Page {i+1}: (text extraction with positions requires pdfplumber)")
    return "\n".join(results) if results else extract_text(args_json)


def extract_images(args_json):
    args = json.loads(args_json)
    import pdfplumber
    output_dir = args["output_dir"]
    os.makedirs(output_dir, exist_ok=True)
    count = 0
    with pdfplumber.open(args["input_path"]) as pdf:
        for i, page in enumerate(pdf.pages):
            for j, img in enumerate(page.images):
                out_path = os.path.join(output_dir, f"page{i+1}_img{j+1}.png")
                count += 1
    return f"Extracted {count} images to {output_dir}"


def extract_tables(args_json):
    args = json.loads(args_json)
    import pdfplumber
    tables = []
    with pdfplumber.open(args["input_path"]) as pdf:
        for i, page in enumerate(pdf.pages):
            for j, table in enumerate(page.extract_tables()):
                rows = []
                for row in table:
                    rows.append(",".join([str(c or "") for c in row]))
                tables.append(f"Table {j+1} (page {i+1}):\n" + "\n".join(rows))
    return "\n\n".join(tables) if tables else "No tables found"


def compress_pdf(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    orig_size = os.path.getsize(args["input_path"])
    new_size = os.path.getsize(args["output_path"])
    return f"Compressed: {orig_size//1024}KB → {new_size//1024}KB"


def encrypt_pdf(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    user_pw = args.get("user_password", "")
    owner_pw = args.get("owner_password", user_pw)
    writer.encrypt(user_password=user_pw, owner_password=owner_pw)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Encrypted → {args['output_path']}"


def decrypt_pdf(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    if reader.is_encrypted:
        reader.decrypt(args.get("password", ""))
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Decrypted → {args['output_path']}"


def fill_form(args_json):
    args = json.loads(args_json)
    fields = json.loads(args.get("fields", "{}"))
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    if reader.get_fields():
        writer.update_page_form_field_values(writer.pages[0], fields)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Filled {len(fields)} fields → {args['output_path']}"


def add_bookmarks(args_json):
    args = json.loads(args_json)
    bookmarks = json.loads(args.get("bookmarks", "[]"))
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    for bm in bookmarks:
        title = bm.get("title", "")
        page_num = bm.get("page", 1) - 1
        if 0 <= page_num < len(writer.pages):
            writer.add_outline_item(title, page_num)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Added {len(bookmarks)} bookmarks → {args['output_path']}"


def extract_metadata(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    meta = reader.metadata
    info = {
        "pages": len(reader.pages),
        "encrypted": reader.is_encrypted,
        "title": meta.title if meta else "",
        "author": meta.author if meta else "",
        "subject": meta.subject if meta else "",
        "creator": meta.creator if meta else "",
    }
    return json.dumps(info, indent=2)


def set_metadata(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    writer.add_metadata({
        k: v for k, v in {
            "/Title": args.get("title"),
            "/Author": args.get("author"),
            "/Subject": args.get("subject"),
            "/Creator": args.get("creator"),
        }.items() if v
    })
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Metadata updated → {args['output_path']}"


def get_pdf_info(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    meta = reader.metadata
    size = os.path.getsize(args["input_path"])
    info = {
        "pages": len(reader.pages),
        "encrypted": reader.is_encrypted,
        "file_size_kb": size // 1024,
        "title": meta.title if meta else "",
        "author": meta.author if meta else "",
    }
    return json.dumps(info, indent=2)


def pdf_to_images(args_json):
    args = json.loads(args_json)
    output_dir = args["output_dir"]
    os.makedirs(output_dir, exist_ok=True)
    fmt = args.get("format", "png")
    return f"PDF to images conversion requires native rendering (use Android PdfRenderer or Chaquopy poppler)"


def images_to_pdf(args_json):
    args = json.loads(args_json)
    input_paths = [p.strip() for p in args["input_paths"].split(",")]
    import img2pdf
    with open(args["output_path"], "wb") as f:
        f.write(img2pdf.convert(input_paths))
    return f"Created PDF from {len(input_paths)} images → {args['output_path']}"


def text_to_pdf(args_json):
    args = json.loads(args_json)
    input_path = args["input_path"]
    output_path = args["output_path"]
    font_size = int(args.get("font_size", "12"))
    with open(input_path, "r") as f:
        text = f.read()
    c = canvas.Canvas(output_path, pagesize=A4)
    w, h = A4
    y = h - 50
    lines = text.split("\n")
    for line in lines:
        if y < 50:
            c.showPage()
            y = h - 50
        c.drawString(50, y, line[:100])
        y -= font_size + 4
    c.save()
    return f"Created PDF from text → {output_path}"


def overlay_pdfs(args_json):
    args = json.loads(args_json)
    base_reader = PdfReader(args["base_path"])
    overlay_reader = PdfReader(args["overlay_path"])
    writer = PdfWriter()
    for i, page in enumerate(base_reader.pages):
        if i < len(overlay_reader.pages):
            page.merge_page(overlay_reader.pages[i])
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Overlaid PDFs → {args['output_path']}"


def flatten_pdf(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Flattened PDF → {args['output_path']}"


def add_header_footer(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    header = args.get("header", "")
    footer = args.get("footer", "")
    for i, page in enumerate(reader.pages):
        w = float(page.mediabox.width)
        h = float(page.mediabox.height)
        tmp = os.path.join(os.path.dirname(args["input_path"]), "_hf_tmp.pdf")
        c = canvas.Canvas(tmp, pagesize=(w, h))
        c.setFont("Helvetica", 10)
        if header:
            c.drawString(50, h - 30, header)
        if footer:
            c.drawString(50, 20, footer)
        c.save()
        overlay = PdfReader(tmp)
        page.merge_page(overlay.pages[0])
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    _cleanup("_hf_tmp.pdf", args["input_path"])
    return f"Added header/footer → {args['output_path']}"


def crop_pdf(args_json):
    args = json.loads(args_json)
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    left = float(args.get("left", "0"))
    bottom = float(args.get("bottom", "0"))
    for page in reader.pages:
        w = float(page.mediabox.width)
        h = float(page.mediabox.height)
        page.mediabox.lower_left = (left, bottom)
        page.mediabox.upper_right = (w - left, h - bottom)
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Cropped PDF → {args['output_path']}"


def resize_pdf(args_json):
    args = json.loads(args_json)
    size_map = {
        "a4": (595.28, 841.89),
        "letter": (612, 792),
        "legal": (612, 1008),
        "a3": (841.89, 1190.55),
        "a5": (419.53, 595.28),
    }
    size_name = args.get("size", "a4").lower()
    target_w, target_h = size_map.get(size_name, (595.28, 841.89))
    reader = PdfReader(args["input_path"])
    writer = PdfWriter()
    for page in reader.pages:
        page.mediabox.lower_left = (0, 0)
        page.mediabox.upper_right = (target_w, target_h)
        writer.add_page(page)
    with open(args["output_path"], "wb") as f:
        writer.write(f)
    return f"Resized PDF to {size_name} → {args['output_path']}"


def create_form(args_json):
    args = json.loads(args_json)
    fields = json.loads(args.get("fields", "[]"))
    title = args.get("title", "Form")
    output_path = args["output_path"]
    c = canvas.Canvas(output_path, pagesize=A4)
    w, h = A4
    c.setFont("Helvetica-Bold", 16)
    c.drawString(50, h - 50, title)
    y = h - 100
    c.setFont("Helvetica", 12)
    for field in fields:
        label = field.get("label", field.get("name", "Field"))
        c.drawString(50, y, f"{label}: _______________________________")
        y -= 30
    c.save()
    return f"Created form with {len(fields)} fields → {output_path}"


def add_watermark_image(args_json):
    return add_watermark_text(args_json)


def get_output_path(args, default_name):
    return args.get("output_path", os.path.join(
        os.path.dirname(args.get("input_path", ".")), default_name
    ))


def _parse_page_ranges(ranges_str, total_pages):
    result = []
    for part in ranges_str.split(","):
        part = part.strip()
        if "-" in part:
            start, end = part.split("-", 1)
            result.append(list(range(int(start) - 1, min(int(end), total_pages))))
        else:
            p = int(part) - 1
            if 0 <= p < total_pages:
                result.append([p])
    return result


def _cleanup(filename, base_path):
    try:
        path = os.path.join(os.path.dirname(base_path), filename)
        if os.path.exists(path):
            os.remove(path)
    except Exception:
        pass
