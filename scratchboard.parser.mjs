// Reads the .scratch/*.md files written by scripts/sync-scratch-tickets.mjs:
// a small front-matter block (id/title/status/labels/url) followed by the
// GitHub issue body.
function parseFrontMatter(text) {
  const match = /^---\n([\s\S]*?)\n---\n?([\s\S]*)$/.exec(text);
  if (!match) return { meta: {}, body: text };
  const [, block, body] = match;
  const meta = {};
  for (const line of block.split("\n")) {
    const idx = line.indexOf(":");
    if (idx === -1) continue;
    meta[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  }
  return { meta, body };
}

export function parse(path, text) {
  const { meta, body } = parseFrontMatter(text);
  const labels = meta.labels
    ? meta.labels.split(",").map((s) => s.trim()).filter(Boolean)
    : [];

  return {
    id: meta.id ?? null,
    title: meta.title || path,
    body,
    fields: {
      status: meta.status || "",
      labels,
      url: meta.url || "",
    },
  };
}
