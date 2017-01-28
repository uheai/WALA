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
package com.ibm.wala.dalvik.ipa.callgraph.androidModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.dalvik.analysis.strings.results.Intent;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.parameters.AndroidModelParameterManager;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.parameters.IInstantiationBehavior;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.parameters.IInstantiationBehavior.InstanceBehavior;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.parameters.Instantiator;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.parameters.ReuseParameters;
import com.ibm.wala.dalvik.ipa.callgraph.androidModel.structure.AbstractAndroidModel;
import com.ibm.wala.dalvik.ipa.callgraph.impl.AndroidEntryPoint;
import com.ibm.wala.dalvik.util.AndroidEntryPointManager;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.rta.CallSite;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.ipa.summaries.SummarizedMethodWithNames;
import com.ibm.wala.ipa.summaries.VolatileMethodSummary;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.ssa.ParameterAccessor;
import com.ibm.wala.util.ssa.SSAValue;
import com.ibm.wala.util.ssa.SSAValue.TypeKey;
import com.ibm.wala.util.ssa.SSAValue.VariableKey;
import com.ibm.wala.util.ssa.SSAValueManager;
import com.ibm.wala.util.ssa.TypeSafeInstructionFactory;
import com.ibm.wala.util.strings.Atom;

/**
 * A version of {@link MicroModel}, which can deal with multiple targets
 * 
 * @author maik
 *
 */
public class MultipleMicroModel {

	public static final Atom name = Atom.findOrCreateAsciiAtom("MultipleMicroModel");

	private Collection<Atom> targets;

	private AbstractAndroidModel labelSpecial;
	private IInstantiationBehavior behavior;
	private SSAValueManager paramManager;
	private ParameterAccessor modelAcc;
	// pc's of the instructions we added
	private Set<Integer> instPCs;

	private IClass klass;
	private IClassHierarchy cha;
	private AnalysisCache cache;
	private AnalysisOptions options;

	private MethodReference mRef;
	private SummarizedMethod model;
	private VolatileMethodSummary body;
	
	private SummarizedMethod starterSummary;

	//private boolean built;

	public MultipleMicroModel(IClassHierarchy cha, AnalysisOptions options, AnalysisCache cache, Intent intent) {
		// super(cha, options, cache);
		this.cha = cha;
		this.options = options;
		this.cache = cache;
		//built = false;
		Collection<String> classValues = intent.getClassVar().getValues();
		targets = new HashSet<Atom>(classValues.size());

		for (String klass : classValues) {
			targets.add(Atom.findOrCreateAsciiAtom(klass));
		}
		instPCs = new HashSet<Integer>();
		starterSummary = null;
	}

	private boolean selectEntryPoint(AndroidEntryPoint ep) {
		for (Atom target : targets) {
			if (ep.isMemberOf(target)) {
				return true;
			}
		}

		return false;
	}

	private void build(Atom name, Collection<? extends AndroidEntryPoint> entrypoints) throws CancelException {
		// register
		// TypeName typeName =
		// TypeName.findOrCreate(Atom.findOrCreateAsciiAtom("Lcom/ibm/wala"),
		// name, 0);
		// klass =
		// cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial,
		// typeName));
		klass = cha.lookupClass(AndroidModelClass.ANDROID_MODEL_CLASS);

		if (klass == null) {
			// add to cha
			klass = AndroidModelClass.getInstance(cha);
			cha.addClass(klass);
		}

		// create signature of method

		behavior = AndroidEntryPointManager.MANAGER.getInstantiationBehavior(cha);

		// get basic reference containing the parameters needed for the
		// entrypoints.
		// This is the basis to which other parameters will be added.
		mRef = getBasisReference(entrypoints);

		Selector sel = mRef.getSelector();
		TypeName[] mPars = sel.getDescriptor().getParameters();
		TypeName[] newPars = Arrays.copyOf(mPars, mPars.length + 1);

		// insert int parameter
		newPars[newPars.length - 1] = TypeReference.IntName;

		Descriptor mDesc = Descriptor.findOrCreate(newPars, TypeReference.VoidName);

		// update mRef
		mRef = MethodReference.findOrCreate(klass.getReference(), this.name, mDesc);

		modelAcc = new ParameterAccessor(mRef, false);
		paramManager = new SSAValueManager(modelAcc);

		// Add parameter to method which will be used by switch instruction
		int switch_param = mRef.getNumberOfParameters(); // switch parameter is
															// last parameter of
															// method

		final Selector selector = mRef.getSelector();

		// if method is already built, AndroidModelClass will contain it
		final AndroidModelClass androidCl = AndroidModelClass.getInstance(cha);

		if (androidCl.containsMethod(selector)) {
			model = (SummarizedMethod) androidCl.getMethod(selector);
			return;
		}

		body = new VolatileMethodSummary(new MethodSummary(mRef));
		
		body.setStatic(true);

		labelSpecial = AndroidEntryPointManager.MANAGER.makeModelBehavior(body, new TypeSafeInstructionFactory(cha),
				paramManager, entrypoints);

		populate(entrypoints, switch_param);

		body.setLocalNames(paramManager.makeLocalNames());
		model = new SummarizedMethodWithNames(mRef, body, klass) {

			@Override
			public TypeReference getParameterType(int i) {
				IClassHierarchy cha = getClassHierarchy();
				TypeReference tRef = super.getParameterType(i);

				if (tRef.isClassType()) {
					if (cha.lookupClass(tRef) != null) {
						return tRef;
					} else {
						for (IClass c : cha) {
							if (c.getName().toString().equals(tRef.getName().toString())) {
								return c.getReference();
							}
						}
					}
					return tRef;
				} else {
					return tRef;
				}
			}
		};

		//built = true;
	}

	private void populate(Collection<? extends AndroidEntryPoint> entries, int switch_param) {

		// boolean enteredSection = false;

		final TypeSafeInstructionFactory tsif = new TypeSafeInstructionFactory(cha);
		final Instantiator instantiator = new Instantiator(body, tsif, paramManager, cha, mRef,
				options.getAnalysisScope());

		body.reserveProgramCounters(1); // for switch instruction

		for (AndroidEntryPoint ep : entries) {

			// collect parameters
			List<List<SSAValue>> paramses = new ArrayList<List<SSAValue>>(1);
			List<Integer> multiTypePositions = new ArrayList<Integer>();
			final List<SSAValue> params = new ArrayList<SSAValue>(ep.getNumberOfParameters());
			paramses.add(params);

			for (int i = 0; i < ep.getNumberOfParameters(); i++) {

				// enteredSection |= needSpecialHandling(ep);

				if (ep.getParameterTypes(i).length != 1) {
					multiTypePositions.add(i);
					params.add(null); // set later
				} else {
					for (final TypeReference type : ep.getParameterTypes(i)) {
						if (behavior.getBehavior(type.getName(), ep.getMethod(), null) == InstanceBehavior.REUSE) {
							params.add(paramManager.getCurrent(new TypeKey(type.getName())));
						} else if (type.isPrimitiveType()) {
							params.add(paramManager.getUnmanaged(type, "p"));
						} else {
							final boolean asManaged = false;
							final VariableKey key = null;
							final Set<SSAValue> seen = null;
							params.add(instantiator.createInstance(type, asManaged, key, seen));
						}
					}
				}
			}

			// build cartesian product for multiTypePositions
			for (int pos = 0; pos < multiTypePositions.size(); pos++) {
				final Integer multiTypePosition = multiTypePositions.get(pos);
				final TypeReference[] typesOnPosition = ep.getParameterTypes(multiTypePosition);
				final int typeCountOnPosition = typesOnPosition.length;

				final List<List<SSAValue>> new_paramses = new ArrayList<List<SSAValue>>(paramses.size() * typeCountOnPosition);

				for (int i = 0; i < typeCountOnPosition; i++) {
					for (final List<SSAValue> p : paramses) {
						final List<SSAValue> new_params = new ArrayList<SSAValue>(p.size());
						new_params.addAll(p);
						new_paramses.add(new_params);
					}
				}
				paramses = new_paramses;

				// set current multiTypePosition
				for (int i = 0; i < paramses.size(); i++) {
					final List<SSAValue> pars = paramses.get(i);
					final TypeReference type = typesOnPosition[(i * (pos + 1)) % typeCountOnPosition];

					if (behavior.getBehavior(type.getName(), ep.getMethod(), null) == InstanceBehavior.REUSE) {
						pars.set(multiTypePosition, paramManager.getCurrent(new TypeKey(type.getName())));
					} else if (type.isPrimitiveType()) {
						pars.set(multiTypePosition, paramManager.getUnmanaged(type, "p"));
					} else {
						final boolean asManaged = false;
						final VariableKey key = null;
						final Set<SSAValue> seen = null;
						pars.set(multiTypePosition, instantiator.createInstance(type, asManaged, key, seen));
					}
				}
			}
			// pc for switch instruction
			// int switchPC = body.getNextProgramCounter(); //switchPC is 0
			// body.reserveProgramCounters(1); // reserve for switch instruction
			insertCalls(ep, paramses, tsif);
			// addSwitchInstruction(0, switch_param);
		}

		int afterSwitch = addSwitchInstruction(0, switch_param);

		// add return statement as final statement of method
		SSAReturnInstruction retInst = tsif.ReturnInstruction(afterSwitch);
		body.addStatement(retInst);

		// close all sections by "jumping over" the remaining labels
		// if (enteredSection) {
		// labelSpecial.finish(body.getNextProgramCounter());
		// }
	}

	private void insertCalls(AndroidEntryPoint ep, List<List<SSAValue>> paramses, TypeSafeInstructionFactory tsif) {
		for (List<SSAValue> params : paramses) {
			final int callPC = body.getNextProgramCounter();
			final CallSiteReference site = ep.makeSite(callPC);
			final SSAAbstractInvokeInstruction invocation;
			final SSAValue exception = paramManager.getException();

			// remember current pc, as this marks the entry point for a switch
			// case
			instPCs.add(callPC);

			if (ep.getMethod().getReturnType().equals(TypeReference.Void)) {
				invocation = tsif.InvokeInstruction(callPC, params, exception, site);
				body.addStatement(invocation);
				// reserve one pc for goto instruction (break after switch case)
				body.reserveProgramCounters(1);
			} else {
				// add return instruction like in AndroidModel.populate
				TypeReference returnType = ep.getMethod().getReturnType();
				TypeKey returnKey = new TypeKey(returnType.getName());

				if (/*paramManager.isSeen(returnKey)*/ false) {
					SSAValue oldValue = paramManager.getCurrent(returnKey);
					paramManager.invalidate(returnKey);
					SSAValue retunValue = paramManager.getUnallocated(returnType, returnKey);

					invocation = tsif.InvokeInstruction(callPC, retunValue, params, exception, site);
					body.addStatement(invocation);
					paramManager.setAllocation(retunValue, invocation);

					// phi instruction
					paramManager.invalidate(returnKey);
					SSAValue newValue = paramManager.getFree(returnType, returnKey);
					int phiPC = body.getNextProgramCounter();
					List<SSAValue> toPhi = new ArrayList<SSAValue>(2);
					toPhi.add(oldValue);
					toPhi.add(retunValue);
					SSAPhiInstruction phi = tsif.PhiInstruction(phiPC, newValue, toPhi);
					body.addStatement(phi);
					// reserve next pc
					body.reserveProgramCounters(1);
					paramManager.setPhi(newValue, phi);
				} else {
					// ignore return value
					SSAValue returnValue = paramManager.getUnmanaged(returnType, new SSAValue.UniqueKey());
					invocation = tsif.InvokeInstruction(callPC, returnValue, params, exception, site);
					body.addStatement(invocation);
					// reserve next pc
					body.reserveProgramCounters(1);
				}
			}
		}

	}

	/**
	 * 
	 * @param pc
	 *            program counter of switch instruction
	 * @param param
	 *            parameter of switch instruction
	 * @return the pc belonging to the instruction after the switch instruction,
	 *         i.e. at the end of each case there is a Goto-Instruction
	 *         targeting this pc (break). This pc has no instruction though.
	 */
	private int addSwitchInstruction(int pc, int param) {
		ArrayList<Integer> casesAndLabels = new ArrayList<Integer>();
		int afterSwitch = body.getNextProgramCounter(); // where to continue
		body.allowReserved(true); // allow modification of reserved pc's
		int caseIndex = 0;
		int label = 1; //first label is first instruction
		for (Iterator<Integer> labels = instPCs.iterator(); labels.hasNext();) {
			// add case
			casesAndLabels.add(caseIndex);
			// add label
			casesAndLabels.add(label);
			
			label = labels.next();
			//casesAndLabels.add(label);
			// increment caseIndex
			caseIndex++;
			// add goto
			int gotoIndex = label + 1; //add goto after invoke instruction
			
			/*if (body.getStatementAt(label + 1) != null) {
				// this is a phi instruction, add goto after it
				gotoIndex++;
			}*/
			
			// for debugging purposes, should never happen
			if (body.getStatementAt(gotoIndex) != null) {
				Assertions.UNREACHABLE(
						"statement: " + gotoIndex + " - " + body.getStatementAt(gotoIndex) + "is not null");
			}
			// add goto instruction
			SSAGotoInstruction gotoInst = new SSAGotoInstruction(gotoIndex, afterSwitch);
			body.addStatement(gotoInst);
			
			//next label leads to instruction following the goto instruction
			label = gotoIndex + 1;
		}
		// add switch instruction
		int[] cAndL = new int[casesAndLabels.size()];
		for (int i = 0; i < casesAndLabels.size(); i++) {
			cAndL[i] = casesAndLabels.get(i);
		}
		
		SSASwitchInstruction switchInst = new SSASwitchInstruction(pc, param, 0, cAndL);
		body.addStatement(switchInst);

		return afterSwitch;
	}

	private boolean needSpecialHandling(AndroidEntryPoint ep) {

		if (labelSpecial.hadSectionSwitch(ep.order)) {
			labelSpecial.enter(ep.getSection(), body.getNextProgramCounter());
			return true;
		}

		return false;
	}

	/**
	 * returns MethodReference which contains all parameters needed for the
	 * entrypoints. Will be used as basis for other MethodReferences.
	 * 
	 * @param entrypoints
	 * @return MethodReference with entrypoints-parameters
	 */
	private MethodReference getBasisReference(Iterable<? extends Entrypoint> entrypoints) {
		// collect parameters
		List<TypeName> parameters = new ArrayList<TypeName>();

		for (Entrypoint ep : entrypoints) {
			int paramCount = ep.getNumberOfParameters();

			for (int i = 0; i < paramCount; i++) {
				TypeReference[] types = ep.getParameterTypes(i);
				if (types.length < 1) {
					throw new IllegalStateException("Entrypoint " + ep + " returns no types for " + i + "th parameter");
				}
				// TODO: check this part, currently copied from
				// ReuseParameters.collectParameters(...)
				// maybe could be done better
				for (int j = 0; j < types.length; j++) {
					TypeName paramType = types[j].getName();

					if (behavior.getBehavior(paramType, null, null) == InstanceBehavior.REUSE) {
						if (!parameters.contains(paramType)) {
							parameters.add(paramType);
						}
					}
				}
			}
		}

		// creating method reference
		TypeName[] types = parameters.toArray(new TypeName[parameters.size()]);
		Descriptor desc = Descriptor.findOrCreate(types, TypeReference.VoidName);

		return MethodReference.findOrCreate(klass.getReference(), name, desc);
	}

	public Atom getName() {
		return name;
	}

	public SummarizedMethod getMethod() throws CancelException {
		if (model == null) {
			// restrict entry points
			List<AndroidEntryPoint> restrictedEntries = new ArrayList<AndroidEntryPoint>();
			for (AndroidEntryPoint ep : AndroidEntryPointManager.ENTRIES) {
				if (selectEntryPoint(ep)) {
					restrictedEntries.add(ep);
				}
			}

			build(name, restrictedEntries);
			// register
			if (!((AndroidModelClass) klass).containsMethod(model.getSelector())) {
				((AndroidModelClass) klass).addMethod(model);
			}

		}

		return model;
	}
	
	public SummarizedMethod getStarterSummary(IMethod starter) throws CancelException {
		if (starterSummary != null) {
			return starterSummary;
		}
		if (model == null) {
			getMethod();
		}
		//create instanciator to get parameters needed
		MethodReference starterRef = starter.getReference();
		TypeSafeInstructionFactory tsif = new TypeSafeInstructionFactory(cha);
		VolatileMethodSummary starterBody = new VolatileMethodSummary(new MethodSummary(starterRef));
		ParameterAccessor acc = new ParameterAccessor(starter);
		SSAValueManager pm = new SSAValueManager(acc);
		
		Instantiator instantiator = new Instantiator(starterBody, tsif, pm, cha, starterRef, cha.getScope());
		
		//create parameters
		List<SSAValue> params = new ArrayList<SSAValue>(model.getNumberOfParameters());
		
		for (int i = 0; i < model.getNumberOfParameters(); i++) {
			TypeReference paramType = model.getParameterType(i);
			int pc = starterBody.getNextProgramCounter();
			//calling createInstance also add instructions to body
			SSAValue instance = instantiator.createInstance(paramType, false, null, null);
			params.add(instance);
		}
		
		//add invoke instruction
		int iindex = starterBody.getNextProgramCounter();
		SSAValue exception = pm.getException();
		CallSiteReference site = CallSiteReference.make(iindex, model.getReference(), IInvokeInstruction.Dispatch.STATIC);
		SSAInvokeInstruction invoke = tsif.InvokeInstruction(iindex, params, exception, site);
		starterBody.addStatement(invoke);
		
		//create ir
		starterBody.setLocalNames(pm.makeLocalNames());
		starterSummary = new SummarizedMethodWithNames(starterRef, starterBody, starter.getDeclaringClass());
		
		return starterSummary;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj instanceof MultipleMicroModel) {
			MultipleMicroModel other = (MultipleMicroModel) obj;
			return this.targets.equals(other.targets);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return targets.hashCode() * options.hashCode();
	}
}
