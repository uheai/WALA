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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.analysis.strings.util.MethodReferences;
import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.MethodVariable;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;


public class MethodVariableProvider {
	
	private static Logger log = LoggerFactory.getLogger(MethodVariableProvider.class);

	/**
	 * mapping from reference to method variable
	 */
	private HashMap<MethodReference, MethodVariable> methods;

	private SSAOptions options;

	private static MethodVariable unknown = MethodVariable.makeUnknown();

	private static DexIRFactory factory = new DexIRFactory();

	public MethodVariableProvider(AnalysisCache cache, SSAOptions options) {
		if (options == null) {
			this.options = new SSAOptions();
		} else {
			this.options = options;
		}
		methods = new HashMap<MethodReference, MethodVariable>();
	}
	
	public MethodVariableProvider() {
		methods = new HashMap<MethodReference, MethodVariable>();
	}
	
	/**
	 * resolves methods.
	 * @see MethodVariable#resolveVariables()
	 * @return true iff any value changed
	 */
	public boolean resolveMethods() {
		return resolveHotspots(methods.values());
	}
	
	/**
	 * @see #resolveMethods()
	 * @param hotspots methods to analyze
	 * @return true iff any value changed
	 */
	public boolean resolveHotspots(Collection<MethodVariable> hotspots) {
		boolean changed = false;
		boolean localChanged = false;
		for (MethodVariable mVar : hotspots) {
			log.debug("solving" + mVar.getName());
			localChanged = mVar.resolveVariables();
			if (localChanged) {
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * create method variable for this method.
	 * Nothing will happen if m is abstract.
	 * @param m method to get variable for. Abstract methods will be ignored
	 */
	public void makeMethodVariable(IMethod m) {
		if (m.isAbstract()) {
			return; // nothing to do
		}
		assert options != null : "options is null";
		IR ir = factory.makeIR(m, Everywhere.EVERYWHERE, options);
		MethodVariable mVar = new MethodVariable(ir);
		methods.put(m.getReference(), mVar);
	}
	
	public void makeMethodVariable(IR ir) {
		if (ir.getMethod().isAbstract()) {
			return;
		}
		MethodVariable mVar = new MethodVariable(ir);
		methods.put(ir.getMethod().getReference(), mVar);
	}

	/**
	 * create method variables for all declared methods, which are not abstract
	 * @param c class
	 */
	public void makeMethodVariables(IClass c) {
		for (IMethod m : c.getDeclaredMethods()) {
			makeMethodVariable(m);
		}
	}
	
	/**
	 * create variables for methods. Abstract methods will be ignored.
	 * @param methods methods to create variables for
	 */
	public void makeMethodVariables(Collection<IMethod> methods) {
		for (IMethod m : methods) {
			makeMethodVariable(m);
		}
	}

	/**
	 * declare local as a local variable of ref
	 * @param ref reference of method. Must be in scope, i.e. MethodVariable has been created
	 * @param local local variable
	 * @return False iff local was already a local variable of ref
	 */
	public boolean addLocal(MethodReference ref, Local local) {
		return methods.get(ref).addLocal(local);
	}

	/**
	 * @param ref method
	 * @return method variable of ref
	 */
	public MethodVariable getVariable(MethodReference ref) {
		MethodVariable var = methods.get(ref);
		if (var == null) {
			return unknown;
		}
		return var;
	}
	
	/**
	 * For type T returns toString method of class where T is declard.
	 * If T has no toString method an unknown method will be returned, i.e one that returns anystring
	 * @see MethodVariable#makeUnknown()
	 * @param ref type T
	 * @return toString method
	 */
	public MethodVariable getToStringMethod(TypeReference ref) {
		return getVariable(MethodReferences.makeToStringRef(ref));
	}

	/**
	 * get all method variables
	 * @return all method variables
	 */
	public Collection<MethodVariable> getAllVariables() {
		return methods.values();
	}
	
	/**
	 * get all references of all methods in scope
	 * @return references
	 */
	public Collection<MethodReference> getAllMethodReferences() {
		return methods.keySet();
	}

	/**
	 * For a call of a method m(a,b,c) the parameters (a,b,c) may be added .
	 * @param ref reference of method
	 * @param params given parameters of a call, length has to match number of parameters
	 * @return False iff params was already added
	 */
	public boolean addParameters(MethodReference ref, FieldOrLocal[] params) {
		MethodVariable var = methods.get(ref);
		if (var == null) {
			// unknown method, ignore this parameters
			return false;
		} else {
			return var.addParameters(params);
		}
	}
	
	/**
	 * calculates total number of instructions (bytecode), e.g. sum of instructions of every method
	 * @return total number of bytecode instructions
	 */
	public int getTotalNumberOfInstructions() {
		int result = 0;
		for (MethodVariable method : methods.values()) {
			result += method.getNumberOfInstructions();
		}
		return result;
	}
	
	/**
	 * 
	 * @return number of methods
	 */
	public int size() {
		return methods.size();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Method Variables:\n\n");
		for (MethodVariable mVar : methods.values()) {
			TypeReference returnType = mVar.getIR().getMethod().getReturnType();
			if (mVar.getReturnValue() == null || !Util.hasRelevantType(returnType.getName())) {
				continue; //method is void
			}
			sb.append(mVar);
			sb.append("\n");
		}
		return sb.toString();
	}

}
