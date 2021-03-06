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
package com.maxprograms.xliff2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.lang.System.Logger.Level;
import java.lang.System.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;
import com.maxprograms.xml.PI;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLOutputter;
import com.maxprograms.xml.XMLUtils;

public class ToXliff2 {

	private static Element root2;
	private static int fileId;

	private ToXliff2() {
		// do not instantiate this class
		// use run method instead
	}

	public static Vector<String> run(File xliffFile, String catalog) {
		return run(xliffFile.getAbsolutePath(), xliffFile.getAbsolutePath(), catalog);
	}

	public static Vector<String> run(String sourceFile, String outputFile, String catalog) {
		Vector<String> result = new Vector<>();
		fileId = 1;
		try {
			SAXBuilder builder = new SAXBuilder();
			builder.setEntityResolver(new Catalog(catalog));
			Document doc = builder.build(sourceFile);
			Element root = doc.getRootElement();
			if (!root.getAttributeValue("version", "1.2").equals("1.2")) {
				result.add("1");
				result.add("Wrong XLIFF version.");
				return result;
			}
			Document xliff2 = new Document(null, "xliff", null, null);
			root2 = xliff2.getRootElement();
			recurse(root, root2);
			Indenter.indent(root2, 2);
			XMLOutputter outputter = new XMLOutputter();
			outputter.preserveSpace(true);
			try (FileOutputStream out = new FileOutputStream(new File(outputFile))) {
				out.write(XMLUtils.UTF8BOM);
				outputter.output(xliff2, out);
			}
			result.addElement("0");
		} catch (SAXException | IOException | ParserConfigurationException ex) {
			Logger logger = System.getLogger(ToXliff2.class.getName());
			logger.log(Level.ERROR, "Error generating XLIFF 2.0");
			result.add("1");
			result.add(ex.getMessage());
		}
		return result;
	}

	private static void recurse(Element source, Element target) {
		if (source.getName().equals("xliff")) {
			target.setAttribute("version", "2.0");
			target.setAttribute("xmlns", "urn:oasis:names:tc:xliff:document:2.0");
			target.setAttribute("xmlns:mda", "urn:oasis:names:tc:xliff:metadata:2.0");

			List<PI> encodings = source.getPI("encoding");
			if (!encodings.isEmpty()) {
				String encoding = encodings.get(0).getData();
				if (!encoding.equalsIgnoreCase(StandardCharsets.UTF_8.name())) {
					target.addContent(new PI("encoding", encoding));
				}
			}
		}

		if (source.getName().equals("file")) {
			root2.setAttribute("srcLang", source.getAttributeValue("source-language"));
			String tgtLang = source.getAttributeValue("target-language", "");
			if (!tgtLang.equals("")) {
				root2.setAttribute("trgLang", tgtLang);
			}
			Element file = new Element("file");
			file.setAttribute("id", "" + fileId++);
			file.setAttribute("original", source.getAttributeValue("original"));
			file.setAttribute("canResegment", "no");

			Element fileMetadata = new Element("mda:metadata");
			Element typeGroup = new Element("mda:metaGroup");
			typeGroup.setAttribute("category", "format");
			Element typeMeta = new Element("mda:meta");
			typeMeta.setAttribute("type", "datatype");
			typeMeta.addContent(source.getAttributeValue("datatype"));
			typeGroup.addContent(typeMeta);
			fileMetadata.addContent(typeGroup);

			Element header = source.getChild("header");
			if (header != null) {
				Element skl = header.getChild("skl");
				if (skl != null) {
					Element skeleton = new Element("skeleton");
					Element external = skl.getChild("external-file");
					if (external != null) {
						skeleton.setAttribute("href", external.getAttributeValue("href"));
					} else {
						Element internal = skl.getChild("internal-file");
						skeleton.setContent(internal.getContent());
					}
					file.addContent(skeleton);
				}

				List<Element> propGroups = header.getChildren("prop-group");
				for (int i = 0; i < propGroups.size(); i++) {
					Element sgroup = propGroups.get(i);
					Element tgroup = new Element("mda:metaGroup");
					tgroup.setAttribute("id", "" + i);
					tgroup.setAttribute("category", sgroup.getAttributeValue("name", ""));
					List<Element> props = sgroup.getChildren("prop");
					Iterator<Element> it = props.iterator();
					while (it.hasNext()) {
						Element prop = it.next();
						Element meta = new Element("mda:meta");
						meta.setAttribute("type", prop.getAttributeValue("prop-type", ""));
						meta.addContent(prop.getText());
						tgroup.addContent(meta);
					}
					if (!tgroup.getChildren().isEmpty()) {
						fileMetadata.addContent(tgroup);
					}
				}

				Element tool = header.getChild("tool");
				if (tool != null) {
					Element toolGroup = new Element("mda:metaGroup");
					toolGroup.setAttribute("category", "tool");
					String toolId = tool.getAttributeValue("tool-id", "");
					if (!toolId.isEmpty()) {
						Element meta = new Element("mda:meta");
						meta.setAttribute("type", "tool-id");
						meta.addContent(toolId);
						toolGroup.addContent(meta);
						if (toolId.equals("Fluenta")) {
							Element fluentaGroup = new Element("mda:metaGroup");
							fluentaGroup.setAttribute("category", "project-data");
							Element name = new Element("mda:meta");
							name.setAttribute("type", "project-name");
							name.addContent(source.getAttributeValue("product-name", ""));
							fluentaGroup.addContent(name);
							Element project = new Element("mda:meta");
							project.setAttribute("type", "project-id");
							project.addContent(source.getAttributeValue("product-version", ""));
							fluentaGroup.addContent(project);
							Element build = new Element("mda:meta");
							build.setAttribute("type", "build-number");
							build.addContent(source.getAttributeValue("build-num", ""));
							fluentaGroup.addContent(build);
							fileMetadata.addContent(fluentaGroup);
						}
					}
					String toolName = tool.getAttributeValue("tool-name", "");
					if (!toolName.isEmpty()) {
						Element meta = new Element("mda:meta");
						meta.setAttribute("type", "tool-name");
						meta.addContent(toolName);
						toolGroup.addContent(meta);
					}
					String toolCompany = tool.getAttributeValue("tool-company", "");
					if (!toolCompany.isEmpty()) {
						Element meta = new Element("mda:meta");
						meta.setAttribute("type", "tool-company");
						meta.addContent(toolCompany);
						toolGroup.addContent(meta);
					}
					String toolVersion = tool.getAttributeValue("tool-version", "");
					if (!toolVersion.isEmpty()) {
						Element meta = new Element("mda:meta");
						meta.setAttribute("type", "tool-version");
						meta.addContent(toolVersion);
						toolGroup.addContent(meta);
					}
					if (!toolGroup.getChildren().isEmpty()) {
						fileMetadata.addContent(toolGroup);
					}
				}
			}

			List<PI> pis = source.getPI();
			if (!pis.isEmpty()) {
				Element piGroup = new Element("mda:metaGroup");
				piGroup.setAttribute("category", "PI");
				Iterator<PI> pit = pis.iterator();
				while (pit.hasNext()) {
					PI pi = pit.next();
					Element meta = new Element("mda:meta");
					meta.setAttribute("type", pi.getTarget());
					meta.addContent(pi.getData());
					piGroup.addContent(meta);
				}
				if (!piGroup.getChildren().isEmpty()) {
					fileMetadata.addContent(piGroup);
				}
			}

			if (!fileMetadata.getChildren().isEmpty()) {
				file.addContent(fileMetadata);
			}

			target.addContent(file);
			target = file;
		}

		if (source.getName().equals("group")) {
			Element group = new Element("group");
			group.setAttribute("id", source.getAttributeValue("id"));
			String ts = source.getAttributeValue("ts");
			if (!ts.isEmpty()) {
				Element metadata = new Element("mda:metadata");
				Element metaGroup = new Element("mda:metaGroup");
				metadata.addContent(metaGroup);
				Element meta = new Element("mda:meta");
				meta.setAttribute("type", "ts");
				meta.addContent(ts);
				metaGroup.addContent(meta);
				group.addContent(metadata);
				if (source.getAttributeValue("xml:space").equals("preserve")) {
					Element space = new Element("mda:meta");
					space.setAttribute("type", "space");
					space.addContent("keep");
					metaGroup.addContent(space);
				}

			}
			target.addContent(group);
			target = group;
		}

		if (source.getName().equals("trans-unit")) {
			Element unit = new Element("unit");
			unit.setAttribute("id", source.getAttributeValue("id"));
			unit.setAttribute("canResegment", "no");
			if (source.getAttributeValue("translate", "yes").equals("no")) {
				unit.setAttribute("translate", "no");
			}
			target.addContent(unit);

			List<Element> notesList = source.getChildren("note");
			if (!notesList.isEmpty()) {
				Element notes = new Element("notes");
				unit.addContent(notes);
				for (int i = 0; i < notesList.size(); i++) {
					Element note = notesList.get(i);
					Element n = new Element("note");
					notes.addContent(n);
					n.addContent(note.getText());
				}
			}

			Element originalData = new Element("originalData");
			unit.addContent(originalData);

			Element src = source.getChild("source");
			List<Element> tags = src.getChildren("ph");
			Set<String> tagSet = new TreeSet<>();
			for (int i = 0; i < tags.size(); i++) {
				Element ph = tags.get(i);
				String id = "ph" + ph.getAttributeValue("id");
				if (!tagSet.contains(id)) {
					Element data = new Element("data");
					data.setAttribute("id", id);
					data.setContent(ph.getContent());
					originalData.addContent(data);
					tagSet.add("ph" + ph.getAttributeValue("id"));
				}
			}
			
			Element segment = new Element("segment");
			segment.setAttribute("id", source.getAttributeValue("id"));
			segment.setAttribute("canResegment", "no");
			unit.addContent(segment);
			if (source.getAttributeValue("approved", "no").equals("yes")) {
				segment.setAttribute("state", "final");
			}
			Element src2 = new Element("source");
			if (source.getAttributeValue("xml:space", "default").equals("preserve")) {
				src2.setAttribute("xml:space", "preserve");
			}
			segment.addContent(src2);
			List<XMLNode> srcContent = src.getContent();
			Iterator<XMLNode> nodes = srcContent.iterator();
			while (nodes.hasNext()) {
				XMLNode node = nodes.next();
				if (node.getNodeType() == XMLNode.TEXT_NODE) {
					src2.addContent(node);
				}
				if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
					Element tag = (Element) node;
					if (tag.getName().equals("ph")) {
						Element ph = new Element("ph");
						ph.setAttribute("id", "ph" + tag.getAttributeValue("id"));
						ph.setAttribute("dataRef", "ph" + tag.getAttributeValue("id"));
						src2.addContent(ph);
					}
					if (tag.getName().equals("mrk")) {
						Element mrk = new Element("mrk");
						mrk.setAttribute("id", "mrk" + tag.getAttributeValue("mid"));
						if (tag.getAttributeValue("mtype", "").equals("protected")) {
							mrk.setAttribute("translate", "no");
						}
						mrk.setAttribute("value", tag.getAttributeValue("ts", ""));
						mrk.setContent(tag.getContent());
						src2.addContent(mrk);
					}
				}
			}

			Element tgt = source.getChild("target");
			if (tgt != null) {
				Element tgt2 = new Element("target");
				if (source.getAttributeValue("xml:space", "default").equals("preserve")) {
					tgt2.setAttribute("xml:space", "preserve");
				}
				List<XMLNode> tgtContent = tgt.getContent();
				nodes = tgtContent.iterator();
				while (nodes.hasNext()) {
					XMLNode node = nodes.next();
					if (node.getNodeType() == XMLNode.TEXT_NODE) {
						tgt2.addContent(node);
					}
					if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
						Element tag = (Element) node;
						if (tag.getName().equals("ph")) {
							Element ph = new Element("ph");
							String id = "ph" + tag.getAttributeValue("id");
							ph.setAttribute("id", id);
							if (!tagSet.contains(id)) {
								Element data = new Element("data");
								data.setAttribute("id", id);
								data.setContent(ph.getContent());
								originalData.addContent(data);
								tagSet.add(id);
							}
							ph.setAttribute("dataRef", id);
							tgt2.addContent(ph);
						}
						if (tag.getName().equals("mrk")) {
							Element mrk = new Element("mrk");
							mrk.setAttribute("id", "mrk" + tag.getAttributeValue("mid"));
							if (tag.getAttributeValue("mtype", "").equals("protected")) {
								mrk.setAttribute("translate", "no");
							}
							mrk.setAttribute("value", tag.getAttributeValue("ts", ""));
							mrk.setContent(tag.getContent());
							tgt2.addContent(mrk);
						}
					}
				}
				segment.addContent(tgt2);
			}

			List<Element> matches = source.getChildren("alt-trans");
			if (!matches.isEmpty()) {
				root2.setAttribute("xmlns:mtc", "urn:oasis:names:tc:xliff:matches:2.0");
				Element mtc = new Element("mtc:matches");
				unit.getContent().add(0, mtc);
				for (int i = 0; i < matches.size(); i++) {
					Element smatch = matches.get(i);
					Element tmatch = new Element("mtc:match");
					tmatch.setAttribute("ref", "#" + source.getAttributeValue("id"));
					String matchQuality = smatch.getAttributeValue("match-quality");
					if (!matchQuality.isEmpty()) {
						try {
							tmatch.setAttribute("matchQuality", "" + Float.parseFloat(matchQuality));
						} catch (NumberFormatException nf) {
							// ignore
						}
					}
					String origin = smatch.getAttributeValue("origin");
					if (!origin.isEmpty()) {
						tmatch.setAttribute("origin", origin);
					}
					Element tsrc = new Element("source");
					List<XMLNode> content = smatch.getChild("source").getContent();
					Iterator<XMLNode> snodes = content.iterator();
					while (snodes.hasNext()) {
						XMLNode node = snodes.next();
						if (node.getNodeType() == XMLNode.TEXT_NODE) {
							tsrc.addContent(node);
						}
						if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
							Element tag = (Element) node;
							if (tag.getName().equals("ph")) {
								Element ph = new Element("ph");
								String id =  "ph" + tag.getAttributeValue("id");
								ph.setAttribute("id",id);
								if (!tagSet.contains(id)) {
									Element data = new Element("data");
									data.setAttribute("id", id);
									data.setContent(ph.getContent());
									originalData.addContent(data);
									tagSet.add(id);
								}
								tsrc.addContent(ph);
							}
							if (tag.getName().equals("mrk")) {
								Element mrk = new Element("mrk");
								mrk.setAttribute("id", "mrk" + tag.getAttributeValue("mid"));
								if (tag.getAttributeValue("mtype", "").equals("protected")) {
									mrk.setAttribute("translate", "no");
								}
								mrk.setAttribute("value", tag.getAttributeValue("ts", ""));
								mrk.setContent(tag.getContent());
								tsrc.addContent(mrk);
							}
						}
					}
					tmatch.addContent(tsrc);
					Element ttgt = new Element("target");
					content = smatch.getChild("target").getContent();
					snodes = content.iterator();
					while (snodes.hasNext()) {
						XMLNode node = snodes.next();
						if (node.getNodeType() == XMLNode.TEXT_NODE) {
							ttgt.addContent(node);
						}
						if (node.getNodeType() == XMLNode.ELEMENT_NODE) {
							Element tag = (Element) node;
							if (tag.getName().equals("ph")) {
								Element ph = new Element("ph");
								String id =  "ph" + tag.getAttributeValue("id");
								ph.setAttribute("id",id);
								if (!tagSet.contains(id)) {
									Element data = new Element("data");
									data.setAttribute("id", id);
									data.setContent(ph.getContent());
									originalData.addContent(data);
									tagSet.add(id);
								}
								ttgt.addContent(ph);
							}
							if (tag.getName().equals("mrk")) {
								Element mrk = new Element("mrk");
								mrk.setAttribute("id", "mrk" + tag.getAttributeValue("mid"));
								if (tag.getAttributeValue("mtype", "").equals("protected")) {
									mrk.setAttribute("translate", "no");
								}
								mrk.setAttribute("value", tag.getAttributeValue("ts", ""));
								mrk.setContent(tag.getContent());
								ttgt.addContent(mrk);
							}
						}
					}
					tmatch.addContent(ttgt);
					mtc.addContent(tmatch);
				}
			}
			if (originalData.getChildren("data").isEmpty()) {
				unit.removeChild(originalData);
			}
		}

		List<Element> children = source.getChildren();
		Iterator<Element> it = children.iterator();
		while (it.hasNext()) {
			recurse(it.next(), target);
		}
	}

}
