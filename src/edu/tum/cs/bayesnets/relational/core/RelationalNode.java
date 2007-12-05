package edu.tum.cs.bayesnets.relational.core;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.tum.cs.srldb.Database;

public class RelationalNode {
	/**
	 * index into the network's array of BeliefNodes
	 */
	public int index;
	/**
	 * the function/predicate name this node is concerned with (without any arguments)
	 */
	protected String functionName;
	/**
	 * the list of node parameters
	 */
	public String[] params;
	/**
	 * a reference to the BeliefNode that this node extends
	 */
	public BeliefNode node;
	/**
	 * noisy-or parameters, i.e. parameters that are free in some parents (which must consequently be grounded in an auxiliary parent node, and all aux. parents must be combined via noisy-or)
	 */
	public String[] addParams;
	protected RelationalBeliefNetwork bn;
	public boolean isConstant, isAuxiliary, isPrecondition, isUnobserved;
	public boolean usesCombinationFunction;
	public String parentMode;
	
	public static String join(String glue, String[] elems) {
		StringBuffer res = new StringBuffer();
		for(int i = 0; i < elems.length; i++) {
			res.append(elems[i]);
			if(i < elems.length-1)
				res.append(glue);
		}
		return res.toString();
	}
	
	public static String formatName(String nodeName, String[] args) {
		return String.format("%s(%s)", nodeName, join(",", args));
	}
	
	/**
	 * extracts the node name (function/predicate name) from a variable name (which contains arguments)
	 * @param varName
	 * @return
	 */
	public static String extractFunctionName(String varName) {
		if(varName.contains("("))
			return varName.substring(0, varName.indexOf('('));
		return varName;
	}
	
	public RelationalNode(RelationalBeliefNetwork bn, BeliefNode node) throws Exception {
		this.bn = bn;
		Pattern namePat = Pattern.compile("(\\w+)\\((.*)\\)");
		String name = node.getName();
		// preprocessing: special parent nodes encoded in prefix 
		if(name.charAt(0) == '#') { // auxiliary: CPT is meaningless
			isAuxiliary = true;
			name = name.substring(1);
		}
		else if(name.charAt(0) == '+') { // precondition: node is boolean and required to be true
			isPrecondition = true;
			isAuxiliary = true;
			name = name.substring(1);
		}
		// preprocessing: special child node that requires different treatment of parent nodes
		int sepPos = name.indexOf('|');
		if(sepPos != -1) {
			String decl = name.substring(sepPos+1);
			Pattern declPat = Pattern.compile("([A-Z]+):(.*)");			
			Matcher m = declPat.matcher(decl);
			if(m.matches()) {
				parentMode = m.group(1);
				addParams = m.group(2).split("\\s*,\\s*");
			}
			else {
				addParams = decl.split("\\s*,\\s*");
				usesCombinationFunction = true;
			}
			name = name.substring(0, sepPos);
		}
		// match function and parameters
		Matcher matcher = namePat.matcher(name);
		if(matcher.matches()) {	// a proper relational node, such as "foo(x,y)"
			this.functionName = matcher.group(1);
			this.params = matcher.group(2).split("\\s*,\\s*");
			this.isConstant = false;
		}
		else { // constant: usually a node such as "x"
			this.functionName = name;
			this.params = new String[0];
			this.isConstant = true;
		}
		this.index = bn.getNodeIndex(node);
		this.node = node;
	}
	
	public String toString() {
		return getName();		
	}
	
	public String getName() {
		return this.node.getName();
	}
	
	public String getCleanName() {
		return formatName(this.functionName, this.params);
	}
	
	/**
	 * @return true if the node node is boolean, i.e. it has a boolean domain
	 */
	public boolean isBoolean() {
		Signature sig = bn.getSignature(this);
		if(sig != null)
			return sig.returnType.equals("Boolean");
		else
			return bn.isBooleanDomain(node.getDomain());
	}
	
	public static class Signature {
		public String returnType;
		public String[] argTypes;
		public String functionName;
	
		public Signature(String functionName, String returnType, String[] argTypes) {
			this.returnType = returnType;
			this.argTypes = argTypes;
			this.functionName = functionName;
		}
		
		public void replaceType(String oldType, String newType) {
			if(this.returnType.equals(oldType))
				this.returnType = newType;
			for(int i = 0; i < argTypes.length; i++) {
				if(argTypes[i].equals(oldType))
					argTypes[i] = newType;
			}
		}
		
		@Override
		public String toString() {
			return String.format("%s %s(%s)", returnType, functionName, RelationalNode.join(",", argTypes));
		}
	}
	
	/**
	 * gets the name of the function/predicate that this node corresponds to
	 * @return
	 */
	public String getFunctionName() {
		return this.functionName;
	}
	
	/**
	 * generates a textual representation of the logical literal that this node represents for a certain assignment (and, optionally, substitutions of its parameters) 
	 * @param setting  the value this node is set to given by an index into the node's domain
	 * @param constantValues  mapping of this node's arguments to constants; any number of arguments may be mapped; may be null
	 * @return
	 */
	protected String toLiteral(int setting, HashMap<String,String> constantValues) {
		// predicate name
		StringBuffer sb = new StringBuffer(String.format("%s(", Database.lowerCaseString(functionName)));
		// add parameters
		for(int i = 0; i < params.length; i++) {
			if(i > 0)
				sb.append(",");
			String value = constantValues != null ? constantValues.get(params[i]) : null;
			if(value == null)
				sb.append(params[i]);
			else
				sb.append(value);
		}
		// add node value (negation as prefix or value of non-boolean variable as additional parameter)
		String value = ((Discrete)node.getDomain()).getName(setting);
		if(isBoolean()) {
			if(value.equalsIgnoreCase("false"))
				sb.insert(0, '!');
		}
		else {
			sb.append(',');
			sb.append(Database.upperCaseString(value));			
		}
		sb.append(')');
		return sb.toString();
	}
	
	/**
	 * gets the network this node belongs to
	 */
	public RelationalBeliefNetwork getNetwork() {
		return bn;
	}
	
	public boolean requiresNoisyOr() {
		return addParams != null && addParams.length > 0 && parentMode.equals("OR");
	}
	
	/**
	 * @return true if the node has a conditional probability distribution given as a CPT
	 */
	public boolean hasCPT() {
		return !requiresNoisyOr();
	}
	
	/**
	 * retrieves this node's signature
	 * @return
	 */
	public Signature getSignature() {
		return bn.getSignature(this);
	}
	
	/**
	 * @return true if the node represents a relation between two or more objects
	 */
	public boolean isRelation() {
		return params != null && params.length > 1;
	}
	
	
	/**
	 * gets the name of the variable (grounded node) that results when applying the given actual parameters to this node 
	 * @param actualParams
	 * @return
	 * @throws Exception 
	 */
	public String getVariableName(String[] actualParams) throws Exception {
		if(actualParams.length != params.length)
			throw new Exception(String.format("Invalid number of actual parameters suppplied for %s: expected %d, got %d", toString(), params.length, actualParams.length));
		return formatName(getFunctionName(), actualParams);
	}
}
