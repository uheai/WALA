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

import java.util.HashMap;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.analysis.strings.main.StringAnalysis;
import com.ibm.wala.dalvik.analysis.strings.results.Intent;
import com.ibm.wala.dalvik.analysis.strings.util.IntentSet;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.util.intset.IntSet;

public class CtxSelector implements com.ibm.wala.ipa.callgraph.ContextSelector {

	private ContextSelector deleg;
	private IntentStarters starters;
	private StringAnalysis analysis;

	public CtxSelector(ContextSelector deleg, IClassHierarchy cha, StringAnalysis analysis) {
		this.deleg = deleg;
		starters = new IntentStarters(cha);
		this.analysis = analysis;
	}

	@Override
	public Context getCalleeTarget(CGNode caller, CallSiteReference site, IMethod callee,
			InstanceKey[] actualParameters) {
		// get context to which we add our information
		Context context = deleg.getCalleeTarget(caller, site, callee, actualParameters);
		if (!starters.isStarter(callee.getReference())) {
			return context;
		}
		IR ir = caller.getIR();
		SSAAbstractInvokeInstruction call = ir.getCalls(site)[0]; // take
																				// first
																				// entry
		// get intent
		IntentSet intents = analysis.getSolvedIntents();
		Intent intent = intents.getIntent(call);
		if (intent != null) {
			//System.out.println(
			//		"caller: " + caller + "\nsite: " + site + "\ncallee: " + callee + "\nintent: " + intent + "\n\n");
			IntentCtx intentContext = new IntentCtx(context, intent);
			//map.put(site, intentContext);
			return intentContext;
		} else {
			//TODO: check whether intent is a parameter of this method 
			analysis.getSolvedMethod(callee.getReference());
		}
		return context;
	}

	@Override
	public IntSet getRelevantParameters(CGNode caller, CallSiteReference site) {
		// TODO: implement, currently just delegating
		return deleg.getRelevantParameters(caller, site);
	}

}
