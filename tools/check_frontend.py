"""静态校验前端 ES 模块：相对路径可解析、具名导入在目标文件里确有导出。

Node 无法直接 require 这些模块（顶层就摸 window/document），所以只做静态检查。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/static/js"

IMPORT_RE = re.compile(r"import\s+(?P<clause>[^;]*?)\s+from\s+['\"](?P<path>[^'\"]+)['\"]", re.S)
EXPORT_DECL_RE = re.compile(r"export\s+(?:async\s+)?(?:function|class|const|let|var)\s+(\w+)")
EXPORT_LIST_RE = re.compile(r"export\s*\{([^}]*)\}")
DEFAULT_RE = re.compile(r"export\s+default")


def exports_of(path: Path):
    text = path.read_text(encoding="utf-8")
    names = set(EXPORT_DECL_RE.findall(text))
    for group in EXPORT_LIST_RE.findall(text):
        for item in group.split(","):
            item = item.strip()
            if not item:
                continue
            names.add(item.split(" as ")[-1].strip())
    return names, bool(DEFAULT_RE.search(text))


def main() -> int:
    problems = []
    for file in sorted(ROOT.rglob("*.js")):
        text = file.read_text(encoding="utf-8")
        for match in IMPORT_RE.finditer(text):
            spec = match.group("path")
            if not spec.startswith("."):
                continue
            target = (file.parent / spec).resolve()
            if not target.exists():
                problems.append(f"{file.relative_to(ROOT)}: 找不到模块 {spec}")
                continue
            available, has_default = exports_of(target)
            clause = match.group("clause")
            if clause.strip().startswith("*"):
                continue  # import * as ns 不需要目标有默认导出
            braced = re.search(r"\{([^}]*)\}", clause)
            if not braced:
                if not has_default:
                    problems.append(f"{file.relative_to(ROOT)}: {spec} 没有默认导出")
                continue
            for raw in braced.group(1).split(","):
                name = raw.strip()
                if not name:
                    continue
                imported = name.split(" as ")[0].strip()
                if imported not in available:
                    problems.append(
                        f"{file.relative_to(ROOT)}: 从 {spec} 导入 {imported}，但目标未导出"
                    )
    if problems:
        print("\n".join(problems))
        return 1
    print(f"ok: {len(list(ROOT.rglob('*.js')))} 个模块的导入关系全部可解析")
    return 0


if __name__ == "__main__":
    sys.exit(main())
