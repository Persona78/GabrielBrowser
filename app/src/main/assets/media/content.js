// Gabriel Media Detector: procura media diretamente descarregável na página
// e envia a lista à app (mensagem nativa "browser"). Só ficheiros http(s).
(function () {
  "use strict";
  if (window.__gabrielMedia) return;
  window.__gabrielMedia = true;

  var seen = new Set();
  var timer = null;
  var MIN_IMG = 200; // ignora ícones e imagens pequenas

  function abs(u) {
    if (!u) return null;
    try { return new URL(u, location.href).href; } catch (e) { return null; }
  }

  function collect() {
    var items = [];
    function add(u, kind, w, h) {
      if (!u) return;
      var a = abs(u);
      if (!a || !/^https?:/i.test(a) || seen.has(a)) return;
      seen.add(a);
      items.push({ url: a, kind: kind, w: w || 0, h: h || 0 });
    }

    document.querySelectorAll("video").forEach(function (v) {
      add(v.currentSrc || v.src, "video", v.videoWidth, v.videoHeight);
      v.querySelectorAll("source").forEach(function (s) { add(s.src, "video", v.videoWidth, v.videoHeight); });
    });
    document.querySelectorAll("audio").forEach(function (a) {
      add(a.currentSrc || a.src, "audio");
      a.querySelectorAll("source").forEach(function (s) { add(s.src, "audio"); });
    });
    document.querySelectorAll("img").forEach(function (i) {
      if (i.naturalWidth >= MIN_IMG && i.naturalHeight >= MIN_IMG) {
        add(i.currentSrc || i.src, "image", i.naturalWidth, i.naturalHeight);
      }
    });

    if (items.length) {
      try {
        browser.runtime.sendNativeMessage("browser", {
          type: "media",
          page: location.href,
          items: items.slice(0, 100)
        }).catch(function () {});
      } catch (e) {}
    }
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(collect, 1200);
  }

  try {
    new MutationObserver(schedule).observe(document.documentElement, {
      childList: true, subtree: true, attributes: true, attributeFilter: ["src"]
    });
  } catch (e) {}
  document.addEventListener("loadedmetadata", schedule, true);
  document.addEventListener("load", schedule, true);
  window.addEventListener("load", schedule);
  schedule();

  // ------------------------------------------------------------ modo de leitura
  // A app pede {cmd:"reader"} por uma porta nativa (só no quadro principal). Constrói uma versão
  // limpa do artigo com elementos novos (nunca copia atributos nem scripts da página).
  if (window === window.top) {
    try {
      var port = browser.runtime.connectNative("browser");
      port.onMessage.addListener(function (m) {
        if (m && m.cmd === "reader") {
          var ok = false;
          try { ok = reader(); } catch (e) { ok = false; }
          if (!ok) { try { port.postMessage({ type: "reader", ok: false }); } catch (e) {} }
        }
      });
    } catch (e) {}
  }

  var ALLOWED = { P: 1, H1: 1, H2: 1, H3: 1, H4: 1, UL: 1, OL: 1, LI: 1, BLOCKQUOTE: 1, PRE: 1, CODE: 1,
    EM: 1, STRONG: 1, B: 1, I: 1, A: 1, IMG: 1, FIGURE: 1, FIGCAPTION: 1, BR: 1 };
  var SKIP = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, IFRAME: 1, OBJECT: 1, EMBED: 1, FORM: 1, NAV: 1, ASIDE: 1,
    BUTTON: 1, INPUT: 1, SELECT: 1, TEXTAREA: 1, SVG: 1, CANVAS: 1, VIDEO: 1, AUDIO: 1, FOOTER: 1 };

  function score(el) {
    var ps = el.querySelectorAll("p"), total = 0, links = 0;
    for (var i = 0; i < ps.length; i++) {
      var t = ps[i].textContent.trim().length;
      if (t < 60) continue;
      total += t;
      var as = ps[i].querySelectorAll("a");
      for (var j = 0; j < as.length; j++) links += as[j].textContent.length;
    }
    return total ? total * (1 - Math.min(1, links / total)) : 0;
  }

  function pick() {
    var list = Array.prototype.slice.call(
      document.querySelectorAll("article, main, [role=main], section, div"), 0, 2500);
    var best = null, bestScore = 0, scores = new Map();
    list.forEach(function (el) {
      var sc = score(el);
      scores.set(el, sc);
      if (sc > bestScore) { bestScore = sc; best = el; }
    });
    if (!best || bestScore < 300) return null;
    var deepest = best;
    list.forEach(function (el) {
      if (el !== best && best.contains(el) && deepest.contains(el) && scores.get(el) >= bestScore * 0.8) deepest = el;
    });
    return deepest;
  }

  function walk(src, dst, top) {
    for (var n = src.firstChild; n; n = n.nextSibling) {
      if (n.nodeType === 3) {
        var tx = n.nodeValue;
        if (!tx || !tx.trim()) continue;
        if (top) {
          if (tx.trim().length < 20) continue;
          var p = document.createElement("p");
          p.textContent = tx.trim();
          dst.appendChild(p);
        } else {
          dst.appendChild(document.createTextNode(tx));
        }
        continue;
      }
      if (n.nodeType !== 1) continue;
      var tag = n.tagName.toUpperCase();
      if (SKIP[tag] || n.getAttribute("aria-hidden") === "true" || n.hidden) continue;
      if (ALLOWED[tag]) {
        var el = document.createElement(tag.toLowerCase());
        if (tag === "A") {
          var h = abs(n.getAttribute("href"));
          if (!h || !/^https?:/i.test(h)) { walk(n, dst, top); continue; }
          el.href = h;
          el.rel = "noopener noreferrer";
        } else if (tag === "IMG") {
          var u = abs(n.currentSrc || n.src || n.getAttribute("data-src"));
          if (!u || !/^https?:/i.test(u)) continue;
          if (n.naturalWidth && n.naturalWidth < 120) continue;
          el.src = u;
          el.alt = n.alt || "";
          el.loading = "lazy";
          el.style.cssText = "max-width:100%;height:auto;border-radius:6px";
          dst.appendChild(el);
          continue;
        }
        dst.appendChild(el);
        walk(n, el, false);
        if ((tag === "P" || tag === "LI") && !el.textContent.trim() && !el.querySelector("img")) dst.removeChild(el);
      } else {
        walk(n, dst, top);
      }
    }
  }

  function reader() {
    if (window.__gabrielReader) { location.reload(); return true; }
    var root = pick();
    if (!root) return false;
    var out = document.createElement("div");
    walk(root, out, true);
    if (out.textContent.trim().length < 300) return false;

    var dark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    var head = document.createElement("head");
    var meta = document.createElement("meta");
    meta.name = "viewport";
    meta.content = "width=device-width, initial-scale=1";
    head.appendChild(meta);
    var title = document.createElement("title");
    title.textContent = document.title;
    head.appendChild(title);

    var body = document.createElement("body");
    body.style.cssText = "margin:0;padding:20px 18px 60px;font:19px/1.7 sans-serif;word-wrap:break-word;" +
      "background:" + (dark ? "#1b1f23" : "#fbfaf7") + ";color:" + (dark ? "#d7dbe0" : "#1c1f23");
    var wrap = document.createElement("div");
    wrap.style.cssText = "max-width:40em;margin:0 auto";
    if (!out.querySelector("h1")) {
      var h1 = document.createElement("h1");
      h1.textContent = document.title;
      h1.style.cssText = "font-size:1.5em;line-height:1.3";
      wrap.appendChild(h1);
    }
    wrap.appendChild(out);
    var links = wrap.querySelectorAll("a");
    for (var i = 0; i < links.length; i++) links[i].style.color = dark ? "#8ab4f8" : "#0b6b4d";
    var pres = wrap.querySelectorAll("pre");
    for (var k = 0; k < pres.length; k++) pres[k].style.cssText = "overflow:auto;font-size:14px;background:" + (dark ? "#2a2f35" : "#eee") + ";padding:10px;border-radius:6px";
    body.appendChild(wrap);

    var de = document.documentElement;
    while (de.firstChild) de.removeChild(de.firstChild);
    de.appendChild(head);
    de.appendChild(body);
    window.scrollTo(0, 0);
    window.__gabrielReader = true;
    return true;
  }
})();
