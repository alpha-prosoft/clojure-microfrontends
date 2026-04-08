const path = require("path");
const { ModuleFederationPlugin } = require("@module-federation/enhanced/rspack");

/** @type {import('@rspack/core').Configuration} */
module.exports = {
  mode: "development",
  entry: "./js-out/main.js",
  output: {
    path: path.resolve(__dirname, "dist"),
    publicPath: "http://localhost:3001/",
    filename: "bundle.js",
    clean: true,
  },
  devServer: {
    port: 3001,
    static: path.join(__dirname, "public"),
    headers: {
      "Access-Control-Allow-Origin": "*",
    },
    hot: false,
    liveReload: false,
  },
  experiments: {
    // Allow ES module output
    outputModule: false,
  },
  resolve: {
    extensions: [".js"],
    // Make sure only ONE copy of React/ReactDOM is loaded
    alias: {
      react: path.resolve(__dirname, "node_modules/react"),
      "react-dom": path.resolve(__dirname, "node_modules/react-dom"),
    },
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        // ClojureScript output — no transpilation needed
        type: "javascript/auto",
      },
    ],
  },
  plugins: [
    new ModuleFederationPlugin({
      name: "mainApp",
      filename: "remoteEntry.js",
      exposes: {
        // Expose the compiled ClojureScript JS bundle as a shared module
        "./cljs-main": "./js-out/main.js",
        // Expose shared React so remotes can consume the same instance
        "./react": "./node_modules/react/index.js",
        "./react-dom": "./node_modules/react-dom/index.js",
        // Expose MUI components
        "./mui-button": "./node_modules/@mui/material/Button/index.js",
        "./mui-typography": "./node_modules/@mui/material/Typography/index.js",
        "./mui-box": "./node_modules/@mui/material/Box/index.js",
        "./mui-paper": "./node_modules/@mui/material/Paper/index.js",
      },
      shared: {
        react: {
          singleton: true,
          requiredVersion: "^18.3.1",
          eager: true,
        },
        "react-dom": {
          singleton: true,
          requiredVersion: "^18.3.1",
          eager: true,
        },
        "@mui/material": {
          singleton: true,
          requiredVersion: "^5.16.7",
          eager: true,
        },
        "@emotion/react": {
          singleton: true,
          requiredVersion: "^11.13.3",
          eager: true,
        },
        "@emotion/styled": {
          singleton: true,
          requiredVersion: "^11.13.0",
          eager: true,
        },
      },
    }),
  ],
};
