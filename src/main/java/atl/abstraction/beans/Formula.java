package atl.abstraction.beans;

import com.google.common.hash.HashCode;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Formula extends JsonObject implements Cloneable {

	//private HashMap<String, String> mapAtomToFormula = new HashMap<>();

	@SerializedName("group")
	@Expose
	private String name;
	@SerializedName("sub-formula")
	@Expose
	private Formula subformula;
	@SerializedName("ltl")
	@Expose
	private String ltl;
	@SerializedName("operator")
	@Expose
	private String operator;
	@SerializedName("terms")
	@Expose
	private List<String> terms = new ArrayList<>();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Formula getSubformula() {
		return subformula;
	}

	public String getLTLFormula() { return ltl; }

	public String getOperator() { return operator; }

	public void setSubformula(Formula subformula) {
		this.subformula = subformula;
	}

	public List<String> getTerms() {
		return subformula == null ? terms : Stream.concat(terms.stream(), subformula.getTerms().stream()).collect(Collectors.toList());
	}

	/*public HashMap<String, String> getMapAtomToFormula() {
		return mapAtomToFormula;
	}*/

	public void setTerms(List<String> terms) {
		this.terms = terms;
	}

	public Formula innermostFormula() {
		if(ltl != null && subformula == null) {
			return this;
		} else return subformula.innermostFormula();
	}

	public void updateInnermostFormula(String atom, HashMap<String, String> mapAtomToFormula) {
		if(subformula == null) {
			Formula aux = new Formula();
			aux.name = name;
			aux.terms = terms;
			aux.ltl = ltl;
			mapAtomToFormula.put(atom, aux.toString());
			name = null;
			operator = null;
			terms = new ArrayList<>();
			terms.add(atom);
			ltl = atom;
			return;
		}
		if(subformula.ltl != null && subformula.subformula == null) {
			mapAtomToFormula.put(atom, subformula.toString());
			subformula = null;
			terms.add(atom);
			ltl = this.ltl + " " + this.operator + " " + atom;
			operator = null;
		} else {
			subformula.updateInnermostFormula(atom, mapAtomToFormula);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(name != null) {
			sb.append("<<").append(name).append(">> ");
		}
		sb.append(ltl);
		if(subformula != null){
			sb.append(" ").append(operator).append(" ").append(subformula.toString());
		}
		return sb.toString();
	}
	public String toStringWithStatesForAtoms(HashMap<String, String> mapAtomToFormula) {
		StringBuilder sb = new StringBuilder();
		if(name != null) {
			sb.append("<<").append(name).append(">> ");
		}
		String f = ltl.toString();
		while(f.contains("atom")) {
			int i = f.indexOf("atom")+4;
			while(i < f.length() && f.charAt(i) >= '0' && f.charAt(i) <= '9') {
				i++;
			}
			f = f.replace(f.substring(f.indexOf("atom"), i), mapAtomToFormula.get(f.substring(f.indexOf("atom"), i)).toString());
		}
		sb.append(f);
		if(subformula != null){
			sb.append(" ").append(operator).append(" ").append(subformula.toString());
		}
		return sb.toString();
	}


	public String extractLTL(Formula formula, String atom, HashMap<String, String> mapAtomToFormula) {
		return "F(" + this.extractLTLAux(formula, atom, mapAtomToFormula) + ")";
	}

	private String extractLTLAux(Formula formula, String atom, HashMap<String, String> mapAtomToFormula) {
		String f = formula.toString();
		while(f.contains("atom")) {
			int i = f.indexOf("atom")+4;
			while(i < f.length() && f.charAt(i) >= '0' && f.charAt(i) <= '9') {
				i++;
			}
			f = f.replace(f.substring(f.indexOf("atom"), i), mapAtomToFormula.get(f.substring(f.indexOf("atom"), i)).toString());
		}
		if(this.toString().equals(f)) {
			return atom;
		}
		if(subformula != null) {
			String subLTL = subformula.extractLTLAux(formula, atom, mapAtomToFormula);
			if(subLTL == null) {
				return null;
			} else {
				if (subLTL.equals(atom)) {
//					if(operator.equals("and")) {
//						return insertAtomInLTL(ltl, operator, atom);
//					}
					return ltl;
				}
				return ltl + " " + operator + " " + subLTL;
			}
		}
		return null;
	}

	private String insertAtomInLTL(String ltl, String operator, String atom) {
		boolean startWithF = ltl.startsWith("F");
		boolean startWithG = ltl.startsWith("G");
		boolean startWithX = ltl.startsWith("X");
		if(startWithF || startWithG || startWithX) {
			ltl = ltl.substring(1);
			if(ltl.startsWith("(")) ltl = ltl.substring(1);
			if(ltl.endsWith(")")) ltl = ltl.substring(0, ltl.length()-1);
			return (startWithF ? "F(" : (startWithG ? "G(" : "X")) + insertAtomInLTL(ltl, operator, atom) + ")";
		} else return ltl + operator + "F(" + atom + ")";
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Formula)) {
			return false;
		}
		Formula formula = (Formula) obj;
		return
				Objects.equals(this.name, formula.name) &&
				new HashSet<>(this.terms).equals(new HashSet<>(formula.terms)) &&
				Objects.equals(this.ltl, formula.ltl) &&
				Objects.equals(this.subformula, formula.subformula) &&
				Objects.equals(this.operator, formula.operator);
	}

	@Override
	public int hashCode() {
		return
				(this.name == null ? 0 : this.name.hashCode()) +
				(this.ltl == null ? 0 : this.ltl.hashCode()) +
				this.terms.stream().mapToInt(String::hashCode).sum() +
				(this.subformula == null ? 0 : this.subformula.hashCode()) +
				(this.operator == null ? 0 : this.operator.hashCode());
	}

	@Override
	public Formula clone() {
		Formula formula = new Formula();
		formula.name = name;
		formula.terms = new ArrayList<>(this.terms);
		formula.operator = operator;
		formula.ltl = this.ltl;
		if(subformula != null) {
			formula.subformula = subformula.clone();
		}
		return formula;
	}
}
