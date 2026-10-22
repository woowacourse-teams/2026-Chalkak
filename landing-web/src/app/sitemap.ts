import type { MetadataRoute } from "next";

import { siteUrl } from "@/shared/store-links";

export default function sitemap(): MetadataRoute.Sitemap {
  return [{ url: siteUrl(), changeFrequency: "monthly", priority: 1 }];
}
