/*
 * Copyright (c) 2013 University of Tartu
 */
package com.turn.jpmml.evaluator;

import java.util.*;


import org.dmg.pmml.*;

import com.turn.jpmml.manager.*;

public class ModelManagerEvaluationContext extends EvaluationContext {

	private ModelManager<?> modelManager = null;


	public ModelManagerEvaluationContext(ModelManager<?> modelManager, Map<FieldName, ?> parameters){
		super(parameters);

		setModelManager(modelManager);
	}

	@Override
	public DerivedField resolve(FieldName name){
		ModelManager<?> modelManager = getModelManager();

		return modelManager.resolve(name);
	}

	@Override
	public ModelManagerEvaluationContext clone(){
		return (ModelManagerEvaluationContext)super.clone();
	}

	public ModelManager<?> getModelManager(){
		return this.modelManager;
	}

	private void setModelManager(ModelManager<?> modelManager){
		this.modelManager = modelManager;
	}
}