const motion = matchMedia("(prefers-reduced-motion: reduce)");
const revealItems = document.querySelectorAll("[data-reveal]");
if (!motion.matches && "IntersectionObserver" in window) {
  const observer = new IntersectionObserver(
    (entries) =>
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      }),
    { threshold: 0.12 },
  );
  revealItems.forEach((item) => {
    item.classList.add("will-reveal");
    observer.observe(item);
  });
  motion.addEventListener("change", (event) => {
    if (event.matches)
      revealItems.forEach((item) => item.classList.add("is-visible"));
  });
}

const menu = document.querySelector(".menu-toggle");
const nav = document.querySelector("#primary-links");
const closeMenu = () => {
  menu?.setAttribute("aria-expanded", "false");
  nav?.classList.remove("is-open");
};
menu?.addEventListener("click", () => {
  const open = menu.getAttribute("aria-expanded") !== "true";
  menu.setAttribute("aria-expanded", String(open));
  nav.classList.toggle("is-open", open);
});
nav
  ?.querySelectorAll("a")
  .forEach((link) => link.addEventListener("click", closeMenu));
document.addEventListener("keydown", (event) => {
  if (
    event.key === "Escape" &&
    menu?.getAttribute("aria-expanded") === "true"
  ) {
    closeMenu();
    menu.focus();
  }
});
matchMedia("(min-width: 761px)").addEventListener("change", (event) => {
  if (event.matches) closeMenu();
});

const visual = document.querySelector(".hero-visual");
const tabs = [...document.querySelectorAll("[data-view-tab]")];
function selectView(tab, focus = false) {
  tabs.forEach((item) => {
    const selected = item === tab;
    item.setAttribute("aria-selected", String(selected));
    item.tabIndex = selected ? 0 : -1;
    document.getElementById(item.getAttribute("aria-controls")).hidden =
      !selected;
  });
  visual.classList.toggle("is-plan", tab.dataset.viewTab === "plan");
  if (focus) tab.focus();
}
tabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectView(tab));
  tab.addEventListener("keydown", (event) => {
    let next;
    if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
    if (event.key === "ArrowLeft")
      next = (index - 1 + tabs.length) % tabs.length;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = tabs.length - 1;
    if (next !== undefined) {
      event.preventDefault();
      selectView(tabs[next], true);
    }
  });
});

const header = document.querySelector(".site-header");
let scheduled = false;
function updateScroll() {
  header?.classList.toggle("has-scrolled", scrollY > 24);
  const image = document.querySelector(".house-image");
  if (image && !motion.matches && innerWidth > 760) {
    const rect = visual.getBoundingClientRect();
    const progress = Math.max(
      -1,
      Math.min(
        1,
        (innerHeight * 0.5 - (rect.top + rect.height * 0.5)) / innerHeight,
      ),
    );
    image.style.setProperty("--drift", `${progress * 18}px`);
  }
  scheduled = false;
}
addEventListener(
  "scroll",
  () => {
    if (!scheduled) {
      scheduled = true;
      requestAnimationFrame(updateScroll);
    }
  },
  { passive: true },
);
updateScroll();

document.querySelectorAll(".faq-list details").forEach((item) =>
  item.addEventListener("toggle", () => {
    if (item.open)
      document.querySelectorAll(".faq-list details").forEach((other) => {
        if (other !== item) other.open = false;
      });
  }),
);
