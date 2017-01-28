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
package com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods;

import com.ibm.wala.dalvik.analysis.strings.util.MethodReferences;
import com.ibm.wala.dalvik.analysis.strings.util.Type;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.dalvik.analysis.typeInference.DalvikTypeInference;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

/**
 * String valueOf
 * @author maik
 *
 */
public class String_valueOf extends UnaryOperator<NodeVariable> {

	/**
	 * Object containing parameter
	 */
	private Object obj;

	/**
	 * type of object
	 */
	private Type type;

	/**
	 * value number of variable in which result is stored.
	 */
	private int def;

	private DalvikTypeInference inf;

	public String_valueOf(SSAInvokeInstruction inst, IR ir, AnalysisCache cache) {
		// Assert: inst invokes String.valueOf(Object)
		int use = inst.getUse(0); // no implicit this pointer, only one
									// parameter
		def = inst.getDef();
		SymbolTable table = ir.getSymbolTable();
		if (table.isConstant(use)) {
			Local local = new Local();
			obj = local;
			type = Type.CONST;
			if (table.isStringConstant(use)) {
				local.addValue(table.getStringValue(use));
			} else if (table.isNumberConstant(use)) {
				local.addValue(String.valueOf(table.getDoubleValue(use)));
			} else {
				local.addValue("unknown constant type: " + table.getValueString(use));
			}
		} else if (table.isParameter(use)) {
			int pos = -1;
			int[] paramValueNumber = table.getParameterValueNumbers();
			for (int i = 0; i < paramValueNumber.length; i++) {
				if (paramValueNumber[i] == use) {
					pos = ir.getMethod().isStatic() ? i + 1 : i;
					break;
				}
			}
			if (pos == 0) {
				// this pointer is parameter
				// handle this like "other"
				obj = inst;
				inf = DalvikTypeInference.make(ir, false);
				type = Type.OTHER;
			} else {
				obj = Pair.make(ir.getMethod().getReference(), pos);
				type = Type.PARAM;
			}
		} else {
			SSAInstruction def = cache.getDefUse(ir).getDef(use);
			if (def != null && def instanceof SSAAbstractInvokeInstruction) {
				type = Type.METHOD;
				obj = (SSAAbstractInvokeInstruction) def;
			} else {
				type = Type.OTHER;
				inf = DalvikTypeInference.make(ir, false);

				obj = inst;
			}
		}
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		boolean changed = false;
		switch (type) {
		case CONST:
			changed = lhs.registerVar(def, (Local) obj);
			break;
		case METHOD:
			SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) obj;
			FieldOrLocal ret = lhs.getReturnValue(invoke.getDeclaredTarget(), invoke.getDef());
			changed = lhs.registerVar(def, ret);
			break;
		case PARAM:
			Pair<MethodReference, Integer> pair = (Pair<MethodReference, Integer>) obj;
			int pos = pair.snd;
			FieldOrLocal param = lhs.getParameterVar(pair.fst, pair.snd);
			changed = lhs.registerVar(def, param);
			break;
		case OTHER:
			SSAInvokeInstruction inst = (SSAInvokeInstruction) obj;
			FieldOrLocal var = lhs.getVar(inst.getUse(0));
			if (var == null) {
				TypeReference declaringClass = inf.getType(inst.getUse(0)).getTypeReference();
				if (declaringClass == null) {
					// we don't know anything about this variable, so make
					// anystring
					var = new Local(String.valueOf(inst.getUse(0)));
					var.setAnyString();
				} else {
					MethodReference toString = MethodReferences.makeToStringRef(declaringClass);
					var = lhs.getReturnValue(toString, def);
				}
				return CHANGED_AND_FIXED; // all done
			}
			changed = lhs.registerVar(def, var);
			break;
		case ANY:
		case FIELD:
		default:
			Assertions.UNREACHABLE();
		}
		if (changed) {
			return CHANGED_AND_FIXED;
		} else {
			return NOT_CHANGED_AND_FIXED;
		}

	}

	@Override
	public int hashCode() {
		return 1337 * def;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "String_valueOf: " + def;
	}

}
