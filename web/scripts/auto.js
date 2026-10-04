(() => {
  const { urlWithParams } = window.istu.archive;

  // Older browsers don't have this
  // TOOD: Maybe polyfill? -M 2024.12.07
  const getAbortController = "AbortController" in window
    ? () => new AbortController()
    : () => null;

  async function getSuggestions(params, abort) {
    const url = urlWithParams("/api/suggest", params);
    const res = await fetch(url, {
      signal: abort?.signal,
    });
    if (!res.ok)
      throw new Error(await res.text());

    return await res.text();
  }

  const URL_REGEXP = /^https?:\/\//;

  /** Инициализирует автодополнение на элементе
    *
    * В объекте с параметрами можно передать функцию `onchange`, которая будет
    * вызываться при выборе одного из вариантов автодополнения.
    *
    * @param el {HTMLElement} - элемент
    * @param opts {object} - дополнительные параметры
    */
  window.istu.archive.initAutocomplete = function (el, opts) {
    const innerInput = el.querySelector("[data-autocomplete-inner]");
    const visibleInput = el.querySelector("[data-autocomplete-visible]");

    const target = el.getAttribute("data-autocomplete");
    const data = el.hasAttribute("data-autocomplete-data")
      ? JSON.parse(el.getAttribute("data-autocomplete-data"))
      : {};

    if (!innerInput || !visibleInput)
      return;

    visibleInput.disabled = innerInput.disabled;
    const validityClass = innerInput.required ? "is-invalid" : "is-caution";

    let currentFocus = -1;

    el.addEventListener('autocomplete:select', (evt) => {
      const { value, label } = evt.detail;
      innerInput.value = value;
      visibleInput.value = label;
      opts && opts.onchange && opts.onchange(evt.detail);
      closeAllLists();
    });

    let loadAbort;
    function showSuggestions() {
      loadAbort?.abort();
      loadAbort = getAbortController();
      const value = visibleInput.value;
      currentFocus = -1;

      const params = { ...data, target, q: value };
      getSuggestions(params, loadAbort).then((data) => {
        closeAllLists();
        el.insertAdjacentHTML("beforeend", data);
      }).catch((err) => {
        if (!(err instanceof DOMException && err.name === "AbortError"))
          throw err;
      });
    }

    function checkValidity() {
      visibleInput.classList.toggle(
        validityClass,
        !innerInput.value && (visibleInput.value || innerInput.required),
      );
    }

    function onBlur(event) {
      // Удалить глобальные обработчики, если элемента больше нет в дереве
      !el.isConnected && document.removeEventListener("click", onBlur);
      if (!el.contains(event.target)) {
        closeAllLists();
        checkValidity();
      }
    }

    visibleInput.addEventListener("focus", (e) => {
      document.addEventListener("click", onBlur);
      visibleInput.classList.remove("is-invalid");
      !innerInput.value && showSuggestions();
    });

    visibleInput.addEventListener("input", (e) => {
      innerInput.value = "";
      showSuggestions();

      if (data["allow-iri"]) {
        const value = visibleInput.value;
        if (URL_REGEXP.test(value)) {
          innerInput.value = value;
        }
      }
    });

    visibleInput.addEventListener("keydown", (e) => {
      const x = el
        .querySelector(".autocomplete-items")?
        .getElementsByTagName("li");
      switch (e.keyCode) {
      // Down arrow
      case 40:
        currentFocus++;
        addActive(x);
        break;
      // Up arrow
      case 38:
        currentFocus--;
        addActive(x);
        break;
      // Enter
      case 13:
        e.preventDefault();
        if (currentFocus > -1) {
          x && x[currentFocus].click();
        }
        break;
      // Escape
      case 27:
      // Tab
      case 9:
        checkValidity();
        closeAllLists();
        break;
      }
    });

    function addActive(x) {
      if (!x)
        return false;

      removeActive(x);
      if (currentFocus >= x.length)
        currentFocus = 0;
      else if (currentFocus < 0)
        currentFocus = (x.length - 1);
      x[currentFocus].classList.add("autocomplete-active");
    }

    function removeActive(x) {
      for (const child of x) {
        child.classList.remove("autocomplete-active");
      }
    }

    function closeAllLists() {
      const x = el.querySelector(".autocomplete-items");
      x && el.removeChild(x);
    }
  };

  window.istu.archive.selectSuggestion = function (item) {
    const value = item.getAttribute("data-autocomplete-value");
    const label = item.getAttribute("data-autocomplete-label");
    const event = new CustomEvent('autocomplete:select', {
      bubbles: true, // чтобы событие всплыло наверх
      detail: { value, label },
    });
    item.dispatchEvent(event);
  };

  window.istu.archive.initAutocompleteRecursive = function (el) {
    el.querySelectorAll("[data-autocomplete]")
      .forEach(window.istu.archive.initAutocomplete);
  };
})();
