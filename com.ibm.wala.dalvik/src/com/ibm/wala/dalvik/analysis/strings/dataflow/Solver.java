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

import com.ibm.wala.dalvik.analysis.strings.providers.Superprovider;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.dataflow.graph.DataflowSolver;
import com.ibm.wala.dataflow.graph.IKilldallFramework;
import com.ibm.wala.ssa.ISSABasicBlock;


public class Solver extends DataflowSolver<ISSABasicBlock, NodeVariable> {
	
	private Superprovider provider;
	

	public Solver(IKilldallFramework<ISSABasicBlock, NodeVariable> problem, Superprovider provider) {
		super(problem);
		this.provider = provider;
	}

	@Override
	protected NodeVariable makeNodeVariable(ISSABasicBlock n, boolean IN) {
		return new NodeVariable(provider);   
	}

	@Override
	protected NodeVariable makeEdgeVariable(ISSABasicBlock src, ISSABasicBlock dst) {
		return new NodeVariable(provider);
	}

	@Override
	protected NodeVariable[] makeStmtRHS(int size) {
		return new NodeVariable[size];
	}
	
	

}
