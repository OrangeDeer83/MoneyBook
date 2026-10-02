# -*- coding: utf-8 -*-
"""把各分流的結果合併成一份含截圖的 HTML 報告。用法：python3 build_report.py <結果資料夾> <輸出.html>"""
import base64
import glob
import html
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CASES = json.load(open(os.path.join(HERE, "cases.json"), encoding="utf-8"))

MANUAL = {
    "file": "需要透過系統的檔案選擇器選檔，無法穩定自動操作",
    "device": "要靠實機手感或主觀判斷（動畫順不順、好不好看、真機鍵盤），不適合自動化",
    "release": "發版後才能驗證，屬於發版流程的檢查",
    "todo": "還沒寫成自動測試，目前仍需手動測",
}
MANUAL_BY_ID = {
    "TC003": "file", "TC004": "file", "TC005": "file", "TC006": "file", "TC007": "file", "TC008": "file", "TC009": "file",
    "TC010": "device", "TC094": "device", "TC118": "device", "TC119": "device", "TC091": "todo", "TC092": "todo",
    "TC105": "release", "TC106": "release", "TC107": "release",
    "TC023": "todo", "TC029": "todo", "TC032": "todo", "TC035": "todo", "TC036": "todo", "TC037": "todo", "TC041": "todo",
    "TC042": "todo", "TC068": "todo", "TC071": "todo", "TC019": "todo", "TC064": "todo",
}

STATUS_LABEL = {"pass": "通過", "review": "通過・待看圖", "fail": "失敗", "manual": "手動"}


def thumb(path, width=420):
    try:
        from PIL import Image
        im = Image.open(path).convert("RGB")
        r = width / im.width
        im = im.resize((width, int(im.height * r)))
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=68)
        return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()
    except Exception:  # noqa: BLE001
        return ""


def esc(s):
    return html.escape(str(s))


def main(res_dir, out_path):
    results = {}
    for p in glob.glob(os.path.join(res_dir, "**", "results_*.json"), recursive=True):
        base = os.path.dirname(p)
        for r in json.load(open(p, encoding="utf-8")):
            r["_base"] = base
            results[r["id"]] = r

    rows = []
    for c in CASES:
        r = results.get(c["id"])
        if r:
            status = r["status"]
        else:
            status = "manual"
        rows.append((c, r, status))

    counts = {k: 0 for k in STATUS_LABEL}
    for _, _, s in rows:
        counts[s] += 1
    total = len(rows)
    auto = total - counts["manual"]
    sha = os.environ.get("GITHUB_SHA", "")[:7]

    parts = []
    w = parts.append
    w("<title>記帳本 自動測試報告</title>")
    w("""<style>
:root{--bg:#f4f6f3;--surface:#fff;--ink:#1c2420;--sub:#5a665f;--line:#dde3de;--accent:#cf4a0c;--pass:#23844f;--fail:#c93535;--review:#8a6a00;--manual:#6d7882;--tint:#eaf0ec}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#121714;--surface:#1a211d;--ink:#e6ece8;--sub:#98a69e;--line:#2b362f;--accent:#ff8d4f;--pass:#5fcf8e;--fail:#ff7d7d;--review:#e8b84a;--manual:#9aa6b0;--tint:#222c26;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#121714;--surface:#1a211d;--ink:#e6ece8;--sub:#98a69e;--line:#2b362f;--accent:#ff8d4f;--pass:#5fcf8e;--fail:#ff7d7d;--review:#e8b84a;--manual:#9aa6b0;--tint:#222c26;color-scheme:dark}
*{box-sizing:border-box}
body{background:var(--bg);color:var(--ink);font:15px/1.6 "Noto Sans TC","PingFang TC","Microsoft JhengHei",system-ui,sans-serif;margin:0;padding-inline:16px;padding-block:0 60px}
.wrap{max-width:900px;margin-inline:auto}
h1{font-size:1.7rem;margin:28px 0 4px}
.sub{color:var(--sub);margin:0 0 16px}
.sum{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px;margin:14px 0}
.sum div{background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:10px 14px}
.sum b{display:block;font-size:1.6rem;font-variant-numeric:tabular-nums}
.sum span{color:var(--sub);font-size:13px}
.bar{display:flex;height:10px;border-radius:6px;overflow:hidden;background:var(--tint);margin-bottom:14px}
.bar i{display:block}
.filters{position:sticky;top:env(safe-area-inset-top,0px);background:var(--bg);padding-block:10px;z-index:5;display:grid;gap:8px;border-bottom:1px solid var(--line)}
.filters input{width:100%;padding:8px 12px;border:1px solid var(--line);border-radius:8px;background:var(--surface);color:var(--ink);font:inherit}
.chips{display:flex;flex-wrap:wrap;gap:6px}
.chip{border:1px solid var(--line);background:var(--surface);color:var(--ink);border-radius:999px;padding:2px 12px;font:inherit;font-size:13px;cursor:pointer}
.chip[aria-pressed="true"]{background:var(--ink);color:var(--bg);border-color:var(--ink)}
h2{font-size:1.15rem;margin:26px 0 8px;padding-bottom:4px;border-bottom:2px solid var(--ink)}
details{background:var(--surface);border:1px solid var(--line);border-radius:10px;margin:8px 0}
details[data-s="fail"]{border-color:var(--fail)}
summary{cursor:pointer;padding:10px 12px;display:flex;gap:10px;align-items:baseline;flex-wrap:wrap}
summary .id{font-family:ui-monospace,Consolas,monospace;font-size:12px;color:var(--sub)}
summary .name{font-weight:500;flex:1;min-width:12em;overflow-wrap:anywhere}
.badge{font-size:12px;padding:1px 8px;border-radius:999px;border:1px solid currentColor;white-space:nowrap}
.b-pass{color:var(--pass)}.b-fail{color:var(--fail)}.b-review{color:var(--review)}.b-manual{color:var(--manual)}
.lv{font-family:ui-monospace,Consolas,monospace;font-size:11px;color:var(--sub)}
.body{padding:0 14px 14px;display:grid;gap:12px}
.body h4{margin:0;font-size:13px;color:var(--sub);letter-spacing:.04em}
.pre{color:var(--sub);font-size:14px}
ol.st{margin:0;padding-left:1.4em;display:grid;gap:4px}
ol.st .e{color:var(--sub);font-size:13.5px;display:block}
ul.ck{list-style:none;margin:0;padding:0;display:grid;gap:3px;font-size:14px}
ul.ck li.ok::before{content:"✓ ";color:var(--pass)}
ul.ck li.no::before{content:"✗ ";color:var(--fail)}
ul.ck .d{color:var(--sub);font-size:12.5px}
.err{background:var(--tint);border-left:3px solid var(--fail);padding:6px 10px;font-size:13px;overflow-wrap:anywhere}
.shots{display:flex;gap:10px;overflow-x:auto;padding-bottom:6px}
.shots figure{margin:0;flex:none;width:200px}
.shots img{width:200px;border:1px solid var(--line);border-radius:8px;display:block;cursor:zoom-in;background:var(--tint)}
.shots figcaption{font-size:12px;color:var(--sub);margin-top:4px}
#lb{position:fixed;inset:0;background:rgba(0,0,0,.85);display:none;z-index:20;align-items:center;justify-content:center;padding:16px}
#lb img{max-height:100%;max-width:100%;border-radius:8px}
#lb.on{display:flex}
.manual-note{color:var(--sub);font-size:14px}
</style>""")
    w('<div class="wrap">')
    w("<h1>記帳本 自動測試報告</h1>")
    w(f'<p class="sub">在 Android 15 模擬器上自動操作 debug 版 App、逐步截圖。版本 {esc(sha)}。共 {total} 個用例，其中 {auto} 個已自動執行，{counts["manual"]} 個需要手動。</p>')
    w('<div class="sum">')
    for k in ("pass", "review", "fail", "manual"):
        w(f'<div><b style="color:var(--{ {"pass":"pass","review":"review","fail":"fail","manual":"manual"}[k] })">{counts[k]}</b><span>{STATUS_LABEL[k]}</span></div>')
    w("</div>")
    w('<div class="bar">')
    for k in ("pass", "review", "fail", "manual"):
        w(f'<i style="width:{counts[k] / total * 100:.2f}%;background:var(--{k})"></i>')
    w("</div>")
    w('<div class="filters"><input type="search" id="q" placeholder="搜尋用例名稱或編號" aria-label="搜尋"><div class="chips" id="fs"></div></div>')

    cur = None
    for c, r, status in rows:
        mod = c["m"]
        if mod != cur:
            if cur is not None:
                w("</section>")
            w(f'<section class="mod"><h2>{esc(mod.strip("/").replace("/", " › "))}</h2>')
            cur = mod
        opn = " open" if status == "fail" else ""
        w(f'<details data-s="{status}" data-t="{esc((c["id"] + c["n"]).lower())}"{opn}>')
        w(f'<summary><span class="id">{c["id"]}</span><span class="name">{esc(c["n"])}</span><span class="lv">{c["l"]}</span><span class="badge b-{status}">{STATUS_LABEL[status]}</span></summary>')
        w('<div class="body">')
        if c["p"]:
            w(f'<div class="pre">前置條件：{esc(c["p"])}</div>')
        w("<div><h4>預期（測試清單）</h4><ol class='st'>")
        for s, e in zip(c["s"], c["e"]):
            w(f"<li>{esc(s)}<span class='e'>→ {esc(e)}</span></li>")
        w("</ol></div>")
        if r:
            w("<div><h4>自動檢查結果</h4><ul class='ck'>")
            for ck in r["checks"]:
                det = f" <span class='d'>（{esc(ck['detail'])}）</span>" if ck["detail"] else ""
                w(f"<li class='{'ok' if ck['ok'] else 'no'}'>{esc(ck['desc'])}{det}</li>")
            w("</ul></div>")
            if r["error"]:
                w(f'<div class="err">{esc(r["error"])}</div>')
            for n in r.get("notes", []):
                w(f'<div class="manual-note">{esc(n)}</div>')
            if r["shots"]:
                w("<div><h4>截圖</h4><div class='shots'>")
                for s in r["shots"]:
                    src = thumb(os.path.join(r["_base"], s["file"]))
                    if src:
                        w(f'<figure><img src="{src}" alt="{esc(s["label"])}"><figcaption>{esc(s["label"])}</figcaption></figure>')
                w("</div></div>")
            if status == "review":
                w('<div class="manual-note">數值與流程已自動檢查通過；外觀（圖示、版面、顏色）請看上面的截圖確認。</div>')
        else:
            reason = MANUAL[MANUAL_BY_ID.get(c["id"], "todo")]
            w(f'<div class="manual-note">未自動執行：{esc(reason)}。</div>')
        w("</div></details>")
    if cur is not None:
        w("</section>")
    w("</div>")
    w('<div id="lb"><img alt=""></div>')
    w("""<script>
const fs=document.getElementById('fs'),q=document.getElementById('q');let cur='';
[['','全部'],['fail','失敗'],['review','待看圖'],['pass','通過'],['manual','手動']].forEach(([v,t])=>{const b=document.createElement('button');b.className='chip';b.textContent=t;b.dataset.v=v;b.setAttribute('aria-pressed',v===''?'true':'false');b.onclick=()=>{cur=v;fs.querySelectorAll('.chip').forEach(x=>x.setAttribute('aria-pressed',x.dataset.v===v?'true':'false'));apply()};fs.append(b)});
function apply(){const t=q.value.trim().toLowerCase();document.querySelectorAll('details').forEach(d=>{d.hidden=!((!cur||d.dataset.s===cur)&&(!t||d.dataset.t.includes(t)))});document.querySelectorAll('.mod').forEach(m=>{m.hidden=![...m.querySelectorAll('details')].some(d=>!d.hidden)})}
q.oninput=apply;
const lb=document.getElementById('lb');document.addEventListener('click',e=>{const i=e.target.closest('.shots img');if(i){lb.querySelector('img').src=i.src;lb.classList.add('on')}else if(lb.classList.contains('on')){lb.classList.remove('on')}});
</script>""")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(parts))
    summary = dict(total=total, auto=auto, **{k: counts[k] for k in counts})
    with open(os.path.splitext(out_path)[0] + ".json", "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False)
    print(summary)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
