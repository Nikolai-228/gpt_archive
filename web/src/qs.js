"use strict";

/**
 * Разбирает строку запроса на массив пар «ключ—значение».
 *
 * @param qs {string} - строка запроса
 * @returns генератор пар «ключ—значение» (объект со строковыми ключами key, value)
 */
export function* parseEntries(qs) {
  let i, j;
  i = j = 0;

  if (qs.charCodeAt(i) === 0x3f)
    i = j = 1;

  const getPart = () => decodeURIComponent(qs.slice(i, j).replace(/\+/g, ' '));

  let current = { key: null, value: null };
  while (true) {
    if ((j == qs.length || qs.charCodeAt(j) === 0x26)) { // &
      const part = getPart();
      if (current.key)
        current.value = part;
      else
        current.key = part;
      yield current;
      current = { key: null, value: null };
      i = ++j;
      continue;
    } else if (qs.charCodeAt(j) === 0x3d && j > i) { // =
      current.key = getPart();
      i = ++j;
      continue;
    }
    if (j >= qs.length)
      break;
    j++;
  }
}

export function parse(qs) {
  const res = {}
  for (const { key, value } of parseEntries(qs)) {
    res[key] = value;
  }
  return res;
}
