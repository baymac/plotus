type IconName =
  | "search"
  | "sidebar"
  | "projects"
  | "mobileProjects"
  | "settings"
  | "account"
  | "sort"
  | "more"
  | "close"
  | "passkey"
  | "google"
  | "arrow";
const light: Record<IconName, string> = {
  search: "bf78e",
  sidebar: "f6f4d",
  projects: "e7b29",
  mobileProjects: "cd9e3",
  settings: "8f1a4",
  account: "a5a97",
  sort: "aa5c4",
  more: "bf333",
  close: "f5960",
  passkey: "cf006",
  google: "67d2a",
  arrow: "63871",
};
const dark: Partial<Record<IconName, string>> = {
  mobileProjects: "5535c",
  passkey: "57bc9",
  sidebar: "47908",
  projects: "c24aa",
  settings: "8fc66",
  account: "2dfc9",
  sort: "35434",
  more: "ca6a3",
};

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return (
    <span
      className="icon"
      style={{ width: size, height: size }}
      aria-hidden="true"
    >
      <img
        className={dark[name] ? "light-asset" : ""}
        src={`/assets/figma/${light[name]}.svg`}
        alt=""
      />
      {dark[name] && (
        <img
          className="dark-asset"
          src={`/assets/figma/${dark[name]}.svg`}
          alt=""
        />
      )}
    </span>
  );
}

export function Geometry() {
  return (
    <span className="geometry" aria-hidden="true">
      <span className="geometry-art">
        <img className="light-asset" src="/assets/figma/41530.svg" alt="" />
        <img className="dark-asset" src="/assets/figma/169db.svg" alt="" />
        <span>PLOT</span>
      </span>
    </span>
  );
}
