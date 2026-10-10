#!/usr/bin/env node
// Mirrors this repo's GitHub issues into .scratch/*.md so scratchboard has
// local tickets to read. Re-run before `npx scratchboard --serve` to refresh.
import { execFileSync } from "node:child_process";
import { mkdirSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const SCRATCH_DIR = join(import.meta.dirname, "..", ".scratch");

const TRIAGE_LABELS = new Set([
  "needs-triage",
  "needs-info",
  "ready-for-agent",
  "ready-for-human",
  "wontfix",
]);

function fetchIssues() {
  const raw = execFileSync(
    "gh",
    [
      "issue",
      "list",
      "--state",
      "all",
      "--json",
      "number,title,body,labels,state,url",
      "--limit",
      "500",
    ],
    { encoding: "utf8", maxBuffer: 1024 * 1024 * 32 },
  );
  return JSON.parse(raw);
}

function statusFor(issue) {
  if (issue.state === "CLOSED") return "done";
  const labelNames = issue.labels.map((l) => l.name);
  const triage = labelNames.find((l) => TRIAGE_LABELS.has(l));
  if (triage) return triage;
  if (labelNames.includes("wayfinder:map")) return "map";
  return "needs-triage";
}

function frontMatterEscape(value) {
  return String(value).replace(/\n/g, " ").trim();
}

function toMarkdown(issue) {
  const labels = issue.labels.map((l) => l.name).join(", ");
  const lines = [
    "---",
    `id: ${issue.number}`,
    `title: ${frontMatterEscape(issue.title)}`,
    `status: ${statusFor(issue)}`,
    `labels: ${labels}`,
    `url: ${issue.url}`,
    "---",
    "",
    issue.body ?? "",
    "",
  ];
  return lines.join("\n");
}

function main() {
  const issues = fetchIssues();

  mkdirSync(SCRATCH_DIR, { recursive: true });
  for (const name of readdirSync(SCRATCH_DIR)) {
    if (name.endsWith(".md")) rmSync(join(SCRATCH_DIR, name));
  }

  for (const issue of issues) {
    const path = join(SCRATCH_DIR, `${issue.number}.md`);
    writeFileSync(path, toMarkdown(issue), "utf8");
  }

  console.log(`Synced ${issues.length} issues into ${SCRATCH_DIR}`);
}

main();
