import type { Project } from "@plotus/contracts";

// Design fixtures, never returned from the API or treated as account data.
export const sampleProjects: Project[] = [
  {
    id: "preview-jaipur",
    name: "Jaipur Corner Plot",
    location: "C-Scheme, Jaipur",
    areaSqFt: 1500,
    updatedAt: "2026-10-27T10:00:00Z",
  },
  {
    id: "preview-bengaluru",
    name: "Koramangala Plot #402",
    location: "4th Block, Bengaluru",
    areaSqFt: 3400,
    updatedAt: "2026-10-26T09:00:00Z",
  },
  {
    id: "preview-alwar",
    name: "Alwar Bypass Farmstead",
    location: "Bhiwadi Corridor",
    areaSqFt: 10890,
    updatedAt: "2026-10-24T09:00:00Z",
  },
];

const previewReferenceTime = new Date("2026-10-27T12:00:00Z").getTime();
export function relativeTime(date: string) {
  const elapsed = Math.max(
    0,
    (previewReferenceTime - new Date(date).getTime()) / 3600000,
  );
  if (elapsed < 1) return "Just now";
  if (elapsed < 24) return `${Math.floor(elapsed)} hr ago`;
  if (elapsed < 48) return "Yesterday";
  return `${Math.floor(elapsed / 24)} days ago`;
}

export function areaLabel(area: number | null) {
  return area === null ? "Not set" : `${area.toLocaleString("en-US")} sq ft`;
}
