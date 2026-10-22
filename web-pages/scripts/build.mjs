import { fileURLToPath } from "node:url";
import { buildSite } from "./site.mjs";
const projectRoot = fileURLToPath(new URL("../", import.meta.url));
const result = await buildSite({ projectRoot });
console.log(
  `Built ${result.pages.length} routes and validated ${result.files} files.`,
);
console.log(
  `Configured surveys: ${result.surveysReady}. Routes: ${result.pages.join(", ")}`,
);
