import sys
import os
import io
import traceback


def run_code(code_str):
    output_buffer = io.StringIO()
    old_stdout = sys.stdout
    sys.stdout = output_buffer
    try:
        exec(code_str, {"__builtins__": __builtins__, "__name__": "__main__"})
        result = output_buffer.getvalue()
        if not result.strip():
            result = "Code executed successfully (no output)"
        return result
    except Exception as e:
        tb = traceback.format_exc()
        return f"ERROR: {e}\n{tb}"
    finally:
        sys.stdout = old_stdout
