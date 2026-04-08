const path = require("path");
const { ModuleFederationPlugin } = require("@module-federation/enhanced/rspack");

/** @type {import('@rspack/core').Configuration} */
module.exports = {
  mode: "development",
  entry: "./js-out/app1.js",
  output: {
    path: path.resolve(__dirname, "dist"),
    publicPath: "http://localhost:3002/",
    filename: "bundle.js",
    clean: true,
  },
  devServer: {
    port: 3002,
    static: path.join(__dirname, "public"),
    allowedHosts: "all",
    headers: {
      "Access-Control-Allow-Origin": "*",
    },
    hot: false,
    liveReload: false,
  },
  resolve: {
    extensions: [".js"],
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        type: "javascript/auto",
      },
    ],
  },
  // Exclude the entire cljs-runtime directory from app1's bundle.
  // These files (goog bootstrap, cljs.core, SHADOW_ENV, etc.) are
  // side-effect globals that must only execute once — from main's bundle.
  // Declaring them as externals prevents Rspack from re-bundling them here,
  // which would cause "Namespace already declared" errors at runtime.
  externals: [
    function ({ context, request }, callback) {
      // Resolve the full path of the requested module so we can check whether
      // it lives in the cljs-runtime directory regardless of whether the import
      // is written as "./cljs-runtime/foo.js" (from app1.js) or as
      // "./foo.js" (from within another cljs-runtime file).
      const nodePath = require("path");
      const resolved = request.startsWith(".")
        ? nodePath.join(context, request)
        : null;

      const inCljsRuntime = resolved && resolved.includes("/cljs-runtime/");
      if (!inCljsRuntime) return callback();

      // Allow app1-specific namespaces through so they get bundled and their
      // goog.provide / globalThis side-effects execute normally.
      const file = nodePath.basename(resolved);
      const isAppSpecific =
        file.startsWith("app1.") ||
        file.startsWith("shadow.module.app1.");

      if (!isAppSpecific) {
        // Stub as globalThis — these are pure side-effect globals already
        // executed by main's bundle. "global" type emits a standard (non-ESM)
        // external so MF chunk loading via <script> tags keeps working.
        return callback(null, "globalThis", "global");
      }

      callback();
    },
  ],
  plugins: [
    new ModuleFederationPlugin({
      name: "app1",
      filename: "remoteEntry.js",
      remotes: {
        main: "mainApp@http://localhost:3001/remoteEntry.js",
      },
      exposes: {
        "./app1": "./js-out/app1.js",
      },
      // Shared libs come from main — app1 participates in the singleton
      // pool but defers version resolution to main (which has eager: true).
      shared: {
        react: {
          singleton: true,
          requiredVersion: "^18.3.1",
        },
        "react-dom": {
          singleton: true,
          requiredVersion: "^18.3.1",
        },
        "@mui/material": {
          singleton: true,
          requiredVersion: "^5.16.7",
        },
        "@emotion/react": {
          singleton: true,
          requiredVersion: "^11.13.3",
        },
        "@emotion/styled": {
          singleton: true,
          requiredVersion: "^11.13.0",
        },
      },
    }),
  ],
};
