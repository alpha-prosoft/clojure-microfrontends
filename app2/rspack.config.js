const path = require("path");
const { ModuleFederationPlugin } = require("@module-federation/enhanced/rspack");

/** @type {import('@rspack/core').Configuration} */
module.exports = {
  mode: "development",
  entry: "./js-out/app2.js",
  output: {
    path: path.resolve(__dirname, "dist"),
    publicPath: "http://localhost:3003/",
    filename: "bundle.js",
    clean: true,
  },
  devServer: {
    port: 3003,
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
  // Exclude the entire cljs-runtime directory from app2's bundle.
  // These files (goog bootstrap, cljs.core, SHADOW_ENV, etc.) are
  // side-effect globals that must only execute once — from main's bundle.
  // Declaring them as externals prevents Rspack from re-bundling them here,
  // which would cause "Namespace already declared" errors at runtime.
  externals: [
    function ({ context, request }, callback) {
      const nodePath = require("path");
      const resolved = request.startsWith(".")
        ? nodePath.join(context, request)
        : null;

      const inCljsRuntime = resolved && resolved.includes("/cljs-runtime/");
      if (!inCljsRuntime) return callback();

      const file = nodePath.basename(resolved);
      const isAppSpecific =
        file.startsWith("app2.") ||
        file.startsWith("shadow.module.app2.");

      if (!isAppSpecific) {
        return callback(null, "globalThis", "global");
      }

      callback();
    },
  ],
  plugins: [
    new ModuleFederationPlugin({
      name: "app2",
      filename: "remoteEntry.js",
      remotes: {
        main: "mainApp@http://localhost:3001/remoteEntry.js",
      },
      exposes: {
        "./app2": "./js-out/app2.js",
      },
      shared: {
        react: {
          singleton: true,
          requiredVersion: "^18.3.1",
          import: false,
        },
        "react-dom": {
          singleton: true,
          requiredVersion: "^18.3.1",
          import: false,
        },
        "@mui/material": {
          singleton: true,
          requiredVersion: "^5.16.7",
          import: false,
        },
        "@emotion/react": {
          singleton: true,
          requiredVersion: "^11.13.3",
          import: false,
        },
        "@emotion/styled": {
          singleton: true,
          requiredVersion: "^11.13.0",
          import: false,
        },
      },
    }),
  ],
};
