/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.junit.platform.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import org.junit.platform.console.ConsoleLauncher
import org.junit.platform.engine.discovery.ClassNameFilter
import spock.lang.Specification

/**
 * @since 1.0
 */
class JUnitPlatformPluginSpec extends Specification {

	Project project

	def setup() {
		project = ProjectBuilder.builder().build()
	}


	def "applying the plugin"() {
		when:
		project.apply plugin: 'org.junit.platform.gradle.plugin'
		then:
		project.plugins.hasPlugin(JUnitPlatformPlugin)
		project.plugins.getPlugin(JUnitPlatformPlugin) instanceof JUnitPlatformPlugin
		project.extensions.findByName('junitPlatform') instanceof JUnitPlatformExtension
	}

	def "setting junitPlatform properties"() {

		project.apply plugin: 'java'
		project.apply plugin: 'org.junit.platform.gradle.plugin'

		when:
		project.junitPlatform {
			platformVersion '5.0.0-M1'
			enableStandardTestTask true
			logManager 'org.apache.logging.log4j.jul.LogManager'

			filters {
				includeClassNamePattern '.*Tests?'
				engines {
					include 'foo'
					exclude 'bar'
				}
				tags {
					include 'fast'
					exclude 'slow'
				}
			}

			reportsDir new File("any")
		}
		then:
		true == true
	}

	def "creating junitPlatformTest task"() {

		project.apply plugin: 'java'
		project.apply plugin: 'org.junit.platform.gradle.plugin'

		when:
		project.junitPlatform {
			// enableStandardTestTask // defaults to false
			logManager 'org.apache.logging.log4j.jul.LogManager'

			filters {
				includeClassNamePattern '.*Tests?'
				includeClassNamePatterns 'Foo', 'Bar'
				engines {
					include 'foo'
					exclude 'bar'
				}
				tags {
					include 'fast'
					exclude 'slow'
				}
			}

			reportsDir new File("/any")
		}
		project.evaluate()

		then:
		Task junitTask = project.tasks.findByName('junitPlatformTest')
		junitTask instanceof JavaExec
		junitTask.main == ConsoleLauncher.class.getName()

		junitTask.args.contains('--hide-details')
		junitTask.args.contains('--scan-class-path')
		junitTask.args.containsAll('-n', '.*Tests?', '-n', 'Foo', '-n', 'Bar')
		junitTask.args.containsAll('-t', 'fast')
		junitTask.args.containsAll('-T', 'slow')
		junitTask.args.containsAll('-e', 'foo')
		junitTask.args.containsAll('-E', 'bar')
		junitTask.args.containsAll('--reports-dir', new File('/any').getCanonicalFile().toString())
		junitTask.args.contains(project.file('build/classes/main').absolutePath)
		junitTask.args.contains(project.file('build/resources/main').absolutePath)
		junitTask.args.contains(project.file('build/classes/test').absolutePath)
		junitTask.args.contains(project.file('build/resources/test').absolutePath)

		Task testTask = project.tasks.findByName('test')
		testTask instanceof Test
		testTask.enabled == false
	}

	def "uses standard class name pattern"() {

		project.apply plugin: 'java'
		project.apply plugin: 'org.junit.platform.gradle.plugin'

		when:
		project.evaluate()

		then:
		Task junitTask = project.tasks.findByName('junitPlatformTest')
		junitTask.args.containsAll('-n', ClassNameFilter.STANDARD_INCLUDE_PATTERN)
	}

	def "enableStandardTestTask set to true"() {

		project.apply plugin: 'java'
		project.apply plugin: 'org.junit.platform.gradle.plugin'

		when:
		project.junitPlatform { enableStandardTestTask true }
		project.evaluate()

		then:
		Task testTask = project.tasks.findByName('test')
		testTask instanceof Test
		testTask.enabled == true
	}

	def "when buildDir is set to non-standard location, it will be honored"() {
		project.apply plugin: 'java'
		project.apply plugin: 'org.junit.platform.gradle.plugin'

		when:
		project.buildDir = new File('/foo/bar/build')
		project.evaluate()

		then:
		Task junitTask = project.tasks.findByName('junitPlatformTest')
		junitTask.args.containsAll('--reports-dir', new File(project.buildDir, 'test-results/junit-platform').getCanonicalFile().toString())
	}

	def "users can set buildDir to be a GString, and it will be converted to file"() {
		project.apply plugin: 'java'
		project.apply plugin: 'org.junit.platform.gradle.plugin'

		when:
		project.junitPlatform {
			reportsDir = "$project.buildDir/foo/bar/baz"
		}
		project.evaluate()

		then:
		Task junitTask = project.tasks.findByName('junitPlatformTest')
		junitTask.args.containsAll('--reports-dir', new File(project.buildDir, 'foo/bar/baz').getCanonicalFile().toString())
	}

	def "selectors can be specified"() {

		project.apply plugin: 'java'
		project.apply plugin: 'org.junit.platform.gradle.plugin'

		when:
		project.junitPlatform {
			selectors {
				uris 'u:foo', 'u:bar'
				uri 'u:qux'
				files 'foo.txt', 'bar.csv'
				file 'qux.json'
				directories 'foo/bar', 'bar/qux'
				directory 'qux/bar'
				packages 'com.acme.foo', 'com.acme.bar'
				aPackage 'com.example.app'
				classes 'com.acme.Foo', 'com.acme.Bar'
				aClass 'com.example.app.Application'
				methods 'com.acme.Foo#a', 'com.acme.Foo#b'
				method 'com.example.app.Application#run(java.lang.String[])'
				resources '/bar.csv', '/foo/input.json'
				resource '/com/acme/my.properties'
			}
		}
		project.evaluate()

		then:
		Task junitTask = project.tasks.findByName('junitPlatformTest')

		!junitTask.args.contains('--scan-class-path')
		!junitTask.args.contains(project.file('build/classes/main').absolutePath)

		junitTask.args.containsAll('-u', 'u:foo', '-u', 'u:bar', '-u', 'u:qux')
		junitTask.args.containsAll('-f', 'foo.txt', '-f', 'bar.csv', '-f', 'qux.json')
		junitTask.args.containsAll('-d', 'foo/bar', '-d', 'bar/qux', '-d', 'qux/bar')
		junitTask.args.containsAll('-p', 'com.acme.foo', '-p', 'com.acme.bar', '-p', 'com.example.app')
		junitTask.args.containsAll('-c', 'com.acme.Foo', '-c', 'com.acme.Bar', '-c', 'com.example.app.Application')
		junitTask.args.containsAll('-m', 'com.acme.Foo#a', '-m', 'com.acme.Foo#b', '-m', 'com.example.app.Application#run(java.lang.String[])')
		junitTask.args.containsAll('-r', '/bar.csv', '-r', '/foo/input.json', '-r', '/com/acme/my.properties')
	}

}
