# Hydration: Ui_General

**Session:** settings UI normalization — General tab: removed the "DISPLAY" SectionHeader and
promoted "Layers" / "Navigation" to title-case top-level headers (`SectionHeader` now supports
`uppercase = false`); tightened collapsed padding (`SettingsExpander` row 8→6dp, after-expander
spacer 16→4dp). System tab: stop-detection card de-nested (two nested `SettingsToggleRow` → inline
rows); Regenerate Layers merged from 4 standalone cards into one card with visible dividers.
New `settings_section_layers` / `settings_section_navigation` strings (EN+FR); removed
`settings_section_display`. `ui-tokens.properties` + `ui-component-guidelines.md`
(§2.3/§2.6/§2.9/§3) synced. BUILD SUCCESSFUL.

**Target files:**
- `MapScreen.kt`, `ui-tokens.properties`, `values/strings.xml`, `values-fr/strings.xml`, `docs/ui-component-guidelines.md`

**Plans:**
- (none — work captured directly in the session above)

**Last Bake:** 2026-09-02 15:16 UTC
