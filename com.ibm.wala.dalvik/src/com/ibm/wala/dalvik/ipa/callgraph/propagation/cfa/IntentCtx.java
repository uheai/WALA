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

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.analysis.strings.results.Intent;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextItem.Value;
import com.ibm.wala.ipa.callgraph.ContextKey;

public class IntentCtx implements Context {

	public static final ContextKey KEY_INTENT = new ContextKey() {};
	//public static final ContextKey KEY_STARTER = new ContextKey() {};
	
	private Context basis;
	private Intent intent;
	//private IMethod starter;
	
	public IntentCtx(Context basis, Intent intent) {
		this.basis = basis;
		this.intent = intent;
	}

	@Override
	public ContextItem get(ContextKey name) {
		if (name == null) {
			throw new IllegalArgumentException("name is null");
		}
		if (name == KEY_INTENT) {
			return new Value<Intent>(intent);
		}
		/*if (name == KEY_STARTER) {
			return new Value<IMethod>(starter);
		}*/
		return (basis == null ? null : basis.get(name));
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		IntentCtx ic = (IntentCtx) obj;
		return (this.basis.equals(ic.basis) && this.intent.equals(ic.intent));
	}
	
	@Override
	public int hashCode() {
		return basis.hashCode() * intent.hashCode();
	}
	
	@Override
	public String toString() {
		if (intent != null) {
			return "target: " + intent.getClassString();
		}
		return "Intent: null " + basis.toString();
	}

}
