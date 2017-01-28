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

import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Invoke;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.IntentVar;
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
 * Intent putExtra(String key,...)
 * @author maik
 *
 */
public class IntentPutExtra extends Invoke {
	
	private int vn;
	
	private int def;
	
	private SSAInvokeInstruction inst;

	public IntentPutExtra(SSAInvokeInstruction inst, IR ir, AnalysisCache cache) {
		super(inst, ir, cache);
		vn = inst.getUse(0);
		def = inst.getDef();
		this.inst = inst;
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		assert (params.length == 2); //for debugging purposes
		boolean changed = false;
		IntentVar intent = lhs.getOrCreateIntent(vn, inst); //this intent should exist though
		//putExtra returns pointer to intent its called on, which results in new ssa variable
		//we have to register this variable, otherwise parts of calls like intent.putExtra.putExtra.putExtra...
		//would get lost
		
		//we do not need this anymore, because we create a new intent for the returned variable
		//lhs.registerVar(def, lhs.getVar(vn));
		
		
		//we are currently only interested in the first argument, which is a string.
		//Second argument is of type Bundle, which needs implementation of other methods to interpret them.
		switch (types[0]) {
		case CONST:
			Local constant = (Local) params[0];
			//the intent this method is called on, should already exist
			changed = lhs.addVar(intent.getExtra(), constant);
			break;
		case METHOD:
			SSAAbstractInvokeInstruction inst = (SSAAbstractInvokeInstruction) params[0];
			FieldOrLocal returnValue = lhs.getReturnValue(inst.getDeclaredTarget(), inst.getDef());
			changed = lhs.addVar(intent.getExtra(), returnValue);
			break;
		case PARAM:
			Pair<MethodReference, Integer> pair = (Pair<MethodReference, Integer>) params[0];
			FieldOrLocal param = lhs.getParameterVar(pair.fst, pair.snd);
			changed = lhs.addVar(intent.getExtra(), param);
			break;
		case OTHER:
			FieldOrLocal other = lhs.getOrCreateVar((Integer) params[0]);
			changed = lhs.addVar(intent.getExtra(), other);
			break;
		case ANY:
		case FIELD:
		default: 
			Assertions.UNREACHABLE("unknown type " + types[0]);
		}
		if (changed) {
			return CHANGED_AND_FIXED;
		} else {
			return NOT_CHANGED_AND_FIXED;
		}
	}
	
	@Override
	public int hashCode() {
		return 1337 * vn;
	}
	
	@Override
	public boolean equals(Object o) {
		return this == o;
	}
	
	@Override
	public String toString() {
		return "Intent.putExtra: " + vn;
	}
}
