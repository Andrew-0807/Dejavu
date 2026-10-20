# Address bar design

The field where the user reads or types an address. Layout and position (top, bottom, compact, sidebar) are out of scope here.

## Implementation

Fenix in this tree uses the Compose `BrowserToolbar` from `android-components/components/compose/browser-toolbar` everywhere. There is no View toolbar behind a flag.

- Display, top position and compact bar: `dejavu/browser/DejavuTopBar.kt` (Dejavu's own display bar).
- Display, bottom position and home: `FullDisplayToolbar` with `Origin` (android-components).
- Edit, everywhere: `BrowserEditToolbar` with `InlineAutocompleteTextField` (android-components).
- Shared look: `compose/browser-toolbar/.../ui/AddressField.kt`. Every field above draws through it, so the look is defined once.
- Tablet suggestions dropdown: `search/awesomebar/AwesomeBarComposable.kt` plus the row and header composables of `compose/awesomebar`.

## States

1. Display. Pill. Leading slot holds the site security or tracking protection icon. Text is start aligned in one line. A page with a real title shows the title, plus the domain dimmed after it when the field is at least 280dp wide. A page without a real title (the title is empty or is the address itself) shows the domain in full color and the path dimmed. The path is dropped below 200dp and ellipsized otherwise. Trailing slot keeps the page actions (reader mode, translate, share).
2. Edit. Same pill and position, so the text does not jump. A 2dp accent ring replaces the 1dp hairline. The leading slot swaps to the search engine icon. The URL is selected (existing behaviour). Trailing slot shows the clear button while there is text, otherwise voice, QR or Lens. Inline autocomplete, the IME `Go` action, the Uri keyboard and no autocorrect are kept.
3. Hint. Empty field shows the hint in `onSurfaceVariant`.
4. Private. Same rules. Colors come from the private scheme (Firefox purple), so nothing is special cased.

## Tokens

All colors come from the ambient `MaterialTheme.colorScheme`. The bars already map `surfaceContainerHighest` to a translucent tint of `onSurface` while not editing (`DejavuToolbar.glass`), so the field reads as glass on the workspace colors and turns solid while editing.

| Role | Token |
| --- | --- |
| Field fill | `surfaceContainerHighest` |
| Hairline (display) | `onSurface` at 12% alpha, 1dp |
| Focus ring (edit) | `primary`, 2dp |
| Title and domain | `onSurface` |
| Path, domain after title, hint | `onSurfaceVariant` |
| Cursor | `primary` (existing) |
| Dropdown surface | `surfaceContainerHigh`, 1dp `outlineVariant` border |
| Dropdown row highlight (hover, keyboard focus, press) | Material state layer of `onSurface`, clipped to a 12dp rounded row |
| Dropdown section header | `primary`, `labelLarge` |

## Sizes

- Field height 48dp, pill shape (`CircleShape`), inside a 64dp bar (8dp above and below).
- Text 16sp. The toolbar size setting shrinks the whole bar by a layer (`ScaledToolbar`, 70% to 100%). The field text grows to keep at least 13sp on screen: `max(16, 13 / scale)` sp.
- Touch target: the whole 64dp bar row opens editing in the top and compact bars. At 100% to 75% this is at least 48dp on screen. At 70% it is 44.8dp (see risks).
- Dropdown: 20dp corner radius, 6dp inner padding, rows at least 48dp tall with a 12dp corner highlight, headers at least 36dp in `labelLarge`. Rows and headers are shared with the phone suggestion list.

## Motion

Focusing happens tens of times a day, so motion is short and limited to color and opacity.

| What | Spec |
| --- | --- |
| Ring in (hairline to 2dp `primary`) | 150ms, cubic-bezier(0.23, 1, 0.32, 1) |
| Leading icon swap (search engine icon fades in from 40%) | same animation, same clock |
| Unfocus | instant. Exits are faster than entries, and most exits come from Enter or Back. |
| Focus from a hardware keyboard (Ctrl+L, typing) | instant, no animation |

The ring is a `Compose Animatable` drawn in the draw phase, so it does not recompose per frame. Leaving edit mode mid animation disposes it, which is the interruption. Compose scales every animation by the system animator duration scale, and "Remove animations" sets that scale to 0, so the ring snaps. Nothing moves position, so reduced motion needs no other change.
