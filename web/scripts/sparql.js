(() => {
  const { initIndicator, urlWithParams } = window.istu.archive;

  const indicator = initIndicator(document.getElementById("indicator"));

  const examplesBar = (examples, opts) => {
    const el = document.createElement("div");
    el.className = "mb-2";
    for (const example of examples) {
      const btn = document.createElement("button");
      btn.className = "btn btn-secondary btn-sm me-1";
      btn.setAttribute("type", "button");
      btn.innerText = example.title;
      if (opts?.onselect)
        // Old firefox has some troubles with loop variables in lambdas
        btn.onclick = opts.onselect.bind(null, example);
      el.appendChild(btn);
    }
    return el;
  };

  function initQueryForm(el) {
    const textarea = el.elements["q"];
    const submitBtn = el.querySelector("[type=submit]");

    const updateQuery = () => {
      if (!textarea.value) {
        submitBtn.setAttribute("disabled", "");
      } else {
        submitBtn.removeAttribute("disabled");
      }
    }
    textarea.addEventListener("input", updateQuery);
    updateQuery();

    el.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!textarea.value) return;

      try {
        const url = urlWithParams(location.href, { q: textarea.value });
        indicator.show({
          type: "progress",
          msg: "Запрос обрабатывается…",
        });
        const res = await fetch(url, {
          headers: {
            "HX-Request": "true",
          },
        });

        if (!res.ok) throw new Error(await res.text());

        const html = await res.text();
        const results = document.getElementById("query-results");
        results.outerHTML = html;
        indicator.clear();
      } catch (e) {
        indicator.show({
          type: "error",
          msg: `Во время выполнения запроса произошла ошибка: ${e.message}`,
        });
      }
    });

    fetch("./examples.json")
      .then((res) => res.json())
      .then((data) => {
        el.insertAdjacentElement("afterbegin", examplesBar(data, {
          onselect({ query }) {
            textarea.value = query;
            updateQuery();
            submitBtn.focus();
          },
        }));
      });
  }

  const form = document.getElementById("query-form");
  form && initQueryForm(form);
})();
