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
package com.ibm.wala.dalvik.analysis.strings.variables;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.TypeReference;


public class MethodVariable {

	private String name;

	/**
	 * set of parameters, where one array represents one call. The length of an
	 * array is always paramNum.
	 */
	private Set<FieldOrLocal[]> params;

	private Local[] paramVars;

	private int paramNum;

	// private TypeReference[] paramTypes;

	private Local returnValue;

	private Set<Local> locals;

	private IR ir;

	public MethodVariable(IR ir) {
		this.ir = ir;
		IMethod method = ir.getMethod();
		name = method.getDeclaringClass().getName().toString() + "." + method.getName().toString();
		// do not count this parameter
		paramNum = method.isStatic() ? ir.getNumberOfParameters() : ir.getNumberOfParameters() - 1;
		// paramNum = ir.getNumberOfParameters();
		locals = new HashSet<Local>();
		if (!method.getReturnType().getName().equals(TypeReference.VoidName)) {
			returnValue = new Local();
		}
		if (paramNum > 0) {
			params = new HashSet<FieldOrLocal[]>();
			paramVars = new Local[paramNum];
			for (int i = 0; i < paramNum; i++) {
				paramVars[i] = new Local();
			}
		}
	}

	private MethodVariable() {
		// unknown methods return anystring
		name = "unknown method";
		returnValue = new Local();
		returnValue.setAnyString();
	}

	/**
	 * 
	 * @return is this method unknown
	 */
	public boolean isUnknown() {
		return false;
	}

	/**
	 * add parameters (a,b,c) of a call of m(a,b,c) where m is this method
	 * @param params parameters, number has to be right
	 * @return False iff this parameters were already added
	 */
	public boolean addParameters(FieldOrLocal... params) {
		if (params.length != paramNum) {
			throw new IllegalArgumentException(
					"this method requires " + paramNum + "arguments, but got " + params.length);
		}
		if (this.params.add(params)) {
			for (int i = 0; i < paramNum; i++) {
				paramVars[i].addUnresolvedVar(params[i]);
			}
			return true;
		}
		return false;

	}

	/**
	 * add equation returnValue 'superset of' value
	 * @param value variable
	 * @return False iff this equation was already added
	 */
	public boolean addReturnValue(FieldOrLocal value) {
		return returnValue.addUnresolvedVar(value);
	}

	/**
	 * declares local as a local variable of this method
	 * 
	 * @param local
	 *            local variable of this method, must no be null
	 * @return true iff something changed
	 */
	public boolean addLocal(Local local) {
		if (local == null) {
			throw new IllegalArgumentException("null value");
		}
		return locals.add(local);
	}

	public IR getIR() {
		return ir;
	}

	/**
	 * @return variable representing return value
	 */
	public Local getReturnValue() {
		return returnValue;
	}

	/**
	 * get variable representing ith parameter of this methods
	 * 
	 * @param i
	 *            index of parameter (counting from one)
	 * @return
	 */
	public Local getParameter(int i) {
		if (i < 0 || i > paramVars.length) {
			throw new IllegalArgumentException(
					"method has " + paramVars.length + " parameters. Invalid argument: " + i);
		}
		return paramVars[i - 1];
	}

	/**
	 * 
	 * @return local variables
	 */
	public Set<Local> getLocals() {
		return locals;
	}

	/**
	 * 
	 * @return parameter variables
	 */
	public Local[] getParamVars() {
		return paramVars;
	}

	public String getName() {
		return name;
	}

	/**
	 * 
	 * @return number of bytecode instructions this method has
	 */
	public int getNumberOfInstructions() {
		return ((DexIMethod) ir.getMethod()).getDexInstructions().length;
	}

	/**
	 * resolve all variables this method has, e.g return value, local variables
	 * and parameter variables
	 * 
	 * @return true iff any variable changed
	 */
	public boolean resolveVariables() {
		boolean changed = false;
		if (returnValue != null) {
			if (returnValue.resolveUnresolved()) {
				changed = true;
			}
		}
		// resolve locals
		for (FieldOrLocal local : locals) {
			if (local.resolveUnresolved()) {
				changed = true;
			}
		}
		// resolve parameters
		if (paramVars != null) {
			for (FieldOrLocal param : paramVars) {
				param.resolveUnresolved();
			}
		}
		return changed;
	}

	/**
	 * get number of equations this mehthod has, e.g. return value, local
	 * variables, parameters
	 * 
	 * @return number of equations
	 */
	public int getNumberOfEquations() {
		int result = 0;
		// return value
		if (returnValue != null) {
			result += returnValue.getNumberOfEquations();
		}
		// local variables
		for (FieldOrLocal local : locals) {
			result += local.getNumberOfEquations();
		}
		// parameters
		if (paramVars != null) {
			for (FieldOrLocal param : paramVars) {
				result += param.getNumberOfEquations();
			}
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(name);
		sb.append("(");
		if (paramNum > 0) {
			sb.append(paramNum);
		}
		sb.append(")");
		if (returnValue != null) {
			sb.append("\n\t" + Util.set2String("Return values", returnValue.getValues()));
		}
		if (!locals.isEmpty()) {
			sb.append("\n\tlocals:");
			for (Local local : locals) {
				sb.append("\n\t\t" + local);
			}
		}
		sb.append("\n");
		return sb.toString();
	}

	/**
	 * set this method unknown, i.e we know nothing about this method
	 * @return False iff this method was already unknown
	 */
	public static MethodVariable makeUnknown() {
		return new UnknownMethod();
	}

	private static class UnknownMethod extends MethodVariable {

		public UnknownMethod() {
			super();
		}

		@Override
		public boolean isUnknown() {
			return true;
		}

		// -------------------
		// overwrite methods to avoid nullpointer exception

		@Override
		public boolean resolveVariables() {
			return false; // nothing to do
		}

		@Override
		public boolean addLocal(Local local) {
			return false;
		}

		@Override
		public boolean addParameters(FieldOrLocal... params) {
			return false;
		}

		@Override
		public Local getParameter(int i) {
			throw new IllegalArgumentException();
		}

	}

}
