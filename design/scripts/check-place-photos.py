"""Exercise the place album in Chrome and capture its actual click-driven motion.
Run: python3 design/scripts/check-place-photos.py [--frames]
Uses the android-prototype skill's dependency-free Chrome runner.
"""
from pathlib import Path
from types import SimpleNamespace
import importlib.util
import json
import re
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SKILL = Path('/Users/jun/Downloads/skills/android-prototype-skill/skills/android-prototype')
spec = importlib.util.spec_from_file_location('proto', SKILL / 'scripts/proto.py')
proto = importlib.util.module_from_spec(spec)
spec.loader.exec_module(proto)
SOURCE = ROOT / 'design/wend-prototype/app.src.html'
OUT = ROOT / 'design/wend-prototype/place-review'
OUT.mkdir(exist_ok=True)
built = proto.build(SOURCE)
html = built.read_text()

TEST = r'''
<script>
var testErrors = [];
window.addEventListener('error', function(e) { testErrors.push(e.message); });
var oldConsoleError = console.error;
console.error = function() { testErrors.push(Array.from(arguments).join(' ')); oldConsoleError.apply(console, arguments); };
document.addEventListener('app:ready', async function() {
  var passed = [], wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  function ok(value, name) { if (!value) throw Error(name); passed.push(name); }
  function tap(selector) { var el = document.querySelector(selector); if (!el) throw Error('Missing: ' + selector); el.click(); }
  function back() { document.dispatchEvent(new KeyboardEvent('keydown', { key:'Escape', bubbles:true })); }
  function more(stop) { return '#tp-days [data-action="openPlacePhotos"][data-s="' + stop + '"]'; }
  function tile(key) { return '#pg-grid [data-k="' + key + '"]'; }
  try {
    App.reset('trip'); await wait(400);
    ok(!document.querySelector(more(0)), '6 photos: every tile opens a photo directly');
    tap('#tp-days [data-action="viewTrip"][data-s="0"][data-k="2"]'); await wait(500);
    ok(App.current() === 'viewer' && App.state.vw.i === 2 && App.state.vw.items.length === 6, 'ordinary detail tile opens correct photo');
    back(); await wait(500);
    ok(App.current() === 'trip', 'direct viewer returns to trip');
    var detail = document.querySelector('#trip .content');
    var entry = document.querySelector(more(1));
    detail.scrollTop += entry.getBoundingClientRect().top - detail.getBoundingClientRect().top - 400;
    var detailY = detail.scrollTop;
    ok(entry.textContent.includes('+33'), 'overflow count includes all 33 photos hidden behind the tile');
    tap(more(1)); await wait(500);
    ok(App.current() === 'place-photos' && document.querySelectorAll('#pg-grid button').length === 38, 'overflow opens all 38 photos in this visit');
    ok(document.getElementById('pg-name').textContent === '감천문화마을', 'place name is carried into album');
    ok(App.stack.slice(-2).join(',') === 'trip,place-photos', 'album is pushed above trip');
    var container = document.getElementById('pg-scroll');
    container.scrollTop = 700;
    var savedY = container.scrollTop;
    tap(tile(21)); await wait(500);
    ok(vwCur().k === 21 && document.getElementById('vw-count').textContent === '22 / 38', 'album opens the selected photo with full place count');
    var track = document.getElementById('vw-track');
    track.scrollLeft = track.clientWidth * 36;
    track.dispatchEvent(new Event('scroll')); await wait(100);
    ok(vwCur().k === 36 && document.getElementById('vw-count').textContent === '37 / 38', 'viewer pages beyond the six preview photos');
    tap('#vw-acts [data-action="vwBest"]');
    back(); await wait(500);
    ok(App.current() === 'place-photos', 'viewer system back returns to album');
    ok(document.querySelector(tile(36) + ' .pg-star'), 'best-shot change appears on the same album photo');
    var r = document.querySelector(tile(36)).getBoundingClientRect(), c = container.getBoundingClientRect();
    ok(r.top >= c.top - 1 && r.bottom <= c.bottom + 1, 'last viewed photo is visible on return after paging');
    var restoredY = container.scrollTop;
    tap(tile(36)); await wait(500); back(); await wait(500);
    ok(Math.abs(container.scrollTop - restoredY) < 2, 'back without paging preserves gallery scroll');
    tap('#place-photos [data-back]'); await wait(500);
    ok(App.current() === 'trip' && Math.abs(detail.scrollTop - detailY) < 2, 'album app-bar back preserves trip reading position');
    tap(more(1)); await wait(500);
    ok(container.scrollTop === 0, 'reopening album starts at its header');
    tap(tile(7)); await wait(500); tap('#vw-acts [data-action="vwOut"]'); await wait(1200);
    ok(App.current() === 'place-photos' && !document.querySelector(tile(7)), 'remove from trip returns to album and removes exact photo');
    ok(document.getElementById('pg-count').textContent === '사진 37장', 'album count updates after removal');
    tap(tile(8)); await wait(500); tap('#vw-acts [data-action="vwTrash"]');
    back(); await wait(100);
    ok(App.current() === 'viewer' && !document.querySelector('#sys-trash.open'), 'back dismisses confirmation without leaving viewer');
    tap('#vw-acts [data-action="vwTrash"]'); tap('#sys-trash [data-action="trashOk"]'); await wait(500);
    ok(App.current() === 'place-photos' && !document.querySelector(tile(8)) && tripPhotos(1).length === 36, 'trash confirmation removes exact photo and updates album');
    back(); await wait(500);
    tap(more(7)); await wait(500);
    ok(document.querySelectorAll('#pg-grid button').length === 8 && document.getElementById('pg-stop').textContent === '8', 'same-name station on another day keeps its own photos');
    back(); await wait(500);
    BUSAN.stops[1].place = esc('해 질 무렵 오래 머물렀던 골목 & 바다');
    tap(more(1)); await wait(500);
    ok(document.getElementById('pg-name').textContent.includes('& 바다'), 'renamed place is decoded and shown safely');
    ok(document.getElementById('pg-name').scrollWidth <= document.getElementById('pg-name').clientWidth, 'long place name wraps without horizontal overflow');
    back(); await wait(500);
    App.reducedMotion = true;
    tap(more(1)); tap(tile(1)); back(); back();
    ok(App.current() === 'trip' && !document.querySelector('.photo-flight,.pg-under,.photo-moving'), 'reduced motion keeps navigation and leaves no overlays');
    App.reducedMotion = false;
    tap(more(1)); back(); await wait(500);
    ok(App.current() === 'trip' && !document.querySelector('.photo-flight,.pg-under,.photo-moving'), 'rapid back during opening cleans animation state');
    ok(!testErrors.length, 'no runtime errors');
    document.body.insertAdjacentHTML('beforeend', '<pre id="flow-test-result"></pre>');
    document.getElementById('flow-test-result').textContent = JSON.stringify({ passed:passed, errors:testErrors });
  } catch(e) {
    document.body.insertAdjacentHTML('beforeend', '<pre id="flow-test-result"></pre>');
    document.getElementById('flow-test-result').textContent = JSON.stringify({ passed:passed, failure:e.message, errors:testErrors });
  }
});
</script>
'''
DEMO = r'''
<script>
document.addEventListener('app:ready', function() {
  App.reset('trip');
  setTimeout(function() {
    var el = document.querySelector('#tp-days [data-action="openPlacePhotos"][data-s="1"]');
    var sc = document.querySelector('#trip .content');
    sc.scrollTop += el.getBoundingClientRect().top - sc.getBoundingClientRect().top - 410;
  }, 100);
  setTimeout(function() { document.querySelector('#tp-days [data-action="openPlacePhotos"][data-s="1"]').click(); }, 900);
  setTimeout(function() { document.querySelector('#pg-grid [data-k="4"]').click(); }, 1800);
  setTimeout(function() { App.back(); }, 2700);
  setTimeout(function() { App.back(); }, 3600);
});
</script>
'''
with tempfile.TemporaryDirectory(prefix='wend-place-') as temp:
    page = Path(temp) / 'flow.html'
    page.write_text(html.replace('</body>', TEST + '</body>'))
    dom = proto.run_chrome(['--virtual-time-budget=25000', '--run-all-compositor-stages-before-draw', '--dump-dom'], proto.file_url(page, '', 'trip'), (800, 1200))
    matches = re.findall(r'<pre id="flow-test-result">(.*?)</pre>', dom, re.S)
    matches = [match for match in matches if match.strip().startswith('{')]
    if not matches:
        raise SystemExit('No test result produced by Chrome')
    import html as html_module
    result = json.loads(html_module.unescape(matches[-1]))
    (OUT / 'interaction-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps(result, ensure_ascii=False, indent=2), flush=True)
    if result.get('failure') or result['errors']:
        raise SystemExit(1)
    if '--frames' in sys.argv:
        page.write_text(html.replace('</body>', DEMO + '</body>'))
        for name, times in [('album-open', [.8, .95, 1.08, 1.22, 1.5]), ('photo-open', [1.7, 1.85, 2, 2.15, 2.4]), ('photo-return', [2.6, 2.75, 2.9, 3.05, 3.3]), ('album-return', [3.5, 3.65, 3.8, 3.95, 4.2])]:
            proto.cmd_frames(SimpleNamespace(file=str(page), screen='trip', at=times, o=str(OUT / (name + '.png'))))
