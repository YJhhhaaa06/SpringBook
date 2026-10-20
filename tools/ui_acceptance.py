# -*- coding: utf-8 -*-
"""前端验收（无头 Chrome + CDP）：逐页渲染、真实点击、抓截图、收集前端异常。

覆盖：登录/注册页、管理员四个页面、读者端页面、借阅弹层、预约队列抽屉、
越权拦截、无效令牌回登录页、深色主题。

前置：
  1. MySQL 容器运行、应用运行在 127.0.0.1:8080；
  2. 已执行 `python tools/seed_demo_data.py` 灌入演示数据；
  3. 需要 `pip install websockets`（CDP 走 WebSocket），本机装有 Chrome/Edge。

产物：temp-script/shots/*.png 与终端 PASS/FAIL 报告（退出码 0 表示全通过）。
"""
import asyncio
import base64
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

try:
    import websockets
except ImportError:  # pragma: no cover
    print("缺少依赖：pip install websockets")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8080"
DEBUG_PORT = 9222
SESSIONS = ROOT / "temp-script" / "ui_sessions.json"
SHOTS = ROOT / "temp-script" / "shots"


def find_chrome():
    """CHROME_PATH 优先，其次本机常见安装位置。"""
    candidates = [
        os.environ.get("CHROME_PATH"),
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/usr/bin/google-chrome",
        "/usr/bin/chromium",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    return None


CHROME = find_chrome()
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

problems = []
notes = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ((" | " + str(detail)) if detail else ""))
    if not ok:
        problems.append(name)


def decode_jwt(token):
    seg = token.split(".")[1]
    seg += "=" * (-len(seg) % 4)
    return json.loads(base64.urlsafe_b64decode(seg))


def session_literal(token):
    payload = decode_jwt(token)
    session = {
        "token": token,
        "username": payload.get("username", ""),
        "role": "ADMIN" if payload.get("role") == "ADMIN" else "READER",
        "readerId": payload.get("readerId"),
        "accountId": payload.get("sub"),
        "expiresAt": int(payload["exp"]) * 1000,
    }
    return json.dumps(json.dumps(session, ensure_ascii=False))


class CDP:
    def __init__(self, ws):
        self.ws = ws
        self.next_id = 0
        self.pending = {}
        self.events = []

    async def start(self):
        self.reader_task = asyncio.create_task(self._reader())

    async def _reader(self):
        try:
            async for raw in self.ws:
                message = json.loads(raw)
                if "id" in message:
                    future = self.pending.pop(message["id"], None)
                    if future and not future.done():
                        future.set_result(message)
                else:
                    self.events.append(message)
        except Exception:  # 连接关闭
            pass

    async def send(self, method, **params):
        self.next_id += 1
        message_id = self.next_id
        future = asyncio.get_event_loop().create_future()
        self.pending[message_id] = future
        await self.ws.send(json.dumps({"id": message_id, "method": method, "params": params}))
        message = await asyncio.wait_for(future, timeout=40)
        if "error" in message:
            raise RuntimeError("%s -> %s" % (method, message["error"]))
        return message.get("result", {})

    async def evaluate(self, expression, await_promise=False):
        result = await self.send(
            "Runtime.evaluate", expression=expression, returnByValue=True,
            awaitPromise=await_promise, userGesture=True,
        )
        if result.get("exceptionDetails"):
            details = result["exceptionDetails"]
            raise RuntimeError("JS 异常: %s" % json.dumps(details, ensure_ascii=False)[:500])
        return result.get("result", {}).get("value")

    async def wait_js(self, expression, timeout=12):
        script = """
        new Promise((resolve) => {
          const started = Date.now();
          const timer = setInterval(() => {
            let value = null;
            try { value = (%s); } catch (e) { value = null; }
            if (value || Date.now() - started > %d) {
              clearInterval(timer);
              resolve(value || null);
            }
          }, 120);
        })
        """ % (expression, timeout * 1000)
        return await self.evaluate(script, await_promise=True)

    async def goto(self, url):
        await self.send("Page.navigate", url=url)
        await asyncio.sleep(0.4)

    async def shot(self, name):
        result = await self.send("Page.captureScreenshot", format="png", captureBeyondViewport=False)
        path = str(SHOTS / (name + ".png"))
        with open(path, "wb") as handle:
            handle.write(base64.b64decode(result["data"]))
        return path

    def errors(self):
        found = []
        for event in self.events:
            method = event.get("method")
            params = event.get("params", {})
            if method == "Runtime.exceptionThrown":
                found.append("exception: " + json.dumps(params.get("exceptionDetails", {}), ensure_ascii=False)[:300])
            elif method == "Runtime.consoleAPICalled" and params.get("type") in ("error", "assert"):
                args = [a.get("value") or a.get("description", "") for a in params.get("args", [])]
                found.append("console.error: " + " ".join(str(a) for a in args)[:300])
            elif method == "Log.entryAdded":
                entry = params.get("entry", {})
                text = entry.get("text", "")
                # 故意用无效令牌探活时，401 是预期结果，不算前端错误
                if entry.get("source") == "network" and "401" in text:
                    notes.append("预期内的 401（无效令牌探活）")
                    continue
                if entry.get("level") == "error":
                    found.append("log: %s %s" % (entry.get("source"), text)[:300])
        return found


async def run():
    with open(SESSIONS, encoding="utf-8") as handle:
        sessions = json.load(handle)

    profile = tempfile.mkdtemp(prefix="sb-chrome-")
    chrome = subprocess.Popen(
        [CHROME, "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
         "--disable-extensions", "--hide-scrollbars", "--no-proxy-server", "--mute-audio",
         "--user-data-dir=" + profile, "--remote-debugging-port=%d" % DEBUG_PORT,
         "--window-size=1440,1000", "about:blank"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )

    try:
        for _ in range(60):
            try:
                with OPENER.open("http://127.0.0.1:%d/json/version" % DEBUG_PORT, timeout=1) as resp:
                    json.loads(resp.read().decode())
                break
            except Exception:
                time.sleep(0.4)
        else:
            raise RuntimeError("Chrome 调试端口未就绪")

        request = urllib.request.Request(
            "http://127.0.0.1:%d/json/new?about:blank" % DEBUG_PORT, method="PUT")
        with OPENER.open(request, timeout=5) as resp:
            target = json.loads(resp.read().decode())

        async with websockets.connect(target["webSocketDebuggerUrl"], max_size=32 * 1024 * 1024) as ws:
            cdp = CDP(ws)
            await cdp.start()
            await cdp.send("Page.enable")
            await cdp.send("Runtime.enable")
            await cdp.send("Log.enable")
            await cdp.send("Emulation.setDeviceMetricsOverride",
                           width=1440, height=1000, deviceScaleFactor=1, mobile=False)

            # ---------- 1. 未登录：登录页 ----------
            await cdp.goto(BASE + "/")
            title = await cdp.wait_js("document.querySelector('.auth__pitch h1')?.textContent")
            check("登录页渲染", bool(title), title)
            await cdp.evaluate("localStorage.removeItem('springbook.session.v1')")
            await cdp.goto(BASE + "/#/login")
            await cdp.wait_js("!!document.querySelector('[data-role=submit]')")
            await cdp.shot("01-login")

            # 切到注册 tab 看表单字段是否展开
            await cdp.evaluate("document.querySelector('[data-tab=register]').click()")
            await asyncio.sleep(0.25)
            fields = await cdp.evaluate(
                "[...document.querySelectorAll('.field:not([hidden])')].length")
            check("注册表单字段可见", fields == 4, "visible fields=%s" % fields)
            await cdp.shot("02-register")

            # ---------- 2. 管理员 ----------
            await cdp.evaluate("localStorage.setItem('springbook.session.v1', %s)" % session_literal(sessions["admin"]))
            await cdp.goto(BASE + "/#/books")
            cards = await cdp.wait_js("document.querySelectorAll('.book-card').length", timeout=15)
            check("图书卡片渲染", bool(cards) and cards >= 7, "cards=%s" % cards)
            admin_actions = await cdp.evaluate(
                "document.querySelectorAll('[data-action=copies]').length")
            check("管理员专属操作可见", admin_actions > 0, "调册按钮=%s" % admin_actions)
            queue_note = await cdp.evaluate(
                "[...document.querySelectorAll('.book-card__note')].map(n=>n.textContent.trim()).join(' | ')")
            notes.append("图书页提示：" + str(queue_note))
            await cdp.shot("03-admin-books")

            # 借阅弹层（管理员 -> 需要选读者）
            await cdp.evaluate("document.querySelector('.book-card [data-action=borrow]').click()")
            modal_title = await cdp.wait_js("document.querySelector('.overlay .modal__title')?.textContent")
            check("借阅弹层打开", modal_title == "借出图书", modal_title)
            picker = await cdp.evaluate("!!document.querySelector('.overlay .picker__item')")
            check("借阅弹层含读者选择器", bool(picker))
            await cdp.shot("04-borrow-modal")
            await cdp.evaluate("document.querySelector('.overlay [data-close]').click()")
            await asyncio.sleep(0.3)

            # 预约队列抽屉
            await cdp.evaluate("""
              (() => {
                const card = [...document.querySelectorAll('.book-card')]
                  .find(c => c.textContent.includes('算法导论'));
                card?.querySelector('[data-action=queue]')?.click();
              })()
            """)
            drawer = await cdp.wait_js("document.querySelector('.drawer .desc')?.textContent")
            check("队列抽屉打开", bool(drawer), str(drawer)[:60])
            queue_text = await cdp.evaluate("document.querySelector('.drawer')?.textContent")
            check("队列含排队读者", "张三" in (queue_text or ""), (queue_text or "")[:80])
            await cdp.shot("05-queue-drawer")
            await cdp.evaluate("document.querySelector('.drawer [data-close]').click()")
            await asyncio.sleep(0.3)

            await cdp.goto(BASE + "/#/loans")
            rows = await cdp.wait_js("document.querySelectorAll('.table tbody tr').length")
            check("借阅管理行数", bool(rows), "rows=%s" % rows)
            fine_pill = await cdp.evaluate("document.body.textContent.includes('待缴')")
            check("借阅页展示待缴罚款", bool(fine_pill))
            await cdp.shot("06-admin-loans")

            await cdp.goto(BASE + "/#/readers")
            reader_rows = await cdp.wait_js("document.querySelectorAll('.table tbody tr').length")
            check("读者管理行数", bool(reader_rows), "rows=%s" % reader_rows)
            await cdp.shot("07-admin-readers")

            # 检索「李四」后再看冻结状态与详情（演示库里读者较多，李四不在首页）
            await cdp.evaluate("""
              (() => {
                const form = document.querySelector('[data-role=search]');
                form.querySelector('input[name=keyword]').value = '李四';
                form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
              })()
            """)
            hit = await cdp.wait_js(
                "[...document.querySelectorAll('tbody tr')].some(tr => tr.textContent.includes('李四'))")
            check("读者检索命中", bool(hit))
            frozen = await cdp.evaluate("""
              (() => {
                const row = [...document.querySelectorAll('tbody tr')]
                  .find(tr => tr.textContent.includes('李四'));
                return row ? row.textContent.includes('已冻结') : false;
              })()
            """)
            check("读者页展示冻结状态", bool(frozen))
            await cdp.shot("08-reader-frozen")

            # 读者详情抽屉
            await cdp.evaluate("""
              (() => {
                const row = [...document.querySelectorAll('tbody tr')]
                  .find(tr => tr.textContent.includes('李四'));
                row?.querySelector('[data-action=detail]')?.click();
              })()
            """)
            detail = await cdp.wait_js("document.querySelector('.drawer .desc')?.textContent")
            check("读者详情抽屉打开", "李四" in (detail or ""), str(detail)[:60])
            debt = await cdp.evaluate("document.querySelector('.drawer')?.textContent.includes('5.00')")
            check("读者详情展示欠款", bool(debt))
            await cdp.shot("09-reader-detail")
            await cdp.evaluate("document.querySelector('.drawer [data-close]').click()")
            await asyncio.sleep(0.3)

            await cdp.goto(BASE + "/#/account")
            account = await cdp.wait_js("document.querySelector('.panel')?.textContent")
            check("我的账户渲染", bool(account))
            countdown = await cdp.evaluate(
                "document.querySelector('[data-countdown]')?.textContent")
            check("令牌倒计时在跳", bool(countdown) and countdown != "—", countdown)
            await cdp.shot("10-admin-account")

            # ---------- 3. 读者端 ----------
            await cdp.evaluate("localStorage.setItem('springbook.session.v1', %s)" % session_literal(sessions["reader"]))
            await cdp.goto(BASE + "/#/books")
            await cdp.wait_js("document.querySelectorAll('.book-card').length", timeout=15)
            borrow_buttons = await cdp.evaluate("document.querySelectorAll('[data-action=borrow]').length")
            check("读者端借阅按钮", borrow_buttons > 0, "buttons=%s" % borrow_buttons)
            no_admin = await cdp.evaluate("document.querySelectorAll('[data-action=copies]').length")
            check("读者端无管理操作", no_admin == 0)
            mine_note = await cdp.evaluate(
                "[...document.querySelectorAll('.book-card__note')].map(n=>n.textContent.trim().replace(/\\s+/g,' ')).join(' | ')")
            check("读者端显示排队状态", "排队中" in (mine_note or ""), mine_note)
            notes.append("读者端卡片提示：" + str(mine_note))
            await cdp.shot("11-reader-books")

            await cdp.goto(BASE + "/#/loans")
            await cdp.wait_js("document.querySelectorAll('.table tbody tr').length")
            await cdp.shot("12-reader-loans")

            await cdp.goto(BASE + "/#/account")
            detail = await cdp.wait_js("document.querySelector('[data-role=reader-detail]')?.textContent")
            check("读者档案加载", "张三" in (detail or ""), str(detail)[:70])
            await cdp.shot("13-reader-account")

            # ---------- 4. 深色主题 ----------
            await cdp.evaluate("localStorage.setItem('springbook.theme','dark')")
            await cdp.goto(BASE + "/#/books")
            await cdp.send("Page.reload")
            await asyncio.sleep(0.4)
            dark_cards = await cdp.wait_js("document.querySelectorAll('.book-card').length", timeout=15)
            theme = await cdp.evaluate("document.documentElement.dataset.theme")
            check("深色主题生效", theme == "dark" and bool(dark_cards), "%s cards=%s" % (theme, dark_cards))
            await cdp.shot("14-dark-books")
            await cdp.evaluate("localStorage.setItem('springbook.theme','light')")

            # ---------- 5. 越权与失效 ----------
            await cdp.goto(BASE + "/#/readers")
            await asyncio.sleep(1.0)
            redirected = await cdp.evaluate("location.hash")
            check("读者访问读者管理被拦回", redirected.startswith("#/books"), redirected)

            await cdp.evaluate(
                "localStorage.setItem('springbook.session.v1', JSON.stringify({token:'bad.token.value',username:'x',role:'READER',readerId:1,accountId:1,expiresAt:Date.now()+3600000}))")
            await cdp.send("Page.reload")
            await asyncio.sleep(0.5)
            notice = await cdp.wait_js(
                "document.querySelector('.auth') ? document.querySelector('.auth-card')?.textContent : null",
                timeout=15)
            check("无效令牌回到登录页", bool(notice) and "登录" in notice, str(notice)[:80])
            expired_hint = await cdp.evaluate("document.body.textContent.includes('登录已过期')")
            check("提示登录已过期", bool(expired_hint))
            await cdp.shot("15-expired")

            await asyncio.sleep(0.4)
            errors = cdp.errors()
            if errors:
                print("\n--- 浏览器侧错误 ---")
                for item in errors:
                    print("  " + str(item))
            check("无前端异常/资源错误", not errors, "count=%s" % len(errors))

    finally:
        chrome.terminate()
        try:
            chrome.wait(timeout=8)
        except subprocess.TimeoutExpired:
            chrome.kill()
        shutil.rmtree(profile, ignore_errors=True)

    print("\n--- 备注 ---")
    for note in notes:
        print("  " + note)
    print("\n结果：%d 项失败" % len(problems))
    return 0 if not problems else 1


if __name__ == "__main__":
    if not CHROME:
        print("未找到 Chrome / Edge，可用 CHROME_PATH 环境变量指定浏览器可执行文件")
        sys.exit(2)
    SHOTS.mkdir(parents=True, exist_ok=True)
    sys.exit(asyncio.run(run()))
