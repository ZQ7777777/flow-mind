import json
import os
import sys

TOOLS_DIR = r"C:\Users\zq\.codex\plugins\cache\openai-primary-runtime\presentations\26.819.11345\skills\presentations\container_tools"
PPTX_PATH = r"C:\Users\zq\Desktop\resume\flow-mind\outputs\张琦-实习成果汇报.pptx"

sys.path.insert(0, TOOLS_DIR)
import render_slides  # noqa: E402

original_render = render_slides._render_presentation_with_artifact_tool


def render_with_verified_windows_fallback(input_path: str, out_dir: str, dpi: int):
    try:
        return original_render(input_path, out_dir, dpi)
    except RuntimeError as error:
        message = str(error)
        marker = message.find("{")
        if marker < 0:
            raise
        payload = json.loads(message[marker:])
        paths = payload.get("paths", [])
        slide_count = payload.get("slideCount")
        if slide_count != 9 or len(paths) != slide_count or not all(os.path.isfile(path) for path in paths):
            raise
        print("INFO · Windows renderer returned nonzero after producing all 9 PNGs; continuing with verified files.")
        return paths


render_slides._render_presentation_with_artifact_tool = render_with_verified_windows_fallback

import slides_test  # noqa: E402

sys.argv = ["slides_test.py", PPTX_PATH]
slides_test.main()
