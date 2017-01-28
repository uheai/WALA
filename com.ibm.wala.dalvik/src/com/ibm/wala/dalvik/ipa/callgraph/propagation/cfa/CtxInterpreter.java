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
package com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa;

import java.util.EnumSet;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.CodeScanner;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.dalvik.analysis.strings.main.StringAnalysis;
import com.ibm.wala.dalvik.analysis.strings.results.Intent;
import com.ibm.wala.dalvik.analysis.strings.util.IntentSet;
import com.ibm.wala.dalvik.analysis.strings.util.IntentType;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.AndroidModel;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.MicroModel;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.MultipleMicroModel;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.stubs.UnknownTargetModel;
import com.ibm.wala.dalvik.ipa.callgraph.propagation.cfa.IntentStarters.StartInfo;
import com.ibm.wala.dalvik.util.AndroidComponent;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ContextItem.Value;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.Atom;

public class CtxInterpreter implements SSAContextInterpreter {

	private static Logger log = LoggerFactory.getLogger(CtxInterpreter.class);

	// private IntentContextInterpreter ici;

	// private CallGraph cg;

	private StringAnalysis analysis;

	private SSAContextInterpreter interpreter;

	private IntentStarters starters;

	private AnalysisCache cache;

	private AnalysisOptions options;

	public CtxInterpreter(IClassHierarchy cha, SSAContextInterpreter interpreter, AnalysisCache cache,
			AnalysisOptions options) {
		this(cha, interpreter, cache, options, null);
		analysis = new StringAnalysis(cha);
		try {
			analysis.runAnalysis();
			analysis.solveHotspots(null, null); // solve Intents
		} catch (CancelException e) {
			// TODO Auto-generated catch block
			System.err.println("Could not run string analysis");
			e.printStackTrace();
		}
	}

	public CtxInterpreter(IClassHierarchy cha, SSAContextInterpreter interpreter, AnalysisCache cache,
			AnalysisOptions options, StringAnalysis analysis) {
		this.interpreter = interpreter;
		this.analysis = analysis;
		this.options = options;
		this.cache = cache;
		starters = new IntentStarters(cha);
	}

	@Override
	public Iterator<NewSiteReference> iterateNewSites(CGNode node) {
		if (node == null) {
			throw new IllegalArgumentException("node is null");
		}
		return getIR(node).iterateNewSites();
	}

	@Override
	public Iterator<FieldReference> iterateFieldsRead(CGNode node) {
		assert understands(node);

		SSAInstruction[] stmts = getIR(node).getInstructions();
		return CodeScanner.getFieldsRead(stmts).iterator();
	}

	@Override
	public Iterator<FieldReference> iterateFieldsWritten(CGNode node) {
		assert understands(node);

		SSAInstruction[] stmts = getIR(node).getInstructions();
		return CodeScanner.getFieldsWritten(stmts).iterator();
	}

	@Override
	public boolean recordFactoryType(CGNode node, IClass klass) {
		// ici.recordFactoryType(node, klass);
		return false;
	}

	/**
	 * understands node, iff context selector added usable context
	 * 
	 * @see IntentStarter
	 * @Override
	 */
	public boolean understands(CGNode node) {
		if (node == null) {
			throw new IllegalArgumentException("node is null");
		}

		Value<Intent> contextIntent = ((Value<Intent>) node.getContext().get(IntentCtx.KEY_INTENT));
		return contextIntent != null;
	}


	@Override
	public Iterator<CallSiteReference> iterateCallSites(CGNode node) {
		if (node == null) {
			throw new IllegalArgumentException("node is null");
		}

		assert understands(node);

		return getIR(node).iterateCallSites();
	}

	@Override
	public IR getIR(CGNode node) {
		assert understands(node);
		IR ir = interpreter.getIR(node);

		com.ibm.wala.dalvik.analysis.strings.results.Intent intent = ((Value<Intent>) node.getContext().get(IntentCtx.KEY_INTENT)).getValue();
		// log.debug("Call: " + call);
		log.debug("Intent: " + intent);
		if (intent == null) {
			// wird vermutlich gar nicht benötigt
			System.err.println("null intent");
			// IntentSet params = getParams(ir, call)
			// TODO handle params
		}

		// handle intent
		try {
			ir = handleIntent(intent, node.getMethod().getReference(), node);
		} catch (CancelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		log.debug("\n\n");

		return ir;
	}

	private IR handleIntent(Intent intent, MethodReference method, CGNode node) throws CancelException {
		IClassHierarchy cha = analysis.getClassHierarchy();
		IntentType type = intent.getType(cha);
		log.debug("type: " + type);
		StartInfo info = null;
		MultipleMicroModel model = null;
		IR returnIR = null;
		switch (type) {
		case INTERNAL_TARGET:
			info = starters.getInfo(method);
			model = new MultipleMicroModel(cha, options, cache, intent);
			SummarizedMethod summ;
			//summ = model.getMethod();
			summ = model.getStarterSummary(node.getMethod());
			// System.out.println(summ.makeIR(node.getContext(),
			// options.getSSAOptions()));
			returnIR = summ.makeIR(node.getContext(), options.getSSAOptions());
			
			break;
		case EXTERNAL_TARGET:

			break;
		case SYSTEM_SERVICE:

			break;
			
		case STANDARD_ACTION:
			
			break;
		case UNKNOWN_TARGET:

			break;
		default:
			Assertions.UNREACHABLE("Unknown type: " + type);
		}

		if (info == null || model == null) {
			returnIR = interpreter.getIR(node);
		}
		// MethodReference nodeMethod = node.getMethod().getReference();
		// SummarizedMethod override = model.getMethodAs(method,
		// method.getDeclaringClass(), info, node);
		// return override.makeIR(node.getContext(), options.getSSAOptions());
		return returnIR;
	}

	private IntentSet getParams(IR ir, SSAAbstractInvokeInstruction inst) {
		int use = IntentSet.getPositionOfIntentParam(inst);
		int vn = inst.getUse(use);
		if (ir.getSymbolTable().isParameter(vn)) {
			int[] valueNumbers = ir.getSymbolTable().getParameterValueNumbers();
			int pos = 0;
			for (int i = 0; i < valueNumbers.length; i++) {
				if (vn == valueNumbers[i]) {
					pos = ir.getMethod().isStatic() ? i + 1 : i;
					break;
				}
			}
			return analysis.getSolvedMethod(ir.getMethod().getReference()).getParameterIntent(pos);
		}
		return null;
	}

	@Override
	public DefUse getDU(CGNode node) {
		assert understands(node);
		return new DefUse(getIR(node));
	}

	@Override
	public int getNumberOfStatements(CGNode node) {
		assert understands(node);
		return getIR(node).getInstructions().length;
	}

	@Override
	public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(CGNode n) {
		assert understands(n);
		return getIR(n).getControlFlowGraph();
	}

}
