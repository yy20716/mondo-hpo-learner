package org.monarchinitiative.mondohpolearner.ncit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.monarchinitiative.mondohpolearner.common.ReportGenerator;

public class NCITReportGenerator extends ReportGenerator {

	public NCITReportGenerator() {
		super();
	}

	@Override
	protected List<String> sortClassCurieKeys() {
		List<String> classKeyList = new ArrayList<>();
		for (String classKey: classSubclassMap.asMap().keySet()) {
			if (classKey.contains("NCIT")) classKeyList.add(classKey);
		}

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
}
