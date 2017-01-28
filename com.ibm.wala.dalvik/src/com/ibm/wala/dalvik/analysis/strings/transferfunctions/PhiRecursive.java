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
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ssa.SSAPhiInstruction;

/**
 * Represents a phi-node with a recursive dependency, 
 * like v7 = phi(v3,v7) or v10(v3,v7), v3 = phi(v4,v10)
 * @author maik
 *
 */
public class PhiRecursive extends UnaryOperator<NodeVariable> {
	
	private int prefix;
	
	private int var; 
	
	private int suffix;
	
	public PhiRecursive(SSAPhiInstruction inst, int cause) {
		var = inst.getDef();
		//find variable which leads to a cycle
		if (inst.getUse(0) == cause) {
			prefix = inst.getUse(1);
			suffix = inst.getUse(0);
		} else {
			prefix = inst.getUse(0);
			suffix = inst.getUse(1);
		}
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		FieldOrLocal prefixVar = lhs.getOrCreateVar(prefix);
		FieldOrLocal variable = lhs.getOrCreateVar(var);
		FieldOrLocal suffixVar = lhs.getOrCreateVar(suffix);
		if (lhs.addLoop(variable, prefixVar, suffixVar)) {
			return CHANGED_AND_FIXED;
		} else {
			return NOT_CHANGED_AND_FIXED;
		}
	}

	@Override
	public int hashCode() {
		return 42*(prefix + var + suffix);
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "phi-recursive: " + var;
	}

}
