import type { MetadataRoute } from "next";

import { siteUrl } from "@/shared/store-links";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/", disallow: "/download" },
    sitemap: `${siteUrl()}/sitemap.xml`,
  };
}
