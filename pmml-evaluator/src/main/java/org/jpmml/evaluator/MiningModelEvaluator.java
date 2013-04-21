package org.jpmml.evaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.dmg.pmml.DataField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningModel;
import org.dmg.pmml.MultipleModelMethodType;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Segment;
import org.jpmml.manager.MiningModelManager;
import org.jpmml.manager.UnsupportedFeatureException;

public class MiningModelEvaluator extends MiningModelManager implements Evaluator {

	private HashMap<Segment, Integer> segmentToId = new HashMap<Segment, Integer>();
	private Integer segmentMaxId = 0;

	public MiningModelEvaluator(PMML pmml) {
		super(pmml);
	}

	public MiningModelEvaluator(PMML pmml, MiningModel miningModel) {
		super(pmml, miningModel);
	}

	public MiningModelEvaluator(MiningModelManager parent){
		this(parent.getPmml(), parent.getModel());
	}

	// Work for vote. Each value is at least > 0.0. Return the key of the pair
	// that has the biggest value.
	private Object getBetterKey(Map<?, Double> map) {
		Double max = 0.0;
		Object result = null;
		for (Map.Entry<?, Double> e : map.entrySet()) {
			if (e.getValue() > max) {
				max = e.getValue();
				result = e.getKey();
			}
		}

		return result;
	}

	private String getId(Segment s) {
		if (!segmentToId.containsKey(s)) {
			segmentToId.put(s, segmentMaxId++);
		}

		return "segmentNumber" + segmentToId.get(s);

	}

	// Evaluate the parameters on the score card.
	public Object evaluate(Map<FieldName, ?> parameters) {
		String outputVariableName = null;
		List<FieldName> predictedFields = getPredictedFields();
		// Get the predicted field. If there is none, it is an error.
		if (predictedFields!=null && predictedFields.size()>0) {
			outputVariableName = predictedFields.get(0).getValue();
		}
		if (outputVariableName==null) {
			throw new EvaluationException("Predicted variable is not defined");
		}
		
		DataField outputField = getDataField(new FieldName(outputVariableName));		
		if (outputField==null || outputField.getDataType()==null) {
			throw new EvaluationException("Predicted variable [" +
					outputVariableName + "] does not have type defined");
		}
		switch (getFunctionType()) {
			case CLASSIFICATION:
				return evaluateClassification((Map<FieldName, Object>) parameters, outputField);
			case REGRESSION:
				return evaluateRegression((Map<FieldName, Object>) parameters, outputField);
			default:
				throw new UnsupportedOperationException();
		}
	}

	private Double getDouble(Object obj) {
		Double tmpRes = null;
		// FIXME: This is done because TreeModelEvaluator returns String even for regression.
		if (obj instanceof String) {
			tmpRes = Double.parseDouble((String) obj);
		}
		else {
			tmpRes = (Double) obj;
		}
		
		return tmpRes;

	}
	
	
	private Object evaluateRegression(Map<FieldName, Object> parameters, DataField outputField) {
		assert parameters != null;
		
		Object result = null;

		TreeMap<String, Object> results = new TreeMap<String, Object>();
		TreeMap<String, Double> idToWeight = new TreeMap<String, Double>();

		ModelEvaluatorFactory factory = new ModelEvaluatorFactory();

		for (Segment s : getSegment()) {
			if (PredicateUtil.evaluatePredicate(s.getPredicate(), parameters)) {
				Evaluator m = (Evaluator) factory.getModelManager(getPmml(), s.getModel());
				Object tmpObj = m.evaluate(parameters);

				if (getMultipleMethodModel() == MultipleModelMethodType.MODEL_CHAIN) {
					parameters.put(outputField.getName(), tmpObj);
				}
				if (tmpObj != null) {
					results.put(getId(s), tmpObj);
					idToWeight.put(getId(s), s.getWeight());
					if (getMultipleMethodModel() == MultipleModelMethodType.SELECT_FIRST) {
						result = results.get(getId(s));
						break;
					}
				}
			}
		}

		switch (getMultipleMethodModel()) {
		case SELECT_FIRST:
			// result already have the right value.
			break;
		case SELECT_ALL:
			throw new UnsupportedFeatureException();
		case MODEL_CHAIN:
			// This case is to be managed before.
			break;
		case AVERAGE:
			result = new Double(0.0);
			for (Map.Entry<String, Object> e : results.entrySet()) {
				result = (Double) result + getDouble(e.getValue());
			}
			if (results.size() != 0)
				result = (Double) result / results.size();
			break;
		case WEIGHTED_AVERAGE:
			Double sumWeight = 0.0;
			result = new Double(0.0);
			for (Map.Entry<String, Object> e : results.entrySet()) {
				result = (Double) result
						+ idToWeight.get(e.getKey())
						* getDouble(e.getValue());
				sumWeight += idToWeight.get(e.getKey());
			}
			if (sumWeight != 0.0)
				result = (Double) result / sumWeight;
			break;
		case MEDIAN:
			ArrayList<Double> list = new ArrayList<Double>(results.size());
			for (Map.Entry<String, Object> e : results.entrySet()) {
				list.add(getDouble(e.getValue()));
			}
			Collections.sort(list);
			result = list.get(list.size() / 2);
			break;
		default:
			throw new EvaluationException("The method " + getMultipleMethodModel().value()
					+ " is not compatible with the regression.");
		}

		return result;
	}

	private Object evaluateClassification(Map<FieldName, Object> parameters, DataField outputField) {
		assert parameters != null;
		Object result = null;

		TreeMap<String, Object> results = new TreeMap<String, Object>();
		TreeMap<String, Double> idToWeight = new TreeMap<String, Double>();

		ModelEvaluatorFactory factory = new ModelEvaluatorFactory();

		for (Segment s : getSegment()) {
			if (PredicateUtil.evaluatePredicate(s.getPredicate(), parameters)) {
				Evaluator m = (Evaluator) factory.getModelManager(getPmml(), s.getModel());
				Object tmpRes = m.evaluate(parameters);
				if (getMultipleMethodModel() == MultipleModelMethodType.MODEL_CHAIN) {
					parameters.put(outputField.getName(), tmpRes);
				}
				if (tmpRes != null) {
					results.put(getId(s), tmpRes);
					idToWeight.put(getId(s), s.getWeight());
				}
				if (getMultipleMethodModel() == MultipleModelMethodType.SELECT_FIRST) {
					result = results.get(getId(s));
					break;
				}
			}
		}

		switch (getMultipleMethodModel()) {
		case SELECT_FIRST:
			// result already have the right value.
			break;
		case MODEL_CHAIN:
			// This case is to be managed before.
			throw new UnsupportedFeatureException("Missing implementation.");
		case MAJORITY_VOTE:
			TreeMap<Object, Double> vote = new TreeMap<Object, Double>();
			for (Map.Entry<String, Object> e : results.entrySet()) {
				if (vote.containsKey(e.getValue())) {
					// We increment our number of vote.
					vote.put(e.getValue(), vote.get(e.getValue()) + 1.0);
				}
				else {
					vote.put(e.getValue(), 1.0);
				}
			}
			result = getBetterKey(vote);
			break;
		case WEIGHTED_MAJORITY_VOTE:
			TreeMap<Object, Double> vote2 = new TreeMap<Object, Double>();
			for (Map.Entry<String, Object> e : results.entrySet()) {
				if (vote2.containsKey(e.getValue())) {
					// We increment our counter wit the weight of the segment.
					vote2.put(e.getValue(), vote2.get(e.getValue())
							+ idToWeight.get(e.getKey()));
				}
				else {
					vote2.put(e.getValue(), idToWeight.get(e.getKey()));
				}
			}
			result = getBetterKey(vote2);
			break;
		case AVERAGE:
		case WEIGHTED_AVERAGE:
		case MEDIAN:
		case MAX:
			throw new UnsupportedFeatureException("Missing implementation.");
		default:
			throw new EvaluationException("The method " + getMultipleMethodModel().value()
					+ " is not compatible with the regression.");
		}

		return result;
	}

	public String getResultExplanation() {
		return "";
	}
}
