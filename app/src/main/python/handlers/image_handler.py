import json
import os
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance


def resize_image(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    w = int(args.get("width", "0"))
    h = int(args.get("height", "0"))
    if w == 0 and h == 0:
        w, h = img.size
    elif w == 0:
        ratio = h / img.height
        w = int(img.width * ratio)
    elif h == 0:
        ratio = w / img.width
        h = int(img.height * ratio)
    img = img.resize((w, h), Image.LANCZOS)
    img.save(args["output_path"])
    return f"Resized to {w}x{h} → {args['output_path']}"


def crop_image(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    x = int(args.get("x", "0"))
    y = int(args.get("y", "0"))
    w = int(args.get("width", str(img.width)))
    h = int(args.get("height", str(img.height)))
    img = img.crop((x, y, x + w, y + h))
    img.save(args["output_path"])
    return f"Cropped to {w}x{h} → {args['output_path']}"


def rotate_image(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    degrees = float(args.get("degrees", "90"))
    img = img.rotate(-degrees, expand=True, fillcolor=(255, 255, 255))
    img.save(args["output_path"])
    return f"Rotated {degrees}° → {args['output_path']}"


def flip_image(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    direction = args.get("direction", "horizontal")
    if direction == "horizontal":
        img = img.transpose(Image.FLIP_LEFT_RIGHT)
    else:
        img = img.transpose(Image.FLIP_TOP_BOTTOM)
    img.save(args["output_path"])
    return f"Flipped {direction}ally → {args['output_path']}"


def convert_image_format(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    fmt = args.get("format", "png").upper()
    if fmt == "JPG":
        fmt = "JPEG"
    if img.mode == "RGBA" and fmt == "JPEG":
        img = img.convert("RGB")
    img.save(args["output_path"], fmt)
    return f"Converted to {fmt} → {args['output_path']}"


def compress_image(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    quality = int(args.get("quality", "60"))
    if img.mode == "RGBA":
        img = img.convert("RGB")
    img.save(args["output_path"], "JPEG", quality=quality, optimize=True)
    orig = os.path.getsize(args["input_path"])
    new = os.path.getsize(args["output_path"])
    return f"Compressed: {orig//1024}KB → {new//1024}KB"


def get_image_metadata(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    info = {
        "format": img.format,
        "mode": img.mode,
        "width": img.width,
        "height": img.height,
        "file_size_kb": os.path.getsize(args["input_path"]) // 1024,
    }
    if hasattr(img, "info"):
        for k, v in img.info.items():
            if isinstance(v, (str, int, float)):
                info[k] = v
    return json.dumps(info, indent=2)


def strip_image_metadata(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    data = list(img.getdata())
    clean = Image.new(img.mode, img.size)
    clean.putdata(data)
    clean.save(args["output_path"])
    return f"Stripped metadata → {args['output_path']}"


def adjust_brightness(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    factor = float(args.get("factor", "1.5"))
    enhancer = ImageEnhance.Brightness(img)
    img = enhancer.enhance(factor)
    img.save(args["output_path"])
    return f"Brightness adjusted ({factor}x) → {args['output_path']}"


def adjust_contrast(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    factor = float(args.get("factor", "1.5"))
    enhancer = ImageEnhance.Contrast(img)
    img = enhancer.enhance(factor)
    img.save(args["output_path"])
    return f"Contrast adjusted ({factor}x) → {args['output_path']}"


def adjust_saturation(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    factor = float(args.get("factor", "1.5"))
    enhancer = ImageEnhance.Color(img)
    img = enhancer.enhance(factor)
    img.save(args["output_path"])
    return f"Saturation adjusted ({factor}x) → {args['output_path']}"


def apply_image_filter(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    filter_name = args.get("filter", "grayscale")
    filters = {
        "grayscale": lambda i: i.convert("L").convert("RGB"),
        "sepia": lambda i: _apply_sepia(i),
        "invert": lambda i: Image.eval(i, lambda x: 255 - x),
        "blur": lambda i: i.filter(ImageFilter.GaussianBlur(radius=5)),
        "sharpen": lambda i: i.filter(ImageFilter.SHARPEN),
        "emboss": lambda i: i.filter(ImageFilter.EMBOSS),
        "edge_detect": lambda i: i.filter(ImageFilter.FIND_EDGES),
        "smooth": lambda i: i.filter(ImageFilter.SMOOTH),
    }
    fn = filters.get(filter_name, lambda i: i)
    img = fn(img)
    img.save(args["output_path"])
    return f"Applied {filter_name} filter → {args['output_path']}"


def add_text_overlay(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"]).convert("RGBA")
    draw = ImageDraw.Draw(img)
    text = args.get("text", "")
    x = int(args.get("x", "10"))
    y = int(args.get("y", "10"))
    font_size = int(args.get("font_size", "24"))
    color = args.get("color", "#FFFFFF")
    draw.text((x, y), text, fill=color)
    img.save(args["output_path"])
    return f"Added text overlay → {args['output_path']}"


def add_image_overlay(args_json):
    args = json.loads(args_json)
    base = Image.open(args["base_path"]).convert("RGBA")
    overlay = Image.open(args["overlay_path"]).convert("RGBA")
    x = int(args.get("x", "0"))
    y = int(args.get("y", "0"))
    opacity = float(args.get("opacity", "1.0"))
    if opacity < 1.0:
        overlay.putalpha(int(opacity * 255))
    base.paste(overlay, (x, y), overlay)
    base.save(args["output_path"])
    return f"Added image overlay → {args['output_path']}"


def add_watermark_image(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"]).convert("RGBA")
    text = args.get("watermark_text", "")
    opacity = float(args.get("opacity", "0.3"))
    draw = ImageDraw.Draw(img)
    w, h = img.size
    draw.text((w // 4, h // 2), text, fill=(128, 128, 128, int(opacity * 255)))
    img.save(args["output_path"])
    return f"Added watermark → {args['output_path']}"


def generate_thumbnail(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    max_size = int(args.get("max_size", "200"))
    img.thumbnail((max_size, max_size), Image.LANCZOS)
    img.save(args["output_path"])
    return f"Thumbnail {img.width}x{img.height} → {args['output_path']}"


def auto_enhance(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    img = ImageEnhance.Contrast(img).enhance(1.2)
    img = ImageEnhance.Sharpness(img).enhance(1.5)
    img = ImageEnhance.Brightness(img).enhance(1.05)
    img.save(args["output_path"])
    return f"Auto-enhanced → {args['output_path']}"


def create_border(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    border = int(args.get("border_size", "10"))
    color = args.get("color", "#000000")
    new_w = img.width + 2 * border
    new_h = img.height + 2 * border
    bordered = Image.new(img.mode, (new_w, new_h), color)
    bordered.paste(img, (border, border))
    bordered.save(args["output_path"])
    return f"Added {border}px border → {args['output_path']}"


def change_dpi(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"])
    dpi = int(args.get("dpi", "300"))
    img.save(args["output_path"], dpi=(dpi, dpi))
    return f"Set DPI to {dpi} → {args['output_path']}"


def create_image(args_json):
    args = json.loads(args_json)
    w = int(args.get("width", "800"))
    h = int(args.get("height", "600"))
    color = args.get("color", "#FFFFFF")
    img = Image.new("RGB", (w, h), color)
    img.save(args["output_path"])
    return f"Created {w}x{h} image → {args['output_path']}"


def generate_qr_code(args_json):
    args = json.loads(args_json)
    content = args.get("content", "")
    size = int(args.get("size", "300"))
    try:
        from pyzbar.pyzbar import decode
        img = Image.new("RGB", (size, size), "white")
        return f"QR generation requires qrcode library"
    except ImportError:
        return f"QR generation requires 'qrcode' Python package"


def create_collage(args_json):
    args = json.loads(args_json)
    input_paths = [p.strip() for p in args["input_paths"].split(",")]
    cols = int(args.get("columns", "2"))
    spacing = int(args.get("spacing", "5"))
    images = [Image.open(p) for p in input_paths]
    rows_count = (len(images) + cols - 1) // cols
    max_w = max(img.width for img in images)
    max_h = max(img.height for img in images)
    collage_w = cols * max_w + (cols + 1) * spacing
    collage_h = rows_count * max_h + (rows_count + 1) * spacing
    collage = Image.new("RGB", (collage_w, collage_h), args.get("background_color", "#FFFFFF"))
    for idx, img in enumerate(images):
        r, c = divmod(idx, cols)
        x = spacing + c * (max_w + spacing)
        y = spacing + r * (max_h + spacing)
        collage.paste(img, (x, y))
    collage.save(args["output_path"])
    return f"Created {cols}x{rows_count} collage → {args['output_path']}"


def batch_resize(args_json):
    args = json.loads(args_json)
    input_paths = [p.strip() for p in args["input_paths"].split(",")]
    w = int(args.get("width", "800"))
    h = int(args.get("height", "600"))
    output_dir = args["output_dir"]
    os.makedirs(output_dir, exist_ok=True)
    for p in input_paths:
        img = Image.open(p)
        img = img.resize((w, h), Image.LANCZOS)
        out = os.path.join(output_dir, os.path.basename(p))
        img.save(out)
    return f"Resized {len(input_paths)} images → {output_dir}"


def batch_convert_format(args_json):
    args = json.loads(args_json)
    input_paths = [p.strip() for p in args["input_paths"].split(",")]
    fmt = args.get("format", "png").upper()
    if fmt == "JPG":
        fmt = "JPEG"
    output_dir = args["output_dir"]
    os.makedirs(output_dir, exist_ok=True)
    for p in input_paths:
        img = Image.open(p)
        if img.mode == "RGBA" and fmt == "JPEG":
            img = img.convert("RGB")
        base = os.path.splitext(os.path.basename(p))[0]
        out = os.path.join(output_dir, f"{base}.{fmt.lower()}")
        img.save(out, fmt)
    return f"Converted {len(input_paths)} images to {fmt} → {output_dir}"


def draw_shapes(args_json):
    args = json.loads(args_json)
    img = Image.open(args["input_path"]).convert("RGBA")
    draw = ImageDraw.Draw(img)
    shapes = json.loads(args.get("shapes", "[]"))
    for shape in shapes:
        shape_type = shape.get("type", "rect")
        color = shape.get("color", "#FF0000")
        sw = shape.get("stroke_width", 2)
        if shape_type == "rect":
            x, y, w, h = shape["x"], shape["y"], shape["w"], shape["h"]
            draw.rectangle([x, y, x + w, y + h], outline=color, width=sw)
        elif shape_type == "circle":
            x, y, r = shape["x"], shape["y"], shape.get("r", 50)
            draw.ellipse([x - r, y - r, x + r, y + r], outline=color, width=sw)
        elif shape_type == "line":
            draw.line([shape["x1"], shape["y1"], shape["x2"], shape["y2"]], fill=color, width=sw)
    img.save(args["output_path"])
    return f"Drew {len(shapes)} shapes → {args['output_path']}"


def _apply_sepia(img):
    img = img.convert("RGB")
    w, h = img.size
    pixels = img.load()
    for y in range(h):
        for x in range(w):
            r, g, b = pixels[x, y]
            tr = int(0.393 * r + 0.769 * g + 0.189 * b)
            tg = int(0.349 * r + 0.686 * g + 0.168 * b)
            tb = int(0.272 * r + 0.534 * g + 0.131 * b)
            pixels[x, y] = (min(tr, 255), min(tg, 255), min(tb, 255))
    return img
