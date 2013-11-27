/*
 * Copyright (c) 2012 University of Tartu
 */
package com.turn.tpmml;

import java.util.*;

import javax.xml.bind.annotation.*;

@XmlTransient
abstract
public class SparseArray extends PMMLObject {

	abstract
	public List<Integer> getIndices();

	abstract
	public Integer getN();

	abstract
	public void setN(Integer n);
}
