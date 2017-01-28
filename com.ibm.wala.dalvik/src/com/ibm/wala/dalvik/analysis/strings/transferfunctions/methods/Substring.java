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

import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;

/**
 * call of substring on a string. Currently just constant parameters can be handled.
 * @author maik
 *
 */
public class Substring extends UnaryOperator<NodeVariable> {
	
	private int def;
	
	private Local substring;
	
	public Substring(SSAInvokeInstruction inst, IR ir) {
		//Assert: inst invokes substring
		def = inst.getDef();
		SymbolTable table = ir.getSymbolTable();
		int sup = inst.getUse(0); //the string substring is called on
		int val = inst.getUse(1); //parameter of substring call
		if (table.isStringConstant(sup) && table.isIntegerConstant(val)) {
			String supString = table.getStringValue(sup);
			int param = table.getIntValue(val);
			String substr = supString.substring(param);
			substring = new Local("const: substring");
			substring.addValue(substr);
		}
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		if (substring == null) {
			//we could not interpret substring call, so set lhs-variable to anystring
			lhs.getOrCreateVar(def).setAnyString();
			return CHANGED_AND_FIXED;
		}
		boolean ret = lhs.addVar(def, substring);
		if (ret) {
			return CHANGED_AND_FIXED;
		} else {
			return NOT_CHANGED_AND_FIXED;
		}
	}

	@Override
	public int hashCode() {
		return 1337*def;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Substring) {
			Substring other = (Substring) o;
			if (this.def == other.def && this.substring == other.substring) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "substring: def = " + def + ", substring = " + substring;
	}

}
