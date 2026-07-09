# Alch Hero: Setup and Testing for Dummies

You have never done Java development. That's fine. Follow this top to bottom and you'll have RuneLite running with your plugin loaded in about 30 minutes, most of which is waiting on downloads.

This guide assumes Windows. Mac/Linux differences are noted where they matter.

---

## Part 1: Install the two tools you need

### Step 1: Install Java (JDK 11)

RuneLite plugins are built against Java 11 specifically. Newer is not better here.

1. Go to **adoptium.net**
2. Click the dropdown that says "Latest LTS" or version selector and choose **11 (LTS)**, then download the installer for your OS (the .msi on Windows)
3. Run the installer. **Important:** when you see a screen with feature options, click the dropdown next to **"Set JAVA_HOME variable"** and select **"Will be installed on local hard drive."** Everything else stays default
4. Finish the install

To verify it worked: open Command Prompt (press Windows key, type `cmd`, Enter) and type:

```
java -version
```

You should see something like `openjdk version "11.0.x"`. If it says "not recognized," restart your computer and try again.

### Step 2: Install IntelliJ IDEA Community Edition

This is the code editor. Community Edition is the free one.

1. Go to **jetbrains.com/idea/download**
2. Scroll DOWN past the paid "Ultimate" version to **IntelliJ IDEA Community Edition** and download it
3. Run the installer, all defaults are fine
4. Launch it once so it finishes first-time setup. Skip any tour/login prompts

---

## Part 2: Open the project

### Step 3: Unzip the plugin

Extract `alch-hero.zip` somewhere sensible, like `C:\dev\alch-hero`. Avoid folders with spaces or OneDrive-synced locations if you can, they occasionally cause weird build issues.

The folder should contain `build.gradle`, `README.md`, and a `src` folder. If you see a single `alch-hero` folder inside another `alch-hero` folder, open the inner one in the next step.

### Step 4: Open it in IntelliJ

1. In IntelliJ: **File > Open** (or "Open" on the welcome screen)
2. Select the folder that contains `build.gradle` (the folder itself, not the file)
3. When asked, click **Trust Project**
4. Now wait. IntelliJ runs a "Gradle sync" that downloads the entire RuneLite client and all dependencies. You'll see a progress bar at the bottom of the window. First time takes 5 to 15 minutes depending on your connection. **Do not touch anything until the bottom bar goes quiet**

### Step 5: Fix the two most common first-time problems

**Problem A: Everything is underlined in red with errors about "getSpawnTime" or "getNotes" not existing.**
This is Lombok (a library that auto-generates boilerplate code) not being enabled.

1. **File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors**
2. Check **Enable annotation processing**, click OK
3. If still red: **File > Settings > Plugins**, search "Lombok," make sure it's installed and enabled, then restart IntelliJ

**Problem B: An error mentioning the wrong Java version.**

1. **File > Project Structure > Project**
2. Set **SDK** to your installed Java 11 (it may say "temurin-11")
3. Set language level to 11, click OK

---

## Part 3: Run it

### Step 6: Set up the run configuration

1. In the left file tree, navigate to `src > test > java > com.alchhero > AlchHeroPluginTest`
2. Open it. You'll see a small **green play arrow** in the left margin next to `public static void main`
3. Click the arrow and choose **Run 'AlchHeroPluginTest.main()'**. It will launch RuneLite. Close it for now, we need one tweak first
4. Top right of IntelliJ, next to the play button, click the dropdown showing `AlchHeroPluginTest` and choose **Edit Configurations**
5. Click **Modify options > Add VM options**, and in the new "VM options" box type exactly:

```
-ea
```

6. Click OK

`-ea` means "enable assertions." RuneLite's developer tooling expects it, and Plugin Hub reviewers assume you tested with it.

### Step 7: Actually test

Click the green play button. A real, fully functional RuneLite client opens with Alch Hero loaded as if it were built in.

1. Log in with your normal account. This is just the regular client, nothing sketchy is happening
2. Click the **wrench icon** in the RuneLite sidebar, search **"Alch Hero,"** confirm it appears and is toggled on
3. Grab some nature runes and alchables, cast High Alchemy once, and the track should appear anchored to the spell icon
4. Keep casting on rhythm and watch notes fall and get scored

### Step 8: The test checklist

Run through all of these before submitting anywhere:

- [ ] Track appears after the first alch cast and notes fall every 5 ticks
- [ ] Ratings feel right in default Cast mode. If they're consistently early or late, adjust **Calibration offset** in the plugin config until a perfectly timed cast reads Perfect
- [ ] Switch scoring mode to **Click** in the config, confirm clicks still work normally in game (nothing feels swallowed or delayed) and scoring gets more precise
- [ ] Open the bank: everything disappears. Close it: track comes back
- [ ] Open the Settings side panel: track pauses
- [ ] Stop alching for about 9 seconds: track clears itself
- [ ] Toggle **Fixed** and **Resizable** mode (Esc > Display in game settings) and drag the window around in resizable. Track must stay glued to the spell icon in both
- [ ] Turn the plugin off and on from the wrench panel, no errors in the IntelliJ console at the bottom

If the client crashes or misbehaves, the IntelliJ console at the bottom shows the error. Copy the first chunk that mentions `com.alchhero` and bring it back to me.

---

## Part 4: Put it on GitHub

You need the code in a public GitHub repo before you can submit to the Plugin Hub. Easiest path with zero command line:

1. Install **GitHub Desktop** (desktop.github.com) and sign in with your GitHub account
2. **File > Add local repository**, pick your `alch-hero` folder. It will say it isn't a repo yet and offer to **create a repository here**. Accept, defaults are fine
3. Bottom left, type a commit message like `Initial release`, click **Commit to main**
4. Click **Publish repository** at the top. **Uncheck "Keep this code private."** Publish

Your code now lives at `github.com/YOURNAME/alch-hero`.

Optional but nice: add an `icon.png` (48x72 max) to the folder, commit, and push. It becomes the plugin's Hub icon.

## Part 5: Submit to the Plugin Hub

1. On GitHub (in a browser), go to **github.com/runelite/plugin-hub** and click **Fork** (top right)
2. In YOUR fork, navigate into the `plugins` folder, click **Add file > Create new file**
3. Name the file exactly `alch-hero` (no extension). Contents, two lines:

```
repository=https://github.com/YOURNAME/alch-hero.git
commit=PASTE_FULL_COMMIT_HASH_HERE
```

To get the commit hash: on your alch-hero repo page, click the commit count or the clock icon showing latest commit, and copy the full 40-character hash (there's a copy button)

4. Commit the new file, then GitHub will offer to **open a pull request** back to runelite/plugin-hub. Do it
5. In the PR description, paste the Compliance section from your README and one screenshot of the overlay in action
6. Their CI will build your plugin automatically. Green check means it compiles. Then wait for a human reviewer, which can take days to a couple weeks. Respond to any feedback with new commits to your repo, then update the `commit=` hash in the PR

That's it. Once merged, it appears in the Plugin Hub for everyone.

---

## Quick troubleshooting reference

| Symptom | Fix |
|---|---|
| `java -version` not recognized | Reboot; if still broken, reinstall JDK with JAVA_HOME option enabled |
| Gradle sync fails or hangs | Check internet, then **File > Invalidate Caches > Invalidate and Restart** |
| Red errors on getters/setters | Enable annotation processing (Step 5, Problem A) |
| "Unsupported class file version" | Wrong JDK selected (Step 5, Problem B) |
| Client opens but plugin missing | Make sure you ran `AlchHeroPluginTest`, not some other config |
| Overlay never appears in game | Cast High Alchemy once first; it activates on your first cast |
