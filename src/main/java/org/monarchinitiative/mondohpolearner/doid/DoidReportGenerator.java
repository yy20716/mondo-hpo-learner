package org.monarchinitiative.mondohpolearner.doid;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
public class DoidReportGenerator {
	private static final Logger logger = Logger.getLogger(DoidReportGenerator.class.getName());
	
	private Multimap<String, String> classSubclassMap;
	private Multimap<String, String> classEqEntityMap;
	private Map<String, Set<? extends EvaluatedDescription>> classResultMap;
	private OWLObjectRenderer renderer = StringRenderer.getRenderer();
	private DecimalFormat dfPercent = new DecimalFormat("0.00%");	
	private QueryExecutor mondoQueryExecutor = new QueryExecutor();
	private String newLineChar = System.getProperty("line.separator");
	/* key: doid class, value: rdfs:labels of doid classes */
	private Map<String, String> entityLabelMap = Maps.newHashMap();
	private String mondoVersion;
	private CurieUtil curieUtil;

	public DoidReportGenerator(Multimap<String, String> classSubclassMap, Multimap<String, String> classEqEntityMap) {
		super();
		this.classSubclassMap = classSubclassMap;
		this.classEqEntityMap = classEqEntityMap;

		/* Generates directories that will store report files */
		try {
			FileUtils.forceMkdir(new File(DoidProcessor.reportHomeDir));
			FileUtils.forceMkdir(new File(DoidProcessor.markdownDir));
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	public void setClassResultMap(Map<String, Set<? extends EvaluatedDescription>> classResultMap) {
		this.classResultMap = classResultMap;
	}

	public void setCurieUtil(CurieUtil curieUtil) {
		this.curieUtil = curieUtil;
	}

	/* pre-compute all labels (rdfs:label)for doid classes */ 
	public void precomputeLabels() {
		mondoQueryExecutor.loadModel("doid.owl");

		/* 2. extract class labels from doid.owl */
		ResultSet mondoLabelResultSet = mondoQueryExecutor.execute(Main.queryComputeEntityLabel);
		while (mondoLabelResultSet.hasNext()) {
			QuerySolution binding = mondoLabelResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}

		/* 3. extract class labels from hp.owl */
		ResultSet hpResultSet = QueryExecutor.executeOnce("hp.owl", Main.queryComputeEntityLabel);
		while (hpResultSet.hasNext()) {
			QuerySolution binding = hpResultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal label = (Literal) binding.get("label");
			entityLabelMap.put(classRsrc.toString(), label.toString());
		}
	}

	/* 
	 * Generates an index file so that users can easily navigate report files
	 * Each entry is connected to the report file. Each report file is for a single doid class.
	 */
	public void generateIndexFile() {
		try {
			String indexFilename = DoidProcessor.reportHomeDir + File.separator + "index.md";
			FileUtils.deleteQuietly(new File(indexFilename));
			FileWriter fw = new FileWriter(indexFilename,  false);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);

			List<String> classKeyList = new ArrayList<>(classSubclassMap.asMap().keySet());
			Collections.sort(classKeyList);

			StringBuilder sb = new StringBuilder();
			sb.append("Version: " + mondoVersion).append(newLineChar);
			sb.append("Format: DO classname (Label) [#subclasses][accuracy]").append(newLineChar).append(newLineChar);

			Pattern pattern = Pattern.compile("[.\\d]+%");
			for (String classCurie : classKeyList) {
				String classLabel = entityLabelMap.get(curieUtil.getIri(classCurie).get());
				if (classLabel == null) continue;

				File reportFile = new File(DoidProcessor.markdownDir + File.separator + classCurie.replace(":", "_") + ".md");
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

			/* Uncomment these if we want to use pandoc to convert markdown into html */
			/*
			String exepath = "pandoc";
			String exeargs = indexFilename + " -f markdown -t html -s -o report/index.html";
			Runtime r = Runtime.getRuntime();
			r.exec(exepath + " " + exeargs);
			 */
		} catch (Exception e) {
			logger.error(e.getMessage() + e.getStackTrace());
		}
	}

	private String generateAnnoClassListStr(Collection<String> classCurieCol) {
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

	private String annoIRIwithLink(String classCurie) {
		classCurie = classCurie.replace("ORPHA", "Orphanet");
		Optional<String> classCurieOpt = curieUtil.getIri(classCurie);
		if (classCurieOpt.isPresent()) {
			return new Link(classCurie, classCurieOpt.get()).toString();
		} else {
			return classCurie;
		}
	}

	/* render and write a report file for a mondo class */
	@SuppressWarnings("unchecked")
	public void render(String classCurie) {
		StringBuilder sb = new StringBuilder();
		logger.info("Rendering a report file...");
		try{
			String reportFilenameBody = classCurie.replace(":", "_");
			FileWriter fw = new FileWriter(DoidProcessor.markdownDir + File.separator + reportFilenameBody + ".md", false);
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
			Set<? extends EvaluatedDescription> resultSet = classResultMap.get(classCurie);
			if (resultSet == null ) {
				sb.append("No results from DL-Learner...").append(newLineChar);
			} else {
				for(EvaluatedDescription<? extends Score> ed : resultSet) {
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

			/*
			String exepath = "pandoc";
			String exeargs = "report/markdown/" + reportFilenameBody + ".md -f markdown -t html -s -o " + "report/html/" + reportFilenameBody + ".html";
			Runtime r = Runtime.getRuntime();
			r.exec(exepath + " " + exeargs);
			 */
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}
}