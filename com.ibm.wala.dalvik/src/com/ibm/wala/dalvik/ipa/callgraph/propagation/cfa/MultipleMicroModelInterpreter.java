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

import java.util.Iterator;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.CodeScanner;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.AndroidModelClass;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.MultipleMicroModel;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.FieldReference;

public class MultipleMicroModelInterpreter implements SSAContextInterpreter {

	private AndroidModelClass klass;

	private AnalysisOptions options;

	public MultipleMicroModelInterpreter(IClassHierarchy cha, AnalysisOptions options) {
		IClass cl = cha.lookupClass(AndroidModelClass.ANDROID_MODEL_CLASS);

		if (cl == null) {
			klass = AndroidModelClass.getInstance(cha);
			cha.addClass(klass);
		} else {
			klass = (AndroidModelClass) cl;
		}

		this.options = options;
	}

	@Override
	public Iterator<NewSiteReference> iterateNewSites(CGNode node) {
		assert understands(node);

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
		return false;
	}

	@Override
	public boolean understands(CGNode node) {
		if (node == null) {
			throw new IllegalArgumentException("node is null");
		}
		IMethod method = node.getMethod();
		if (method.toString().contains(MultipleMicroModel.name.toString())) {
			return klass.containsMethod(method.getSelector());
		}
		return false;
	}

	@Override
	public Iterator<CallSiteReference> iterateCallSites(CGNode node) {
		assert understands(node);
		return getIR(node).iterateCallSites();
	}

	@Override
	public IR getIR(CGNode node) {
		SummarizedMethod method = (SummarizedMethod) klass.getMethod(node.getMethod().getSelector());
		IR ir = method.makeIR(node.getContext(), options.getSSAOptions());
		return ir;
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
