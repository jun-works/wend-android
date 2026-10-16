/** Offline layout renderer: real emulator PNGs + localized copy, no mock app UI. */
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = __dirname;
const repo = path.resolve(root, '../..');
const data = (file, type) => `data:${type};base64,${fs.readFileSync(file).toString('base64')}`;
const font = name => data(path.join(repo, 'app/src/main/res/font', name), 'font/ttf');
const escape = s => s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
const lines = a => a.map(escape).join('<br>');
const locales = process.argv.slice(2);
const targets = locales.length ? locales : fs.readdirSync(path.join(root,'locales')).filter(x=>x.endsWith('.json')).map(x=>x.slice(0,-5));
(async()=>{
 const browser = await chromium.launch({headless:true});
 const page = await browser.newPage({viewport:{width:1080,height:1920},deviceScaleFactor:1});
 const thumbnails=[];
 for(const locale of targets){
  const copy=JSON.parse(fs.readFileSync(path.join(root,'locales',locale+'.json')));
  const output=path.join(repo,'design/store-listing',locale,'images/phoneScreenshots');
  fs.mkdirSync(output,{recursive:true});
  for(const [i,slide] of copy.slides.entries()){
   const shot=data(path.join(root,'raw',locale,(slide.source || slide.id)+'.png'),'image/png');
   const html=`<!doctype html><html lang="${locale}"><head><meta charset="utf-8"><style>
@font-face{font-family:WendSans;src:url('${font('wend_sans_kr_regular.ttf')}');font-weight:400}
@font-face{font-family:WendSans;src:url('${font('wend_sans_kr_semi_bold.ttf')}');font-weight:600}
@font-face{font-family:WendBatang;src:url('${font('wend_batang_bold.ttf')}');font-weight:700}
*{box-sizing:border-box}body{margin:0;width:1080px;height:1920px;overflow:hidden;background:#F6F1E9;color:#241F1A;font-family:WendSans;--accent:${slide.accent};--wash:${slide.wash}}
.brand{position:absolute;left:72px;top:38px;font:700 44px WendBatang;letter-spacing:-2px}.brand span{color:var(--accent)}
.sequence{position:absolute;right:76px;top:59px;display:flex;gap:13px}.sequence b{width:8px;height:8px;border-radius:50%;background:#CDC3B6}.sequence b.active{background:var(--accent);width:32px;border-radius:8px}
.copy{position:absolute;top:120px;left:72px;right:60px}.eyebrow{color:var(--accent);font-size:23px;letter-spacing:2px;font-weight:600;margin-bottom:16px}
h1{font:700 76px/1.17 WendBatang;letter-spacing:-2.8px;margin:0 0 20px;word-break:keep-all}h1 em{font-style:normal;color:var(--accent)}
p{font-size:29px;line-height:1.46;margin:0;color:#65584D;letter-spacing:-.45px}
.land{position:absolute;left:0;right:0;top:800px;bottom:0;background:var(--wash);border-radius:380px 380px 0 0}
.orbit{position:absolute;width:1300px;height:1300px;left:-110px;top:645px;border:2px solid var(--accent);opacity:.12;border-radius:50%}
.device{position:absolute;top:490px;left:150px;width:780px;padding:8px;background:#FBF8F3;border:1px solid #CFC3B4;border-radius:38px;box-shadow:0 24px 46px #30241822}
.device img{display:block;width:762px;height:auto;border-radius:29px}
.globe .device{top:520px;left:60px;width:960px;height:1180px;padding:0;overflow:hidden;background:#F6F1E9;border-radius:48px}
.globe .device img{position:absolute;width:1850px;max-width:none;left:-446px;top:-320px;border-radius:0}
.globe .land{top:640px}.globe .orbit{top:460px}
.detail-note{position:absolute;top:1740px;width:100%;text-align:center;font-size:24px;color:#65584D}
</style></head><body class="${slide.layout || 'standard'}"><div class="land"></div><div class="orbit"></div><div class="brand">wend<span>.</span></div><div class="sequence">${copy.slides.map((_,j)=>`<b class="${i===j?'active':''}"></b>`).join('')}</div><section class="copy"><div class="eyebrow">${escape(slide.eyebrow)}</div><h1>${escape(slide.title[0])}<br><em>${escape(slide.title[1])}</em></h1><p>${lines(slide.body)}</p></section><div class="device"><img src="${shot}"></div>${slide.note ? `<div class="detail-note">${escape(slide.note)}</div>` : ''}</body></html>`;
   await page.setContent(html); await page.evaluate(()=>document.fonts.ready);
   await page.locator('.device img').evaluate(e=>e.decode());
   const check=await page.evaluate(()=>({copyBottom:document.querySelector('.copy').getBoundingClientRect().bottom,deviceTop:document.querySelector('.device').getBoundingClientRect().top,deviceBottom:document.querySelector('.device').getBoundingClientRect().bottom,overflow:document.querySelector('.copy').scrollWidth>document.querySelector('.copy').clientWidth}));
   if(check.copyBottom>check.deviceTop-15 || check.deviceBottom>1920 || check.overflow)throw Error(JSON.stringify({locale,slide:slide.id,...check}));
   const file=path.join(output,slide.id+'.png');
   await page.screenshot({path:file,omitBackground:false});
   thumbnails.push({locale,id:slide.id,src:data(file,'image/png')});
   console.log(locale,slide.id,check);
  }
 }
 const preview=path.join(repo,'design/store-listing/screenshots-preview.html');
 fs.writeFileSync(preview,`<!doctype html><html><meta charset="utf-8"><title>Wend · Store screenshots</title><style>body{background:#e8e1d7;font:16px system-ui;padding:24px;color:#30261f}main{display:grid;grid-template-columns:repeat(5,1fr);gap:20px}img{width:100%;border-radius:10px}p{margin:8px 0 20px}</style><h1>Wend · 한국어 / English</h1><main>${thumbnails.map(t=>`<section><img src="${t.src}"><p>${t.locale} · ${t.id}</p></section>`).join('')}</main></html>`);
 await page.setViewportSize({width:2000,height:1600});
 await page.goto('file://'+preview);await page.screenshot({path:path.join(repo,'design/store-listing/screenshots-preview.png'),fullPage:true});
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
