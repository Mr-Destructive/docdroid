import os


def get_video_info(args_json):
    import json
    args = json.loads(args_json)
    size = os.path.getsize(args["input_path"])
    ext = os.path.splitext(args["input_path"])[1]
    return json.dumps({
        "format": ext.lstrip("."),
        "file_size_mb": round(size / (1024 * 1024), 2),
        "path": args["input_path"]
    })


def trim_video(args_json):
    return "Video trimming requires FFmpeg (native binary). Use execute_python with a custom FFmpeg command."
