(() => {
  const mkIndicator = {
    error({ msg }) {
      const el = document.createElement("div");
      el.className = "alert alert-danger";
      el.setAttribute("role", "alert");
      el.innerText = msg;
      return el;
    },
    progress({ msg }) {
      const el = document.createElement("div");
      el.className = "progress";
      const progress = document.createElement("div");
      progress.className = "progress-bar progress-bar-striped progress-bar-animated container-fluid";
      progress.innerText = msg;
      el.appendChild(progress);
      return el;
    },
  };

  window.istu.archive.initIndicator = function (mount) {
    let indicator;

    function clear() {
      if (indicator)
        indicator.remove();
    }

    function set(el) {
      clear();
      indicator = el;
      mount.appendChild(indicator);
    }

    return {
      clear,
      show(data) {
        const builder = mkIndicator[data.type];
        if (!builder)
          return;

        const el = builder(data);
        set(el);
      },
    };
  }
})();
