(() => {
  window.istu = window.istu || {};
  window.istu.archive = window.istu.archive || {};

  window.istu.archive.urlWithParams = (base, params) => {
    let url = base, sep = "?";
    if (params) {
      // NOTE: URLSearchParams may be unavailable in some browsers as of
      //       the beginning of 2017
      const usp = new URLSearchParams();
      if (params instanceof FormData) {
        for (const [key, value] of params.entries()) {
          usp.set(key, value);
        }
      } else {
        for (const key in params)
          usp.set(key, params[key]);
      }
      url = url + sep + usp.toString();
    }
    return url;
  };
})();
