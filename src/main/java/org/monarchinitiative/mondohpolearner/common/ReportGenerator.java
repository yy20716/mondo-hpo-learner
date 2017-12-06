package org.monarchinitiative.mondohpolearner.common;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.utils.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.Score;
import org.dllearner.core.StringRenderer;
import org.monarchinitiative.mondohpolearner.Main;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.prefixcommons.CurieUtil;
import org.semanticweb.owlapi.io.OWLObjectRenderer;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import net.steppschuh.markdowngenerator.link.Link;
import net.steppschuh.markdowngenerator.list.UnorderedList;
import net.steppschuh.markdowngenerator.text.emphasis.BoldText;
import net.steppschuh.markdowngenerator.text.heading.Heading;

@SuppressWarnings("rawtypes")
public class ReportGenerator {
	private static final Logger logger = Logger.getLogger(ReportGenerator.class.getName());

	public Multimap<String, String> classSubclassMap;
	public Multimap<String, String> classEqEntityMap;
	public CurieUtil curieUtil;
	public QueryExecutor queryExecutor;

	protected OWLObjectRenderer renderer = StringRenderer.getRenderer();
	protected DecimalFormat dfPercent = new DecimalFormat("0.00%");	
	protected String newLineChar = System.getProperty("line.separator");
	protected Map<String, String> entityLabelMap = Maps.newHashMap();
	protected String version;

	public ReportGenerator() {
		super();

		/* Generates directories that will store report files */
		try {
			FileUtils.forceMkdir(new File(Processor.reportHomeDir));
			FileUtils.forceMkdir(new File(Processor.markdownDir));
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/* pre-compute all labels (rdfs:label)for doid classes */ 
	public void precomputeLabels() {
		/* 1. extract versionIRIs from doid ontology, 
		 * e.g., <http://purl.obolibrary.org/obo/doid/releases/2017-11-10/doid.owl> 
		 * We assume that there will be only one version URI in datasets.*/
		ResultSet versionResultSet = queryExecutor.executeSelect(Main.queryExtractVersion);
		while (versionResultSet.hasNext()) {
			QuerySolution binding = versionResultSet.nextSolution();
			Resource versionRsrc = (Resource)binding.get("version");
			version = versionRsrc.getURI().split("/")[6];
		}

		/* 2. extract class labels from doid.owl */
		ResultSet labelResultSet = queryExecutor.executeSelect(Main.queryComputeEntityLabel);
		while (labelResultSet.hasNext()) {
			QuerySolution binding = labelResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}

		/* 3. extract class labels from hp.owl */
		ResultSet hpResultSet = QueryExecutor.executeSelectOnce("hp.owl", Main.queryComputeEntityLabel);
		while (hpResultSet.hasNext()) {
			QuerySolution binding = hpResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}
	}

	protected List<String> sortClassCurieKeys() {
		List<String> classKeyList = new ArrayList<>(classSubclassMap.asMap().keySet());
		Collections.sort(classKeyList, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				String o1NumOnly = o1.split(":")[1].trim().replaceAll("[^\\d.]", "");
				String o2NumOnly = o2.split(":")[1].trim().replaceAll("[^\\d.]", "");
				Integer o1Int = Integer.valueOf(o1NumOnly);
				Integer o2Int = Integer.valueOf(o2NumOnly);
				return o1Int.compareTo(o2Int);
			}
		});
		return classKeyList;
	}

	/* 
	 * Generates an index file so that users can easily navigate report files
	 * Each entry is connected to the report file. Each report file is for a single doid class.
	 */
	public void generateIndexFile() {
		try {
			String indexFilename = Processor.reportHomeDir + File.separator + "index.md";
			FileUtils.deleteQuietly(new File(indexFilename));
			FileWriter fw = new FileWriter(indexFilename,  false);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);
			List<String> classKeyList = sortClassCurieKeys();

			StringBuilder sb = new StringBuilder();
			sb.append("Version: " + version).append(newLineChar);
			sb.append("Format: classname (Label) [#subclasses][accuracy]").append(newLineChar).append(newLineChar);

			Pattern pattern = Pattern.compile("[.\\d]+%");
			for (String classCurie : classKeyList) {
				String classLabel = entityLabelMap.get(curieUtil.getIri(classCurie).get());
				if (classLabel == null) continue;

				File reportFile = new File(Processor.markdownDir + File.separator + classCurie.replace(":", "_") + ".md");
				if (reportFile.exists() != true) continue;

				if (reportFile.length() > 0) {
					/* We open each report file and read the top entry's percentage so that we can show it with the link in the index file. */
					String reportString = FileUtils.readFileToString(reportFile, Charset.defaultCharset());
					Matcher matcher = pattern.matcher(reportString);

					if (matcher.find()) {
						Link classLink =  new Link(classCurie, "markdown/" + classCurie.replace(":","_") + ".md");
						sb.append("1. " + classLink + " (" + classLabel  +") [" + classSubclassMap.get(classCurie).size() + "][" + matcher.group(0) + "]").append(newLineChar);
					} else {
						Link classLink =  new Link(classCurie, "markdown/" + classCurie.replace(":","_") + ".md");
						sb.append("1. " + classLink + " (" + classLabel  +") [" + classSubclassMap.get(classCurie).size() + "][no results]").append(newLineChar);
					}
				}
			}

			out.println(sb.toString());
			sb.setLength(0);

			out.flush();
			out.close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	protected String generateAnnoClassListStr(Collection<String> classCurieCol) {
		StringBuilder sb = new StringBuilder();
		List<String> classCurieList = new ArrayList<String>(classCurieCol);
		for (String classCurie: classCurieList) {
			if (curieUtil.getIri(classCurie).isPresent()) {
				String classLabel = entityLabelMap.get(curieUtil.getIri(classCurie).get());
				if (classLabel != null)
					sb.append(annoIRIwithLink(classCurie) + " (" + classLabel  +")").append(", ");
				else
					sb.append(annoIRIwithLink(classCurie)).append(", ");
			} else {
				sb.append(annoIRIwithLink(classCurie)).append(", ");
			}
		}
		return sb.toString();
	}

	protected String annoIRIwithLink(String classCurie) {
		classCurie = classCurie.replace("ORPHA", "Orphanet");
		Optional<String> classCurieOpt = curieUtil.getIri(classCurie);
		if (classCurieOpt.isPresent()) {
			return new Link(classCurie, classCurieOpt.get()).toString();
		} else {
			return classCurie;
		}
	}

	/* render and write a report file */
	@SuppressWarnings("unchecked")
	public void render(String classCurie, Set<? extends EvaluatedDescription> learnedExprs) {
		StringBuilder sb = new StringBuilder();
		logger.info("Rendering a report file...");
		try{
			String reportFilenameBody = classCurie.replace(":", "_");
			FileWriter fw = new FileWriter(Processor.markdownDir + File.separator + reportFilenameBody + ".md", false);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);

			sb.append(newLineChar).append(new Heading(annoIRIwithLink(classCurie), 3)).append(newLineChar);

			String classIRI = curieUtil.getIri(classCurie).get();
			String mondoClassLabel = entityLabelMap.get(classIRI);
			sb.append(new BoldText("Label:") + " " + mondoClassLabel).append(newLineChar).append(newLineChar);
			sb.append(new BoldText("Subclasses:") + " " + generateAnnoClassListStr(classSubclassMap.get(classCurie))).append(newLineChar).append(newLineChar);
			sb.append(new BoldText("Corr. equiv. classes:") + " " + generateAnnoClassListStr(classEqEntityMap.get(classCurie))).append(newLineChar).append(newLineChar);
			sb.append(new BoldText("Class expressions from DL-Learner:")).append(newLineChar).append(newLineChar);

			List<String> resultList = Lists.newArrayList();
			if (learnedExprs == null ) {
				sb.append("No results from DL-Learner...").append(newLineChar);
			} else {
				for(EvaluatedDescription<? extends Score> ed : learnedExprs) {
					OWLClassExpression description = ed.getDescription();
					Set<OWLClass> hpClasses = description.getClassesInSignature();
					String hpClassExprStr =  renderer.render(description);

					for (OWLClass hpClass: hpClasses) {
						String hpClassStr = hpClass.toString();
						String hpClassCurie = hpClassStr.replace("_", ":");
						Optional<String> hpClassIRIOpt = curieUtil.getIri(hpClassCurie);

						if (hpClassIRIOpt.isPresent()) {
							String hpClassLabel = entityLabelMap.get(hpClassIRIOpt.get());
							hpClassExprStr = hpClassExprStr.replace(hpClassStr, annoIRIwithLink(hpClassCurie) + " (" + hpClassLabel + ")");	
						} else {
							hpClassExprStr = hpClassExprStr.replace(hpClassStr, hpClassCurie);
						}
					}

					hpClassExprStr = hpClassExprStr + " " + dfPercent.format(ed.getAccuracy());
					resultList.add(hpClassExprStr);
				}	
			}

			sb.append(new UnorderedList<>(resultList));
			sb.append(newLineChar).append(newLineChar);

			out.println(sb.toString());
			sb.setLength(0);

			out.flush();
			out.close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
}