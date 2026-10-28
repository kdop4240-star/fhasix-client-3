# Building Fhasix on GitHub

1. Upload everything in this folder to your GitHub repository (the workflow lives in
   `.github/workflows/build.yml`).
2. Open the repository's **Actions** tab. The **Build** workflow runs by itself on every push.
   You can also start it by hand: Actions > Build > **Run workflow**.
3. When it is green, open the run and download **fhasix-ui-theme** under *Artifacts*. The mod
   `.jar` is inside.
4. If it is red, open the failed step (usually **Build the mod**), copy the red `error:` lines
   and send them over.

## If the `.github` folder does not upload

Some upload methods skip folders that start with a dot. Then create the file by hand:
repository > **Add file > Create new file**, type the name exactly as
`.github/workflows/build.yml` (typing the `/` makes the folders) and paste the contents of
`GITHUB_build.yml.txt` into it, then **Commit changes**.

## Notes

* Java 21 and Gradle 9.2.1 are set in the workflow (`GRADLE_VERSION` at the top). The project has
  no `gradlew` wrapper; the workflow installs Gradle itself, so nothing else is needed.
* `.gitignore` keeps `build/` and `.gradle/` out of the repository.
