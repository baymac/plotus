import { useEffect, useState } from "react";
import type { Project } from "@plotus/contracts";
import { Geometry, Icon } from "./Icon";
import { Modal } from "./Modal";
import { ProjectForm } from "./ProjectForm";
import { areaLabel, relativeTime, sampleProjects } from "./preview";

type Route = "signin" | "projects" | "general" | "account";
type Theme = "system" | "light" | "dark";
function readRoute(): Route {
  const route = window.location.hash;
  if (route === "#/preview/projects") return "projects";
  if (route === "#/preview/general") return "general";
  if (route === "#/preview/account") return "account";
  return "signin";
}
function readPreference(key: string, fallback: string) {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}
function savePreference(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* Preferences are optional in private browsing. */
  }
}

export function App() {
  const [route, setRoute] = useState<Route>(readRoute);
  const [theme, setTheme] = useState<Theme>(() => {
    const value = readPreference("plotus.theme", "system");
    return value === "dark" || value === "light" ? value : "system";
  });
  const [collapsed, setCollapsed] = useState(
    () => readPreference("plotus.sidebar", "open") === "collapsed",
  );
  const [projects, setProjects] = useState<Project[]>(() =>
    structuredClone(sampleProjects),
  );
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("recent");
  const [form, setForm] = useState<Project | "new" | null>(null);
  const [deleting, setDeleting] = useState<Project | null>(null);
  const [message, setMessage] = useState("");
  useEffect(() => {
    const changed = () => {
      setRoute(readRoute());
      setMessage("");
    };
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  useEffect(() => {
    const media = matchMedia("(prefers-color-scheme: dark)");
    const apply = () => {
      document.documentElement.dataset.theme =
        theme === "system" ? (media.matches ? "dark" : "light") : theme;
    };
    apply();
    media.addEventListener("change", apply);
    savePreference("plotus.theme", theme);
    return () => media.removeEventListener("change", apply);
  }, [theme]);
  useEffect(() => {
    savePreference("plotus.sidebar", collapsed ? "collapsed" : "open");
  }, [collapsed]);
  useEffect(() => {
    document.title = `${route === "signin" ? "Sign in" : route === "projects" ? "Projects" : route === "general" ? "General" : "Account"} · Plotus`;
  }, [route]);
  useEffect(() => {
    const closeOutside = (event: MouseEvent) =>
      document
        .querySelectorAll<HTMLDetailsElement>("details[open]")
        .forEach((details) => {
          if (event.target instanceof Node && !details.contains(event.target))
            details.open = false;
        });
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape")
        document
          .querySelectorAll<HTMLDetailsElement>("details[open]")
          .forEach((details) => {
            details.open = false;
          });
    };
    document.addEventListener("click", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("click", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(""), 7000);
    return () => clearTimeout(timer);
  }, [message]);
  const settings = route === "general" || route === "account";
  const title =
    route === "projects"
      ? "Projects"
      : route === "general"
        ? "General"
        : "Account";
  const visible = projects
    .filter((project) =>
      `${project.name} ${project.location}`
        .toLowerCase()
        .includes(query.toLowerCase()),
    )
    .sort((a, b) =>
      sort === "name"
        ? a.name.localeCompare(b.name)
        : Date.parse(b.updatedAt) - Date.parse(a.updatedAt),
    );

  return (
    <>
      <a
        className="skip-link"
        href="#main"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main")?.focus();
        }}
      >
        Skip to content
      </a>
      {route === "signin" ? (
        <main className="signin-page" id="main" tabIndex={-1}>
          <div className="signin-card">
            <p className="wordmark">Plotus</p>
            <div className="drafting-motif">
              <img
                className="light-asset"
                src="/assets/figma/864a9.svg"
                alt="A simple measured floor-plan outline"
              />
              <img
                className="dark-asset"
                src="/assets/figma/5a6b6.svg"
                alt="A simple measured floor-plan outline"
              />
            </div>
            <div className="signin-copy">
              <h1 className="mobile-signin-title">Sign in to Plotus</h1>
              <h2>Create and compare floor plans</h2>
              <p>
                Draw your plot, add your priorities and compare floor-plan
                concepts.
              </p>
            </div>
            <div className="signin-actions">
              <button
                className="button primary"
                onClick={() =>
                  setMessage(
                    "Google sign-in is not available in this scaffold yet. You can explore the design preview below.",
                  )
                }
              >
                <Icon name="google" />
                Continue with Google
              </button>
              <button
                className="button outline"
                onClick={() =>
                  setMessage(
                    "Passkeys are not connected yet. You can explore the design preview below.",
                  )
                }
              >
                <Icon name="passkey" />
                Use a passkey
              </button>
            </div>
            <p className="mobile-planning-note">
              Concepts for early planning. Have a qualified architect review
              before building.
            </p>
          </div>
          <a className="preview-link" href="#/preview/projects">
            Explore design preview
          </a>
        </main>
      ) : (
        <div className={`app-shell ${collapsed ? "collapsed" : ""}`}>
          <aside className="sidebar" aria-label="Main navigation">
            <div className="sidebar-heading">
              <a
                className="wordmark"
                href="#/preview/projects"
                aria-label="Plotus projects"
              >
                Plotus
              </a>
              <button
                className="icon-button plain"
                onClick={() => setCollapsed((value) => !value)}
                aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
                aria-expanded={!collapsed}
              >
                <Icon name="sidebar" />
              </button>
            </div>
            <nav>
              {settings ? (
                <>
                  <a
                    className="nav-item"
                    href="#/preview/projects"
                    title="Back to app"
                  >
                    <span className="back-arrow">
                      <Icon name="arrow" />
                    </span>
                    <span>Back to app</span>
                  </a>
                  <a
                    className={`nav-item ${route === "general" ? "active" : ""}`}
                    href="#/preview/general"
                    aria-current={route === "general" ? "page" : undefined}
                    title="General"
                  >
                    <Icon name="settings" />
                    <span>General</span>
                  </a>
                  <a
                    className={`nav-item ${route === "account" ? "active" : ""}`}
                    href="#/preview/account"
                    aria-current={route === "account" ? "page" : undefined}
                    title="Account"
                  >
                    <Icon name="account" />
                    <span>Account</span>
                  </a>
                </>
              ) : (
                <a
                  className="nav-item active"
                  href="#/preview/projects"
                  aria-current="page"
                  title="Projects"
                >
                  <Icon name="projects" />
                  <span>Projects</span>
                </a>
              )}
            </nav>
            {!settings && (
              <footer>
                <a
                  className="nav-item"
                  href="#/preview/general"
                  title="Settings"
                >
                  <Icon name="settings" />
                  <span>Settings</span>
                </a>
              </footer>
            )}
          </aside>
          <div className="app-body">
            <header className="topbar">
              <span className="desktop-title">{title}</span>
              <a className="wordmark mobile-wordmark" href="#/preview/projects">
                Plotus
              </a>
              <details className="account-menu">
                <summary
                  className="icon-button plain"
                  aria-label="Open account menu"
                >
                  <Icon name="account" />
                </summary>
                <div className="popover">
                  <p className="preview-label">
                    Design preview · local sample data
                  </p>
                  <label className="field">
                    Appearance
                    <select
                      value={theme}
                      onChange={(event) =>
                        setTheme(event.target.value as Theme)
                      }
                    >
                      <option value="system">System</option>
                      <option value="light">Light</option>
                      <option value="dark">Dark</option>
                    </select>
                  </label>
                  <a className="menu-action" href="#/signin">
                    Back to sign in
                  </a>
                </div>
              </details>
            </header>
            <main className="workspace" id="main" tabIndex={-1}>
              <h1 className="sr-only">{title}</h1>
              {route === "projects" ? (
                <section
                  className="workspace-panel projects-panel"
                  aria-label="Projects"
                >
                  <div className="mobile-project-heading">
                    <h2>Projects</h2>
                    <button
                      className="button primary"
                      onClick={() => setForm("new")}
                    >
                      New project
                    </button>
                  </div>
                  <div className="table-toolbar">
                    <label className="search">
                      <Icon name="search" size={18} />
                      <input
                        type="search"
                        aria-label="Search projects"
                        placeholder="Search projects"
                        value={query}
                        onChange={(event) => setQuery(event.target.value)}
                      />
                    </label>
                    <label className="sort-control icon-button">
                      <Icon name="sort" />
                      <select
                        aria-label="Sort projects"
                        value={sort}
                        onChange={(event) => setSort(event.target.value)}
                      >
                        <option value="recent">Last edited</option>
                        <option value="name">Name</option>
                      </select>
                    </label>
                    <button
                      className="button primary desktop-new"
                      onClick={() => setForm("new")}
                    >
                      New project
                    </button>
                  </div>
                  <div className="project-table">
                    <div className="table-head" aria-hidden="true">
                      <span>Geometry</span>
                      <span>Name &amp; location</span>
                      <span>Area</span>
                      <span>Last edited</span>
                      <span />
                    </div>
                    {visible.map((project) => (
                      <div className="project-row" key={project.id}>
                        <button
                          className="project-open"
                          aria-label={`Edit ${project.name}`}
                          onClick={() => setForm(project)}
                        >
                          <Geometry />
                          <span className="project-meta">
                            <strong>{project.name}</strong>
                            <span>{project.location}</span>
                            <small>
                              {areaLabel(project.areaSqFt)} ·{" "}
                              {relativeTime(project.updatedAt)}
                            </small>
                          </span>
                          <span className="project-area">
                            {areaLabel(project.areaSqFt)}
                          </span>
                          <span className="project-time">
                            {relativeTime(project.updatedAt)}
                          </span>
                        </button>
                        <details className="project-menu">
                          <summary
                            className="icon-button plain"
                            aria-label={`Actions for ${project.name}`}
                          >
                            <Icon name="more" />
                          </summary>
                          <div className="popover">
                            <button
                              className="menu-action"
                              onClick={(event) => {
                                event.currentTarget
                                  .closest("details")
                                  ?.removeAttribute("open");
                                setForm(project);
                              }}
                            >
                              Edit project
                            </button>
                            <button
                              className="menu-action danger"
                              onClick={(event) => {
                                event.currentTarget
                                  .closest("details")
                                  ?.removeAttribute("open");
                                setDeleting(project);
                              }}
                            >
                              Delete project
                            </button>
                          </div>
                        </details>
                      </div>
                    ))}
                    {visible.length === 0 && (
                      <div className="empty-state">
                        <h2>
                          {query
                            ? "No matching projects"
                            : "Your next plan starts here"}
                        </h2>
                        <p>
                          {query
                            ? "Try a different name or location."
                            : "Create a project to start planning."}
                        </p>
                        {query ? (
                          <button
                            className="button outline"
                            onClick={() => setQuery("")}
                          >
                            Clear search
                          </button>
                        ) : (
                          <button
                            className="button primary"
                            onClick={() => setForm("new")}
                          >
                            New project
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </section>
              ) : (
                <section className="workspace-panel settings-panel">
                  <h2 className="mobile-settings-title">{title}</h2>
                  {route === "general" ? (
                    <>
                      <h3>About</h3>
                      <dl className="about-list">
                        <div>
                          <dt>App</dt>
                          <dd>Plotus</dd>
                        </div>
                        <div>
                          <dt>Version</dt>
                          <dd>0.1.0</dd>
                        </div>
                      </dl>
                    </>
                  ) : (
                    <>
                      <h3>Design preview</h3>
                      <p>
                        This workspace uses sample projects. You are not signed
                        in.
                      </p>
                      <a className="button outline" href="#/signin">
                        Back to sign in
                      </a>
                    </>
                  )}
                </section>
              )}
            </main>
            <nav className="mobile-navigation" aria-label="Main navigation">
              <a
                className={!settings ? "active" : ""}
                href="#/preview/projects"
                aria-current={!settings ? "page" : undefined}
              >
                <span>
                  <Icon name="mobileProjects" />
                </span>
                Projects
              </a>
              <a
                className={settings ? "active" : ""}
                href="#/preview/general"
                aria-current={settings ? "page" : undefined}
              >
                <span>
                  <Icon name="settings" />
                </span>
                Settings
              </a>
            </nav>
          </div>
        </div>
      )}
      {message && (
        <div className="toast" role="status">
          {message}
        </div>
      )}
      {form && (
        <ProjectForm
          project={form === "new" ? undefined : form}
          onClose={() => setForm(null)}
          onSave={(project) => {
            setProjects((current) => [
              project,
              ...current.filter((item) => item.id !== project.id),
            ]);
            setForm(null);
            setMessage("Project saved in the local design preview.");
          }}
        />
      )}
      {deleting && (
        <Modal
          title="Delete project?"
          compact
          onClose={() => setDeleting(null)}
        >
          <p>
            Delete “{deleting.name}” from this preview? This cannot be undone.
          </p>
          <div className="form-actions">
            <button
              className="button outline"
              onClick={() => setDeleting(null)}
            >
              Cancel
            </button>
            <button
              className="button destructive"
              onClick={() => {
                setProjects((current) =>
                  current.filter((item) => item.id !== deleting.id),
                );
                setDeleting(null);
                setMessage("Project deleted from the preview.");
              }}
            >
              Delete project
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
