/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/*
 * This web extension looks for known icon tags, collects URLs and available
 * meta data (e.g. sizes) and passes that to the app code.
 */

/**
 * Takes a DOMTokenList and returns a String array.
 */
function sizesToList(sizes) {
  if (sizes == null) {
    return [];
  }

  if (!(sizes instanceof DOMTokenList)) {
    return [];
  }

  return Array.from(sizes);
}

function collect_link_icons(icons, rel) {
  document
    .querySelectorAll('link[rel="' + rel + '"]')
    .forEach(function (currentValue) {
      icons.push({
        type: rel,
        href: currentValue.href,
        sizes: sizesToList(currentValue.sizes),
        mimeType: currentValue.type,
      });
    });
}

function collect_meta_property_icons(icons, property) {
  document
    .querySelectorAll('meta[property="' + property + '"]')
    .forEach(function (currentValue) {
      icons.push({
        type: property,
        href: currentValue.content,
      });
    });
}

function collect_meta_name_icons(icons, name) {
  document
    .querySelectorAll('meta[name="' + name + '"]')
    .forEach(function (currentValue) {
      icons.push({
        type: name,
        href: currentValue.content,
      });
    });
}

let icons = [];

collect_link_icons(icons, "icon");
collect_link_icons(icons, "shortcut icon");
collect_link_icons(icons, "fluid-icon");
collect_link_icons(icons, "apple-touch-icon");
collect_link_icons(icons, "image_src");
collect_link_icons(icons, "apple-touch-icon image_src");
collect_link_icons(icons, "apple-touch-icon-precomposed");

collect_meta_property_icons(icons, "og:image");
collect_meta_property_icons(icons, "og:image:url");
collect_meta_property_icons(icons, "og:image:secure_url");

collect_meta_name_icons(icons, "twitter:image");
collect_meta_name_icons(icons, "msapplication-TileImage");

const PAGE_ICON_SIZE = 256;
const PAGE_ICON_TIMEOUT_MS = 5000;

/**
 * Loads an icon of a page opened over http the way the page loads its own images, so the exception the user gave the
 * page from HTTPS-Only Mode applies to it, and returns it drawn as a PNG data URL, or null. The app never downloads it
 * over http then, nor decodes anything the network sent.
 */
function drawPageIcon(href) {
  return new Promise(resolve => {
    const image = new Image();
    const timeout = setTimeout(() => resolve(null), PAGE_ICON_TIMEOUT_MS);
    image.onload = () => {
      clearTimeout(timeout);
      try {
        const width = image.naturalWidth || PAGE_ICON_SIZE;
        const height = image.naturalHeight || PAGE_ICON_SIZE;
        const scale = Math.min(1, PAGE_ICON_SIZE / Math.max(width, height));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(width * scale));
        canvas.height = Math.max(1, Math.round(height * scale));
        canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/png"));
      } catch (e) {
        resolve(null);
      }
    };
    image.onerror = () => {
      clearTimeout(timeout);
      resolve(null);
    };
    image.src = href;
  });
}

async function pageIcons() {
  if (document.location.protocol !== "http:") {
    return icons;
  }
  const drawn = await Promise.all(
    icons.map(async icon => {
      if (!icon.href || !icon.href.startsWith("http:")) {
        return icon;
      }
      const href = await drawPageIcon(icon.href);
      return href ? { ...icon, href, mimeType: "image/png" } : null;
    })
  );
  return drawn.filter(icon => icon);
}

pageIcons().then(found => {
  let message = {
    url: document.location.href,
    icons: found,
  };

  browser.runtime.sendNativeMessage("MozacBrowserIcons", message);
});
