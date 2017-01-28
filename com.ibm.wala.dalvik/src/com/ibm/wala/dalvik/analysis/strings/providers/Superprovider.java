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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldVariable;
import com.ibm.wala.dalvik.analysis.strings.variables.IntentVar;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.MethodVariable;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;


public class Superprovider {

	private static Logger log = LoggerFactory.getLogger(Superprovider.class);

	private IClassHierarchy cha;

	private int numOfClasses;

	private FieldVariableProvider fields;

	private MethodVariableProvider methods;

	private Collection<IntentVar> intents;

	/**
	 * contains variables l for which the result value is l*
	 */
	private Collection<FieldOrLocal> loops;

	/**
	 * map from piNode to corresponding variable, e.g for v17 = pi(v18) there's
	 * a mapping 17 -> 18
	 */
	private HashMap<FieldOrLocal, FieldOrLocal> piNodes;

	public Superprovider(IClassHierarchy cha, AnalysisCache cache, SSAOptions options) {
		fields = new FieldVariableProvider();
		methods = new MethodVariableProvider(cache, options);
		this.piNodes = new HashMap<FieldOrLocal, FieldOrLocal>();
		this.cha = cha;
		// this.cache = cache;
		loops = new HashSet<FieldOrLocal>();
		intents = new HashSet<IntentVar>();
		init();
	}

	public Superprovider(CallGraph cg) {
		fields = new FieldVariableProvider();
		methods = new MethodVariableProvider();
		piNodes = new HashMap<FieldOrLocal, FieldOrLocal>();
		cha = cg.getClassHierarchy();
		loops = new HashSet<FieldOrLocal>();
		intents = new HashSet<IntentVar>();
		initCallgraph(cg);
	}

	private void init() {
		numOfClasses += cha.getLoader(ClassLoaderReference.Application).getNumberOfClasses();
		for (Iterator<IClass> classIt = cha.getLoader(ClassLoaderReference.Application).iterateAllClasses(); classIt
				.hasNext();) {
			IClass c = classIt.next();
			fields.makeFieldVariables(c);
			methods.makeMethodVariables(c);
		}
	}

	private void initCallgraph(CallGraph cg) {
		//create fields
		for (Iterator<IClass> classIt = cha.getLoader(ClassLoaderReference.Application).iterateAllClasses(); classIt.hasNext();) {
			IClass c = classIt.next();
			fields.makeFieldVariables(c);
		}
		//handle methods
		for (CGNode node : cg) {
			if (!node.getMethod().getDeclaringClass().getClassLoader().getReference()
					.equals(ClassLoaderReference.Application)) {
				continue; //ingore method if it is not in application scope
			}
			IR ir = node.getIR();
			if (ir == null) {
				continue; //there is no ir
			}
			methods.makeMethodVariable(ir);
		}
	}

	/**
	 * add primordial fields and class initilaizers to scope
	 */
	public void addPrimordialFieldsAndClassInit() {
		numOfClasses += cha.getLoader(ClassLoaderReference.Primordial).getNumberOfClasses();
		for (Iterator<IClass> classIt = cha.getLoader(ClassLoaderReference.Primordial).iterateAllClasses(); classIt
				.hasNext();) {
			IClass c = classIt.next();
			fields.makeFieldVariables(c);
			IMethod init = c.getClassInitializer();
			if (init != null) {
				methods.makeMethodVariable(init);
			}
		}
	}

	/**
	 * solve equation system, e.g every method, every field, intents and other
	 * variables
	 */
	public void solve() {
		log.debug("Solving Main - First");
		solveMain();
		log.debug("Solving Pis - First");
		solvePis();
		log.debug("Solving Lopps");
		solveLoops();
		log.debug("Solving Pis - Second");
		solvePis();
		log.debug("Solving Intents");
		solveIntents();
		log.debug("Solving Main - Second");
		solveMain();
	}

	/**
	 * solve equation system for given methods and fields. Intents and other
	 * variables are solved either way.
	 * 
	 * @param methodHotspots
	 *            methods to analyze (may be null)
	 * @param fieldHotspots
	 *            fields to analyze (may be null)
	 */
	public void solveHotspots(Collection<MethodVariable> methodHotspots, Collection<FieldVariable> fieldHotspots) {
		log.debug("Solving Main - First");
		solveMain(methodHotspots, fieldHotspots);
		log.debug("Solving Pis - First");
		solvePis();
		log.debug("Solving loops");
		solveLoops();
		log.debug("Solving Pis - Second");
		solvePis();
		log.debug("Solving Intents");
		solveIntents();
		log.debug("Solving Main - Second");
		solveMain(methodHotspots, fieldHotspots);
	}

	/**
	 * solve all methods and fields
	 */
	private void solveMain() {
		log.debug("running solveMain()");
		solveMain(methods.getAllVariables(), fields.getAllFieldVariables());
	}

	/**
	 * solve given methods and fields
	 * 
	 * @param methodHotspots
	 *            methods
	 * @param fieldHotspots
	 *            fields
	 */
	private void solveMain(Collection<MethodVariable> methodHotspots, Collection<FieldVariable> fieldHotspots) {
		log.debug("running solveMain(hotspots)\n");
		boolean changed = false;
		boolean localChanged = false;
		do {
			changed = false;
			// solve fields
			if (fieldHotspots != null) {
				log.debug("solving fields...");
				localChanged = fields.resolveHotspots(fieldHotspots);
				if (localChanged) {
					changed = true;
				}
			}
			// solve methods
			if (methodHotspots != null) {
				log.debug("solving methods...");
				localChanged = methods.resolveHotspots(methodHotspots);
				if (localChanged) {
					changed = true;
				}
			}
		} while (changed);
		log.debug("\nsolveMain() completed");
	}

	/**
	 * for a chain of pi-nodes p_1 -> p_2 -> ... -> p_n the resulting string
	 * will be calculated, i.e. result of append calls
	 */
	private void solvePis() {
		/*
		 * for (FieldOrLocal pi : piNodes.keySet()) { pi.resolveConcats();
		 * piNodes.get(pi).addValues(pi); }
		 */
		log.debug("running solvePis()");
		for (FieldOrLocal pi : piNodes.keySet()) {
			log.debug("pi: " + pi);
			resolveChain(pi);
			// XXX
			// this is still an issue
			// if we solve x = pi(y), we need to add the values of x to y at
			// some point
			// otherwise we would lose those values in case y is a field
			// there would be problems if we just execute the following line
			// piNodes.get(pi).addUnresolvedVar(pi);
		}
	}

	/**
	 * get values of variables contained in loop
	 */
	private void solveLoops() {
		for (FieldOrLocal loop : loops) {
			loop.resolveLoops();
		}
	}

	/**
	 * for a chain of pi nodes p_1 -> p_2 -> ... -> p_n and a pi-node p_i the
	 * value of p_i will be calculated.
	 * 
	 * @param piNode
	 *            p_i
	 */
	private void resolveChain(FieldOrLocal piNode) {
		FieldOrLocal target = piNodes.get(piNode);
		if (piNodes.containsKey(target)) {
			resolveChain(target);
		}
		piNode.resolveConcats();
	}

	/**
	 * solve intent variables
	 */
	private void solveIntents() {
		boolean changed = false;
		do {
			changed = false;
			for (IntentVar var : intents) {
				changed |= var.resolve();
			}
		} while (changed);
	}

	/**
	 * @see MethodVariableProvider#addLocal(MethodReference, Local)
	 */
	public boolean addLocal(MethodReference ref, Local local) {
		return methods.addLocal(ref, local);
	}

	/**
	 * @see MethodVariableProvider#addParameters(MethodReference,
	 *      FieldOrLocal[])
	 */
	public boolean addParameters(MethodReference ref, FieldOrLocal... params) {
		return methods.addParameters(ref, params);
	}

	/**
	 * add equation lhs 'superset of' prefix . suffix*
	 * 
	 * @param lhs
	 *            lhs of equation
	 * @param prefix
	 *            prefix of regular expression
	 * @param suffix
	 *            suffix part of regular expression, may be appended arbitrarily
	 *            often
	 * @return
	 */
	public boolean addLoop(FieldOrLocal lhs, FieldOrLocal prefix, FieldOrLocal suffix) {
		boolean b1 = lhs.addLoop(prefix, suffix);
		boolean b2 = loops.add(lhs);
		return b1 || b2;
	}

	/**
	 * declares pi as piNode of var
	 * 
	 * @param pi
	 *            piNode
	 * @param var
	 *            "origin" of piNode
	 */
	public void addPiNode(FieldOrLocal pi, FieldOrLocal var) {
		piNodes.put(pi, var);
	}

	/**
	 * add intent variable
	 * 
	 * @param var
	 *            intent
	 * @return
	 */
	public boolean addIntent(IntentVar var) {
		return intents.add(var);
	}

	/**
	 * get variable for reference
	 * 
	 * @param ref
	 *            field
	 * @return field variable
	 */
	public FieldVariable getFieldVariable(FieldReference ref) {
		return fields.getVariable(ref);
	}

	/**
	 * get method variable for reference
	 * 
	 * @param ref
	 *            method
	 * @return method variable
	 */
	public MethodVariable getMethodVariable(MethodReference ref) {
		MethodVariable method = methods.getVariable(ref);
		return methods.getVariable(ref);
	}

	/**
	 * get all method variables
	 * 
	 * @return method variables
	 */
	public Collection<MethodVariable> getAllMethods() {
		return methods.getAllVariables();
	}

	/**
	 * @return field provider
	 */
	public FieldVariableProvider getFieldProvider() {
		return fields;
	}

	/**
	 * 
	 * @return method provider
	 */
	public MethodVariableProvider getMethodPovider() {
		return methods;
	}

	/**
	 * 
	 * @return all intent variables
	 */
	public Collection<IntentVar> getIntents() {
		return intents;
	}

	/**
	 * 
	 * @return number of classes used for analysis
	 */
	public int getNumOfClasses() {
		return numOfClasses;
	}

	/*
	 * public String toString() { StringBuilder sb = new StringBuilder();
	 * sb.append(fields.toString()); // sb.append("methods:\n\n");
	 * sb.append(methods.toString()); if (!intents.isEmpty()) {
	 * sb.append("Intents:\n\n"); for (IntentVar var : intents) {
	 * sb.append(var.toString()).append("\n"); } } return sb.toString(); }
	 */

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("analyzed:\n");
		sb.append(fields.size()).append(" fields\n");
		sb.append(methods.size()).append(" methods\n");
		sb.append(intents.size()).append(" intents\n");
		return sb.toString();
	}

	/**
	 * @see FieldVariableProvider#toString()
	 */
	public String getFieldString() {
		return fields.toString();
	}

	/**
	 * 
	 * @see MethodVariableProvider#toString()
	 */
	public String getMethodString() {
		return methods.toString();
	}

	/**
	 * 
	 * @return string representation of intents
	 */
	public String getIntentString() {
		StringBuilder sb = new StringBuilder();
		if (!intents.isEmpty()) {
			sb.append("Intents:\n\n");
			for (IntentVar var : intents) {
				sb.append(var.toString()).append("\n");
			}
		}
		return sb.toString();
	}

	/**
	 * @return total number of equations
	 */
	public int getNumberOfEquations() {
		int result = 0;
		// field variables
		for (FieldVariable field : fields.getAllFieldVariables()) {
			result += field.getNumberOfEquations();
		}
		// methods
		for (MethodVariable method : methods.getAllVariables()) {
			result += method.getNumberOfEquations();
		}
		// pi nodes
		for (FieldOrLocal pi : piNodes.values()) {
			result += pi.getNumberOfEquations();
		}
		// loops
		for (FieldOrLocal loop : loops) {
			result += loop.getNumberOfEquations();
		}
		// intents
		for (IntentVar intent : intents) {
			result += intent.getNumberOfAllEquations();
		}
		return result;
	}

}
