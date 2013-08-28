/*
 * Copyright (c) 2013 University of Tartu
 */
package com.turn.jpmml.evaluator;

import java.util.*;


import org.dmg.pmml.*;

import com.turn.jpmml.manager.*;

public class ExpressionUtil {

	private ExpressionUtil(){
	}

	static
	public Object evaluate(FieldName name, EvaluationContext context){
		DerivedField derivedField = context.resolve(name);
		if(derivedField != null){
			return evaluate(derivedField, context);
		}

		return context.getParameter(name);
	}

	static
	public Object evaluate(DerivedField derivedField, EvaluationContext context){
		Object value = evaluate(derivedField.getExpression(), context);

		DataType dataType = derivedField.getDataType();
		if(dataType != null){
			value = ParameterUtil.cast(dataType, value);
		}

		return value;
	}

	static
	public Object evaluate(Expression expression, EvaluationContext context){

		if(expression instanceof Constant){
			return evaluateConstant((Constant)expression, context);
		} else

		if(expression instanceof FieldRef){
			return evaluateFieldRef((FieldRef)expression, context);
		} else

		if(expression instanceof NormContinuous){
			return evaluateNormContinuous((NormContinuous)expression, context);
		} else

		if(expression instanceof NormDiscrete){
			return evaluateNormDiscrete((NormDiscrete)expression, context);
		} else

		if(expression instanceof Discretize){
			return evaluateDiscretize((Discretize)expression, context);
		} else

		if(expression instanceof MapValues){
			return evaluateMapValues((MapValues)expression, context);
		} else

		if(expression instanceof Apply){
			return evaluateApply((Apply)expression, context);
		}

		throw new UnsupportedFeatureException(expression);
	}

	static
	public Object evaluateConstant(Constant constant, EvaluationContext context){
		String value = constant.getValue();

		DataType dataType = constant.getDataType();
		if(dataType == null){
			dataType = ParameterUtil.getConstantDataType(value);
		}

		return ParameterUtil.parse(dataType, value);
	}

	static
	public Object evaluateFieldRef(FieldRef fieldRef, EvaluationContext context){
		Object value = evaluate(fieldRef.getField(), context);
		if(value == null){
			return fieldRef.getMapMissingTo();
		}

		return value;
	}

	static
	public Object evaluateNormContinuous(NormContinuous normContinuous, EvaluationContext context){
		Number value = (Number)evaluate(normContinuous.getField(), context);
		if(value == null){
			return normContinuous.getMapMissingTo();
		}

		return NormalizationUtil.normalize(normContinuous, value.doubleValue());
	}

	static
	public Object evaluateNormDiscrete(NormDiscrete normDiscrete, EvaluationContext context){
		Object value = evaluate(normDiscrete.getField(), context);
		if(value == null){
			return normDiscrete.getMapMissingTo();
		}

		boolean equals = ParameterUtil.equals(value, normDiscrete.getValue());

		return Double.valueOf(equals ? 1.0 : 0.0);
	}

	static
	public Object evaluateDiscretize(Discretize discretize, EvaluationContext context){
		DataType dataType = discretize.getDataType();

		Object value = evaluate(discretize.getField(), context);
		if(value == null){
			return parseSafely(dataType, discretize.getMapMissingTo());
		}

		String result = DiscretizationUtil.discretize(discretize, value);

		return parseSafely(dataType, result);
	}

	static
	public Object evaluateMapValues(MapValues mapValues, EvaluationContext context){
		DataType dataType = mapValues.getDataType();

		Map<String, Object> values = new LinkedHashMap<String, Object>();

		List<FieldColumnPair> fieldColumnPairs = mapValues.getFieldColumnPairs();
		for(FieldColumnPair fieldColumnPair : fieldColumnPairs){
			Object value = evaluate(fieldColumnPair.getField(), context);
			if(value == null){
				return parseSafely(dataType, mapValues.getMapMissingTo());
			}

			values.put(fieldColumnPair.getColumn(), value);
		}

		String result = DiscretizationUtil.mapValue(mapValues, values);

		return parseSafely(dataType, result);
	}

	static
	public Object evaluateApply(Apply apply, EvaluationContext context){
		List<Object> values = new ArrayList<Object>();

		List<Expression> arguments = apply.getExpressions();
		for(Expression argument : arguments){
			Object value = evaluate(argument, context);

			values.add(value);
		}

		Object result = FunctionUtil.evaluate(apply.getFunction(), values);
		if(result == null){
			return apply.getMapMissingTo();
		}

		return result;
	}

	static
	private Object parseSafely(DataType dataType, String value){

		if(value != null){

			if(dataType != null){
				return ParameterUtil.parse(dataType, value);
			}
		}

		return value;
	}
}