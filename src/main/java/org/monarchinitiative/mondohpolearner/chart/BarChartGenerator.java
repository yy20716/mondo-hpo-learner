package org.monarchinitiative.mondohpolearner.chart;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.monarchinitiative.mondohpolearner.doid.DoidProcessor;
import org.monarchinitiative.mondohpolearner.mondo.MondoProcessor;

public class BarChartGenerator {
	private static final Logger logger = Logger.getLogger(BarChartGenerator.class.getName());
	private List<Integer> mondoAccCountList = Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	private List<Integer> doidAccCountList = Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	public BarChartGenerator() {
	}

	public void run() {
		retrieveAccuracy(MondoProcessor.markdownDir, mondoAccCountList); 
		retrieveAccuracy(DoidProcessor.markdownDir, doidAccCountList);

		DefaultCategoryDataset mondoDataset = new DefaultCategoryDataset();
		DefaultCategoryDataset doidDataset = new DefaultCategoryDataset();
		
		for (int i = 0; i < 10; i++) {
			int beginIndex = i * 10;
			int endIndex = (i + 1) * 10;
			mondoDataset.addValue(mondoAccCountList.get(i), "Acc", beginIndex + "-" + endIndex + "%");
			doidDataset.addValue(doidAccCountList.get(i), "Acc", beginIndex + "-" + endIndex + "%");
		}

		JFreeChart mondoChart = ChartFactory.createBarChart("MonDO", "Acc. Range", "#Disease group", 
				mondoDataset, PlotOrientation.VERTICAL, true, true, false);
		JFreeChart doidChart = ChartFactory.createBarChart("Do", "Acc. Range", "#Disease group", 
				doidDataset, PlotOrientation.VERTICAL, true, true, false);
		
		mondoChart.removeLegend();
		doidChart.removeLegend();
		
		try {
			ChartUtils.saveChartAsPNG(new File("mondoaccbar.png"), mondoChart, 1000, 500);
			ChartUtils.saveChartAsPNG(new File("doidaccbar.png"), doidChart, 1000, 500);
		}  catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	private void retrieveAccuracy(String reportDir, List<Integer> accCountList) {
		List<File> files = (List<File>) FileUtils.listFiles(new File(reportDir), 
				TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
		Pattern pattern = Pattern.compile("[.\\d]+%");

		for (File file : files) {
			try {
				String reportString = FileUtils.readFileToString(file, Charset.defaultCharset());
				Matcher matcher = pattern.matcher(reportString);

				if (matcher.find()) {
					Double acc = Double.valueOf(matcher.group(0).replace("%", "")) - 0.01d;
					Integer accIndex = (int) (acc / 10.0d);
					accCountList.set(accIndex, accCountList.get(accIndex) + 1);
				} else {
					accCountList.set(0, accCountList.get(0) + 1);
				}
			} catch (Exception e) {
				logger.error(e.getMessage());
			}
		}
	}
}
