/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html.
 *
 * This file is a derivative of code released under the terms listed below.  
 *
 * 	Copyright (c) 2017,
 *      Maik Wiesner
 * 	 All rights reserved.
 *
 * 	Redistribution and use in source and binary forms, with or without
 * 	modification, are permitted provided that the following conditions are met:
 *
 * 	1. Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *  
 * 	2. Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 * 	3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package com.ibm.wala.dalvik.analysis.strings.providers;

import java.util.Collection;
import java.util.HashMap;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldVariable;
import com.ibm.wala.types.FieldReference;



public class FieldVariableProvider {

	/**
	 * mapping from reference to field variable
	 */
	private HashMap<FieldReference, FieldVariable> fields;

	public FieldVariableProvider() {
		fields = new HashMap<FieldReference, FieldVariable>();
	}

	/**
	 * declares this field anystring
	 * @param ref field to be set anystring
	 * @return true iff value changed. False means this variable was already anystring
	 */
	public boolean setAnyString(FieldReference ref) throws IllegalArgumentException {
		FieldVariable fv = fields.get(ref);
		if (fv == null) {
			throw new IllegalArgumentException("unknown field: " + ref);
		}
		return fv.setAnyString();
	}

	/**
	 * creates field variables for every field in class c
	 * 
	 * @param c
	 *            class
	 */
	public void makeFieldVariables(IClass c) {
		makeFieldVariables(c.getDeclaredInstanceFields());
		try {
			makeFieldVariables(c.getDeclaredStaticFields());
		} catch (NullPointerException e) {
			// class has no static fields
			// nothing to do
		}
	}

	/**
	 * get field variable 
	 * @param ref field 
	 * @return field variable for this field
	 */
	public FieldVariable getVariable(FieldReference ref) {
		return fields.get(ref);
	}

	/**
	 * adding statement lhs = rhs to global equation system. After all values of
	 * lhs and rhs have been calculated, we can resolve this dependency by
	 * adding all values of rhs to lhs
	 * 
	 * @param defined
	 *            field
	 * @param rhs
	 *            defining field
	 * @return true if something changed, false otherwise
	 */
	public boolean addUnresolved(FieldReference lhs, FieldReference rhs) {
		FieldVariable lVar = fields.get(lhs);
		FieldVariable rVar = fields.get(rhs);
		if (lVar == null || rVar == null) {
			throw new IllegalArgumentException("unknown field(s):\n\t" + lhs + "\n\t" + rhs);
		}
		return lVar.addUnresolvedVar(rVar);
	}

	/**
	 * Solving equation system for statements like field = something by simple
	 * fixpoint iteration. Be aware that equation system for each ir has to be
	 * solved already. Otherwise some values might get lost.
	 * 
	 * @return true iff any value has changed
	 */
	public boolean resolveUnresolvedFields() {
		return resolveHotspots(fields.values());
	}

	/**
	 * @see FieldVariableProvider#resolveUnresolvedFields()
	 * @param hotspots
	 *            fields to analyze
	 * @return true iff any value changed
	 */
	public boolean resolveHotspots(Collection<FieldVariable> hotspots) {
		if (hotspots == null) {
			return false; // nothing to do
		}
		boolean globalChanged = false;
		boolean changed = false;
		do {
			changed = false;
			for (FieldVariable fVar : hotspots) {
				changed = fVar.resolveUnresolved();
				if (changed) {
					globalChanged = true;
				}
			}
		} while (changed);
		return globalChanged;
	}

	/**
	 * creates field variables for relevant fields (see {@link Util#hasRelevantType(IField)} and adds them to scope
	 * @param f fields to add
	 */
	public void makeFieldVariables(Collection<IField> f) {
		for (IField field : f) {
			if (!Util.hasRelevantType(field)) {
				continue; // this field does not matter
			}
			String clazz = field.getDeclaringClass().getReference().getName().getClassName().toString();
			if (clazz.equals("R") || clazz.startsWith("R$")) {
				continue;
			}
			String name = field.getDeclaringClass() + "." + field.getName().toString();
			fields.put(field.getReference(), new FieldVariable(name));

		}
	}

	/**
	 * 
	 * @return number of fields
	 */
	public int size() {
		return fields.size();
	}

	/**
	 * 
	 * @return returns all field variables
	 */
	public Collection<FieldVariable> getAllFieldVariables() {
		return fields.values();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Field Variables\n");
		sb.append("\n");
		for (FieldReference ref : fields.keySet()) {
			sb.append(ref.getDeclaringClass().getName()).append(".").append(fields.get(ref).toString());
			sb.append("\n");
		}
		sb.append("\n");
		return sb.toString();
	}

}
