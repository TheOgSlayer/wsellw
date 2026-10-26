# SellPlugin

Paper 1.20+ plugin: `/sell` GUI, `/worth` lookup GUI, and "Worth: $X" on item tooltips. All prices come from `plugins/SellPlugin/prices.yml`.

**Requires:** Paper (or fork), [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy plugin (e.g. EssentialsX). **Optional:** [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) for the tooltip worth lines.

## Commands
| Command | What it does |
|---|---|
| `/sell` | Opens the double chest. Bottom-left clock = sell history, bottom-middle book = items sold, green pane (bottom-right) = sell. |
| `/sell reload` | Reloads prices.yml + config (`sellplugin.reload`) |
| `/sellhistory [stats]` | Opens your sell history (or the items-sold statistics). Same as the buttons in `/sell`. |
| `/worth` | GUI with every priced item, sortable A-Z / high-low / low-high |
| `/worth oak planks` | Exact item: shows its worth in chat + action bar |
| `/worth oak` | Not an exact item: opens GUI with all matching items |

## Build with GitHub
1. Create an empty repo on GitHub, then in this folder:
   ```
   git init && git add . && git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/SellPlugin.git
   git push -u origin main
   ```
2. Open the **Actions** tab, wait for the build, download the `SellPlugin` artifact (the jar).
3. For a Release page: `git tag v1.0.0 && git push --tags`.

Local build: `mvn package` -> `target/SellPlugin.jar`.
