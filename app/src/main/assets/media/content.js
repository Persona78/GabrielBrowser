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
})();
