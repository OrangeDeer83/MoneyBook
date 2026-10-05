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

STATUS_LABEL = {"pass": "通過", "review": "通過・待看圖", "fail": "App 問題", "unknown": "測試未能判定", "manual": "手動"}


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


def main(res_dir, out_path, triage_path=None, user_path=None):
    triage = json.load(open(triage_path, encoding="utf-8")) if triage_path else {}
    user = json.load(open(user_path, encoding="utf-8")) if user_path else []
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
            t = triage.get(c["id"])
            if status == "fail" and t:
                status = {"app": "fail", "shots": "review", "pass": "pass"}.get(t["kind"], "unknown")
            if t:
                r["triage"] = t
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
:root{--bg:#f4f6f3;--surface:#fff;--ink:#1c2420;--sub:#5a665f;--line:#dde3de;--accent:#cf4a0c;--pass:#23844f;--fail:#c93535;--review:#8a6a00;--manual:#6d7882;--unknown:#b45309;--tint:#eaf0ec}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#121714;--surface:#1a211d;--ink:#e6ece8;--sub:#98a69e;--line:#2b362f;--accent:#ff8d4f;--pass:#5fcf8e;--fail:#ff7d7d;--review:#e8b84a;--manual:#9aa6b0;--unknown:#f59e0b;--tint:#222c26;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#121714;--surface:#1a211d;--ink:#e6ece8;--sub:#98a69e;--line:#2b362f;--accent:#ff8d4f;--pass:#5fcf8e;--fail:#ff7d7d;--review:#e8b84a;--manual:#9aa6b0;--unknown:#f59e0b;--tint:#222c26;color-scheme:dark}
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
details[data-s="fail"]{border-color:var(--fail)}details[data-s="unknown"]{border-color:var(--unknown)}
summary{cursor:pointer;padding:10px 12px;display:flex;gap:10px;align-items:baseline;flex-wrap:wrap}
summary .id{font-family:ui-monospace,Consolas,monospace;font-size:12px;color:var(--sub)}
summary .name{font-weight:500;flex:1;min-width:12em;overflow-wrap:anywhere}
.badge{font-size:12px;padding:1px 8px;border-radius:999px;border:1px solid currentColor;white-space:nowrap}
.b-pass{color:var(--pass)}.b-fail{color:var(--fail)}.b-review{color:var(--review)}.b-manual{color:var(--manual)}.b-unknown{color:var(--unknown)}
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
.tri{background:var(--tint);border-left:3px solid var(--unknown);padding:6px 10px;font-size:14px}.err{background:var(--tint);border-left:3px solid var(--fail);padding:6px 10px;font-size:13px;overflow-wrap:anywhere}
.shots{display:flex;gap:10px;overflow-x:auto;padding-bottom:6px}
.shots figure{margin:0;flex:none;width:200px}
.shots img{width:200px;border:1px solid var(--line);border-radius:8px;display:block;cursor:zoom-in;background:var(--tint)}
.shots figcaption{font-size:12px;color:var(--sub);margin-top:4px}
#lb{position:fixed;inset:0;background:rgba(0,0,0,.85);display:none;z-index:20;align-items:center;justify-content:center;padding:16px}
#lb img{max-height:100%;max-width:100%;border-radius:8px}
#lb.on{display:flex}
.rvbar{margin:12px 0;padding:10px 14px;border-radius:10px;border:1px solid var(--line);background:var(--surface);display:flex;gap:10px;align-items:center;flex-wrap:wrap}
.rvbar.todo{border-color:var(--unknown)}.rvbar b{font-variant-numeric:tabular-nums}
.rv{border-top:1px dashed var(--line);padding-top:10px;display:grid;gap:8px}
.rv .seg{display:inline-flex;border:1px solid var(--line);border-radius:8px;overflow:hidden;width:max-content;max-width:100%}
.rv .seg button{border:0;background:transparent;color:var(--sub);padding:5px 14px;font:inherit;font-size:13.5px;cursor:pointer;border-right:1px solid var(--line)}
.rv .seg button:last-child{border-right:0}
.rv .seg button[aria-pressed="true"][data-v="ok"]{background:var(--pass);color:var(--bg)}
.rv .seg button[aria-pressed="true"][data-v="bad"]{background:var(--fail);color:var(--bg)}
.rv .seg button[aria-pressed="true"][data-v="later"]{background:var(--manual);color:var(--bg)}
.rv textarea{width:100%;min-height:60px;resize:vertical;border:1px solid var(--line);border-radius:8px;padding:8px 10px;background:var(--bg);color:var(--ink);font:inherit}
.mine{font-size:12px;color:var(--sub)}
details[data-r="bad"]{border-color:var(--fail)}
.sync{font-size:12px;color:var(--sub);margin-left:auto}
.tbl{overflow-x:auto}table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:left;padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:top}th{color:var(--sub);font-weight:500;font-size:12px}.mono{font-family:ui-monospace,Consolas,monospace;font-size:12.5px}.manual-note{color:var(--sub);font-size:14px}
</style>""")
    w('<div class="wrap">')
    w("<h1>記帳本 自動測試報告</h1>")
    w(f'<p class="sub">在 Android 15 模擬器上自動操作 debug 版 App、逐步截圖。版本 {esc(sha)}。共 {total} 個用例，其中 {auto} 個已自動執行，{counts["manual"]} 個需要手動。</p>')
    w('<div class="sum">')
    for k in ("pass", "review", "fail", "unknown", "manual"):
        w(f'<div><b style="color:var(--{k})">{counts[k]}</b><span>{STATUS_LABEL[k]}</span></div>')
    w("</div>")
    w('<div class="bar">')
    for k in ("pass", "review", "fail", "unknown", "manual"):
        w(f'<i style="width:{counts[k] / total * 100:.2f}%;background:var(--{k})"></i>')
    w("</div>")
    if user:
        w('<h2>你的手測結果與自動測試的對照</h2><div class="tbl"><table><thead><tr><th>用例</th><th>你的結果與備註</th><th>自動測試有找到嗎？</th></tr></thead><tbody>')
        for u in user:
            w(f"<tr><td class='mono'>{esc(u['id'])}<br>{esc(u['name'])}</td><td>{esc(u['yours'])}</td><td><b class='b-{u['tag']}'>{esc(u['verdict'])}</b><br>{esc(u['detail'])}</td></tr>")
        w("</tbody></table></div>")
    w('<div class="rvbar" id="rvbar" role="status"></div>')
    w('<div class="filters"><input type="search" id="q" placeholder="搜尋用例名稱或編號" aria-label="搜尋"><div class="chips" id="fs"></div></div>')

    cur = None
    for c, r, status in rows:
        mod = c["m"]
        if mod != cur:
            if cur is not None:
                w("</section>")
            w(f'<section class="mod"><h2>{esc(mod.strip("/").replace("/", " › "))}</h2>')
            cur = mod
        opn = " open" if status in ("fail", "unknown") else ""
        w(f'<details data-id="{c["id"]}" data-s="{status}" data-r="" data-t="{esc((c["id"] + c["n"]).lower())}"{opn}>')
        w(f'<summary><span class="id">{c["id"]}</span><span class="name">{esc(c["n"])}</span><span class="lv">{c["l"]}</span><span class="badge b-{status}">{STATUS_LABEL[status]}</span><span class="mine"></span></summary>')
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
            if r.get("triage"):
                w(f'<div class="tri"><b>分析：</b>{esc(r["triage"]["note"])}</div>')
            elif r["error"]:
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
            if status == "review" and not r.get("triage"):
                w('<div class="manual-note">數值與流程已自動檢查通過；外觀（圖示、版面、顏色）請看上面的截圖確認。</div>')
        else:
            reason = MANUAL[MANUAL_BY_ID.get(c["id"], "todo")]
            w(f'<div class="manual-note">未自動執行：{esc(reason)}。</div>')
        w(f'<div class="rv" data-id="{c["id"]}"></div>')
        w("</div></details>")
    if cur is not None:
        w("</section>")
    w("</div>")
    w('<div id="lb"><img alt=""></div>')
    w("""<script>
(function(){
const SK='mb-review-v1',REV={};let db=null,syncTxt='只存在這個瀏覽器';const tm={};
try{Object.assign(REV,JSON.parse(localStorage.getItem(SK)||'{}'))}catch(e){}
const ls=()=>{try{localStorage.setItem(SK,JSON.stringify(REV))}catch(e){}};
const LBL={ok:'你：沒問題',bad:'你：有問題',later:'你：先跳過'};
const $$=s=>[...document.querySelectorAll(s)];
function persist(id,now){ls();if(!db)return;clearTimeout(tm[id]);const run=()=>{delete tm[id];const r=REV[id];const ref=db.doc('review/'+id);(r&&(r.s||r.note)?ref.set({s:r.s||'',note:r.note||'',at:Date.now()}):ref.delete()).catch(()=>{syncTxt='儲存失敗，暫存在瀏覽器';bar()})};now?run():tm[id]=setTimeout(run,600)}
function paint(id){const d=document.querySelector('details[data-id="'+id+'"]');if(!d)return;const r=REV[id]||{};d.dataset.r=r.s||'';d.querySelector('.mine').textContent=r.s?LBL[r.s]:'';const box=d.querySelector('.rv');box.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',b.dataset.v===r.s?'true':'false'));const ta=box.querySelector('textarea');if(document.activeElement!==ta)ta.value=r.note||''}
function setRv(id,patch){REV[id]=Object.assign(REV[id]||{},patch);paint(id);persist(id,'s' in patch);bar()}
function bar(){const todo=$$('details[data-s="review"]').filter(d=>!d.dataset.r).length;const all=$$('details[data-s="review"]').length;const bad=$$('details[data-r="bad"]').length;const el=document.getElementById('rvbar');el.className='rvbar'+(todo?' todo':'');el.innerHTML='';const t=document.createElement('span');t.innerHTML=todo?('還有 <b>'+todo+'</b> 個「待看圖」的用例你還沒檢視（共 '+all+' 個）。請看截圖，標記沒問題或有問題。'):('待看圖的 '+all+' 個用例都檢視過了。');el.append(t);if(todo){const b=document.createElement('button');b.className='chip';b.textContent='只看這些';b.onclick=()=>pick('todo');el.append(b)}const m=document.createElement('span');m.innerHTML='你標了 <b>'+bad+'</b> 個「有問題」';el.append(m);const s=document.createElement('span');s.className='sync';s.textContent=syncTxt;el.append(s)}
let pick=()=>{};
$$('.rv').forEach(box=>{const id=box.dataset.id;box.innerHTML='<h4>你的檢視</h4>';const seg=document.createElement('div');seg.className='seg';seg.setAttribute('role','group');[['ok','沒問題'],['bad','有問題'],['later','先跳過']].forEach(([v,t])=>{const b=document.createElement('button');b.type='button';b.dataset.v=v;b.textContent=t;b.setAttribute('aria-pressed','false');b.onclick=()=>setRv(id,{s:(REV[id]&&REV[id].s)===v?'':v});seg.append(b)});const ta=document.createElement('textarea');ta.placeholder='你實際看到什麼？（有問題時請寫下現象，方便我修改）';ta.setAttribute('aria-label',id+' 你看到的內容');ta.oninput=()=>setRv(id,{note:ta.value});box.append(seg,ta)});
// 篩選：加兩個和檢視有關的選項
const fs=document.getElementById('fs');let cur2='';
[['todo','待你看圖（未檢視）'],['bad','你標了有問題']].forEach(([v,t])=>{const b=document.createElement('button');b.className='chip';b.textContent=t;b.dataset.v2=v;b.setAttribute('aria-pressed','false');b.onclick=()=>pick(v);fs.append(b)});
pick=function(v){cur2=cur2===v?'':v;fs.querySelectorAll('.chip').forEach(x=>{if(x.dataset.v2)x.setAttribute('aria-pressed',x.dataset.v2===cur2?'true':'false');else x.setAttribute('aria-pressed','false')});if(cur2){const all=fs.querySelector('.chip[data-v=""]');}
 const q=document.getElementById('q').value.trim().toLowerCase();$$('details').forEach(d=>{const okR=!cur2||(cur2==='todo'?(d.dataset.s==='review'&&!d.dataset.r):(d.dataset.r==='bad'));d.hidden=!(okR&&(!q||d.dataset.t.includes(q)))});$$('.mod').forEach(m=>{m.hidden=![...m.querySelectorAll('details')].some(d=>!d.hidden)})};
fs.addEventListener('click',e=>{if(e.target.dataset&&e.target.dataset.v!==undefined)cur2=''},true);document.getElementById('q').addEventListener('input',()=>{cur2='';fs.querySelectorAll('[data-v2]').forEach(x=>x.setAttribute('aria-pressed','false'))});
Object.keys(REV).forEach(paint);bar();
(async()=>{try{db=await window.claude.use('db')}catch(e){}if(!db)return;syncTxt='已同步，下次打開還在';bar();
db.collection('review').onSnapshot(snap=>{const seen=new Set();snap.docs.forEach(x=>{const v=x.data()||{};seen.add(x.id);if(tm[x.id])return;REV[x.id]={s:v.s||'',note:v.note||''};paint(x.id)});Object.keys(REV).forEach(id=>{if(!seen.has(id)&&(REV[id].s||REV[id].note)&&!tm[id])persist(id,true)});ls();bar()},()=>{syncTxt='同步中斷，目前只存在這個瀏覽器';bar()})})();
})();
</script>""")
    w("""<script>
const fs=document.getElementById('fs'),q=document.getElementById('q');let cur='';
[['','全部'],['fail','App 問題'],['unknown','未能判定'],['review','待看圖'],['pass','通過'],['manual','手動']].forEach(([v,t])=>{const b=document.createElement('button');b.className='chip';b.textContent=t;b.dataset.v=v;b.setAttribute('aria-pressed',v===''?'true':'false');b.onclick=()=>{cur=v;fs.querySelectorAll('.chip').forEach(x=>x.setAttribute('aria-pressed',x.dataset.v===v?'true':'false'));apply()};fs.append(b)});
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
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None, sys.argv[4] if len(sys.argv) > 4 else None)
