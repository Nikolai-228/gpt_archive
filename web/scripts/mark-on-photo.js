(() => {
  const { initAutocompleteRecursive } = window.istu.archive;

  const relativePosition = (el, { offsetX, offsetY }) => ({
    x: offsetX / el.offsetWidth,
    y: offsetY / el.offsetHeight,
  });

  const pc = (x) => (x*100).toFixed(4);

  const mkPolygon = (x1, y1, x2, y2) => `
0, 0
100, 0
100, 100
0, 100
0, 0
${pc(x1)}, ${pc(y1)}
${pc(x2)}, ${pc(y1)}
${pc(x2)}, ${pc(y2)}
${pc(x1)}, ${pc(y2)}
${pc(x1)}, ${pc(y1)}
`;

  function initArea(area) {
    const polygon = area.querySelector("polygon");

    return {
      resize(width, height) {
        area.setAttribute("width", width);
        area.setAttribute("height", height);
      },
      redraw(first, second) {
        area.style.display = "block";
        polygon.setAttribute(
          "points",
          mkPolygon(first.x, first.y, second.x, second.y)
        );
      },
    }
  }

  function initSelection(el, { update }) {
    const image = el.querySelector("img");
    const area = initArea(el.querySelector("[data-selection-area]"));
    let selecting = false;
    let first, second;

    function onResize() {
      // NOTE: Consider throttling.
      area.resize(image.offsetWidth, image.offsetHeight);
    }
    onResize();
    image.addEventListener("load", onResize);
    window.addEventListener("resize", onResize);

    function updateSelection() {
      area.redraw(first, second);
      const xs = [first.x, second.x].sort();
      const ys = [first.y, second.y].sort();
      update({
        x: xs[0],
        y: ys[0],
        width: xs[1] - xs[0],
        height: ys[1] - ys[0]
      });
    }

    function onPointerDown(evt) {
      evt.preventDefault();
      selecting = true;
      first = second = relativePosition(el, evt);
      updateSelection();
    }

    function onPointerMove(evt) {
      if (selecting) {
        second = relativePosition(el, evt);
        updateSelection();
      }
    }

    function onPointerUp(evt) {
      evt.preventDefault();
      selecting = false;
      second = relativePosition(el, evt);
      updateSelection();
    }

    if (el.onpointerdown) {
      el.onpointerdown = onPointerDown;
      el.onpointermove = onPointerMove;
      el.onpointerup   = onPointerUp;
    } else {
      el.onmousedown = onPointerDown;
      el.onmousemove = onPointerMove;
      el.onmouseup   = onPointerUp;
    }
  }

  const form = document.getElementById("form");
  initSelection(form.querySelector("[data-selection]"), {
    update(params) {
      for (const key in params)
        form.elements[key].value = params[key].toFixed(7);
    },
  });
  initAutocompleteRecursive(form);
})();
