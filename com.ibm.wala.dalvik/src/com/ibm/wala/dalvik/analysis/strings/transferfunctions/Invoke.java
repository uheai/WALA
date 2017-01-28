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
package com.ibm.wala.dalvik.analysis.strings.transferfunctions;

import com.ibm.wala.dalvik.analysis.strings.util.Type;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;



/**
 * Represents method call. To add interpretation of a method extend this class and overwrite {@link #evaluate(NodeVariable, NodeVariable)}
 * @author maik
 *
 */
public class Invoke extends UnaryOperator<NodeVariable> {

	protected Object[] params;

	protected SSAInvokeInstruction inst;

	protected Type[] types;
	
	public Invoke(SSAInvokeInstruction inst, IR ir, AnalysisCache cache) {
		this.inst = inst;
		int paramNum = inst.getNumberOfParameters();
		boolean hasThis = false; // indicates whether there is an implicit this
									// parameter
		if (!inst.isStatic()) {
			paramNum--; // do not count this parameter
			hasThis = true;
		}
		if (paramNum > 0) {
			params = new Object[paramNum];
			types = new Type[paramNum];
			// check for each parameter, if its (string)constant
			SymbolTable table = ir.getSymbolTable();
			int index = 0;
			for (int i = hasThis ? 1 : 0; i < inst.getNumberOfParameters(); i++) {
				if (hasThis) {
					index = i - 1;
				} else {
					index = i;
				}
				int use = inst.getUse(i);
				if (table.isConstant(use)) {
					Local local = new Local("const");
					params[index] = local;
					types[index] = Type.CONST;
					if (table.isStringConstant(use)) {
						local.addValue(table.getStringValue(use));
					} else if (table.isNumberConstant(use)) {
						local.addValue(String.valueOf(table.getDoubleValue(use)));
					} else {
						local.addValue("unknown constant type");
					}
				} else if (table.isParameter(use)) {
					types[index] = Type.PARAM;
					int parPos = new Integer(1);
					int[] paramValueNumbers = table.getParameterValueNumbers();
					for (int j = 0; j < paramValueNumbers.length; j++) {
						if (paramValueNumbers[j] == use) {
							parPos = ir.getMethod().isStatic() ? j + 1 : j;
							break;
						}
					}
					params[index] = Pair.make(ir.getMethod().getReference(), parPos);
				} else {
					SSAInstruction def = cache.getDefUse(ir).getDef(use);
					if (def != null && def instanceof SSAAbstractInvokeInstruction) {
						types[index] = Type.METHOD;
						params[index] = (SSAAbstractInvokeInstruction) def;

					} else {
						params[index] = use;
						types[index] = Type.OTHER;
					}
				}
			}
		}

	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
  		if (params == null) {
			return NOT_CHANGED_AND_FIXED; // nothing to do, there are no
											// parameters
		}
		FieldOrLocal[] locals = new FieldOrLocal[params.length];
		for (int i = 0; i < params.length; i++) {
			switch (types[i]) {
			case CONST:
				locals[i] = (Local) params[i];
				break;
			case PARAM:
				//locals[i] = lhs.getParameterVar(inst.getDeclaredTarget(), (Integer) params[i]);
				Pair<MethodReference, Integer> p = (Pair<MethodReference, Integer>) params[i];
				if (p.snd == 0) {
					//this pointer is parameter
					locals[i] = new Local();
					locals[i].setAnyString();
					break;
				}
				locals[i] = lhs.getParameterVar(p.fst, p.snd);
				break;
			case OTHER: // local or field variable
				locals[i] = lhs.getVar((Integer) params[i]);
				if (locals[i] == null) {
					locals[i] = new Local();
					locals[i].setAnyString();
				}
				break;
			case METHOD:
				SSAAbstractInvokeInstruction def = (SSAAbstractInvokeInstruction) params[i];
				//locals[i] = lhs.getParameterVar(def.getDeclaredTarget(), def.getDef());
				locals[i] = lhs.getReturnValue(def.getDeclaredTarget(), def.getDef());
				break;
			case ANY:
			case FIELD:
			default:
				Assertions.UNREACHABLE("unknown type: " + types[i]);
			}
		}
		if (lhs.addParamaters(inst.getDeclaredTarget(), locals)) {
			return CHANGED;
		} else {
			return NOT_CHANGED;
		}

	}

	@Override
	public int hashCode() {
		return 1337;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "invoke: " + inst;
	}

}
