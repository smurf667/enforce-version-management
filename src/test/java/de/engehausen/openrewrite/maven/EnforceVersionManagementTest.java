package de.engehausen.openrewrite.maven;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.test.RewriteTest;

class EnforceVersionManagementTest implements RewriteTest {

	@Test
	void untouched() {
		rewriteRun(spec -> spec.recipe(
			new EnforceVersionManagement(null)),
			pomXml("""
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>junit</groupId>
								<artifactId>junit</artifactId>
								<version>4.13.2</version>
							</dependency>
						</dependencies>
					</dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""")
			);
	}

	@Test
	void manageVersion() {
		rewriteRun(spec -> spec.recipe(
			new EnforceVersionManagement(null)),
			pomXml("""
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<version>4.13.2</version>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""", """
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>junit</groupId>
								<artifactId>junit</artifactId>
								<version>4.13.2</version>
							</dependency>
						</dependencies>
					</dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""")
			);
	}

	@Test
	void manageVersionWithProperty() {
		rewriteRun(spec -> spec.recipe(
			new EnforceVersionManagement(null)),
			pomXml("""
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<properties>
						<the.version>4.13.2</the.version>
					</properties>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<version>${the.version}</version>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""", """
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<properties>
						<the.version>4.13.2</the.version>
					</properties>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>junit</groupId>
								<artifactId>junit</artifactId>
								<version>${the.version}</version>
							</dependency>
						</dependencies>
					</dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""")
			);
	}

	@Test
	void manageVersionWithAlreadyManaged() {
		rewriteRun(spec -> spec.recipe(
			new EnforceVersionManagement(null)),
			pomXml("""
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>org.junit.jupiter</groupId>
								<artifactId>junit-jupiter-engine</artifactId>
								<version>5.9.2</version>
							</dependency>
						</dependencies>
					</dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<version>4.13.2</version>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""", """
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>junit</groupId>
								<artifactId>junit</artifactId>
								<version>4.13.2</version>
							</dependency>
							<dependency>
								<groupId>org.junit.jupiter</groupId>
								<artifactId>junit-jupiter-engine</artifactId>
								<version>5.9.2</version>
							</dependency>
						</dependencies>
					</dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""")
			);
	}

	@ParameterizedTest
	@CsvSource({"junit:junit:4.13.2", "junit:junit"})
	void exemptVersions(final String version) {
		rewriteRun(spec -> spec.recipe(
			new EnforceVersionManagement(List.of(version))),
			pomXml("""
				<project>
					<groupId>foo</groupId>
					<artifactId>bar</artifactId>
					<version>0</version>
					<dependencies>
						<dependency>
							<groupId>junit</groupId>
							<artifactId>junit</artifactId>
							<version>4.13.2</version>
							<scope>test</scope>
						</dependency>
					</dependencies>
				</project>
				""")
			);
	}

	@Test
	void manageMultiModule() {
		rewriteRun(spec -> spec.recipe(
			new EnforceVersionManagement(null)),
			pomXml("""
				<project>
					<groupId>foo</groupId>
					<artifactId>bar-parent</artifactId>
					<version>0</version>
					<modules>
						<module>child</module>
					</modules>
				</project>
				""",
				"""
				<project>
					<groupId>foo</groupId>
					<artifactId>bar-parent</artifactId>
					<version>0</version>
					<modules>
						<module>child</module>
					</modules>
					<properties>
						<junit.version>4.13.2</junit.version>
					</properties>
					<dependencyManagement>
						<dependencies>
							<dependency>
								<groupId>junit</groupId>
								<artifactId>junit</artifactId>
								<version>${junit.version}</version>
							</dependency>
						</dependencies>
					</dependencyManagement>
				</project>
				"""),
			mavenProject("child",
				pomXml("""
					<project>
						<parent>
							<groupId>foo</groupId>
							<artifactId>bar-parent</artifactId>
							<version>0</version>
						</parent>
						<properties>
							<ignore>true</ignore>
							<junit.version>4.13.2</junit.version>
						</properties>
						<dependencies>
							<dependency>
								<groupId>junit</groupId>
								<artifactId>junit</artifactId>
								<version>${junit.version}</version>
							</dependency>
						</dependencies>
					</project>
					""",
					"""
					<project>
						<parent>
							<groupId>foo</groupId>
							<artifactId>bar-parent</artifactId>
							<version>0</version>
						</parent>
						<properties>
							<ignore>true</ignore>
						</properties>
						<dependencies>
							<!--~~(No version provided)~~>--><dependency>
								<groupId>junit</groupId>
								<artifactId>junit</artifactId>
							</dependency>
						</dependencies>
					</project>
					""")
				)
		);
	}
}
