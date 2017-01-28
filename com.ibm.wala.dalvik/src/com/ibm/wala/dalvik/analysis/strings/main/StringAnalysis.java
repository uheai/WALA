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
package com.ibm.wala.dalvik.analysis.strings.main;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.analysis.strings.dataflow.KilldallFramework;
import com.ibm.wala.dalvik.analysis.strings.dataflow.PiNodePolicy;
import com.ibm.wala.dalvik.analysis.strings.dataflow.Solver;
import com.ibm.wala.dalvik.analysis.strings.providers.FieldVariableProvider;
import com.ibm.wala.dalvik.analysis.strings.providers.MethodVariableProvider;
import com.ibm.wala.dalvik.analysis.strings.providers.Superprovider;
import com.ibm.wala.dalvik.analysis.strings.results.Method;
import com.ibm.wala.dalvik.analysis.strings.results.Variable;
import com.ibm.wala.dalvik.analysis.strings.util.IntentSet;
import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldVariable;
import com.ibm.wala.dalvik.analysis.strings.variables.MethodVariable;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;

/**
 * entry point of analysis and user interface
 * 
 * @author maik
 *
 */
public class StringAnalysis {

	private static Logger log = LoggerFactory.getLogger(StringAnalysis.class);

	private IClassHierarchy cha;

	private AnalysisCache cache;

	private Superprovider provider;

	private SSAOptions options;

	public StringAnalysis(IClassHierarchy cha) {
		this.cha = cha;
		cache = new AnalysisCache();
		options = new SSAOptions();
		options.setPiNodePolicy(new PiNodePolicy());
		provider = new Superprovider(cha, cache, options);
	}

	public StringAnalysis(File apk) throws IOException, ClassHierarchyException {
		if (!apk.exists() || !apk.getPath().endsWith("apk")) {
			throw new IllegalArgumentException("not a valid apk file");
		}
		AnalysisScope scope = Util.makeAndroidScope(apk);
		cha = ClassHierarchy.make(scope);
		cache = new AnalysisCache();
		options = new SSAOptions();
		options.setPiNodePolicy(new PiNodePolicy());
		provider = new Superprovider(cha, cache, options);
	}

	public StringAnalysis(CallGraph cg) {
		cha = cg.getClassHierarchy();
		cache = new AnalysisCache();
		// don't need to set options, because cg already took care of it

		// options = new SSAOptions();
		// options.setPiNodePolicy(new PiNodePolicy());

		provider = new Superprovider(cg);
	}

	/**
	 * adds fields and class initializers of primordial classes to scope
	 */
	public void addPrimordialFieldsAndClassInits() {
		provider.addPrimordialFieldsAndClassInit();
	}

	/**
	 * adds fields to scope
	 * 
	 * @see FieldVariableProvider#makeFieldVariables(Collection)
	 * @param fields
	 *            fields to be added to scope
	 */
	public void addFields(Collection<IField> fields) {
		provider.getFieldProvider().makeFieldVariables(fields);
	}

	/**
	 * adds methods to scope
	 * 
	 * @see MethodVariableProvider#makeMethodVariables(Collection)
	 * @param methods
	 *            methods to be added to scope
	 */
	public void addMethods(Collection<IMethod> methods) {
		provider.getMethodPovider().makeMethodVariables(methods);
	}

	/**
	 * build equation system. Configuration has to be finished before calling
	 * this method
	 * 
	 * @throws CancelException
	 */
	public void runAnalysis() throws CancelException {
		for (MethodVariable mVar : provider.getAllMethods()) {
			analyzeMethod(mVar);
		}
		// log.info(provider.toString());
		// log.info(provider.getFieldString());
		// log.info(provider.getMethodString());
		// log.info(provider.getIntentString());
	}

	/**
	 * solves whole equation system (containing all application classes in scope
	 * and own addition, like Android classes). For very big equation systems
	 * (lot of classes) much space will be needed.
	 */
	public void solve() {
		provider.solve();
	}

	/**
	 * solve equation system for given methods and fields
	 * 
	 * @param fRefHotspots
	 *            fields to analyze (may be null)
	 * @param mRefHotspots
	 *            methods to analyze (may be null)
	 */
	public void solveHotspots(Collection<FieldReference> fRefHotspots, Collection<MethodReference> mRefHotspots) {
		// get corresponding field and method variables
		Collection<FieldVariable> fieldHotspots = null;
		Collection<MethodVariable> methodHotspots = null;
		// get field variables
		if (fRefHotspots != null) {
			fieldHotspots = new HashSet<FieldVariable>();
			for (FieldReference fRef : fRefHotspots) {
				FieldVariable fVar = provider.getFieldVariable(fRef);
				fieldHotspots.add(fVar);
			}
		}
		// get method variables
		if (mRefHotspots != null) {
			methodHotspots = new HashSet<MethodVariable>();
			for (MethodReference mRef : mRefHotspots) {
				MethodVariable mVar = provider.getMethodVariable(mRef);
				methodHotspots.add(mVar);
			}
		}
		// solve Hotspots
		provider.solveHotspots(methodHotspots, fieldHotspots);
	}

	/**
	 * solve equation system just for intents. Has same effect as calling
	 * {@link #solveHotspots(Collection, Collection)} with null arguments
	 */
	public void solveIntentsOnly() {
		solveHotspots(null, null);
	}

	/**
	 * After equation system is built, this method returns all found analyzable
	 * methods. This methods may occur as hotspots
	 * 
	 * @return analyzable methods.
	 */
	public Collection<MethodReference> getAnalyzableMethods() {
		return provider.getMethodPovider().getAllMethodReferences();
	}

	/**
	 * Solve equation system before calling this! Caller has to make sure that
	 * equation system has been solved (by one of the solve methods) for this
	 * parameter. If this method is called without the equation system being
	 * solved already or with a parameter not marked as hotspot the returned
	 * value can contain anything.
	 * 
	 * @param ref
	 *            method to get result of. Make sure you either solved the whole
	 *            equation system or you solved it with ref as hotspot.
	 *            Otherwise the result can be anything.
	 * @return result for this method. Be aware that this method creates a new
	 *         instance of {@link Method} which will not be cached.
	 * @throws IllegalArgumentException
	 *             in case this method is unknown, e.g. not in ClassHierarchy or
	 *             excluded
	 */
	public Method getSolvedMethod(MethodReference ref) throws IllegalArgumentException {
		MethodVariable mVar = provider.getMethodVariable(ref);
		if (mVar.isUnknown()) {
			throw new IllegalArgumentException("unnown method: " + mVar.getName());
		}
		return new Method(mVar);
	}

	/**
	 * Solve equation system before calling this! Caller has to make sure that
	 * equation system has been solved (by one of the solve methods) for this
	 * parameter. If this method is called without the equation system being
	 * solved already or with a parameter not marked as hotspot the returned
	 * value can contain anything.
	 * 
	 * @param ref
	 *            field to get result of. Make sure you either solved the whole
	 *            equation system or you solved it with ref as hotspot.
	 *            Otherwise the result can be anything.
	 * @return result for this field. Be aware that this method creates a new
	 *         instance of {@link Variable} which will not be cached.
	 * @throws IllegalArgumentException
	 *             in case this field is unknown
	 */
	public Variable getSolvedField(FieldReference ref) throws IllegalArgumentException {
		FieldVariable fVar = provider.getFieldVariable(ref);
		if (fVar == null) {
			throw new IllegalArgumentException("unknown field: " + fVar.getName());
		}
		return new Variable(fVar);
	}

	/**
	 * Solve equation system before calling this! Caller has to make sure that
	 * equation system has been solved (by one of the solve methods). Do not
	 * worry about hotspots, intents are solved anyway. If this method is called
	 * without the equations system being solved the result can contain
	 * anything.
	 * 
	 * @return set, containing all found intents. Be aware that the result will
	 *         not be cached.
	 */
	public IntentSet getSolvedIntents() {
		return new IntentSet(provider.getIntents());
	}

	/**
	 * 
	 * @return class hierarchy used
	 */
	public IClassHierarchy getClassHierarchy() {
		return cha;
	}

	/**
	 * Number of classes used for analysis. This contains all application
	 * classes of the used class hierarchy and additional classes added by user.
	 * 
	 * @return
	 */
	public int getNumberOfAnalyzedClasses() {
		return provider.getNumOfClasses();
	}

	/**
	 * Calculates total number of bytecode instructions
	 * 
	 * @see MethodVariableProvider#getTotalNumberOfInstructions()
	 * @return total number of bytecode instructions
	 */
	public int getNumberOfInstructions() {
		return provider.getMethodPovider().getTotalNumberOfInstructions();
	}

	/**
	 * Get total number of equation. Equations system has to be build before
	 * calling this method.
	 * 
	 * @return number of equations
	 */
	public int getNumberOfEquations() {
		return provider.getNumberOfEquations();
	}

	/**
	 * Number of all analyzed methods
	 * 
	 * @return number of methods
	 */
	public int getNumberOfAnalyzedMethods() {
		return provider.getAllMethods().size();
	}

	public int getNumberOfFields() {
		return provider.getFieldProvider().size();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(provider.toString());
		sb.append(provider.getFieldString());
		sb.append(provider.getMethodString());
		sb.append(provider.getIntentString());
		return sb.toString();
	}

	/**
	 * add this method to global equation system
	 * 
	 * @param mVar
	 * @throws CancelException
	 */
	private void analyzeMethod(MethodVariable mVar) throws CancelException {
		IR ir = mVar.getIR();
		KilldallFramework framework = new KilldallFramework(mVar.getIR(), cache);
		Solver solver = new Solver(framework, provider);
		solver.solve(null);
	}

}
