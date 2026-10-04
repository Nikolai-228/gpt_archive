(() => {
  const repl = document.getElementById("repl");
  const replRecordTpl = document.getElementById("repl-record");
  const replInputTpl = document.getElementById("repl-input");

  const clearBtn = document.getElementById("clear");

  const MARKERS = {
    input: ">",
    output: "<",
  };

  function mkInput() {
    const el = replInputTpl.content.cloneNode(true).firstChild;
    return el;
  }

  function mkRecord(data) {
    const el = replRecordTpl.content.cloneNode(true).firstChild;
    
    const marker = el.querySelector("[data-repl-marker]");
    marker.innerText = MARKERS[data.direction];

    const content = el.querySelector("[data-repl-content]");
    content.innerText = data.content;

    if (data.direction === "input")
      el.classList.add("bg-body-secondary")
    else if (data.error)
      el.classList.add("bg-danger");
    
    return el;
  }

  let currentInput;
  function initInput() {
    currentInput = mkInput();
    repl.appendChild(currentInput);
  }
  initInput();

  clearBtn.addEventListener("click", () => {
    repl.replaceChildren();
    initInput();
  });

  repl.addEventListener("submit", async (event) => {
    event.preventDefault();

    const input = new FormData(repl).get("input");
    const recordIn = mkRecord({
      direction: "input",
      content: input,
    });
    currentInput.replaceWith(recordIn);
    currentInput = null;
    clearBtn.disabled = true;

    try {
      const res = await fetch(location.href, {
        method: "POST",
        headers: {
          "Content-Type": "text/plain",
        },
        body: input,
      });

      const content = await res.text();
      if (!res.ok)
        throw new Error(content);

      const recordOut = mkRecord({
        direction: "output",
        content,
      });
      repl.appendChild(recordOut);
    } catch (e) {
      const recordError = mkRecord({
        direction: "output",
        content: e.message,
        error: true,
      });
      repl.appendChild(recordError);
    } finally {
      initInput();
      clearBtn.disabled = false;
    }
  });
})();
