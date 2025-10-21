import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import replace from '@rollup/plugin-replace';

export default {
  input: 'src/main/resources/static/node_modules/hwp.js/build/esm.js',
  output: {
    file: 'dist/hwp.browser.js',
    format: 'es',
  },

  plugins: [
    replace({
      preventAssignment: true,
      values: {
        'require("fs")': 'undefined',
        'require(\'fs\')': 'undefined',
      },
    }),
    resolve({
      browser: true,
      preferBuiltins: false,
    }),
    commonjs(),
  ],

};