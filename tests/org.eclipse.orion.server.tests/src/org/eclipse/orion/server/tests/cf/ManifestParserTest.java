/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.server.cf.manifest.v2.InputLine;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.Parser;
import org.eclipse.orion.server.cf.manifest.v2.ParserException;
import org.eclipse.orion.server.cf.manifest.v2.Preprocessor;
import org.eclipse.orion.server.cf.manifest.v2.Tokenizer;
import org.eclipse.orion.server.cf.manifest.v2.TokenizerException;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestParser;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestPreprocessor;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestTokenizer;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestTransformator;
import org.eclipse.orion.server.cf.manifest.v2.utils.SymbolResolver;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.junit.Test;

public class ManifestParserTest {

	private static String CORRECT_MANIFEST_LOCATION = "testData/manifestTest/correct"; //$NON-NLS-1$
	private static String INCORRECT_MANIFEST_LOCATION = "testData/manifestTest/incorrect"; //$NON-NLS-1$
	private static String INHERITANCE_MANIFEST_LOCATION = "testData/manifestTest/inheritance"; //$NON-NLS-1$
	private static String MANIFEST_LOCATION = "testData/manifestTest"; //$NON-NLS-1$

	@Test
	public void testParserAgainsCorrectManifests() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(CORRECT_MANIFEST_LOCATION);
		File manifestSource = new File(FileLocator.toFileURL(entry).getPath());

		File[] manifests = manifestSource.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".yml"); //$NON-NLS-1$
			}
		});

		for (File manifestFile : manifests) {
			InputStream inputStream = new FileInputStream(manifestFile);

			/* export the manifest and parse the output */
			String exported = exportManifest(inputStream);
			inputStream = new ByteArrayInputStream(exported.getBytes());
			String exportedOutput = exportManifest(inputStream);
			assertEquals(exported, exportedOutput);
		}
	}

	@Test
	public void testParserAgainsIncorrectManifests() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(INCORRECT_MANIFEST_LOCATION);
		File manifestSource = new File(FileLocator.toFileURL(entry).getPath());

		File[] manifests = manifestSource.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".yml"); //$NON-NLS-1$
			}
		});

		boolean failure = false;
		for (File manifestFile : manifests) {
			failure = false;
			InputStream inputStream = new FileInputStream(manifestFile);

			/* export the manifest */
			try {
				exportManifest(inputStream);
			} catch (IOException ex) {
				failure = true;
			} catch (TokenizerException ex) {
				failure = true;
			} catch (ParserException ex) {
				failure = true;
			}

			assertTrue(failure);
		}
	}

	@Test
	public void testQuotedManifestProperties() throws Exception {
		String manifestName = "quotedPropertiesManifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		File manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		InputStream inputStream = new FileInputStream(manifestFile);
		ManifestParseTree manifest = parse(inputStream);

		ManifestParseTree application = manifest.get("applications").get(0); //$NON-NLS-1$
		ManifestParseTree path = application.get("path"); //$NON-NLS-1$

		assertEquals(".", path.getValue()); //$NON-NLS-1$

		ManifestParseTree host = application.get("host"); //$NON-NLS-1$
		assertEquals("quoted-path-application", host.getValue()); //$NON-NLS-1$

		ManifestParseTree domain = application.get("domain"); //$NON-NLS-1$

		assertEquals("cloud-foundry-domain.org", domain.getValue()); //$NON-NLS-1$
	}

	@Test
	public void testTargetBaseManifestProperties() throws Exception {
		String manifestName = "targetBaseManifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		File manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		InputStream inputStream = new FileInputStream(manifestFile);
		ManifestParseTree manifest = parse(inputStream);
		SymbolResolver resolver = new SymbolResolver("api.sauron.mordor.com"); //$NON-NLS-1$
		resolver.apply(manifest);

		assertEquals("api.sauron.mordor.com", manifest.get("applications").get(0).get("domain").getValue()); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
		assertTrue(manifest.get("applications").get(0).get("url").getValue().endsWith(".api.sauron.mordor.com")); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
	}

	@Test
	public void testManifestGlobalProperties() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(INHERITANCE_MANIFEST_LOCATION);

		String manifestName = "01.yml"; //$NON-NLS-1$
		File manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		InputStream inputStream = new FileInputStream(manifestFile);
		ManifestParseTree manifest = parse(inputStream);

		ManifestTransformator transformator = new ManifestTransformator();
		transformator.apply(manifest);

		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		for (ManifestParseTree application : applications.getChildren())
			assertTrue(application.get("propertyA").getValue().equals("valueA") && application.get("propertyB").getValue().equals("valueB")); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$

		manifestName = "02.yml"; //$NON-NLS-1$
		manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		inputStream = new FileInputStream(manifestFile);
		manifest = parse(inputStream);

		transformator = new ManifestTransformator();
		transformator.apply(manifest);

		applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals("nativeA", applications.get(0).get("A").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("nativeB", applications.get(0).get("B").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("overriddenC", applications.get(0).get("C").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("valueD", applications.get(0).get("D").get("overriddenD").getValue()); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$

		assertEquals("overriddenA", applications.get(1).get("A").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("nativeB", applications.get(1).get("B").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("nativeC", applications.get(1).get("C").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("valueD", applications.get(1).get("D").get("overriddenD").getValue()); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$

		assertEquals("nativeA", applications.get(2).get("A").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("overriddenB", applications.get(2).get("B").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("nativeC", applications.get(2).get("C").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("valueD", applications.get(2).get("D").get("overriddenD").getValue()); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$

		assertEquals("overriddenA", applications.get(3).get("A").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("overriddenB", applications.get(3).get("B").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("overriddenC", applications.get(3).get("C").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("valueD", applications.get(3).get("D").get("nativeD").getValue()); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
	}

	private ManifestParseTree parse(InputStream inputStream) throws IOException, TokenizerException, ParserException {
		Preprocessor preprocessor = new ManifestPreprocessor();
		List<InputLine> contents = preprocessor.process(inputStream);
		Tokenizer tokenizer = new ManifestTokenizer(contents);

		Parser parser = new ManifestParser();
		return parser.parse(tokenizer);
	}

	private String exportManifest(InputStream inputStream) throws IOException, TokenizerException, ParserException {
		return parse(inputStream).toString();
	}
}
