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

import java.util.HashMap;

import com.ibm.wala.dalvik.analysis.strings.providers.Superprovider;
import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;


public class NodeVariable implements IVariable<NodeVariable> {

	/**
	 * mapping from field variables to variable numbers
	 */
	private HashMap<Integer, FieldOrLocal> vn2var;

	/**
	 * provides all variables and global equation system
	 */
	private Superprovider provider;

	private int id;

	private int order_num;

	public NodeVariable(Superprovider provider) {
		vn2var = new HashMap<Integer, FieldOrLocal>();
		this.provider = provider;
		id = -1;		
	}

	@Override
	public int getGraphNodeId() {
		return id;
	}

	@Override
	public void setGraphNodeId(int number) {
		id = number;
	}

	@Override
	public int getOrderNumber() {
		return order_num;
	}

	@Override
	public void setOrderNumber(int i) {
		order_num = i;
	}

	@Override
	public void copyState(NodeVariable v) {
		id = v.getGraphNodeId();
		order_num = v.getOrderNumber();
		vn2var = v.vn2var;
		provider = v.provider;
	}

	/**
	 * makes sure the equation this 'superset of' v if fullfilled. (meet)
	 * 
	 * @param v
	 *            right hand side of equation
	 * @return true iff something changed.
	 */
	public boolean meet(NodeVariable v) {
		boolean changed = false;
		boolean local = false;
		for (Integer r_vn : v.vn2var.keySet()) {
			FieldOrLocal rVar = v.vn2var.get(r_vn);
			if (vn2var.containsKey(r_vn)) {
				FieldOrLocal lVar = vn2var.get(r_vn);
				if (rVar.isAny) {
					local = lVar.setAnyString();
				} else {
					local = lVar.addValues(rVar);
				}
				if (local) {
					changed = true;
				}
			} else {
				vn2var.put(r_vn, rVar);
			}
		}
		return changed;
	}

	public boolean addMethod(FieldOrLocal var, MethodReference ref) {
		return var.addUnresolvedMethod(provider.getMethodVariable(ref));
	}

	public boolean addVar(FieldOrLocal lhs, FieldOrLocal rhs) {
		if (lhs == rhs) {
			return false;
		}
		return lhs.addUnresolvedVar(rhs);
	}


	/**
	 * adds equation var 'superset of' valuesOf(prefix) . valuesOf(suffix) to
	 * global equation system
	 * 
	 * @param var
	 *            lhs of equation
	 * @param prefix
	 *            variable holding all prefixes
	 * @param suffix
	 *            variable holding all suffixes
	 * @return true iff something changed
	 */
	public boolean addAppend(FieldOrLocal var, FieldOrLocal prefix, FieldOrLocal suffix) {
		provider.addPiNode(var, prefix);
		return var.addConcat(prefix, suffix);
	}

	public boolean addLoop(FieldOrLocal lhs, FieldOrLocal prefix, FieldOrLocal suffix) {
		return provider.addLoop(lhs, prefix, suffix);
	}

	/**
	 * getting variable representing parameter with index pos of method ref
	 * 
	 * @param ref
	 *            method
	 * @param pos
	 *            index of parameter (counting from 1)
	 * @return parameter variable
	 */
	public Local getParameterVar(MethodReference ref, int pos) {
		MethodVariable var = provider.getMethodVariable(ref);
		return var.getParameter(pos);
	}

	public FieldOrLocal getReturnValue(MethodReference ref, int vn) {
		// FieldOrLocal value = getOrCreateLocal(vn);
		// provider.addReturnValue(ref, value);
		FieldOrLocal returnValue = null;
		MethodVariable var = provider.getMethodVariable(ref);
		if (var.isUnknown() && vn >= 0) {
			FieldOrLocal temp = vn2var.get(vn); // maybe we know the result
												// without
			// knowning the method (like
			// StringBuilder.toString)
			if (temp != null) {
				// we found a value
				returnValue = temp;
			} else {
				returnValue = var.getReturnValue(); // method is unknown, so
													// return value is anystring
			}
		} else {
			returnValue = var.getReturnValue();
			FieldOrLocal vn_var = vn2var.get(vn);
			if (vn_var != null) {
				addVar(returnValue, vn_var);
			}

		}
		assert (returnValue != null) : "method is void";

		//registerVar(vn, returnValue);

		return returnValue;
	}

	/**
	 * adds local parameter to method
	 * 
	 * @param ref
	 *            method
	 * @param vn
	 *            variable number of local variable
	 * @param local
	 *            local variable
	 * @return true iff something changed
	 */
	public boolean addLocal(MethodReference ref, int vn, Local local) {
		registerVar(vn, local);
		return provider.addLocal(ref, local);
	}

	public boolean addParamaters(MethodReference ref, FieldOrLocal... params) {
		return provider.addParameters(ref, params);
	}

	/**
	 * sets var rhs_vn maps to to the set of unresolved variables of lhs, e.g.
	 * adds equation lhs 'superset of' rhs to global equation system If rhs is
	 * unknown lhs is set to anystring in order to be sound
	 * 
	 * @param lhs
	 *            lhs of equation
	 * @param rhs_vn
	 *            rhs of equation
	 * @return true iff something changed
	 */
	public boolean addVar(FieldOrLocal lhs, int rhs_vn) {
		FieldOrLocal rhs = vn2var.get(rhs_vn);
		if (rhs == null) {
			return lhs.setAnyString();
		} else {
			if (lhs == rhs) {
				return false;
			}
			return addVar(lhs, rhs);
		}
	}

	/**
	 * add equation lhs 'superset of' rhs
	 * @param lhs_vn variable number of lhs variable
	 * @param rhs rhs variable
	 * @return False iff this equation was alerady added.
	 */
	public boolean addVar(int lhs_vn, FieldOrLocal rhs) {
		FieldOrLocal lhs = getOrCreateVar(lhs_vn);
		return addVar(lhs,rhs);
	}

	/**
	 * 
	 * @param vn variable number 
	 * @return variable of vn
	 */
	public FieldOrLocal getVar(int vn) {
		return vn2var.get(vn);
	}

	public FieldVariable getField(FieldReference ref, int vn) {
		FieldVariable var = provider.getFieldVariable(ref);
		//Assert.assertNotNull("field var is null", var);
		//set unknown field to anystring
		if (var == null) {
			var = new FieldVariable("unknown field: " + ref.getName());
			var.setAnyString();
		}
		registerVar(vn, var);
		return var;
	}

	/**
	 * if there is a variable for vn it will be returned, otherwise a new local
	 * variable will be created and returned
	 * 
	 * @param vn
	 *            variable number
	 * @return variable corresponding to vn
	 */
	public FieldOrLocal getOrCreateVar(int vn) {
		FieldOrLocal var = vn2var.get(vn);
		if (var == null) {
			var = new Local(String.valueOf(vn));
			registerVar(vn, var);
		}
		return var;
	}
	
	/**
	 * Create an intent-variable with given parameters or get an already created one.s
	 * @param ref method where intent is created
	 * @param name name of the variable
	 * @param vn variable number
	 * @return intent-variable
	 */
	public IntentVar getOrCreateIntent(SSAInstruction inst, String name, int vn) {
		FieldOrLocal var = vn2var.get(vn);
		if (var == null) {
			var = new IntentVar(inst, name);
			registerVar(vn, var);
		} else if (!(var instanceof IntentVar)) {
			var = new IntentVar(inst, "phi");
			((IntentVar) var).setAllAnyString();
			vn2var.put(vn, var);
		} else if (inst.hasDef()) { //check whether its a new intent. Make a copy if necessary
			if (inst.getDef() != vn) {
				var = new IntentVar(inst, (IntentVar) var);
				//update mapping, so that vn maps to the new created intent, which has more information
				//Example: 18 = new Intent
				//		   19 = 18.putExtra
				//		   20 = 18.putExtra
				//		   startActivity(18)
				//If we would not change the mapping, 18 would not contain the extra values, but only 19 and 20, which
				//are not used
				vn2var.put(vn, var);
				registerVar(inst.getDef(), var);
			}
		}
		IntentVar intent = (IntentVar) var;
		provider.addIntent(intent);
		//intent.setMethod(ref);
		//Assert.true(var.isIntent());
		return (IntentVar) var;
	}
	
	/**
	 * create an intent-variable for given variable number or get an existing one
	 * @param vn variable number 
	 * @return intent-variable
	 */
	public IntentVar getOrCreateIntent(int vn, SSAInstruction inst) {
		return getOrCreateIntent(inst, "intent", vn);
	}

	/**
	 * adds mapping from vn to var. If there is already a mapping for vn, this
	 * method does nothing.
	 * 
	 * @param vn
	 *            value number of var
	 * @param var
	 *            value vn will be mapped to, must not be null
	 * @return true iff something changed
	 */
	public boolean registerVar(int vn, FieldOrLocal var) {
		if (var == null) {
			throw new IllegalArgumentException("var is null");
		}
		if (!vn2var.containsKey(vn)) {
			vn2var.put(vn, var);
			return true;
		}
		return false;
	}

	/**
	 * adds mapping from vn to var specified by ref. If this field is unknown or
	 * there already is a mapping for vn this method does nothing
	 * 
	 * @param vn
	 *            value number
	 * @param ref
	 *            field
	 * @return true iff someting changed
	 */
	public boolean registerField(int vn, FieldReference ref) {
		FieldOrLocal var = provider.getFieldVariable(ref);
		if (var == null) {
			return false;
		} else {
			return registerVar(vn, var);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(
				"Node-Variable: Graph node id = " + id + ", Order num = " + order_num + "\n");
		sb.append(Util.set2String("ssa variables", vn2var.keySet()));
		return sb.toString();
	}

}
