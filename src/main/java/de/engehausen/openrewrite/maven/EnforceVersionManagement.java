package de.engehausen.openrewrite.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.AddManagedDependencyVisitor;
import org.openrewrite.maven.AddProperty;
import org.openrewrite.maven.ManageDependencies;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.RemoveProperty;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.GroupArtifactVersion;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.xml.tree.Xml.Document;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enforce dependency management in root {@code pom.xml}.
 * Use option {@code retainVersions} to specify a list of GAVs
 * that should be left untouched (optional).
 */
public class EnforceVersionManagement extends ScanningRecipe<EnforceVersionManagement.ProjectInformation> {

	protected static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)\\}");

	@Option(displayName = "Retain versions", description = """
		Accepts a list of GAVs. Each matching GAV that is a project direct dependency
		will be exempt from a move to dependency management. The version can be omitted
		from the GAV.
		""", example = "com.jcraft:jsch", required = false)
	@Nullable
	protected List<String> retainVersions;
	protected final Set<GroupArtifactVersion> exemptions;

	/**
	 * Creates the recipe with the given list of GAV exemptions.
	 * @param retainVersions the list of GAVs to not touch; may be {@code null}.
	 */
	@JsonCreator
	public EnforceVersionManagement(@Nullable @JsonProperty("retainVersions") final List<String> retainVersions) {
		this.retainVersions = retainVersions;
		exemptions = new HashSet<>(Optional.ofNullable(retainVersions)
			.map(list -> list.stream()
				.map(str -> {
					final String[] parts = str.split(":");
					switch (parts.length) {
						case 3:
							return new GroupArtifactVersion(parts[0], parts[1], parts[2]);
						case 2:
							return new GroupArtifactVersion(parts[0], parts[1], null);
						default:
							return null;
					}
				})
				.filter(version -> version != null)
				.toList()
			).orElseGet(List::of));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDisplayName() {
		return "Enforce Maven version management";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDescription() {
		return "Makes sure versions are managed in the management sections of the (root) pom.xml.";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProjectInformation getInitialValue(final ExecutionContext ctx) {
		return new ProjectInformation(new HashMap<>(), new HashMap<>(), new HashSet<>());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TreeVisitor<?, ExecutionContext> getScanner(final ProjectInformation accumulator) {
		return new MavenIsoVisitor<ExecutionContext>() {
			@Override
			public Xml.Document visitDocument(final Xml.Document document, final ExecutionContext ctx) {
				final ResolvedPom pom = getResolutionResult().getPom();
				final List<GroupArtifactVersion> list = accumulator.dependencies().computeIfAbsent(document.getId(), id -> new ArrayList<>());
				Optional.ofNullable(pom.getRequestedDependencies())
					.ifPresent(deps -> deps
						.stream()
						.map(Dependency::getGav)
						.filter(gav -> gav.getVersion() != null)
						.filter(this::notExempt)
						.forEach(gav -> {
							list.add(gav);
							final String version = gav.getVersion();
							if (EnforceVersionManagement.hasProperty(version)) {
								EnforceVersionManagement
									.extractPropertyNames(version)
									.forEach(name -> {
										Optional
											.ofNullable(pom.getProperties())
											.map(props -> props.get(name))
											.filter(value -> value != null)
											.ifPresent(value -> {
												accumulator.versionProperties().put(name, value);
											});
									});
							}
						})
					);
				if (getResolutionResult().getParent() == null) {
					accumulator.roots().add(document.getId());
				}
				return document;
			}
			protected boolean notExempt(final GroupArtifactVersion candidate) {
				if (exemptions.contains(candidate) || exemptions.contains(candidate.withVersion(null))) {
					return false;
				}
				return true;
			}
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor(final ProjectInformation accumulated) {
		return new MavenIsoVisitor<ExecutionContext>() {
			@Override
			public Document visitDocument(final Document document, final ExecutionContext ctx) {
				final boolean isRoot = accumulated.roots().contains(document.getId());
				if (isRoot) {
					final List<GroupArtifactVersion> dependencies = accumulated
						.dependencies()
						.values()
						.stream()
						.flatMap(List::stream)
						.toList();
					if (!dependencies.isEmpty()) {
						maybeUpdateModel();
						dependencies
							.forEach(gav -> 
								doAfterVisit(new AddManagedDependencyVisitor(gav.getGroupId(), gav.getArtifactId(), gav.getVersion(), null, null, null))
							);
						if (!accumulated.versionProperties().isEmpty()) {
							accumulated
								.versionProperties()
								.forEach((name, value) ->
									doAfterVisit(new AddProperty(name, value, Boolean.TRUE, Boolean.FALSE).getVisitor())
								);
						}
					}
				}
				Optional.ofNullable(accumulated.dependencies().get(document.getId()))
					.filter(deps -> !deps.isEmpty())
					.ifPresent(deps -> {
						maybeUpdateModel();
						deps.forEach(gav -> {
							final String version = gav.getVersion();
							if (!isRoot && EnforceVersionManagement.hasProperty(version)) {
								EnforceVersionManagement
									.extractPropertyNames(version)
									.forEach(name -> doAfterVisit(new RemoveProperty(name).getVisitor()));
							}
							doAfterVisit(new ManageDependencies(gav.getGroupId(), gav.getArtifactId(), Boolean.TRUE, Boolean.FALSE).getVisitor());
						});
					});
				return super.visitDocument(document, ctx);
			}
		};
	}

	protected static boolean hasProperty(final String version) {
		return version != null && version.indexOf("${") >= 0;
	}

	protected static List<String> extractPropertyNames(final String version) {
		final Matcher matcher = PROPERTY.matcher(version);
		final List<String> result = new ArrayList<>();
		while (matcher.find()) {
			result.add(matcher.group(1));
		}
		return result;
	}

	protected static record ProjectInformation(Map<UUID, List<GroupArtifactVersion>> dependencies, Map<String, String> versionProperties, Set<UUID> roots) {}
}
