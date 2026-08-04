(() => {
  if (window.__ahlibReservationRequestOverrideInstalled) {
    return;
  }

  const targetHost = "www.lib.ah.cn";
  const targetPath = "/api-server/pc/room/appointDataPage";
  const rewriteRequestUrl = (value) => {
    let url;
    try {
      url = new URL(value.toString(), window.location.href);
    } catch (_error) {
      return value;
    }
    if (
      url.protocol !== "https:" ||
      url.hostname !== targetHost ||
      url.pathname !== targetPath
    ) {
      return value;
    }
    url.searchParams.delete("type");
    url.searchParams.set("status", "1");
    return url.toString();
  };

  const originalXhrOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, ...args) {
    return originalXhrOpen.call(this, method, rewriteRequestUrl(url), ...args);
  };

  const originalFetch = window.fetch;
  if (typeof originalFetch === "function") {
    window.fetch = function (input, init) {
      if (typeof Request !== "undefined" && input instanceof Request) {
        const rewrittenUrl = rewriteRequestUrl(input.url);
        if (rewrittenUrl !== input.url) {
          input = new Request(rewrittenUrl, input);
        }
      } else {
        input = rewriteRequestUrl(input);
      }
      return originalFetch.call(this, input, init);
    };
  }

  window.__ahlibReservationRequestOverrideInstalled = true;
})();
