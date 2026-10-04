(() => {
  const {
    initAutocomplete,
    initAutocompleteRecursive,
    urlWithParams,
  } = window.istu.archive;

  const INSTANT_PARTS = 6;

  /** Инициализирует поля ввода момента времени.
    *
    * Разрешается вводить каждую ме́ньшую единицу (месяц, день, час и т.д.) только
    * в том случае, если все более большие единицы указаны.
    */
  function initInstantInput(el) {
    const parts = el.querySelectorAll("[date-input-instant-part]");
    const partsNum = el.querySelector("input[name=parts]");
    let filled = 0; // bit vector

    function updateFields() {
      partsNum.value = 0;
      let canInput = 1;
      for (let i = 0; i < INSTANT_PARTS; i++) {
        parts[i].disabled = !canInput;
        canInput && partsNum.value++;
        canInput *= (filled >> i) & 1;
      }
    }

    parts.forEach((part, k) => {
      part.addEventListener("input", () => {
        const newVector = part.value
          ? filled | (1 << k)
          : filled & ~(1 << k);
        if (newVector != filled) {
          filled = newVector;
          updateFields();
        }
      });
    });

    updateFields();
  }

  function initIntervalInput(el) {
    const instantTemplate = el.querySelector("[data-input-interval-instant]");
    const parts = el.querySelectorAll("[data-input-interval-part]");

    parts.forEach((part) => {
      const predicate = part.getAttribute("data-input-interval-part");
      const btn = part.querySelector("button");
      let block = null;

      const setState = (active) => {
        btn.innerText = active ? "Удалить" : "Добавить";
        btn.classList.toggle("btn-success", !active);
        btn.classList.toggle("btn-danger", active);
      }

      btn.addEventListener("click", () => {
        if (block) {
          block.remove();
          block = null;
        } else {
          block = instantTemplate.content.cloneNode(true).firstChild;
          const predInput = document.createElement("input");
          predInput.type = "hidden";
          predInput.name = "predicate";
          predInput.value = predicate;
          block.insertAdjacentElement("afterbegin", predInput);
          part.insertAdjacentElement("beforeend", block);
          initInstantInput(block.querySelector("[data-input-instant]"));
        }
        setState(!!block);
      });
    });
  }

  async function getEditor(options) {
    const res = await fetch(urlWithParams("/api/editor", options));
    if (!res.ok)
      throw new Error(await res.text());

    const html = await res.text();
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, "text/html");
    const newEl = doc.querySelector("[data-statement]");
    if (!newEl)
      throw new Error("Returned markup has no [data-statement]");

    return newEl;
  }

  function mkDeleteButton(opts) {
    const btn = document.createElement("button");
    btn.className = "btn btn-danger position-absolute top-0 end-0 btn-sm";
    btn.innerText = "Удалить";
    opts && opts.onclick && btn.addEventListener("click", opts.onclick);
    return btn;
  }

  function initEditor(el, opts) {
    let predicate, kind;
    let valueEl = el.querySelector("[data-statement-value]");

    function initValue() {
      if (opts.deletable) {
        const btn = mkDeleteButton({
          onclick() {
            el.remove();
          },
        });
        valueEl.insertAdjacentElement("beforeend", btn);
      }
      initAutocompleteRecursive(valueEl);
      valueEl.querySelectorAll("[data-input-instant]").forEach(initInstantInput);
      valueEl.querySelectorAll("[data-input-interval]").forEach(initIntervalInput);

      const kindSelect = valueEl.querySelector("[data-statement-kind]");
      kindSelect && kindSelect.addEventListener("change", () => {
        kind = kindSelect.value;
        updateEditor();
      });
    }
    initValue();

    async function updateEditor() {
      const newOpts = { ...opts };
      predicate && (newOpts.predicate = predicate);
      kind && (newOpts.kind = kind);
      const editor = await getEditor(newOpts);
      const value2 = editor.querySelector("[data-statement-value]");
      valueEl.replaceWith(value2);
      valueEl = value2;
      initValue();
    }

    const predicateInput = el.querySelector("[data-autocomplete=predicate]");
    predicateInput && initAutocomplete(predicateInput, {
      onchange({ value }) {
        predicate = value;
        updateEditor();
      },
    });
  }

  document.querySelectorAll("[data-statement]").forEach(initEditor);

  window.istu.archive.addProperty = async function (ev) {
    const target = ev.target;
    const opts = target.hasAttribute("data-statement-opts")
      ? JSON.parse(target.getAttribute("data-statement-opts"))
      : {};
    opts.deletable = true;
    const el = await getEditor(opts);
    target.insertAdjacentElement("beforebegin", el);
    initEditor(el, opts);
  };
})();
