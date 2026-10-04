import { src, dest, watch } from 'gulp';
import filter from 'gulp-filter';
import * as sass from 'sass';
import gulpSass from 'gulp-sass';
import svgSprite from 'gulp-svg-sprite';
import postcss from 'gulp-postcss';
import postcssPresetEnv from 'postcss-preset-env';
import cleanCss from 'gulp-clean-css';

import { swcDir } from '@swc/cli';

const sassFactory = gulpSass(sass);

const env = process.env.NODE_ENV || "development";
const isProduction = env === "production";

export const styles = () => src('styles/index.scss')
  .pipe(sassFactory())
  .pipe(postcss([
    postcssPresetEnv({
      env,
      features: {
        'system-ui-font-family': false,
      },
    }),
  ]))
  .pipe(cleanCss({
    level: isProduction ? 1 : 0,
  }))
  .pipe(dest('static/'));

export const watchStyles = () => {
  watch('styles/**/*.scss', {
    ignoreInitial: false,
  }, styles);
}

export const icons = () => src('node_modules/remixicon/**/*.svg')
  .pipe(filter(f => [
    'add-line',
    'arrow-down-line',
    'arrow-up-line',
    'delete-bin-line',
    'search-line',
  ].includes(f.stem)))
  .pipe(svgSprite({
    shape: {
      id: {
        separator: '',
      },
    },
    mode: {
      symbol: {
        sprite: 'icons.svg',
        dest: '.',
      },
    },
  }))
  .pipe(dest('static/assets/'));

const swcOptions = {
  jsc: {
    // По спецификации целевые браузеры — 2017 года и новее
    target: isProduction ? "es2016" : "esnext",
    minify: {
      compress: isProduction,
      mangle: isProduction,
    },
  },
  minify: isProduction,
};

const runSwc = (cb, options = {}) => swcDir({
  cliOptions: {
    outDir: "static/js",
    watch: options.watch,
    filenames: ["scripts"],
    extensions: [".js"],
    stripLeadingPaths: true,
  },
  swcOptions,
  callback: {
    onSuccess: (e) => {
      cb();
    },
    onFail: (e) => {
      console.log(e);
      cb();
    },
  },
});

export const scripts = (cb) => runSwc(cb, {});
export const watchScripts = (cb) => runSwc(cb, { watch: true });
