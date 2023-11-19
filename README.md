# Enforce version management

This is an [OpenRewrite](https://docs.openrewrite.org/) recipe to enforce
managing all dependency versions in the root `pom.xml` of a project building
with [Maven](https://maven.apache.org/).

The idea is that **all** dependency versions should be specified once, in one
place. For this, the `dependencyManagement` element is used. This makes
sense for most multi-module Maven builds, but may be overkill for simple
builds.

To build the recipe, run

	mvn install

Now it can be used on an existing Maven project like this:

```
mvn org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=de.engehausen:enforce-version-management:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=de.engehausen.openrewrite.maven.EnforceVersionManagement
```

The recipe can also be used in other declarative recipes using

```
---
type: specs.openrewrite.org/v1beta/recipe
name: foo.bar.ManageDependencies
displayName: Manage all dependency versions in root pom.xml.
recipeList:
  - de.engehausen.openrewrite.maven.EnforceVersionManagement:
      # list of GAVs (groupId, artifactId, version) that should not be
      # touched, version field is optional
      retainVersions: null
```

**Disclaimer**

Maven builds can be complex, and the performed updates may break the build;
carefully check them before committing the changes.
