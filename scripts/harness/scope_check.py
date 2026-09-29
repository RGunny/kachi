"""phase scope와 동결 경로 판정. 러너(세션 뒤)와 훅(세션 중)이 같은 함수를 쓴다.

경로는 프로젝트 루트 기준 상대경로다. scope 패턴은 디렉터리 접두(`docs/`), 정확한 파일, fnmatch 글롭 중 하나다.
"""

from __future__ import annotations

import fnmatch
import os
from pathlib import Path

from _utils import INDEX_FILE, git, git_lines, read_json
from task_state import Phase, load_phases


def path_under(path: str, patterns: list[str]) -> bool:
    for pattern in patterns:
        prefix = pattern.rstrip("/")
        if path == prefix or path.startswith(prefix + "/") or fnmatch.fnmatch(path, pattern):
            return True
    return False


def changed_paths(root: Path) -> list[str]:
    """git status 기준 변경·미추적 경로. 이름 변경은 새 경로만 본다."""
    paths = []
    for line in git("status", "--porcelain", cwd=root).stdout.splitlines():
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        paths.append(path.strip('"'))
    return paths


def outside_scope(root: Path, scope: list[str]) -> list[str]:
    return [p for p in changed_paths(root) if not path_under(p, scope)]


def frozen_touched(root: Path, frozen: list[str], baseline: str) -> list[str]:
    """baseline 대비 diff와 미추적 파일 중 동결 경로에 걸린 것."""
    if not frozen:
        return []
    diff = git("diff", "--name-only", baseline, "--", *frozen, cwd=root).stdout.split()
    untracked = git_lines("ls-files", "--others", "--exclude-standard", "--", *frozen, cwd=root)
    return diff + untracked


def relative_to_root(root: Path, raw: str, cwd: Path | None = None) -> str | None:
    """절대·상대 경로를 루트 기준으로 바꾼다. 루트 밖이면 None."""
    path = Path(raw)
    if not path.is_absolute():
        path = (cwd or root) / path
    normalized = path.resolve()
    root_resolved = root.resolve()
    if not normalized.is_relative_to(root_resolved):
        return None
    return normalized.relative_to(root_resolved).as_posix()


def current_phase(root: Path, env: dict | None = None) -> Phase | None:
    """러너 세션(HARNESS_TASK, HARNESS_PHASE)이면 그 phase를, 아니면 None."""
    env = os.environ if env is None else env
    task, phase_id = env.get("HARNESS_TASK"), env.get("HARNESS_PHASE")
    if not task or not phase_id:
        return None
    task_dir = root / "tasks" / task
    index_path = task_dir / INDEX_FILE
    if not index_path.exists():
        return None
    for phase in load_phases(read_json(index_path), task_dir):
        if phase.id == str(phase_id):
            return phase
    return None
