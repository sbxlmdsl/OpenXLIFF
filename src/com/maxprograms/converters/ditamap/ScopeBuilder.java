/*******************************************************************************
 * Copyright (c) 2003-2019 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters.ditamap;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Utils;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;

public class ScopeBuilder {

	private static final Logger LOGGER = System.getLogger(ScopeBuilder.class.getName());

	private SAXBuilder builder;
	private Scope currentScope;
	private Set<String> recursed;
	private static Hashtable<String, Set<String>> excludeTable;
	private static Hashtable<String, Set<String>> includeTable;
	private static boolean filterAttributes;

	public Scope buildScope(String inputFile, String ditavalFile, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {

		if (ditavalFile != null) {
			parseDitaVal(ditavalFile, catalog);
		}

		recursed = new TreeSet<>();

		builder = new SAXBuilder();
		builder.setEntityResolver(catalog);
		Document doc = builder.build(inputFile);
		Element root = doc.getRootElement();

		currentScope = new Scope(root.getAttributeValue("keyscope", ""));
		Scope rootScope = currentScope;
		recurse(root, inputFile);

		return rootScope;
	}

	private void recurse(Element e, String parentFile) {

		if (filterOut(e)) {
			return;
		}

		if (!e.getAttributeValue("format", "dita").startsWith("dita") && e.getAttributeValue("keys", "").equals("")) {
			return;
		}

		if (e.getAttributeValue("scope", "local").equals("external")) {
			return;
		}

		String scope = e.getAttributeValue("keyscope", "");
		if (!scope.equals("")) {
			Scope c = new Scope(scope);
			currentScope.addScope(c);
			currentScope = c;
		}

		String href = e.getAttributeValue("href", "");

		String path = "";
		if (!href.isEmpty()) {
			try {
				path = Utils.getAbsolutePath(parentFile, href);
				if (!recursed.contains(path)) {
					File f = new File(path);
					if (f.exists() && f.isFile()) {
						String format = e.getAttributeValue("format", "dita");
						if (format.startsWith("dita") && !DitaParser.ditaClass(e, "topic/image")) {
							Element root = builder.build(f).getRootElement();
							Scope old = currentScope;
							recursed.add(path);
							recurse(root, path);
							currentScope = old;
						}
					}
				}
			} catch (IOException | SAXException | ParserConfigurationException e1) {
				// skipped images and broken links
			}
		}

		String val = e.getAttributeValue("keys", "");

		if (!val.equals("")) {
			String[] keys = val.split("\\s");
			Element topicmeta = e.getChild("topicmeta");
			String keyref = e.getAttributeValue("keyref", "");
			for (int i = 0; i < keys.length; i++) {
				String key = keys[i];
				if (!keyref.equals("")) {
					if (!currentScope.addKey(new Key(key, keyref, parentFile))) {
						MessageFormat mf = new MessageFormat("Duplicate key definition: {0} -> {1}");
						LOGGER.log(Level.WARNING, mf.format(new Object[] { key, keyref }));
					}
				} else {
					if (href.equals("")) {
						if (!currentScope.addKey(new Key(key, parentFile, topicmeta, parentFile))) {
							MessageFormat mf = new MessageFormat("Duplicate key definition: {0}");
							LOGGER.log(Level.WARNING, mf.format(new Object[] { key }));
						}
					} else {
						try {
							path = Utils.getAbsolutePath(parentFile, href);
							File f = new File(path);
							if (f.exists() && f.isFile()
									&& !currentScope.addKey(new Key(key, path, topicmeta, parentFile))) {
								MessageFormat mf = new MessageFormat("Duplicate key definition: {0} -> {1}");
								LOGGER.log(Level.WARNING, mf.format(new Object[] { key, path }));
							}
						} catch (IOException ioe) {
							// ignore files that can't be parsed
						}
					}
				}
			}
		}
		List<Element> children = e.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			Scope old = currentScope;
			recurse(it.next(), parentFile);
			currentScope = old;
		}
	}

	private static void parseDitaVal(String ditaval, Catalog catalog)
			throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder bder = new SAXBuilder();
		bder.setEntityResolver(catalog);
		Document doc = bder.build(ditaval);
		Element root = doc.getRootElement();
		if (root.getName().equals("val")) {
			List<Element> props = root.getChildren("prop");
			Iterator<Element> it = props.iterator();
			excludeTable = new Hashtable<>();
			includeTable = new Hashtable<>();
			while (it.hasNext()) {
				Element prop = it.next();
				if (prop.getAttributeValue("action", "include").equals("exclude")) {
					String att = prop.getAttributeValue("att", "");
					String val = prop.getAttributeValue("val", "");
					if (!att.equals("")) {
						Set<String> set = excludeTable.get(att);
						if (set == null) {
							set = new HashSet<>();
						}
						if (!val.equals("")) {
							set.add(val);
						}
						excludeTable.put(att, set);
					}
				}
				if (prop.getAttributeValue("action", "include").equals("include")) {
					String att = prop.getAttributeValue("att", "");
					String val = prop.getAttributeValue("val", "");
					if (!att.equals("") && !val.equals("")) {
						Set<String> set = includeTable.get(att);
						if (set == null) {
							set = new HashSet<>();
						}
						set.add(val);
						includeTable.put(att, set);
					}
				}
			}
			filterAttributes = true;
		}
	}

	private static boolean filterOut(Element e) {
		if (filterAttributes) {
			List<Attribute> atts = e.getAttributes();
			Iterator<Attribute> it = atts.iterator();
			while (it.hasNext()) {
				Attribute a = it.next();
				if (excludeTable.containsKey(a.getName())) {
					Set<String> forbidden = excludeTable.get(a.getName());
					if (forbidden.isEmpty() && includeTable.containsKey(a.getName())) {
						Set<String> allowed = includeTable.get(a.getName());
						StringTokenizer tokenizer = new StringTokenizer(a.getValue());
						while (tokenizer.hasMoreTokens()) {
							String token = tokenizer.nextToken();
							if (allowed.contains(token)) {
								return false;
							}
						}
					}
					StringTokenizer tokenizer = new StringTokenizer(a.getValue());
					Vector<String> tokens = new Vector<>();
					while (tokenizer.hasMoreTokens()) {
						tokens.add(tokenizer.nextToken());
					}
					if (forbidden.containsAll(tokens)) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
