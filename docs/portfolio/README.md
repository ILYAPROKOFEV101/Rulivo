# Portfolio image provenance

These images are resized copies of existing application assets and QA captures. Source files were preserved. The copies have a maximum side of 960 px; the app icon has a maximum side of 144 px. No UI, camera scene, or measurement was generated for the portfolio.

| Portfolio file | Original file under the project root | Context |
| --- | --- | --- |
| `mode-menu.png` | `build/mode-icons/menu.png` | English mode-selection UI capture. |
| `room-result.png` | `build/camera-figures-qa/camera-room-result.png` | Room result UI capture with test values and earlier Russian localization. |
| `fit-result.png` | `build/camera-figures-qa/fit-result.png` | Fit-result UI capture with test values and earlier Russian localization. |
| `contour-renderer.png` | `build/contour-feature/qa/contour-world.png` | Application renderer output with deterministic contour geometry and synthetic background. |
| `box-renderer.png` | `build/camera-figures-qa/wireframe-box.png` | Application renderer output with deterministic box geometry. |
| `icon.png` | `app/src/main/ic_launcher-playstore.png` | Existing application icon. |

The source `build/` directories are local QA output and may be absent from a clean checkout. The copies in this directory make the README self-contained. Renderer captures demonstrate drawing behavior and are not physical accuracy evidence.
