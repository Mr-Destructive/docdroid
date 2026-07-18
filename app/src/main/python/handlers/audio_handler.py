import os


def get_audio_info(args_json):
    import json
    args = json.loads(args_json)
    size = os.path.getsize(args["input_path"])
    ext = os.path.splitext(args["input_path"])[1]
    return json.dumps({
        "format": ext.lstrip("."),
        "file_size_kb": size // 1024,
        "path": args["input_path"]
    })


def trim_audio(args_json):
    return "Audio trimming requires FFmpeg (native binary). Use execute_python with a custom FFmpeg command."


def convert_audio_format(args_json):
    return "Audio conversion requires FFmpeg (native binary). Use execute_python with a custom FFmpeg command."
