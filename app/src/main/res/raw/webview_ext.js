// The persistent rule also covers footer nodes replaced by the source page's
// client-side router without another main-frame navigation.
(() => {
  const styleId = "ahlib-footer-compositing-fix";
  const installStyle = () => {
    if (document.getElementById(styleId)) {
      return;
    }
    const parent = document.head || document.documentElement;
    if (!parent) {
      return;
    }
    const style = document.createElement("style");
    style.id = styleId;
    style.textContent = `
                .wap-footer {
                    -webkit-transform: translateZ(0) !important;
                    transform: translateZ(0) !important;
                    -webkit-backface-visibility: hidden !important;
                    backface-visibility: hidden !important;
                }
            `;
    parent.appendChild(style);
  };

  installStyle();
  if (!window.__ahlibFooterCompositingFixObserver) {
    const observer = new MutationObserver(() => installStyle());
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
    });
    window.__ahlibFooterCompositingFixObserver = observer;
  }
})();
