"use strict";

(() => {
  const media = window.matchMedia("(prefers-color-scheme: dark)");
  let searchForm;

  const updateTheme = () => {
    const theme = media.matches ? "dark" : "light";
    document.documentElement.setAttribute("data-bs-theme", theme);
    searchForm?.setAttribute("data-bs-theme", theme);
  }
  updateTheme();

  media.addEventListener("change", updateTheme);
  document.addEventListener("DOMContentLoaded", () => {
    searchForm = document.getElementById("search-form");
    updateTheme();
  });
})();
