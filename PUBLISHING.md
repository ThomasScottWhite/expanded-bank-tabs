# Publishing to Plugin Hub

1. Create a public GitHub repository from the RuneLite example-plugin template
   or copy this project into a new public repository.
2. Push the project and verify that `runelite-plugin.properties` contains the
   correct display name, author, description, tags, and plugin class.
3. Test the plugin in a clean RuneLite development client with the normal Bank
   Tags plugin enabled. Do not load it together with the old source-fork
   implementation because both versions create an expanded-view button.
4. Fork `runelite/plugin-hub`, create a branch, and add
   `plugins/expanded-bank-tabs` containing:

   ```properties
   repository=https://github.com/<account>/expanded-bank-tabs.git
   commit=<40-character-commit-hash>
   ```

5. Open a pull request from the fork branch. Fix any CI or Plugin Hub check
   feedback by updating the repository commit hash in the same pull request.
6. Wait for review. Plugin Hub maintainers review security, Jagex rule
   compliance, dependencies, and compatibility with rejected or rolled-back
   RuneLite features.

The authoritative instructions are in the [RuneLite Plugin Hub README](https://github.com/runelite/plugin-hub).
