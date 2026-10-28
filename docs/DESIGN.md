# Design

Plotus uses warm paper tones, terracotta accents and a quiet sage palette to make a technical planning task feel approachable. Light and dark themes share the same layout and visual identity across iPhone and web.

The welcome screen pairs a miniature home illustration with “Make room for your next idea.” Google, Facebook and passkey actions sit below it. Projects keep names, locations and client details easy to scan.

## Sources

- [Plotus interface](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=349-144623): the supplied project screens, typography, colours and icons.
- [Plotus logo](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx/Plotus--Mobile--Desktop?node-id=463-18): the supplied mark and app-icon treatment.
- The supplied welcome-screen reference informed the large illustration, short headline and placement of sign-in actions.
- [Linear mobile](https://mobbin.com/screens/edac1658-5f94-4d9c-8be1-46eef50fbe09) and [Linear web](https://mobbin.com/screens/3997d60f-bea7-4068-af79-3942a664072e): clear authentication choices and restrained copy, adapted to Plotus's own branding.

The welcome illustration was newly drawn for Plotus. It is decorative and does not represent a checked or generated home design. The website favicon reuses the supplied Plotus mark. Desktop headers show the current section; account and appearance controls live in Settings. Detailed asset references are kept in the [source record](design-sources.json).

Plot setup follows the current **App design clean** flow: [mobile Boundary](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx?node-id=313-131949), [Direction](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx?node-id=313-133447), [Roads](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx?node-id=314-132220) and [desktop Boundary](https://www.figma.com/design/0AIbJahPpgu09bT3AqguPx?node-id=315-134015). The shared Kotlin canvas draws the measured geometry. The canvas, numbered tabs and actions stay fixed while the inspector scrolls. The smaller keyboard layout keeps measurements and save actions reachable.

This milestone ends with **Save plot**. Image upload and the Floor plan stage remain unavailable until their implementation.
