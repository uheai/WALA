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

import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

/**
 * Represents constructor call of StringBuilder and StringBuffer
 * @author maik
 *
 */
public class InvokeStringLikeInit extends Invoke {

	private int vn;

	public InvokeStringLikeInit(SSAInvokeInstruction inst, IR ir, AnalysisCache cache) {
		super(inst, ir, cache);
		vn = inst.getUse(0);
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		FieldOrLocal var = lhs.getVar(vn);
		byte state = NOT_CHANGED_AND_FIXED;
		if (params == null) {
			// if there is no initial parameter, set prefix to empty string
			// (epsilon)
			Local local = new Local();
			local.addValue("");
			lhs.addVar(var, local);
			return CHANGED_AND_FIXED;
		}
		for (int i = 0; i < params.length; i++) {
			switch (types[i]) {
			case CONST:
				if (lhs.addVar(var, (FieldOrLocal) params[i])) {
					state = CHANGED_AND_FIXED;
				}
				break;
			case PARAM:
				Pair<MethodReference, Integer> p = (Pair<MethodReference, Integer>) params[i];
				Local param = lhs.getParameterVar(p.fst, p.snd);
				// there is no chance we get a this pointer as paramter because
				// valid parameter types are
				// String, CharSequence and int
				if (lhs.addVar(var, param)) {
					state = CHANGED_AND_FIXED;
				}
				break;
			case METHOD:
				SSAAbstractInvokeInstruction def = (SSAAbstractInvokeInstruction) params[i];
				FieldOrLocal ret = lhs.getReturnValue(def.getDeclaredTarget(), def.getDef());
				if (lhs.addVar(var, ret)) {
					state = NOT_CHANGED_AND_FIXED;
				}
				break;
			case OTHER:
				if (lhs.addVar(var, (Integer) params[i])) {
					state = CHANGED_AND_FIXED;
				}
				break;
			case ANY:
			case FIELD:
			default:
				Assertions.UNREACHABLE("unknown type: " + types[i]);
			}
		}
		return state;
	}

	@Override
	public String toString() {
		return "string like init: " + vn;
	}

}
