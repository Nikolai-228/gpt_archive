(() => {
  const {
    convertImages,
    initAutocompleteRecursive,
    initIndicator,
    urlWithParams,
  } = window.istu.archive;

  const form = document.getElementById("galleryFilter");
  initAutocompleteRecursive(form);
  const indicator = initIndicator(document.getElementById("indicator"));

  function scrollToImages() {
    document.getElementById("query-results").scrollIntoView();
  }

  async function loadGallery() {
    try {
      const res = await fetch(location.href, {
        headers: { "HX-Request": "true" },
      });
      indicator.show({
        type: "progress",
        msg: "Запрос обрабатывается…",
      });
      const content = await res.text();

      if (!res.ok)
        throw new Error(content);

      const results = document.getElementById("query-results");
      results.outerHTML = content;
      enhancePagination();
      convertImages(document.getElementById("query-results"));
      indicator.clear();
    } catch (e) {
      indicator.show({
        type: "error",
        msg: `Во время выполнения запроса произошла ошибка: ${e.message}`,
      });
    }
  }

  function enhancePagination() {
    document.querySelectorAll("#query-results .page-link")
      .forEach((link) => {
        if (!link.href)
          return;

        link.addEventListener("click", (event) => {
          event.preventDefault();
          history.pushState({}, "", link.href);
          loadGallery();
          scrollToImages();
        });
      });
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();

    const data = new FormData(form);
    const url = urlWithParams(location.pathname, data);
    history.pushState({}, "", url);
    loadGallery();
    scrollToImages();
  });

  window.addEventListener("popstate", () => {
    // TODO: Maybe somehow restore form inputs? -M 2025.02.17
    loadGallery();
  });

  enhancePagination();

  const filtersContainer = document.getElementById("filtersContainer");
  const filterTemplate = filtersContainer.querySelector("[data-filter-template]");
  const addFilter = filtersContainer.querySelector("[data-filter-add]");

  const filterKindTemplates = Array.from(
    filtersContainer.querySelectorAll("[data-filter-template-kind]")
  ).reduce((acc, el) => {
    const kind = el.getAttribute("data-filter-template-kind");
    acc[kind] = el;
    return acc;
  }, {});

  let personNumber = 1;

  addFilter.addEventListener("click", () => {
    const card = filterTemplate.content.cloneNode(true).firstChild;
    filtersContainer.appendChild(card);
    initFilter(card);
    personNumber++;
  });

  function initCollapsible(el) {
    const btn = el.querySelector(".collapsible-button");
    const content = el.querySelector(".collapsible-content");

    function resize() {
      content.style.display = "block";
      content.style.maxHeight = `${content.scrollHeight}px`;
    }

    function toggle() {
      const expanded = btn.getAttribute("aria-expanded") === "true";
      if (expanded) {
        btn.title = "Развернуть";
        content.style.maxHeight = "0";
      } else {
        btn.title = "Свернуть";
        resize();
      }
      btn.setAttribute("aria-expanded", !expanded);
    }

    btn.addEventListener("click", () => toggle());
    toggle();

    return {
      resize,
      toggle,
    };
  }

  function initFilter(el) {
    const collapsible = initCollapsible(el);

    const title = el.querySelector("[data-filter-title]");
    title.innerText = `Человек ${personNumber}`;

    const kind = el.querySelector("[data-filter-kind]");
    function onChangeKind() {
      const fields = el.querySelector("[data-filter-fields]");
      const template = filterKindTemplates[kind.value];
      const newFields = template.content.cloneNode(true).firstChild;
      fields.replaceWith(newFields);
      collapsible.resize();
      initAutocompleteRecursive(newFields);
    }
    onChangeKind();
    kind.addEventListener("change", () => onChangeKind());

    const remove = el.querySelector("[data-filter-remove]");
    remove?.addEventListener("click", () => {
      el.remove();
    });
  }
})();
