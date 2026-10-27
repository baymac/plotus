import { cp, mkdir, rm, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { legalPages, contactEmail, legalLinks } from "../content/legal.mjs";

const root = resolve(import.meta.dirname, "..");
const out = resolve(root, "dist");
const origin = "https://plotus.app";
const escape = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
const arrow =
  '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const diagonal =
  '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" aria-hidden="true"><path d="M6 18 18 6M6 6h12v12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const brand = (light = false) =>
  `<a class="brand" href="/" aria-label="Plotus home"><img src="/assets/plotus-mark${light ? "-paper" : ""}.svg" alt="" width="28" height="28"><span>Plotus</span></a>`;
const mail = (subject = "Hello Plotus") =>
  `mailto:${contactEmail}?subject=${encodeURIComponent(subject)}`;

function header() {
  return `<header class="site-header"><nav class="nav-shell" aria-label="Main navigation">${brand()}<button class="menu-toggle" type="button" aria-label="Toggle navigation" aria-controls="primary-links" aria-expanded="false"><span></span><span></span></button><div class="nav-links" id="primary-links"><a href="/#idea">The idea</a><a href="/#for-you">Who it’s for</a><a href="/#questions">Questions</a><a class="button button-small button-dark" href="/projects">Start project ${diagonal}</a></div></nav></header>`;
}
function footer() {
  return `<footer class="site-footer"><div class="container"><div class="footer-top"><div>${brand()}<p>Considered beginnings.<br>Extraordinary possibilities.</p></div><div class="footer-links"><div><span class="eyebrow">EXPLORE</span><a href="/#idea">The idea</a><a href="/#for-you">Who it’s for</a><a href="/#questions">Questions</a><a href="https://github.com/baymac/plotus" target="_blank" rel="noopener noreferrer">Demo source ${diagonal}</a></div><div><span class="eyebrow">HERE TO HELP</span><a href="/support/">Support</a><a href="/privacy/">Privacy policy</a><a href="/terms/">Terms of use</a><a href="/data-deletion/">Data deletion</a><a href="/account-deletion/">Delete your account</a></div><div><span class="eyebrow">LET’S TALK</span><a href="${mail()}">${escape(contactEmail)} ${diagonal}</a><span class="footer-note">Ideas, questions, or a home<br>you can’t stop thinking about.</span></div></div></div><div class="footer-wordmark" aria-hidden="true"><img src="/assets/plotus-mark.svg" alt="" width="80" height="80">Plotus</div><div class="footer-bottom"><span>© ${new Date().getUTCFullYear()} Plotus</span><span>Made for the way you want to live.</span><a href="/accessibility/">Accessibility</a></div></div></footer>`;
}
function document({
  title,
  description,
  path = "/",
  body,
  legal = false,
  noindex = false,
}) {
  return `<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(title)}</title><meta name="description" content="${escape(description)}"><meta name="theme-color" content="#fbf9f2">${noindex ? '<meta name="robots" content="noindex">' : ""}<link rel="canonical" href="${origin}${path}"><meta property="og:type" content="website"><meta property="og:site_name" content="Plotus"><meta property="og:title" content="${escape(title)}"><meta property="og:description" content="${escape(description)}"><meta property="og:url" content="${origin}${path}"><meta property="og:image" content="${origin}/assets/social-card.jpg"><meta property="og:image:alt" content="An architectural concept of a warm courtyard home"><meta name="twitter:card" content="summary_large_image"><link rel="icon" href="/assets/plotus-mark.svg" type="image/svg+xml"><link rel="preload" href="/assets/jakarta_regular.ttf" as="font" type="font/ttf" crossorigin><link rel="preload" href="/assets/jakarta_semibold.ttf" as="font" type="font/ttf" crossorigin><link rel="stylesheet" href="/site.css"><script src="/site.js" defer></script></head><body${legal ? ' class="legal-page"' : ""}><a class="skip-link" href="#main">Skip to content</a>${header()}<main id="main" tabindex="-1">${body}</main>${footer()}</body></html>`;
}

function planGraphic(prefix = "plan") {
  return `<svg class="plan-drawing" viewBox="0 0 800 570" role="img" aria-labelledby="${prefix}-title ${prefix}-desc"><title id="${prefix}-title">An imagined courtyard home</title><desc id="${prefix}-desc">A conceptual floor plan with a central planted courtyard, shared living spaces, and quieter private rooms. An illustration, not a generated design or construction drawing.</desc><defs><pattern id="${prefix}-grid" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0v24" fill="none" stroke="currentColor" stroke-opacity=".08" stroke-width=".6"/></pattern></defs><rect x="0" y="0" width="800" height="570" fill="url(#${prefix}-grid)"/><g class="plan-foliage" fill="currentColor" fill-opacity=".07" stroke="currentColor" stroke-opacity=".25"><circle cx="629" cy="100" r="35"/><circle cx="133" cy="455" r="28"/><circle cx="670" cy="438" r="40"/><circle cx="393" cy="279" r="45"/><circle cx="405" cy="263" r="31"/><path d="m393 238 6 80m-31-56 51 31m-55 10 54-48"/></g><g class="plan-site" fill="none" stroke="currentColor" stroke-opacity=".35" stroke-width="1"><path d="M93 63h614v450H93z" stroke-dasharray="5 6"/><path d="M75 84V494m-6-410h12m-12 410h12M118 42h568m-568-6v12m568-12v12"/><path d="M650 42v-20m-8 10 8-10 8 10"/></g><g class="plan-walls" fill="none" stroke="currentColor" stroke-width="7" stroke-linecap="square"><path d="M131 104h516v354H131z"/><path d="M131 223h169v-119m0 0v83m0 63v208m0-104h-72m-50 0h-47m169-167h191v166H300m191-249v83m0 56v215m0-171h156m-156 85h156"/><path d="M300 187v42m0 82v42m147 0h44" stroke-width="3"/></g><g class="plan-window" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M173 100h86v8h-86zm-46 152v68h8v-68zm23 202h85v8h-85zm193 0h90v8h-90zm190-354h77v8h-77zm110 40v90h8v-90zm0 178v107h8V318z"/><path d="M300 243h-42v-42a42 42 0 0 1 42 42zm194-6h40v-40a40 40 0 0 0-40 40zM220 354v40h-40a40 40 0 0 1 40-40z"/></g><g class="plan-furniture" fill="currentColor" fill-opacity=".05" stroke="currentColor" stroke-width="1" stroke-opacity=".6"><rect x="151" y="129" width="107" height="21" rx="3"/><rect x="151" y="129" width="21" height="68" rx="3"/><rect x="190" y="165" width="57" height="28" rx="10"/><rect x="543" y="129" width="73" height="103" rx="3"/><rect x="547" y="134" width="30" height="20" rx="3"/><rect x="583" y="134" width="29" height="20" rx="3"/><path d="M543 160h73"/><rect x="529" y="316" width="91" height="22" rx="2"/><rect x="540" y="394" width="71" height="38" rx="4"/><rect x="157" y="392" width="80" height="35" rx="4"/><circle cx="149" cy="409" r="7"/><circle cx="247" cy="409" r="7"/><circle cx="179" cy="383" r="7"/><circle cx="215" cy="383" r="7"/><circle cx="179" cy="436" r="7"/><circle cx="215" cy="436" r="7"/></g><g fill="currentColor" font-family="Arial,sans-serif" font-size="10" letter-spacing="1.5" text-anchor="middle"><text x="211" y="285">ROOM TO GATHER</text><text x="395" y="335">A QUIETER MOMENT</text><text x="572" y="260">ROOM TO REST</text><text x="400" y="545" font-size="9">CONCEPT STUDY · NOT A CONSTRUCTION DRAWING</text><text x="650" y="16" font-size="11">N</text></g></svg>`;
}

const FAQs = [
  [
    "What is Plotus?",
    "Plotus is a home-planning workspace for homeowners, architects, and residential designers. We’re building a more considered place to bring together the site, the people, and the possibilities at the beginning of a home project.",
  ],
  [
    "What can I try today?",
    "Sign in with Google or Facebook to create projects, organise client details, and pick up your saved work on iPhone or web. You can search, sort and edit your projects, and add a passkey in web Account settings.",
  ],
  [
    "Does Plotus generate floor plans yet?",
    "Not in the current public demo. Plot setup, room requirements, and floor-plan concepts are in development. The home and plan illustrations on this website express our direction; they are not generated app results.",
  ],
  [
    "Is this for homeowners or design professionals?",
    "Both perspectives matter. Homeowners bring the way they want to live. Architects and designers bring the expertise to shape it. Plotus is being built to make those early conversations clearer.",
  ],
  [
    "Can I use a concept to start construction?",
    "A concept is a starting point for discussion. Any real building project needs a qualified local professional to check site conditions, structure, permissions, and applicable regulations. Plotus is not a replacement for that work.",
  ],
  [
    "How can I get involved?",
    "We’d love to hear about your project, your design practice, or what makes home planning difficult today. Email hello@plotus.app to start a conversation.",
  ],
];

const home = `
<section class="hero" aria-labelledby="hero-title">
  <div class="container hero-copy">
    <p class="eyebrow hero-eyebrow"><span class="little-mark" aria-hidden="true">✳</span> A NEW PERSPECTIVE ON HOME PLANNING</p>
    <h1 id="hero-title">Make room for<br><span>something better.</span></h1>
    <div class="hero-bottom"><p>An emerging home-planning workspace for homeowners and designers.<br class="desktop-break"> A place for your plot, your people, and your possibilities.</p><div class="hero-action-wrap"><a class="button button-dark hero-cta" href="/projects">Start project <span>${arrow}</span></a><span class="hero-status"><span></span> An early demo. A bigger idea.</span></div></div>
  </div>
  <figure class="hero-figure wide" data-reveal>
    <div class="hero-visual">
      <div class="visual-tabs" role="tablist" aria-label="Architectural concept views"><button id="tab-model" role="tab" aria-selected="true" aria-controls="visual-model" data-view-tab="model" type="button">The possibility</button><button id="tab-plan" role="tab" aria-selected="false" aria-controls="visual-plan" tabindex="-1" data-view-tab="plan" type="button">The perspective</button></div>
      <div id="visual-model" role="tabpanel" aria-labelledby="tab-model"><img class="house-image" src="/assets/courtyard-concept.webp" alt="A warm architectural model of a modern courtyard home, with sunlit living spaces, natural wood, and a garden at its heart." width="1536" height="1024" fetchpriority="high"><div class="image-note note-one"><span></span>Space to come together.</div><div class="image-note note-two"><span></span>Room to be yourself.</div></div>
      <div id="visual-plan" role="tabpanel" aria-labelledby="tab-plan" hidden>${planGraphic("hero-study")}</div>
      <span class="corner corner-tl" aria-hidden="true"></span><span class="corner corner-tr" aria-hidden="true"></span><span class="corner corner-bl" aria-hidden="true"></span><span class="corner corner-br" aria-hidden="true"></span>
      <div class="visual-index" aria-hidden="true"><span>PLT—001</span><span>A STUDY IN POSSIBILITY</span></div>
    </div>
    <figcaption><span>THE COURTYARD HOUSE <span class="caption-divider">/</span> AN IMAGINED BEGINNING</span><span>Concept illustrations, not app-generated results.</span></figcaption>
  </figure>
</section>

<section id="idea" class="idea-section section-space">
  <div class="container"><div class="section-heading" data-reveal><p class="eyebrow"><span class="section-dot"></span> THE IDEA BEHIND PLOTUS</p><h2>A home is more<br>than a floor plan.</h2><p class="section-intro">It’s Sunday lunches. A quiet corner. Light in the right places.<br class="desktop-break"> It’s the life you want to make room for.</p></div>
  <div class="principles"><article data-reveal><div class="principle-icon icon-life" aria-hidden="true"><span></span><span></span><span></span></div><h3>Start with what matters.</h3><p>Every family has its own rhythm. The best beginning is understanding yours—before the details take over.</p></article><article data-reveal><div class="principle-icon icon-land" aria-hidden="true"><span></span><span></span></div><h3>See the whole picture.</h3><p>A plot has a story of its own. Its shape, surroundings, and sense of place deserve to be part of the conversation.</p></article><article data-reveal><div class="principle-icon icon-together" aria-hidden="true"><span></span><span></span></div><h3>Give ideas common ground.</h3><p>Make those early conversations between homeowner and designer clearer, more focused, and full of possibility.</p></article></div></div>
</section>

<section class="vision-section" aria-labelledby="vision-title">
  <div class="container vision-grid"><div class="vision-copy" data-reveal><p class="eyebrow"><span class="section-dot"></span> A MORE CONSIDERED BEGINNING</p><h2 id="vision-title">Less scattered.<br>More <em>possibility.</em></h2><p>A home project begins with a hundred things on your mind. We’re building Plotus to bring the important ones into focus.</p><p>Your place. Your priorities. A shared vision of what comes next.</p><a class="text-link light-link" href="#for-you">A little space for everyone ${arrow}</a><span class="development-note"><span></span> Taking shape. Built with care.</span></div><div class="vision-art" data-reveal><div class="art-caption"><span>YOUR LIFE, GIVEN SPACE</span><span>01 / CONCEPT</span></div>${planGraphic("vision-study")}<div class="plan-quote">“What if home felt<br>a little more like <em>you?</em>”</div></div></div>
</section>

<section id="for-you" class="audience-section section-space"><div class="container"><div class="section-heading heading-split" data-reveal><div><p class="eyebrow"><span class="section-dot"></span> DIFFERENT PERSPECTIVES. ONE POSSIBILITY.</p><h2>Your dream.<br>Our common ground.</h2></div><p>Whether you’re imagining a home or helping someone create one, a good beginning makes space for both.</p></div><div class="audience-grid"><article class="audience-card homeowner" data-reveal><div class="audience-card-copy"><span class="eyebrow">FOR HOMEOWNERS</span><h3>It starts with<br>“what if?”</h3><p>A home that feels like you begins with the things only you know. Bring your hopes, your habits, and that one idea you keep coming back to.</p><a class="text-link" href="${mail("My home, my possibilities")}">Tell us what you’re imagining ${diagonal}</a></div><div class="home-crop"><img src="/assets/courtyard-concept.webp" alt="A close view of a sunlit shared living room in the courtyard-home concept illustration." width="1536" height="1024" loading="lazy"></div></article><article class="audience-card designer" data-reveal><div class="audience-card-copy"><span class="eyebrow">FOR ARCHITECTS & DESIGNERS</span><h3>Better beginnings.<br>Better conversations.</h3><p>The first brief is rarely the whole story. We’re making room for site context and human priorities to meet your professional perspective.</p><a class="text-link" href="${mail("Plotus for my design practice")}">Share your perspective ${diagonal}</a></div><div class="designer-art" aria-hidden="true"><div class="drawing-sheet sheet-back"></div><div class="drawing-sheet sheet-front">${planGraphic("designer-study")}</div><span class="drawing-annotation">A CLEARER STARTING POINT.</span></div></article></div></div></section>

<section class="outlook-section"><div class="container outlook-content" data-reveal><span class="outlook-symbol" aria-hidden="true">✳</span><p class="eyebrow">THE SPACE BETWEEN AN IDEA AND A HOME</p><h2>That’s where<br>possibility lives.</h2><p>Plotus is still taking shape. Our current demo is a small beginning of a bigger idea: giving every home a more thoughtful start.</p><a class="text-link" href="https://github.com/baymac/plotus" target="_blank" rel="noopener noreferrer">View the demo source ${diagonal}</a></div></section>

<section id="questions" class="questions-section section-space"><div class="container questions-grid"><div class="questions-heading" data-reveal><p class="eyebrow"><span class="section-dot"></span> A FEW THINGS TO KNOW</p><h2>Good questions.<br>Clear answers.</h2><p>Something else on your mind?<br><a href="${mail()}">We’re a hello away. ${diagonal}</a></p></div><div class="faq-list" data-reveal>${FAQs.map(([question, answer], i) => `<details${i === 0 ? " open" : ""}><summary>${question}<span aria-hidden="true"></span></summary><div class="faq-answer"><p>${escape(answer)}${i === 1 ? ' <a href="https://github.com/baymac/plotus" target="_blank" rel="noopener noreferrer">Source and setup instructions ↗</a>' : ""}</p></div></details>`).join("")}</div></div></section>

<section class="closing-section"><div class="container closing-inner" data-reveal><div><p class="eyebrow">YOUR NEXT CHAPTER STARTS SOMEWHERE.</p><h2>Let’s make<br>room for it.</h2></div><div class="closing-action"><p>Have a plot, a practice,<br>or a possibility in mind?</p><a class="button button-paper" href="/projects">Start project ${diagonal}</a><span>${escape(contactEmail)}</span></div></div><span class="closing-orbit" aria-hidden="true"></span></section>`;

function legalBody(page) {
  const related = page.related || legalLinks;
  return `<div class="container legal-layout"><aside class="legal-sidebar"><a class="text-link" href="/">← Back to Plotus</a><nav aria-label="Information pages">${legalLinks.map((link) => `<a href="${escape(link.href)}"${link.href.replace(/\/$/, "") === page.path.replace(/\/$/, "") ? ' aria-current="page"' : ""}>${escape(link.label)}</a>`).join("")}</nav><a class="legal-contact" href="${mail()}">${escape(contactEmail)} ${diagonal}</a></aside><article class="legal-article"><header class="legal-heading"><p class="eyebrow">${escape(page.eyebrow)}</p><h1>${escape(page.title)}</h1><p class="legal-intro">${escape(page.intro)}</p>${page.updated ? `<p class="updated">Last updated: <time datetime="2026-10-28">${escape(page.updated)}</time></p>` : ""}</header>${page.sections.map((section) => `<section><h2>${escape(section.title)}</h2>${(section.paragraphs || []).map((p) => `<p>${escape(p)}</p>`).join("")}${section.bullets ? `<ul>${section.bullets.map((p) => `<li>${escape(p)}</li>`).join("")}</ul>` : ""}${section.steps ? `<ol>${section.steps.map((p) => `<li>${escape(p)}</li>`).join("")}</ol>` : ""}${section.links ? `<div class="article-links">${section.links.map((l) => `<a href="${escape(l.href)}">${escape(l.label)} ${diagonal}</a>`).join("")}</div>` : ""}</section>`).join("")}${page.action ? `<a class="button button-dark legal-action" href="${escape(page.action.href)}">${escape(page.action.label)} ${diagonal}</a>` : ""}<div class="related-pages"><p class="eyebrow">RELATED INFORMATION</p>${related.map((link) => `<a href="${escape(link.href)}">${escape(link.label)} ${arrow}</a>`).join("")}</div></article></div>`;
}

await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });
await cp(resolve(root, "public"), out, { recursive: true });
await writeFile(
  resolve(out, "index.html"),
  document({
    title: "Plotus — Make room for something better.",
    description:
      "A more thoughtful beginning for your home. Meet Plotus, the home-planning workspace being built for homeowners, architects, and residential designers.",
    body: home,
  }),
);
for (const page of legalPages) {
  const path = "/" + page.path.replace(/^\/+|\/+$/g, "") + "/";
  const directory = resolve(out, "." + path);
  await mkdir(directory, { recursive: true });
  await writeFile(
    resolve(directory, "index.html"),
    document({
      title: `${page.eyebrow === "Privacy" ? "Privacy policy" : page.eyebrow} — Plotus`,
      description: page.intro,
      path,
      body: legalBody(page),
      legal: true,
    }),
  );
}
const accessibility = {
  path: "/accessibility/",
  eyebrow: "ACCESSIBILITY",
  title: "A little more room for everyone.",
  intro:
    "We want Plotus to be clear, comfortable, and usable for as many people as possible.",
  sections: [
    {
      title: "Using this website",
      paragraphs: [
        "The site supports keyboard navigation, visible focus indicators, readable text, and your system’s reduced-motion preference. The concept views can be switched using arrow keys, and the questions can be expanded with a keyboard.",
      ],
    },
    {
      title: "Tell us what needs attention",
      paragraphs: [
        "If you encounter a barrier or need information in another format, contact us. Include the page address, your browser or device, and what you were trying to do. Please do not include passwords or sensitive account information.",
      ],
      links: [
        {
          label: "Email the Plotus team",
          href: mail("Accessibility feedback"),
        },
      ],
    },
  ],
};
await mkdir(resolve(out, "accessibility"), { recursive: true });
await writeFile(
  resolve(out, "accessibility/index.html"),
  document({
    title: "Accessibility — Plotus",
    description: accessibility.intro,
    path: "/accessibility/",
    body: legalBody(accessibility),
    legal: true,
  }),
);
await writeFile(
  resolve(out, "404.html"),
  document({
    title: "A little off-plan — Plotus",
    description: "This page could not be found. Find your way back to Plotus.",
    path: "/404.html",
    noindex: true,
    body: `<section class="container error-page"><p class="eyebrow">404 · A LITTLE OFF-PLAN</p><h1>There’s room<br>to turn around.</h1><p>We couldn’t find that page. Let’s take you somewhere familiar.</p><a class="button button-dark" href="/">Back to Plotus ${arrow}</a></section>`,
  }),
);
const routes = [
  "/",
  ...legalPages.map((p) => "/" + p.path.replace(/^\/+|\/+$/g, "") + "/"),
  "/accessibility/",
];
await writeFile(
  resolve(out, "sitemap.xml"),
  `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${routes.map((path) => `<url><loc>${origin}${path}</loc></url>`).join("")}</urlset>`,
);
await writeFile(
  resolve(out, "robots.txt"),
  `User-agent: *\nAllow: /\nDisallow: /projects\nDisallow: /settings\nDisallow: /login\nDisallow: /_app/\nSitemap: ${origin}/sitemap.xml\n`,
);
await writeFile(
  resolve(out, "_redirects"),
  "/privacy-policy /privacy/ 301\n/privacy-policy/ /privacy/ 301\n/terms-of-service /terms/ 301\n/terms-of-service/ /terms/ 301\n/delete-account /account-deletion/ 301\n/delete-account/ /account-deletion/ 301\n",
);
await writeFile(
  resolve(out, "_headers"),
  `/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: strict-origin-when-cross-origin\n  X-Frame-Options: DENY\n  Permissions-Policy: camera=(), microphone=(), geolocation=()\n  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'none'; upgrade-insecure-requests\n/assets/*\n  Cache-Control: public, max-age=604800\n/.well-known/apple-app-site-association\n  Content-Type: application/json\n  Cache-Control: public, max-age=3600\n`,
);
console.log(
  `Built Plotus marketing site: ${routes.length} public routes + 404 in ${out}`,
);
