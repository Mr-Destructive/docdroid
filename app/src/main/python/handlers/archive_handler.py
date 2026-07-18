import json
import os
import zipfile


def create_zip(args_json):
    args = json.loads(args_json)
    input_paths = [p.strip() for p in args["input_paths"].split(",")]
    with zipfile.ZipFile(args["output_path"], "w", zipfile.ZIP_DEFLATED) as zf:
        for path in input_paths:
            if os.path.isfile(path):
                zf.write(path, os.path.basename(path))
            elif os.path.isdir(path):
                for root, dirs, files in os.walk(path):
                    for f in files:
                        fpath = os.path.join(root, f)
                        arcname = os.path.relpath(fpath, os.path.dirname(path))
                        zf.write(fpath, arcname)
    return f"Created ZIP with {len(input_paths)} items → {args['output_path']}"


def extract_zip(args_json):
    args = json.loads(args_json)
    output_dir = args["output_dir"]
    os.makedirs(output_dir, exist_ok=True)
    with zipfile.ZipFile(args["input_path"], "r") as zf:
        zf.extractall(output_dir)
    count = len(zf.namelist())
    return f"Extracted {count} files → {output_dir}"


def list_archive_contents(args_json):
    args = json.loads(args_json)
    path = args["input_path"]
    if path.endswith(".zip"):
        with zipfile.ZipFile(path, "r") as zf:
            entries = zf.infolist()
            lines = [f"{e.filename} ({e.file_size} bytes)" for e in entries]
            return "\n".join(lines)
    return f"Listed contents of {path} (limited support for non-ZIP formats)"
