# Plotus

**Make room for your next idea.**

Plotus is a home-planning workspace for architects and residential designers. It brings a project's location, client and design brief together before detailed design begins.

## Keep your projects together

Sign in with Google or Facebook to create projects, add client details and keep your work organised. Search by name or location, sort your projects, and update or remove them as your work changes. Your projects are saved to your account and available on iPhone and web.

Open a project to define its plot. Enter side lengths and optional diagonals, adjust the corners, set north and choose road-facing sides. On iPhone, the canvas stays on screen while Boundary, Direction and Roads reveal their controls below it. Upload a photo of a hand-drawn or printed sketch to extract measurements, then review and correct them before saving. Save a draft at any point, or complete Plot setup to save the boundary, direction and road details together. Your measured area appears in Projects.

Account settings also let you add a passkey on iPhone or web. Appearance options live in **Settings → General**.

Choose **Start project** at [plotus.app](https://plotus.app) to open your workspace. Sign-in is required on the web. The iPhone's separate **View demo** option contains temporary sample projects.

## Plan the home

Continue to **Floor plan** to review setbacks and the buildable area, choose the building’s floors, BHK and Vastu preference. Use automatic requirements or add rooms, attached bathrooms, closets, parking and circulation. Save the brief to continue on another device.

Choose **Generate plan** to request a checked concept. Follow the elapsed time while generation runs, browse earlier plans in History, or cancel a run. Generation continues when you leave the screen. Export a completed plan as PNG, PDF or DWG. If the requirements cannot produce a checked layout, Plotus asks you to adjust them. Detailed design, structural approval and construction documents still need a professional designer.

On the web, newly generated plans also include a **3D** concept view. Switch between the original 2D drawing and a simple model of the same floor, orbit or pan around it, view it from above, and show furniture or full-height walls. The model uses restrained materials to explain the layout. Earlier saved drawings remain available in 2D.

[App guide](docs/START-HERE.md) · [Design](docs/DESIGN.md)

Web builds also require Node.js 22.12+ and npm. Gradle builds the isolated React 3D viewer in `apps/plan-viewer` and packages it with the Compose web app. It loads in the browser when 3D is opened; native builds keep their existing Compose screens.

## Website

The public website and support/privacy/deletion pages live at [plotus.app](https://plotus.app). Your workspace opens at [plotus.app/projects](https://plotus.app/projects), with its own General and Account settings pages.

## Credits

The app and supporting public services were created during the hackathon. The supplied visual design and private planning engine predate the event; the app calls it through a private hosted service. Sketch reading uses [Meta’s hosted image-understanding API](https://dev.meta.ai/docs/image-understanding); its app integration was written during the event. [Sources and credits](docs/PROVENANCE.md).
