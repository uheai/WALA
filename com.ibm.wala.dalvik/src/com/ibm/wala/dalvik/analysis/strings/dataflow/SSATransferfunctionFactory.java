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
package com.ibm.wala.dalvik.analysis.strings.dataflow;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Get;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Identity;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.IntentInit;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.InvokeStringLikeInit;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.New;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.NewIntent;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Phi;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.PhiRecursive;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Put;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Return;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.UnknownIntentMethod;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.Append;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.IntentPutExtra;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.SetAction;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.SetClassName;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.SetData;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.StartActivity;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.String_valueOf;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.Substring;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.ToString;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.methods.Uri_parse;
import com.ibm.wala.dalvik.analysis.strings.util.MethodReferences;
import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.dalvik.dex.instructions.Invoke;
import com.ibm.wala.dalvik.util.AndroidTypes;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstruction.IVisitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.types.MethodReference;


public class SSATransferfunctionFactory {

	private Logger log = LoggerFactory.getLogger(SSAAbstractInvokeInstruction.class);

	private UnaryOperator<NodeVariable> function;

	private SSATransferfunctionVisitor visitor = new SSATransferfunctionVisitor();

	private IR ir;

	private AnalysisCache cache;

	private int exitBlockNumber;

	public SSATransferfunctionFactory(IR ir, AnalysisCache cache) {
		visitor = new SSATransferfunctionVisitor();
		this.ir = ir;
		this.cache = cache;
		exitBlockNumber = ir.getExitBlock().getNumber();
	}

	public UnaryOperator<NodeVariable> getTransferFunction(SSAInstruction inst) {
		function = new Identity();
		inst.visit(visitor); // changes function to right value
		return function;
	}

	private class SSATransferfunctionVisitor implements IVisitor {

		@Override
		public void visitGoto(SSAGotoInstruction instruction) {
			// not needed

		}

		@Override
		public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
			// not needed

		}

		@Override
		public void visitArrayStore(SSAArrayStoreInstruction instruction) {
			// not needed

		}

		@Override
		public void visitBinaryOp(SSABinaryOpInstruction instruction) {
			// not needed

		}

		@Override
		public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
			// not needed

		}

		@Override
		public void visitConversion(SSAConversionInstruction instruction) {
			// not needed

		}

		@Override
		public void visitComparison(SSAComparisonInstruction instruction) {
			// not needed

		}

		@Override
		public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
			// not needed
		}

		@Override
		public void visitSwitch(SSASwitchInstruction instruction) {
			// not needed

		}

		@Override
		public void visitReturn(SSAReturnInstruction instruction) {
			function = new Return(instruction, ir, cache);
		}

		@Override
		public void visitGet(SSAGetInstruction instruction) {
			function = new Get(instruction);
		}

		@Override
		public void visitPut(SSAPutInstruction instruction) {
			if (Util.hasRelevantType(instruction.getDeclaredFieldType().getName())) {
				function = new Put(instruction, ir, cache);
			} // ignore put instructions for fields that are not relevant anyway
		}

		/*
		 * @Override public void visitInvoke(SSAInvokeInstruction instruction) {
		 * MethodReference target = instruction.getDeclaredTarget(); // int
		 * paramNum = instruction.getNumberOfParameters(); if (target.isInit()
		 * && Util.isStringLikeType(target.getDeclaringClass().getName())) {
		 * function = new InvokeStringLikeInit(instruction, ir, cache); } else
		 * if (Util.isStringLikeType(target.getDeclaringClass().getName())) { if
		 * (target.getSelector().equals(MethodReferences.StringBufferToString.
		 * getSelector())) { function = new ToString(instruction); } else if
		 * (target.getName().equals(MethodReferences.StrValueOf)) { // we know
		 * this instruction invokes // String.valueOf(something), // the actual
		 * parameter type is not important function = new
		 * String_valueOf(instruction, ir, cache); }
		 * 
		 * } else { function = new Invoke(instruction, ir, cache); } }
		 */

		@Override
		public void visitInvoke(SSAInvokeInstruction instruction) {
			MethodReference target = instruction.getDeclaredTarget();

			// init methods, e.g constructor calls
			if (target.isInit()) {
				if (Util.isStringLikeType(target.getDeclaringClass().getName())) {
					function = new InvokeStringLikeInit(instruction, ir, cache);
					return;
				}
				if (target.getDeclaringClass().getName().equals(AndroidTypes.IntentName)) {
					function = new IntentInit(instruction, ir, cache);
					return;
				}
			}

			// String / StringBuilder / StringBuffer methods
			if (Util.isStringLikeType(target.getDeclaringClass().getName())) {
				if (target.getSelector().equals(MethodReferences.StringBufferToString.getSelector())) {
					function = new ToString(instruction);
					return;
				}

				if (target.getName().equals(MethodReferences.StrValueOf)) {
					function = new String_valueOf(instruction, ir, cache);
					return;
				}

				if (target.getSelector().equals(MethodReferences.substring.getSelector())) {
					function = new Substring(instruction, ir);
					return;
				}
			}

			// Intent methods
			if (target.getDeclaringClass().getName().equals(AndroidTypes.IntentName)) {

				if (target.getName().equals(MethodReferences.IntentPutExtraAtom)
						&& target.getNumberOfParameters() == 2) {
					function = new IntentPutExtra(instruction, ir, cache);
					return;
				}
				
				if (target.getSelector().equals(MethodReferences.IntentSetAction.getSelector())) {
					function = new SetAction(instruction, ir, cache);
					return;
				}
				
				if (target.getSelector().equals(MethodReferences.IntentSetData.getSelector())) {
					function = new SetData(instruction, ir, cache);
					return;
				}
				
				if (target.getSelector().equals(MethodReferences.IntentSetDataAndNormalize.getSelector())) {
					function = new SetData(instruction, ir, cache);
					return;
				}
				
				if (target.getSelector().equals(MethodReferences.IntentSetDataAndType.getSelector())) {
					function = new SetData(instruction, ir, cache);
					return;
				}
				
				if (target.getSelector().equals(MethodReferences.IntentSetDataAndTypeAndNormalize.getSelector())) {
					function = new SetData(instruction, ir, cache);
					return;
				}
				
				if (target.getSelector().equals(MethodReferences.IntentSetClassName.getSelector()) ||
						target.getSelector().equals(MethodReferences.IntentSetClassNameContextString.getSelector())) {
					function = new SetClassName(instruction, ir, cache);
					return;
				}
				/*
				 * ----------------------
				 * 		default case 
				 * ----------------------
				 */
				
				//for methods, called on an intent and returning an intent, which we can not interpret, we assume 
				//that a reference of the instance the method is called on will be returned.
				if (target.getReturnType().getName().equals(AndroidTypes.IntentName)) {
					function = new UnknownIntentMethod(instruction);
					return;
				}
			}

			// startActivity
			if (target.getName().equals(MethodReferences.ContextStartActivity)
					&& target.getParameterType(0).getName().equals(AndroidTypes.IntentName)) {
				// there are several startActivity methods where first parameter
				// is an intent.
				// We only care about that
				function = new StartActivity(instruction.getUse(1));
				return;
			}

			// Uri methods

			if (target.getDeclaringClass().getName().equals(MethodReferences.Uri.getName())) {

				if (target.getSelector().equals(MethodReferences.Uri_parse.getSelector())) {
					function = new Uri_parse(instruction, ir, cache);
					return;
				}
			}

			function = new com.ibm.wala.dalvik.analysis.strings.transferfunctions.Invoke(instruction, ir, cache);

		}

		@Override
		public void visitNew(SSANewInstruction instruction) {
			if (Util.hasRelevantType(instruction.getConcreteType().getName())) {
				function = new New(instruction, ir.getMethod().getReference(),
						ir.getLocalNames(instruction.iindex, instruction.getDef()));
			} 
			
			if (instruction.getConcreteType().getName().equals(AndroidTypes.IntentName)) {
				function = new NewIntent(instruction);
			}
			// ignore if this object is not interesting
		}

		@Override
		public void visitArrayLength(SSAArrayLengthInstruction instruction) {
			// not needed

		}

		@Override
		public void visitThrow(SSAThrowInstruction instruction) {
			// not needed

		}

		@Override
		public void visitMonitor(SSAMonitorInstruction instruction) {
			// not needed

		}

		@Override
		public void visitCheckCast(SSACheckCastInstruction instruction) {
			// not needed

		}

		@Override
		public void visitInstanceof(SSAInstanceofInstruction instruction) {
			// not needed

		}

		@Override
		public void visitPhi(SSAPhiInstruction instruction) {
			int def = instruction.getDef();
			SSAPhiInstruction reduced = reduceInstruction(instruction);
			if (reduced.getNumberOfUses() == 2) {
				int use0 = reduced.getUse(0);
				int use1 = reduced.getUse(1);
				boolean b0 = isRecursivePiChain(def, use0, new HashSet<Integer>());
				boolean b1 = isRecursivePiChain(def, use1, new HashSet<Integer>());
				if (b0 != b1) {
					// one branch is recursive, the other not
					int cause = b0 ? use0 : use1;
					function = new PhiRecursive(reduced, cause);
					return;
				}
			}
			function = new Phi(reduced, ir, cache);
		}

		@Override
		public void visitPi(SSAPiInstruction instruction) {
			if (instruction.getSuccessor() == exitBlockNumber) {
				return; // ignore this instruction as this is the case were an
						// exception is thrown
			}
			if (instruction.getCause() instanceof SSAAbstractInvokeInstruction) {
				SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) instruction.getCause();
				int def_vn = instruction.getDef();
				if (invoke.getDeclaredTarget().getName().equals(MethodReferences.append.getName())) {
					function = new Append(invoke, def_vn, ir, cache);
				}
			}
		}

		@Override
		public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
			// not needed
		}

		@Override
		public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
			// not needed

		}

		/**
		 * used to detect recursive dependencies, like def = phi (use_1, use_2)
		 * use_1 = phi (use_3, use_4) ... use_n = pi (def)
		 * 
		 * @param def
		 *            left hand side of equation like in def = phi (var1, var2)
		 * @param use
		 *            one of the uses of the right hand side
		 * @return true iff there is a recursive dependency
		 */
		private boolean isRecursivePiChain(int def, int use, Set<Integer> seen) {
			if (def == use) {
				return true;
			}
			if (seen.contains(use)) {
				return false; // we ran into a cycle of which def is not a part
			}
			if (use == -1) {
				return false; // we reached TOP, there is nothing to do anymore
			}
			seen.add(use);
			SSAInstruction def_inst = cache.getDefUse(ir).getDef(use);
			if (def_inst instanceof SSAPiInstruction) {
				return isRecursivePiChain(def, def_inst.getUse(0), seen);
			}
			if (def_inst instanceof SSAPhiInstruction) {
				SSAPhiInstruction phi_inst = (SSAPhiInstruction) def_inst;
				boolean result = false;
				int seen_size = seen.size();
				for (int i = 0; i < phi_inst.getNumberOfUses(); i++) {
					int use_i = phi_inst.getUse(i);
					result = isRecursivePiChain(def, use_i, seen);
					if (seen.size() > seen_size) {
						// don't delete an element which already was in this set
						seen.remove(use_i);
					}
					if (result) {
						// we can stop here because we found a circle.
						// It does not matter where the remaining uses (edges)
						// of this phi
						// instruction are leading
						return true;
					}
				}

			}
			return false;
		}

		/**
		 * reduces phi(v1,v1,v2,v3,v3) to phi(v1,v2,v3)
		 * @param inst phi instruction 
		 * @return reduced instruction, i.e. instruction where each variable used appears only once
		 */
		private SSAPhiInstruction reduceInstruction(SSAPhiInstruction inst) {
			int def = inst.getDef();
			int numOfUses = inst.getNumberOfUses();
			HashSet<Integer> params = new HashSet<Integer>(numOfUses);
			for (int i = 0; i < numOfUses; i++) {
				int use = inst.getUse(i);
				if (use != def) {
					params.add(use);
				}
			}
			if (params.size() == numOfUses) {
				// nothing to reduce, there are no self-edges and no duplicates
				return inst;
			}
			int[] uses = new int[params.size()];
			int pos = 0;
			for (int use : params) {
				uses[pos] = use;
				pos++;
			}
			return new SSAPhiInstruction(inst.iindex, def, uses);
		}
	}

}
