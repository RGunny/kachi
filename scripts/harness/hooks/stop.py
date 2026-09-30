#!/usr/bin/env python3
"""Stop 훅. 러너 세션이 scope 밖·동결 경로 변경을 남긴 채 끝나지 못하게 한다.

HARNESS_HEADLESS=1이 아니면 아무것도 하지 않는다(감독 세션 무영향).
stop_hook_active가 true면 이미 한 번 막은 뒤이므로 통과시킨다. 남은 위반은 러너의 사후 검사가 잡는다.
verify(gradle)는 느려서 여기서 돌리지 않는다.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from _utils import HarnessError, find_project_root, load_config  # noqa: E402
from scope_check import current_phase, frozen_touched, outside_scope  # noqa: E402


def main() -> None:
    if os.environ.get("HARNESS_HEADLESS") != "1":
        sys.exit(0)
    try:
        payload = json.loads(sys.stdin.read() or "{}")
    except json.JSONDecodeError:
        payload = {}
    if payload.get("stop_hook_active"):
        sys.exit(0)
    try:
        root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or find_project_root(Path(payload.get("cwd") or Path.cwd()))).resolve()
        phase = current_phase(root)
        if phase is None:
            sys.exit(0)
        problems = []
        outside = outside_scope(root, phase.scope)
        if outside:
            problems.append(f"scope 밖 변경: {', '.join(outside[:10])} (허용 scope: {phase.scope})")
        baseline = os.environ.get("HARNESS_BASELINE")
        frozen = [p for p in load_config(root).get("frozen_paths", []) if p not in phase.unfreeze]
        if baseline and frozen:
            touched = frozen_touched(root, frozen, baseline)
            if touched:
                problems.append(f"동결 경로 변경: {', '.join(touched[:10])}")
    except HarnessError as exc:
        print(f"stop 훅 설정 오류(차단하지 않음): {exc}", file=sys.stderr)
        sys.exit(1)
    if problems:
        print("끝내기 전에 되돌리세요. 러너가 이 상태를 phase 실패로 처리합니다.\n" + "\n".join(problems), file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
