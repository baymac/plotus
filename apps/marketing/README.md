# Plotus marketing site

Static HTML, CSS and browser JavaScript. The product UI remains Kotlin/Compose; this site is separate marketing and policy content. Node 22 or later is the only build dependency.

```sh
cd apps/marketing
npm run build
npm run dev
```

Build output: `apps/marketing/dist`. Cloudflare Pages project: `plotus-landing`; public domain: `plotus.app`. No server, tracking pixel, analytics script, or website form storage is included. `hello@plotus.app` uses Cloudflare Email Routing to the verified project contact. Deployment uses the authenticated Cloudflare account, never repository credentials.

```sh
npx wrangler pages deploy dist --project-name plotus-landing --branch main
```

## Public review URLs

| Purpose                                           | URL                                  |
| ------------------------------------------------- | ------------------------------------ |
| App website                                       | https://plotus.app/                  |
| Privacy policy                                    | https://plotus.app/privacy/          |
| Terms                                             | https://plotus.app/terms/            |
| App Store support                                 | https://plotus.app/support/          |
| Meta data-deletion **instructions**, not callback | https://plotus.app/data-deletion/    |
| Account deletion instructions                     | https://plotus.app/account-deletion/ |
| Contact                                           | https://plotus.app/contact/          |

These public pages do not complete app approval. Current account deletion is a manual email request. Apple still requires an accessible in-app deletion action, an in-app privacy link, and a qualifying privacy-preserving login option under guideline 4.8 unless an exemption applies. Facebook Login implementation, testing and reviewer access are separate work. Do not configure the instructions page as an automated Meta deletion callback.

Current demo limits and data handling are reflected in the public copy. Update `content/legal.mjs` before changing collection, storage, providers or tracking. Concept imagery is clearly labelled and does not claim to be app output.

## Design sources

The supplied [Plotus landing design](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=81-2) supplies the brand and palette. Marketing references: [Cover](https://buildcover.com/), [Rayon](https://www.rayon.design/), [Maket](https://www.maket.ai/), [Finch](https://www.finch3d.com/), [Higharc](https://www.higharc.com/), [Homebound](https://www.homebound.com/) and [Houzz Pro](https://pro.houzz.com/pro). No competitor imagery or page copy is included.

The original courtyard image was generated with the built-in image generation tool for this site, then compressed to `public/assets/courtyard-concept.webp`; its social-preview copy is `public/assets/social-card.jpg`. The final prompt requested a premium, realistic architectural maquette of a single-storey courtyard home, partial roof exposing warm internal rooms, ivory plaster, pale oak, terracotta details, an olive garden and mature tree, on a seamless warm-cream background. Elevated three-quarter camera, soft late-afternoon studio light, no text, logos, watermarks or interface. The hand-authored SVG study is an illustrative composition, not a construction drawing. Mark and font files reuse existing public Plotus assets.
