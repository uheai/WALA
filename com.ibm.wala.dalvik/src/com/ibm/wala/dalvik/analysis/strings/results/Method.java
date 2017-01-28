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
package com.ibm.wala.dalvik.analysis.strings.results;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.dalvik.analysis.strings.util.IntentSet;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.IntentVar;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.MethodVariable;
import com.ibm.wala.ssa.IR;


//Facade for method variable. Makes sure usesr can not access any public method of MethodVariable

/**
 * solved method variable
 * @author maik
 *
 */
public class Method {

	private MethodVariable var;

	private Variable ret;
	private Set<Variable> locals;
	private Variable[] parameters;

	public Method(MethodVariable var) {
		this.var = var;
	}

	// variables

	/**
	 * 
	 * @return return variable of this method
	 */
	public Variable getReturnVariable() {
		if (ret == null) {
			ret = new Variable(var.getReturnValue());
		}
		return ret;
	}

	/**
	 * returns a set containing all local variables of this method
	 * 
	 * @return local variables
	 */
	public Set<Variable> getLocalVariables() {
		if (locals == null) {
			locals = new HashSet<Variable>();
			for (Local l : var.getLocals()) {
				locals.add(new Variable(l));
			}
		}
		return locals;
	}

	/**
	 * returns array of variables, where i-th variable corresponds to the i-th parameter of this method.
	 * It contains all values this method got called with. Each variable stands alone, so if this method is
	 * replaceChar(char, int) and variable 0 contains 'a' and 'b' and variable 1 contains "1" and "2" (as strings)
	 * you can not determine whether 'a' belongs to "1" or to "2", e.g whether replaceChar('a',1) or
	 * replaceChar('b',2) was called. 
	 * Be aware that this are sets, so following call sequence would also be possible
	 * replaceChar('a',1)
	 * replaceChar('b',1)
	 * replaceChar('a',2)
	 * replaceChar('b',2)
	 * @return parameter variables of this method
	 */
	public Variable[] getParameters() {
		if (parameters == null) {
			Local[] params = var.getParamVars();
			if (params == null) {
				parameters = new Variable[0];
				return parameters;
			}
			parameters = new Variable[params.length];
			for (int i = 0; i < params.length; i++) {
				parameters[i] = new Variable(params[i]);
			}
		}
		return parameters;
	}
	
	public IntentSet getParameterIntent(int pos) throws IllegalArgumentException {
		if (pos >= getNumberOfParameters() || pos < 0) {
			throw new IllegalArgumentException("pos must be < " + getNumberOfParameters() + " and > 0");
		}
		Local root = var.getParameter(pos);
		Set<IntentVar> intents = new HashSet<IntentVar>(root.getUnresolved().size());
		for (FieldOrLocal v : root.getUnresolved()) {
			if ((!v.isField()) && (!v.isLocal())) {
				//if v is not a field and not a local it is an intent
				intents.add((IntentVar) v);
			} else { //debugging, should not be called
				throw new IllegalStateException("not an intent");
			}
		}
		return new IntentSet(intents);
	}
	
	/**
	 * 
	 * @return how many parameters does this method has
	 */
	public int getNumberOfParameters() {
		return var.getParamVars().length;
	}
	
	/**
	 * get ir of method
	 * @return
	 */
	public IR getIR() {
		return var.getIR();
	}
	
	/**
	 * 
	 * @return name of this variable
	 */
	public String getName() {
		return var.getName();
	}
	
	@Override
	public String toString() {
		return var.toString();
	}

}
