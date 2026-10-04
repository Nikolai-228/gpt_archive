(() => {
  const { urlWithParams } = window.istu.archive;

  const extractId = (src) => {
    const match = /\/images\/([a-z0-9]+)\.jpg$/.exec(src);
    if (match) {
      return match[1];
    }
  }

  function processImage(img) {
    const success = img.complete && img.naturalHeight > 0;
    if (success)
      return;

    const id = extractId(img.src);
    const newSrc = urlWithParams("/image-convert", { id });
    img.src = newSrc;
  }

  function onImageError({ target }) {
    target.removeEventListener("error", onImageError);
    processImage(target);
  }

  window.istu.archive.convertImages = function (under) {
    under.querySelectorAll("[data-image-convert]").forEach((img) => {
      if (!img.complete) {
        img.addEventListener("error", onImageError);
        return;
      }
      processImage(img);
    });
  }

  window.istu.archive.convertImages(document);
})();
