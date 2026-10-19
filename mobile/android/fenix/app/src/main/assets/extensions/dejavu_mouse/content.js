const linkOf = (event) => {
  const a = event.target.closest && event.target.closest("a[href]");
  return a && a.protocol !== "javascript:" ? a.href : "";
};
const send = (message) => browser.runtime.sendMessage(message).catch(() => {});
let hovered = "";
const hover = (url) => {
  if (url !== hovered) {
    hovered = url;
    send({ type: "hover", url });
  }
};
document.addEventListener("pointerover", (e) => e.pointerType === "mouse" && hover(linkOf(e)), true);
document.addEventListener("pointerout", (e) => e.pointerType === "mouse" && !e.relatedTarget && hover(""), true);
document.addEventListener("auxclick", (e) => {
  const url = e.button === 1 ? linkOf(e) : "";
  if (url) {
    e.preventDefault();
    send({ type: "open", url });
  }
}, true);
