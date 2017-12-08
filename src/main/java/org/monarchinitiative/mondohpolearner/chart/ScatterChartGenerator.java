package org.monarchinitiative.mondohpolearner.chart;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.monarchinitiative.mondohpolearner.Main;
import org.monarchinitiative.mondohpolearner.doid.DoidProcessor;
import org.monarchinitiative.mondohpolearner.mondo.MondoProcessor;
import org.monarchinitiative.mondohpolearner.util.CurieMapGenerator;
import org.monarchinitiative.mondohpolearner.util.QueryExecutor;
import org.prefixcommons.CurieUtil;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class ScatterChartGenerator {
	private static final Logger logger = Logger.getLogger(ScatterChartGenerator.class.getName());

	private Multimap<String, String> classEqEntityMap;
	private CurieUtil curieUtil;

	public ScatterChartGenerator () {
		classEqEntityMap = Multimaps.newSetMultimap(new LinkedHashMap<>(), HashSet::new);
		CurieMapGenerator generator = new CurieMapGenerator();
		Map<String, String> curieMap = generator.generate();
		curieUtil = new CurieUtil(curieMap);
		
		try {
			FileUtils.forceMkdir(new File("chart"));
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	public void run() {
		computeClassMappings();

		XYSeriesCollection dataset = new XYSeriesCollection();
		XYSeries series = new XYSeries("Mondo, Do");

		for (String mondoClassCurie : classEqEntityMap.asMap().keySet()) {
			String mondoClassAcc = retrieveAccuracy(MondoProcessor.markdownDir, mondoClassCurie);
			if (mondoClassAcc == null) continue;
			mondoClassAcc = mondoClassAcc.replace("%", "");
			Double mondoClassAccVal = Double.valueOf(mondoClassAcc);

			Collection<String> doidClassCurieList = classEqEntityMap.get(mondoClassCurie);
			/* logger.info(mondoClassCurie + " : " + doidClassCurieList); */

			for (String doidClassCurie : doidClassCurieList) {
				String doidClassAcc = retrieveAccuracy(DoidProcessor.markdownDir, doidClassCurie);

				if (doidClassAcc == null) {
					series.add(mondoClassAccVal, (Double) 0.0);
				} else {
					doidClassAcc = doidClassAcc.replace("%", "");
					Double doidClassAccVal = Double.valueOf(doidClassAcc);
					series.add(mondoClassAccVal, doidClassAccVal);
				}
			}
		}

		dataset.addSeries(series);

		try {
			JFreeChart chart = ChartFactory.createScatterPlot("Accuracy comparison chart", "Accuracy in MonDO, %", "Accuracy in Do, %", dataset);
			XYPlot plot = (XYPlot)chart.getPlot();
			plot.setBackgroundPaint(new Color(255,228,196));			
			plot.setForegroundAlpha(0.75f);
			plot.setDomainPannable(true);
			plot.setRangePannable(true);
			chart.removeLegend();
			
			ChartUtils.saveChartAsPNG(new File("chart/mondodoidaccscatter.png"), chart, 500, 500);	
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	private String retrieveAccuracy(String markdownDir, String classCurie) {
		try {
			Pattern pattern = Pattern.compile("[.\\d]+%");
			File reportFile = new File(markdownDir + File.separator  + classCurie.replace(":", "_") + ".md");
			if (reportFile.exists() != true) return null;

			if (reportFile.length() > 0) {
				/* We open each report file and read the top entry's percentage so that we can show it with the link in the index file. */
				String reportString = FileUtils.readFileToString(reportFile, Charset.defaultCharset());
				Matcher matcher = pattern.matcher(reportString);

				if (matcher.find()) {
					return matcher.group(0);
				} else {
				}
				return null;
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return null;
	}

	private void computeClassMappings() {
		ResultSet resultSet = QueryExecutor.executeSelectOnce (MondoProcessor.inputOWLFile, Main.queryExtractEqClasses);

		while (resultSet.hasNext()) {
			QuerySolution binding = resultSet.nextSolution();
			Resource classRsrc = (Resource)binding.get("class");
			Literal source = (Literal) binding.get("source");

			String classRsrcIRI = curieUtil.getCurie(classRsrc.getURI()).get();
			String sourceIRI = source.getString();

			classEqEntityMap.put(classRsrcIRI, sourceIRI);
		}
	}
}
