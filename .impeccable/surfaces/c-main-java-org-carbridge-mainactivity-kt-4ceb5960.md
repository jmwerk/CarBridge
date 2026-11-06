---
version: 1
slug: "c-main-java-org-carbridge-mainactivity-kt-4ceb5960"
primary_target: "patcher/src/main/java/org/carbridge/MainActivity.kt"
related_targets: []
---

Scope: CarBridge main screen (Android, Compose). Mode: Operate.
Audience/job: non-technical Android Auto owners; set up once, add an app to the car, keep car copies current or remove them.
Constraints: one system install prompt per copy; AA must reconnect; honest limits (parked/passengers, some apps refuse).

## Direction contract
THESIS: Each car copy is a highway guide sign — the app's name on its own green panel, "exit to the car". Refuses the stock settings-list with Patch buttons and a debug log.
OWN-WORLD: Guide-sign green (#00603F family) fields with white type and an inset white rounded border; sign-yellow (#F2C200) for setup/caution with near-black type; neutral Material surfaces elsewhere. Heavy-weight sans, uppercase short labels, generous corner radius like sign blanks. Material 3 controls.
STORY: The user sees what's on their car, what still needs setup, and which apps they can add; one tap adds, one prompt confirms, a snackbar says to reconnect.
FIRST VIEWPORT: Green sign header (CarBridge + one-line purpose, info button). Yellow setup card while steps remain. "On your car" sign cards. "Add an app" list with search below.
FORM: Highway guide signage, my list #3 of 7, seed f0d11730.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
