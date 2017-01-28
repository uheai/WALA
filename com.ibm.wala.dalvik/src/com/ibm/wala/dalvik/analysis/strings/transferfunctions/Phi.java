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

import java.util.Collection;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.dalvik.analysis.strings.util.Type;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.IntentVar;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.dalvik.analysis.typeInference.DalvikTypeInference;
import com.ibm.wala.dalvik.util.AndroidTypes;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

/**
 * Represents a phi node, like v4 = phi(v7,v9)
 * @author maik
 *
 */
public class Phi extends UnaryOperator<NodeVariable> {
	
	private static Logger log = LoggerFactory.getLogger(Phi.class);

	private Object[] uses;

	private Type[] types;

	private int def;
	
	private SSAInstruction inst;
	/**
	 * true iff one of the uses is an intent. If so the resulting variable (def)
	 * is also an intent
	 */
	private boolean isIntentPhi;

	public Phi(SSAPhiInstruction inst, IR ir, AnalysisCache cache) {
		def = inst.getDef();
		this.inst = inst;
		SymbolTable table = ir.getSymbolTable();
		int numOfUses = inst.getNumberOfUses();
		uses = new Object[numOfUses];
		types = new Type[numOfUses];
		DalvikTypeInference inf = DalvikTypeInference.make(ir, true);
		for (int i = 0; i < numOfUses; i++) {
			int current = inst.getUse(i);
			if (current == -1) {
			  continue;
			}
			//if one of the used variables is an intent, the resulting variable must also be an intent, e.g
			//the union of all intents
			TypeAbstraction ta = inf.getType(current);
			if (ta != null && (!ta.equals(TypeAbstraction.TOP)) &&  ta.getTypeReference().getName().equals(AndroidTypes.IntentName)) {
				isIntentPhi = true;
			}
			if (table.isConstant(current)) {
				Local local = new Local();
				uses[i] = local;
				types[i] = Type.CONST;
				if (table.isStringConstant(current)) {
					local.addValue(table.getStringValue(current));
				} else if (table.isNumberConstant(current)) {
					String num = String.valueOf(table.getDoubleValue(current));
					local.addValue(num);
				} else {
					local.addValue("unknown constant type");
				}
			} else if (table.isParameter(current)) {
				int[] paramValueNumbers = table.getParameterValueNumbers();
				int pos = -1;
				for (int j = 0; j < paramValueNumbers.length; j++) {
					if (paramValueNumbers[j] == current) {
						pos = ir.getMethod().isStatic() ? j + 1 : j;
						break;
					}
				}
				uses[i] = Pair.make(ir.getMethod().getReference(), pos);
				types[i] = Type.PARAM;

				if (pos == -1) {
					log.error("position is not set properly");
				}
			} else {
				SSAInstruction def = cache.getDefUse(ir).getDef(current);
				if (def != null && def instanceof SSAAbstractInvokeInstruction) {
					uses[i] = (SSAAbstractInvokeInstruction) def;
					types[i] = Type.METHOD;
				} else {
					uses[i] = current;
					types[i] = Type.OTHER;
				}
			}
		}
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		if (isIntentPhi) {
			return evaluateIntentPhi(lhs);
		}
		FieldOrLocal var = lhs.getOrCreateVar(def);
		for (int i = 0; i < uses.length; i++) {
		  if (types[i] == null) {
		    continue; //ignore TOP
		  }
			switch (types[i]) {
			case CONST:
				lhs.addVar(var, (Local) uses[i]);
				break;
			case METHOD:
				SSAAbstractInvokeInstruction inst = (SSAAbstractInvokeInstruction) uses[i];
				FieldOrLocal ret = lhs.getReturnValue(inst.getDeclaredTarget(), inst.getDef());
				if (var != ret) {
				lhs.addVar(var, ret);
				}
				break;
			case PARAM:
				Pair<MethodReference, Integer> p = (Pair<MethodReference, Integer>) uses[i];
				if (p.snd == 0) {
					//this parameter, we have no information about, so set this variable to anystring
					var.setAnyString();
					//var is anystring, so we can stop here
					return CHANGED_AND_FIXED;
				}
				FieldOrLocal param = lhs.getParameterVar(p.fst, p.snd);
				lhs.addVar(var, param);
				break;
			case OTHER:
				FieldOrLocal other = lhs.getOrCreateVar((Integer) uses[i]);
				lhs.addVar(var, other);
				break;
			case ANY:
			case FIELD:
			default:
				Assertions.UNREACHABLE();
			}
		}
		return CHANGED_AND_FIXED;
	}
	
	/**
	 * implements union of intents, i.e. union of each component (action, data...) 
	 * @param lhs node variable
	 * @return state
	 */
	private byte evaluateIntentPhi(NodeVariable lhs) {
		IntentVar intent = lhs.getOrCreateIntent(def, inst);
		Collection<FieldOrLocal> inits = new HashSet<FieldOrLocal>(uses.length);
		for (int i = 0; i < uses.length; i++) {
			if (types[i] == null) {
				continue; //ignore TOP
			}
			switch (types[i]) {
			case CONST:
				Local local = (Local) uses[i];
				if (!local.getValues().contains("0.0")) {
				Assertions.UNREACHABLE("unexpected constant in intent phi");
				}
				//constant 0 means null
				//we ignore null intents
				continue;
			case PARAM:
				Pair<MethodReference, Integer> p = (Pair<MethodReference, Integer>) uses[i];
				if (p.snd == 0) {
					//this parameter, we have no inforamtion about, so set variables of intent to anystring
					intent.setAllAnyString();
					//because this intent is anystring, we can stop here
					return CHANGED_AND_FIXED;
				}
				FieldOrLocal param = lhs.getParameterVar(p.fst, p.snd);
				inits.add(param);
				break;
			case METHOD:
				SSAAbstractInvokeInstruction inst = (SSAAbstractInvokeInstruction) uses[i];
				FieldOrLocal ret = lhs.getReturnValue(inst.getDeclaredTarget(), inst.getDef());
				inits.add(ret);
				break;
			case OTHER:
				//unlike in the PARAM and METHOD cases, where the actually intents are the unresolved varables of the FieldOrLocal variable,
				//the variable we get is already an intent. So we can not just add it to inits.
				//We have to build the eqautions manually
				IntentVar other = lhs.getOrCreateIntent((Integer) uses[i], this.inst);
				intent.addAction(other.getAction());
				intent.addExtra(other.getExtra());
				intent.addClassVar(other.getClassVar());
				intent.addData(other.getData());
				break;
			case ANY:
			case FIELD:
				Assertions.UNREACHABLE();
			}
			//add inits to intent
			intent.setInits(inits);
		}
		return CHANGED_AND_FIXED;
	}
	

	@Override
	public int hashCode() {
		return 1234*def;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "Phi:" + def;
	}

}
