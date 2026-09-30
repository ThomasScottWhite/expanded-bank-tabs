# Expanded Bank Tags Viewer

Expanded Bank Tags Viewer adds an expanded interface to manage and view Bank Tags. It keeps Bank Tags as the source of truth and adds organization tools without replacing the normal bank interface.

## Features

- View all Bank Tags tabs in one scrollable panel.
- Create, rename, delete, collapse, and reorder groups.
- Drag tabs between groups.
- Create new Bank Tags tabs from a group.
- Keep tab icons synchronized with Bank Tags.

## Usage

Enable both **Bank Tags** and **Expanded Bank Tags Viewer**, then open a bank and
select the chest button in the bank tab sidebar. Use **Create group** and the
group controls to organize your tabs.

## Compatibility

The plugin stores group settings separately and uses the existing Bank Tags
configuration for tab names, icons, layouts, and item tags.

Bank Tags integration uses the public `BankTagsService`. Sidebar refreshes locate
the existing Bank Tags plugin through `PluginManager`; they do not inject its
private plugin instance or tab manager.

## Development checks

Run `gradle test -PruneLiteVersion=1.13.1` to check the current Plugin Hub client
version, or `gradle test` to use the latest release. The regression tests cover
plugin injection, preserving saved tabs when adding tabs or changing icons, and
sidebar refresh lifecycle handling. JUnit and Mockito are test-only dependencies.

Before release, manually check the chest button, opening a tag, adding a tab,
changing an icon, and reopening the bank in a clean development client with Bank
Tags enabled. Confirm existing tabs, icons, layouts, and groups are preserved.
