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
import com.ibm.wala.dalvik.analysis.strings.variables.FieldVariable;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstruction.Visitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.debug.Assertions;

public class Put extends UnaryOperator<NodeVariable> {

	private Type type;

	private Object put;

	private int vn;

	private FieldReference ref;

	private MethodReference method;

	public Put(SSAPutInstruction instruction, IR ir, AnalysisCache cache) {
		DefUse defUse = cache.getDefUse(ir);
		vn = instruction.getVal();
		ref = instruction.getDeclaredField();
		if (ir.getSymbolTable().isConstant(vn)) {
			Local local = new Local("const");
			type = Type.CONST;
			String s = null;
			SymbolTable table = ir.getSymbolTable();
			if (table.isStringConstant(vn)) {
				s = table.getStringValue(vn);
			} else if (table.isNumberConstant(vn)) {
				s = Double.toString(table.getDoubleValue(vn));
			} else if (table.isNullConstant(vn)) {
				s = "NULL";
			} else {
				s = "Unknown Constant Type: " + instruction.getDeclaredFieldType().getName().toString();
			}
			local.addValue(s);
			put = local;
		} else {
			// put = "NAC: Type = " +
			// instruction.getDeclaredFieldType().getName().toString();
			SSAInstruction def = defUse.getDef(vn);
			if (def == null) {
				SymbolTable table = ir.getSymbolTable();
				if (table.isParameter(vn))
					put = new Integer(1);
				type = Type.PARAM;
				method = ir.getMethod().getReference();
				if (table.getNumberOfParameters() > 1) {
					int[] params = table.getParameterValueNumbers();
					for (int i = 1; i < params.length; i++) {
						if (params[i] == vn) {
							put = i;
							break;
						}
					}
				}
			} else {
				def.visit(new PutVisitor());
			}
		}
		if (put == null) {
			put = vn;
			type = Type.OTHER;
		}

	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		FieldVariable var = lhs.getField(ref, vn);
		switch (type) {
		case CONST:
			if (lhs.addVar(var, (Local) put)) {
				return CHANGED;
			}
			break;
		case FIELD:
			SSAGetInstruction inst = (SSAGetInstruction) put;
			FieldVariable field_rhs = lhs.getField(inst.getDeclaredField(), inst.getDef());
			if (lhs.addVar(var, field_rhs)) {
				return CHANGED;
			}
			break;
		case METHOD:
			SSAInvokeInstruction invoke = (SSAInvokeInstruction) put;
			if (lhs.addMethod(var, invoke.getDeclaredTarget())) {
				return CHANGED;
			}
			break;
		case PARAM:
			Local param = lhs.getParameterVar(method, (Integer) put);
			if (lhs.addVar(var, param)) {
				return CHANGED;
			}
			break;
		case OTHER:
			if (lhs.addVar(var, (Integer) put)) {
				return CHANGED;
			}
			break;
		case ANY:
		default:
			Assertions.UNREACHABLE();
		}
		return NOT_CHANGED;
	}

	@Override
	public int hashCode() {
		return 1337*vn;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "SSAPut Transferfunction " + vn;
	}

	/**
	 * visitor to determine what type of value this field is set to, i.e another
	 * field (field1 = field2) or result from method (field1 = someMethod())
	 * 
	 * @author maik
	 *
	 */
	private class PutVisitor extends Visitor {

		@Override
		public void visitGet(SSAGetInstruction instruction) {
			put = instruction;
			type = Type.FIELD;
		}

		@Override
		public void visitInvoke(SSAInvokeInstruction instruction) {
			put = instruction;
			type = Type.METHOD;
		}
	}

}
