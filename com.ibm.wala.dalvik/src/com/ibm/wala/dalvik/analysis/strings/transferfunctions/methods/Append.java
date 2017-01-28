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
import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

/**
 * StringBuilder append
 * @author maik
 *
 */
public class Append extends UnaryOperator<NodeVariable> {

	/**
	 * value number of created pi-node (where the result is stored)
	 */
	private int piNode;

	/**
	 * value numbe of object append is being called on, e.g. for v3.append(v4)
	 * obj is 3
	 */
	private int obj;

	/**
	 * value number the append instruction returns, e.g v5 = v3.append(v4).
	 * After all its just an alias for the piNode
	 */
	private int def;

	/**
	 * parameter which is appended to object
	 */
	private Object par;

	/**
	 * type of paramaeter
	 */
	private Type type;

	public Append(SSAAbstractInvokeInstruction inst, int piNode, IR ir, AnalysisCache cache) {
		// Assert: inst invokes append method, a.append(b)
		this.piNode = piNode;
		obj = inst.getUse(0);
		def = inst.getDef();
		int param = inst.getUse(1);
		SymbolTable table = ir.getSymbolTable();
		if (table.isConstant(param)) {
			Local local = new Local("const");
			if (table.isStringConstant(param)) {
				local.addValue(table.getStringValue(param));
			} else if (table.isNumberConstant(param)) {
				local.addValue(String.valueOf(table.getDoubleValue(param)));
			} else {
				local.addValue("unknown constant type:" + table.getValueString(param));
			}
			par = local;
			type = Type.CONST;
		} else if (table.isParameter(param)) {
			type = Type.PARAM;
			int[] paramValueNumbers = table.getParameterValueNumbers();
			for (int i = 0; i < paramValueNumbers.length; i++) {
				if (paramValueNumbers[i] == param) {
					int pos = ir.getMethod().isStatic() ? i + 1 : i;
					par = Pair.make(ir.getMethod().getReference(), pos);
					break;
				}
			}
		} else {
			SSAInstruction def = cache.getDefUse(ir).getDef(param);
			if (def != null && def instanceof SSAAbstractInvokeInstruction) {
				type = Type.METHOD;
				par = (SSAAbstractInvokeInstruction) def;
			} else {
				par = inst;
				type = Type.OTHER;
			}
		}
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		FieldOrLocal var = lhs.getOrCreateVar(piNode);
		lhs.registerVar(def, var);
		FieldOrLocal prefix = lhs.getOrCreateVar(obj);
		assert (prefix != null) : "prefix is null"; // should already exist
		boolean state = false;
		switch (type) {
		case CONST:
			state = lhs.addAppend(var, prefix, (Local) par);
			break;
		case PARAM:
			Pair<MethodReference, Integer> p = (Pair<MethodReference, Integer>) par;
			FieldOrLocal param = null;
			int pos = p.snd;
			if (pos == 0) {
				// this pointer is parameter
				// try to find toString method of declaring class
				MethodReference toString = MethodReferences.makeToStringRef(p.fst.getDeclaringClass());
				param = lhs.getReturnValue(toString, -1);
			} else {
				param = lhs.getParameterVar(p.fst, pos);
			}
			state = lhs.addAppend(var, prefix, param);
			break;
		case METHOD:
			SSAAbstractInvokeInstruction def = (SSAAbstractInvokeInstruction) par;
			TypeReference returnType = def.getDeclaredResultType();
			MethodReference method = def.getDeclaredTarget();
			if (!Util.isStringLikeType(returnType.getName())) {
				//this methods returns an object
				//try to find string representation via toString method
				method = MethodReferences.makeToStringRef(returnType);			
			}
			FieldOrLocal returnVar = lhs.getReturnValue(method, def.getDef());
			state = lhs.addAppend(var, prefix, returnVar);
			break;
		case OTHER:
			SSAAbstractInvokeInstruction inst = (SSAAbstractInvokeInstruction) par;
			FieldOrLocal other = lhs.getVar(inst.getUse(1));
			if (other == null) {
				other = new Local();
				other.setAnyString();
			}
			state = lhs.addAppend(var, prefix, other);
			break;
		case ANY:
		case FIELD:
		default:
			Assertions.UNREACHABLE("unknown type: " + type);
		}
		if (state)

		{
			return CHANGED_AND_FIXED;
		} else {
			return NOT_CHANGED_AND_FIXED;
		}
	}

	@Override
	public int hashCode() {
		return 1337 * piNode;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "append: " + piNode;
	}

}
