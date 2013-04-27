package org.jpmml.itest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.dmg.pmml.PMML;
import org.jpmml.manager.IOUtil;
import org.testng.annotations.Test;

@Test
public class RegressionModelTest extends BaseModelTest {
	@Test
	public void testSampleRegressionModel() throws Exception {
		PMML pmmlDoc = IOUtil.unmarshal(getClass().getResourceAsStream("/regression.xml"));
		Map<String, List<?>> variableToValues = new HashMap<String, List<?>>();
		//variableToValues.put("department", "engineering");
		variableToValues.put("age", Arrays.asList(22, 35, 45, 63, 33, 42, 51));
		variableToValues.put("salary", Arrays.asList(1600, 1000, 500));
		variableToValues.put("car_location", Arrays.asList("street", "carpark"));

		testModelEvaluation(pmmlDoc,
			SAMPLE_REGRESSION_MODEL_TEMPLATE,
			new SampleRegressionModel(),
			variableToValues, 
			20);
	}


	@Test
	public void testSampleRegressionModelNormalization() throws Exception {
		PMML pmmlDoc = IOUtil.unmarshal(getClass().getResourceAsStream("/regression2.xml"));
		Map<String, List<?>> variableToValues = new HashMap<String, List<?>>();
		variableToValues.put("age", Arrays.asList(22, 35, 45, 63, 33, 42, 51));
		variableToValues.put("salary", Arrays.asList(1600, 1000, 500));
		variableToValues.put("car_location", Arrays.asList("street", "carpark"));

		testModelEvaluation(pmmlDoc,
			SAMPLE_REGRESSION_MODEL_TEMPLATE,
			new SampleRegressionModelNormalization(),
			variableToValues, 
			20);
	}

	@Test
	public void testSampleClassification() throws Exception {
		PMML pmmlDoc = IOUtil.unmarshal(getClass().getResourceAsStream("/regressionClassification.xml"));
		Map<String, List<?>> variableToValues = new HashMap<String, List<?>>();
		variableToValues.put("age", Arrays.asList(22.0, 35.0, 45.0, 63.0, 33.0, 42.0, 51.0));
		variableToValues.put("work", Arrays.asList(10.0, 20.0, 30.0));
		variableToValues.put("sex", Arrays.asList("0", "1"));
		variableToValues.put("minority", Arrays.asList(0, 1));

		testModelEvaluation(pmmlDoc,
			SAMPLE_CLASSIFICATION_MODEL_TEMPLATE,
			new SampleClassificationModel(),
			variableToValues,
			20);
		
		
	}
	
	protected double getMissingVarProbability() {
		return 0.01;
	}
	
	static public class SampleClassificationModel implements ManualModelImplementation {
		public Object execute(Map<String, Object> nameToValue) {

			Double age = (Double) nameToValue.get("age"); 
			Double work = (Double) nameToValue.get("work"); 
			String sex = (String) nameToValue.get("sex");
			Integer minority = (Integer) nameToValue.get("minority");
			
			TreeMap<String, Double> categoryNameToValue = new TreeMap<String, Double>();
			double clerical = 0.0;
			double professional = 0.0;
			double trainee = 0.0;
			double skilled = 0.0;
			
			if (age == null || work == null) {
				return null;
			}
			else {
				clerical = 46.418 - 0.132 * age + work * 0.07867;
				professional = 51.169 - 0.302 * age + 0.155 * work;
				trainee = 25.478 - 0.154 * age + 0.266 * work;
			}
			
			if (sex != null && sex.equals("0")) {
				clerical += -20.525;
				professional += -21.389;
				trainee += -2.639; 
			}
			if (sex != null && sex.equals("1")) {
				clerical += 0.5;
				professional += 0.1;
				trainee += 0.8;
			}

			if (minority != null && minority.equals(0)) {
				clerical += -19.054;
				professional += -18.443;
				trainee += -19.821; 
			}
			if (minority != null && minority.equals(1)) {
				trainee += 0.2;
			}
			
			categoryNameToValue.put("clerical", clerical);
			categoryNameToValue.put("professional", professional);
			categoryNameToValue.put("trainee", trainee);
			categoryNameToValue.put("skilled", skilled);
			
//			System.out.println("clerical: " + clerical);
//			System.out.println("professional: " + professional);
//			System.out.println("trainee: " + trainee);
//			System.out.println("skilled: " + skilled);
			
			double sum = Math.exp(clerical) + Math.exp(trainee) + Math.exp(trainee) + 1;
			
			TreeMap<Double, String> scoreToCategory = new TreeMap<Double, String>();
			
			scoreToCategory.put(Math.exp(clerical) / sum, "clerical");
			scoreToCategory.put(Math.exp(professional) / sum, "professional");
			scoreToCategory.put(Math.exp(trainee) / sum, "trainee");
			scoreToCategory.put(Math.exp(skilled) / sum, "skilled");
			
			return scoreToCategory.lastEntry().getValue();
		}

		String resultExplanation = null;
		public String getResultExplanation() {
			return resultExplanation;
		}
	}
	
	static public class SampleRegressionModel implements ManualModelImplementation {

		public Object execute(Map<String, Object> nameToValue) {
			double score = 0.0; 

			String car_location = (String) nameToValue.get("car_location");
			Integer age = (Integer) nameToValue.get("age");
			Integer salary = (Integer) nameToValue.get("salary");

			if (age == null || salary == null) {
				return null;
			}
			else {
				score = 132.37 + 7.1  * age + 0.01 * salary;
			}
			if (car_location != null) {
				score +=  41.1 * (car_location.equals("carpark") ? 1 : 0)
						+ 325.03 * (car_location.equals("street") ? 1 : 0);
			}
			
			return score;
	}

		String resultExplanation = null;
		public String getResultExplanation() {
			return resultExplanation;
		}
	}

	static public class SampleRegressionModelNormalization extends SampleRegressionModel {
		@Override
		public Object execute(Map<String, Object> nameToValue) {
			Double result = (Double) super.execute(nameToValue);

			if (result == null)
				return null;

			return 1.0 / (1.0 + Math.exp(-result));
		}
	}
	
	static private final String SAMPLE_REGRESSION_MODEL_TEMPLATE = "" +
			"package org.jpmml.itest;\n" +
			"import java.util.Map;\n" +
			"import org.jpmml.itest.BaseModelTest.CompiledModel;\n" +
			"" +
			"#foreach($import in $imports) \n" + 
			"${import}\n" + 
			"#end\n" + 
			"\n" +
			"#foreach($constant in $constants) \n" + 
			"static private final ${constant}\n" + 
			"#end" + 
			"\n" +
			"public class ${className} implements CompiledModel {\n" + 
			"\n" + 
			"	public Object execute(Map<String, Object> nameToValue) {\n" + 
			"		try {\n" +
			"		Double number_of_claims = 0.0;\n" + 
			"		Integer age = (Integer)nameToValue.get(\"age\");\n" + 
			"		Integer salary = (Integer)nameToValue.get(\"salary\");\n" + 
			"		String car_location = (String)nameToValue.get(\"car_location\");\n" + 
			"		\n" + 
			"${modelCode}\n" + 
			"		return number_of_claims;\n" +
			"	} catch (Exception eee) { return null; }\n" +
			"	}\n" +
			"	String resultExplanation = null;\n" +
			" 	public String getResultExplanation() {\n" +
			" 		return resultExplanation;\n" +
			"	}\n" +
			"}\n";
	
	static private final String SAMPLE_CLASSIFICATION_MODEL_TEMPLATE =
					"package org.jpmml.itest;\n" +
					"import java.util.Map;\n" +
					"import org.jpmml.itest.BaseModelTest.CompiledModel;\n" +
					"" +
					"#foreach($import in $imports) \n" + 
					"${import}\n" + 
					"#end\n" + 
					"\n" +
					"#foreach($constant in $constants) \n" + 
					"static private final ${constant}\n" + 
					"#end" + 
					"\n" +
					"public class ${className} implements CompiledModel {\n" + 
					"\n" + 
					"	public Object execute(Map<String, Object> nameToValue) {\n" + 
					"		try {\n" +
					"		String jobcat = null;\n" + 
					"		Double work = (Double) nameToValue.get(\"work\");\n" + 
					"		Double age = (Double) nameToValue.get(\"age\");\n" + 
					"		String sex = (String) nameToValue.get(\"sex\");\n" +
					"		Integer minority = (Integer) nameToValue.get(\"minority\");\n" +
					"		\n" + 
					"${modelCode}\n" + 
					"		return jobcat;\n" +
					"	} catch (Exception eee) { /*System.out.println(eee.getMessage())*/; return null; }\n" +
					"	}\n" +
					"	String resultExplanation = null;\n" +
					" 	public String getResultExplanation() {\n" +
					" 		return resultExplanation;\n" +
					"	}\n" +
					"}\n"; 
}