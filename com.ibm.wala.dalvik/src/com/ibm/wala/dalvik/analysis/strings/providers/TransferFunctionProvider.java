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
package com.ibm.wala.dalvik.analysis.strings.providers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.dalvik.analysis.strings.dataflow.MeetOperator;
import com.ibm.wala.dalvik.analysis.strings.dataflow.SSATransferfunctionFactory;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Composition;
import com.ibm.wala.dalvik.analysis.strings.transferfunctions.Identity;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.dataflow.graph.AbstractMeetOperator;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;


public class TransferFunctionProvider implements ITransferFunctionProvider<ISSABasicBlock, NodeVariable> {
	
	private static Logger log = LoggerFactory.getLogger(TransferFunctionProvider.class);
	
	private SSATransferfunctionFactory factory;
	
	private MeetOperator meet;
	
	public TransferFunctionProvider(IR ir, AnalysisCache cache) {
		factory = new SSATransferfunctionFactory(ir, cache);
		meet = new MeetOperator();
	}

	@Override
	public UnaryOperator<NodeVariable> getNodeTransferFunction(ISSABasicBlock node) {
		if (node.isEntryBlock() || node.isExitBlock()) {
			return new Identity();
		}
		//System.out.println(node + "\n");
		return makeTransferfunctionForBlock(node);
	}

	@Override
	public boolean hasNodeTransferFunctions() {
		return true;
	}

	@Override
	public UnaryOperator<NodeVariable> getEdgeTransferFunction(ISSABasicBlock src, ISSABasicBlock dst) {
		//not needed
		return null;
	}

	@Override
	public boolean hasEdgeTransferFunctions() {
		// currently not
		return false;
	}

	@Override
	public AbstractMeetOperator<NodeVariable> getMeetOperator() {
		return meet;
	}
	
	/**
	 * returns transferfunction for this block as composition of transfer-functions of every instruction
	 * in it
	 * @param block block to get transferfunction of
	 * @return transferfunction for this block
	 */
	private Composition makeTransferfunctionForBlock(ISSABasicBlock block) {
		List<UnaryOperator<NodeVariable>> functions = new ArrayList<UnaryOperator<NodeVariable>>();
		functions.add(new Identity()); //identity has to be part of the transferfunction
										//it contains the meet function
		log.debug("creating transferfunction for block \n\n" + block);
		for (Iterator<SSAInstruction> it = block.iterator(); it.hasNext(); ) {
			SSAInstruction inst = it.next();
			log.debug("instruction: " + inst);
			UnaryOperator<NodeVariable> transferfunction = factory.getTransferFunction(inst);
			if (transferfunction.isIdentity()) {
				continue;
			}
			log.debug("added function " + transferfunction);
			functions.add(transferfunction);
		}
		return new Composition(functions);
	}

}
