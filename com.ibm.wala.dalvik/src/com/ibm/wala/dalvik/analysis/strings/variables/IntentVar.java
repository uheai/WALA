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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;

/**
 * Represent an intent
 * 
 * @author maik
 *
 */
public class IntentVar extends FieldOrLocal {

	/**
	 * possible actions this intent can perform
	 */
	private FieldOrLocal action;

	/**
	 * possible datas this intent can contain
	 */
	private FieldOrLocal data;

	private FieldOrLocal cls;

	private FieldOrLocal extra;

	/**
	 * if Intent i = new Intent(Intent other) is used, and "other" is return
	 * value of another method or parameter of the method i is created in, we
	 * need to save "other" because we can resolve it only at the end (after we
	 * have seen every method). We also can have Intent i = phi(...). In this
	 * case every used variable of the phi instruction is handled like a init
	 * variable
	 */
	private Collection<FieldOrLocal> inits;

	private boolean startActivityCalled;

	// private MethodReference ref;

	/**
	 * instruction defining this intent
	 */
	private SSAInstruction inst;

	public IntentVar(SSAInstruction inst, String name) {
		super(name);
		this.inst = inst;
		startActivityCalled = false;
		action = new Local("action");
		data = new Local("data");
		cls = new Local("class");
		extra = new Local("extra");
	}

	/**
	 * creates a copy of var. This is needed when instructions like "putExtra"
	 * are called. Every call of such instructions returns a new ssa-variable,
	 * which is a just a copy of the original variable
	 * 
	 * @param inst
	 *            instruction which generated this intent
	 * @param var
	 *            original intent, which will be copied.
	 */
	public IntentVar(SSAInstruction inst, IntentVar var) {
		super(var.getName() + "'");
		this.inst = inst;
		startActivityCalled = false;
		action = new Local("action");
		var.action.applyCopy(action);
		data = new Local("data");
		var.data.applyCopy(data);
		cls = new Local("class");
		var.cls.applyCopy(cls);
		extra = new Local("extra");
		var.extra.applyCopy(extra);
	}

	/*
	 * public IntentVar(SSAInstruction inst, String name) { this(name);
	 * //this.ref = ref; }
	 */

	public boolean resolve() {
		boolean ret = resolveInits();
		action.resolveUnresolved();
		data.resolveUnresolved();
		cls.resolveUnresolved();
		extra.resolveUnresolved();
		makeClassNamesWalaLike();
		return ret;
	}

	private void makeClassNamesWalaLike() {
		Set<String> classNames = new HashSet<String>(cls.values.size());
		for (String s : cls.values) {
			if (s.startsWith("L")) {
				classNames.add(s); // s is already wala conform
			} else {
				String cls = "L" + s.replace('.', '/');
				classNames.add(cls);
			}
		}
		cls.values = classNames;
	}

	/**
	 * init is either a return value of a method or a parameter of a method.
	 * Those are just placeholders. The real parameters used are their
	 * unresolvedVariables. So for every unresolved variable of init, check
	 * whether it is an intent (we ignore other variables, like phis) and in
	 * case it is, add all values to this intent.
	 */
	private boolean resolveInits() {
		boolean ret = false;
		if (inits != null) {
			for (FieldOrLocal init : inits) {
				for (FieldOrLocal other : init.getUnresolved()) {
					if (other instanceof IntentVar) {
						IntentVar intent = (IntentVar) other;
						ret |= action.addUnresolvedVar(intent.action);
						ret |= data.addUnresolvedVar(intent.data);
						ret |= cls.addUnresolvedVar(intent.cls);
						ret |= extra.addUnresolvedVar(intent.extra);
					}
				}
			}
		}
		return ret;
	}

	/**
	 * If intent is created with Intent i = new Intent(Intent other) other can
	 * be set with this method. Every value of other will be added to this
	 * intent.
	 * 
	 * @param init
	 *            init variable (other)
	 */
	public void setInit(FieldOrLocal init) {
		inits = Collections.singleton(init);
	}

	/**
	 * Every variable in inits will be treated like an init variable.
	 * 
	 * @param inits
	 *            union of this variables is basis of this intent (like in
	 *            intent = phi(...))
	 */
	public void setInits(Collection<FieldOrLocal> inits) {
		this.inits = inits;
	}

	/*
	 * public boolean setMethod(MethodReference ref) { // method can be set only
	 * once if (ref == null) { this.ref = ref; return true; } return false; }
	 */

	/**
	 * add equation this.action 'superset of' action
	 * 
	 * @param action
	 *            right hand side of equation
	 * @return False iff this equation was already added
	 */
	public boolean addAction(FieldOrLocal action) {
		return this.action.addUnresolvedVar(action);
	}

	/**
	 * add equation this.data 'superset of' data
	 * 
	 * @param data
	 *            right hand side of equation
	 * @return False iff this equation was already added
	 */
	public boolean addData(FieldOrLocal data) {
		return this.data.addUnresolvedVar(data);
	}

	/**
	 * add equation this.extra 'superset of' extra
	 * 
	 * @param extra
	 *            right hand side of equation
	 * @return False iff this equation was already added
	 */
	public boolean addExtra(FieldOrLocal extra) {
		return this.extra.addUnresolvedVar(extra);
	}

	/**
	 * add equation this.class 'superset of' class
	 * 
	 * @param classVar
	 *            right hand side of equation
	 * @return False iff this equation was already added
	 */
	public boolean addClassVar(FieldOrLocal classVar) {
		return cls.addUnresolvedVar(classVar);
	}

	public FieldOrLocal getAction() {
		return action;
	}

	public FieldOrLocal getData() {
		return data;
	}

	public FieldOrLocal getClassVar() {
		return cls;
	}

	public FieldOrLocal getExtra() {
		return extra;
	}

	public SSAInstruction getDefiningInstruction() {
		return inst;
	}

	/**
	 * @return method where this intent was created, if known. May be null
	 */
	/*
	 * public MethodReference getMethod() { return ref; }
	 */

	/**
	 * declares that startActivity was called on this intent
	 */
	public void setStartActivityCalledTrue() {
		startActivityCalled = true;
	}

	/**
	 * sets all components of this intent, e.g action, data... to ANYSTRING
	 */
	public void setAllAnyString() {
		action.setAnyString();
		data.setAnyString();
		extra.setAnyString();
		cls.setAnyString();
	}

	@Override
	public boolean isField() {
		return false;
	}

	@Override
	public boolean isLocal() {
		return false;
	}

	public boolean startActivityCalled() {
		return startActivityCalled;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		assert inst != null;
		sb.append("Creating instruction ").append(inst.toString()).append("\n");
		sb.append("\t").append(action.toString()).append("\n");
		sb.append("\t").append(data.toString()).append("\n");
		sb.append("\t").append(cls.toString()).append("\n");
		sb.append("\t").append(extra.toString()).append("\n");

		if (startActivityCalled) {
			sb.append("\n\tstartActivtiy called\n");
		}
		return sb.toString();
	}

	/**
	 * Get total number of equations this intent has, e.g sum
	 * {x.getNumberOfEqautions} x = action, data, extra, class
	 * 
	 * @see FieldOrLocal#getNumberOfEquations()
	 * @return number of equations
	 */
	public int getNumberOfAllEquations() {
		int result = 0;
		result += action.getNumberOfEquations();
		result += data.getNumberOfEquations();
		result += extra.getNumberOfEquations();
		result += cls.getNumberOfEquations();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof IntentVar) {
			IntentVar var = (IntentVar) obj;
			if ((!this.inst.equals(var.inst))) {
				return false;
			}
			return (this.action.equals(var.action) && this.data.equals(var.data) && this.cls.equals(var.cls)
					&& this.extra.equals(var.extra));
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return action.hashCode() * data.hashCode() + cls.hashCode() * extra.hashCode();
	}

}
