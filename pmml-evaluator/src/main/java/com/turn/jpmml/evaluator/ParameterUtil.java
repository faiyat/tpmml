/*
 * Copyright (c) 2012 University of Tartu
 */
package com.turn.tpmml.evaluator;

import java.util.*;


import org.dmg.pmml.*;

import com.turn.tpmml.manager.*;

public class ParameterUtil {

	private ParameterUtil(){
	}

	@SuppressWarnings (
		value = {"unused"}
	)
	static
	public Object prepare(DataField dataField, MiningField miningField, Object value){

		if(dataField == null){
			throw new EvaluationException();
		}

		outlierTreatment:
		if(isOutlier(dataField, value)){

			if(miningField == null){
				throw new EvaluationException();
			}

			OutlierTreatmentMethodType outlierTreatmentMethod = miningField.getOutlierTreatment();
			switch(outlierTreatmentMethod){
				case AS_IS:
					break;
				case AS_MISSING_VALUES:
					value = null;
					break;
				case AS_EXTREME_VALUES:
					{
						Double lowValue = miningField.getLowValue();
						Double highValue = miningField.getHighValue();

						if(lowValue == null || highValue == null){
							throw new EvaluationException();
						}

						DataType dataType = dataField.getDataType();

						if(compare(dataType, value, lowValue) < 0){
							value = lowValue;
						} else

						if(compare(dataType, value, highValue) > 0){
							value = highValue;
						}
					}
					break;
				default:
					throw new UnsupportedFeatureException(outlierTreatmentMethod);
			}
		}

		missingValueTreatment:
		if(isMissing(dataField, value)){

			if(miningField == null){
				return null;
			}

			value = miningField.getMissingValueReplacement();
			if(value != null){
				break missingValueTreatment;
			}

			return null;
		} // End if

		invalidValueTreatment:
		if(isInvalid(dataField, value)){

			if(miningField == null){
				throw new EvaluationException();
			}

			InvalidValueTreatmentMethodType invalidValueTreatmentMethod = miningField.getInvalidValueTreatment();
			switch(invalidValueTreatmentMethod){
				case RETURN_INVALID:
					throw new EvaluationException();
				case AS_IS:
					break invalidValueTreatment;
				case AS_MISSING:
					{
						value = miningField.getMissingValueReplacement();
						if(value != null){
							break invalidValueTreatment;
						}

						return null;
					}
				default:
					throw new UnsupportedFeatureException(invalidValueTreatmentMethod);
			}
		}

		return cast(dataField.getDataType(), value);
	}

	static
	private boolean isOutlier(DataField dataField, Object value){

		if(value == null){
			return false;
		}

		OpType opType = dataField.getOptype();
		switch(opType){
			case CONTINUOUS:
				{
					List<Double> range = new ArrayList<Double>();

					List<Interval> fieldIntervals = dataField.getIntervals();
					for(Interval fieldInterval : fieldIntervals){
						range.add(fieldInterval.getLeftMargin());
						range.add(fieldInterval.getRightMargin());
					}

					List<Value> fieldValues = dataField.getValues();
					for(Value fieldValue : fieldValues){
						Value.Property property = fieldValue.getProperty();

						switch(property){
							case VALID:
								range.add(toDouble(fieldValue.getValue()));
								break;
							default:
								break;
						}
					}

					if(range.isEmpty()){
						return false;
					}

					Double doubleValue = toDouble(value);

					Double minValue = Collections.min(range);
					if((doubleValue).compareTo(minValue) < 0){
						return true;
					}

					Double maxValue = Collections.max(range);
					if((doubleValue).compareTo(maxValue) > 0){
						return true;
					}
				}
				break;
			case CATEGORICAL:
			case ORDINAL:
				break;
			default:
				throw new UnsupportedFeatureException(opType);
		}

		return false;
	}

	static
	private boolean isMissing(DataField dataField, Object value){

		if(value == null){
			return true;
		}

		List<Value> fieldValues = dataField.getValues();
		for(Value fieldValue : fieldValues){
			Value.Property property = fieldValue.getProperty();

			switch(property){
				case MISSING:
					{
						boolean equals = equals(DataType.STRING, value, fieldValue.getValue());
						if(equals){
							return true;
						}
					}
					break;
				default:
					break;
			}
		}

		return false;
	}

	static
	private boolean isInvalid(DataField dataField, Object value){
		return !isValid(dataField, value);
	}

	@SuppressWarnings (
		value = "fallthrough"
	)
	static
	private boolean isValid(DataField dataField, Object value){
		DataType dataType = dataField.getDataType();

		// Speed up subsequent conversions
		value = cast(dataType, value);

		OpType opType = dataField.getOptype();
		switch(opType){
			case CONTINUOUS:
				{
					Double doubleValue = toDouble(value);

					int intervalCount = 0;

					List<Interval> fieldIntervals = dataField.getIntervals();
					for(Interval fieldInterval : fieldIntervals){
						intervalCount += 1;

						if(DiscretizationUtil.contains(fieldInterval, doubleValue)){
							return true;
						}
					}

					if(intervalCount > 0){
						return false;
					}
				}
				// Falls through
			case CATEGORICAL:
			case ORDINAL:
				{
					int validValueCount = 0;

					List<Value> fieldValues = dataField.getValues();
					for(Value fieldValue : fieldValues){
						Value.Property property = fieldValue.getProperty();

						switch(property){
							case VALID:
								{
									validValueCount += 1;

									boolean equals = equals(dataType, value, fieldValue.getValue());
									if(equals){
										return true;
									}
								}
								break;
							case INVALID:
								{
									boolean equals = equals(dataType, value, fieldValue.getValue());
									if(equals){
										return false;
									}
								}
								break;
							case MISSING:
								break;
							default:
								throw new UnsupportedFeatureException(property);
						}
					}

					if(validValueCount > 0){
						return false;
					}
				}
				break;
			default:
				throw new UnsupportedFeatureException(opType);
		}

		return true;
	}

	/**
	 * Checks the equality between different value representations.
	 *
	 * @param value The {@link #getDataType(Object) runtime data type representation} of the value.
	 * @param string The String representation of the value.
	 */
	static
	public boolean equals(Object value, String string){
		return equals(getDataType(value), value, string);
	}

	static
	private boolean equals(DataType dataType, Object left, Object right){
		return (cast(dataType, left)).equals(cast(dataType, right));
	}

	/**
	 * Calculates the order between the value and the reference value.
	 *
	 * @param value The {@link #getDataType(Object) runtime data type representation} of the value.
	 * @param string The String representation of the reference value.
	 *
	 * @see Comparable#compareTo(Object)
	 */
	static
	public int compare(Object value, String string){
		return compare(getDataType(value), value, string);
	}

	@SuppressWarnings (
		value = {"cast", "rawtypes", "unchecked"}
	)
	static
	private int compare(DataType dataType, Object left, Object right){
		return ((Comparable)cast(dataType, left)).compareTo((Comparable)cast(dataType, right));
	}

	static
	public Object parse(DataType dataType, String string){

		switch(dataType){
			case STRING:
				return string;
			case INTEGER:
				return new Integer(string);
			case FLOAT:
				return new Float(string);
			case DOUBLE:
				return new Double(string);
			default:
				break;
		}

		throw new UnsupportedFeatureException(dataType);
	}

	/**
	 * @return The data type of the value.
	 */
	static
	public DataType getDataType(Object value){

		if(value instanceof String){
			return DataType.STRING;
		} else

		if(value instanceof Integer){
			return DataType.INTEGER;
		} else

		if(value instanceof Float){
			return DataType.FLOAT;
		} else

		if(value instanceof Double){
			return DataType.DOUBLE;
		}

		throw new EvaluationException();
	}

	/**
	 * @return The least restrictive data type of the data types of two values
	 *
	 * @see #getResultDataType(DataType, DataType)
	 */
	static
	public DataType getResultDataType(Object left, Object right){
		return getResultDataType(getDataType(left), getDataType(right));
	}

	static
	public DataType getResultDataType(DataType left, DataType right){

		if((left).equals(right)){
			return left;
		} // End if

		DataType[] dataTypes = ParameterUtil.precedenceSequence;
		for(DataType dataType : dataTypes){

			if((dataType).equals(left) || (dataType).equals(right)){
				return dataType;
			}
		}

		throw new EvaluationException();
	}

	static
	public Object cast(DataType dataType, Object value){

		switch(dataType){
			case STRING:
				return toString(value);
			case INTEGER:
				return toInteger(value);
			case FLOAT:
				return toFloat(value);
			case DOUBLE:
				return toDouble(value);
			default:
				break;
		}

		throw new EvaluationException();
	}

	/**
	 * Converts the specified value to String data type.
	 *
	 * @see DataType#STRING
	 */
	static
	public String toString(Object value){

		if(value instanceof String){
			return (String)value;
		} else

		if(value instanceof Number){
			Number number = (Number)value;

			return number.toString();
		}

		throw new EvaluationException();
	}

	/**
	 * Converts the specified value to Integer data type.
	 *
	 * @see DataType#INTEGER
	 */
	static
	public Integer toInteger(Object value){

		if(value instanceof String){
			String string = (String)value;

			return Integer.valueOf(string);
		} else

		if(value instanceof Integer){
			return (Integer)value;
		} else

		if(value instanceof Number){
			Number number = (Number)value;

			return Integer.valueOf(number.intValue());
		}

		throw new EvaluationException();
	}

	/**
	 * Converts the specified value to Float data type.
	 *
	 * @see DataType#FLOAT
	 */
	static
	public Float toFloat(Object value){

		if(value instanceof String){
			String string = (String)value;

			return Float.valueOf(string);
		} else

		if(value instanceof Float){
			return (Float)value;
		} else

		if(value instanceof Number){
			Number number = (Number)value;

			return Float.valueOf(number.floatValue());
		}

		throw new EvaluationException();
	}

	/**
	 * Converts the specified value to Double data type.
	 *
	 * @see DataType#DOUBLE
	 */
	static
	public Double toDouble(Object value){

		if(value instanceof String){
			String string = (String)value;

			return Double.valueOf(string);
		} else

		if(value instanceof Double){
			return (Double)value;
		} else

		if(value instanceof Number){
			Number number = (Number)value;

			return Double.valueOf(number.doubleValue());
		}

		throw new EvaluationException();
	}

	static
	public DataType getConstantDataType(String string){

		try {
			if(string.indexOf('.') > -1){
				Float.parseFloat(string);

				return DataType.FLOAT;
			} else

			{
				Integer.parseInt(string);

				return DataType.INTEGER;
			}
		} catch(NumberFormatException nfe){
			return DataType.STRING;
		}
	}

	private static final DataType[] precedenceSequence = {DataType.STRING, DataType.DOUBLE, DataType.FLOAT, DataType.INTEGER};
}
