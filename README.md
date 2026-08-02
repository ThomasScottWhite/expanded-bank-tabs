# Expanded Bank Tabs

This is a standalone RuneLite Plugin Hub plugin. It uses the existing Bank
Tags plugin as its source of bank tabs and does not replace or modify the
RuneLite Bank Tags implementation.

## Current features

- Opens an expanded bank-tab page from a square chest button below the Bank
  Tags new-tab button.
- Reads existing Bank Tags tabs and their configured item icons.
- Opens a tab through the public `BankTagsService` API and closes the expanded
  page when a tab is selected.
- Stores custom groups separately from Bank Tags, with an always-present
  ungrouped default group.
- Supports creating, renaming, deleting, collapsing, reordering, and dragging
  tabs between groups.
- Blocks interaction with the bank items hidden underneath the expanded page.

## Local development

From the parent workspace, run `run-new-dev.bat`. It builds the clean RuneLite
checkout in `runelite-standalone-dev`, compiles this plugin against that client,
and starts a separate development client using the `bank-tabs-dev` profile.

The development client uses the normal `%USERPROFILE%\.runelite` directory so
it can read the credentials written by the Jagex Launcher and the existing Bank
Tags configuration. Its RuneLite settings are kept in the `bank-tabs-dev`
profile. Do not run the old fork launcher and this standalone launcher at the
same time with the same profile.

If your normal Bank Tags settings use a different profile, change
`DEV_PROFILE=bank-tabs-dev` near the top of `run-new-dev.bat` to that profile's
name.

For manual builds, use the commands below.

Build the RuneLite development client first, then compile this plugin against
its shaded jar:

```powershell
$env:JAVA_HOME='C:\path\to\jdk21'
& 'C:\path\to\gradle.bat' build --offline --no-daemon `
  -PruneLiteClientJar='C:\path\to\runelite-client-*-shaded.jar'
```

If JDK 21 reports a ZIP filesystem `AccessDeniedException` while closing the
compiler, use the RuneLite shaded jar outside a synchronized folder or use
`-PruneLiteClientClasses` with an exploded client classpath for compilation.

The Plugin Hub build uses the standard RuneLite plugin template dependency
when `runeLiteClientJar` is not supplied.

## Important limitation

The plugin intentionally uses the Bank Tags configuration as a read/write
compatibility boundary. It does not reach into Bank Tags' package-private
classes or modify RuneLite source files. This is what makes it suitable for a
standalone Plugin Hub submission, but it also means future Bank Tags config
format changes need compatibility handling here.
