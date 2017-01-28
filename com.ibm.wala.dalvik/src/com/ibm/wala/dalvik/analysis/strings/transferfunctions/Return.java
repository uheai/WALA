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
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.debug.Assertions;


public class Return extends UnaryOperator<NodeVariable> {

	private int val;

	private MethodReference ref;

	private Object value;

	private Type type;

	public Return(SSAReturnInstruction inst, IR ir, AnalysisCache cache) {
		val = inst.getResult();
		this.ref = ir.getMethod().getReference();
		type = Type.OTHER;
		SymbolTable table = ir.getSymbolTable();
		if (val != -1) {
			if (table.isStringConstant(val)) {
				Local local = new Local();
				local.addValue(table.getStringValue(val));
				type = Type.CONST;
				value = local;
			} else if (table.isParameter(val)) {
				type = Type.PARAM;
				int pos = -1;
				int[] params = table.getParameterValueNumbers();
				for (int i = 0; i < params.length; i++) {
					if (params[i] == val) {
						pos = i;
						break;
					}
				}
				if (pos == 0) {
					//method returns this parameter
					pos = inst.getUse(0);
					type = Type.OTHER; //change type to other as this is no "real" parameter
				}
				value = pos;
			} else {
				SSAInstruction def = cache.getDefUse(ir).getDef(val);
				if (def != null && def instanceof SSAAbstractInvokeInstruction) {
					value = (SSAAbstractInvokeInstruction) def;
					type = Type.METHOD;
				}
			}
		}
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		if (val == -1) { // method is void
			// nothing to do, this equation is lhs = rhs <==> lhs = id(rhs)
			// which is already in equation system
			return NOT_CHANGED_AND_FIXED;
		}
		FieldOrLocal returnVar = lhs.getReturnValue(ref, val);
		switch (type) {
		case CONST:
			Local local = (Local) value;
			if (lhs.addVar(returnVar, local)) {
				return CHANGED;
			}
			break;
		case PARAM:
			Local param = lhs.getParameterVar(ref, (Integer) value);
			if (lhs.addVar(returnVar, param)) {
				return CHANGED;
			}
			break;
		case METHOD:
			SSAAbstractInvokeInstruction callee = (SSAAbstractInvokeInstruction) value;
			FieldOrLocal otherReturn = lhs.getReturnValue(callee.getDeclaredTarget(), callee.getDef());
			if (otherReturn.equals(returnVar)) {
				return NOT_CHANGED;
			}
			if (lhs.addVar(returnVar, otherReturn)) {
				return CHANGED;
			}
			break;
		case OTHER:
			if (lhs.addVar(returnVar, val)) {
				return CHANGED;
			}
			//nothing to do
			break;
		case ANY:
		case FIELD:
		default:
			Assertions.UNREACHABLE("unknown type: " + type);
		}
		return NOT_CHANGED;
	}

	@Override
	public int hashCode() {
		return 1337 * val;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "Return Transferfunction: " + val;
	}

}
