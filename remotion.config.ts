import path from "path";
import { Config } from "@remotion/cli/config";

Config.overrideWebpackConfig((config) => {
  return {
    ...config,
    resolve: {
      ...config.resolve,
      modules: [
        "node_modules",
        path.resolve(__dirname, "backend/node_modules"),
      ],
    },
  };
});
